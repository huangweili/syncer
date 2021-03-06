package com.github.zzt93.syncer.data.exception;

/**
 * @author zzt
 */
public class InvalidConfigException extends IllegalArgumentException {

  public InvalidConfigException() {
  }

  public InvalidConfigException(String s) {
    super(s);
  }

  public InvalidConfigException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidConfigException(Throwable cause) {
    super(cause);
  }
}
