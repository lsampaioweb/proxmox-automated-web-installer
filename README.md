# Proxmox Web Installer: Automated Proxmox VE Install with VLAN Support

## Purpose

Automate Proxmox VE installation and configure its management VLAN with DHCP on first boot.

## Overview

- Serves Proxmox unattended-install answer files over HTTP.
- Works around the installer's lack of an automated management VLAN setting by applying VLAN configuration before first-boot networking.
- Records installation completion through a webhook.
- Supports DHCP-based management addressing and host-specific answer templates.

## The Problem

Proxmox VE's installer, including [automated installation](https://pve.proxmox.com/wiki/Automated_Installation), does not provide an automated-installation option to assign a VLAN tag to the management interface. Nodes on a VLAN-only network cannot reach the installer's network or answer server, and the usual manual fix is to log in to every server and edit `/etc/network/interfaces`.

**Install Proxmox VE unattended even when the management network requires a tagged VLAN.** Start installation on an untagged/native network; this service supplies the answer file and applies the VLAN configuration on first boot.

## The Solution

1. Boot the node from the prepared USB on an untagged (native VLAN) port; the installer fetches its answer file from `POST /answer`.
2. During installation, Proxmox downloads the first-boot script from `GET /first-boot?interface=vmbr0&vlan-id=1110`.
3. On the first boot, before networking starts, the script turns the bridge into a VLAN-aware bridge and adds `vmbr0.<vlan-id>` with DHCP.
4. The node reports completion to `POST /api/v1/post-install-events`; with a matching DHCP reservation and switch configuration, its management interface receives an address on the VLAN without a manual network edit.

```text
auto vmbr0
iface vmbr0 inet manual
        bridge-ports enp2lacp3
        bridge-vlan-aware yes
        bridge-vids 1110

auto vmbr0.1110
iface vmbr0.1110 inet dhcp
```

Verified on Proxmox VE 9.2. The first-boot script is intentionally limited to the VLAN change; pair it with Ansible for the full network configuration.

## Prerequisites

- Java 25 and Maven 3.9+.
- Linux `libcrypt` with yescrypt support.
- Optional for answer validation and ISO preparation: `proxmox-auto-install-assistant` and `xorriso`.
- On target nodes using the first-boot hook: Bash, ifupdown2 for network configuration, and a reachable DHCP server on the selected VLAN.
- For the tested DHCP-timeout ISO workflow: Ubuntu Bash, `squashfs-tools`, and `sudo`; see the [installation guide](docs/operations/automated-installation.md#longer-dhcp-timeout-tested-workflow).
- Maven configures JNA native access for `spring-boot:run`. Packaged JAR launches need `--enable-native-access=ALL-UNNAMED`.

## Configuration

Run Maven commands from `app/`. Paths in application configuration are relative to that directory.

| Setting | Purpose | Example |
|---|---|---|
| `proxmox.answers.directory` / `PROXMOX_ANSWERS_DIRECTORY` | Directory containing answer templates | `./answers` |
| `SPRING_PROFILES_ACTIVE` | Runtime profile override; defaults to `development` | `production` for deployment |
| `PROXMOX_ROOT_PASSWORD` | Required startup secret, hashed with yescrypt in memory | Protected runtime secret |

Do not commit secrets or pass the password as a command-line argument. Templates
using the password placeholder share one salted yescrypt hash generated at
startup. Restart the service to apply password changes.

The app listens on HTTP at port `8080`. Internal HTTP is supported; an optional
trusted reverse proxy can terminate HTTPS and forward original client headers.
The app rejects request bodies over 1 MiB; set any proxy limit to the same size
or lower. The service has no authentication: restrict it and Actuator to the
trusted installation network, never the public internet.

## Run

From the repository root, set the required password without echoing it and start
the development profile:

```bash
cd app
read -rsp 'Proxmox root password: ' PROXMOX_ROOT_PASSWORD
printf '\n'
export PROXMOX_ROOT_PASSWORD
SPRING_PROFILES_ACTIVE=development mvn spring-boot:run
```

In another terminal, check the answer endpoint without printing the rendered
password hash:

```bash
curl -fsS -o /dev/null -w 'HTTP %{http_code} %{content_type}\n' \
  -H 'Content-Type: application/json' \
  -d '{"network_interfaces":[{"mac":"b4:2e:99:ad:b0:c0"}]}' \
  http://localhost:8080/answer
```

For production, run the `production` profile within the trusted internal network:

```bash
cd app
SPRING_PROFILES_ACTIVE=production mvn spring-boot:run
```

## Access

- Answer endpoint: `POST http://localhost:8080/answer` (application upstream).
- Completion webhook: `POST http://localhost:8080/api/v1/post-install-events`.
- First-boot script: `GET http://localhost:8080/first-boot?interface=vmbr0&vlan-id=1110`.
- Optional HTTPS client URL: `https://your-proxy-host/answer`.
- Health endpoint: `GET http://localhost:8080/actuator/health`.
- OpenAPI JSON (development only): `http://localhost:8080/v3/api-docs`.
- Swagger UI (development only): `http://localhost:8080/swagger-ui.html`.

The health endpoint is available for probes. Non-health Actuator endpoints are
not intended as public APIs.

Request logs use the request's locale; logs without a request use the configured
default locale. Audit fields include `mac`, `file`, `outcome`, `errorCode`, and
`latencyMs`; request JSON, answer contents, and password material are never
logged. The first-boot success log records script generation, not a completed
download or successful execution on the node.

Message-bundle lookup permits fallback to the host's system locale
(`spring.messages.fallback-to-system-locale=true`). The API locale resolver
still defaults to English and supports English and Brazilian Portuguese.

## Tests

From `app/`:

```bash
mvn test
```

The suite renders every committed answer template. If
`proxmox-auto-install-assistant` is installed and supports the template schema,
the suite also validates the rendered files with `validate-answer`.

First-boot tests run the script against temporary network files to test the
resulting layout, idempotence, a missing bridge, and the backup. They never touch
a live network configuration. The hook does not check network-file syntax before
replacement. Verify the switch, DHCP, and first-boot behavior on a Proxmox node;
see the
[hook checks](docs/operations/automated-installation.md#installation-hooks).

## API summary

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/answer` | `200` + `application/toml` | `400` invalid JSON / validation / MAC errors; `413` body over 1 MiB; `404` no matching file; `405` wrong method; `415` wrong Content-Type; `500` unreadable file, invalid TOML, invalid template, or internal error |
| `GET` | `/first-boot` | `200` + `text/x-shellscript` | `400` missing, repeated, extra, or invalid parameters; `405` wrong method; `500` script unavailable |
| `POST` | `/api/v1/post-install-events` | `200` + JSON receipt | `400` invalid JSON / metadata; `413` body over 1 MiB; `405` wrong method; `415` wrong Content-Type |

Errors use `application/json` with a localized message and stable `errorCode`.

## Further reading

- [Rendered answers, API checks, tested DHCP-timeout ISO preparation, and USB installation](docs/operations/automated-installation.md)
- [Answer templates, password policy, and disk selection](app/answers/README.md)
- [API and filesystem decisions](docs/adr/README.md)
