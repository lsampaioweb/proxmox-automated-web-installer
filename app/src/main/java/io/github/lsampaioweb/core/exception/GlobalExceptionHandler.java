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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;
import lombok.extern.slf4j.Slf4j;

import io.github.lsampaioweb.core.i18n.LogMessages;

/**
 * Turns application and Spring MVC failures into the API's standard JSON
 * response.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String ERR_INTERNAL = "error.internal.server";
  private static final String ERR_RESOURCE_NOT_FOUND = "error.resource.not.found";
  private static final String ERR_VALIDATION_FAILED = "error.validation.failed";
  private static final String ERR_INVALID_JSON = "error.request.invalid.json";
  private static final String ERR_REQUEST_BODY_TOO_LARGE = "error.request.body.too.large";
  private static final String ERR_METHOD_NOT_ALLOWED = "error.request.method.not.allowed";
  private static final String ERR_MEDIA_TYPE_UNSUPPORTED = "error.request.media.type.unsupported";

  private static final String LOG_UNHANDLED = "log.exception.unhandled";
  private static final String LOG_REJECTED = "log.request.rejected";
  private static final String LOG_HOOK_REJECTED = "log.hook.rejected";

  private static final String CODE_INTERNAL = "INTERNAL_ERROR";
  private static final String CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
  private static final String CODE_VALIDATION = "VALIDATION_ERROR";
  private static final String CODE_INVALID_JSON = "INVALID_JSON";
  private static final String CODE_REQUEST_BODY_TOO_LARGE = "REQUEST_BODY_TOO_LARGE";
  private static final String CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
  private static final String CODE_UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";

  private static final String STACKTRACE_ALWAYS = "always";

  private final MessageSource messageSource;
  private final LogMessages logMessages;
  private final boolean stacktraceEnabled;

  /**
   * Sets up the message and logging services used by this handler.
   *
   * @param messageSource     source of translated API messages
   * @param logMessages       source of translated log messages
   * @param includeStacktrace {@code server.error.include-stacktrace} value
   */
  public GlobalExceptionHandler(MessageSource messageSource, LogMessages logMessages,
      @Value("${server.error.include-stacktrace:never}") String includeStacktrace) {
    this.messageSource = messageSource;
    this.logMessages = logMessages;
    this.stacktraceEnabled = STACKTRACE_ALWAYS.equalsIgnoreCase(includeStacktrace);
  }

  /**
   * Returns the API response for an application error.
   *
   * @param ex      application error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
    if (!"/answer".equals(request.getRequestURI())) {
      logRejectedRequest(request, ex.getErrorCode());
    }
    return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessageKey(), ex.getArgs(), request, ex, null);
  }

  /**
   * Returns an error response when the request body can't be parsed.
   *
   * @param ex      body parsing error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
      HttpServletRequest request) {
    if (ex.getCause() instanceof RequestBodySizeLimitAdvice.RequestBodyTooLargeException) {
      return requestBodyTooLarge(ex, request);
    }

    logRejectedRequest(request, CODE_INVALID_JSON);

    return problem(HttpStatus.BAD_REQUEST, CODE_INVALID_JSON, ERR_INVALID_JSON, null, request, ex, null);
  }

  /**
   * Returns field errors when request validation fails.
   *
   * @param ex      validation error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
      HttpServletRequest request) {
    logRejectedRequest(request, CODE_VALIDATION);

    List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
        .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
        .toList();

    return problem(HttpStatus.BAD_REQUEST, CODE_VALIDATION, ERR_VALIDATION_FAILED, null, request, ex, fields);
  }

  /**
   * Returns an error when the endpoint doesn't accept the HTTP method.
   *
   * @param ex      method error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    logRejectedRequest(request, CODE_METHOD_NOT_ALLOWED);

    return problem(HttpStatus.METHOD_NOT_ALLOWED, CODE_METHOD_NOT_ALLOWED, ERR_METHOD_NOT_ALLOWED, null, request, ex,
        null);
  }

  /**
   * Returns an error when the endpoint doesn't accept the request Content-Type.
   *
   * @param ex      media-type error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    logRejectedRequest(request, CODE_UNSUPPORTED_MEDIA_TYPE);

    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CODE_UNSUPPORTED_MEDIA_TYPE, ERR_MEDIA_TYPE_UNSUPPORTED, null,
        request, ex, null);
  }

  /**
   * Returns an error when no endpoint matches the requested URL.
   *
   * @param ex      missing resource error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex,
      HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, CODE_RESOURCE_NOT_FOUND, ERR_RESOURCE_NOT_FOUND, null, request, ex, null);
  }

  /**
   * Returns a generic error response for unexpected failures.
   *
   * @param ex      unexpected error
   * @param request current HTTP request
   * @return JSON error response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
    log.error(logMessages.get(RequestContextUtils.getLocale(request), LOG_UNHANDLED), ex);

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

  private ResponseEntity<ErrorResponse> requestBodyTooLarge(HttpMessageNotReadableException ex,
      HttpServletRequest request) {
    logRejectedRequest(request, CODE_REQUEST_BODY_TOO_LARGE);

    return problem(HttpStatus.CONTENT_TOO_LARGE, CODE_REQUEST_BODY_TOO_LARGE, ERR_REQUEST_BODY_TOO_LARGE, null,
        request, ex, null);
  }

  private void logRejectedRequest(HttpServletRequest request, String errorCode) {
    if (!"/answer".equals(request.getRequestURI())) {
      log.warn(logMessages.get(RequestContextUtils.getLocale(request), LOG_HOOK_REJECTED,
          Instant.now(), errorCode));
      return;
    }
    log.warn(logMessages.get(
        RequestContextUtils.getLocale(request),
        LOG_REJECTED,
        Instant.now().atOffset(ZoneOffset.UTC),
        LogMessages.safeField(request.getRemoteAddr()),
        errorCode));
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
