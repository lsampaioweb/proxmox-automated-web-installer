# Answer files

These files are the runtime store for `POST /answer`. Each file name is
`mac_bind[0].mac_address` from `03-proxmox-cluster/ansible/host_vars`.
The service tries every MAC in the installer POST until one file matches.

Each file is a Proxmox answer template. The committed
`__PROXMOX_ROOT_PASSWORD_HASH__` token is replaced in memory with a yescrypt
hash generated from `PROXMOX_ROOT_PASSWORD` when the application starts. Source
files are never modified, and the hash/password are never logged.

Bond/VLAN layout stays with Ansible after install; the installer configures its
initial network interface.

## Configure the root password

Set `PROXMOX_ROOT_PASSWORD` in the service environment. The application requires
the variable at startup and fails to start if it is missing or blank. The value
is plaintext only in the runtime environment; do not put it in a committed file,
command-line argument, TOML answer file, or log. Use your service manager's
protected environment/secret mechanism in production.

For an interactive local run, enter it without echoing it:

```bash
read -rsp 'Proxmox root password: ' PROXMOX_ROOT_PASSWORD
printf '\n'
export PROXMOX_ROOT_PASSWORD
SPRING_PROFILES_ACTIVE=development mvn spring-boot:run
```

All answer files use the same password and startup-generated hash. A new
random salt is generated at each application startup. Restart the application
after changing the environment value.

## Configure `disk-list`

Set the OS install disk for that host. On an already-running Proxmox node:

```bash
lsblk -d -o NAME,SIZE,MODEL,SERIAL
# or, when available:
proxmox-auto-install-assistant device-info -t disk
```

Edit each host answer file:

```toml
[disk-setup]
filesystem = "ext4"
disk-list = ["nvme0n1"]
```

Use the real device name for that host (`sda`, `nvme0n1`, …). Prefer a
unique filter (for example `filter.ID_SERIAL = "..."`) when disk names are
not stable across boots.

## Validate rendered answer files

Committed templates contain the placeholder and are not ready to install. Validate
the rendered answer returned by the service, not the template on disk.

From `app/`, with the service running and the appropriate host MAC:

```bash
curl -fsS -H 'Content-Type: application/json' \
	-d '{"network_interfaces":[{"mac":"b4:2e:99:ad:b0:c0"}]}' \
	http://localhost:8080/answer > /tmp/proxmox-answer.toml
proxmox-auto-install-assistant validate-answer /tmp/proxmox-answer.toml
rm /tmp/proxmox-answer.toml
```

Repeat for each host file. Protect and remove the temporary rendered answer
because it contains the real password hash.

## Home

| Host | First NIC | Answer file |
|---|---|---|
| pve-01.lan.home | enp2lacp0 | `60:be:b4:08:f7:66.toml` |
| pve-02.lan.home | enp2lacp0 | `00:d0:b4:01:77:7a.toml` |

## Homelab

| Host | First NIC | Answer file |
|---|---|---|
| pve-01.lan.homelab | enp1board0 | `b4:2e:99:ad:b0:c0.toml` |
| pve-02.lan.homelab | enp1board0 | `18:c0:4d:f1:fe:c9.toml` |
| pve-03.lan.homelab | enp1board0 | `b4:2e:99:f4:b8:21.toml` |
| pve-04.lan.homelab | enp1board0 | `00:e0:4c:49:fb:b7.toml` |
| pve-05.lan.homelab | enp1board0 | `00:e0:4c:56:a1:3d.toml` |
| pve-06.lan.homelab | enp1board0 | `00:e0:0c:a6:70:79.toml` |
| pve-07.lan.homelab | enp1board0 | `00:e0:0b:d9:b0:2c.toml` |
