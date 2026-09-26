package io.github.lsampaioweb.answer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import tools.jackson.dataformat.toml.TomlMapper;

/**
 * Resolves {@code <mac>.toml} files from the configured answers directory.
 */
@Repository
@RequiredArgsConstructor
class AnswerRepositoryImpl implements AnswerRepository {

  private static final String TOML_EXTENSION = ".toml";
  private static final String ROOT_PASSWORD_HASH_PLACEHOLDER = "__PROXMOX_ROOT_PASSWORD_HASH__";
  private static final TomlMapper TOML_MAPPER = TomlMapper.shared();

  private final AnswerProperties answerProperties;
  private final RootPasswordHash rootPasswordHash;

  /** Reads and renders the answer template for the requested MAC address. */
  @Override
  public byte[] readByMac(String normalizedMac) {
    Path answersDirectory = answerProperties.directory().toAbsolutePath().normalize();
    Path answerFile = answersDirectory.resolve(normalizedMac + TOML_EXTENSION).normalize();
    if (!answerFile.startsWith(answersDirectory)) {
      throw new InvalidAnswerRequestException();
    }

    if (!Files.exists(answerFile, LinkOption.NOFOLLOW_LINKS)) {
      throw new AnswerNotFoundException();
    }

    // Reject symlinks so a link planted inside the answers directory cannot serve
    // files outside it.
    if (Files.isSymbolicLink(answerFile) || !Files.isRegularFile(answerFile, LinkOption.NOFOLLOW_LINKS)) {
      throw new AnswerUnreadableException(new IOException("Answer path is not a regular file"));
    }

    byte[] content = renderAnswer(readFile(answerFile));
    validateToml(content);

    return content;
  }

  private byte[] renderAnswer(byte[] content) {
    String template = new String(content, StandardCharsets.UTF_8);
    int placeholderPosition = template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER);
    if (placeholderPosition < 0 || template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER,
        placeholderPosition + ROOT_PASSWORD_HASH_PLACEHOLDER.length()) >= 0) {
      throw new InvalidAnswerTomlException(
          new IllegalStateException("Answer file must contain exactly one root password placeholder"));
    }

    return template.replace(ROOT_PASSWORD_HASH_PLACEHOLDER, rootPasswordHash.value())
        .getBytes(StandardCharsets.UTF_8);
  }

  private byte[] readFile(Path answerFile) {
    try {
      return Files.readAllBytes(answerFile);
    } catch (IOException ex) {
      throw new AnswerUnreadableException(ex);
    }
  }

  private void validateToml(byte[] content) {
    try {
      TOML_MAPPER.readTree(content);
    } catch (RuntimeException ex) {
      throw new InvalidAnswerTomlException(ex);
    }
  }

}
