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
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.collection.mutable.ListBuffer
import io.prometheus.client.exporter.HTTPServer
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import java.net.URI
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.Logger

given ExecutionContext = ExecutionContext.global

def setupLogging() =
  var ctx = LogManager.getContext(false).asInstanceOf[LoggerContext]
  val logger = ctx.getLogger(classOf[Activity].getName)

  // var logConfigFile = File("src/main/resources/log4j2.xml")
  // var logConfig = ConfigurationFactory.getInstance().getConfiguration(null, null, logConfigFile.toURI())
  // logConfig.initialize()

  var layout = PatternLayout.newBuilder()
    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
    .build()

  var fileAppenderBuilder: FileAppender.Builder[?] = FileAppender.newBuilder()
  val mlogName = sys.env.getOrElse("MLOG_NAME", "/tmp/output")
  val mlogFilePath = sys.env.getOrElse("MLOG_FILE_PATH", "/tmp/output.log")
  fileAppenderBuilder.setName(mlogName)
  fileAppenderBuilder.withFileName(mlogFilePath)
  fileAppenderBuilder.withLayout(layout)
  fileAppenderBuilder.setConfiguration(ctx.getConfiguration())

  var fileAppender = fileAppenderBuilder.build()
  fileAppender.start()

  sys.addShutdownHook {
    println("Gracefully stopping the logs")
    LogManager.shutdown()
  }

  ctx.getConfiguration().getRootLogger().addAppender(fileAppender, null, null)
  ctx.updateLoggers()

  logger

@main def mandelbrot(role: String, args: String*): Unit =

  // val logger = LoggerFactory.getLogger(classOf[Activity].getName)
  val logger = setupLogging()

  val topic = role match {
    case "consumer"             => args(0)
    case "data-writer-consumer" => args(0)
    case _                      => "test"
  }

  val frameConfigFile = role match {
    case "producer" => args(0)
    case _ => ""
  }

  try
    run(role, topic, frameConfigFile, logger)
  catch
    case e: Exception => {
      logger.error(s"Unfortunately, an exception will halt execution: ${e.getClass.toString}; message: ${e.getMessage()}")
      logger.trace(
        e.getMessage()
        + "\n"
        + e.getStackTrace().map(_.toString).reduce((carry, s: String) => carry + s + "\n")
      )
    }
  
def run(role: String, topic: String, frameConfigFile: String, logger: Logger): Unit =
  if (role.equals("producer"))
    logger.info(f"<< PRODUCER >> starting producer from config: \"$frameConfigFile\"")
    val activity = new Activity(Role.Producer, UUID.randomUUID().toString(), logger)
    // Future {
    //   Thread.sleep(10000)
    //   activity.uploadJar("./target/scala-3.3.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar")
    // }
    activity.producer("./target/scala-3.5.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar", frameConfigFile)
    //Producer.produceGridPoints(topic, iterations)
  else if (role.equals("consumer"))
    logger.info(f"<< CONSUMER >> listening to \"$topic\"")
    logger.info("Attempting to bring up metrics server...")
    val statsServer = HTTPServer.Builder()
        .withPort(9180)
        //.withDaemonThreads(true)
        .build()
    logger.info(s"Metrics Server is up on port ${statsServer.getPort()}")

    val activity = new Activity(Role.Consumer, UUID.randomUUID().toString(), logger)
    Future {
      Thread.sleep(10000)
      activity.uploadJar("./target/scala-3.5.0/mandelbrot-generator_3-0.1.0-SNAPSHOT.jar")
    }

    logger.info("Waiting for work...")

    activity.consumeJar(topic)
    statsServer.close()
  else if (role.equals("data-writer-consumer"))
    logger.info(f"<< DATA-WRITER >> listnening to \"$topic\"")
    val activity = new Activity(Role.DataWriter, UUID.randomUUID().toString(), logger)
    activity.consumeJar(topic)
    //DataWriter.consume(topic)
  else if (role.equals("console"))
      logger.info("<< CONSOLE >>")
      val activity = new Activity(Role.Console, null, logger)
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
            case "jar:consume" => activity.consumeJar(args*)
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
class Activity(var role: Role, var kafkaGroupId: String = UUID.randomUUID().toString(), inLogger: Logger):

  // val logger = LoggerFactory.getLogger(classOf[Activity].getName)
  val logger = inLogger

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
    
    producerProps.put("bootstrap.servers", "PLAINTEXT://kafka-topic-server:9092")  
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
    consumerProps.put("bootstrap.servers", "PLAINTEXT://kafka-topic-server:9092")
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
      case e: Exception => logger.error(s"Exception encountered: ${e.getClass()} ${e.getMessage()}")
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
          (new FileOutputStream(new File("/tmp/out.jar"))).write(record.value())
        })  
        
        // replace the operating instances of the base Consumer class.
        

        if (role == Role.Consumer) {
          try {
            val clazz = this.classLoader("/tmp/out.jar").loadClass("rrt.Consumer")
            //val workConsumer = clazz.getDeclaredConstructor().newInstance()
            clazz.getDeclaredMethod("consume", classOf[String]).invoke(null, params(0))
          } catch {
            case e: Exception => 
              logger.error(s"<ERR> Caller encountered issue with the consumer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
              logger.error(s"<ERR> Stack trace: ")
              e.getStackTrace().foreach(t => logger.debug(s"<ERR> $t"))
              logger.error(s"<ERR> Caused by: ${e.getCause().getClass()}")
          }
        }

        if (role == Role.Producer) {
          try {
            //val workProducer = clazz.getDeclaredConstructor().newInstance()    
            val clazz = this.classLoader("/tmp/out.jar").loadClass("rrt.Producer")
            clazz.getDeclaredMethod("produceGridPoints", classOf[String], classOf[KafkaProducer[String,String]]).invoke(null, params(0), null)
          } catch {
            case e: Exception => logger.error(s"Encountered issue setting up producer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
          }
        }

        if (role == Role.DataWriter) {
          try {
            val clazz = this.classLoader("/tmp/out.jar").loadClass("rrt.DataWriter")
            clazz.getDeclaredMethod("consume", classOf[String]).invoke(null, params(0))
          } catch {
            case e: Exception => logger.error(s"Encountered issue setting up data writer consumer: <<${e.getClass().getName()} -> ${e.getMessage()}>>")
          }
        }
    }

  def producer(jarfile: String, frameConfigFile: String, args: String*): Unit =
     val clazz = this.classLoader(jarfile).loadClass("rrt.Producer")
     clazz.getDeclaredMethod("produceGridPoints", classOf[String], classOf[KafkaProducer[String,String]]).invoke(null, frameConfigFile, null)

  def classLoader(classJarPackage: String): URLClassLoader = 
    new URLClassLoader(Array(new java.io.File(classJarPackage).toURI.toURL), this.getClass().getClassLoader()
)