package uk.ac.bbk.mongoycsb;

import com.mongodb.MongoClientURI;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.MongoClientOptions.Builder;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
//import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;
import com.mongodb.AutoEncryptionSettings;
import com.mongodb.BulkWriteOperation;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.InsertOptions;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.yahoo.ycsb.ByteIterator;
//import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.db.MongoDbClient;
import com.yahoo.ycsb.db.OptionsSupport;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
//import org.bson.conversions.Bson;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

class UuidUtils {
    public static UUID asUuid(byte[] bytes) {
      ByteBuffer bb = ByteBuffer.wrap(bytes);
      long firstLong = bb.getLong();
      long secondLong = bb.getLong();
      return new UUID(firstLong, secondLong);
    }

    public static byte[] asBytes(UUID uuid) {
      ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
      return bb.array();
    }
  }

public class ShardedMongo extends MongoDbClient {
	
	/** The database name to access. */
	//private static MongoDatabase database;

    /** Used to include a field in a response. */
    protected static final Integer INCLUDE = Integer.valueOf(1);

    /** A singleton MongoClient instance. */
    private static MongoClient[] mongo;

    private static com.mongodb.DB[] db;

    private static int serverCounter = 0;

    /** The default write concern for the test. */
    private static WriteConcern writeConcern;

    /** The default read preference for the test */
    private static ReadPreference readPreference;

    /** Allow inserting batches to save time during load */
    private static Integer BATCHSIZE;
    private List<DBObject> insertList = new ArrayList<DBObject>();
    private Integer insertCount = 0;
    private BulkWriteOperation bulkWriteOperation = null;

    /** Credentials */
    private static String username;
    private static String password;

    /** Count the number of times initialized to teardown on the last {@link #cleanup()}. */
    private static final AtomicInteger initCount = new AtomicInteger(0);

    private static InsertOptions io = new InsertOptions().continueOnError(true);

    /** Measure of how compressible the data is, compressibility=10 means the data can compress tenfold.
     *  The default is 1, which is uncompressible */
    private static float compressibility = (float) 1.0;

    private static String datatype = "binData";

    private static String algorithm = "AEAD_AES_256_CBC_HMAC_SHA_512-Random";
	
	/********/
	
    Map<String, Boolean> sharded;

    /** The database name to access. */
    //private static MongoDatabase database;

    private static MongoDatabase adminDb;

    @Override
    public Status insert(String table, String key, HashMap<String, ByteIterator> values) {

        if (!sharded.containsKey(table) || sharded.get(table) == false) {
            // MongoCollection<Document> collection = db.getCollection(table);
            // database.runCommand(new Document("db.runCommand",sh.shardCollection'",
            // database.getName() + "." + table));

            // adminDb.runCommand(new Document("shardCollection", database.getName() + "." +
            // table));

            // System.out.println("shard key = " + key);

            // database.getCollection(table).createIndex(new Document("_id", 1));

            // adminDb.runCommand(new Document("enableSharding", database.getName()));

            // Document shardCommandParams = new Document();
            // shardCommandParams.append("shardCollection", database.getName() + "." +
            // table);
            // shardCommandParams.append("key", new Document(key, 1));
            // shardCommandParams.append("key", new Document("_id", "hashed"));

            // adminDb.runCommand(shardCommandParams);
            // sharded.put(table, true);
        }
       
        return super.insert(table, key, values);
    }
    
    {
    	try {
			MongoDbClient.class.getDeclaredField("database").setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }


    @Override
    public void init() throws DBException {
    	
    	super.init();
        sharded = new HashMap<>();
        System.out.println("reached.11");
        String url = getProperties().getProperty("mongodb.url");
        
        
		Builder mongoClientOptions = MongoClientOptions.builder()
				.sslEnabled(true)
				.sslInvalidHostNameAllowed(true);
		

        MongoClientURI uri = new MongoClientURI(url, mongoClientOptions);

        //MongoCredential cred1 = MongoCredential.createCredential("CN=mongos-1-svc,OU=ExperimentServers,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=UK", "$external", "".toCharArray());
        MongoCredential cred1 = MongoCredential.createMongoX509Credential("CN=mongos-1-svc,OU=ExperimentServers,O=Roderick,O=BBK,L=Southmister,ST=Essex,C=UK");
        
        MongoClient mongoClient = new MongoClient(new ServerAddress("mongos-1-svc:27017"), Arrays.asList(cred1), mongoClientOptions.build()); 
        
        
        String dbName = uri.getDatabase();
        MongoDatabase database = mongoClient.getDatabase(dbName).withWriteConcern(uri.getOptions().getWriteConcern())
                .withReadPreference(uri.getOptions().getReadPreference());
        
        try {
        	Field f = MongoDbClient.class.getDeclaredField("database");
        	f.setAccessible(true);
			f.set(this, database);
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

        try {
            adminDb = mongoClient.getDatabase("admin");
            
//            Document creds = new Document("username", "CN=mongos-1-svc,OU=ExperimentServers,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=UK");
//            creds.append("pwd", "");
//           
//            Document authDoc = new Document("auth", creds);
//            System.out.println(authDoc);
//            mongoClient.getDatabase("$external").runCommand(authDoc);
            System.out.println("---DEBUG---");
            System.out.println(mongoClient.getCredentialsList());
            adminDb.runCommand(new Document("enableSharding", database.getName()));
            System.out.println("*****");
            System.out.println(mongoClient.getCredentialsList());
            System.out.println("HERE 3");

            Document shardCommandParams = new Document();
            shardCommandParams.append("shardCollection", database.getName() + ".usertable");
            // shardCommandParams.append("key", new Document(key, 1));
            shardCommandParams.append("key", new Document("_id", "hashed"));

            adminDb.runCommand(shardCommandParams);
            
        } catch (MongoException me) {
        	System.err.println("MongoException: " + me);
     	} catch (IllegalArgumentException e) {

        }

        sharded = new HashMap<>();
        
    }
       
}
