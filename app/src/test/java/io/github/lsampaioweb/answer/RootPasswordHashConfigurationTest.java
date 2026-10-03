package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

class RootPasswordHashConfigurationTest {

  private static final String ERROR_ROOT_PASSWORD_REQUIRED = "error.root.password.required";
  private static final MessageSource MESSAGE_SOURCE = createMessageSource();

  @TempDir
  Path answersDirectory;

  @Test
  void createsYescryptHashForConfiguredPassword() {
    RootPasswordHash hash = new RootPasswordHashConfiguration()
        .rootPasswordHash(properties("test-root-password"), MESSAGE_SOURCE, "en");

    assertThat(hash.value()).startsWith("$y$");
  }

  @Test
  void reportsBlankPasswordUsingConfiguredDefaultLocale() {
    Locale defaultLocale = Locale.forLanguageTag("pt-BR");
    RootPasswordHashConfiguration configuration = new RootPasswordHashConfiguration();
    ProxmoxProperties blankPasswordProperties = properties("  ");

    assertThatThrownBy(() -> configuration.rootPasswordHash(blankPasswordProperties, MESSAGE_SOURCE, "pt-BR"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(MESSAGE_SOURCE.getMessage(ERROR_ROOT_PASSWORD_REQUIRED, null, defaultLocale));
  }

  @Test
  void rejectsMissingAnswersConfiguration() {
    assertThatThrownBy(() -> new ProxmoxProperties("secret", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("answers");
  }

  @Test
  void rejectsMissingAnswersDirectory() {
    assertThatThrownBy(() -> new ProxmoxProperties.Answers(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("directory");
  }

  private ProxmoxProperties properties(String password) {
    return new ProxmoxProperties(password, new ProxmoxProperties.Answers(answersDirectory));
  }

  private static MessageSource createMessageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");

    return messageSource;
  }

}
