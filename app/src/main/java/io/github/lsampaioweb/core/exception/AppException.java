package io.github.lsampaioweb.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected application errors returned with an HTTP status.
 */
public abstract class AppException extends RuntimeException {

  private final String messageKey;
  private final String errorCode;
  private final transient Object[] args;
  private final HttpStatus status;

  /**
   * Sets the message, error code, arguments, and HTTP status for this error.
   *
   * @param messageKey key used to look up the localized message
   * @param errorCode  stable code returned to the client
   * @param args       values inserted into the message
   * @param status     HTTP status returned to the client
   */
  protected AppException(String messageKey, String errorCode, Object[] args, HttpStatus status) {
    super(messageKey);

    this.messageKey = messageKey;
    this.errorCode = errorCode;
    this.args = args;
    this.status = status;
  }

  /**
   * Returns the key for the localized message.
   *
   * @return message key
   */
  public String getMessageKey() {
    return messageKey;
  }

  /**
   * Returns the error code sent to the client.
   *
   * @return error code
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the values inserted into the message.
   *
   * @return format arguments
   */
  public Object[] getArgs() {
    return args;
  }

  /**
   * Returns the HTTP status for this error.
   *
   * @return HTTP status
   */
  public HttpStatus getStatus() {
    return status;
  }

}
