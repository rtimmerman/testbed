package example
import java.util.Properties
import org.apache.kafka.clients.producer._

import breeze.math._
import breeze.linalg._

object Producer {
  def gridWorkStream(
      kafkaProducer: KafkaProducer[String, String],
      topicPrefix: String
  ) = {

    val data =
      for (
        r <- BigDecimal(-2.0) to BigDecimal(2.0) by 0.01;
        i <- BigDecimal(-2.0) to BigDecimal(2.0) by 0.01
      )
        yield breeze.math.Complex(r.toDouble, i.toDouble)

    val plane = breeze.linalg.DenseMatrix.create(401, 401, data.toArray)

    var lot = 0

    kafkaProducer.beginTransaction()
    for (x <- 0 to 401 by 26) {
      print(s"Sending lot ${lot} ")
      val limit = if (x + 26 > 401) 400 else x + 26
      val entries = plane(x to limit, x to limit).toArray
      val topic = s"${topicPrefix}-${lot}"

      println(s"(${entries.length} entries to ${topic})")
      entries.foreach(value => {
        //println(value.toString())
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            value.toString
          )
        )
      })

      lot += 1
    }

    kafkaProducer.close()
  }

  def produceGridPoints(topicPrefix: String) = {
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
    gridWorkStream(producer, topicPrefix)
    producer.close()

  }
}
