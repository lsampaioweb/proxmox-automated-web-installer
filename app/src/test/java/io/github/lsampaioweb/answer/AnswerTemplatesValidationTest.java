package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Renders every committed answer template and checks it with the Proxmox tool
 * when supported.
 */
class AnswerTemplatesValidationTest {

  private static final String PASSWORD_HASH = "$y$j9T$RXxfPAHqPMqk41tKVbXP./$TQkN9KnjzT0sSUFIYV33HkZe4bwD9U5brWuhnXaIHn0";
  private static final String ASSISTANT_NAME = "proxmox-auto-install-assistant";
  private static final Path ANSWERS = resolveAnswersDirectory();

  @TempDir
  Path tempDir;

  private AnswerRepositoryImpl repository;
  private Path assistantPath;
  private boolean assistantSupportsSchema;

  @BeforeEach
  void setUp() throws IOException {
    assertThat(Files.isDirectory(ANSWERS)).as("committed answers directory must exist").isTrue();
    assistantPath = findAssistant();
    assistantSupportsSchema = assistantPath != null;
    Path copy = tempDir.resolve("answers");
    Files.createDirectories(copy);
    try (Stream<Path> files = Files.list(ANSWERS)) {
      for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".toml")).toList()) {
        Files.copy(file, copy.resolve(file.getFileName()));
      }
    }

    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("i18n/messages");
    messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
    ProxmoxProperties properties = new ProxmoxProperties("unused", new ProxmoxProperties.Answers(copy));
    repository = new AnswerRepositoryImpl(properties, new RootPasswordHash(PASSWORD_HASH), messageSource);
  }

  @Test
  void shouldRenderEveryCommittedAnswerTemplate() throws Exception {
    try (Stream<Path> files = Files.list(ANSWERS)) {
      List<Path> templates = files.filter(path -> path.getFileName().toString().endsWith(".toml")).toList();
      assertThat(templates).isNotEmpty();

      for (Path template : templates) {
        String mac = template.getFileName().toString().replace(AnswerProperties.ANSWER_FILE_EXTENSION, "");
        byte[] rendered = repository.readByMac(mac);
        String body = new String(rendered, StandardCharsets.UTF_8);
        assertThat(body)
            .doesNotContain("__PROXMOX_ROOT_PASSWORD_HASH__")
            .contains(PASSWORD_HASH)
            .contains("[network.interface-name-pinning]")
            .contains("[post-installation-webhook]")
            .contains("url = \"http://proxmox-auto-install-assistant.lan.homelab:8080/api/v1/post-install-events\"");

        if (mac.equals("60:be:b4:08:f7:66") || mac.equals("00:d0:b4:01:77:7a")) {
          assertThat(body).contains("[first-boot]", "ordering = \"before-network\"",
              "/api/v1/first-boot?interface=vmbr0&vlan-id=1110");
        } else {
          assertThat(body).doesNotContain("[first-boot]");
        }

        Path renderedFile = tempDir.resolve(mac + "-rendered.toml");
        Files.write(renderedFile, rendered);
        maybeValidateWithAssistant(renderedFile);
      }
    }
  }

  private void maybeValidateWithAssistant(Path renderedFile) throws Exception {
    if (!assistantSupportsSchema) {
      return;
    }

    ProcessBuilder validate = new ProcessBuilder(assistantPath.toString(), "validate-answer", renderedFile.toString());
    validate.redirectErrorStream(true);
    Process process = validate.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0 && output.contains("unknown field")) {
      assistantSupportsSchema = false;
      return;
    }

    assertThat(exitCode).as("validate-answer output: %s", output).isZero();
  }

  private static Path resolveAnswersDirectory() {
    Path moduleDirectory = Path.of("answers");
    if (Files.isDirectory(moduleDirectory)) {
      return moduleDirectory;
    }

    return Path.of("app", "answers");
  }

  private static Path findAssistant() {
    String pathValue = System.getenv("PATH");
    if (pathValue == null) {
      return null;
    }

    for (String directory : pathValue.split(File.pathSeparator)) {
      Path candidate = Path.of(directory).resolve(ASSISTANT_NAME);
      if (Files.isExecutable(candidate)) {
        return candidate;
      }
    }

    return null;
  }

}
