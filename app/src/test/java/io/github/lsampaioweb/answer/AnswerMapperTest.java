package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnswerMapperTest {

  private final AnswerMapper mapper = new AnswerMapperImpl();

  @Test
  void shouldReturnEveryUsableMacInRequestOrder() {
    AnswerRequest request = new AnswerRequest(null, null, null, null, List.of(
        new NetworkInterfaceRequest("  B4:2E:99:AD:B0:C0  "),
        new NetworkInterfaceRequest("  "),
        new NetworkInterfaceRequest("18:c0:4d:f1:fe:c9")));

    assertThat(mapper.normalizedMacs(request))
        .containsExactly("b4:2e:99:ad:b0:c0", "18:c0:4d:f1:fe:c9");
  }

  @Test
  void shouldIgnoreMissingAndBlankMacs() {
    AnswerRequest request = new AnswerRequest(null, null, null, null,
        Arrays.asList(null, new NetworkInterfaceRequest(null), new NetworkInterfaceRequest("  ")));

    assertThat(mapper.normalizedMacs(request)).isEmpty();
    assertThat(mapper.normalizedMacs(new AnswerRequest(null, null, null, null, null))).isEmpty();
  }

  @Test
  void shouldRejectMalformedMacEvenWhenAValidMacWasAlreadyCollected() {
    for (String malformedMac : List.of(
        "b4:2e:99:ad:b0:c",
        "b4:2e:99:ad:b0:c0:01",
        "b4:2e:99:ad:b0:cg",
        "b4-2e-99-ad-b0-c0",
        "b４:2e:99:ad:b0:c0")) {
      AnswerRequest request = new AnswerRequest(null, null, null, null, List.of(
          new NetworkInterfaceRequest("b4:2e:99:ad:b0:c0"),
          new NetworkInterfaceRequest(malformedMac)));

      assertThatThrownBy(() -> mapper.normalizedMacs(request))
          .isInstanceOfSatisfying(InvalidAnswerRequestException.class,
              exception -> assertThat(exception.getErrorCode()).isEqualTo("ANSWER_MAC_INVALID"));
    }
  }

}
