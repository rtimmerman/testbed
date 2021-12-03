package example
import java.util.{Properties, UUID}
import org.apache.kafka.clients.producer._
import breeze.math._
import breeze.linalg._

import java.security.MessageDigest;

object Producer {
  def gridWorkStream(
      kafkaProducer: KafkaProducer[String, String],
      topicPrefix: String,
      iterations: Int
  ) = {

    val data =
      for (
        r <- BigDecimal(-2.0) to BigDecimal(2.0) by 0.01;
        i <- BigDecimal(-2.0) to BigDecimal(2.0) by 0.01
      )
        yield breeze.math.Complex(r.toDouble, i.toDouble)

    val plane = breeze.linalg.DenseMatrix.create(401, 401, data.toArray)

    var lot: Int = 0

    kafkaProducer.beginTransaction()
    for (x <- 0 to 399 by 100; y <- 0 to 399 by 100) {
      val topic = s"${topicPrefix}-${lot}"
      val ylimit = if (y + 100 >= 400) 400 else y + 100
      print(s"Sending lot ${lot} ")

      val xlimit = if (x + 100 >= 400) 400 else x + 100
      val entries = plane(x to xlimit, y to ylimit).toArray

      val runUUID = UUID.randomUUID()

      println(s"(${entries.length} entries to ${topic})")
      entries.foreach(value => {
        //println(value.toString())
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            value.toString + ";" + iterations + ";" + runUUID.toString
          )
        )
      })

      lot = if (((x + y) % 100) == 0) lot + 1 else lot

    }

    kafkaProducer.close()
  }

  def produceGridPoints(topicPrefix: String, iterations: Int) = {
    val props = new Properties()
    props.put("bootstrap.servers", "kafka-topic-server:9092")
    props.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    props.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    props.put(
      "transactional.id",
      "transaction-id-1"
    )
    val producer = new KafkaProducer[String, String](props)
    producer.initTransactions()
    //val record =
    //  new ProducerRecord[String, String]("test", "test-key", "test value")
    gridWorkStream(producer, topicPrefix, iterations)
    producer.close()

  }
}
