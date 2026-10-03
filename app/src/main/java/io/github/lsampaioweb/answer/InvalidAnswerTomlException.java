package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Signals that the selected answer file isn't valid TOML.
 */
public class InvalidAnswerTomlException extends AppException {

  private static final String MESSAGE_KEY = "error.answer.invalid.toml";
  private static final String ERROR_CODE = "ANSWER_INVALID_TOML";

  /**
   * Creates an error for invalid TOML content.
   *
   * @param cause parse failure
   */
  public InvalidAnswerTomlException(Throwable cause) {
    super(MESSAGE_KEY, ERROR_CODE, null, HttpStatus.INTERNAL_SERVER_ERROR);
    initCause(cause);
  }

}
