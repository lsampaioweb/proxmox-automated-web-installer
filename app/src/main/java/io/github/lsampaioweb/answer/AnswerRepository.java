package io.github.lsampaioweb.answer;

/**
 * Reads Proxmox answer files from the configured directory.
 */
public interface AnswerRepository {

  /**
   * Reads the answer file for a normalized MAC address.
   *
   * @param normalizedMac lowercase MAC used in the file name
   * @return bytes from the selected TOML file
   */
  byte[] readByMac(String normalizedMac);

}
