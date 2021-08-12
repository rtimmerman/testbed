package example
import java.util.Properties
import org.apache.kafka.clients.consumer._
import spire.math._
import spire.implicits._
import spire.algebra._
import org.mongodb.scala._
import org.bson.BsonValue
import com.mongodb
import mongodb.client.result._
import javax.net.ssl.SSLContext
import java.security.KeyFactory
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import java.security.SecureRandom

object Consumer {

  var mongoDbClient: MongoClient = null

  def process(z: Complex[Double], c: Complex[Double], iterations: Int): Int = {
    if (iterations < 1) {
      return -1
    }

    val M: Complex[Double] =
      (z pow 2) + c

    if (M.abs > 4.0) {
      return iterations
    }

    process(M, c, iterations - 1)
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
    props.put(
      "group.id",
      "0"
    )

    props.put(
      "topic",
      topic
    )

    println(s"Consuming from ${topic}")

    val consumer = new KafkaConsumer[String, String](props);
    var topics = new java.util.ArrayList[String]()
    topics.add(props.get("topic").asInstanceOf[String])

    consumer.subscribe(topics)

    val run0db = mongoDbClient.getDatabase("mandelbrot").getCollection("run0")

    while (true) {
      val work = consumer.poll(1000)
      if (work != null)
        work.forEach(record => {
          //println(record.key() + " = " + record.value())
          ("""([0-9.-]+)\s*\+\s*([0-9.-]+)""".r)
            .findAllIn(record.value)
            .matchData foreach { m =>
            {
              val z =
                new Complex[Double](m.group(1).toDouble, m.group(2).toDouble)

              val res = process(z, z, 10)

              val outcome = mongoDbClient
                .getDatabase("mandelbrot")
                .getCollection("run0")
                .insertOne(
                  Document("r" -> m.group(1), "i" -> m.group(2), "value" -> res)
                )

              outcome.subscribe(
                (e: Throwable) => {
                  throw e
                },
                () => {}
              )

              /*if (m.group(1).toDouble >= 2.0)
                println(if (res > -1) "." else " ")
              else
                print(if (res > -1) "." else " ")*/
              // println(res)
            }
          }
        })
    }
  }

  def getMongoDbClient(): MongoClient = {
    // connect to the mongodb instance

    if (mongoDbClient != null) {
      return mongoDbClient
    }

    println("attempting to connect")

    val uri =
      "mongodb://mongos-1-svc:27017/mandelbrot?authenticationDatabase=$external&authMechanism=MONGODB-X509"

    val cred = MongoCredential.createMongoX509Credential(
      "CN=localhost,OU=ExperimentClients,O=Roderick,O=Outside,L=Southmister,ST=Essex,C=UK"
    )

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(
      new java.io.FileInputStream(
        "/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ks"
      ),
      "xiec.gate.r".toCharArray()
    )

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
    trustStore.load(
      new java.io.FileInputStream(
        "/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ts"
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

    mongoDbClient = MongoClient(dbSettings);

    mongoDbClient

    /*println(db.listDatabaseNames().foreach(println));
    val dataset = Document("r" -> -2, "i" -> -2, "value" -> 3)

    val outcome =
      db.getDatabase("mandelbrot").getCollection("run0").insertOne(dataset)

    outcome
      .collect()
      .subscribe(
        (e: Throwable) => { println("error: " + e + ", " + e.getCause()) },
        () => { println("Complete") }
      )*/
  }

  mongoDbClient = getMongoDbClient()
}
