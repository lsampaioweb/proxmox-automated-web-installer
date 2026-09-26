# ADR-0003: Filesystem Answer Repository

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-26
**Project:** Proxmox Web Installer

## Context

Known node answer files are prepared outside the application. Their names are MAC addresses with a `.toml` suffix, for example `b4:2e:99:ad:b0:c0.toml`. The initial files are seeded from `host_vars` using each host's `mac_bind[0].mac_address` value. The HTTP layer may try several MACs from one request; the repository still resolves one MAC to one file.

## Decision

Use an externally configurable filesystem directory as the answer-file store. The repository resolves a normalized, syntactically valid MAC address to `<mac-address>.toml`; `.toml` is the one canonical answer-file suffix and must be represented by one shared application constant. Do not define alternate suffixes or duplicate the suffix as a separate application/test literal. The repository does not use a database or an in-memory startup cache.

The repository reads the selected template on every request. Additions, removals, and edits therefore take effect immediately without a rebuild, redeploy, or restart. Before returning it, the repository replaces exactly one `__PROXMOX_ROOT_PASSWORD_HASH__` token in memory with the yescrypt hash generated at application startup. The source file is never modified; all other TOML content remains unchanged. Filesystem lookup and read failures remain distinguishable so the API can map a missing file to 404 and unreadable or invalid content to 500.

Treat the configured answers directory's canonical real directory as the filesystem boundary. Resolve only the direct-child filename formed from the validated normalized MAC and canonical suffix. Open the answer relative to an anchored directory handle without following symlinks, and verify that the opened object is a regular file. Do not rely only on lexical normalization, `startsWith`, or a check-then-read sequence. Symlinks below the canonical answers-directory boundary must not redirect lookup outside it, including when an answer entry is replaced during lookup. If the filesystem provider cannot provide directory-relative no-follow semantics, fail closed as an unreadable answer rather than falling back to an unsafe lookup. Symlinks used to reach the configured directory are resolved when establishing its canonical boundary; that canonical directory is the configured root for lookups.

The application does not seed or mutate answer files. Seeding remains an operational responsibility based on `host_vars` and `mac_bind[0].mac_address`. Committed files are templates containing the password token and must have the Proxmox answer structure (`global`, `network`, and `disk-setup`). Validate the rendered response with `proxmox-auto-install-assistant validate-answer`; the committed template is not directly installable.

## Consequences

Filesystem state is the runtime source of truth and must be mounted and permissioned consistently in the homelab deployment. There is no file cache invalidation problem, but each request performs a filesystem read and depends on the availability and readability of the configured directory. The root password is supplied through `PROXMOX_ROOT_PASSWORD`, hashed once at startup using yescrypt, and must be changed by restarting the application. Host identity can match any installer-reported NIC whose MAC has a file. Answer lookup cannot escape the canonical configured root by following symlinks below that root.

## Alternatives Considered

- Database-backed answer files: rejected because the service needs a small, local, filesystem-managed repository.
- Generating answer files dynamically: rejected because known files are prepared and reviewed outside the application.
- Lexical path checks followed by ordinary path-based reads: rejected because symlink resolution and replacement can invalidate the check before the file is opened.

## Out of scope

- Database tables, migrations, ORM mappings, or repositories backed by a database.
- Startup scanning or startup caching of answer files.
- Application endpoints for answer-file management.
- Automatic generation or synchronization of files from `host_vars`.
