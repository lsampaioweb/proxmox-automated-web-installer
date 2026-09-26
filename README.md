# Proxmox Web Installer

Lightweight Spring Boot service that returns Proxmox automated-installation TOML answer files from a MAC address lookup.

## Purpose

Serve the operator-managed answer file for a MAC address reported by the Proxmox installer.

## Overview

- Exposes a single endpoint: `POST /answer`
- Identifies the node by trying each `network_interfaces[].mac` until a file matches
- Reads `<mac>.toml` from a configurable filesystem directory on every request
- Returns the selected TOML with only its root-password placeholder replaced in memory, using `Content-Type: application/toml`
- Returns a JSON error envelope with localized messages and stable error codes for invalid input, missing files, and unreadable or invalid TOML
- Records audit metadata without logging request bodies or answer-file contents

## Prerequisites

- Java 25
- Maven 3.9+
- A Linux runtime with `libcrypt` supporting yescrypt
- JVM native access enabled for JNA (`--enable-native-access=ALL-UNNAMED`)
- A directory of answer files named `{mac-address}.toml`. Seeded files for the Proxmox hosts are in [`app/answers/`](app/answers/README.md).
- Optional: `proxmox-auto-install-assistant` and `xorriso` to validate answer files and prepare an automated-install ISO

## Configuration

Run commands from `app/`. Relative paths below are relative to that directory.

| Setting | Purpose | Example |
|---|---|---|
| `proxmox.answers.directory` | Filesystem directory that contains answer files | `./answers` |
| `SPRING_PROFILES_ACTIVE` | Runtime profile override | `development` |
| `PROXMOX_ANSWERS_DIRECTORY` | Environment override for the answers directory | `/var/lib/proxmox-web-installer/answers` |
| `PROXMOX_ROOT_PASSWORD` | Plaintext root password; required at startup and hashed with yescrypt in memory | Set through a protected runtime secret |

Do not commit secrets or pass the password as a command-line argument. The
application creates one salted yescrypt hash at startup, substitutes it into
answer responses in memory, and never writes the hash back to the answer files.
All hosts currently share this root password. Restart the service to apply a
password change. TLS may be terminated by a reverse proxy; this application does
not enable authentication.

## Prepare answer files before a real install

Seeded files in [`app/answers/`](app/answers/README.md) are templates:

- `root-password-hashed` contains `__PROXMOX_ROOT_PASSWORD_HASH__`, replaced only in the response
- `disk-list = ["sda"]` may not be the OS disk on that host

Set `PROXMOX_ROOT_PASSWORD` through the service's protected environment before
starting it. Full setup and host mapping: [`app/answers/README.md`](app/answers/README.md).
For a packaged JAR launched outside Maven, include
`--enable-native-access=ALL-UNNAMED` in the JVM options for JNA/libcrypt access.

Find the OS disk on the target host (or from similar hardware):

```bash
lsblk -d -o NAME,SIZE,MODEL,SERIAL
```

Set `disk-list` to that device, for example `disk-list = ["nvme0n1"]`.

Validate the rendered response from `app/` while the service is running with
`PROXMOX_ROOT_PASSWORD` configured:

```bash
curl -fsS -H 'Content-Type: application/json' \
  -d '{"network_interfaces":[{"mac":"b4:2e:99:ad:b0:c0"}]}' \
  http://localhost:8080/answer > /tmp/proxmox-answer.toml
proxmox-auto-install-assistant validate-answer /tmp/proxmox-answer.toml
rm -f /tmp/proxmox-answer.toml
```

The response contains the generated root-password hash; protect and remove the
temporary file after validation. Committed templates are not valid install
answers until the service renders the password placeholder.

## Run

All Maven commands are run from `app/`.

For a local interactive run, enter the password without echoing it before starting
the service:

```bash
read -rsp 'Proxmox root password: ' PROXMOX_ROOT_PASSWORD
printf '\n'
export PROXMOX_ROOT_PASSWORD
```

Development (console + file logs, port `8080`):

```bash
cd app
SPRING_PROFILES_ACTIVE=development mvn spring-boot:run
```

Production (explicit `production` profile, file logs only, port `9443`):

```bash
cd app
SPRING_PROFILES_ACTIVE=production mvn spring-boot:run
```

Use `development` for the curl checks below so audit lines appear on the console.

## Access

- Answer endpoint: `POST http://localhost:8080/answer` (`development`)
- Answer endpoint: `POST http://localhost:9443/answer` (`production`)
- Health endpoint: `GET http://localhost:8080/actuator/health` (`development`)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs` (`development`)
- Swagger UI: `http://localhost:8080/swagger-ui.html` (`development`)

The answer endpoint is intentionally anonymous because this service is restricted
to the internal homelab network. Do not expose it directly to the public internet.
Actuator health is available for probes; non-health actuator endpoints are not
intended as public application APIs.

