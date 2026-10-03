# ADR-0005: Testing and Operations

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

The service has a small public surface but its behavior depends on request shape, MAC syntax and normalization, multi-interface lookup, filesystem state, symlink-safe resolution, in-memory password substitution, and safe audit metadata. Proxmox also documents compatibility requirements and provides a validate-answer tool for operational validation.

## Decision

Tests must cover the controller and repository, including:

- valid MAC lookup and case-insensitive filename matching;
- acceptance of exactly six two-digit hexadecimal octets separated by colons, including uppercase input and surrounding whitespace;
- rejection of malformed nonblank MACs with HTTP 400 and `ANSWER_MAC_INVALID`;
- unchanged skipping of null, missing, and blank MAC values, and `ANSWER_MAC_REQUIRED` when no nonblank MAC remains;
- trying later valid interfaces when an earlier MAC has no file;
- stopping on unreadable, invalid TOML, or invalid template without trying the next MAC;
- missing answers directory returning `ANSWER_UNREADABLE` rather than not-found;
- malformed JSON and its existing error response;
- request bodies over 1 MiB returning HTTP 413 before service invocation;
- unsupported HTTP method (`405`) and unsupported media type (`415`);
- missing answer files for every valid requested MAC returning 404;
- filesystem read failure returning 500;
- invalid TOML returning 500;
- template policy failures returning `ANSWER_TEMPLATE_INVALID`;
- optional password placeholder omission when a finished password field is already present;
- canonical `.toml` suffix use from the shared application constant;
- symlinked answer entries and symlink-based attempts to resolve outside the canonical answers directory being rejected as unreadable; and
- audit metadata, control-character sanitization in request-derived log fields, and the absence of request-body or TOML-content logging, as appropriate.

Tests for error responses must verify status and error codes, and localized messages for both English and `pt-BR`. New message keys must be present in both bundles with matching key sets and placeholder arity.

Render every committed answer template in automated tests. When `proxmox-auto-install-assistant` is on `PATH` and new enough for the template schema, run `validate-answer` against each rendered file; if the installed assistant rejects newer fields such as interface-name pinning, skip that external check rather than failing the suite.

Operational setup must provide the externally configured answer-file directory, seed templates from `host_vars` `mac_bind`, set `PROXMOX_ROOT_PASSWORD` through a protected runtime environment, ensure Linux `libcrypt` supports yescrypt, place a TLS-terminating reverse proxy in front of production, and keep request bodies within the application's 1 MiB limit. Restart the service to change the password.

## Consequences

The test suite protects the narrow API contract, MAC validation, filesystem containment, template rendering policy, and localization. Proxmox-specific answer validity remains validated with the vendor tool against rendered responses when a new enough assistant is available. Operational answer-template changes take effect without rebuilding or restarting; password changes require an application restart.

## Alternatives Considered

- Testing only the controller: rejected because filesystem behavior, normalization, password substitution, and operational compatibility are part of the contract.
- Validating only with unit tests: rejected because the Proxmox answer-file validator and deployment checks provide necessary operational coverage.
- Relying only on lexical path normalization for symlink containment: rejected because it cannot guarantee that the opened file remains within the canonical answers directory.
- Hard-failing CI when an older assistant rejects newer schema fields: rejected; rendering coverage remains mandatory, vendor validation is best-effort against the installed tool.

## Out of scope

- End-to-end Proxmox installation automation.
- Treating `validate-answer` as an application endpoint or embedded workflow.
- Performance targets beyond recording request latency in audit events.
- A database or persistent audit-event store.
