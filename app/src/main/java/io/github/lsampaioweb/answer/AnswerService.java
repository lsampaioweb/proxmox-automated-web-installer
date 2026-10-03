package io.github.lsampaioweb.answer;

import java.util.Locale;

/**
 * Resolves a Proxmox answer file for an installer request.
 */
public interface AnswerService {

  /**
   * Finds the answer file for the first installer MAC that has a matching file.
   *
   * @param request       installer request
   * @param clientAddress remote address used in audit metadata
   * @param locale        locale resolved from the request
   * @return exact TOML file bytes
   */
  byte[] findAnswer(AnswerRequest request, String clientAddress, Locale locale);

}
