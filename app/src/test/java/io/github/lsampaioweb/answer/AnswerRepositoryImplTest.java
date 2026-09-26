package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class AnswerRepositoryImplTest {

  private static final String MAC = "b4:2e:99:ad:b0:c0";
  private static final String PASSWORD_HASH_PLACEHOLDER = "__PROXMOX_ROOT_PASSWORD_HASH__";
  private static final String TEST_PASSWORD_HASH = "$y$test$fake-test-hash";
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
    repository = new AnswerRepositoryImpl(new AnswerProperties(answersDirectory),
        new RootPasswordHash(TEST_PASSWORD_HASH));
  }

  @Test
  void shouldReturnExactBytesForValidTomlFile() throws IOException {
    Files.write(answersDirectory.resolve(MAC + ".toml"), TOML_BYTES);

    byte[] result = repository.readByMac(MAC);

    assertThat(result).isEqualTo(new String(TOML_BYTES, StandardCharsets.UTF_8)
        .replace(PASSWORD_HASH_PLACEHOLDER, TEST_PASSWORD_HASH).getBytes(StandardCharsets.UTF_8));
    assertThat(Files.readAllBytes(answersDirectory.resolve(MAC + ".toml"))).isEqualTo(TOML_BYTES);
  }

  @Test
  void shouldThrowNotFoundWhenFileIsMissing() {
    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(AnswerNotFoundException.class);
  }

  @Test
  void shouldThrowInvalidTomlWhenContentIsNotToml() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + ".toml"), "this is not [[ toml");

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(InvalidAnswerTomlException.class);
  }

  @Test
  void shouldRejectAnswerFileWithoutPasswordHashPlaceholder() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + ".toml"), "[global]\nkeyboard = \"en-us\"\n");

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(InvalidAnswerTomlException.class);
  }

  @Test
  void shouldRejectAnswerFileWithMultiplePasswordHashPlaceholders() throws IOException {
    Files.writeString(answersDirectory.resolve(MAC + ".toml"), """
        [global]
        root-password-hashed = "__PROXMOX_ROOT_PASSWORD_HASH__"
        note = "__PROXMOX_ROOT_PASSWORD_HASH__"
        """);

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(InvalidAnswerTomlException.class);
  }

  @Test
  void shouldThrowUnreadableWhenPathIsADirectory() throws IOException {
    Files.createDirectory(answersDirectory.resolve(MAC + ".toml"));

    assertThatThrownBy(() -> repository.readByMac(MAC))
        .isInstanceOf(AnswerUnreadableException.class);
  }

  @Test
  @EnabledOnOs({ OS.LINUX, OS.MAC })
  void shouldThrowUnreadableWhenFileCannotBeRead() throws IOException {
    Path answerFile = answersDirectory.resolve(MAC + ".toml");
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
  @EnabledOnOs({ OS.LINUX, OS.MAC })
  void shouldRejectSymlinkedAnswerFile() throws IOException {
    Path outsideFile = answersDirectory.resolveSibling("outside.toml");
    Files.write(outsideFile, TOML_BYTES);
    Path answerFile = answersDirectory.resolve(MAC + ".toml");
    Files.createSymbolicLink(answerFile, outsideFile);

    try {
      assertThatThrownBy(() -> repository.readByMac(MAC))
          .isInstanceOf(AnswerUnreadableException.class);
    } finally {
      Files.deleteIfExists(outsideFile);
    }
  }

}
