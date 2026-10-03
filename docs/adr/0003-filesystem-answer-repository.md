# ADR-0003: Filesystem Answer Repository

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

Known node answer files are prepared outside the application. Their names are MAC addresses with a `.toml` suffix, for example `b4:2e:99:ad:b0:c0.toml`. The initial files are seeded from `host_vars` using each host's `mac_bind` values. The HTTP layer may try several MACs from one request; the repository still resolves one MAC to one file.

## Decision

Use an externally configurable filesystem directory as the answer-file store. The repository resolves a normalized, syntactically valid MAC address to a filename matching `<mac-address>.toml` case-insensitively; `.toml` is the one canonical answer-file suffix and must be represented by one shared application constant. Prefer lowercase filenames operationally. The repository does not use a database or an in-memory startup cache.

The repository reads the selected template on every request. Before returning it, the repository:

- replaces at most one `__PROXMOX_ROOT_PASSWORD_HASH__` token when it is exactly the `root-password-hashed` assignment value;
- validates that the rendered document is TOML and sets exactly one of `root-password` or `root-password-hashed`.

The source file is never modified. A missing answers directory is an unreadable configuration failure. A missing file for the requested MAC remains not-found. Ambiguous case-insensitive filename matches fail closed as unreadable.

Treat the configured answers directory's canonical real directory as the filesystem boundary. Open the answer relative to an anchored directory handle without following symlinks, and verify that the opened object is a regular file. If the filesystem provider cannot provide directory-relative no-follow semantics, fail closed as an unreadable answer rather than falling back to an unsafe lookup.

Committed files are templates. Validate rendered responses with `proxmox-auto-install-assistant validate-answer` (PVE 9.1+ for interface-name pinning). Prefer stable disk filters over `disk-list = ["sda"]` before real installs. Optional `root-ssh-keys` belong in the answer file itself when needed.

## Consequences

Filesystem state is the runtime source of truth and must be mounted and permissioned consistently. There is no file cache invalidation problem, but each request performs a filesystem read and depends on the availability and readability of the configured directory. Password changes require an application restart. Host identity can match any installer-reported NIC whose MAC has a file.

## Alternatives Considered

- Database-backed answer files: rejected because the service needs a small, local, filesystem-managed repository.
- Generating answer files dynamically: rejected because known files are prepared and reviewed outside the application.
- Lexical path checks followed by ordinary path-based reads: rejected because symlink resolution and replacement can invalidate the check before the file is opened.
- Exact-case-only filenames: rejected because the Proxmox wiki examples use uppercase MAC filenames.

## Out of scope

- Database tables, migrations, ORM mappings, or repositories backed by a database.
- Startup scanning or startup caching of answer files.
- Application endpoints for answer-file management.
- Automatic generation or synchronization of files from `host_vars`.
- Runtime injection of SSH keys or other answer fields beyond the password placeholder.
