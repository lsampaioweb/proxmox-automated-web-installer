# ADR-0005: Testing and Operations

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

The service has a small public surface but its behavior depends on request shape, MAC syntax and normalization, multi-interface lookup, filesystem state, symlink-safe resolution, in-memory password substitution, and safe audit metadata. Proxmox also documents compatibility requirements and provides a validate-answer tool for operational validation.

## Decision

Tests must cover the controller and repository, including:

- valid MAC lookup;
- acceptance of exactly six two-digit hexadecimal octets separated by colons, including uppercase input and surrounding whitespace;
- rejection of malformed nonblank MACs with HTTP 400 and `ANSWER_MAC_INVALID`, including a malformed MAC alongside a valid interface MAC, with no filesystem lookup;
- unchanged skipping of null, missing, and blank MAC values, and `ANSWER_MAC_REQUIRED` when no nonblank MAC remains;
- trying later valid interfaces when an earlier MAC has no file;
- stopping on unreadable or invalid TOML without trying the next MAC;
- malformed JSON and its existing error response;
- missing answer files for every valid requested MAC returning 404;
- filesystem read failure returning 500;
- invalid TOML returning 500;
- canonical `.toml` suffix use from the shared application constant, including in tests;
- symlinked answer entries and symlink-based attempts to resolve outside the canonical answers directory being rejected as unreadable, without following the link;
- unchanged source templates, exact password-placeholder substitution, and `application/toml` content type; and
- audit metadata, control-character sanitization in request-derived log fields, and the absence of request-body or TOML-content logging, as appropriate.

Tests for error responses must verify the existing status and error codes remain unchanged for existing cases, the new malformed-MAC code, and localized messages for both English and `pt-BR`. New message keys must be present in both bundles with matching key sets and placeholder arity.

Use Proxmox's documented compatibility guidance and its `validate-answer` tool to validate rendered answer responses. Committed answer templates contain a password placeholder and are not directly installable. The application parses the rendered TOML to classify invalid content as the specified 500 response.

Operational setup must provide the externally configured answer-file directory, seed templates from `host_vars` `mac_bind[0].mac_address`, set `PROXMOX_ROOT_PASSWORD` through a protected runtime environment, ensure Linux `libcrypt` supports yescrypt, and provide TLS through the application or an internal reverse proxy when required by the homelab boundary. Restart the service to change the password. The runtime filesystem provider must support the repository's directory-relative no-follow file opening; if it cannot, answer-file reads fail closed rather than using an unsafe fallback.

## Consequences

The test suite protects the narrow API contract, MAC validation, filesystem containment, runtime filesystem semantics, and localization. Proxmox-specific answer validity remains validated with the vendor tool against rendered responses, while the application distinguishes unreadable files from invalid TOML when serving requests. Operational answer-template changes take effect without rebuilding or restarting; password changes require an application restart.

## Alternatives Considered

- Testing only the controller: rejected because filesystem behavior, normalization, password substitution, and operational compatibility are part of the contract.
- Validating only with unit tests: rejected because the Proxmox answer-file validator and deployment checks provide necessary operational coverage.
- Relying only on lexical path normalization for symlink containment: rejected because it cannot guarantee that the opened file remains within the canonical answers directory.

## Out of scope

- End-to-end Proxmox installation automation.
- Treating `validate-answer` as an application endpoint or embedded workflow.
- Performance targets beyond recording request latency in audit events.
- A database or persistent audit-event store.
