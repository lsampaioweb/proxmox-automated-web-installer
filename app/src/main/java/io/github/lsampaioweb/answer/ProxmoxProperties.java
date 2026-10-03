package io.github.lsampaioweb.answer;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration for answer-file serving.
 *
 * @param rootPassword plaintext root password hashed once at startup
 * @param answers      filesystem answer-file store
 */
@ConfigurationProperties(prefix = "proxmox")
public record ProxmoxProperties(String rootPassword, Answers answers) {

  public ProxmoxProperties {
    Objects.requireNonNull(answers, "answers");
  }

  /**
   * Filesystem location of Proxmox answer files.
   *
   * @param directory directory that contains answer files
   */
  public record Answers(Path directory) {

    public Answers {
      Objects.requireNonNull(directory, "directory");
    }
  }

}
