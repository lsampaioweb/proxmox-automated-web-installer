# ADR-0006: Proxmox Post-Install Webhook

**Status:** Accepted
**Date:** 2026-10-26

## Context

Proxmox can send a webhook after a successful automated installation. This
service should receive that report and write a limited audit summary to its
existing application log. It must not store the full payload or trigger other
automation.

The official [Proxmox Automated Installation documentation](https://pve.proxmox.com/wiki/Automated_Installation)
shows a v1.2 payload with `$schema.version`, `product`, `fqdn`, `dmi.system`,
and the hyphenated `network-interfaces` array. The payload may also include
disks, machine ID, SSH host keys, and other information. It has no event-time
field, so the receiver records its own receipt timestamp.

## Decision

- Add anonymous `POST /api/v1/post-install-events`, accepting Proxmox JSON.
  Read `network-interfaces` using the exact hyphenated key; tolerate absent
  optional fields and ignore unknown fields.
- Enforce the existing 1 MiB request-body limit and localized JSON error
  envelope. On success, return `200 OK` with a small JSON acknowledgement.
- Enable `[post-installation-webhook]` in all nine answer templates with the
  receiver URL. This reports installation completion, not first-boot success.
- Write one INFO event to the existing application log. Log only
  `product.short`, `product.fullname`, `product.version`, `fqdn`,
  `dmi.system.uuid`, each `network-interfaces` entry's `name` and `mac`, and a
  server-generated receipt timestamp. Sanitize request-derived values.
- Never log the raw body or other fields, including tokens, machine ID, disk
  details, interface addresses, udev properties, serials, SSH keys, or unknown
  fields.
- Do not persist or forward the report, create a database or separate log
  store, or trigger first-boot behavior.
- Keep the route internal and HTTP as currently requested. Preserve all
  existing endpoint and Actuator behavior.

## Approved Instruction override

For `POST /api/v1/post-install-events` only:

- `spring-boot-project.instructions.md`: the private-API Spring Security
  requirement is overridden.
- `spring-boot-security.instructions.md`: HTTP Basic and per-route
  authentication requirements are overridden.

Anonymous access is approved to match the existing internal service access
model. No blanket `permitAll()` rule or public exposure is authorized. All
other security requirements and routes remain unchanged.

## Implementation and verification

Use the existing feature, service, logging, localization, and error-handling
conventions. Test the actual Proxmox field names, optional and unknown fields,
the allowlisted log contents, receipt timestamp, rejection behavior, body-size
limit, and absence of raw or sensitive payload data in logs. Update operational
documentation for the endpoint and its logging boundary.

## Out of scope

- Persistent webhook storage, queues, callbacks, or webhook-triggered actions.
- Logging fields outside the allowlist or the raw webhook document.
- Changes to `POST /answer`, Actuator, or other routes.
- Public-internet exposure.
