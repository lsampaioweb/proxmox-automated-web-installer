# Answer files

These files are the runtime store for `POST /answer`. Each file name is
`mac_bind[0].mac_address` from `03-proxmox-cluster/ansible/host_vars`.
Filenames may use any hex letter casing; the service matches them
case-insensitively. Prefer lowercase names for consistency with the seeded files.
The service tries every MAC in the installer POST until one file matches.

Each file is a Proxmox answer template:

- `root-password-hashed` uses `__PROXMOX_ROOT_PASSWORD_HASH__`, replaced in memory
- `[network.interface-name-pinning]` maps every `mac_bind` NIC from Ansible (PVE 9.1+)
- `disk-list = ["sda"]` is a placeholder — replace before a real install

Source files are never modified. Secrets are never logged.

Bond/VLAN layout stays with Ansible after install; the installer configures the
initial network interface and pinned names.

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

All answer files that use the password placeholder share the same startup hash.
A new random salt is generated at each application startup. Restart the application
after changing the environment value.

Templates may instead ship a finished `root-password` or `root-password-hashed`
value and omit the placeholder. Do not set both keys.

To allow Ansible SSH access after install, add `root-ssh-keys` directly in the
host answer file (no service-side injection).

## Configure disks

`disk-list = ["sda"]` is not safe for production installs. On an already-running
Proxmox node:

```bash
lsblk -d -o NAME,SIZE,MODEL,SERIAL
# or, when available:
proxmox-auto-install-assistant device-info -t disk
```

Prefer a unique filter and remove `disk-list`:

```toml
[disk-setup]
filesystem = "ext4"
filter.ID_SERIAL = "YOUR_DISK_SERIAL*"
```

Only use `disk-list` when the device name is known and stable.

## Validate rendered answer files

Committed templates contain the password placeholder and are not ready to install.
Validate the rendered answer returned by the service, not the template on disk.

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

`mvn test` also renders every committed template. When
`proxmox-auto-install-assistant` is on `PATH`, that test runs `validate-answer`
against each rendered file when the installed tool is new enough for the schema.

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
