package io.github.lsampaioweb.answer;

import java.util.regex.Pattern;

/**
 * Shared answer-file naming and MAC syntax constants.
 */
public final class AnswerProperties {

  static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}");

  /** Canonical answer-file suffix required by this service. */
  public static final String ANSWER_FILE_EXTENSION = ".toml";

  private AnswerProperties() {
  }

}
