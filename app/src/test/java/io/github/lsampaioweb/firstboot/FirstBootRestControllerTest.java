package io.github.lsampaioweb.firstboot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.lsampaioweb.ProxmoxWebInstallerApplication;
import io.github.lsampaioweb.core.exception.GlobalExceptionHandler;
import io.github.lsampaioweb.core.i18n.I18nLocaleResolverConfig;
import io.github.lsampaioweb.core.i18n.LogMessages;

@WebMvcTest(FirstBootRestController.class)
@ContextConfiguration(classes = ProxmoxWebInstallerApplication.class)
@Import({ GlobalExceptionHandler.class, I18nLocaleResolverConfig.class })
class FirstBootRestControllerTest {

  private final MockMvc mockMvc;

  @MockitoBean
  private FirstBootService service;

  @MockitoBean
  private LogMessages logMessages;

  @Autowired
  FirstBootRestControllerTest(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void returnsScriptBytesAndPassesParametersToService() throws Exception {
    byte[] script = "#!/bin/bash\n".getBytes(StandardCharsets.UTF_8);
    when(service.script(any(), any())).thenReturn(script);
    mockMvc.perform(get("/first-boot").param("interface", "vmbr0").param("vlan-id", "1110"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/x-shellscript"))
        .andExpect(content().bytes(script));
    verify(service).script(Map.of("interface", List.of("vmbr0"), "vlan-id", List.of("1110")), Locale.ENGLISH);
  }

  @Test
  void preservesDuplicateValuesForServiceValidationAndLocalizesErrors() throws Exception {
    when(service.script(any(), any())).thenThrow(new FirstBootException(HttpStatus.BAD_REQUEST));
    mockMvc.perform(get("/first-boot").header("Accept-Language", "pt-BR")
        .param("interface", "vmbr0").param("vlan-id", "1110", "3"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("FIRST_BOOT_PARAMETERS_INVALID"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Informe exatamente")));
    verify(service).script(eq(Map.of("interface", List.of("vmbr0"), "vlan-id", List.of("1110", "3"))),
        eq(Locale.forLanguageTag("pt-BR")));
  }

  @Test
  void rejectsWrongMethod() throws Exception {
    mockMvc.perform(post("/first-boot")).andExpect(status().isMethodNotAllowed());
  }
}
