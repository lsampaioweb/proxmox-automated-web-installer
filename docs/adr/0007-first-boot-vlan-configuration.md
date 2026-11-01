# ADR-0007: First-Boot VLAN Configuration

**Status:** Accepted
**Date:** 2026-10-26

## Context

The automated installer does not configure the desired management VLAN. A
first-boot hook can adjust VLAN-specific settings after installation. The
user's current Home answer files specify:

```toml
[first-boot]
source = "from-url"
ordering = "before-network"
url = "http://proxmox-auto-install-assistant.lan.homelab:8080/first-boot?interface=vmbr0&vlan-id=1110"
```

Proxmox downloads a `from-url` executable during installation; the
`before-network` setting determines when the local executable runs. The script
must not rely on a later network callback. Existing answer-file edits are
user-owned and must be preserved.

## Decision

- Add anonymous `GET /first-boot` with required `interface` and `vlan-id`
  query parameters. For example:
  `/first-boot?interface=vmbr0&vlan-id=1110`.
- Validate `interface` as a bridge name (`vmbr` followed by decimal digits)
  and `vlan-id` as a decimal 802.1Q VLAN ID from 1 through 4094. Reject missing,
  repeated, malformed, or out-of-range values using the existing localized JSON
  error envelope.
- Return a Bash script with an interpreter shebang, at most 1 MiB, with a
  shell-script content type and HTTP 200.
  The service returns, but does not execute, the script.
- The script edits only the selected bridge stanza in
  `/etc/network/interfaces`: sets it to `manual`, enables VLAN awareness,
  permits the requested VLAN, and adds a `<bridge>.<vlan-id>` DHCP stanza
  after it. Existing `bridge-vlan-aware` and `bridge-vids` settings are
  replaced, not merged with other permitted VLANs. Every other line is copied
  unchanged. Ansible applies the full network configuration later.
- `network.source = "from-dhcp"` obtains installation settings through DHCP,
  but the installer writes static addressing. Remove the selected bridge's
  old address, netmask, and gateway; Fortigate DHCP reservations supply the
  management address on the VLAN.
- Exit without changes when the VLAN DHCP stanza already exists, without
  verifying the rest of the configuration; fail without changes when the
  bridge is missing. Validate the new file with
  `ifup --syntax-check -a --interfaces <candidate>` (the Proxmox ifupdown2
  package supports an alternate file on `ifup`, not `ifreload`), keep a backup,
  then move the file into place. Never run network activation, reload, or
  equivalent commands.
- Support an initially untagged installer-created bridge; an existing VLAN
  subinterface is not required. Verify the supported input layout and
  `ifupdown2` validation approach against a representative Proxmox-generated
  configuration before implementation is considered complete.
- Answer files provide the bridge and VLAN values. Do not hard-code an
  application-side VLAN-to-physical-interface mapping. Preserve the current
  Home values `vmbr0` and `1110`; do not change other answer files unless their
  intended values are established.
- Keep the endpoint internal and use HTTP as currently requested. Do not change
  existing endpoint or Actuator behavior. This feature has no callback,
  database, application-side persistent state, or dependency on the
  post-install webhook. The target configuration and its backup persist.

## Approved Instruction overrides

For `GET /first-boot` only:

- `spring-boot-project.instructions.md`: the private-API Spring Security
  requirement is overridden.
- `spring-boot-security.instructions.md`: HTTP Basic and per-route
  authentication requirements are overridden.
- `spring-boot-controller.instructions.md`: a successful response may return
  the Bash script rather than JSON. The controller must still use
  `ResponseEntity` and HTTP 200; errors remain in the existing JSON envelope.

Anonymous access matches the existing internal service access model. These
overrides do not authorize a blanket `permitAll()` rule, public exposure, or
changes to any other route.

## Implementation and verification

Use existing feature, service, controller, localization, and error-handling
conventions. Test parameter validation, script content type and size, the exact
resulting Home layout, idempotence, missing bridge, validation failure, backup,
and replacement using temporary files only. Verify Bash syntax. Tests must not
modify a live network configuration or activate networking. Update operational
documentation for the URL parameters, script behavior, and internal-network
boundary.

Following the approved simplification, the script is a short, readable edit of
the installer-generated layout, not a general network-file parser or
transaction system. Layout validation beyond the syntax check is left to
Ansible. The default fixture validator does not parse ifupdown2 configuration;
validate the real parser and first-boot behavior on a Proxmox node before
relying on the hook.

## Out of scope

- Network activation or reload by the script.
- Applying full Ansible network configuration during first boot.
- Arbitrary shell commands, physical-parent parameters, or invalid VLAN IDs.
- Changes to existing endpoints, Actuator, or authentication behavior.
- Public-internet exposure.
