# ADR-0002: HTTP API Contract

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

Proxmox Automated Installation calls an HTTP source with `POST` JSON that includes `network_interfaces`. Each interface includes a MAC address and link name. A server can have multiple network interfaces. The Proxmox wiki documents that the installer accepts a TOML or JSON answer body and that a TOML-only server remains compatible.

## Decision

Expose exactly one public endpoint: `POST /answer`.

The request follows the Proxmox Automated Installation HTTP source protocol. The service keeps the documented POST fields (`$schema`, `product`, `iso`, `dmi`, and `network_interfaces[].mac` / `link`) for audit. Unknown keys are ignored so a newer installer document still resolves an answer file. Lookup walks `network_interfaces` in request order, normalizes each `mac` to lowercase, and uses the first MAC whose `<mac>.toml` file exists. Blank or missing `mac` values are skipped. If the list has no usable MAC, return HTTP 400. Matching is case-insensitive.

On a successful lookup, return the selected TOML template with content type `application/toml`. Replace exactly one `__PROXMOX_ROOT_PASSWORD_HASH__` token in memory with the startup-generated yescrypt hash; do not modify the source file. Other template content remains unchanged. JSON answer bodies are not generated; the installer advertises JSON in `Accept` but documents that a TOML response continues to work.

Invalid JSON, or a request with no usable MAC, returns HTTP 400 with the shared JSON error envelope containing `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. A missing answer file for every usable MAC returns HTTP 404 with the same envelope. An unreadable answer file or invalid TOML returns HTTP 500 with the same envelope and does not continue to a later MAC. The endpoint does not expose CRUD operations or authentication endpoints.

## Consequences

Multi-interface nodes are identified even when the management NIC is not `network_interfaces[0]`. The service preserves the operator-managed TOML except for the in-memory password substitution. The shared error envelope gives malformed input and server failures a localized, machine-readable shape while keeping the success response as TOML.

## Alternatives Considered

- Using only `network_interfaces[0].mac`: rejected because the installer does not guarantee that the first listed NIC is the host's identifying interface.
- Returning a generated JSON answer: rejected because the operator-managed TOML file is the source of truth and the installer accepts TOML.
- Falling back to a `default.toml` when no MAC matches: rejected because an unknown host must fail closed with 404.

## Out of scope

- Additional public endpoints.
- Rewriting, normalizing, or augmenting TOML beyond the in-memory root-password placeholder substitution.
- Application-level authentication (`Authorization: Bearer` is optional in the ISO, not in this service).
- Serving JSON answer documents.
