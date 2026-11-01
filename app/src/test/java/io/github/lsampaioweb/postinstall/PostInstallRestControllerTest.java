package io.github.lsampaioweb.postinstall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.lsampaioweb.ProxmoxWebInstallerApplication;
import io.github.lsampaioweb.core.exception.GlobalExceptionHandler;
import io.github.lsampaioweb.core.i18n.I18nLocaleResolverConfig;
import io.github.lsampaioweb.core.i18n.LogMessages;

@WebMvcTest(PostInstallRestController.class)
@ContextConfiguration(classes = ProxmoxWebInstallerApplication.class)
@Import({ GlobalExceptionHandler.class, I18nLocaleResolverConfig.class })
class PostInstallRestControllerTest {

  private static final String URL = "/api/v1/post-install-events";
  private static final String BODY = """
      {"$schema":{"version":"1.2"},
       "product":{"short":"pve","fullname":"Proxmox VE","version":"9.2"},
       "fqdn":"pve-01.lan.home",
       "dmi":{"system":{"uuid":"node-uuid","serial":"SECRET-SERIAL"}},
       "network-interfaces":[{"name":"enp2lacp3","mac":"60:be:b4:08:f7:69",
         "address":"SECRET-ADDRESS","udev-properties":{"token":"SECRET-UDEV"}}],
       "machine-id":"SECRET-MACHINE","ssh-public-host-keys":{"rsa":"SECRET-KEY"}}
      """;

  private final MockMvc mockMvc;

  @MockitoBean
  private PostInstallService service;

  @MockitoBean
  private LogMessages logMessages;

  @Autowired
  PostInstallRestControllerTest(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void receivesActualProxmoxPayloadAndIgnoresUnknownFields() throws Exception {
    Instant receivedAt = Instant.parse("2026-10-26T12:00:00Z");
    when(service.receive(any(), eq(Locale.forLanguageTag("pt-BR"))))
        .thenReturn(new PostInstallResponse(true, receivedAt));
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "pt-BR").content(BODY))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.received").value(true))
        .andExpect(jsonPath("$.receivedAt").value(receivedAt.toString()));
    ArgumentCaptor<PostInstallRequest> request = ArgumentCaptor.forClass(PostInstallRequest.class);
    verify(service).receive(request.capture(), eq(Locale.forLanguageTag("pt-BR")));
    assertThat(request.getValue().networkInterfaces().getFirst().name()).isEqualTo("enp2lacp3");
    assertThat(request.getValue().toString()).doesNotContain("SECRET");
  }

  @Test
  void acceptsAbsentOptionalDmiAndInterfaceName() throws Exception {
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
        {"product":{"short":"pve"},"fqdn":"pve-01.lan.home",
         "network-interfaces":[{"mac":"60:BE:B4:08:F7:69"}]}
        """))
        .andExpect(status().isOk());
    verify(service).receive(any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}",
      "{\"product\":{\"short\":\"pve\"},\"fqdn\":\"host\",\"network_interfaces\":[{\"mac\":\"60:be:b4:08:f7:69\"}]}",
      "{\"product\":{\"short\":\"pve\"},\"fqdn\":\"host\",\"network-interfaces\":[null]}",
      "{\"product\":{\"short\":\"pve\"},\"fqdn\":\"host\",\"network-interfaces\":[{\"mac\":\"malformed\"}]}"
  })
  void rejectsMissingRequiredFieldsAndInvalidInterfaces(String body) throws Exception {
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    verifyNoInteractions(service);
  }

  @Test
  void rejectsMalformedJsonWithoutInvokingService() throws Exception {
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INVALID_JSON"));
    verifyNoInteractions(service);
  }

  @Test
  void enforcesBodyLimitBeforeParsing() throws Exception {
    mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("x".repeat(1024 * 1024 + 1)))
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_TOO_LARGE"));
    verifyNoInteractions(service);
  }

  @Test
  void rejectsWrongMethodAndMediaType() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
    mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(BODY))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(service);
  }
}
