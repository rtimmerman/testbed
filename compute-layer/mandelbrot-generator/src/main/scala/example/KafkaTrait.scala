package example

import java.util.UUID
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.Properties
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

trait KafkaTrait extends LoggingTrait:
    var running: Boolean = true;

    def initConsumer(topic: String, groupId: String): KafkaConsumer[String, String] = {
        val props = new Properties
        props.put("bootstrap.servers", "kafka-topic-server:9092")
        props.put(
            "key.deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer"
        )
        props.put(
            "value.deserializer",
            "org.apache.kafka.common.serialization.StringDeserializer"
        )
        props.put("group.id", groupId)
        var topics = new java.util.ArrayList[String]
        topics.add(topic)
        val consumer = KafkaConsumer[String, String](props)
        consumer.subscribe(topics)

        return consumer
    }

    def initProducer(transactionId: String): KafkaProducer[String, String] = {
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
        dataOutProps.put("transactional.id", transactionId)

        dataOutProps.put("retries", "10")
        dataOutProps.put("retry.backoff.ms", "5000")

        return new KafkaProducer[String, String](dataOutProps)
    }

    def handleSystemMessages[K, V](sysConsumer: KafkaConsumer[K, V]) = {
      val sysWork = sysConsumer.poll(100)
      //logger.info("system tick")
      if (sysWork != null) {
        //logger.info(s"Received work, no. of items=${sysWork.count()}")
        sysWork.forEach(record => {
            //logger.info(s"Record: ${record.key()}:${record.value()}")
            if (record.key() == SystemMessage.STOP.getMessage()) {
              logger.warn("Received system level request to halt operations.")
              running = false
            }
        })
      }
    }