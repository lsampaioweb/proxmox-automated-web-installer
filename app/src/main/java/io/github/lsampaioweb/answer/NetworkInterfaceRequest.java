package io.github.lsampaioweb.answer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One network interface reported by the Proxmox installer.
 *
 * @param mac hardware address of the interface
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkInterfaceRequest(String mac) {
}
