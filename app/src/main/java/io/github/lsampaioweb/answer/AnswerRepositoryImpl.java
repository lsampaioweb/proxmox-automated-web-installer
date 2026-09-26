package io.github.lsampaioweb.answer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import tools.jackson.dataformat.toml.TomlMapper;

/**
 * Resolves answer files from the configured answers directory.
 */
@Repository
@RequiredArgsConstructor
class AnswerRepositoryImpl implements AnswerRepository {

  private static final String ERROR_ANSWER_PATH_NOT_REGULAR = "error.answer.path.not.regular";
  private static final String ERROR_ANSWER_PASSWORD_PLACEHOLDER = "error.answer.password.placeholder";
  private static final String ERROR_ANSWER_FILESYSTEM_UNSUPPORTED = "error.answer.filesystem.unsupported";
  private static final String ROOT_PASSWORD_HASH_PLACEHOLDER = "__PROXMOX_ROOT_PASSWORD_HASH__";
  private static final TomlMapper TOML_MAPPER = TomlMapper.shared();

  private final AnswerProperties answerProperties;
  private final RootPasswordHash rootPasswordHash;
  private final MessageSource messageSource;

  /** Reads and renders the answer template for the requested MAC address. */
  @Override
  public byte[] readByMac(String normalizedMac) {
    if (normalizedMac == null || !AnswerProperties.MAC_ADDRESS_PATTERN.matcher(normalizedMac).matches()) {
      throw InvalidAnswerRequestException.forMalformedMacAddress();
    }

    String filename = normalizedMac + AnswerProperties.ANSWER_FILE_EXTENSION;
    byte[] template;
    try {
      Path canonicalDirectory = answerProperties.directory().toRealPath();
      Path relativeFilename = canonicalDirectory.getFileSystem().getPath(filename);
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(canonicalDirectory)) {
        template = readRelativeFile(secureDirectoryStream(directoryStream), relativeFilename);
      }
    } catch (NoSuchFileException _) {
      throw new AnswerNotFoundException();
    } catch (IOException ex) {
      throw new AnswerUnreadableException(ex);
    }

    byte[] content = renderAnswer(template);
    validateToml(content);

    return content;
  }

  private byte[] readRelativeFile(SecureDirectoryStream<Path> directoryStream, Path relativeFilename)
      throws IOException {
    BasicFileAttributeView attributeView = directoryStream.getFileAttributeView(relativeFilename,
        BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (attributeView == null || !attributeView.readAttributes().isRegularFile()) {
      throw answerPathNotRegular();
    }

    Set<OpenOption> openOptions = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    try (SeekableByteChannel answerChannel = directoryStream.newByteChannel(relativeFilename, openOptions);
        InputStream answerStream = Channels.newInputStream(answerChannel)) {
      if (!attributeView.readAttributes().isRegularFile()) {
        throw answerPathNotRegular();
      }

      return answerStream.readAllBytes();
    }
  }

  private SecureDirectoryStream<Path> secureDirectoryStream(DirectoryStream<Path> directoryStream)
      throws IOException {
    if (!(directoryStream instanceof SecureDirectoryStream<?>)) {
      String message = messageSource.getMessage(ERROR_ANSWER_FILESYSTEM_UNSUPPORTED, null,
          LocaleContextHolder.getLocale());
      throw new IOException(message);
    }

    return (SecureDirectoryStream<Path>) directoryStream;
  }

  private IOException answerPathNotRegular() {
    String message = messageSource.getMessage(ERROR_ANSWER_PATH_NOT_REGULAR, null, LocaleContextHolder.getLocale());

    return new IOException(message);
  }

  private byte[] renderAnswer(byte[] content) {
    String template = new String(content, StandardCharsets.UTF_8);

    int placeholderPosition = template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER);
    if (placeholderPosition < 0 || template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER,
        placeholderPosition + ROOT_PASSWORD_HASH_PLACEHOLDER.length()) >= 0) {
      String message = messageSource.getMessage(ERROR_ANSWER_PASSWORD_PLACEHOLDER, null,
          LocaleContextHolder.getLocale());
      throw new InvalidAnswerTomlException(new IllegalStateException(message));
    }

    return template.replace(ROOT_PASSWORD_HASH_PLACEHOLDER, rootPasswordHash.value()).getBytes(StandardCharsets.UTF_8);
  }

  private void validateToml(byte[] content) {
    try {
      TOML_MAPPER.readTree(content);
    } catch (RuntimeException ex) {
      throw new InvalidAnswerTomlException(ex);
    }
  }

}
