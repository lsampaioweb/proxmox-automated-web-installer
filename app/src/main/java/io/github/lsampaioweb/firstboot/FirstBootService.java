package io.github.lsampaioweb.firstboot;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides a validated first-boot script without executing it. */
public interface FirstBootService {

  /**
   * Renders a script for exactly one bridge and VLAN query value.
   *
   * @param parameters query parameters of the request
   * @param clientAddress remote address recorded when the script is served
   * @param locale locale used for errors and log messages
   * @return the rendered script
   */
  byte[] script(Map<String, List<String>> parameters, String clientAddress, Locale locale);
}
