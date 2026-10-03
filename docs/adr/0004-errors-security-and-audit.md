# ADR-0004: Errors, Security, and Audit

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

The endpoint receives installer metadata and returns sensitive installation configuration. Operators need enough structured evidence to diagnose requests without logging request bodies or answer-file contents. Clients need stable error codes and localized messages without receiving internal exception details.

## Decision

Return a shared JSON error envelope containing `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. The `message` and field-validation details are localized; `errorCode` remains stable and machine-readable.

Map API errors according to ADR-0002, including oversized request bodies (`413`) and method/media-type failures (`405` / `415`) ahead of the catch-all handler so framework client errors do not collapse into `INTERNAL_ERROR`.

Record an INFO audit event for every answer request that reaches the service, including malformed-MAC requests and failures. Include timestamp, client address, MAC metadata, selected or last-considered MAC, filename, outcome, error code, and latency. In production behind a reverse proxy, enable `server.forward-headers-strategy=framework` so the audited client address is the installer rather than the proxy. Sanitize request-derived log fields by replacing control characters and Unicode line and paragraph separators with `_`.

Requests rejected before the service, including invalid JSON, Bean Validation failures, oversized bodies, unsupported methods, and unsupported media types, write the same `Answer request` prefix with the corresponding error code. DEBUG logs may record each MAC lookup attempt as `HIT` or `MISS`. Never log raw request JSON, answer TOML contents, the plaintext root password, or the generated password hash.

The root password is supplied through protected runtime configuration and hashed once at startup using yescrypt. `crypt(3)` is not thread-safe and must remain on the startup path only.

TLS terminates at an internal reverse proxy by default. The application rejects request bodies larger than 1 MiB before message conversion; the reverse proxy should enforce the same or a stricter limit. Application-level authentication remains disabled because this is an internal homelab service; network placement and transport protection are the operational controls.

Actuator endpoints remain unauthenticated in this internal deployment. Expose only `health`, `info`, and `metrics`; rely on network placement as the access boundary and do not expose the service directly to the public internet.

## Consequences

Stable error codes let clients distinguish malformed requests, missing answers, template policy failures, and server failures. Audit records let operators correlate outcomes with a node and selected file while keeping installation documents and secrets out of logs. The deployment remains dependent on its internal network boundary because the endpoint and Actuator endpoints have no application authentication.

## Alternatives Considered

- Logging full requests or answer files: rejected because those payloads may contain sensitive installation configuration.
- Returning detailed internal errors or third-party cause messages to callers: rejected because diagnostics belong in controlled operator logs, not API responses.
- Skipping malformed MAC values and continuing lookup: rejected; malformed nonblank MACs are client errors and must not be used to construct filesystem paths.
- Adding application-level authentication: rejected for this internal homelab service; network placement and TLS remain the operational controls.
- Exposing all Actuator endpoints: rejected; only `health`, `info`, and `metrics` are exposed.
- Requiring TLS termination in the application: rejected as the default deployment constraint; TLS may instead terminate at an internal reverse proxy.

## Out of Scope

- Logging complete request JSON, credentials, secrets, or TOML documents.
- Adding authentication or authorization middleware.
- Treating audit logs as a second answer-file store or adding persistent audit storage.
- Exposing the service or Actuator endpoints directly to the public internet.
- Prometheus, tracing, or other observability components not required by this contract.
