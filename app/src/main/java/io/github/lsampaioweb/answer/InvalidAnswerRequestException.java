package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Signals that the request has no usable MAC address.
 */
public class InvalidAnswerRequestException extends AppException {

  private static final String MESSAGE_KEY_REQUIRED = "error.answer.mac.required";
  private static final String MESSAGE_KEY_INVALID = "error.answer.mac.invalid";
  private static final String ERROR_CODE_REQUIRED = "ANSWER_MAC_REQUIRED";
  private static final String ERROR_CODE_INVALID = "ANSWER_MAC_INVALID";

  /**
   * Creates an error for a request without a usable MAC.
   */
  public InvalidAnswerRequestException() {
    this(MESSAGE_KEY_REQUIRED, ERROR_CODE_REQUIRED);
  }

  static InvalidAnswerRequestException forMalformedMacAddress() {
    return new InvalidAnswerRequestException(MESSAGE_KEY_INVALID, ERROR_CODE_INVALID);
  }

  /**
   * Creates a bad-request error with the given message key and code.
   *
   * @param messageKey localized message key
   * @param errorCode  stable error code
   */
  protected InvalidAnswerRequestException(String messageKey, String errorCode) {
    super(messageKey, errorCode, null, HttpStatus.BAD_REQUEST);
  }

}
