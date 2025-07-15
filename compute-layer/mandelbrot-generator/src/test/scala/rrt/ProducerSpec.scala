package rrt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.Tag
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentCaptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.MockProducer
import org.mockito.hamcrest.MockitoHamcrest
import scala.concurrent.Future
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.Metadata
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import breeze.linalg._

import scala.util.boundary
import boundary.break
import rrt.external.PerformanceEvaluator
import org.mockito.MockedStatic


object ParameterSpace extends Tag("rrt.tags.ParameterSpace")
object Kafka extends Tag("rrt.tags.Kafka")
object Navigation extends Tag("rrt.tags.Navigation")
object WorkConfig extends Tag("rrt.tags.WorkConfig")
object Dimensionality extends Tag("rrt.tags.Dimensionality")

class ProducerSpec extends AnyFlatSpec with Matchers {
  "A producer instance" should "be able to support customisable canvas plots" in {
    val params = ProducerParams(1, 100,"test",-2,2,2,-2,1000,1000)

    val b = Producer.createSpace(params.sizeX, params.sizeY, params.minR, params.maxR, params.minI, params.maxI)

    // left extreme should -2-2i
    assert(b(0)(0)("r") == -2)
    assert(b(0)(0)("i") == 2)

    // origin should be 0+0i
    assert(b(500)(500)("r") == 0)
    assert(b(500)(500)("i") == 0)

    // right most should be 2+2i
    assert(b(1000)(1000)("r") == 2)
    assert(b(1000)(1000)("i") == -2)
  }

  "Producer" should "be able to produce neighbour points of fixed size around locus" in {
    val c = rrt.Complex(0, 0)
    val space = Producer.createSpaceFromPoint(c)
    // left most should be -2-2i
    assert(space(0)(0)("r") == -2)
    assert(space(0)(0)("i") == 2)

    // origin should be 0+0i
    assert(space(500)(500)("r") == 0)
    assert(space(500)(500)("i") == 0)

    // right most should be 2+2i
    assert(space(1000)(1000)("r") == 2)
    assert(space(1000)(1000)("i") == -2)
  }

  "Producer" should "generate space able to support points of fixed size around locus" in {
    val c = rrt.Complex(-1, -1)
    val space = Producer.createSpaceFromPoint(c)
    // left most should be -2-2i
    assert(space(0)(0)("r") == -3)
    assert(space(0)(0)("i") == 1)

    // origin should be 0+0i
    assert(space(500)(500)("r") == -1)
    assert(space(500)(500)("i") == -1)

    // right most should be 2+2i
    assert(space(1000)(1000)("r") == 1)
    assert(space(1000)(1000)("i") == -3)
  }

  "Producer" should "be able to consume v1 parameters" in {
    val yamlText = """
    version: 1
    iterations: 1000
    topicPrefix: test
    minI: -2.0
    maxI: 2.0
    minR: -2.0
    maxR: 2.0
    sizeX: 1000
    sizeY: 1000
    """

    var mapper = new ObjectMapper(new YAMLFactory())

    var params = mapper.readValue(yamlText, classOf[ProducerParams]): ProducerParams
    assert(params.version == 1)
    assert(params.iterations == 1000)
    assert(params.topicPrefix == "test")
    assert(params.minI == -2)
    assert(params.maxI == 2)
    assert(params.minI == -2)
    assert(params.maxI == 2)
    assert(params.sizeX == 1000)
    assert(params.sizeY == 1000)
  }

  "Producer" should "be able to consume v2 parameters" taggedAs(WorkConfig) in {
    var mapper = new ObjectMapper(new YAMLFactory())

    val yamlText = """
    version: 2
    iterations: 1000
    topicPrefix: test
    coordinate: 0+0i
    neighbourhoodSize: 1000
    zoomPc: 100_000
    """
    var params = mapper.readValue(yamlText, classOf[ProducerParamsV2]): ProducerParamsV2
    assert(params.version == 2)
    assert(params.coordinate == "0+0i")
    assert(params.neighbourhoodSize == 1000)
    assert(params.zoomPc == 100000)
  }

  "Producer" should "be able to consume either v1 or v2 parameters" in {
    val yamlText = """
    version: 2
    iterations: 1000
    topicPrefix: test
    coordinate: 0+0i
    neighbourhoodSize: 1000
    zoomPc: 100
    """

    var mapper = new ObjectMapper(new YAMLFactory())
    var paramsBase = mapper.readValue(yamlText, classOf[ProducerParams]): ProducerParams
    var b: Array[Array[Map[String,Double]]] = null
    var space = 0

    if (paramsBase.version == 2)
        val params = mapper.readValue(yamlText, classOf[ProducerParamsV2]): ProducerParamsV2
        b = Producer.createSpaceFromPoint(rrt.Complex.fromString(params.coordinate), params.zoomPc, params.neighbourhoodSize)
        space = params.neighbourhoodSize * params.neighbourhoodSize
    else
        val params = paramsBase
        b = Producer.createSpace(params.sizeX, params.sizeY, params.minR, params.maxR, params.minI, params.maxI)
        space = params.sizeX * params.sizeY
    
    assert(space == 1e6)
    assert(b.size == 1001)
    assert(b(0).size == 1001)
  }

