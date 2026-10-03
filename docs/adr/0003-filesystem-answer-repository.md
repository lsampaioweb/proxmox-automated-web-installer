# ADR-0003: Filesystem Answer Repository

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

Known node answer files are prepared outside the application. Their names are MAC addresses with a `.toml` suffix, for example `b4:2e:99:ad:b0:c0.toml`. The initial files are seeded from `host_vars` using each host's `mac_bind` values. The HTTP layer may try several MACs from one request; the repository still resolves one MAC to one file.

## Decision

Store answer files in a configurable directory. For a normalized, valid MAC, the repository looks for `<mac-address>.toml`, ignoring letter case. Keep `.toml` as the single suffix in a shared application constant. Lowercase filenames are preferred. The repository uses neither a database nor a startup cache.

Read the selected template on every request. Before returning it:

- replaces at most one `__PROXMOX_ROOT_PASSWORD_HASH__` token when it is exactly the `root-password-hashed` assignment value;
- validates that the rendered document is TOML and sets exactly one of `root-password` or `root-password-hashed`.

The source file is never modified. A missing answers directory is an unreadable configuration failure. A missing file for the requested MAC remains not-found. Ambiguous case-insensitive filename matches fail closed as unreadable.

Use the answers directory's canonical real path as the filesystem boundary. Open files relative to that directory handle without following symlinks, then verify that each opened object is a regular file. If the filesystem can't provide directory-relative, no-follow access, return `ANSWER_UNREADABLE`. Do not fall back to an unsafe path lookup.

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
