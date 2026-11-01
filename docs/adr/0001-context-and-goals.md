# ADR-0001: Context and Goals

**Status:** Accepted
**Date:** 2026-09-08
**Updated:** 2026-09-29
**Project:** Proxmox Web Installer

## Context

The internal homelab needs a small Spring Boot service that answers Proxmox automated-installation requests. The service must select the correct answer file from the installer-reported MAC addresses and remain simple to operate without a database.

## Decision

Build the service with Spring Boot 4+ and Java 25 using the base package `io.github.lsampaioweb`. Initially it resolved and returned automated-installation TOML answers. ADR-0006 adds installation-completion logging.

The deployment is internal to the homelab. The application has no authentication and exposes no CRUD operations. Internal HTTP is approved; HTTPS through a reverse proxy remains an optional transport-protection measure.

## Consequences

The service is intentionally narrow and mostly stateless. Answer-file changes are filesystem changes rather than application releases. Network exposure must remain limited to the internal deployment boundary, with TLS recommended for transport protection.

## Out of scope

- Public internet hosting.
- User accounts, application authentication, or authorization workflows.
- Database persistence.
- Create, update, or delete endpoints.
- Installation orchestration beyond answer delivery and the webhook in ADR-0006.

## Alternatives Considered

1. **Terraform/Ansible provisioning:** Not a replacement for the bare-metal installer's answer endpoint. Ansible is agentless and remains suitable for configuration after installation.
2. **Static HTTP file server (nginx):** Rejected because every MAC would need a manual URL mapping. This service looks up the matching file itself.
3. **Database-backed answer file repository:** Rejected because a database would add schema, migration, and backup work. The filesystem is enough for this homelab.
