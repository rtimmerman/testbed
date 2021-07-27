package example
import java.util.Properties
import org.apache.kafka.clients.consumer._
import spire.math._
import spire.implicits._
import spire.algebra._

object Consumer {

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

  def consume() = {
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
      "test"
    )

    val consumer = new KafkaConsumer[String, String](props);
    var topics = new java.util.ArrayList[String]()
    topics.add("test")

    consumer.subscribe(topics)
    while (true) {
      val work = consumer.poll(1000)
      if (work != null)
        work.forEach(record => {
          //println(record.key() + " = " + record.value())

          ("""([0-9.-]+)\+([0-9.-]+)""".r)
            .findAllIn(record.value)
            .matchData foreach { m =>
            {
              val z =
                new Complex[Double](m.group(1).toDouble, m.group(2).toDouble)

              val res = process(z, z, 10)
              if (m.group(1).toDouble >= 2.0)
                println(if (res > -1) "." else " ")
              else
                print(if (res > -1) "." else " ")
            }
          }
        })
    }
  }
}
