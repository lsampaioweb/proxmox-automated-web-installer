package io.github.lsampaioweb.answer;

/**
 * Yescrypt hash inserted into rendered answer files.
 */
record RootPasswordHash(String value) {

  /**
   * Returns a safe summary without exposing the hash.
   *
   * @return redacted password hash marker
   */
  @Override
  public String toString() {
    return "RootPasswordHash[redacted]";
  }

}