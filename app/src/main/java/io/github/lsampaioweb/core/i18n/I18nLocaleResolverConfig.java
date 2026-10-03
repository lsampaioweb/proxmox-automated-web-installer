package io.github.lsampaioweb.core.i18n;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Chooses the API locale from the {@code Accept-Language} header.
 */
@Configuration
public class I18nLocaleResolverConfig {

  private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

  /**
   * Creates a resolver for English and Brazilian Portuguese requests.
   *
   * @return resolver for English and {@code pt-BR}
   */
  @Bean
  LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.ENGLISH);
    resolver.setSupportedLocales(List.of(Locale.ENGLISH, LOCALE_PT_BR));

    return resolver;
  }

}
