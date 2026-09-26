package io.github.lsampaioweb.answer;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Proxmox automated-installation HTTP source request.
 *
 * <p>
 * Known installer fields are mapped. Unknown keys are ignored so a newer
 * installer document does not fail lookup.
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
   * Schema version of the installer POST document.
   *
   * @param version schema version string
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DocumentSchema(String version) {
  }

  /**
   * Proxmox product identity from the installer POST.
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
   * Installer ISO release identity.
   *
   * @param release    ISO release
   * @param isorelease ISO point release
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Iso(String release, String isorelease) {
  }

  /**
   * DMI identity blocks from the installer POST.
   *
   * @param system    system DMI block
   * @param baseboard baseboard DMI block
   * @param chassis   chassis DMI block
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Dmi(SystemInfo system, Baseboard baseboard, Chassis chassis) {
  }

  /**
   * System DMI fields.
   *
   * @param serial system serial number
   * @param name   system product name
   * @param sku    system SKU
   * @param uuid   system UUID
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SystemInfo(String serial, String name, String sku, String uuid) {
  }

  /**
   * Baseboard DMI fields.
   *
   * @param name     baseboard name
   * @param assetTag baseboard asset tag
   * @param serial   baseboard serial number
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Baseboard(
      String name,
      @JsonProperty("asset_tag") String assetTag,
      String serial) {
  }

  /**
   * Chassis DMI fields.
   *
   * @param assetTag chassis asset tag
   * @param serial   chassis serial number
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Chassis(
      @JsonProperty("asset_tag") String assetTag,
      String serial) {
  }

}
