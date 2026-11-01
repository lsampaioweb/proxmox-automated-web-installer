package io.github.lsampaioweb.answer;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request sent by the Proxmox automated installer.
 *
 * <p>
 * The fields used by this service are mapped here. Unknown fields are ignored,
 * so newer installer versions can add data without breaking lookups.
 *
 * @param schema            document schema metadata
 * @param product           product being installed
 * @param iso               installer ISO identity
 * @param dmi               hardware identity reported by the installer
 * @param networkInterfaces installer-reported network interfaces
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnswerRequest(
    @JsonProperty("$schema") DocumentSchema schema,
    Product product,
    Iso iso,
    Dmi dmi,
    @JsonProperty("network_interfaces") @NotEmpty(message = "{error.validation.network.interfaces.required}") List<NetworkInterfaceRequest> networkInterfaces) {

  /**
   * Schema version reported by the installer.
   *
   * @param version schema version string
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentSchema(String version) {
  }

  /**
   * Product details sent by the installer.
   *
   * @param fullname human-readable product name
   * @param product  short product code
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Product(
      String fullname,
      String product) {
  }

  /**
   * Release details for the installer ISO.
   *
   * @param release    ISO release
   * @param isorelease ISO point release
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Iso(String release, String isorelease) {
  }

  /**
   * Hardware details reported through DMI.
   *
   * @param system    system DMI block
   * @param baseboard baseboard DMI block
   * @param chassis   chassis DMI block
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Dmi(SystemInfo system, Baseboard baseboard, Chassis chassis) {
  }

  /**
   * System identifiers used in audit logs.
   *
   * @param serial system serial number
   * @param name   system product name
   * @param uuid   system UUID
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SystemInfo(String serial, String name, String uuid) {
  }

  /**
   * Baseboard details used in audit logs.
   *
   * @param serial baseboard serial number
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Baseboard(String serial) {
  }

  /**
   * Chassis details used in audit logs.
   *
   * @param serial chassis serial number
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Chassis(String serial) {
  }

}
