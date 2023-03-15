package example
import com.mongodb
import org.apache.kafka.clients.consumer._
import org.mongodb.scala._
import org.mongodb.scala.model.UpdateOneModel
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

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.core.`type`.TypeReference

object DataWriter {
  val logger = LoggerFactory.getLogger(DataWriter.getClass().getName())

  val run0db: MongoCollection[Document] =
    mongoDbClient().getDatabase("mandelbrot").getCollection("run0")

  val mapper = JsonMapper.builder().addModule(DefaultScalaModule).build()

  def writeData(data: Map[String, String]) {
    val upsertDocument = Document(
      "$set" -> Document(
        "value" -> data("value"),
        "fromTopic" -> data("topic"),
        "modifiedAt" -> new Date(),
        "runUuid" -> data("uuid"),
        "computeDateStamp" -> data("computeDatestamp")
      ),
      "$setOnInsert" -> Document(
        "r" -> data("r"),
        "i" -> data("i")
      )
    )

    val opts = org.mongodb.scala.model.UpdateOptions()
    opts.upsert(true)

    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global
    val upsertFuture = run0db
      .updateOne(
        Document("r" -> data("r"), "i" -> data("i")),
        upsertDocument,
        opts
      )
      .toFuture()

    Await.result(upsertFuture, 60.seconds)

    upsertFuture onComplete {
      case Success(out) =>
        logger.info(
          s"Entry << ${data("r")} | ${data("i")} | ${data("value")} >> upserted."
        )
      case Failure(e) => logger.error(s"Upsert Failed ${e.getMessage()}")
    }
  }

  def clearDb() {
    val deleteFuture = run0db.deleteMany(Document())
  }

  def handleData(record: ConsumerRecord[String, String]) {
    //"a=1;b=2;c=d;d=4".split(";").toList.map(v => {v.split("=").toList}).collect {case List(a: String, b: String) => (a, b)}.toMap
    //"[-]?[0-9.]+".r

    logger.debug(s"Received record data: ${record.value}")
    // todo: move datawriter logic to own function, add support for data clean-up (e.g. before tests run)

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
    props.put("group.id", "0")
    props.put("topic", topic)

    var topics = new java.util.ArrayList[String]()
    topics.add(props.get("topic").asInstanceOf[String])

    val consumer = new KafkaConsumer[String, String](props);
    consumer.subscribe(topics)

    implicit val ec =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

    while (true) {
      val work = consumer.poll(1000)

      if (work != null) {
        val writes = ListBuffer[UpdateOneModel[Nothing]]()
        val insert = mutable.Queue[UpdateOneModel[Nothing]]()

        val runs = ListBuffer[Future[Unit]]();
        for (i <- Range(0, 10)) {
          runs.append(Future {
            if (work.records(topic).iterator().hasNext())
              handleData(work.records(topic).iterator().next())
          })
        }

        val futures = Future.sequence(runs)

        // val f1 = Future {
        //   if (work.records(topic).iterator().hasNext())
        //     handleData(work.records(topic).iterator().next())
        // }

        // val f2 = Future {
        //   if (work.records(topic).iterator().hasNext())
        //     handleData(work.records(topic).iterator().next())
        // }

        // val f3 = Future {
        //   if (work.records(topic).iterator().hasNext())
        //     handleData(work.records(topic).iterator().next())
        // }

        // val f4 = Future {
        //   if (work.records(topic).iterator().hasNext())
        //     handleData(work.records(topic).iterator().next())
        // }

        // val f5 = Future {
        //   if (work.records(topic).iterator().hasNext())
        //     handleData(work.records(topic).iterator().next())
        // }

        // val futures = Future.sequence(List(f1, f2, f3, f4, f5))
        Await.result(futures, 60.seconds)

        /*work.forEach(record => {
          val future = Future {
            writeData(record)
          }

          Await.ready(future, 60.seconds)
        })*/

      }
    }

  }

  def mongoDbClient(): MongoClient = {
    // connect to the mongodb instance

    println("attempting to connect")

    val uri =
      "mongodb://mongos-1-svc:27017/mandelbrot?authSource=$external&authMechanism=MONGODB-X509"

    val cred = MongoCredential.createMongoX509Credential(
      "CN=localhost,OU=ExperimentClients,O=Roderick,O=Outside,L=Southminster,ST=Essex,C=GB"
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

    MongoClient(dbSettings);
  }
}
