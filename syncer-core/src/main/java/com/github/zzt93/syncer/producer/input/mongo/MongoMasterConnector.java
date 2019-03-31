package com.github.zzt93.syncer.producer.input.mongo;

import com.github.zzt93.syncer.ShutDownCenter;
import com.github.zzt93.syncer.common.exception.ShutDownException;
import com.github.zzt93.syncer.common.util.FallBackPolicy;
import com.github.zzt93.syncer.config.common.InvalidConfigException;
import com.github.zzt93.syncer.config.common.MongoConnection;
import com.github.zzt93.syncer.config.consumer.input.Entity;
import com.github.zzt93.syncer.producer.dispatch.mongo.MongoDispatcher;
import com.github.zzt93.syncer.producer.input.Consumer;
import com.github.zzt93.syncer.producer.input.MasterConnector;
import com.github.zzt93.syncer.producer.output.ProducerSink;
import com.github.zzt93.syncer.producer.register.ConsumerRegistry;
import com.google.common.base.Throwables;
import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author zzt
 */
public class MongoMasterConnector implements MasterConnector {

  public static final String NS = "ns";
  public static final String TS = "ts";
  private final Logger logger = LoggerFactory.getLogger(MongoMasterConnector.class);

  private final String identifier;
  private MongoCursor<Document> cursor;
  private MongoDispatcher mongoDispatcher;
  private MongoClient client;
  private Document query;


  public MongoMasterConnector(MongoConnection connection, ConsumerRegistry registry) throws IOException {
    identifier = connection.connectionIdentifier();

    client = new MongoClient(new MongoClientURI(connection.toConnectionUrl(null)));
    configDispatch(connection, registry);
    configQuery(connection, registry);
  }

  private void configQuery(MongoConnection connection, ConsumerRegistry registry) {
    DocTimestamp docTimestamp = registry.votedMongoId(connection);
    Pattern namespaces = getNamespaces(connection, registry);
    query = new Document()
        .append(NS, new BasicDBObject("$regex", namespaces))
        // https://www.mongodb.com/blog/post/tailing-mongodb-oplog-sharded-clusters
        // fromMigrate indicates the operation results from a shard re-balancing.
        .append("fromMigrate", new BasicDBObject("$exists", "false"))
    ;
    if (docTimestamp.getTimestamp() != null) {
      query.append(TS, new BasicDBObject("$gte", docTimestamp.getTimestamp()));
    } else {
      // initial export
      logger.warn("Start with initial export, may take a long time");
      query.append(TS, new BasicDBObject("$gt", new BsonTimestamp()));
    }
    // no need for capped collections:
    // perform a find() on a capped collection with no ordering specified,
    // MongoDB guarantees that the ordering of results is the same as the insertion order.
    // BasicDBObject sort = new BasicDBObject("$natural", 1);
  }

  private void configDispatch(MongoConnection connection, ConsumerRegistry registry) {
    HashMap<Consumer, ProducerSink> schemaSinkMap = registry
        .outputSink(connection);
    mongoDispatcher = new MongoDispatcher(schemaSinkMap);
  }

  private void configCursor() {
    this.cursor = getReplicaCursor(client, query);
  }

  private MongoCursor<Document> getReplicaCursor(MongoClient client, Document query) {
    MongoDatabase db = client.getDatabase("local");
    MongoCollection<Document> coll = db.getCollection("oplog.rs");

    return coll.find(query)
        .cursorType(CursorType.TailableAwait)
        .oplogReplay(true)
        .iterator();
  }

  private Pattern getNamespaces(MongoConnection connection, ConsumerRegistry registry) {
    // TODO 2019/2/28 check whether registered collection is exists
    Set<Consumer> consumers = registry.outputSink(connection).keySet();
    StringJoiner joiner = new StringJoiner("|");
    consumers.stream().map(Consumer::getRepos).flatMap(Set::stream).flatMap(s -> {
      List<Entity> entities = s.getEntities();
      ArrayList<String> res = new ArrayList<>(entities.size());
      for (Entity entity : entities) {
        res.add("(" + s.getName() + "\\." + entity.getName() + ")");
      }
      return res.stream();
    }).forEach(joiner::add);
    return Pattern.compile(joiner.toString());
  }

  @Override
  public void loop() {
    Thread.currentThread().setName(identifier);

    long sleepInSecond = 1;
    while (!Thread.interrupted()) {
      try {
        configCursor();
        eventLoop();
      } catch (MongoTimeoutException | MongoSocketException e) {
        logger
            .error("Fail to connect to remote: {}, retry in {} second", identifier, sleepInSecond);
        try {
          sleepInSecond = FallBackPolicy.POW_2.next(sleepInSecond, TimeUnit.SECONDS);
          TimeUnit.SECONDS.sleep(sleepInSecond);
        } catch (InterruptedException e1) {
          logger.error("Interrupt mongo {}", identifier, e1);
          Thread.currentThread().interrupt();
        }
      } catch (MongoInterruptedException e) {
        logger.warn("Mongo master interrupted");
        throw new ShutDownException(e);
      }
    }
  }

  private void eventLoop() {
    while (cursor.hasNext()) {
      Document d = cursor.next();
      try {
        mongoDispatcher.dispatch(null, d);
      } catch (InvalidConfigException e) {
        ShutDownCenter.initShutDown(e);
      } catch (Throwable e) {
        logger.error("Fail to dispatch this doc {}", d);
        Throwables.throwIfUnchecked(e);
      }
    }
  }

  @Override
  public void close() {
    try {
      cursor.close();
      client.close();
    } catch (Throwable e){
      logger.error("[Shutting down] failed", e);
      return;
    }
    MasterConnector.super.close();
  }
}
