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

    // static factor at 1000, in future this could be dynamically established instead (via an arg)
    val factor = 1e3
    val divf = 1 / factor

    val data =
      for (
        r <- BigDecimal(-2.0) to BigDecimal(2.0) by divf;
        i <- BigDecimal(-2.0) to BigDecimal(2.0) by divf
      )
        yield breeze.math.Complex(r.toDouble, i.toDouble)

    val plane = breeze.linalg.DenseMatrix.create(
      4 * factor.toInt + 1,
      4 * factor.toInt + 1,
      data.toArray
    )

    var lot: Int = 0

    kafkaProducer.beginTransaction()
    val runUUID = UUID.randomUUID()

    println(s"Starting run: $runnUUID")

    for (
      x <- 0 to (4 * factor.toInt - 1) by factor.toInt;
      y <- 0 to (4 * factor.toInt - 1) by factor.toInt
    ) {
      val topic = s"${topicPrefix}-${lot}"
      val ylimit =
        if (y + factor.toInt >= (4 * factor.toInt)) 4 * factor.toInt
        else y + factor.toInt
      print(s"Sending lot ${lot} ")

      val xlimit =
        if (x + factor.toInt >= (4 * factor.toInt)) 4 * factor.toInt
        else x + factor.toInt
      val entries = plane(x to xlimit, y to ylimit).toArray

      println(s"(${entries.length} entries to ${topic})")
      entries.foreach(value => {
        //println(value.toString())
        kafkaProducer.send(
          new ProducerRecord[String, String](
            topic,
            "coordinate",
            //value.toString + ";" + iterations + ";" + runUUID.toString
            Consumer.encodedPayload(
              value.real.toString(),
              value.imag.toString(),
              iterations.toString(),
              runUUID.toString()
            )
          )
        )
      })

      lot = if (((x + y) % factor) == 0) lot + 1 else lot

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
