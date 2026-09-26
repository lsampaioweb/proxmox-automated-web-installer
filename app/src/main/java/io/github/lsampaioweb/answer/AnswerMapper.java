package io.github.lsampaioweb.answer;

import java.util.List;

/**
 * Converts installer request DTOs into normalized MAC lookup keys.
 */
public interface AnswerMapper {

  /**
   * Returns usable interface MAC addresses in request order.
   *
   * @param request installer request
   * @return trimmed, lowercase MAC addresses, excluding blanks
   */
  List<String> normalizedMacs(AnswerRequest request);

}