Logs:

- `development`: console and `app/logs/proxmox-web-installer.log`
- `production`: `app/logs/proxmox-web-installer.log` only

A successful lookup writes an INFO audit line like:

```text
Answer request timestamp=2026-09-20T21:00:00Z client=127.0.0.1 product=pve productName=Proxmox VE iso=9.0 isoRelease=1 enableBtrfs=true schema=1.0 systemSerial=900030XXXX systemUuid=6fb36fd4-77a1-57e6-13ae-XXXXXXcb48d7 systemName=RS700 baseboardSerial=23041870000XXXX chassisSerial=I02308XXXX macs=b4:2e:99:ad:b0:c0 mac=b4:2e:99:ad:b0:c0 file=b4:2e:99:ad:b0:c0.toml outcome=SUCCESS errorCode= latencyMs=4
```

Every request that reaches the service writes this INFO line, including errors. `outcome` is one of `SUCCESS`, `BAD_REQUEST`, `NOT_FOUND`, `UNREADABLE`, or `INVALID_TOML`. Count them with `grep -c "outcome=SUCCESS"` (and the other outcomes) in the log file.

Invalid JSON and Bean Validation never reach the service. Those still start with `Answer request` and use `outcome=BAD_REQUEST errorCode=INVALID_JSON` or `errorCode=VALIDATION_ERROR`.

With the `debug` profile, each MAC lookup also logs `Answer MAC attempt mac=... result=HIT` or `result=MISS`.

The line must not contain the raw request JSON or the TOML body.

### Curl checks (`development`)

From another terminal, with the app already running.

HTTP 200 and TOML body (`pve-01.lan.homelab`):

```bash
curl -sS -D - -o /tmp/pve-answer.toml \
  -H "Content-Type: application/json" \
  -d '{"network_interfaces":[{"mac":"B4:2E:99:AD:B0:C0","link":"enp1board0"}]}' \
  http://localhost:8080/answer
```

Expect `HTTP/1.1 200`, `Content-Type: application/toml`, and a TOML file in `/tmp/pve-answer.toml`. Console audit: `mac=b4:2e:99:ad:b0:c0 file=b4:2e:99:ad:b0:c0.toml outcome=SUCCESS`.

HTTP 200 using a later NIC when the first MAC has no file:

```bash
curl -sS -D - -o /tmp/pve-answer-second.toml \
  -H "Content-Type: application/json" \
  -d '{"network_interfaces":[{"mac":"00:11:22:33:44:55","link":"nic0"},{"mac":"18:c0:4d:f1:fe:c9","link":"enp1board0"}]}' \
  http://localhost:8080/answer
```

Expect `200` and audit `mac=18:c0:4d:f1:fe:c9 file=18:c0:4d:f1:fe:c9.toml outcome=SUCCESS`.

HTTP 400 invalid JSON:

```bash
curl -sS -D - \
  -H "Content-Type: application/json" \
  -d "{ not-json" \
  http://localhost:8080/answer
```

Expect `400` and `Content-Type: application/json` with `"errorCode":"INVALID_JSON"`.

HTTP 400 no usable MAC:

```bash
curl -sS -D - \
  -H "Content-Type: application/json" \
  -d '{"network_interfaces":[{"mac":"  "}]}' \
  http://localhost:8080/answer
```

Expect `400` and `"errorCode":"ANSWER_MAC_REQUIRED"`. Console audit: `outcome=BAD_REQUEST`.

HTTP 404 unknown MAC:

```bash
curl -sS -D - \
  -H "Content-Type: application/json" \
  -d '{"network_interfaces":[{"mac":"aa:bb:cc:dd:ee:ff"}]}' \
  http://localhost:8080/answer
```

Expect `404` and `"errorCode":"ANSWER_NOT_FOUND"`. Console audit: `outcome=NOT_FOUND`.

Follow the log file with:

```bash
tail -f logs/proxmox-web-installer.log
```

## ISO preparation

The Proxmox installer only calls this service if the boot media (or DHCP/DNS) tells it the answer URL. Preparing an ISO embeds that URL and enables the **Automated Installation** boot entry.

