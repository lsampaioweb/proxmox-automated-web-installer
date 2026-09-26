package io.github.lsampaioweb.answer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One network interface reported by the Proxmox installer.
 *
 * @param mac hardware address of the interface
 * @param link kernel interface name reported by the installer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkInterfaceRequest(String mac, String link) {
}
