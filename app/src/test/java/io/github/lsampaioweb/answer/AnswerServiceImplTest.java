package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.lsampaioweb.core.i18n.LogMessages;

@ExtendWith(MockitoExtension.class)
class AnswerServiceImplTest {

  private static final String CLIENT = "192.0.2.10";
  private static final String FIRST_MAC = "b4:2e:99:ad:b0:c0";
  private static final String SECOND_MAC = "18:c0:4d:f1:fe:c9";
  private static final String FIRST_FILENAME = FIRST_MAC + AnswerProperties.ANSWER_FILE_EXTENSION;
  private static final String SECOND_FILENAME = SECOND_MAC + AnswerProperties.ANSWER_FILE_EXTENSION;
  private static final String LOG_AUDIT = "log.answer.audit";
  private static final Locale REQUEST_LOCALE = Locale.ENGLISH;

  @Mock
  private AnswerMapper answerMapper;

  @Mock
  private AnswerRepository answerRepository;

  @Mock
  private LogMessages logMessages;

  private AnswerServiceImpl answerService;

  @BeforeEach
  void setUp() {
    answerService = new AnswerServiceImpl(answerMapper, answerRepository, logMessages);
    when(logMessages.get(any(Locale.class), eq(LOG_AUDIT), any(Object[].class))).thenReturn("audit");
  }

  @Test
  void shouldReturnTheFirstMatchingAnswerFile() {
    byte[] toml = "[global]\nkeyboard = \"en-us\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn(toml);

    byte[] result = answerService.findAnswer(request, CLIENT, REQUEST_LOCALE);

    assertThat(result).isEqualTo(toml);
    verify(answerRepository).readByMac(FIRST_MAC);
    verify(answerRepository, never()).readByMac(SECOND_MAC);
    verifyAudit(FIRST_MAC, FIRST_FILENAME, "SUCCESS", "");
  }

