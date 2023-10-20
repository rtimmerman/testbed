package example
import java.util.Properties
import java.net.URLClassLoader
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.io.File
import java.io.FileInputStream
import org.apache.kafka.clients.producer.ProducerRecord
import java.io.FileOutputStream
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer

@main def mandelbrot(role: String, args: String*): Unit =
  val topic = role match {
    case "producer"             => args(0)
    case "consumer"             => args(0)
    case "data-writer-consumer" => args(0)
    case _                      => "test"
  }

  val iterations = role match {
    case "producer" => args(1).toInt
    case _ => 0
  }

  if (role.equals("producer"))
    println(f"<< PRODUCER >> submitting to topic: \"$topic\", # iterations = $iterations")
    Producer.produceGridPoints(topic, iterations)
  else if (role.equals("consumer"))
    println(f"<< CONSUMER >> listening to \"$topic\"")
    Consumer.consume(topic)
  else if (role.equals("data-writer-consumer"))
    println(f"<< DATA-WRITER >> listnening to \"$topic\"")
    DataWriter.consume(topic)

enum Role(val name: String):
  case Consumer extends Role("Consumer")
  case Producer extends Role("Producer")
  case DataWriter extends Role("DataWriter")

enum JarState:
  case Fresh, Stale

class StaleJarException extends Exception

/**
  * Sets up provisions for live loading jars with new workflows within this framework
  *
  * @param role
  * @param kafkaGroupId default is a different group per instantiation to allow for multiple consumers to read from one source of truth for jar updates.
  */
class Activity(var role: Role, var kafkaGroupId: String = UUID.randomUUID().toString()):
  val logger = LoggerFactory.getLogger(classOf[Activity].getName)
  def kafkaProducer[K, V](): KafkaProducer[K, V] = 
    val producerProps = new Properties()
    
    producerProps.put("bootstrap.servers", "kafka-topic-server:9092")  
    producerProps.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    producerProps.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.BytesSerializer"
    )
    producerProps.put(
      "transactional.id",
      "transaction-id-1"
    )

    return new KafkaProducer[K, V](producerProps)

  def kafkaConsumer(): KafkaConsumer[String, Array[Byte]] =
    val consumerProps = new Properties()
    consumerProps.put("bootstrap.servers", "kafka-topic-server:9092")
    consumerProps.put(
      "key.deserializer",
      "org.apache.kafka.common.serialization.StringDeserializer"
    )
    consumerProps.put(
      "value.deserializer",
      "org.apache.kafka.common.serialization.BytesDeserializer"
    )
    consumerProps.put(
      "group.id",
      kafkaGroupId
    )

    val topic = "hotload"
    consumerProps.put(
      "topic",
      topic
    )

    val consumer = new KafkaConsumer[String, Array[Byte]](consumerProps);
    var topics = new java.util.ArrayList[String]()
    topics.add(consumerProps.get("topic").asInstanceOf[String])

    consumer.subscribe(topics)

    return consumer

  def uploadJar(classJarPackage: String) = 
    val file = new File(classJarPackage)
    val fileInputStream = new FileInputStream(file)
    var bytes: Array[Byte] = null
    fileInputStream.read(bytes)

    val producer = kafkaProducer[String, Array[Byte]]()
    producer.initTransactions()
    producer.beginTransaction()
    producer.send(new ProducerRecord[String, Array[Byte]]("loadjar", "bytes", bytes))
    producer.commitTransaction()

    val sysProducer = kafkaProducer[String, String]()
    sysProducer.beginTransaction()
    sysProducer.send(new ProducerRecord[String, String]("system", "stop", ""))
    sysProducer.commitTransaction()

  def consumeJar(): Unit = 
    val consumer = kafkaConsumer()
    given ExecutionContext = ExecutionContext.global

    var workers: ListBuffer[Future[Unit]] = new ListBuffer[Future[Unit]]()
    
    while (true) {
      val work = consumer.poll(1000)
      // save file
      work.forEach(record => {
        (new FileOutputStream(new File("./out.jar"))).write(record.value())
      })
      
      // replace the operating instances of the base Consumer class.

      if (this.role == Role.Consumer) {
        try {
          val clazz = this.classLoader("./out.jar").loadClass("Consumer")
          val workConsumer = clazz.getDeclaredConstructor().newInstance()
          workers += Future {
            clazz.getDeclaredMethod("consume").invoke(workConsumer, "loadjar")
          }
        } catch {
          case e: Exception => println("Encountered issue setting up consumer: " + e.getMessage())
        }
      }

      if (this.role == Role.Producer) {
        try {
          val clazz = this.classLoader("./out.jar").loadClass("Producer")
          val workProducer = clazz.getDeclaredConstructor().newInstance()
          workers += Future {
            clazz.getDeclaredMethod("produceGridPoints").invoke(workProducer, "loadjar")
          }
        } catch {
          case e: Exception => println("Encountered issue setting up consumer: " + e.getMessage())
        }
      }

      if (this.role == Role.DataWriter) {
        try {
          val clazz = this.classLoader("./out.jar").loadClass("DataWriter")
          val workDataWriterConsumer = clazz.getDeclaredConstructor().newInstance()
          workers += Future {
            clazz.getDeclaredMethod("consume").invoke(workDataWriterConsumer, "loadjar")
          }
        } catch {
          case e: Exception => println("Encountered issue setting up consumer: " + e.getMessage())
        }
      }
    }

  def classLoader(classJarPackage: String): URLClassLoader = 
    new URLClassLoader(Array(new java.io.File(classJarPackage).toURI.toURL), this.getClass().getClassLoader())