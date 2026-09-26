# ADR-0002: HTTP API Contract

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

Proxmox Automated Installation calls an HTTP source with `POST` JSON that includes `network_interfaces`. Each interface includes a MAC address and link name. A server can have multiple network interfaces. The Proxmox wiki documents that the installer accepts a TOML or JSON answer body and that a TOML-only server remains compatible.

## Decision

Expose exactly one public endpoint: `POST /answer`.

The request follows the Proxmox Automated Installation HTTP source protocol. Unknown keys, including `enable_btrfs`, are ignored so a newer installer document still resolves an answer file. Request fields are not required to be retained in audit records. Lookup walks `network_interfaces` in request order. A null, missing, or blank `mac` value is skipped, preserving existing behavior. Trim each nonblank value, then require exactly six two-digit ASCII hexadecimal octets separated by five colons (`XX:XX:XX:XX:XX:XX`). If any nonblank MAC is malformed, reject the entire request before filesystem lookup with HTTP 400 and error code `ANSWER_MAC_INVALID`; do not continue with other interfaces. Otherwise, normalize each MAC to lowercase and use the first MAC whose `<mac>.toml` file exists. Matching is case-insensitive. If all MAC values are missing or blank, return HTTP 400 with the existing `ANSWER_MAC_REQUIRED` error code.

On a successful lookup, return the selected TOML template with content type `application/toml`. Replace exactly one `__PROXMOX_ROOT_PASSWORD_HASH__` token in memory with the startup-generated yescrypt hash; do not modify the source file. Other template content remains unchanged. JSON answer bodies are not generated; the installer advertises JSON in `Accept` but documents that a TOML response continues to work.

Invalid JSON returns HTTP 400 with error code `INVALID_JSON`. A malformed nonblank MAC returns HTTP 400 with error code `ANSWER_MAC_INVALID`; a request with no nonblank MAC returns HTTP 400 with error code `ANSWER_MAC_REQUIRED`. These responses use the shared JSON error envelope containing `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. A missing answer file for every valid MAC returns HTTP 404 with error code `ANSWER_NOT_FOUND`. An unreadable answer file or invalid TOML returns HTTP 500 with the existing respective error code and does not continue to a later MAC. The endpoint does not expose CRUD operations or authentication endpoints.

## Consequences

Multi-interface nodes are identified even when the management NIC is not `network_interfaces[0]`. Blank and missing MAC values retain their existing skip behavior; malformed nonblank values are rejected before lookup. Existing API statuses and error codes remain unchanged for existing cases. The new malformed-MAC case has its own stable error code. The service preserves the operator-managed TOML except for the in-memory password substitution. The shared error envelope gives malformed input and server failures a localized, machine-readable shape while keeping the success response as TOML.

## Alternatives Considered

- Using only `network_interfaces[0].mac`: rejected because the installer does not guarantee that the first listed NIC is the host's identifying interface.
- Treating malformed MACs as lookup candidates: rejected because malformed input must not be used to construct answer-file paths.
- Returning a generated JSON answer: rejected because the operator-managed TOML file is the source of truth and the installer accepts TOML.
- Falling back to a `default.toml` when no MAC matches: rejected because an unknown host must fail closed with 404.

## Out of scope

- Additional public endpoints.
- Rewriting, normalizing, or augmenting TOML beyond the in-memory root-password placeholder substitution.
- Application-level authentication (`Authorization: Bearer` is optional in the ISO, not in this service).
- Serving JSON answer documents.
