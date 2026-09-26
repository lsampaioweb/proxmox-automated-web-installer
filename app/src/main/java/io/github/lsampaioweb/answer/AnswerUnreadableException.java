package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Raised when the selected answer file cannot be read.
 */
public class AnswerUnreadableException extends AppException {

  private static final String MESSAGE_KEY = "error.answer.unreadable";
  private static final String ERROR_CODE = "ANSWER_UNREADABLE";

  /**
   * Creates the exception for an unreadable answer file.
   *
   * @param cause underlying I/O failure
   */
  public AnswerUnreadableException(Throwable cause) {
    super(MESSAGE_KEY, ERROR_CODE, null, HttpStatus.INTERNAL_SERVER_ERROR);
    initCause(cause);
  }

}
