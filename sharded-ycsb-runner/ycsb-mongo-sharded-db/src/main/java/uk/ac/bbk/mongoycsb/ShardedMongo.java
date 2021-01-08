package uk.ac.bbk.mongoycsb;

import com.mongodb.MongoClientURI;
//import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.yahoo.ycsb.ByteIterator;
//import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.db.MongoDbClient;

//import org.bson.conversions.Bson;
import org.bson.Document;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ShardedMongo extends MongoDbClient {

    Map<String, Boolean> sharded;

    /** The database name to access. */
    private static MongoDatabase database;

    private static MongoDatabase adminDb;

    @Override
    public Status insert(String table, String key, HashMap<String, ByteIterator> values) {

        if (!sharded.containsKey(table) || !sharded.get(table)) {
            // MongoCollection<Document> collection = db.getCollection(table);
            // database.runCommand(new Document("db.runCommand",sh.shardCollection'",
            // database.getName() + "." + table));

            // adminDb.runCommand(new Document("shardCollection", database.getName() + "." +
            // table));

            database.getCollection(table).createIndex(new Document(key, 1));

            adminDb.runCommand(new Document("enableSharding", database.getName()));

            Document shardCommandParams = new Document();
            shardCommandParams.append("shardCollection", database.getName() + "." + table);
            shardCommandParams.append("key", new Document(key, 1));

            adminDb.runCommand(shardCommandParams);
            sharded.put(table, true);
        }

        return super.insert(table, key, values);
    }

    @Override
    public void init() throws DBException {
        super.init();
        sharded = new HashMap<>();

        String url = getProperties().getProperty("mongodb.url");
        MongoClientURI uri = new MongoClientURI(url);
        MongoClient mongoClient = new MongoClient(uri);
        String dbName = uri.getDatabase();
        database = mongoClient.getDatabase(dbName).withWriteConcern(uri.getOptions().getWriteConcern())
                .withReadPreference(uri.getOptions().getReadPreference());

        try {
            adminDb = mongoClient.getDatabase("admin");
        } catch (IllegalArgumentException e) {

        }

        sharded = new HashMap<>();
    }

}
