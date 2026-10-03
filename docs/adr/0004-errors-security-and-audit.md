# ADR-0004: Errors, Security, and Audit

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

The endpoint receives installer metadata and returns sensitive installation settings. Operators need enough structured data to investigate requests, but logs must not include request bodies or answer files. Clients need stable error codes and localized messages without internal exception details.

## Decision

Return every API error in the same JSON envelope: `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. Localize the message and field errors. Keep `errorCode` stable for client code.

Use the error mapping in ADR-0002. Handle oversized bodies (`413`), unsupported methods (`405`), and unsupported media types (`415`) before the catch-all handler so they keep their correct status and code.

Write an INFO audit event for every answer request handled by the service, including malformed-MAC requests and failures. Record the timestamp, client address, MAC metadata, selected or last-checked MAC, filename, outcome, error code, and latency. In production, set `server.forward-headers-strategy=framework` behind the reverse proxy so the client address identifies the installer, not the proxy. Replace control characters and Unicode line or paragraph separators in request-derived log fields with `_`.

Requests rejected before the service (invalid JSON, validation failures, oversized bodies, unsupported methods, or unsupported media types) use the same localized audit message and include the matching error code. DEBUG logs may record each MAC lookup as `HIT` or `MISS`. Never log request JSON, answer-file contents, the plaintext root password, or its generated hash.

The root password is supplied through protected runtime configuration and hashed once at startup using yescrypt. `crypt(3)` is not thread-safe and must remain on the startup path only.

TLS terminates at an internal reverse proxy by default. The application rejects request bodies larger than 1 MiB before message conversion; the reverse proxy should enforce the same or a stricter limit. Application-level authentication remains disabled because this is an internal homelab service; network placement and transport protection are the operational controls.

Actuator endpoints are also unauthenticated. Expose only `health`, `info`, and `metrics`, and keep them behind the internal network boundary. Do not expose the service directly to the public internet.

## Consequences

Stable error codes distinguish malformed requests, missing answers, template-policy failures, and server errors. Audit records help operators match a request to a node and answer file without putting installation details or secrets in logs. Because neither the API nor Actuator requires authentication, the deployment depends on its internal network boundary.

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
