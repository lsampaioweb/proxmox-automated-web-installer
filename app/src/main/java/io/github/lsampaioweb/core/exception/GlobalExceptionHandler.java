package io.github.lsampaioweb.core.exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import lombok.extern.slf4j.Slf4j;

import io.github.lsampaioweb.core.i18n.LogMessages;

/**
 * Translates application and framework failures into the shared JSON error
 * envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERR_INTERNAL = "error.internal.server";
  private static final String ERR_RESOURCE_NOT_FOUND = "error.resource.not.found";
  private static final String ERR_VALIDATION_FAILED = "error.validation.failed";
  private static final String ERR_INVALID_JSON = "error.request.invalid.json";

  private static final String LOG_UNHANDLED = "log.exception.unhandled";
  private static final String LOG_REJECTED = "log.request.rejected";

  private static final String CODE_INTERNAL = "INTERNAL_ERROR";
  private static final String CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
  private static final String CODE_VALIDATION = "VALIDATION_ERROR";
  private static final String CODE_INVALID_JSON = "INVALID_JSON";

  private static final String STACKTRACE_ALWAYS = "always";

  private final MessageSource messageSource;
  private final LogMessages logMessages;
  private final boolean stacktraceEnabled;

  /**
   * Creates the global exception handler.
   *
   * @param messageSource     i18n message source
   * @param logMessages       developer-facing log message helper
   * @param includeStacktrace {@code server.error.include-stacktrace} value
   */
  public GlobalExceptionHandler(MessageSource messageSource, LogMessages logMessages,
      @Value("${server.error.include-stacktrace:never}") String includeStacktrace) {
    this.messageSource = messageSource;
    this.logMessages = logMessages;
    this.stacktraceEnabled = STACKTRACE_ALWAYS.equalsIgnoreCase(includeStacktrace);
  }

  /**
   * Maps domain exceptions to the JSON error envelope.
   *
   * @param ex      failed request
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
    return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessageKey(), ex.getArgs(), request, ex, null);
  }

  /**
   * Maps invalid JSON bodies to the JSON error envelope.
   *
   * @param ex      parse failure
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
      HttpServletRequest request) {
    log.warn(logMessages.get(LOG_REJECTED, Instant.now().atOffset(ZoneOffset.UTC), request.getRemoteAddr(),
        CODE_INVALID_JSON));

    return problem(HttpStatus.BAD_REQUEST, CODE_INVALID_JSON, ERR_INVALID_JSON, null, request, ex, null);
  }

  /**
   * Maps Bean Validation failures to the JSON error envelope.
   *
   * @param ex      validation failure
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    log.warn(logMessages.get(LOG_REJECTED, Instant.now().atOffset(ZoneOffset.UTC), request.getRemoteAddr(),
        CODE_VALIDATION));

    List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
        .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
        .toList();

    return problem(HttpStatus.BAD_REQUEST, CODE_VALIDATION, ERR_VALIDATION_FAILED, null, request, ex, fields);
  }

  /**
   * Maps unknown URLs to the JSON error envelope.
   *
   * @param ex      missing resource
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex,
      HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, CODE_RESOURCE_NOT_FOUND, ERR_RESOURCE_NOT_FOUND, null, request, ex, null);
  }

  /**
   * Maps unexpected failures to the JSON error envelope.
   *
   * @param ex      unexpected failure
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
    log.error(logMessages.get(LOG_UNHANDLED), ex);

    return problem(HttpStatus.INTERNAL_SERVER_ERROR, CODE_INTERNAL, ERR_INTERNAL, null, request, ex, null);
  }

  private ResponseEntity<ErrorResponse> problem(HttpStatus status, String errorCode, String messageKey, Object[] args,
      HttpServletRequest request, Throwable ex, List<ErrorResponse.FieldError> fields) {
    Locale locale = LocaleContextHolder.getLocale();
    String message = messageSource.getMessage(messageKey, args, locale);
    ErrorResponse body = new ErrorResponse(
        Instant.now().atOffset(ZoneOffset.UTC),
        status.value(),
        status.getReasonPhrase(),
        errorCode,
        message,
        request.getRequestURI(),
        traceOf(ex),
        fields);

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  private String traceOf(Throwable ex) {
    if (!stacktraceEnabled) {
      return null;
    }

    StringWriter writer = new StringWriter();
    ex.printStackTrace(new PrintWriter(writer));

    return writer.toString();
  }

}
