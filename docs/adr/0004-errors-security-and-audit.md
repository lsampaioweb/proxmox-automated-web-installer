# ADR-0004: Errors, Security, and Audit

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

The endpoint receives installer metadata and returns sensitive installation configuration. Operators need enough structured evidence to diagnose requests without logging request bodies or answer-file contents. Clients need stable error codes and localized messages without receiving internal exception details.

## Decision

Return a shared JSON error envelope containing `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. The `message` and field-validation details are localized; `errorCode` remains stable and machine-readable.

Map API errors as follows:

| HTTP status | Error code | Condition |
|---|---|---|
| 400 | `INVALID_JSON` | The request body is malformed JSON. |
| 400 | `VALIDATION_ERROR` | Bean Validation rejects the request. |
| 400 | `ANSWER_MAC_REQUIRED` | No nonblank MAC address is supplied. |
| 400 | `ANSWER_MAC_INVALID` | A nonblank MAC address has invalid syntax. Reject the request before filesystem lookup; do not use the malformed value to construct a path. |
| 404 | `ANSWER_NOT_FOUND` | No answer file exists for any usable requested MAC. |
| 404 | `RESOURCE_NOT_FOUND` | The requested URL does not map to a resource. |
| 500 | `ANSWER_UNREADABLE` | The selected answer file cannot be read. |
| 500 | `ANSWER_INVALID_TOML` | The selected answer file contains invalid TOML. |
| 500 | `INTERNAL_ERROR` | An unexpected application failure occurs. |

An unreadable file or invalid TOML stops lookup; the service does not continue to a later MAC. Successful responses return the selected TOML template with exactly one `__PROXMOX_ROOT_PASSWORD_HASH__` token replaced in memory by the startup-generated yescrypt hash. The response uses `application/toml`; source files are never modified.

All application-authored natural-language exception details must be represented by i18n message keys. Do not put hardcoded application-authored prose in exceptions or causes. For request-time API errors, resolve localized messages using the request's `Accept-Language`. For startup failures, resolve messages using the configured default locale because no request context exists. OS-locale fallback remains disabled. Third-party technical causes may be retained internally, but must never be exposed in the localized API message.

Record an INFO audit event for every request that reaches the service, including malformed-MAC requests and failures. Include timestamp, client address, MAC metadata, selected or last-considered MAC, filename, outcome, error code, and latency. Retention of every documented request field is not required; `enable_btrfs` is ignored as an unknown key and is not an audit field. A malformed-MAC request is audited with `outcome=BAD_REQUEST` and `errorCode=ANSWER_MAC_INVALID`; do not record a selected MAC or filename when none was selected. Sanitize request-derived log fields by replacing control characters and Unicode line and paragraph separators with `_`.

Requests rejected before the service, including invalid JSON and Bean Validation failures, write the same `Answer request` prefix with `outcome=BAD_REQUEST` and the corresponding error code. DEBUG logs may record each MAC lookup attempt as `HIT` or `MISS`. Never log raw request JSON, answer TOML contents, the plaintext root password, or the generated password hash.

The root password is supplied through protected runtime configuration and hashed once at startup using yescrypt. Startup failures related to the password or hashing use localized message keys and the configured default locale. Do not expose secrets or internal exception details to API callers.

TLS is recommended and may be provided by the application or an internal reverse proxy. Application-level authentication remains disabled because this is an internal homelab service; network placement and transport protection are operational controls, not new API endpoints.

Actuator endpoints remain unauthenticated in this internal deployment. Expose only `health`, `info`, and `metrics`; rely on network placement as the access boundary and do not expose the service directly to the public internet.

## Consequences

Stable error codes let clients distinguish malformed requests, missing answers, and server failures. Localized messages support request locale without leaking internal causes. Audit records let operators correlate outcomes with a node and selected file while keeping installation documents and secrets out of logs. The deployment remains dependent on its internal network boundary because the endpoint and Actuator endpoints have no application authentication.

## Alternatives Considered

- Logging full requests or answer files: rejected because those payloads may contain sensitive installation configuration.
- Returning detailed internal errors or third-party cause messages to callers: rejected because diagnostics belong in controlled operator logs, not API responses.
- Skipping malformed MAC values and continuing lookup: rejected; malformed nonblank MACs are client errors and must not be used to construct filesystem paths.
- Adding application-level authentication: rejected for this internal homelab service; network placement and TLS remain the operational controls.
- Exposing all Actuator endpoints: rejected; only `health`, `info`, and `metrics` are exposed.
- Requiring TLS termination in the application: rejected as a deployment constraint; TLS may instead terminate at an internal reverse proxy.

## Out of Scope

- Logging complete request JSON, credentials, secrets, or TOML documents.
- Adding authentication or authorization middleware.
- Treating audit logs as a second answer-file store or adding persistent audit storage.
- Exposing the service or Actuator endpoints directly to the public internet.
- Prometheus, tracing, or other observability components not required by this contract.
