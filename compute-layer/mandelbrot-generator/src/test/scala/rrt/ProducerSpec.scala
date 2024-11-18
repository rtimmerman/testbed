package rrt

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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory


class ProducerSpec extends AnyFlatSpec with Matchers {
  "The Producer object" should "be able to evenly distribute work to nodes" in {
    // val topic = "test"  
    // val iterations = 100

    // val kafkaProducer = Mockito.mock(classOf[KafkaProducer[String, String]])
    // // val mockFuture = Mockito.mock(classOf[java.util.concurrent.Future.type])
    // //Mockito.doReturn(mockFuture).when(kafkaProducer).send(ArgumentMatchers.any())
    // // Mockito
    // //   .when(kafkaProducer.send(ArgumentMatchers.any()))
    // //   .thenReturn(mockFuture)
    // println("here")

    // Producer.produceGridPoints(topic, 10, kafkaProducer)
  }

  "A Producer instance" should "be able to support technique for a yaml workload file ingestion" in {
    var mapper = new ObjectMapper(new YAMLFactory())
    val frameConfigFile = "work-template.yml"
    var params = mapper.readValue(new java.io.File(frameConfigFile), classOf[ProducerParams]): ProducerParams
    println(params.topicPrefix)
  }
}
