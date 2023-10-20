package example
import java.util.{Properties, UUID}
import org.apache.kafka.clients.producer._
import breeze.math._
import breeze.linalg._

import java.security.MessageDigest;

class Producer(var kafkaProducer: KafkaProducer[String, String] = null)
{

}

val size_x = 1000
val size_y = 1000

extension (x: Int)
  def toR: Double = -2 + (x/size_x.toDouble * 4)
  def toI: Double = -2 + (x/size_y.toDouble * 4)

object Producer extends KafkaTrait {
  def gridWorkStream(
      kafkaProducer: KafkaProducer[String, String],
      topicPrefix: String,
      iterations: Int
  ) = {
    val runUUID = UUID.randomUUID()

    val b = Array.ofDim[Map[String, Double]](size_x, size_y)
    for (y <- Range(0, size_y, 1);
      x <- Range(0, size_x, 1))
      b(x)(y) = Map("r" -> x.toR, "i" -> y.toI)

    val batches = b.flatten.grouped((size_x * size_y)/16).toList
  
    var lot = 0
    
    batches.foreach(batch => {
      kafkaProducer.beginTransaction()
      val topic = s"${topicPrefix}-${lot}"
      println(s"Sending ${batch.length} entries to $topic")
      batch.foreach(entry => {
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            Consumer.encodedPayload(
                "%f".format(entry.getOrElse("r", 0)),
                "%f".format(entry.getOrElse("i", 0)),
                iterations.toString(),
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

  def produceGridPoints(topicPrefix: String, iterations: Int, kafkaProducer: KafkaProducer[String, String] = null) = {
    val producer: KafkaProducer[String, String] = kafkaProducer match {
      case null => initProducer("transaction-id-1")
      case _ => kafkaProducer
    }

    producer.initTransactions()
    gridWorkStream(producer, topicPrefix, iterations)
    producer.close()

  }
}
