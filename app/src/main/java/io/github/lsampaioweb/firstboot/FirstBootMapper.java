package io.github.lsampaioweb.firstboot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class FirstBootMapper {

  FirstBootConfiguration toConfiguration(Map<String, List<String>> parameters) {
    if (parameters.size() != 2) {
      throw new FirstBootException(HttpStatus.BAD_REQUEST);
    }
    String bridge = singleValue(parameters, "interface");
    String vlan = singleValue(parameters, "vlan-id");
    if (!bridge.matches("vmbr[0-9]+") || !vlan.matches("[1-9][0-9]{0,3}")
        || bridge.length() + 1 + vlan.length() > 15) {
      throw new FirstBootException(HttpStatus.BAD_REQUEST);
    }
    int vlanId = Integer.parseInt(vlan);
    if (vlanId > 4094) {
      throw new FirstBootException(HttpStatus.BAD_REQUEST);
    }

    return new FirstBootConfiguration(bridge, vlanId);
  }

  byte[] toScript(String template, FirstBootConfiguration configuration) {
    return template.replace("__BRIDGE__", configuration.bridge())
        .replace("__VLAN_ID__", Integer.toString(configuration.vlanId()))
        .getBytes(StandardCharsets.UTF_8);
  }

  private String singleValue(Map<String, List<String>> parameters, String key) {
    List<String> values = parameters.get(key);
    if (values == null || values.size() != 1 || values.getFirst() == null) {
      throw new FirstBootException(HttpStatus.BAD_REQUEST);
    }

    return values.getFirst();
  }
}
