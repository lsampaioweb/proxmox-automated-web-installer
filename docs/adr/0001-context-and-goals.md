# ADR-0001: Context and Goals

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

The internal homelab needs a small Spring Boot service that answers Proxmox automated-installation requests. The service must select the correct answer file from the installer-reported MAC addresses and remain simple to operate without a database.

## Decision

Build the service with Spring Boot 4+ and Java 25 using the base package `io.github.lsampaioweb`. The service has one responsibility: resolve and return the Proxmox automated-installation TOML answer file for the requesting node.

The deployment is internal to the homelab. The application has no authentication and exposes no CRUD operations. TLS terminates at an internal reverse proxy by default; `server.ssl.*` may be configured only when the JVM itself must terminate TLS.

## Consequences

The service is intentionally narrow and mostly stateless. Answer-file changes are filesystem changes rather than application releases. Network exposure must remain limited to the internal deployment boundary, with TLS recommended for transport protection.

## Out of scope

- Public internet hosting.
- User accounts, application authentication, or authorization workflows.
- Database persistence.
- Create, update, or delete endpoints.
- Post-installation webhook receivers.
- Proxmox installation orchestration beyond serving the answer file.

## Alternatives Considered

1. **Terraform/Ansible provisioning** — Rejected: requires agent on target; this service needs zero prior setup on target machines.
2. **Static HTTP file server (nginx)** — Rejected: would require manual URL mapping per MAC address; no intelligence; this service adds dynamic lookup.
3. **Database-backed answer file repository** — Rejected: adds complexity (schema, migration, backup); filesystem is sufficient for personal homelab.
