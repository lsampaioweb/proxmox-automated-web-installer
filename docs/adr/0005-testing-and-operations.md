# ADR-0005: Testing and Operations

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

The service has a small public surface but its behavior depends on request shape, MAC normalization, multi-interface lookup, filesystem state, in-memory password substitution, and safe audit metadata. Proxmox also documents compatibility requirements and provides a validate-answer tool for operational validation.

## Decision

Tests must cover the controller and repository, including:

- valid MAC lookup;
- case-insensitive MAC normalization;
- trying later interfaces when an earlier MAC has no file;
- stopping on unreadable or invalid TOML without trying the next MAC;
- malformed JSON and a request with no usable MAC;
- missing answer files for every requested MAC returning 404;
- filesystem read failure returning 500;
- invalid TOML returning 500;
- unchanged source templates, exact password-placeholder substitution, and `application/toml` content type; and
- audit metadata, control-character sanitization in request-derived log fields, and the absence of request-body or TOML-content logging, as appropriate.

Use Proxmox's documented compatibility guidance and its `validate-answer` tool to validate rendered answer responses. Committed answer templates contain a password placeholder and are not directly installable. The application parses the rendered TOML to classify invalid content as the specified 500 response.

Operational setup must provide the externally configured answer-file directory, seed templates from `host_vars` `mac_bind[0].mac_address`, set `PROXMOX_ROOT_PASSWORD` through a protected runtime environment, ensure Linux `libcrypt` supports yescrypt, and provide TLS through the application or an internal reverse proxy when required by the homelab boundary. Restart the service to change the password.

## Consequences

The test suite protects the narrow API contract and runtime filesystem semantics. Proxmox-specific answer validity remains validated with the vendor tool against rendered responses, while the application distinguishes unreadable files from invalid TOML when serving requests. Operational answer-template changes take effect without rebuilding or restarting; password changes require an application restart.

## Alternatives Considered

- Testing only the controller: rejected because filesystem behavior, normalization, password substitution, and operational compatibility are part of the contract.
- Validating only with unit tests: rejected because the Proxmox answer-file validator and deployment checks provide necessary operational coverage.

## Out of scope

- End-to-end Proxmox installation automation.
- Treating `validate-answer` as an application endpoint or embedded workflow.
- Performance targets beyond recording request latency in audit events.
- A database or persistent audit-event store.
