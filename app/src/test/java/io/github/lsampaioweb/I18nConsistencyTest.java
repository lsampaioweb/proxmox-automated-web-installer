package io.github.lsampaioweb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class I18nConsistencyTest {

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\d+}");
  private static final String EN_MESSAGES = "i18n/messages.properties";
  private static final String PT_BR_MESSAGES = "i18n/messages_pt_BR.properties";

  @Test
  void localeBundlesShouldContainExactlySameKeysAndPlaceholders() {
    Properties en = loadProperties(EN_MESSAGES);
    Properties ptBr = loadProperties(PT_BR_MESSAGES);

    assertThat(ptBr.stringPropertyNames())
        .as("pt_BR must contain all English keys")
        .containsExactlyInAnyOrderElementsOf(en.stringPropertyNames());

    assertThat(en.stringPropertyNames())
        .as("English must contain all pt_BR keys")
        .containsExactlyInAnyOrderElementsOf(ptBr.stringPropertyNames());

    for (String key : en.stringPropertyNames()) {
      List<String> enPlaceholders = placeholders(en.getProperty(key));
      List<String> ptPlaceholders = placeholders(ptBr.getProperty(key));

      assertThat(ptPlaceholders)
          .as("placeholder mismatch for key: %s", key)
          .containsExactlyElementsOf(enPlaceholders);
    }
  }

  private Properties loadProperties(String classpathLocation) {
    Properties properties = new Properties();

    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
      if (inputStream == null) {
        throw new IllegalStateException("Could not find properties file: " + classpathLocation);
      }

      properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

      return properties;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load properties file: " + classpathLocation, ex);
    }
  }

  private List<String> placeholders(String messageTemplate) {
    List<String> placeholders = new ArrayList<>();
    if (messageTemplate == null) {
      return placeholders;
    }

    Matcher matcher = PLACEHOLDER_PATTERN.matcher(messageTemplate);
    while (matcher.find()) {
      placeholders.add(matcher.group());
    }

    placeholders.sort(String::compareTo);

    return placeholders;
  }

}
