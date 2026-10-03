package io.github.lsampaioweb.answer;

import java.util.List;

/**
 * Extracts and normalizes MAC addresses from an installer request.
 */
public interface AnswerMapper {

  /**
   * Returns valid MAC addresses in the order the installer sent them.
   *
   * @param request installer request
   * @return trimmed, lowercase MAC addresses, excluding blanks
   */
  List<String> normalizedMacs(AnswerRequest request);

}
