package example

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentCaptor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.mockito.hamcrest.MockitoHamcrest
import scala.concurrent.Future
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.clients.Metadata

class ProducerSpec extends AnyFlatSpec with Matchers {
  "The Producer object" should "be able to evenly distribute work to nodes" in {
    val topic = "test"  
    val iterations = 100

    val kafkaProducer = Mockito.mock(classOf[KafkaProducer[String, String]])
    // val mockFuture = Mockito.mock(classOf[java.util.concurrent.Future.type])
    //Mockito.doReturn(mockFuture).when(kafkaProducer).send(ArgumentMatchers.any())
    // Mockito
    //   .when(kafkaProducer.send(ArgumentMatchers.any()))
    //   .thenReturn(mockFuture)

    Producer.produceGridPoints(topic, 10, kafkaProducer)
  }
}
