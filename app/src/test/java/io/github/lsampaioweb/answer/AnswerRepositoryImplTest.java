package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

class AnswerRepositoryImplTest {

  private static final String MAC = "b4:2e:99:ad:b0:c0";
  private static final String ERROR_ANSWER_FILESYSTEM_UNSUPPORTED = "error.answer.filesystem.unsupported";
  private static final String ERROR_ANSWER_PATH_NOT_REGULAR = "error.answer.path.not.regular";
  private static final String ERROR_ANSWER_PASSWORD_PLACEHOLDER = "error.answer.password.placeholder";
  private static final String PASSWORD_HASH_PLACEHOLDER = "__PROXMOX_ROOT_PASSWORD_HASH__";
  private static final String TEST_PASSWORD_HASH = "$y$test$fake-test-hash";
  private static final MessageSource MESSAGE_SOURCE = createMessageSource();
  private static final byte[] TOML_BYTES = """
      [global]
      keyboard = "en-us"
      country = "us"
      root-password-hashed = "__PROXMOX_ROOT_PASSWORD_HASH__"
      """.getBytes(StandardCharsets.UTF_8);

  @TempDir
  Path answersDirectory;

  private AnswerRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = newRepository();
  }

  @Test
  void shouldReturnExactBytesForValidTomlFile() throws IOException {
    Files.write(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION), TOML_BYTES);

    byte[] result = repository.readByMac(MAC);

    assertThat(result).isEqualTo(new String(TOML_BYTES, StandardCharsets.UTF_8)
        .replace(PASSWORD_HASH_PLACEHOLDER, TEST_PASSWORD_HASH).getBytes(StandardCharsets.UTF_8));
    assertThat(Files.readAllBytes(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION)))
        .isEqualTo(TOML_BYTES);
  }

  @Test
  void shouldFindAnswerFileWhenFilenameCaseDiffers() throws IOException {
    Files.writeString(answersDirectory.resolve("B4:2E:99:AD:B0:C0.toml"), """
        [global]
        root-password-hashed = "__PROXMOX_ROOT_PASSWORD_HASH__"
        """);

    byte[] result = repository.readByMac(MAC);

    assertThat(new String(result, StandardCharsets.UTF_8)).contains(TEST_PASSWORD_HASH);
  }

  @Test
  void shouldThrowNotFoundWhenFileIsMissing() {
    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(AnswerNotFoundException.class);
  }

  @Test
  void shouldThrowUnreadableWhenAnswersDirectoryIsMissing() {
    AnswerRepositoryImpl missingDirectoryRepository = newRepository(answersDirectory.resolve("missing"));

    assertThatThrownBy(() -> missingDirectoryRepository.readByMac(MAC))
        .isInstanceOf(AnswerUnreadableException.class);
  }

  @Test
  void shouldThrowInvalidTomlWhenContentIsNotToml() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION), "this is not [[ toml");

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(InvalidAnswerTomlException.class);
  }

  @Test
  void shouldAllowAnswerFileWithPlainRootPasswordAndNoPlaceholder() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION), """
        [global]
        keyboard = "en-us"
        root-password = "12345678"
        """);

    byte[] result = repository.readByMac(MAC);

    assertThat(new String(result, StandardCharsets.UTF_8)).contains("root-password = \"12345678\"");
  }

  @Test
  void shouldRejectAnswerFileWithPlaceholderOutsidePasswordField() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION), """
        [global]
        keyboard = "en-us"
        root-password = "12345678"
        note = "__PROXMOX_ROOT_PASSWORD_HASH__"
        """);

    Locale locale = Locale.forLanguageTag("pt-BR");
    LocaleContextHolder.setLocale(locale);
    try {
      assertThatThrownBy(() -> repository.readByMac(MAC))
          .isInstanceOf(InvalidAnswerTemplateException.class)
          .satisfies(exception -> assertThat(exception.getCause())
              .hasMessage(MESSAGE_SOURCE.getMessage(ERROR_ANSWER_PASSWORD_PLACEHOLDER, null, locale)));
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }

  @Test
  void shouldRejectAnswerFileWithMultiplePasswordHashPlaceholders() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION), """
        [global]
        root-password-hashed = "__PROXMOX_ROOT_PASSWORD_HASH__"
        note = "__PROXMOX_ROOT_PASSWORD_HASH__"
        """);

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(InvalidAnswerTemplateException.class);
  }

  @Test
  void shouldThrowUnreadableWhenPathIsADirectory() throws IOException {
    Files.createDirectory(answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION));

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(AnswerUnreadableException.class)
        .satisfies(exception -> assertThat(exception.getCause())
            .hasMessage(MESSAGE_SOURCE.getMessage(ERROR_ANSWER_PATH_NOT_REGULAR, null, Locale.ENGLISH)));
  }

  @Test
  @EnabledOnOs({ OS.LINUX, OS.MAC })
  void shouldThrowUnreadableWhenFileCannotBeRead() throws IOException {
    Path answerFile = answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION);
    Files.write(answerFile, TOML_BYTES);
    Files.setPosixFilePermissions(answerFile, Set.of());

    try {
      assertThatThrownBy(() -> repository.readByMac(MAC))
          .isInstanceOf(AnswerUnreadableException.class);
    } finally {
      Files.setPosixFilePermissions(answerFile, Set.of(PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE));
    }
  }

  @Test
  void shouldRejectMacThatEscapesTheAnswersDirectory() {
    assertThatThrownBy(() -> repository.readByMac("../secret"))
        .isInstanceOf(InvalidAnswerRequestException.class);
  }

  @Test
  void shouldFailClosedWhenFilesystemDoesNotSupportSecureDirectoryStreams() throws IOException {
    Path archive = answersDirectory.resolve("answers.zip");
    try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()),
        Map.of("create", "true"))) {
      Path zipAnswersDirectory = fileSystem.getPath("/answers");
      Files.createDirectory(zipAnswersDirectory);
      AnswerRepositoryImpl zipRepository = newRepository(zipAnswersDirectory);

      assertThatThrownBy(() -> zipRepository.readByMac(MAC))
          .isInstanceOf(AnswerUnreadableException.class)
          .satisfies(exception -> assertThat(exception.getCause())
              .hasMessage(MESSAGE_SOURCE.getMessage(ERROR_ANSWER_FILESYSTEM_UNSUPPORTED, null, Locale.ENGLISH)));
    }
  }

  @Test
  @EnabledOnOs({ OS.LINUX, OS.MAC })
  void shouldRejectSymlinkedAnswerFile() throws IOException {
    Path outsideFile = answersDirectory.resolveSibling("outside");
    Files.write(outsideFile, TOML_BYTES);
    Path answerFile = answersDirectory.resolve(MAC + AnswerProperties.ANSWER_FILE_EXTENSION);
    Files.createSymbolicLink(answerFile, outsideFile);

    try {
      assertThatThrownBy(() -> repository.readByMac(MAC))
          .isInstanceOf(AnswerUnreadableException.class);
    } finally {
      Files.deleteIfExists(outsideFile);
    }
  }

  private AnswerRepositoryImpl newRepository() {
    return newRepository(answersDirectory);
  }

  private AnswerRepositoryImpl newRepository(Path directory) {
    ProxmoxProperties properties = new ProxmoxProperties("secret", new ProxmoxProperties.Answers(directory));

    return new AnswerRepositoryImpl(properties, new RootPasswordHash(TEST_PASSWORD_HASH), MESSAGE_SOURCE);
  }

  private static MessageSource createMessageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("i18n/messages");
    messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

    return messageSource;
  }

}
