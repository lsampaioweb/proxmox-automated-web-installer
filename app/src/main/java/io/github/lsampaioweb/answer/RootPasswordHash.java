package io.github.lsampaioweb.answer;

/**
 * Yescrypt password hash inserted into generated answer responses.
 */
record RootPasswordHash(String value) {

  /**
   * Returns a redacted representation of this secret value.
   *
   * @return redacted password hash marker
   */
  @Override
  public String toString() {
    return "RootPasswordHash[redacted]";
  }

}