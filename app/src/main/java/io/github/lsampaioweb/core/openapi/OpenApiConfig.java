package io.github.lsampaioweb.core.openapi;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configures OpenAPI document metadata for the exposed REST API.
 */
@Configuration
public class OpenApiConfig {

  private static final String API_VERSION = "v1";
  private static final String TITLE_KEY = "openapi.title";
  private static final String DESCRIPTION_KEY = "openapi.description";

  /**
   * Builds the OpenAPI info block resolved from the default locale.
   *
   * @param messageSource i18n message source
   * @return configured OpenAPI document metadata
   */
  @Bean
  OpenAPI customOpenApi(MessageSource messageSource) {
    String title = messageSource.getMessage(TITLE_KEY, null, Locale.ENGLISH);
    String description = messageSource.getMessage(DESCRIPTION_KEY, null, Locale.ENGLISH);

    return new OpenAPI().info(new Info()
        .title(title)
        .description(description)
        .version(API_VERSION));
  }

}
