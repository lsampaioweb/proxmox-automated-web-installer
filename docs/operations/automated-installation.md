# Automated Installation Operations

This guide covers answer validation, API checks, ISO preparation, and USB
installation. Start the service and configure `PROXMOX_ROOT_PASSWORD` as described
in the [project README](../../README.md). Host templates, password settings, and
disk selectors are documented in the [answer-file guide](../../app/answers/README.md).

## Prerequisites

- A Proxmox VE ISO downloaded from [proxmox.com](https://www.proxmox.com/en/downloads).
- `proxmox-auto-install-assistant` and `xorriso` on the ISO preparation machine.
- PVE 9.1 or newer when answer files use interface-name pinning.
- A reachable answer service and a configured root password.

On Debian-based systems, install the preparation tools with:

```bash
sudo apt update
sudo apt install proxmox-auto-install-assistant xorriso
```

## Validate a rendered answer

Committed files are templates, not complete install answers. Run the service
from `app/` with `PROXMOX_ROOT_PASSWORD` configured, then request a rendered
answer. It contains the generated password hash, so protect and remove the
output file after validation.

```bash
curl -fsS -H 'Content-Type: application/json' \
  -d '{"network_interfaces":[{"mac":"b4:2e:99:ad:b0:c0"}]}' \
  http://localhost:8080/answer > /tmp/proxmox-answer.toml
proxmox-auto-install-assistant validate-answer /tmp/proxmox-answer.toml
rm -f /tmp/proxmox-answer.toml
```

Repeat for each host MAC. The Maven test suite renders every committed template;
when the installed assistant supports the template schema, it also runs
`validate-answer` against each rendered file.

## Check the API

Send these requests to the development service on `localhost:8080`.

Successful lookup:

```bash
curl -sS -o /dev/null -w 'HTTP %{http_code} %{content_type}\n' \
  -H 'Content-Type: application/json' \
  -d '{"network_interfaces":[{"mac":"b4:2e:99:ad:b0:c0"}]}' \
  http://localhost:8080/answer
```

A later installer MAC is tried when an earlier one has no file:

```bash
curl -sS -o /dev/null -w 'HTTP %{http_code} %{content_type}\n' \
  -H 'Content-Type: application/json' \
  -d '{"network_interfaces":[{"mac":"00:11:22:33:44:55"},{"mac":"18:c0:4d:f1:fe:c9"}]}' \
  http://localhost:8080/answer
```

Malformed JSON returns `400` with `INVALID_JSON`:

```bash
curl -sS -D - -H 'Content-Type: application/json' \
  -d '{ not-json' http://localhost:8080/answer
```

No usable MAC returns `400` with `ANSWER_MAC_REQUIRED`; a malformed MAC returns
`400` with `ANSWER_MAC_INVALID`; an unknown MAC returns `404` with
`ANSWER_NOT_FOUND`. An unsupported method returns `405` with
`METHOD_NOT_ALLOWED`. Request bodies larger than 1 MiB return `413` with
`REQUEST_BODY_TOO_LARGE`.

Request audit logs include `mac`, `file`, `outcome`, `errorCode`, and `latencyMs`.
Log message text follows the request locale; structured field names and values
remain stable. Search for `outcome=SUCCESS` to confirm a successful lookup. Logs
must never contain request JSON, answer contents, or password material.

## Prepare the ISO

The automated installer fetches the answer from the configured URL. In this
project the application listens on port `8080`; production clients should reach
it through the TLS-terminating reverse proxy, typically on port `443`.

The assistant uses `--fetch-from http` for its HTTP answer-source mode. This
selects the source mode; it can still use TLS when the URL begins with
`https://`. Prefer HTTPS for production.

For a trusted test network only:

```bash
proxmox-auto-install-assistant prepare-iso \
  proxmox-ve_9.2-1.iso \
  --fetch-from http \
  --url 'http://proxmox-auto-install-assistant.lan.home:8080/answer' \
  --output proxmox-ve-auto.iso
```

For HTTPS, pin the SHA256 fingerprint of the certificate presented by the proxy
(or by the application if it terminates TLS):

```bash
openssl x509 -in /path/to/server.crt -noout -fingerprint -sha256

proxmox-auto-install-assistant prepare-iso \
  proxmox-ve_9.2-1.iso \
  --fetch-from http \
  --url 'https://proxmox-auto-install-assistant.lan.home/answer' \
  --cert-fingerprint 'AA:BB:CC:DD:...' \
  --output proxmox-ve-auto.iso
```

Replace the sample hostname, ISO path, and fingerprint with your environment's
values. The fingerprint must match the certificate that terminates TLS. The
service does not require an authorization token; do not add one to the ISO.

Inspect the preparation sidecar:

```bash
xorriso -indev proxmox-ve-auto.iso -find / -name auto-installer-mode.toml
cat auto-installer-mode.toml
```

Confirm that the sidecar reports `mode = "http"` and a URL ending in
`/answer`. The sidecar records preparation settings; it is not a readback of the
ISO's embedded contents.

## Write the USB installer

Writing an ISO erases the target disk. Identify the whole USB device, unmount its
filesystems, and verify the device name again immediately before writing. Never
use a mounted path or a partition such as `/dev/sdX1` in place of the whole disk.

```bash
lsblk -o PATH,SIZE,MODEL,TRAN,TYPE,MOUNTPOINTS
sudo dd if=proxmox-ve-auto.iso of=/dev/sdX bs=4M status=progress conv=fsync
```

Replace `/dev/sdX` with the verified whole-device path. If the desktop mounts
the USB again after writing, unmount it before verification. Compare the written
bytes with the source ISO:

```bash
lsblk -o PATH,SIZE,MODEL,TRAN,TYPE,MOUNTPOINTS
sudo cmp -n "$(stat -c%s proxmox-ve-auto.iso)" proxmox-ve-auto.iso /dev/sdX \
  && echo 'USB matches ISO'
```

This confirms the image bytes on the USB; it does not confirm that the target
host can reach the answer service from its install network.

## First-install checklist

- `PROXMOX_ROOT_PASSWORD` is set through the protected runtime environment.
- Each host answer file has a verified disk selector.
- Each answer file has exactly one password setting: the runtime placeholder or
a completed password value.
- A rendered answer passes `proxmox-auto-install-assistant validate-answer`.
- The answer service is reachable from the target's install network.
- A request using a real host MAC returns `200` and TOML.
- The prepared ISO uses the same `/answer` URL.
- The audit log records `outcome=SUCCESS` for the expected MAC and filename.
