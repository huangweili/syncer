package com.github.zzt93.syncer.config.producer;

import com.github.zzt93.syncer.config.common.Connection;
import com.github.zzt93.syncer.config.common.MayClusterConnection;
import com.github.zzt93.syncer.config.consumer.input.MasterSourceType;

/**
 * @author zzt
 */
public class ProducerMaster {

  private MasterSourceType type = MasterSourceType.MySQL;
  private MayClusterConnection connection;
  private String file;
  private boolean onlyUpdated = true;

  public Connection getRealConnection() {
    connection.validate(type);
    return connection.getRealConnection();
  }

  public MayClusterConnection getConnection() {
    return connection;
  }

  public void setConnection(MayClusterConnection connection) {
    this.connection = connection;
  }

  public MasterSourceType getType() {
    return type;
  }

  public void setType(MasterSourceType type) {
    this.type = type;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public boolean isOnlyUpdated() {
    return onlyUpdated;
  }

  public void setOnlyUpdated(boolean onlyUpdated) {
    this.onlyUpdated = onlyUpdated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ProducerMaster that = (ProducerMaster) o;

    return connection.equals(that.connection);
  }

  @Override
  public int hashCode() {
    return connection.hashCode();
  }

  @Override
  public String toString() {
    return "ProducerMaster{" +
        "type=" + type +
        ", connection=" + connection +
        ", file='" + file + '\'' +
        '}';
  }
}
