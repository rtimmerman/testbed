package rrt
import java.util.{Properties, UUID}
import org.apache.kafka.clients.producer._
import breeze.math._
import breeze.linalg._

import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

// extension (x: Int)
//   def toR: Double = -2 + (x/size_x.toDouble * 4)
//   def toI: Double = -2 + (x/size_y.toDouble * 4)

object Producer extends KafkaTrait {

  def createSpaceFromPoint(c: rrt.Complex, zoomPc: Integer = 100, ball: Integer = 1000, canvasDimensions: Map[String, Int] = Map("minR" -> -2, "maxR" -> 2, "minI" -> -2, "maxI" -> 2)): Array[Array[Map[String, Double]]] = {
    val scale = 1 / (zoomPc.toDouble / 100)
    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 to x2 by ((x1 - x2).abs / (count)))
    val R = linspace(canvasDimensions("minR"), canvasDimensions("maxR"), BigDecimal(ball))
    val I = linspace(canvasDimensions("minI"), canvasDimensions("maxI"), BigDecimal(ball))

    // the point is c

    // transform the points by adding c and multiplying them by scale across the initial space.
    val tR = R.map(e => (e + c.r) * scale)
    val tI = R.map(e => (e + c.i) * scale)

    val b = Array.ofDim[Map[String, Double]](ball + 1, ball + 1)
    for (i <- tI; r <- tR)
      b(tR.indexOf(r))(tI.indexOf(i)) = Map("r" -> r.toDouble, "i" -> i.toDouble)

    return b
  }

  def createSpace(sizeX: Int, sizeY: Int, minR: Double, maxR: Double, minI: Double, maxI: Double): Array[Array[Map[String,Double]]] = {
    val b = Array.ofDim[Map[String, Double]](sizeX + 1, sizeY + 1)

    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 to x2 by ((x1 - x2).abs/count))

    val toCoord = (x1: Double, x2: Double, scale: Double, point: BigDecimal) => linspace(x1, x2, scale).indexOf(point)

    val I = linspace(minI, maxI, sizeY)
    val R = linspace(minR, maxR, sizeX)
    for (i <- I; r <- R)
      val x = R.indexOf(r)
      val y = I.indexOf(i)
      b(x)(y) = Map("r" -> r.toDouble, "i" -> i.toDouble)

    return b
  }

  def gridWorkStream(
      kafkaProducer: KafkaProducer[String, String],
      frameConfigFile: String
  ) = {

    val runUUID = UUID.randomUUID()

    var mapper = new ObjectMapper(new YAMLFactory())
    var paramsBase = mapper.readValue(new File(frameConfigFile), classOf[ProducerParams]): ProducerParams
    var b: Array[Array[Map[String,Double]]] = null
    var space = 0

    if (paramsBase.version == 2)
        val params = mapper.readValue(new File(frameConfigFile), classOf[ProducerParamsV2]): ProducerParamsV2
        b = createSpaceFromPoint(rrt.Complex.fromString(params.coordinate), params.zoomPc, params.neighbourhoodSize)
        space = params.neighbourhoodSize * params.neighbourhoodSize
    else
        val params = paramsBase
        b = createSpace(params.sizeX, params.sizeY, params.minR, params.maxR, params.minI, params.maxI)
        space = params.sizeX * params.sizeY

    val batches = b.flatten.grouped(space/16).toList
  
    var lot = 0
    
    batches.foreach(batch => {
      kafkaProducer.beginTransaction()
      val topic = s"${paramsBase.topicPrefix}-${lot}"
      println(s"Sending ${batch.length} entries to $topic")
      batch.foreach(entry => {
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            Consumer.encodedPayload(
                "%f".format(entry.getOrElse("r", 0)),
                "%f".format(entry.getOrElse("i", 0)),
                paramsBase.iterations.toString(),
                runUUID.toString()
              )
            )
          ).get()
        })
      kafkaProducer.commitTransaction()
      lot += 1
    })

    kafkaProducer.close()
  }

  def produceGridPoints(frameConfigFile: String, kafkaProducer: KafkaProducer[String, String] = null) = {
    val producer: KafkaProducer[String, String] = kafkaProducer match {
      case null => initProducer("transaction-id-1")
      case _ => kafkaProducer
    }

    producer.initTransactions()
    gridWorkStream(producer, frameConfigFile)
    producer.close()
  }
}
