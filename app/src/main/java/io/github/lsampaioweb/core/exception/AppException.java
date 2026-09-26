package io.github.lsampaioweb.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected application failures that map to an HTTP status.
 */
public abstract class AppException extends RuntimeException {

  private final String messageKey;
  private final String errorCode;
  private final transient Object[] args;
  private final HttpStatus status;

  /**
   * Creates an application exception.
   *
   * @param messageKey i18n message key
   * @param errorCode stable machine error code
   * @param args message format arguments
   * @param status HTTP status to return
   */
  protected AppException(String messageKey, String errorCode, Object[] args, HttpStatus status) {
    super(messageKey);

    this.messageKey = messageKey;
    this.errorCode = errorCode;
    this.args = args;
    this.status = status;
  }

  /**
   * Returns the i18n message key.
   *
   * @return message key
   */
  public String getMessageKey() {
    return messageKey;
  }

  /**
   * Returns the stable machine error code.
   *
   * @return error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the message format arguments.
   *
   * @return format arguments
   */
  public Object[] getArgs() {
    return args;
  }

  /**
   * Returns the HTTP status for this failure.
   *
   * @return HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }

}
