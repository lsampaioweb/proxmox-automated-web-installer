package io.github.lsampaioweb.answer;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

/**
 * Raised when an answer template violates rendering or password-policy rules.
 */
public class InvalidAnswerTemplateException extends AppException {

  private static final String MESSAGE_KEY = "error.answer.invalid.template";
  private static final String ERROR_CODE = "ANSWER_TEMPLATE_INVALID";

  /**
   * Creates the exception for an invalid answer template.
   *
   * @param cause policy or rendering failure
   */
  public InvalidAnswerTemplateException(Throwable cause) {
    super(MESSAGE_KEY, ERROR_CODE, null, HttpStatus.INTERNAL_SERVER_ERROR);
    initCause(cause);
  }

}
