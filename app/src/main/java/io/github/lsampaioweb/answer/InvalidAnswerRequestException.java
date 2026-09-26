package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Raised when the request has no usable interface MAC address.
 */
public class InvalidAnswerRequestException extends AppException {

  private static final String MESSAGE_KEY_REQUIRED = "error.answer.mac.required";
  private static final String MESSAGE_KEY_INVALID = "error.answer.mac.invalid";
  private static final String ERROR_CODE_REQUIRED = "ANSWER_MAC_REQUIRED";
  private static final String ERROR_CODE_INVALID = "ANSWER_MAC_INVALID";

  /**
   * Creates the exception for a request without a usable MAC.
   */
  public InvalidAnswerRequestException() {
    this(MESSAGE_KEY_REQUIRED, ERROR_CODE_REQUIRED);
  }

  static InvalidAnswerRequestException forMalformedMacAddress() {
    return new InvalidAnswerRequestException(MESSAGE_KEY_INVALID, ERROR_CODE_INVALID);
  }

  /**
   * Creates a bad-request exception with the supplied localized error identity.
   *
   * @param messageKey localized message key
   * @param errorCode  stable error code
   */
  protected InvalidAnswerRequestException(String messageKey, String errorCode) {
    super(messageKey, errorCode, null, HttpStatus.BAD_REQUEST);
  }

}
