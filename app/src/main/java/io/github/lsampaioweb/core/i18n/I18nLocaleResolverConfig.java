package io.github.lsampaioweb.core.i18n;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configures Accept-Language locale resolution for API responses.
 */
@Configuration
public class I18nLocaleResolverConfig {

  private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

  /**
   * Resolves the request locale from the Accept-Language header.
   *
   * @return locale resolver limited to English and pt-BR
   */
  @Bean
  LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.ENGLISH);
    resolver.setSupportedLocales(List.of(Locale.ENGLISH, LOCALE_PT_BR));

    return resolver;
  }

}
