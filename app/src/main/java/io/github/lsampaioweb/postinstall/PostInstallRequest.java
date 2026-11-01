package io.github.lsampaioweb.postinstall;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reads only the permitted metadata from a Proxmox installation report.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostInstallRequest(
    @NotNull(message = "{error.validation.failed}") @Valid Product product,
    @NotBlank(message = "{error.validation.failed}")
    @Size(max = 253, message = "{error.validation.failed}") String fqdn,
    @Valid Dmi dmi,
    @JsonProperty("network-interfaces")
    @NotEmpty(message = "{error.validation.network.interfaces.required}")
    @Size(max = 128, message = "{error.validation.failed}")
    List<@NotNull(message = "{error.validation.failed}") @Valid NetworkInterface> networkInterfaces) {

  /** Identifies the installed product. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Product(
      @JsonProperty("short") @NotBlank(message = "{error.validation.failed}")
      @Size(max = 64, message = "{error.validation.failed}") String shortName,
      @Size(max = 256, message = "{error.validation.failed}") String fullname,
      @Size(max = 256, message = "{error.validation.failed}") String version) {
  }

  /** Selects system metadata without retaining other DMI fields. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Dmi(@Valid SystemInfo system) {
  }

  /** Carries the optional system UUID. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SystemInfo(@Size(max = 128, message = "{error.validation.failed}") String uuid) {
  }

  /** Identifies an interface without retaining its addresses or udev data. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NetworkInterface(
      @Size(max = 64, message = "{error.validation.failed}") String name,
      @NotBlank(message = "{error.answer.mac.required}")
      @Pattern(regexp = "[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}", message = "{error.answer.mac.invalid}") String mac) {
  }
}
