import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.SecureRandom
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.ConnectionString
import com.mongodb
import com.mongodb.MongoCredential
import org.bson.Document
import java.security.KeyManagementException

class DataWriterSpec extends AnyFlatSpec with Matchers {
    "Key and Trust stores" should "be loadable" in {
        val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(
            new java.io.FileInputStream(
                //sys.env.getOrElse("KEYSTORE_PATH", "missing_keystore")
                "/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ks"
            ),
            "xiec.gate.r".toCharArray()
        )

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "xiec.gate.r".toCharArray())

        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustStore.load(
            new java.io.FileInputStream(
                //sys.env.getOrElse("TRUSTSTORE_PATH", "missing_truststore")
                "/home/roderick/workspace/mongo-cluster/compute-layer/mandelbrot-generator/test.ts"
            ),
            "xiec.gate.r".toCharArray()
        )

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            new SecureRandom()
        )

        val cred = MongoCredential.createMongoX509Credential(
         "CN=localhost,OU=ExperimentClients,O=Roderick,O=Outside,L=Southminster,ST=Essex,C=GB"
        )

        val uri = "mongodb://rrt-general-services.dcs.bbk.ac.uk:30028/mandelbrot?authSource=$external&authMechanism=MONGODB-X509"

        val dbSettings = MongoClientSettings
            .builder()
            .applyConnectionString(ConnectionString(uri))
            .applyToSslSettings((builder) => {
                builder.enabled(true).invalidHostNameAllowed(true).context(sslContext)
            })
            .credential(cred)
            .writeConcern(mongodb.WriteConcern.ACKNOWLEDGED)
            .build()

        val client = MongoClients.create(dbSettings)
        try {
            val collection = client.getDatabase("mandelbrot").getCollection("run0")
            val res = collection.find(new Document())
            println(res.first().get('r'))
        } catch {
            case e: KeyManagementException => println(f"Key Management Error ${e.getMessage()}")
        }
    }
}
