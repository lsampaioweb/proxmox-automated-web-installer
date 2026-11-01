package io.github.lsampaioweb.firstboot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.github.lsampaioweb.core.i18n.LogMessages;

@Slf4j
@Service
@RequiredArgsConstructor
class FirstBootServiceImpl implements FirstBootService {

  private static final String SCRIPT_RESOURCE = "first-boot/network-vlan.sh";
  private static final String LOG_SCRIPT_SERVED = "log.first.boot.served";
  private static final String LOG_SCRIPT_FAILED = "log.first.boot.failed";
  private static final int MAX_SCRIPT_BYTES = 1024 * 1024;

  private final FirstBootMapper mapper;
  private final LogMessages logMessages;

  @Override
  public byte[] script(Map<String, List<String>> parameters, String clientAddress, Locale locale) {
    FirstBootConfiguration configuration = mapper.toConfiguration(parameters);
    String template;
    try {
      template = new ClassPathResource(SCRIPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException ex) {
      log.error(logMessages.get(locale, LOG_SCRIPT_FAILED), ex);
      throw new FirstBootException(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    byte[] script = mapper.toScript(template, configuration);
    if (!template.startsWith("#!/bin/bash\n") || script.length > MAX_SCRIPT_BYTES
        || template.indexOf("__BRIDGE__") < 0 || template.indexOf("__VLAN_ID__") < 0) {
      log.error(logMessages.get(locale, LOG_SCRIPT_FAILED));
      throw new FirstBootException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    log.info(logMessages.get(locale, LOG_SCRIPT_SERVED, Instant.now(), LogMessages.safeField(clientAddress),
        configuration.bridge(), Integer.toString(configuration.vlanId()), Integer.toString(script.length)));

    return script;
  }
}
