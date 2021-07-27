package example
import java.util.Properties
import org.apache.kafka.clients.producer._

object Producer {
  def gridWorkStream(kafkaProducer: KafkaProducer[String, String]) = {
    var i: Double = -2
    var r: Double = 0

    var step: Double = 0.1
    var passes: Int = 10

    (BigDecimal(-2.0) to BigDecimal(2.0) by step).toList.foreach(i => {
      (BigDecimal(-2.0) to BigDecimal(2.0) by step).toList.foreach(r => {
        kafkaProducer.send(
          new ProducerRecord[String, String](
            "test",
            "coordinate",
            s"${r}+${i}i"
          )
        )
      })
    })
  }

  def produceGridPoints() = {
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
    val producer = new KafkaProducer[String, String](props)
    //val record =
    //  new ProducerRecord[String, String]("test", "test-key", "test value")
    gridWorkStream(producer)
    producer.close()

  }
}
