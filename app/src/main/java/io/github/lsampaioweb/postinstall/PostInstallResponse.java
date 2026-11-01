package io.github.lsampaioweb.postinstall;

import java.time.Instant;

/** Acknowledges receipt, not execution of any first-boot script. */
public record PostInstallResponse(boolean received, Instant receivedAt) {
}
