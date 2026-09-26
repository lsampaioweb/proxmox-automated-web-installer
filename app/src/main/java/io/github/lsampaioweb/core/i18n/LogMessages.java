package io.github.lsampaioweb.core.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * Resolves developer-facing log messages from the i18n bundles.
 */
@Component
@RequiredArgsConstructor
public class LogMessages {

  private final MessageSource messageSource;

  /**
   * Resolves a log message in English.
   *
   * @param key  message key
   * @param args format arguments
   * @return resolved message text
   */
  public String get(String key, Object... args) {
    return get(Locale.ENGLISH, key, args);
  }

  /**
   * Resolves a log message in the given locale.
   *
   * @param locale target locale
   * @param key    message key
   * @param args   format arguments
   * @return resolved message text
   */
  public String get(Locale locale, String key, Object... args) {
    return messageSource.getMessage(key, args, locale);
  }

}
