package io.github.lsampaioweb.core.exception;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard JSON body returned when an API request fails.
 *
 * @param timestamp time the request failed, including its UTC offset
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param errorCode stable machine error code
 * @param message   localized error message
 * @param path      request URI that triggered the failure
 * @param trace     stack trace text when stack traces are enabled
 * @param fields    field errors for Bean Validation failures
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
   * Validation error for one request field.
   *
   * @param field   name of the rejected field
   * @param message localized validation message
   */
  public record FieldError(String field, String message) {
  }

}
