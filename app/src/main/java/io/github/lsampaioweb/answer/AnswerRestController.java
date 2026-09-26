package io.github.lsampaioweb.answer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Exposes the Proxmox automated-installation HTTP source endpoint.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Answer")
class AnswerRestController {

  private static final MediaType APPLICATION_TOML = MediaType.parseMediaType("application/toml");

  private final AnswerService answerService;

  /**
   * Returns the answer file selected from an installer-reported MAC.
   *
   * @param request     installer JSON body
   * @param httpRequest current HTTP request
   * @return exact TOML file bytes
   */
  @PostMapping("/answer")
  public ResponseEntity<byte[]> findAnswer(@Valid @RequestBody AnswerRequest request, HttpServletRequest httpRequest) {
    byte[] answerFile = answerService.findAnswer(request, httpRequest.getRemoteAddr());

    return ResponseEntity.ok()
        .contentType(APPLICATION_TOML)
        .body(answerFile);
  }

}
