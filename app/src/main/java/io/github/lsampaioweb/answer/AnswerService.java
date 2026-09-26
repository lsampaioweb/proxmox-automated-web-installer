package io.github.lsampaioweb.answer;

/**
 * Resolves a Proxmox answer file for an installer request.
 */
public interface AnswerService {

  /**
   * Finds the answer file for the first installer MAC that has a matching file.
   *
   * @param request installer request
   * @param clientAddress remote address used in audit metadata
   * @return exact TOML file bytes
   */
  byte[] findAnswer(AnswerRequest request, String clientAddress);

}
