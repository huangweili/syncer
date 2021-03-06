package com.github.zzt93.syncer.consumer.output;

import com.github.zzt93.syncer.common.util.NamedThreadFactory;
import com.github.zzt93.syncer.config.consumer.output.PipelineOutput;
import com.github.zzt93.syncer.config.syncer.SyncerOutput;
import com.github.zzt93.syncer.consumer.ack.Ack;
import com.github.zzt93.syncer.consumer.output.batch.BatchJob;
import com.github.zzt93.syncer.consumer.output.channel.BufferedChannel;
import com.github.zzt93.syncer.consumer.output.channel.OutputChannel;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author zzt
 */
public class OutputStarter {

  private final Logger logger = LoggerFactory.getLogger(OutputStarter.class);
  private final List<OutputChannel> outputChannels;
  private final ScheduledExecutorService batchService;

  public OutputStarter(String consumerId, PipelineOutput pipelineOutput, SyncerOutput module,
      Ack ack) throws Exception {
    workerCheck(module.getWorker());
    workerCheck(module.getBatch().getWorker());

    outputChannels = pipelineOutput.toOutputChannels(consumerId, ack, module.getOutputMeta());
    batchService = Executors
        .newScheduledThreadPool(Math.min(module.getBatch().getWorker(), outputChannels.size()),
            new NamedThreadFactory("syncer-batch"));

    for (OutputChannel outputChannel : outputChannels) {
      if (outputChannel instanceof BufferedChannel) {
        BufferedChannel bufferedChannel = (BufferedChannel) outputChannel;
        long delay = bufferedChannel.getDelay();
        batchService.scheduleWithFixedDelay(new BatchJob(consumerId, bufferedChannel), delay, delay,
            bufferedChannel.getDelayUnit());
      }
    }
  }

  public List<OutputChannel> getOutputChannels() {
    return outputChannels;
  }

  private void workerCheck(int worker) {
    Preconditions.checkArgument(worker <= Runtime.getRuntime().availableProcessors() * 2,
        "Too many worker thread");
    Preconditions.checkArgument(worker > 0, "Too few worker thread");
  }

  public void close() {
    logger.info("[Shutting down] Output channels");
    for (OutputChannel outputChannel : outputChannels) {
      outputChannel.close();
    }

    logger.info("[Shutting down] Batch service");
    // batchService is not loop but scheduled task, just shutdown is enough to stop it
    batchService.shutdown();
  }
}
