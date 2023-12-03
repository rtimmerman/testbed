package example
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
  def gridWorkStream(
      kafkaProducer: KafkaProducer[String, String],
      frameConfigFile: String
  ) = {

    //val config = new ObjectMapper(new YamlFactory())
    var mapper = new ObjectMapper(new YAMLFactory())
    var params = mapper.readValue(new File(frameConfigFile), classOf[ProducerParams]): ProducerParams

    var size_x: Int = params.sizeX
    var size_y: Int = params.sizeY

    val runUUID = UUID.randomUUID()

    val b = Array.ofDim[Map[String, Double]](size_x, size_y)
    

    // for (y <- Range(0, size_y, 1);
    //   x <- Range(0, size_x, 1))
    //   b(x)(y) = Map("r" -> x.toR, "i" -> y.toI)

    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 to x2 by ((x1 - x2).abs/count))
    val toCoord = (x1: Double, x2: Double, scale: Double, point: BigDecimal) =>  (((Math.max(x1, x2) + point) / Math.abs(x1 - x2)) * scale).toInt

    for (i <- linspace(params.minI, params.maxI, size_y); r <- linspace(params.minR, params.maxR, size_x))
      val x = toCoord(params.minR.toDouble, params.maxR.toDouble, size_x, r)
      val y = toCoord(params.minI.toDouble, params.maxI.toDouble, size_y, i)
      b(x)(y) = Map("r" -> r.toDouble, "i" -> i.toDouble)
      

    val batches = b.flatten.grouped((size_x * size_y)/16).toList
  
    var lot = 0
    
    batches.foreach(batch => {
      kafkaProducer.beginTransaction()
      val topic = s"${params.topicPrefix}-${lot}"
      println(s"Sending ${batch.length} entries to $topic")
      batch.foreach(entry => {
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            Consumer.encodedPayload(
                "%f".format(entry.getOrElse("r", 0)),
                "%f".format(entry.getOrElse("i", 0)),
                params.iterations.toString(),
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
