package rrt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentCaptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.mockito.hamcrest.MockitoHamcrest
import scala.concurrent.Future
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.Metadata
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory


class ProducerSpec extends AnyFlatSpec with Matchers {
  "The Producer object" should "be able to evenly distribute work to nodes" in {
    // val topic = "test"  
    // val iterations = 100

    // val kafkaProducer = Mockito.mock(classOf[KafkaProducer[String, String]])
    // // val mockFuture = Mockito.mock(classOf[java.util.concurrent.Future.type])
    // //Mockito.doReturn(mockFuture).when(kafkaProducer).send(ArgumentMatchers.any())
    // // Mockito
    // //   .when(kafkaProducer.send(ArgumentMatchers.any()))
    // //   .thenReturn(mockFuture)
    // println("here")

    // Producer.produceGridPoints(topic, 10, kafkaProducer)
  }

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

    //var params = mapper.readValue(new File(frameConfigFile), classOf[ProducerParams]): ProducerParams
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

  "Producer" should "be able to consume v2 parameters" in {
    var mapper = new ObjectMapper(new YAMLFactory())

    val yamlText = """
    version: 2
    iterations: 1000
    topicPrefix: test
    coordinate: 0+0i
    neighbourhoodSize: 1000
    zoomPc: 100
    """
    var params = mapper.readValue(yamlText, classOf[ProducerParamsV2]): ProducerParamsV2
    assert(params.version == 2)
    assert(params.coordinate == "0+0i")
    assert(params.neighbourhoodSize == 1000)
    assert(params.zoomPc == 100)
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
}
