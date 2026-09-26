package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnswerMapperTest {

  private final AnswerMapper mapper = new AnswerMapperImpl();

  @Test
  void shouldReturnEveryUsableMacInRequestOrder() {
    AnswerRequest request = new AnswerRequest(null, null, null, null, List.of(
        new NetworkInterfaceRequest("B4:2E:99:AD:B0:C0", "enp1board0"),
        new NetworkInterfaceRequest("  ", null),
        new NetworkInterfaceRequest("18:c0:4d:f1:fe:c9", "enp1board0")));

    assertThat(mapper.normalizedMacs(request))
        .containsExactly("b4:2e:99:ad:b0:c0", "18:c0:4d:f1:fe:c9");
  }

  @Test
  void shouldIgnoreBlankMacs() {
    AnswerRequest request = new AnswerRequest(null, null, null, null,
        List.of(new NetworkInterfaceRequest("  ", null)));

    assertThat(mapper.normalizedMacs(request)).isEmpty();
  }

}
