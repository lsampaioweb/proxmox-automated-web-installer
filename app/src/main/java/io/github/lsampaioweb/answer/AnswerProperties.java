package io.github.lsampaioweb.answer;

import java.nio.file.Path;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem location of Proxmox answer files.
 *
 * @param directory directory that contains answer files
 */
@ConfigurationProperties(prefix = "proxmox.answers")
public record AnswerProperties(Path directory) {

  static final Pattern MAC_ADDRESS_PATTERN = Pattern.compile("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}");
  static final String ANSWER_FILE_EXTENSION = ".toml";
}
