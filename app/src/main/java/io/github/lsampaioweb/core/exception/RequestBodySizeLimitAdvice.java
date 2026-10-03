package io.github.lsampaioweb.core.exception;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Limits the bytes consumed while Spring converts request bodies.
 */
@ControllerAdvice
public class RequestBodySizeLimitAdvice extends RequestBodyAdviceAdapter {

  private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;

  /**
   * Applies the body-size limit to MVC request bodies.
   *
   * @param methodParameter target controller parameter
   * @param targetType      target body type
   * @param converterType   selected message converter
   * @return {@code true} for MVC request bodies
   */
  @Override
  public boolean supports(MethodParameter methodParameter, Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  /**
   * Rejects oversized bodies before deserialization, including chunked bodies.
   *
   * @param inputMessage  original request body
   * @param parameter     target controller parameter
   * @param targetType    target body type
   * @param converterType selected message converter
   * @return request body with bounded reads
   * @throws IOException when the declared body size exceeds the limit
   */
  @Override
  public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
    if (inputMessage.getHeaders().getContentLength() > MAX_REQUEST_BODY_BYTES) {
      throw new RequestBodyTooLargeException();
    }

    byte[] body = inputMessage.getBody().readNBytes(MAX_REQUEST_BODY_BYTES + 1);
    if (body.length > MAX_REQUEST_BODY_BYTES) {
      throw new RequestBodyTooLargeException();
    }

    return new LimitedHttpInputMessage(inputMessage.getHeaders(), new ByteArrayInputStream(body));
  }

  private static record LimitedHttpInputMessage(HttpHeaders headers, InputStream body) implements HttpInputMessage {

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public InputStream getBody() {
      return body;
    }

  }

  static final class RequestBodyTooLargeException extends IOException {

    private RequestBodyTooLargeException() {
      super();
    }

  }

}