  @Test
  void shouldResolveAuditLogUsingRequestLocale() {
    Locale requestLocale = Locale.forLanguageTag("pt-BR");
    AnswerRequest request = requestWith(FIRST_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn("[global]".getBytes(StandardCharsets.UTF_8));

    answerService.findAnswer(request, CLIENT, requestLocale);

    verify(logMessages).get(eq(requestLocale), eq(LOG_AUDIT), any(Object[].class));
  }

  @Test
  void shouldUseALaterMacWhenEarlierFilesAreMissing() {
    byte[] toml = "[global]\nkeyboard = \"en-us\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenThrow(new AnswerNotFoundException());
    when(answerRepository.readByMac(SECOND_MAC)).thenReturn(toml);

    byte[] result = answerService.findAnswer(request, CLIENT, REQUEST_LOCALE);

    assertThat(result).isEqualTo(toml);
    verifyAudit(SECOND_MAC, SECOND_FILENAME, "SUCCESS", "");
  }

  @Test
  void shouldRejectRequestWithoutUsableMac() {
    AnswerRequest request = requestWith("  ");
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of());

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT, REQUEST_LOCALE))
        .isInstanceOf(InvalidAnswerRequestException.class);

    verifyAudit("", "", "BAD_REQUEST", "ANSWER_MAC_REQUIRED");
  }

  @Test
  void shouldAuditMalformedMacWithoutLookingUpAnyAnswerFile() {
    String malformedMac = "b4:2e:99:ad:b0:cg";
    AnswerRequest request = requestWith(FIRST_MAC, malformedMac);
    when(answerMapper.normalizedMacs(request)).thenThrow(InvalidAnswerRequestException.forMalformedMacAddress());

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT, REQUEST_LOCALE))
        .isInstanceOf(InvalidAnswerRequestException.class);

    verify(answerRepository, never()).readByMac(FIRST_MAC);
    verify(answerRepository, never()).readByMac(SECOND_MAC);
    verifyAudit("", "", "BAD_REQUEST", "ANSWER_MAC_INVALID");

    ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
    verify(logMessages).get(eq(REQUEST_LOCALE), eq(LOG_AUDIT), captor.capture());
    assertThat(captor.getValue()[12]).isEqualTo(FIRST_MAC + "," + malformedMac);
    assertThat(captor.getValue()[13]).isEqualTo("");
    assertThat(captor.getValue()[14]).isEqualTo("");
  }

  @Test
  void shouldReturnNotFoundWhenNoMacHasAnAnswerFile() {
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenThrow(new AnswerNotFoundException());
    when(answerRepository.readByMac(SECOND_MAC)).thenThrow(new AnswerNotFoundException());

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT, REQUEST_LOCALE))
        .isInstanceOf(AnswerNotFoundException.class);

    verifyAudit(SECOND_MAC, SECOND_FILENAME, "NOT_FOUND", "ANSWER_NOT_FOUND");
  }

  @Test
  void shouldNotSkipAnUnreadableFileToTryTheNextMac() {
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC))
        .thenThrow(new AnswerUnreadableException(new RuntimeException("io")));

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT, REQUEST_LOCALE))
        .isInstanceOf(AnswerUnreadableException.class);

    verify(answerRepository, never()).readByMac(SECOND_MAC);
    verifyAudit(FIRST_MAC, FIRST_FILENAME, "UNREADABLE", "ANSWER_UNREADABLE");
  }

  @Test
  void shouldAuditUnexpectedRuntimeFailureAsInternalError() {
    AnswerRequest request = requestWith(FIRST_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenThrow(new IllegalStateException("unexpected"));

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT, REQUEST_LOCALE))
        .isInstanceOf(IllegalStateException.class);

    verifyAudit(FIRST_MAC, FIRST_FILENAME, "INTERNAL_ERROR", "INTERNAL_ERROR");
  }

  @Test
  void shouldNotPassTomlContentToAuditLog() {
    byte[] toml = "password = \"secret-install-token\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn(toml);

    answerService.findAnswer(request, CLIENT, REQUEST_LOCALE);

    ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
    verify(logMessages).get(eq(REQUEST_LOCALE), eq(LOG_AUDIT), captor.capture());
    assertThat(captor.getAllValues())
        .flatExtracting(List::of)
        .doesNotContain((Object) toml)
        .noneMatch(value -> String.valueOf(value).contains("secret-install-token"));
  }

  @Test
  void shouldEscapeLineBreaksInRequestMetadataBeforeAuditLogging() {
    AnswerRequest request = new AnswerRequest(null,
        new AnswerRequest.Product("Proxmox\nforged-outcome=SUCCESS", "pve\r\nforged-field=value"),
        null,
        new AnswerRequest.Dmi(new AnswerRequest.SystemInfo("serial\u2028forged-field=value", null, null),
            null, null),
        List.of(new NetworkInterfaceRequest(FIRST_MAC)));
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn("[global]".getBytes(StandardCharsets.UTF_8));

    answerService.findAnswer(request, CLIENT, REQUEST_LOCALE);

    ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
    verify(logMessages).get(eq(REQUEST_LOCALE), eq(LOG_AUDIT), captor.capture());
    List<String> loggedFields = Arrays.stream(captor.getValue())
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();

    assertThat(loggedFields)
        .contains("Proxmox_forged-outcome=SUCCESS", "pve__forged-field=value", "serial_forged-field=value")
        .noneMatch(field -> field.contains("\n") || field.contains("\r") || field.contains("\u2028"));
  }

  private void verifyAudit(String mac, String filename, String outcome, String errorCode) {
    ArgumentCaptor<Object[]> auditFields = ArgumentCaptor.forClass(Object[].class);
    verify(logMessages).get(eq(REQUEST_LOCALE), eq(LOG_AUDIT), auditFields.capture());
    Object[] fields = auditFields.getValue();

    assertThat(fields).hasSize(18);
    assertThat(fields[1]).isEqualTo(CLIENT);
    assertThat(fields[13]).isEqualTo(mac);
    assertThat(fields[14]).isEqualTo(filename);
    assertThat(fields[15]).isEqualTo(outcome);
    assertThat(fields[16]).isEqualTo(errorCode);
  }

  private AnswerRequest requestWith(String... macs) {
    List<NetworkInterfaceRequest> interfaces = Arrays.stream(macs)
        .map(NetworkInterfaceRequest::new)
        .toList();

    return new AnswerRequest(null, null, null, null, interfaces);
  }

}
