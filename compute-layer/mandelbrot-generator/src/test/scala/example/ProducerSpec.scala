package example

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentCaptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer

class ProducerSpec extends AnyFlatSpec with Matchers {
  "The Producer object" should "be able to evenly distribute work to nodes" in {
    val topic = "test"

    val kafkaProducer = Mockito.mock(classOf[KafkaProducer[String, String]])
    Mockito.when()

    val producer = Mockito.mock(classOf[Producer])

    Mockito.when(producer.getKafkaProducer(ArgumentMatchers.any())).thenReturn(kafkaProducer)

    producer.produceGridPoints(topic, 10)
  }
}
