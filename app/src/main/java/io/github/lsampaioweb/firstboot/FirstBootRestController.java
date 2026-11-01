package io.github.lsampaioweb.firstboot;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.RequestContextUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "First-boot")
class FirstBootRestController {

  private static final MediaType SHELL_SCRIPT = MediaType.parseMediaType("text/x-shellscript");

  private final FirstBootService service;

  /** Returns the executable downloaded by Proxmox during installation. */
  @GetMapping("/first-boot")
  public ResponseEntity<byte[]> script(@RequestParam MultiValueMap<String, String> parameters,
      HttpServletRequest request) {
    return ResponseEntity.ok().contentType(SHELL_SCRIPT)
        .body(service.script(parameters, RequestContextUtils.getLocale(request)));
  }
}
