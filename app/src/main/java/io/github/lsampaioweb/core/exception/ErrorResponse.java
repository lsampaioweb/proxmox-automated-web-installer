package io.github.lsampaioweb.core.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * JSON error envelope returned by {@link GlobalExceptionHandler}.
 *
 * @param timestamp UTC offset time of the failure
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param errorCode stable machine error code
 * @param message   localized error message
 * @param path      request URI that triggered the failure
 * @param trace     stack trace text, present only when stack traces are enabled
 * @param fields    Bean Validation field errors, present only for validation
 *                  failures
 */
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String errorCode,
    String message,
    String path,
    String trace,
    List<FieldError> fields) {

  /**
   * One Bean Validation field failure.
   *
   * @param field   name of the rejected field
   * @param message localized validation message
   */
  public record FieldError(String field, String message) {
  }

}
