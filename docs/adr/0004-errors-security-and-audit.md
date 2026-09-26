# ADR-0004: Errors, Security, and Audit

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

The endpoint receives installer metadata and returns sensitive installation configuration. Operators need enough structured evidence to diagnose requests without logging request bodies or answer-file contents.

## Decision

Return a shared JSON error envelope with `timestamp`, `status`, `error`, `errorCode`, `message`, `path`, `trace`, and `fields`. Invalid JSON and a request with no usable `network_interfaces[].mac` return status 400. A missing answer file for every requested MAC returns 404. Unreadable files and invalid TOML return 500. Successful responses return the selected TOML template with `__PROXMOX_ROOT_PASSWORD_HASH__` replaced in memory by a startup-generated yescrypt hash and use `application/toml`; source files are never modified.

Record structured audit metadata for every request that reaches the service, including failures: timestamp, client address, product, ISO release, schema version, DMI serial/uuid/name, baseboard and chassis serials, every usable MAC, the MAC selected or last considered, filename, outcome, and latency. Requests rejected before the service (invalid JSON, Bean Validation) write the same `Answer request` prefix with `outcome=BAD_REQUEST` and an `errorCode`. DEBUG lines may record each MAC attempt as `HIT` or `MISS`. Never log the raw request JSON, answer TOML contents, plaintext root password, or generated hash. MAC matching is case-insensitive and uses lowercase normalization before lookup and audit recording.

TLS is recommended and may be provided by the application or an internal reverse proxy. Application-level authentication remains disabled because this is an internal homelab service; network placement and transport protection are operational controls rather than new API endpoints.

Actuator endpoints remain unauthenticated in this internal deployment. The service exposes only `health`, `info`, and `metrics`, relies on network placement as the access boundary, and must not be exposed directly to the public internet.

## Consequences

Operators can correlate outcomes with a node and selected file without exposing installation documents in logs. Filesystem and parser failures are visible as server errors, while malformed client requests are clearly separated as 400 responses. Internal deployment boundaries remain important because the endpoint has no application authentication.

## Alternatives Considered

- Logging full requests and answer files: rejected because those payloads may contain sensitive installation configuration. The wiki sample logs the JSON body; this service does not.
- Returning detailed internal errors to callers: rejected because diagnostics belong in controlled operator logs, not the public response.

## Out of scope

- Logging complete request JSON, credentials, secrets, or TOML documents.
- Adding authentication or authorization middleware.
- Treating audit logs as a second answer-file store.
- Prometheus, tracing, or other observability components not required by this contract.
