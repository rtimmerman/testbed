package example

import org.apache.kafka.clients.consumer._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.slf4j.LoggerFactory
//import spire.implicits._
// import spire.math._

import java.util.{Date, Properties}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.core.`type`.TypeReference
import com.mongodb.client.model.UpdateOneModel

class Complex(var r: BigDecimal, var i: BigDecimal) {
  def + (other: Complex): Complex = new Complex(this.r + other.r, this.i + other.i)
  

  def * (other: Complex): Complex = new Complex(
        this.r * other.r - (other.i * this.i), 
        this.r * other.i + this.i * other.r
  )

  def pow(n: BigDecimal): Complex = this * this

  def abs(): Double = Math.pow(r.pow(2).toDouble + i.pow(2).toDouble, 0.5)

  def conj(): Complex = new Complex(this.r, -this.i)
}

object Consumer {
  val logger = LoggerFactory.getLogger(Consumer.getClass.getName)
  val mapper =
    JsonMapper
      .builder()
      .addModule(DefaultScalaModule)
      .build()

  def encodedPayload(
      r: String,
      i: String,
      iterations: String,
      uuid: String
  ): String = {
    mapper.writeValueAsString(
      Map[String, String](
        "r" -> r,
        "i" -> i,
        "iterations" -> iterations,
        "uuid" -> uuid
      )
    )
  }

  def decodedPayload(payload: String): Map[String, String] = {
    mapper.readValue(
      payload,
      new TypeReference[Map[String, String]] {}
    )
  }

  def process(z: Complex, c: Complex, iterations: Int): Int = {
    if (iterations < 1) {
      return -1
    }

    val M: Complex = (z pow 2) + c

    if (M.abs() > 4.0) {
      return iterations
    }

    process(M, c, iterations - 1)
  }

  def consume(topic: String) = {
    val props = new Properties()
    props.put("bootstrap.servers", "kafka-topic-server:9092")
    props.put(
      "key.deserializer",
      "org.apache.kafka.common.serialization.StringDeserializer"
    )
    props.put(
      "value.deserializer",
      "org.apache.kafka.common.serialization.StringDeserializer"
    )
    props.put(
      "group.id",
      "0"
    )

    props.put(
      "topic",
      topic
    )

    val dataOutProps = new Properties();
    dataOutProps.put("bootstrap.servers", "kafka-topic-server:9092")
    dataOutProps.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    dataOutProps.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    dataOutProps.put("transactional.id", s"dw-transaction-id-$topic")

    dataOutProps.put("retries", "10")
    dataOutProps.put("retry.backoff.ms", "5000")

    println(s"Consuming from ${topic}")

    val consumer = new KafkaConsumer[String, String](props);
    val resultProducer = new KafkaProducer[String, String](dataOutProps)
    resultProducer.initTransactions()
    var topics = new java.util.ArrayList[String]()
    topics.add(props.get("topic").asInstanceOf[String])

    consumer.subscribe(topics)

    while (true) {
      val work = consumer.poll(1000)

      if (work != null) {
        val writes = ListBuffer[UpdateOneModel[Nothing]]()
        val insert = mutable.Queue[UpdateOneModel[Nothing]]()

        work.forEach(record => {
          println(record.key() + " = " + record.value())

          val payload = decodedPayload(record.value())
          val z =
            new Complex(BigDecimal(payload("r")), BigDecimal(payload("i")))

          // do some processing
          val res = process(z, z, payload("iterations").toInt)

          // Save the result
          DataWriter.doWork(
            resultProducer,
            "writeData",
            s"${payload("r")}:${payload("i")}",
            Map[String, String](
              "r" -> payload("r"),
              "i" -> payload("i"),
              "value" -> res.toString,
              "uuid" -> payload("uuid"),
              "computeDateStamp" -> java.time.Instant
                .now()
                .toString,
              "topic" -> topic
            )
          )
        })
      }
    }
  }

}
