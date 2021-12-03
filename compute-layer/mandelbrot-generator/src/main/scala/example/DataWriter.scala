package example
import com.mongodb
import org.apache.kafka.clients.consumer._
import org.slf4j.LoggerFactory

import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.mongodb.scala._
import org.mongodb.scala.model.{UpdateOneModel, UpdateOptions}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

import java.util.Properties


object DataWriter {
  val logger = LoggerFactory.getLogger(DataWriter.getClass().getName())

  val run0db: MongoCollection[Document] = mongoDbClient().getDatabase("mandelbrot").getCollection("run0")

  def writeData(record: ConsumerRecord[String, String]) {
    //"a=1;b=2;c=d;d=4".split(";").toList.map(v => {v.split("=").toList}).collect {case List(a: String, b: String) => (a, b)}.toMap
    val keyComponents = ("([0-9.-]+)".r).findAllIn(record.key).matchData.toArray
    val valueComponents = record.value.split(";").toList.map(v => { v.split("=").toList }).collect {case List(k: String, v: String) => (k, v)}.toMap

    val upsertDocument = Document(
      "$set" -> Document(
        "r" -> "", //todo fix the key components
        "i" -> "",
        "value" -> valueComponents.get("value"),
        "fromTopic" -> valueComponents.get("topic"),
        "modifiedAt" -> "",
        "uuid" -> valueComponents.get("uuid")
      )
    )

    Await.result(run0db.updateOne(
      Document("r" -> "", "i" -> ""),
      upsertDocument
    ).toFuture(), Duration.Inf)
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

    while (true) {
      val work = consumer.poll(1000)

      if (work != null) {
        val writes = ListBuffer[UpdateOneModel[Nothing]]()
        val insert = mutable.Queue[UpdateOneModel[Nothing]]()

        work.forEach(record => {
          writeData(record)
        })
      }
    }
  }

  def mongoDbClient(): MongoClient = {
    // connect to the mongodb instance

    println("attempting to connect")

    val uri =
      "mongodb://mongos-1-svc:27017/mandelbrot?authenticationDatabase=$external&authMechanism=MONGODB-X509"

    val cred = MongoCredential.createMongoX509Credential(
      "CN=localhost,OU=ExperimentClients,O=Roderick,O=Outside,L=Southmister,ST=Essex,C=UK"
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
