package io.github.lsampaioweb.postinstall;

import java.time.Instant;
import java.util.List;

record PostInstallEvent(Instant receivedAt, String product, String fullname, String version,
    String fqdn, String systemUuid, List<NetworkInterface> interfaces) {

  record NetworkInterface(String name, String mac) {
  }
}
