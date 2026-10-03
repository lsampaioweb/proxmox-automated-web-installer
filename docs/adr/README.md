# Proxmox Web Installer — Architecture Decision Records

**Project:** Proxmox Web Installer (automated Proxmox installation answer server)
**Status:** Design complete
**Last updated:** 2026-09-29

---

## Overview

The Proxmox Web Installer is a lightweight Spring Boot service that dynamically provides Proxmox automated-installation TOML answer files based on MAC address lookups. It replaces manual or static configuration with intelligent, filesystem-based answer file management.

The service is intentionally minimal: mostly stateless, database-free, and focused on a single responsibility. It runs internally on the homelab network only.

## ADRs

| # | Title | Status |
|---|-------|--------|
| [0001](0001-context-and-goals.md) | Context and Goals | ✅ Accepted |
| [0002](0002-http-api-contract.md) | HTTP API Contract | ✅ Accepted |
| [0003](0003-filesystem-answer-repository.md) | Filesystem Answer Repository | ✅ Accepted |
| [0004](0004-errors-security-and-audit.md) | Errors, Security, and Audit | ✅ Accepted |
| [0005](0005-testing-and-operations.md) | Testing and Operations | ✅ Accepted |

## Current Status

- Internal homelab deployment only
- No authentication or authorization
- Filesystem-based answer file storage (no database)
- Stateless design (no persistent answer-file cache)
- MAC address-based answer file lookup across every installer-reported NIC
- Spring Boot 4+ with Java 25

## Key Principles

1. **Simplicity over features:** Single responsibility — serve answer files dynamically
2. **Stateless operation:** No database, no persistent state, no complex dependencies
3. **Filesystem as source:** Answer-template changes are direct filesystem edits; the root password is injected and hashed at runtime
4. **Internal only:** Network boundary enforcement; TLS via reverse proxy by default
5. **Small runtime footprint:** One JVM service with the system `libcrypt` library for yescrypt hashing

## Architecture

```
Proxmox Node (requesting installation)
    ↓ (HTTP request with MAC address)
Spring Boot Service
    ↓ (MAC → filename mapping)
Filesystem Answer Template (.toml)
    ↓
In-memory password substitution
    ↓
Rendered TOML response to Proxmox
    ↓
Proxmox installs from answer file
```

## Related Concepts

- **Proxmox automated installation** — Proxmox supports answer files for unattended deployments
- **MAC address mapping** — Service tries each installer-reported MAC until a file matches
- **TOML format** — Proxmox answer files are TOML (not JSON, YAML, etc.)

## Deployment Context

- Part of a larger Proxmox homelab infrastructure
- Coordinates with other provisioning tools (Ansible for post-install)
- Runs on Ubuntu 24.04 VM in the homelab
