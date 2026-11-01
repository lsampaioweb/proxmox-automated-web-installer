package io.github.lsampaioweb.core.i18n;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Looks up log messages in the locale bundles.
 */
@Component
public class LogMessages {

  private final MessageSource messageSource;
  private final Locale defaultLocale;

  /**
   * Creates a resolver for localized log messages.
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
   * Looks up a log message in the configured default locale.
   *
   * @param key  message key
   * @param args format arguments
   * @return resolved message text
   */
  public String get(String key, Object... args) {
    return get(defaultLocale, key, args);
  }

  /**
   * Looks up a log message in the requested locale.
   *
   * @param locale target locale
   * @param key    message key
   * @param args   format arguments
   * @return resolved message text
   */
  public String get(Locale locale, String key, Object... args) {
    return messageSource.getMessage(key, args, locale);
  }

  /** Removes line-breaking characters from request-derived log fields. */
  public static String safeField(String value) {
    if (value == null) {
      return "";
    }

    return value.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", "_");
  }

}
