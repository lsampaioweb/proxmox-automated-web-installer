package io.github.lsampaioweb.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.lsampaioweb.core.exception.GlobalExceptionHandler;
import io.github.lsampaioweb.core.i18n.I18nLocaleResolverConfig;
import io.github.lsampaioweb.core.i18n.LogMessages;
import io.github.lsampaioweb.ProxmoxWebInstallerApplication;

@WebMvcTest(AnswerRestController.class)
@ContextConfiguration(classes = ProxmoxWebInstallerApplication.class)
@Import({ GlobalExceptionHandler.class, LogMessages.class, I18nLocaleResolverConfig.class })
class AnswerRestControllerTest {

  private static final MediaType APPLICATION_TOML = MediaType.parseMediaType("application/toml");
  private static final String MAC = "b4:2e:99:ad:b0:c0";
  private static final String VALID_BODY = """
      {
        "$schema": { "version": "1.0" },
        "product": { "fullname": "Proxmox VE", "product": "pve", "enable_btrfs": true },
        "iso": { "release": "9.0", "isorelease": "1" },
        "dmi": {
          "system": { "serial": "900030XXXX", "name": "RS700", "sku": "SKU", "uuid": "6fb36fd4-77a1-57e6-13ae-XXXXXXcb48d7" },
          "baseboard": { "name": "Z13PP-D32", "asset_tag": "To be filled by O.E.M.", "serial": "23041870000XXXX" },
          "chassis": { "asset_tag": "To be filled by O.E.M.", "serial": "I02308XXXX" }
        },
        "network_interfaces": [
          { "mac": "%s", "link": "enp1board0" },
          { "mac": "18:c0:4d:f1:fe:c9", "link": "enp1board0" }
        ]
      }
      """
      .formatted(MAC);
  private static final String UPPERCASE_MAC_BODY = """
      {
        "network_interfaces": [
          { "mac": "B4:2E:99:AD:B0:C0" }
        ]
      }
      """;
  private static final String EMPTY_MAC_BODY = """
      {
        "network_interfaces": [
          { "mac": "  " }
        ]
      }
      """;
  private static final String EMPTY_INTERFACES_BODY = """
      {
        "network_interfaces": []
      }
      """;
  private static final String MALFORMED_MAC_BODY = """
      {
        "network_interfaces": [
          { "mac": "b4:2e:99:ad:b0:cg" }
        ]
      }
      """;

  private final MockMvc mockMvc;

  @MockitoBean
  private AnswerService answerService;

  @Autowired
  AnswerRestControllerTest(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void shouldIgnoreUnknownProductFieldsAndReturnTomlBytesForValidLookup() throws Exception {
    byte[] toml = "[global]\nkeyboard = \"en-us\"\n".getBytes();
    when(answerService.findAnswer(any(AnswerRequest.class), anyString())).thenReturn(toml);

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_TOML))
        .andExpect(content().bytes(toml));

    ArgumentCaptor<AnswerRequest> requestCaptor = ArgumentCaptor.forClass(AnswerRequest.class);
    verify(answerService).findAnswer(requestCaptor.capture(), anyString());
    assertThat(requestCaptor.getValue().product())
        .isEqualTo(new AnswerRequest.Product("Proxmox VE", "pve"));
  }

  @Test
  void shouldAcceptUppercaseMacInRequestBody() throws Exception {
    byte[] toml = "ok = true\n".getBytes();
    when(answerService.findAnswer(any(AnswerRequest.class), anyString())).thenReturn(toml);

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(UPPERCASE_MAC_BODY))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_TOML))
        .andExpect(content().bytes(toml));
  }

  @Test
  void shouldReturn400ForInvalidJson() throws Exception {
    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .content("{ not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.errorCode").value("INVALID_JSON"))
        .andExpect(jsonPath("$.message").value("The request body is not valid JSON."))
        .andExpect(jsonPath("$.path").value("/answer"))
        .andExpect(jsonPath("$.fields").doesNotExist());
  }

  @Test
  void shouldLocalizeInvalidJsonMessageForBrazilianPortuguese() throws Exception {
    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "pt-BR")
        .content("{ not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("O corpo da requisição não é um JSON válido."));
  }

  @Test
  void shouldReturn400WhenNetworkInterfacesAreEmpty() throws Exception {
    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(EMPTY_INTERFACES_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields[0].field").value("networkInterfaces"));
  }

  @Test
  void shouldReturn400WhenNoUsableMacIsPresent() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(new InvalidAnswerRequestException());

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(EMPTY_MAC_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("ANSWER_MAC_REQUIRED"));
  }

  @Test
  void shouldReturnLocalized400ForMalformedMac() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(InvalidAnswerRequestException.forMalformedMacAddress());

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .content(MALFORMED_MAC_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("ANSWER_MAC_INVALID"))
        .andExpect(jsonPath("$.message").value("The network interface MAC address is invalid."));
  }

  @Test
  void shouldLocalizeMalformedMacErrorForBrazilianPortuguese() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(InvalidAnswerRequestException.forMalformedMacAddress());

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "pt-BR")
        .content(MALFORMED_MAC_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("ANSWER_MAC_INVALID"))
        .andExpect(jsonPath("$.message").value("O endereço MAC da interface de rede é inválido."));
  }

  @Test
  void shouldReturn404WhenAnswerFileIsMissing() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(new AnswerNotFoundException());

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("ANSWER_NOT_FOUND"));
  }

  @Test
  void shouldLocalizeDomainErrorMessageForBrazilianPortuguese() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(new AnswerNotFoundException());

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "pt-BR")
        .content(VALID_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("ANSWER_NOT_FOUND"))
        .andExpect(jsonPath("$.message")
            .value("Nenhum arquivo de resposta foi encontrado para os endereços MAC solicitados."));
  }

  @Test
  void shouldReturn500WhenAnswerFileIsUnreadable() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(new AnswerUnreadableException(new RuntimeException("io")));

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("ANSWER_UNREADABLE"));
  }

  @Test
  void shouldReturn500WhenAnswerFileIsInvalidToml() throws Exception {
    when(answerService.findAnswer(any(AnswerRequest.class), anyString()))
        .thenThrow(new InvalidAnswerTomlException(new RuntimeException("toml")));

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.errorCode").value("ANSWER_INVALID_TOML"));
  }

  @Test
  void shouldDelegateLookupToService() throws Exception {
    byte[] toml = "ok = true\n".getBytes();
    when(answerService.findAnswer(any(AnswerRequest.class), anyString())).thenReturn(toml);

    mockMvc.perform(post("/answer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(VALID_BODY))
        .andExpect(status().isOk());

    verify(answerService).findAnswer(any(AnswerRequest.class), anyString());
  }

}
