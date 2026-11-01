package io.github.lsampaioweb.postinstall;

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

@RestController
@RequiredArgsConstructor
@Tag(name = "Post-install")
class PostInstallRestController {

  private final PostInstallService service;

  /** Receives the Proxmox successful-installation webhook. */
  @PostMapping(value = "/api/v1/post-install-events", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PostInstallResponse> receive(@Valid @RequestBody PostInstallRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(service.receive(request, RequestContextUtils.getLocale(httpRequest)));
  }
}
