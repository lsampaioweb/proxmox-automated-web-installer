package io.github.lsampaioweb.core.i18n;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Resolves developer-facing log messages from the i18n bundles.
 */
@Component
public class LogMessages {

  private final MessageSource messageSource;
  private final Locale defaultLocale;

  /**
   * Creates the logging message resolver.
   *
   * @param messageSource    localized message source
   * @param defaultLocaleTag configured default locale language tag
   */
  public LogMessages(MessageSource messageSource,
      @Value("${spring.messages.default-locale:en}") String defaultLocaleTag) {
    this.messageSource = messageSource;
    this.defaultLocale = Locale.forLanguageTag(defaultLocaleTag);
  }

  /**
   * Resolves a log message in the configured default locale.
   *
   * @param key  message key
   * @param args format arguments
   * @return resolved message text
   */
  public String get(String key, Object... args) {
    return get(defaultLocale, key, args);
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