  "Producer" should "correctly center on a given coordinate in v2 jobs" taggedAs(Navigation) in {
    val locus = rrt.Complex.fromString("-0.19821982198219823-1.1003100310031004i")

    val space = Producer.createSpaceFromPoint(locus, 200_000, 160)
    val ctr = space(80)(80)

    assert(ctr.get("i").get.toDouble.equals(locus.i.toDouble), s"Expected i to be ${locus.i.toDouble} but got ${ctr.get("i").get.toDouble}")
    assert(ctr.get("r").get.toDouble.equals(locus.r.toDouble), s"Expected r to be ${locus.r.toDouble} but got ${ctr.get("r").get.toDouble}")

    // this code previews the outcome
    space.foreach(i => {
      i.foreach(r => {
        val z = rrt.Complex(BigDecimal(r.get("r").get), BigDecimal(r.get("i").get))
        val n = Consumer.process(z, z, 100)
        if (n > -1) {
          print(List('*', '-', '+', '/')(n % 4))
        } else {
          print(' ')
        }
      })
      println()
    })

  }


  "Producer" should "partition a space evenly between 16 consumers." in {
    
    // create the space
    val space = Array.ofDim[Int](16, 16)
    val spc = (0 to 15)
    var z: Int = 0
    var p, q: Int = 0
    for (i <- spc; j <- spc)
      if (z % 4 == 0 && z > 0)
        p += 1
      if (z % 16 == 0 && z > 0)
        p = q
      if (z % 63 == 0 && z > 0)
        q += 4
      space(i)(j) = p

      // display
      print("%-6d".format(p))
      z += 1

      if (z % 16 == 0)
        println()
    
    // Square shaped partitioning
    val nPartition = 16
    val pSize = 4
    // val allocations: Map[Int, List[Int]]
    println("partition size = %d".format(pSize))
    var rank = 0
    val parts: Array[Double] = null

    (0 to 15 by 4).foreach(y => {
      (0 to 15 by 4).foreach(x => {
        // space.slice(y, y + 4).toList.foreach(s => {s.slice(x, x + 4).toList.foreach(a => print("%-4d".format(a))); println();})
        val items = space.slice(y, y + 4).toList.map(s => s.slice(x, x + 4).toList).flatten
        println(items)
        println("%d ___".format(rank))
        rank += 1
     })
    })
  }

  "Producer" should "partition a square space in 16 subspaces" taggedAs(ParameterSpace) in {
    val c = new Complex(0, 0)
    val nParts = 16
    val space = Producer.createSpaceFromPoint(c, ball=16)
 
    val spaces = Producer.partition(space, nParts)

    assert(spaces.length == nParts)

    spaces.foreach(s => {
        // note well: the square is actually 5 elements by 5 since the count is inclusive (and overlaps).
        assert(s.length == 25)
      })

    // range check
    (0 to 3).foreach(spaces(_).foreach(entry => {
      assert(entry("i") >= 1.0)
    }))

    (0 to 14 by 4).foreach(spaces(_).foreach(entry => {
      assert(entry("r") <= -1.0 && entry("r") >= -2.0)
    }))
  }

  "Producer" should "be able to find the julia dimension from a set of coordinate points" taggedAs(Dimensionality)in {
    // val configFile = System.getProperty("user.dir") + "/src/test/resources/test-work.yml"
    val space = Producer.createSpaceFromPoint(Complex(0, 0), ball=16).flatten
    val dimension = Producer.getJuliaDimension(space, 10, 40)
    assert(1 == Math.round(dimension.dim)) // 1 - indicates

    // position one (0) of the tuple is the dimension, position two is the center point.
    assert(
      1 > Producer.getJuliaDimension(
        Producer.createSpaceFromPoint(Complex(-6, -6), ball=16).flatten,
        10,
        40
      ).dim
    )
  }

  "Producer" should "be able to send work to Kafka" taggedAs(Kafka) in {
    val configFile = System.getProperty("user.dir") + "/src/test/resources/test-work.yml"
    val producerProps = new java.util.Properties()
    val params = Producer.getParams(configFile)

    // val peMock = Mockito.mockStatic(getClass[PerformanceEvaluator.type])
    // Mockito.when(peMock.getLastPerformance())

    Producer.gridWorkStream((txName: String) => {
      val kafkaProducer = MockProducer[String, String](
        true,
        new org.apache.kafka.common.serialization.StringSerializer,
        new org.apache.kafka.common.serialization.StringSerializer
      )
      kafkaProducer.initTransactions
      kafkaProducer
    }, params)
  }
}
