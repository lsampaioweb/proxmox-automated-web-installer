package io.github.lsampaioweb.answer;

import java.util.Locale;

/**
 * Finds the answer file for a Proxmox installer request.
 */
public interface AnswerService {

  /**
   * Returns the file for the first installer MAC with a matching answer file.
   *
   * @param request       installer request
   * @param clientAddress remote address used in audit metadata
   * @param locale        locale resolved from the request
   * @return exact TOML file bytes
   */
  byte[] findAnswer(AnswerRequest request, String clientAddress, Locale locale);

}