Official reference: [Proxmox Automated Installation](https://pve.proxmox.com/wiki/Automated_Installation).

### Prerequisites for ISO prep

1. `PROXMOX_ROOT_PASSWORD` is configured through the service's protected runtime environment and each host's `disk-list` is correct.
2. This service is reachable from the install network at a stable URL ending in `/answer`.
3. Prefer HTTPS. If the certificate is self-signed or the URL uses an IP, pin the SHA256 fingerprint (below).
4. On the machine that builds the ISO:

```bash
sudo apt update
sudo apt install proxmox-auto-install-assistant xorriso
```

5. Download a current Proxmox VE ISO from [proxmox.com](https://www.proxmox.com/en/downloads) to a local path, for example `/path/to/proxmox-ve.iso`.

### Bake the answer URL into the ISO

Replace the URL with your real answer-server address. Production in this project listens on port `9443` unless a reverse proxy terminates TLS on `443`.

HTTP on a trusted lab segment (not recommended for anything beyond temporary tests):

```bash
proxmox-auto-install-assistant prepare-iso \
  /path/to/proxmox-ve.iso \
  --fetch-from http \
  --url "http://172.16.10.X:9443/answer" \
  --output /path/to/proxmox-ve-auto.iso
```

HTTPS with certificate fingerprint (recommended when using an IP or a self-signed / private CA cert):

```bash
# Fingerprint of the certificate that terminates TLS (proxy or app)
openssl x509 -in /path/to/server.crt -noout -fingerprint -sha256
```

```bash
proxmox-auto-install-assistant prepare-iso \
  /path/to/proxmox-ve.iso \
  --fetch-from http \
  --url "https://answers.lan.homelab/answer" \
  --cert-fingerprint "AA:BB:CC:DD:..." \
  --output /path/to/proxmox-ve-auto.iso
```

This application does not require an authorization token. Do not pass `--answer-auth-token` unless you later add bearer authentication to the service. If set, the token is stored in plaintext inside the ISO.

### Inspect the prepared ISO

```bash
proxmox-auto-install-assistant inspect-iso /path/to/proxmox-ve-auto.iso
```

Confirm fetch mode is `http` and the URL is your `/answer` endpoint. Sensitive fields are redacted by default; use `--show-sensitive` only when needed.

### Boot from USB

Write the prepared ISO to a USB stick (device path is an example — check with `lsblk` first):

```bash
sudo dd if=/path/to/proxmox-ve-auto.iso of=/dev/sdX bs=4M status=progress conv=fsync
```

Boot the target host from that USB. The **Automated Installation** entry is selected after about 10 seconds. The installer POSTs JSON (including MACs) to your URL; this service returns the matching `{mac}.toml`.

Watch the answer-server audit log during the install (`outcome=SUCCESS` and the expected MAC/filename). On the installer, failures often leave clues in `/tmp/fetch_answer.log` and `/tmp/auto_installer`.

### Network boot (PXE)

Instead of a USB ISO, split the prepared image for TFTP/PXE. `--output` must be a directory:

```bash
proxmox-auto-install-assistant prepare-iso \
  /path/to/proxmox-ve.iso \
  --fetch-from http \
  --url "https://answers.lan.homelab/answer" \
  --cert-fingerprint "AA:BB:CC:DD:..." \
  --pxe \
  --output /srv/tftp/proxmox-auto/
```

Optional iPXE snippet:

```bash
proxmox-auto-install-assistant prepare-iso \
  /path/to/proxmox-ve.iso \
  --fetch-from http \
  --url "https://answers.lan.homelab/answer" \
  --cert-fingerprint "AA:BB:CC:DD:..." \
  --pxe-loader ipxe \
  --output /srv/tftp/proxmox-auto/
```

Answer fetch still uses HTTP(S) to this service; only the kernel/initrd delivery changes.

### Providing the URL without baking it into the ISO

You can use one generic prepared ISO and supply the URL at runtime:

| Method | How |
|---|---|
| DHCP option `250` | DHCP returns the answer URL |
| DNS TXT | Record at `proxmox-auto-installer.{search-domain}` |

TLS fingerprint can come from DHCP option `251` or DNS TXT `proxmox-auto-installer-cert-fingerprint.{search-domain}`, but only when the URL was obtained by the **same** method. Baking `--url` (and optional `--cert-fingerprint`) into the ISO is the simplest path for this project.

### Checklist before first automated install

- [ ] `PROXMOX_ROOT_PASSWORD` is configured through the service's protected runtime environment
- [ ] Every host `{mac}.toml` has the correct `disk-list` and password placeholder
- [ ] A rendered answer from `/answer` passes `proxmox-auto-install-assistant validate-answer`
- [ ] Answer service is up and reachable from the install NIC network
- [ ] Curl against `/answer` with a real host MAC returns `200` and TOML
- [ ] Prepared ISO (or DHCP/DNS) points at that same `/answer` URL
- [ ] Audit log shows `SUCCESS` when you curl; installer will use the same path

## Tests

From `app/`:

```bash
mvn test
```

Automated tests use a temporary answers directory and do not require a live Proxmox installer. To validate a host answer, follow “Validate rendered answer files” above; the committed template itself contains a password placeholder and is not directly installable.

## API summary

| Method | Path | Success | Errors |
|---|---|---|---|
| `POST` | `/answer` | `200` + `application/toml` | `400` invalid JSON or no usable MAC; `404` no matching file; `500` unreadable file or invalid TOML |

Error responses use `application/json` with the documented error envelope and
stable `errorCode` values.
