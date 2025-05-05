package rrt
import com.mongodb
import org.apache.kafka.clients.consumer._
import com.mongodb.client.model.UpdateOneModel
import org.slf4j.LoggerFactory

import java.security.{KeyStore, SecureRandom}
import java.util.{Date, Properties}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.core.`type`.TypeReference
import com.mongodb.client.model.mql.MqlDocument
import org.bson.Document
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoClient
import com.mongodb.MongoCredential
import com.mongodb.MongoClientSettings
import com.mongodb.ConnectionString
import com.mongodb.client.model.Filters
import scala.collection.View.Filter
import com.mongodb.client.model.Updates
import com.mongodb.client.model.InsertOneModel
import com.mongodb.client.MongoClients
import java.util.UUID

object DataWriter extends LoggingTrait, KafkaTrait {

  val run0db: MongoCollection[Document] =
    mongoDbClient().getDatabase("mandelbrot").getCollection("run0")

  val mapper = JsonMapper.builder().addModule(DefaultScalaModule).build()

  def doWork(
      resultProducer: KafkaProducer[String, String],
      operation: String,
      key: String,
      data: Map[String, String]
  ) = {
    val payload =
      mapper.writeValueAsString(
        Map(
          "metadata" -> Map("operation" -> operation),
          "data" -> data
        )
      )

    resultProducer.beginTransaction()

    resultProducer.send(
      new ProducerRecord[String, String](
        "result",
        key,
        payload
      )
    )

    resultProducer.commitTransaction()
  }

  def writeData(data: Map[String, String]) = {
    var opts = com.mongodb.client.model.UpdateOptions()
    opts.upsert(true)

    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global

    // Insert approach
    run0db.insertOne(
      new Document()
        .append("value", data("value"))
        .append("runUuid", data("uuid"))
        .append("fromTopic", data("topic"))
        .append("modifiedAt", new Date())
        .append("computeDateStamp", data("computeDateStamp"))
        .append("r", data("r"))
        .append("i", data("i"))
    )

    // Upsert approach
    // run0db
    //   .updateOne(
    //     Filters.and(Filters.eq("r", data("r")), Filters.eq("i", data("i"))),
    //     Updates.combine(
    //        Updates.set("value", data("value")),
    //        Updates.set("fromTopic", data("topic")),
    //        Updates.set("modifiedAt", new Date()),
    //        Updates.set("computeDateStamp", data("computeDateStamp")),
    //        Updates.setOnInsert("r", data("r")),
    //        Updates.setOnInsert("i", data("i"))
    //     ),
    //     opts
    //   )

    //Await.result(upsertFuture, 60.seconds)

    //upsertFuture onComplete {
      //case Success(out) =>
        logger.info(
          s"Entry << ${data("r")} | ${data("i")} | ${data("value")} >> upserted."
        )
      //case Failure(e) => logger.error(s"Upsert Failed ${e.getMessage()}")
  }
  

  def clearDb() = {
    val deleteFuture = run0db.deleteMany(Document())
  }

  def handleData(record: ConsumerRecord[String, String]) = {
    logger.debug(s"Received record data: ${record.value}")

    val payload: Map[String, Map[String, String]] = mapper.readValue(
      record.value,
      new TypeReference[Map[String, Map[String, String]]] {}
    )

    val data = payload("data")

    payload("metadata")("operation") match {
      case "writeData" => writeData(data);
      case "clearDb"   => clearDb();
    }
  }

  def consume(topic: String) = {
    val consumer = initConsumer(topic, f"data-writer-main-${UUID.randomUUID().toString()}")
    val sysConsumer = initConsumer("system", UUID.randomUUID().toString())

    implicit val ec =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(30))

    val runs = ListBuffer[Future[Unit]]();
    while (running) {
      val work = consumer.poll(1000)
    
      if (work != null) {
        runs.append(Future {
          work.records(topic).forEach(record => {
            handleData(record)
          })
        })
      }

      handleSystemMessages[String, String](sysConsumer)

      //val futures = Future.sequence(runs)
      //Await.result(futures, 4.days)
    }

  }

  def mongoDbClient(): MongoClient = {
    // connect to the mongodb instance

    println("attempting to connect")

    val uri =
      "mongodb://mongos-1-svc:27017/mandelbrot?authSource=$external&authMechanism=MONGODB-X509"

    val cred = MongoCredential.createMongoX509Credential(
      "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=localhost"
    )

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(
      new java.io.FileInputStream(
        sys.env.getOrElse("KEYSTORE_PATH", "missing_keystore")
        // "/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ks"
      ),
      "xiec.gate.r".toCharArray()
    )

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
    trustStore.load(
      new java.io.FileInputStream(
        sys.env.getOrElse("TRUSTSTORE_PATH", "missing_truststore")
        // "home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ts"
      ),
      "xiec.gate.r".toCharArray()
    )

    val keyManagerFactory =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, "xiec.gate.r".toCharArray())

    val trustManagerFactory =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(trustStore)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(
      keyManagerFactory.getKeyManagers(),
      trustManagerFactory.getTrustManagers(),
      new SecureRandom()
    )

    val dbSettings = MongoClientSettings
      .builder()
      .applyConnectionString(ConnectionString(uri))
      .applyToSslSettings((builder) => {
        builder.enabled(true).invalidHostNameAllowed(true).context(sslContext)
      })
      .credential(cred)
      .writeConcern(mongodb.WriteConcern.ACKNOWLEDGED)
      .build()

    MongoClients.create(dbSettings)
  }

}