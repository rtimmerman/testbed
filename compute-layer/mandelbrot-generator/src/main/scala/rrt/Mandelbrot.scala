package rrt
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
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.collection.mutable.ListBuffer
import io.prometheus.client.exporter.HTTPServer

given ExecutionContext = ExecutionContext.global
@main def mandelbrot(role: String, args: String*): Unit =
  val topic = role match {
    case "consumer"             => args(0)
    case "data-writer-consumer" => args(0)
    case _                      => "test"
  }

  val frameConfigFile = role match {
    case "producer" => args(0)
    case _ => ""
  }

  if (role.equals("producer"))
    println(f"<< PRODUCER >> starting producer from config: \"$frameConfigFile\"")
    val activity = new Activity(Role.Producer, UUID.randomUUID().toString())
    // Future {
    //   Thread.sleep(10000)
    //   activity.uploadJar("./target/scala-3.3.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar")
    // }
    activity.producer("./target/scala-3.5.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar", frameConfigFile)
    //Producer.produceGridPoints(topic, iterations)
  else if (role.equals("consumer"))
    println(f"<< CONSUMER >> listening to \"$topic\"")
    println("Attempting to bring up metrics server...")
    val statsServer = HTTPServer.Builder()
        .withPort(9180)
        //.withDaemonThreads(true)
        .build()
    println(s"Metrics Server is up on port ${statsServer.getPort()}")

    val activity = new Activity(Role.Consumer, UUID.randomUUID().toString())
    Future {
      Thread.sleep(30000)
      activity.uploadJar("./target/scala-3.5.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar")
    }

    println("Waiting for work...")

    activity.consumeJar(topic)
    statsServer.close()
  else if (role.equals("data-writer-consumer"))
    println(f"<< DATA-WRITER >> listnening to \"$topic\"")
    val activity = new Activity(Role.DataWriter, UUID.randomUUID().toString())
    activity.consumeJar(topic)
    //DataWriter.consume(topic)
  else if (role.equals("console"))
      println("<< CONSOLE >>")
      val activity = new Activity(Role.Console)
      val command: String = null
      val args: Seq[String] = null
      var running = true
      while(running)
        print("[console]: ")
        Console.out.flush()
        val instruction = scala.io.StdIn.readLine()
        if (instruction != null && !instruction.isEmpty)
          val List(command, args*) = instruction.split(" ").toList: @unchecked
          Console.out.flush()
          if (command.equals("system-stop")) activity.stopSystem()
          if (command.equals("quit")) running = false
          command match
            case "system:stop" => activity.stopSystem()
            case "jar:upload" => activity.uploadJar(args(0))
            case "jar:consume" => activity.consumeJar(args:_*)
            case "producer" => activity.producer(args(0), args(1))
            case "quit" => running = false
            case "help" => println("""
╭───────────────────╮
│ Avaiable Commands │
╰───────────────────╯

system:stop         - Sends stop signal to all listening-loop consumers
jar:upload          - Uploads new class packages to the listening-loop consumers (note the package may built using `sbt package`)
jar:consume [args]  - Boots using a previously uploaded jar (n.b. be sure to run `system:stop`first)
quit                - Exit the console
help
            """)
            case _ => {}

