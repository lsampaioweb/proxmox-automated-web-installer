package io.github.lsampaioweb.firstboot;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides a validated first-boot script without executing it. */
public interface FirstBootService {

  /** Renders a script for exactly one bridge and VLAN query value. */
  byte[] script(Map<String, List<String>> parameters, Locale locale);
}
