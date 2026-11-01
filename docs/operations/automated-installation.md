# Automated Installation Operations

This guide covers answer validation, API checks, ISO preparation, and USB
installation. Start the service and configure `PROXMOX_ROOT_PASSWORD` as described
in the [project README](../../README.md). Host templates, password settings, and
disk selectors are documented in the [answer-file guide](../../app/answers/README.md).

## Prerequisites

- A Proxmox VE ISO downloaded from [proxmox.com](https://www.proxmox.com/en/downloads).
- `proxmox-auto-install-assistant` and `xorriso` on the ISO preparation machine.
- For DHCP-timeout customization: Ubuntu Bash, `squashfs-tools`, working `sudo`,
and enough free space for the extracted filesystem and intermediate ISOs.
- PVE 9.1 or newer when answer files use interface-name pinning.
- A reachable answer service and a configured root password.

On Ubuntu, install the image-processing tools with:

```bash
sudo apt update
sudo apt install xorriso squashfs-tools
```

Install `proxmox-auto-install-assistant` separately following the
[official preparation guidance](https://pve.proxmox.com/wiki/Automated_Installation#Assistant_Tool).
Do not assume it is available in the default Ubuntu repositories. On a
Debian-based preparation machine with the appropriate Proxmox repository
configured, install it with `sudo apt install proxmox-auto-install-assistant`.

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
project the application listens on port `8080`. Internal HTTP is supported;
HTTPS through a trusted reverse proxy is optional and recommended for transport
protection. Never expose the service publicly.

The assistant uses `--fetch-from http` for its HTTP answer-source mode. This
selects the source mode; it can still use TLS when the URL begins with
`https://`. Prefer HTTPS for production.

### Longer DHCP timeout (tested workflow)

The stock PVE 9.2-1 installer uses a 10-second initial DHCP discovery window.
On the tested four-NIC Mini-PC, answer fetching failed with `Network is
unreachable` before a lease was acquired. An image with a 120-second window
completed automated installation without a debug shell or manual network setup.
This timeout is an acquisition limit, not a mandatory two-minute delay.

Run the [preparation script](../../scripts/prepare-proxmox-auto-iso.sh) from the
repository root as your normal user. It prompts for `sudo` when needed. Start
with the original ISO, not the previously prepared image:

```bash
bash scripts/prepare-proxmox-auto-iso.sh \
  "$HOME/Downloads/ISO/proxmox-ve_9.2-1.iso" \
  "$HOME/Downloads/ISO/proxmox-ve-auto-dhcp120.iso"
```

The default answer URL is
`http://proxmox-auto-install-assistant.lan.homelab:8080/answer`; use plain HTTP
only on a trusted internal network. The hostname must resolve from the installer
network using the DNS server supplied by DHCP. A successful lookup on the
preparation laptop alone does not establish this. For the tested network:

```bash
nslookup proxmox-auto-install-assistant.lan.homelab 20.1.9.14
```

The expected address is `192.168.3.3`. Replace the DNS server, hostname, and
expected address for other environments. To override both the URL and timeout,
provide the optional third and fourth arguments:

```bash
bash scripts/prepare-proxmox-auto-iso.sh \
  "$HOME/Downloads/ISO/proxmox-ve_9.2-1.iso" \
  "$HOME/Downloads/ISO/proxmox-ve-auto-custom.iso" \
  'http://your-answer-host:8080/answer' 120
```

The script preserves the source ISO and refuses an existing output path. Before
publishing the result, it reads back the embedded installer filesystem and
answer-source settings, checks the DHCP timeout, and verifies the original boot
UUID and BIOS/UEFI entries. Temporary build files and the assistant's sidecar
are cleaned up; there is no persistent sidecar to inspect afterward.

This is custom remastering, not a supported assistant timeout option. The
filesystem layout and compression settings were verified against PVE 9.2-1;
revalidate the build and hardware boot when changing ISO releases. Apple HFS+
support is deliberately disabled to avoid overlapping GPT partitions during
remastering; BIOS and UEFI boot entries are retained.

The script accepts HTTPS URLs but does not expose certificate fingerprint
pinning. An HTTPS endpoint must present a certificate trusted by the installer.
Neither answer-file network settings nor first-boot hooks can configure DHCP
before the HTTP answer fetch.

### Standard preparation alternatives

These commands do not extend the installer's DHCP timeout. Use them when the
stock discovery window is sufficient. For a trusted internal network:

```bash
proxmox-auto-install-assistant prepare-iso \
  proxmox-ve_9.2-1.iso \
  --fetch-from http \
  --url 'http://proxmox-auto-install-assistant.lan.homelab:8080/answer' \
  --output proxmox-ve-auto.iso
```

For HTTPS, pin the SHA256 fingerprint of the certificate presented by the proxy
(or by the application if it terminates TLS):

```bash
openssl x509 -in /path/to/server.crt -noout -fingerprint -sha256

proxmox-auto-install-assistant prepare-iso \
  proxmox-ve_9.2-1.iso \
  --fetch-from http \
  --url 'https://192.168.3.3/answer' \
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

## Installation hooks

The webhook reports successful installation.

### Completion webhook

All nine answer templates contain:

```toml
[post-installation-webhook]
url = "http://proxmox-auto-install-assistant.lan.homelab:8080/api/v1/post-install-events"
```

The installer must be able to reach this URL from its initial untagged network.
The receiver accepts the official hyphenated `network-interfaces` key, ignores
unknown fields, and returns `{"received":true,"receivedAt":"..."}`. Required
metadata is the product short name, FQDN, and a nonempty array of interfaces
with valid MACs. DMI UUID, interface name, product fullname, and version are
optional. The existing 1 MiB body limit and localized errors apply.

Test the receiver without sending secrets:

```bash
curl -fsS -H 'Content-Type: application/json' \
  -d '{"product":{"short":"pve","fullname":"Proxmox VE","version":"9.2"},"fqdn":"pve-01.lan.home","network-interfaces":[{"name":"enp2lacp3","mac":"60:be:b4:08:f7:69"}]}' \
  http://localhost:8080/api/v1/post-install-events
```

The existing application log records receipt time, product identifiers, FQDN,
optional system UUID, and interface names/MACs only. It does not retain the
payload or log serials, addresses, disks, udev properties, machine IDs, SSH
keys, or unknown fields. There is no database, webhook-triggered action, or
separate completion-record store.

## Write the USB installer

Writing an ISO erases the target disk. Identify the whole USB device, unmount its
filesystems, and verify the device name again immediately before writing. Never
use a mounted path or a partition such as `/dev/sdX1` in place of the whole disk.

```bash
lsblk -o PATH,SIZE,MODEL,TRAN,TYPE,MOUNTPOINTS
installer_iso="$HOME/Downloads/ISO/proxmox-ve-auto-dhcp120.iso"
sudo dd if="$installer_iso" of=/dev/sdX bs=4M status=progress conv=fsync
```

Replace `/dev/sdX` with the verified whole-device path; never assume `/dev/sda`
is the USB drive. If using a standard preparation alternative, set
`installer_iso` to that output instead. If the desktop mounts the USB again
after writing, unmount it before verification. In the same terminal, compare
the written bytes with the selected ISO:

```bash
lsblk -o PATH,SIZE,MODEL,TRAN,TYPE,MOUNTPOINTS
sudo cmp -n "$(stat -c%s "$installer_iso")" "$installer_iso" /dev/sdX \
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
- A DNS-based answer URL resolves through the installer network's DHCP-provided DNS.
- The network cable is connected before boot; installation needs no debug-shell changes.
- The audit log records `outcome=SUCCESS` for the expected MAC and filename.
