package io.github.lsampaioweb.answer;

/**
 * Reads Proxmox answer files from the configured filesystem directory.
 */
public interface AnswerRepository {

  /**
   * Reads the answer file named after the normalized MAC address.
   *
   * @param normalizedMac lowercase MAC used as the file name stem
   * @return exact bytes of the selected TOML file
   */
  byte[] readByMac(String normalizedMac);

}
