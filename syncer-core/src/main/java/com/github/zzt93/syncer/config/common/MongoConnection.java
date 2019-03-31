package com.github.zzt93.syncer.config.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;

/**
 * @author zzt
 */
public class MongoConnection extends Connection {

  private static final Logger logger = LoggerFactory.getLogger(MongoConnection.class);

  public MongoConnection(Connection connection) {
    try {
      setAddress(connection.getAddress());
    } catch (UnknownHostException ignored) {
      logger.error("Impossible", ignored);
    }
    setPort(connection.getPort());
    if (connection.getUser() == null) {
      return;
    }
    setUser(connection.getUser());
    setPassword(connection.getPassword());
    setPasswordFile(connection.getPasswordFile());
  }

  @Override
  public String toConnectionUrl(String path) {
    String s = super.toConnectionUrl(null);
    if (getUser() != null) {
      s = getUser() + ":" + getPassword() + "@" + s + "/?authSource=admin";
    }
    return "mongodb://" + s;
  }
}
