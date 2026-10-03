package io.github.lsampaioweb.answer;

import java.util.regex.Pattern;

/**
 * Shared constants for answer-file names and MAC syntax.
 */
public final class AnswerProperties {

  static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}");

  /** Suffix used for every answer file. */
  public static final String ANSWER_FILE_EXTENSION = ".toml";

  private AnswerProperties() {
  }

}
