package com.github.zzt93.syncer.consumer.output.channel.kafka;

import com.github.zzt93.syncer.common.data.SyncData;
import com.github.zzt93.syncer.config.common.ClusterConnection;
import com.github.zzt93.syncer.config.consumer.output.FailureLogConfig;
import com.github.zzt93.syncer.config.consumer.output.kafka.Kafka;
import com.github.zzt93.syncer.config.syncer.SyncerOutputMeta;
import com.github.zzt93.syncer.consumer.ack.Ack;
import com.github.zzt93.syncer.consumer.output.channel.AckChannel;
import com.github.zzt93.syncer.consumer.output.channel.OutputChannel;
import com.github.zzt93.syncer.consumer.output.channel.SyncWrapper;
import com.github.zzt93.syncer.consumer.output.failure.FailureEntry;
import com.github.zzt93.syncer.consumer.output.failure.FailureLog;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import org.apache.kafka.common.errors.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author zzt
 */
public class KafkaChannel implements OutputChannel, AckChannel<String> {

  private final Logger logger = LoggerFactory.getLogger(KafkaChannel.class);
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final Ack ack;
  private final FailureLog<SyncWrapper<String>> request;
  private final String consumerId;
  private final MsgMapper msgMapper;
  private final String identifier;

  public KafkaChannel(Kafka kafka, SyncerOutputMeta outputMeta, Ack ack) {
    DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(
        kafka.buildProperties());
    this.kafkaTemplate = new KafkaTemplate<>(factory);
    this.ack = ack;
    this.consumerId = kafka.getConsumerId();
    this.msgMapper = new MsgMapper(kafka.getMsgMapping());
    ClusterConnection connection = kafka.getConnection();
    FailureLogConfig failureLog = kafka.getFailureLog();
    identifier = connection.connectionIdentifier();
    Path path = Paths.get(outputMeta.getFailureLogDir(), identifier);
    this.request = FailureLog.getLogger(path, failureLog, new TypeToken<FailureEntry<SyncWrapper<String>>>() {
    });
  }

  @Override
  public void ackSuccess(List<SyncWrapper<String>> aim) {
    for (SyncWrapper<String> wrapper : aim) {
      ack.remove(wrapper.getSourceId(), wrapper.getSyncDataId());
    }
  }

  @Override
  public void retryFailed(List<SyncWrapper<String>> aim, Throwable e) {
    SyncWrapper<String> wrapper = aim.get(0);
    ErrorLevel level = level(e, wrapper, wrapper.retryCount());
    if (level.retriable()) {
      doSend(wrapper.getData(), wrapper);
      return;
    }
    // TODO 18/11/14 test producer retry
    // because kafka template has configured to retry
    logger.error("Max retry exceed, write '{}' to failure log", wrapper, e);
    request.log(wrapper, e.getMessage());
    ack.remove(wrapper.getSourceId(), wrapper.getSyncDataId());
  }

  @Override
  public ErrorLevel level(Throwable e, SyncWrapper wrapper, int maxTry) {
    if (e instanceof TimeoutException) {
      return AckChannel.super.level(e, wrapper, maxTry);
    }
    return ErrorLevel.MAX_TRY_EXCEED;
  }

  @Override
  public boolean checkpoint() {
    return ack.flush();
  }

  @Override
  public boolean output(SyncData event) throws InterruptedException {
    String topic = msgMapper.map(event);
    SyncWrapper<String> wrapper = new SyncWrapper<>(event, topic);
    doSend(topic, wrapper);
    return true;
  }

  private void doSend(String topic, SyncWrapper<String> wrapper) {
    final SyncData event = wrapper.getEvent();
    // require that messages with the same key (for instance, a unique id) are always seen in the correct order,
    // attaching a key to messages will ensure messages with the same key always go to the same partition in a topic
    ListenableFuture<SendResult<String, Object>> future;
    if (event.getId() != null) {
      String key = event.getId().toString();
      future = kafkaTemplate.send(topic, key, event.getResult());
    } else {
      logger.warn("Send {} to {} without key", event, topic);
      future = kafkaTemplate.send(topic, event.getResult());
    }
    ListenableFutureCallback<SendResult<String, Object>> callback = new ListenableFutureCallback<SendResult<String, Object>>() {

      @Override
      public void onSuccess(final SendResult<String, Object> message) {
        ackSuccess(Lists.newArrayList(wrapper));
        logger.info("sent {} with offset {} ", event, message.getRecordMetadata().offset());
      }

      @Override
      public void onFailure(final Throwable throwable) {
        retryFailed(Lists.newArrayList(wrapper), throwable);
        logger.error("unable to send {} ", event, throwable);
      }
    };
    future.addCallback(callback);
    // no need to wait future, the order between batch is ensured by kafka client
  }

  @Override
  public String des() {
    return "KafkaChannel{" +
        "kafkaTemplate=" + kafkaTemplate +
        ", ack=" + ack +
        ", request=" + request +
        '}';
  }

  @Override
  public void close() {
    kafkaTemplate.flush();
  }

  @Override
  public String id() {
    return identifier;
  }


}
