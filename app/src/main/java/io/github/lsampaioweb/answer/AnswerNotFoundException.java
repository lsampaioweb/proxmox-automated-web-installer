package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Raised when no answer file exists for the requested MAC address.
 */
public class AnswerNotFoundException extends AppException {

  private static final String MESSAGE_KEY = "error.answer.not.found";
  private static final String ERROR_CODE = "ANSWER_NOT_FOUND";

  /**
   * Creates the exception for a missing answer file.
   */
  public AnswerNotFoundException() {
    super(MESSAGE_KEY, ERROR_CODE, null, HttpStatus.NOT_FOUND);
  }

}
