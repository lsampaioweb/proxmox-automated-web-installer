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
import java.util.regex.Pattern;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.toml.TomlMapper;

/**
 * Finds, reads, and renders answer files from the configured directory.
 */
@Repository
@RequiredArgsConstructor
class AnswerRepositoryImpl implements AnswerRepository {

  private static final String ERROR_ANSWER_PATH_NOT_REGULAR = "error.answer.path.not.regular";
  private static final String ERROR_ANSWER_FILESYSTEM_UNSUPPORTED = "error.answer.filesystem.unsupported";
  private static final String ERROR_ANSWER_PASSWORD_PLACEHOLDER = "error.answer.password.placeholder";
  private static final String ERROR_ANSWER_PASSWORD_MISSING = "error.answer.password.missing";
  private static final String ERROR_ANSWER_PASSWORD_BOTH = "error.answer.password.both";
  private static final String ERROR_ANSWER_FILENAME_AMBIGUOUS = "error.answer.filename.ambiguous";
  private static final String ROOT_PASSWORD_HASH_PLACEHOLDER = "__PROXMOX_ROOT_PASSWORD_HASH__";
  private static final Pattern ROOT_PASSWORD_HASHED_ASSIGNMENT = Pattern.compile(
      "(?m)^[ \\t]*root-password-hashed[ \\t]*=[ \\t]*\"" + Pattern.quote(ROOT_PASSWORD_HASH_PLACEHOLDER)
          + "\"[ \\t]*$");
  private static final TomlMapper TOML_MAPPER = TomlMapper.shared();

  private final ProxmoxProperties proxmoxProperties;
  private final RootPasswordHash rootPasswordHash;
  private final MessageSource messageSource;

  /** Reads and renders the template for a MAC address. */
  @Override
  public byte[] readByMac(String normalizedMac) {
    if (normalizedMac == null || !AnswerProperties.MAC_ADDRESS_PATTERN.matcher(normalizedMac).matches()) {
      throw InvalidAnswerRequestException.forMalformedMacAddress();
    }

    String filename = normalizedMac + AnswerProperties.ANSWER_FILE_EXTENSION;
    Path answersDirectory = proxmoxProperties.answers().directory();
    Path canonicalDirectory;
    try {
      canonicalDirectory = answersDirectory.toRealPath();
    } catch (IOException ex) {
      throw new AnswerUnreadableException(ex);
    }

    byte[] template;
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(canonicalDirectory)) {
      SecureDirectoryStream<Path> secureStream = secureDirectoryStream(directoryStream);
      String resolvedFilename = resolveFilenameIgnoreCase(directoryStream, filename);
      Path relativeFilename = canonicalDirectory.getFileSystem().getPath(resolvedFilename);
      template = readRelativeFile(secureStream, relativeFilename);
    } catch (NoSuchFileException _) {
      throw new AnswerNotFoundException();
    } catch (IOException ex) {
      throw new AnswerUnreadableException(ex);
    }

    byte[] content = renderAnswer(template);
    validateRenderedAnswer(content);

    return content;
  }

  private String resolveFilenameIgnoreCase(DirectoryStream<Path> directoryStream, String expectedFilename)
      throws IOException {
    String matched = null;
    for (Path entry : directoryStream) {
      String name = entry.getFileName().toString();
      if (!name.equalsIgnoreCase(expectedFilename)) {
        continue;
      }
      if (matched != null) {
        String message = messageSource.getMessage(ERROR_ANSWER_FILENAME_AMBIGUOUS, new Object[] { expectedFilename },
            LocaleContextHolder.getLocale());
        throw new IOException(message);
      }
      matched = name;
    }

    if (matched == null) {
      throw new NoSuchFileException(expectedFilename);
    }

    return matched;
  }

  private byte[] readRelativeFile(SecureDirectoryStream<Path> directoryStream, Path relativeFilename)
      throws IOException {
    BasicFileAttributeView attributeView = directoryStream.getFileAttributeView(relativeFilename,
        BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (attributeView == null) {
      throw new NoSuchFileException(relativeFilename.toString());
    }

    if (!attributeView.readAttributes().isRegularFile()) {
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
    String template = renderPasswordHash(new String(content, StandardCharsets.UTF_8));

    return template.getBytes(StandardCharsets.UTF_8);
  }

  private String renderPasswordHash(String template) {
    int first = template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER);
    if (first < 0) {
      return template;
    }

    int second = template.indexOf(ROOT_PASSWORD_HASH_PLACEHOLDER, first + ROOT_PASSWORD_HASH_PLACEHOLDER.length());
    if (second >= 0 || !ROOT_PASSWORD_HASHED_ASSIGNMENT.matcher(template).find()) {
      String message = messageSource.getMessage(ERROR_ANSWER_PASSWORD_PLACEHOLDER, null,
          LocaleContextHolder.getLocale());
      throw new InvalidAnswerTemplateException(new IllegalStateException(message));
    }

    return template.replace(ROOT_PASSWORD_HASH_PLACEHOLDER, rootPasswordHash.value());
  }

  private void validateRenderedAnswer(byte[] content) {
    JsonNode tree;
    try {
      tree = TOML_MAPPER.readTree(content);
    } catch (RuntimeException ex) {
      throw new InvalidAnswerTomlException(ex);
    }

    JsonNode global = tree.get("global");
    if (global == null || !global.isObject()) {
      String message = messageSource.getMessage(ERROR_ANSWER_PASSWORD_MISSING, null, LocaleContextHolder.getLocale());
      throw new InvalidAnswerTemplateException(new IllegalStateException(message));
    }

    boolean hasPlain = global.has("root-password");
    boolean hasHashed = global.has("root-password-hashed");
    if (!hasPlain && !hasHashed) {
      String message = messageSource.getMessage(ERROR_ANSWER_PASSWORD_MISSING, null, LocaleContextHolder.getLocale());
      throw new InvalidAnswerTemplateException(new IllegalStateException(message));
    }
    if (hasPlain && hasHashed) {
      String message = messageSource.getMessage(ERROR_ANSWER_PASSWORD_BOTH, null, LocaleContextHolder.getLocale());
      throw new InvalidAnswerTemplateException(new IllegalStateException(message));
    }
  }

}
