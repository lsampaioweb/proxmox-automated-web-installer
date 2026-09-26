package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RootPasswordHashConfigurationTest {

  @Test
  void createsYescryptHashForConfiguredPassword() {
    RootPasswordHash hash = new RootPasswordHashConfiguration().rootPasswordHash("test-root-password");

    assertThat(hash.value()).startsWith("$y$");
  }

}