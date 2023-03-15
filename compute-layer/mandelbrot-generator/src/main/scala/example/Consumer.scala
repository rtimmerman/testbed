package example

import org.apache.kafka.clients.consumer._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.mongodb.scala.model.UpdateOneModel
import org.slf4j.LoggerFactory
import spire.implicits._
import spire.math._

import java.util.{Date, Properties}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.core.`type`.TypeReference

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

  def process(z: Complex[Double], c: Complex[Double], iterations: Int): Int = {
    if (iterations < 1) {
      return -1
    }

    val M: Complex[Double] =
      (z pow 2) + c

    if (M.abs > 4.0) {
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
            new Complex[Double](payload("r").toDouble, payload("i").toDouble)

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
              "computeDateStamp" -> payload("computeDateStamp"),
              "topic" -> payload("topic")
            )
          )

          // ("""([0-9.-]+)\s*\+\s*([0-9.-]+)i;([0-9]+);(.*?)""".r)
          //   .findAllIn(record.value)
          //   .matchData foreach { m =>
          //   {
          //     val z =
          //       new Complex[Double](m.group(1).toDouble, m.group(2).toDouble)

          //     val res = process(
          //       z,
          //       z,
          //       m.group(3).toInt
          //     )

          //     val r = m.group(1)
          //     val i = m.group(2)
          //     val uuid = m.group(4)
          //     val datestamp =
          //       (new Date()).toString() //todo make the datastamp here nicer

          //     val data: Map[String, String] = Map(
          //       "value" -> res.toString(),
          //       "uuid" -> uuid,
          //       "r" -> r,
          //       "i" -> i,
          //       "computeDateStamp" -> datestamp,
          //       "topic" -> topic
          //     )

          //     DataWriter.doWork(resultProducer, "writeData", s"$r:$i", data)
          //   }
          // }
        })
      }
    }
  }

}
