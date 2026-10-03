# Proxmox Web Installer

Spring Boot service that returns Proxmox automated-installation TOML answer files selected by installer-reported MAC address.

## Purpose

Serve the operator-managed answer file selected from a Proxmox installer request.

## Overview

- Exposes `POST /answer` for the Proxmox HTTP answer source.
- Tries installer-reported MAC addresses in request order and matches filenames case-insensitively.
- Reads operator-managed TOML templates from a configurable directory.
- Renders the root-password hash in memory and returns TOML.
- Localizes API errors and request logs; audit records omit request bodies and answer contents.

## Prerequisites

- Java 25 and Maven 3.9+.
- Linux `libcrypt` with yescrypt support.
- Optional for answer validation and ISO preparation: `proxmox-auto-install-assistant` and `xorriso`.
- Maven configures JNA native access for `spring-boot:run`. Packaged JAR launches need `--enable-native-access=ALL-UNNAMED`.

## Configuration

Run Maven commands from `app/`. Paths in application configuration are relative to that directory.

| Setting | Purpose | Example |
|---|---|---|
| `proxmox.answers.directory` / `PROXMOX_ANSWERS_DIRECTORY` | Directory containing answer templates | `./answers` |
| `SPRING_PROFILES_ACTIVE` | Runtime profile override | `development` |
| `PROXMOX_ROOT_PASSWORD` | Required startup secret, hashed with yescrypt in memory | Protected runtime secret |

Do not commit secrets or pass the password as a command-line argument. Templates
using the password placeholder share one salted yescrypt hash generated at
startup. Restart the service to apply password changes.

In production, the app listens on plain HTTP at port `8080` behind a trusted
reverse proxy. The proxy should terminate TLS, typically on port `443`, and
forward the original client headers. The app rejects request bodies over 1 MiB;
set the proxy limit to the same size or lower. The service has no authentication,
so keep it and its Actuator endpoints off the public internet.

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

For production, run the `production` profile behind the configured TLS reverse
proxy:

```bash
cd app
SPRING_PROFILES_ACTIVE=production mvn spring-boot:run
```

## Access

- Answer endpoint: `POST http://localhost:8080/answer` (application upstream)
- Production client URL: `https://your-proxy-host/answer`
- Health endpoint: `GET http://localhost:8080/actuator/health`
- OpenAPI JSON and Swagger UI are enabled only in `development` at `/v3/api-docs` and `/swagger-ui.html`.

The health endpoint is available for probes. Non-health Actuator endpoints are
not intended as public APIs.

Request logs use the request's locale; logs without a request use the configured
default locale. Audit fields include `mac`, `file`, `outcome`, `errorCode`, and
`latencyMs`; request JSON, answer contents, and password material are never
logged.

## Tests

From `app/`:

```bash
mvn test
```

The suite renders every committed answer template. If
`proxmox-auto-install-assistant` is installed and supports the template schema,
the suite also validates the rendered files with `validate-answer`.

## API summary

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/answer` | `200` + `application/toml` | `400` invalid JSON / validation / MAC errors; `413` body over 1 MiB; `404` no matching file; `405` wrong method; `415` wrong Content-Type; `500` unreadable file, invalid TOML, invalid template, or internal error |

Errors use `application/json` with a localized message and stable `errorCode`.

## Further reading

- [Answer templates, password policy, and disk selection](app/answers/README.md)
- [API and filesystem decisions](docs/adr/README.md)
- [Rendered answers, API checks, ISO preparation, and USB installation](docs/operations/automated-installation.md)
