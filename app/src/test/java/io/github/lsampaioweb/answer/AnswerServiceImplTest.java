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
  private static final String FIRST_FILENAME = "b4:2e:99:ad:b0:c0.toml";
  private static final String SECOND_FILENAME = "18:c0:4d:f1:fe:c9.toml";
  private static final String LOG_AUDIT = "log.answer.audit";

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
    when(logMessages.get(eq(LOG_AUDIT), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any())).thenReturn("audit");
  }

  @Test
  void shouldReturnTheFirstMatchingAnswerFile() {
    byte[] toml = "[global]\nkeyboard = \"en-us\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn(toml);

    byte[] result = answerService.findAnswer(request, CLIENT);

    assertThat(result).isEqualTo(toml);
    verify(answerRepository).readByMac(FIRST_MAC);
    verify(answerRepository, never()).readByMac(SECOND_MAC);
    verifyAudit(FIRST_MAC, FIRST_FILENAME, "SUCCESS", "");
  }

  @Test
  void shouldUseALaterMacWhenEarlierFilesAreMissing() {
    byte[] toml = "[global]\nkeyboard = \"en-us\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenThrow(new AnswerNotFoundException());
    when(answerRepository.readByMac(SECOND_MAC)).thenReturn(toml);

    byte[] result = answerService.findAnswer(request, CLIENT);

    assertThat(result).isEqualTo(toml);
    verifyAudit(SECOND_MAC, SECOND_FILENAME, "SUCCESS", "");
  }

  @Test
  void shouldRejectRequestWithoutUsableMac() {
    AnswerRequest request = requestWith("  ");
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of());

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT))
        .isInstanceOf(InvalidAnswerRequestException.class);

    verifyAudit("", "", "BAD_REQUEST", "ANSWER_MAC_REQUIRED");
  }

  @Test
  void shouldReturnNotFoundWhenNoMacHasAnAnswerFile() {
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenThrow(new AnswerNotFoundException());
    when(answerRepository.readByMac(SECOND_MAC)).thenThrow(new AnswerNotFoundException());

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT))
        .isInstanceOf(AnswerNotFoundException.class);

    verifyAudit(SECOND_MAC, SECOND_FILENAME, "NOT_FOUND", "ANSWER_NOT_FOUND");
  }

  @Test
  void shouldNotSkipAnUnreadableFileToTryTheNextMac() {
    AnswerRequest request = requestWith(FIRST_MAC, SECOND_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC, SECOND_MAC));
    when(answerRepository.readByMac(FIRST_MAC))
        .thenThrow(new AnswerUnreadableException(new RuntimeException("io")));

    assertThatThrownBy(() -> answerService.findAnswer(request, CLIENT))
        .isInstanceOf(AnswerUnreadableException.class);

    verify(answerRepository, never()).readByMac(SECOND_MAC);
    verifyAudit(FIRST_MAC, FIRST_FILENAME, "UNREADABLE", "ANSWER_UNREADABLE");
  }

  @Test
  void shouldNotPassTomlContentToAuditLog() {
    byte[] toml = "password = \"secret-install-token\"\n".getBytes(StandardCharsets.UTF_8);
    AnswerRequest request = requestWith(FIRST_MAC);
    when(answerMapper.normalizedMacs(request)).thenReturn(List.of(FIRST_MAC));
    when(answerRepository.readByMac(FIRST_MAC)).thenReturn(toml);

    answerService.findAnswer(request, CLIENT);

    ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
    verify(logMessages).get(eq(LOG_AUDIT), captor.capture());
    assertThat(captor.getAllValues())
        .flatExtracting(List::of)
        .doesNotContain((Object) toml)
        .noneMatch(value -> String.valueOf(value).contains("secret-install-token"));
  }

  private void verifyAudit(String mac, String filename, String outcome, String errorCode) {
    verify(logMessages).get(
        eq(LOG_AUDIT),
        any(),
        eq(CLIENT),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        eq(mac),
        eq(filename),
        eq(outcome),
        eq(errorCode),
        any());
  }

  private AnswerRequest requestWith(String... macs) {
    List<NetworkInterfaceRequest> interfaces = Arrays.stream(macs)
        .map(mac -> new NetworkInterfaceRequest(mac, null))
        .toList();

    return new AnswerRequest(null, null, null, null, interfaces);
  }

}
