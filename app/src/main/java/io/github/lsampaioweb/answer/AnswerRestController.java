package io.github.lsampaioweb.answer;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.RequestContextUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Handles requests from the Proxmox automated installer.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Answer")
class AnswerRestController {

  private static final MediaType APPLICATION_TOML = MediaType.parseMediaType("application/toml");

  private final AnswerService answerService;

  /**
   * Returns the answer file selected from the installer's MAC addresses.
   *
   * @param request     JSON sent by the installer
   * @param httpRequest current HTTP request
   * @return TOML answer file
   */
  @PostMapping("/answer")
  public ResponseEntity<byte[]> findAnswer(@Valid @RequestBody AnswerRequest request, HttpServletRequest httpRequest) {
    Locale locale = RequestContextUtils.getLocale(httpRequest);
    byte[] answerFile = answerService.findAnswer(request, httpRequest.getRemoteAddr(), locale);

    return ResponseEntity.ok()
        .contentType(APPLICATION_TOML)
        .body(answerFile);
  }

}
