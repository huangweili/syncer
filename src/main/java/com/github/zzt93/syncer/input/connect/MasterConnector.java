package com.github.zzt93.syncer.input.connect;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.network.SSLMode;
import com.github.zzt93.syncer.common.ConnectionSchemaMeta;
import com.github.zzt93.syncer.common.SyncData;
import com.github.zzt93.syncer.common.ThreadSafe;
import com.github.zzt93.syncer.common.util.FileUtil;
import com.github.zzt93.syncer.common.util.NetworkUtil;
import com.github.zzt93.syncer.config.pipeline.InvalidPasswordException;
import com.github.zzt93.syncer.config.pipeline.common.MysqlConnection;
import com.github.zzt93.syncer.config.pipeline.common.SchemaUnavailableException;
import com.github.zzt93.syncer.config.pipeline.input.Schema;
import com.github.zzt93.syncer.config.syncer.SyncerMysql;
import com.github.zzt93.syncer.input.filter.InputEnd;
import com.github.zzt93.syncer.input.filter.InputFilter;
import com.github.zzt93.syncer.input.filter.InputPipeHead;
import com.github.zzt93.syncer.input.filter.RowFilter;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * @author zzt
 */
public class MasterConnector implements Runnable {

  private final static Random random = new Random();
  private final String connectorIdentifier;
  private final Path connectorMetaPath;
  private Logger logger = LoggerFactory.getLogger(MasterConnector.class);
  private BinaryLogClient client;
  private AtomicReference<BinlogInfo> binlogInfo = new AtomicReference<>();

  public MasterConnector(MysqlConnection connection, List<Schema> schema,
      BlockingQueue<SyncData> queue, SyncerMysql mysqlMastersMeta)
      throws IOException, SchemaUnavailableException {
    String password = FileUtil.readAll(connection.getPasswordFile());
    if (StringUtils.isEmpty(password)) {
      throw new InvalidPasswordException(password);
    }

    connectorIdentifier = NetworkUtil.toIp(connection.getAddress()) + ":" + connection.getPort();
    connectorMetaPath = Paths.get(mysqlMastersMeta.getLastRunMetadataDir(), connectorIdentifier);

    configLogClient(connection, password);
    configEventListener(connection, schema, queue);
  }

  private void configLogClient(MysqlConnection connection, String password) throws IOException {
    client = new BinaryLogClient(connection.getAddress(), connection.getPort(),
        connection.getUser(), password);
    client.registerLifecycleListener(new LogLifecycleListener());
    client.setEventDeserializer(SyncDeserializer.defaultDeserialzer());
    client.setServerId(random.nextInt(Integer.MAX_VALUE));
    client.setSSLMode(SSLMode.DISABLED);
    if (!Files.exists(connectorMetaPath)) {
      logger.info("Last run meta file not exists, fresh run");
    } else {
      List<String> lines = Files.readAllLines(connectorMetaPath, StandardCharsets.UTF_8);
      if (lines.size() == 2) {
        client.setBinlogFilename(lines.get(0));
        client.setBinlogPosition(Long.parseLong(lines.get(1)));
      } else {
        logger.error("Invalid last run meta file, is it crash? Take it as fresh run");
      }
    }
  }

  private void configEventListener(MysqlConnection connection, List<Schema> schemas,
      BlockingQueue<SyncData> queue) throws SchemaUnavailableException {
    List<InputFilter> filters = new ArrayList<>();
    ConnectionSchemaMeta connectionSchemaMeta;
    assert !schemas.isEmpty();
    try {
      connectionSchemaMeta = new ConnectionSchemaMeta.MetaDataBuilder(connection, schemas).build();
      filters.add(new RowFilter(connectionSchemaMeta));
    } catch (SQLException e) {
      logger.error("Fail to connect to master to retrieve schema metadata", e);
      throw new SchemaUnavailableException(e);
    }
    SyncListener eventListener = new SyncListener(new InputPipeHead(connectionSchemaMeta), filters,
        new InputEnd(), queue);
    client.registerEventListener(eventListener);
    client.registerEventListener((event) -> binlogInfo
        .set(new BinlogInfo(client.getBinlogFilename(), client.getBinlogPosition())));
  }

  @ThreadSafe(des = "final field is thread safe: it is fixed before hook thread start")
  Path connectorMetaPath() {
    return connectorMetaPath;
  }

  @ThreadSafe(sharedBy = {"syncer-input: connect()", "shutdown hook"})
  List<String> connectorMeta() {
    BinlogInfo binlogInfo = this.binlogInfo.get();
    return Lists.newArrayList(binlogInfo.getBinlogFilename(), "" + binlogInfo.getBinlogPosition());
  }


  @Override
  public void run() {
    Thread.currentThread().setName(connectorIdentifier);
    int retry = 5;
    for (int i = 0; i < retry; i++) {
      try {
        // this method is blocked
        client.connect();
      } catch (InvalidBinlogException e) {
        logger.warn("Invalid binlog file info, reconnect to older binlog", e);
        i = 0;
//        client.setBinlogFilename(""); not fetch oldest log
        client.setBinlogFilename(null);
      } catch (IOException e) {
        logger.error("Fail to connect to master", e);
      }
      logger.warn("Re-connect to master, left retry time: {}", retry-i-1);
    }
    logger.error("Max try exceeds, fail to connect");
  }
}
