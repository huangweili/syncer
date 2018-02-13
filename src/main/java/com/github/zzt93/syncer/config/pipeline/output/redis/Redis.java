package com.github.zzt93.syncer.config.pipeline.output.redis;

import com.github.zzt93.syncer.config.pipeline.common.InvalidConfigException;
import com.github.zzt93.syncer.config.pipeline.common.RedisConnection;
import com.github.zzt93.syncer.config.pipeline.output.FailureLogConfig;
import com.github.zzt93.syncer.config.pipeline.output.OutputChannelConfig;
import com.github.zzt93.syncer.config.pipeline.output.PipelineBatch;
import com.github.zzt93.syncer.config.syncer.SyncerOutputMeta;
import com.github.zzt93.syncer.consumer.ack.Ack;
import com.github.zzt93.syncer.consumer.output.channel.redis.RedisChannel;

/**
 * @author zzt
 */
public class Redis implements OutputChannelConfig {

  private RedisConnection connection;
  private OperationMapping mapping = new OperationMapping();
  private PipelineBatch batch = new PipelineBatch();
  private FailureLogConfig failureLog = new FailureLogConfig();
  private String condition;

  public FailureLogConfig getFailureLog() {
    return failureLog;
  }

  public void setFailureLog(FailureLogConfig failureLog) {
    this.failureLog = failureLog;
  }

  public RedisConnection getConnection() {
    return connection;
  }

  public void setConnection(RedisConnection connection) {
    this.connection = connection;
  }

  public OperationMapping getMapping() {
    return mapping;
  }

  public void setMapping(OperationMapping mapping) {
    this.mapping = mapping;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public PipelineBatch getBatch() {
    return batch;
  }

  public void setBatch(PipelineBatch batch) {
    this.batch = batch;
  }

  @Override
  public RedisChannel toChannel(Ack ack,
      SyncerOutputMeta outputMeta) throws Exception {
    if (connection.valid()) {
      return new RedisChannel(this, outputMeta, ack);
    }
    throw new InvalidConfigException("Invalid connection configuration: " + connection);
  }

  @Override
  public String conditionExpr() {
    return condition;
  }
}
