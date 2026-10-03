package io.github.lsampaioweb.core.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class LogMessagesTest {

  private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");

  @Mock
  private MessageSource messageSource;

  @Test
  void shouldUseConfiguredDefaultLocaleWithoutRequestContext() {
    LogMessages logMessages = new LogMessages(messageSource, "pt-BR");
    when(messageSource.getMessage(eq("log.sample"), any(Object[].class), eq(PORTUGUESE)))
        .thenReturn("mensagem localizada");

    String message = logMessages.get("log.sample");

    assertThat(message).isEqualTo("mensagem localizada");
    verify(messageSource).getMessage(eq("log.sample"), any(Object[].class), eq(PORTUGUESE));
  }

}
