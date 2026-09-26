package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Raised when the request has no usable interface MAC address.
 */
public class InvalidAnswerRequestException extends AppException {

  private static final String MESSAGE_KEY = "error.answer.mac.required";
  private static final String ERROR_CODE = "ANSWER_MAC_REQUIRED";

  /**
   * Creates the exception for a request without a usable MAC.
   */
  public InvalidAnswerRequestException() {
    super(MESSAGE_KEY, ERROR_CODE, null, HttpStatus.BAD_REQUEST);
  }

}
