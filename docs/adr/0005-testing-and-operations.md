# ADR-0005: Testing and Operations

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

Answer delivery depends on request shape, MAC validation, lookup order, filesystem state, symlink handling, password rendering, and audit logging. ADR-0006 adds webhook tests. Proxmox provides `validate-answer` for checking rendered files.

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

Error-response tests must check status, error code, and localized messages in English and `pt-BR`. Keep both bundles' key sets and placeholder counts in sync.

Render every committed template in the test suite. If `proxmox-auto-install-assistant` is installed and supports the template schema, run `validate-answer` on each rendered file. If the installed tool rejects a newer field such as interface-name pinning, skip only the external validation. Keep the rendering checks running.

Operational setup must provide the externally configured answer-file directory, seed templates from `host_vars` `mac_bind`, set `PROXMOX_ROOT_PASSWORD` through a protected runtime environment, ensure Linux `libcrypt` supports yescrypt, enforce the internal network boundary, and keep request bodies within the application's 1 MiB limit. Internal HTTP is supported; TLS termination is optional. Restart the service to change the password.

## Consequences

The test suite checks the API contract, MAC validation, filesystem boundaries, template rendering, and localization. When a compatible assistant is available, the vendor tool also checks rendered answers. Template edits take effect without a rebuild or restart. Password changes require an application restart.

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