enum Role(val name: String):
  case Consumer extends Role("Consumer")
  case Producer extends Role("Producer")
  case DataWriter extends Role("DataWriter")
  case Console extends Role("Console")

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
  
  def kafkaProducer(transactionId: String): KafkaProducer[String, Array[Byte]] = 
    val producerProps = new Properties()
    
    producerProps.put("bootstrap.servers", "kafka-topic-server:9092")  
    producerProps.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    producerProps.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.ByteArraySerializer"
    )
    producerProps.put(
      "retries", "10"
    )
    producerProps.put(
      "retry.backoff.ms", "10000"
    )
    producerProps.put(
      "transactional.id",
      transactionId
    )

    return new KafkaProducer[String, Array[Byte]](producerProps)


  def kafkaSysProducer(transactionId: String): KafkaProducer[String, String] = 
    val producerProps = new Properties()
    
    producerProps.put("bootstrap.servers", "kafka-topic-server:9092")  
    producerProps.put(
      "key.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    producerProps.put(
      "value.serializer",
      "org.apache.kafka.common.serialization.StringSerializer"
    )
    producerProps.put(
      "transactional.id",
      transactionId
    )

    return new KafkaProducer[String, String](producerProps)
  

  def kafkaConsumer(): KafkaConsumer[String, Array[Byte]] =
    val consumerProps = new Properties()
    consumerProps.put("bootstrap.servers", "kafka-topic-server:9092")
    consumerProps.put(
      "key.deserializer",
      "org.apache.kafka.common.serialization.StringDeserializer"
    )
    consumerProps.put(
      "value.deserializer",
      "org.apache.kafka.common.serialization.ByteArrayDeserializer"
    )
    consumerProps.put(
      "group.id",
       kafkaGroupId
    )

    val topic = "loadjar"

    val consumer = new KafkaConsumer[String, Array[Byte]](consumerProps);
    var topics = new java.util.ArrayList[String]()
    topics.add(topic)

    consumer.subscribe(topics)

    return consumer

  def stopSystem(): Unit = 
    val sysProducer = kafkaSysProducer("transaction-id-2")
    try {
      sysProducer.initTransactions()
      sysProducer.beginTransaction()
      val f = sysProducer.send(new ProducerRecord[String, String]("system", SystemMessage.STOP.getMessage(), SystemMessage.STOP.getMessage()))
      sysProducer.commitTransaction()
      sysProducer.close()
    } catch {
      case e: Exception => println(s"Exception encountered: ${e.getClass()} ${e.getMessage()}")
      sysProducer.abortTransaction()
      sysProducer.close()
    }

  def uploadJar(classJarPackage: String) = 
    val file = new File(classJarPackage)
    val fileInputStream = new FileInputStream(file)
    var bytes: Array[Byte] = fileInputStream.readAllBytes()

    val producer = kafkaProducer("transaction-id-a")
    producer.initTransactions()
    producer.beginTransaction()
    producer.send(new ProducerRecord[String, Array[Byte]]("loadjar", "bytes", bytes))
    producer.commitTransaction()
    producer.close()

  def consumeJar(params: String*): Unit = 
    val consumer = kafkaConsumer()
    //given ExecutionContext = ExecutionContext.global

    //var workers: ListBuffer[Future[Unit]] = new ListBuffer[Future[Unit]]()
    
    while (true) {
      val work = consumer.poll(1000)

      if (!work.isEmpty())
        // save file
        logger.info(s"Work available")
        work.forEach(record => {
          (new FileOutputStream(new File("./out.jar"))).write(record.value())
        })  
        
        // replace the operating instances of the base Consumer class.
        

        if (role == Role.Consumer) {
          try {
            val clazz = this.classLoader("./out.jar").loadClass("rrt.Consumer")
            //val workConsumer = clazz.getDeclaredConstructor().newInstance()
            clazz.getDeclaredMethod("consume", classOf[String]).invoke(null, params(0))
          } catch {
            case e: Exception => println(s"Encountered issue setting up consumer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
          }
        }

        if (role == Role.Producer) {
          try {
            //val workProducer = clazz.getDeclaredConstructor().newInstance()    
            val clazz = this.classLoader("./out.jar").loadClass("rrt.Producer")
            clazz.getDeclaredMethod("produceGridPoints", classOf[String], classOf[KafkaProducer[String,String]]).invoke(null, params(0), null)
          } catch {
            case e: Exception => println(s"Encountered issue setting up producer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
          }
        }

        if (role == Role.DataWriter) {
          try {
            val clazz = this.classLoader("./out.jar").loadClass("rrt.DataWriter")
            clazz.getDeclaredMethod("consume", classOf[String]).invoke(null, params(0))
          } catch {
            case e: Exception => println(s"Encountered issue setting up data writer consumer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
          }
        }
    }

  def producer(jarfile: String, frameConfigFile: String, args: String*): Unit =
     val clazz = this.classLoader(jarfile).loadClass("rrt.Producer")
     clazz.getDeclaredMethod("produceGridPoints", classOf[String], classOf[KafkaProducer[String,String]]).invoke(null, frameConfigFile, null)

  def classLoader(classJarPackage: String): URLClassLoader = 
    new URLClassLoader(Array(new java.io.File(classJarPackage).toURI.toURL), this.getClass().getClassLoader())
