# Proxmox Web Installer

Spring Boot service for Proxmox automated installation: answer delivery, completion logging, and an optional first-boot VLAN script.

## Purpose

Serve Proxmox installation answers, log completion, and provide an optional first-boot DHCP VLAN script.

## Overview

- Exposes `POST /answer` for the Proxmox HTTP answer source.
- Tries installer-reported MAC addresses in request order and matches filenames case-insensitively.
- Reads operator-managed TOML templates from a configurable directory.
- Renders the root-password hash in memory and returns TOML.
- Localizes API errors and request logs; audit records omit request bodies and answer contents.
- Receives installation-completion webhooks without storing the payload.
- Serves a first-boot script for DHCP VLAN configuration on installer-generated bridges.

## Prerequisites

- Java 25 and Maven 3.9+.
- Linux `libcrypt` with yescrypt support.
- Optional for answer validation and ISO preparation: `proxmox-auto-install-assistant` and `xorriso`.
- On target nodes using the first-boot hook: Bash, ifupdown2, and a reachable DHCP server on the selected VLAN.
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
logged.

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

First-boot tests use temporary network files and a controlled validator to test
the resulting layout, idempotence, failures, and backup. The default validator
does not parse ifupdown2 configuration. To additionally run the fixtures against
a real ifupdown2 validator, set `PVE_TEST_IFUP` to an executable accepting
`--syntax-check -a --interfaces <temporary-file>`. It must only validate syntax,
never activate networking, and may need root privileges. Validate the real
parser, switch, DHCP, and first-boot behavior on a Proxmox node; see the
[hook checks](docs/operations/automated-installation.md#installation-hooks).

## API summary

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/answer` | `200` + `application/toml` | `400` invalid JSON / validation / MAC errors; `413` body over 1 MiB; `404` no matching file; `405` wrong method; `415` wrong Content-Type; `500` unreadable file, invalid TOML, invalid template, or internal error |
| `POST` | `/api/v1/post-install-events` | `200` + JSON receipt | `400` invalid JSON / metadata; `413` body over 1 MiB; `405` wrong method; `415` wrong Content-Type |
| `GET` | `/first-boot` | `200` + `text/x-shellscript` | `400` missing, repeated, extra, or invalid parameters; `405` wrong method; `500` script unavailable |

Errors use `application/json` with a localized message and stable `errorCode`.

## Further reading

- [Rendered answers, API checks, tested DHCP-timeout ISO preparation, and USB installation](docs/operations/automated-installation.md)
- [Answer templates, password policy, and disk selection](app/answers/README.md)
- [API and filesystem decisions](docs/adr/README.md)
