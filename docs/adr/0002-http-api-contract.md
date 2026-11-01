# ADR-0002: HTTP API Contract

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

Proxmox Automated Installation sends a `POST` request with JSON that includes `network_interfaces`. Each entry has a MAC address and link name, and a host can have several interfaces. Proxmox allows an HTTP source to return TOML or JSON, so this service can return TOML only.

## Decision

Expose `POST /answer` for answer delivery. This ADR owns its unchanged protocol; ADR-0006 owns the webhook endpoint.

Follow the Proxmox HTTP source protocol. Ignore unknown keys, including `enable_btrfs`, so newer installer documents still work. Check `network_interfaces` in request order. Skip a missing or blank `mac`; trim each nonblank value and require six two-digit ASCII hexadecimal octets separated by colons (`XX:XX:XX:XX:XX:XX`). If any nonblank MAC is malformed, reject the whole request before looking at the filesystem. Return HTTP 400 with `ANSWER_MAC_INVALID`. Otherwise, lowercase each valid MAC and use the first one with a matching answer file. Filename matching ignores letter case. If every MAC is missing or blank, return HTTP 400 with `ANSWER_MAC_REQUIRED`.

On a match, return the selected TOML template with content type `application/toml`. Replace at most one `__PROXMOX_ROOT_PASSWORD_HASH__` token, and only when it is the value of `root-password-hashed`. Never modify the source file. A template may instead contain a finished `root-password` or `root-password-hashed` value and no placeholder. The service does not generate JSON answers.

Map API errors as follows:

| HTTP status | Error code | Condition |
|---|---|---|
| 400 | `INVALID_JSON` | The request body is malformed JSON. |
| 400 | `VALIDATION_ERROR` | Bean Validation rejects the request. |
| 400 | `ANSWER_MAC_REQUIRED` | No nonblank MAC address is supplied. |
| 400 | `ANSWER_MAC_INVALID` | A nonblank MAC address has invalid syntax. |
| 413 | `REQUEST_BODY_TOO_LARGE` | The request body exceeds 1 MiB. |
| 404 | `ANSWER_NOT_FOUND` | No answer file exists for any usable requested MAC. |
| 404 | `RESOURCE_NOT_FOUND` | The requested URL does not map to a resource. |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported `Content-Type`. |
| 500 | `ANSWER_UNREADABLE` | The answers directory or selected answer file cannot be read. |
| 500 | `ANSWER_INVALID_TOML` | The selected answer file contains invalid TOML. |
| 500 | `ANSWER_TEMPLATE_INVALID` | The selected answer template fails password rendering policy. |
| 500 | `INTERNAL_ERROR` | An unexpected application failure occurs. |

If the answers directory is missing, return `ANSWER_UNREADABLE`, not `ANSWER_NOT_FOUND`. Stop lookup when the selected file is unreadable or contains invalid TOML or an invalid template; do not try a later MAC. The endpoint has no CRUD or authentication routes.

## Consequences

Multi-interface nodes are identified even when the management NIC is not `network_interfaces[0]`. Operators can use wiki-style uppercase filenames without a silent 404. Template policy failures are distinguishable from TOML syntax errors. The shared error envelope gives malformed input and server failures a localized, machine-readable shape while keeping the success response as TOML.

## Alternatives Considered

- Using only `network_interfaces[0].mac`: rejected because the installer does not guarantee that the first listed NIC is the host's identifying interface.
- Treating malformed MACs as lookup candidates: rejected because malformed input must not be used to construct answer-file paths.
- Returning a generated JSON answer: rejected because the operator-managed TOML file is the source of truth and the installer accepts TOML.
- Falling back to a `default.toml` when no MAC matches: rejected because an unknown host must fail closed with 404.

## Out of scope

- Webhook behavior, specified separately in ADR-0006.
- Rewriting, normalizing, or augmenting TOML beyond the in-memory root-password placeholder substitution.
- Application-level authentication (`Authorization: Bearer` is optional in the ISO, not in this service).
- Serving JSON answer documents.
