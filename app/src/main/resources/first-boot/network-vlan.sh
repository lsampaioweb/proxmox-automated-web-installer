#!/bin/bash
# Proxmox first-boot hook: moves the management network of an installer-created
# bridge to a tagged VLAN that gets its address from DHCP. Ansible does the rest.
set -euo pipefail

bridge='__BRIDGE__'
vlan_id='__VLAN_ID__'
vlan_interface="${bridge}.${vlan_id}"

# Tests override these two values; Proxmox uses the defaults.
interfaces_file="${PVE_INTERFACES_FILE:-/etc/network/interfaces}"
validator="${PVE_IFUP:-ifup}"

if grep -qx "iface ${vlan_interface} inet dhcp" "$interfaces_file"; then
  echo "${vlan_interface} is already configured. Nothing to do."
  exit 0
fi

if ! grep -q "^iface ${bridge} inet " "$interfaces_file"; then
  echo "Bridge ${bridge} not found in ${interfaces_file}." >&2
  exit 1
fi

# Write the new file next to the original, keeping its owner and permissions.
new_file=$(mktemp "${interfaces_file}.vlan.XXXXXX")
trap 'rm -f "$new_file"' EXIT
cp -p "$interfaces_file" "$new_file"

# Copy every line, except inside the bridge stanza:
#   - "iface vmbr0 inet static" becomes "iface vmbr0 inet manual";
#   - the old IP settings are removed (the VLAN gets its IP from DHCP);
#   - when the stanza ends, VLAN support and the new VLAN stanza are added.
awk -v bridge="$bridge" -v vlan_id="$vlan_id" -v vlan_interface="$vlan_interface" '
  function add_vlan() {
    print "        bridge-vlan-aware yes"
    print "        bridge-vids " vlan_id
    print ""
    print "auto " vlan_interface
    print "iface " vlan_interface " inet dhcp"
    in_bridge = 0
  }

  in_bridge && !/^[[:space:]]+[^[:space:]]/ { add_vlan() }

  $1 == "iface" && $2 == bridge { print "iface " bridge " inet manual"; in_bridge = 1; next }

  in_bridge && $1 ~ /^(address|netmask|gateway|bridge-vlan-aware|bridge-vids)$/ { next }

  { print }

  END { if (in_bridge) add_vlan() }
' "$interfaces_file" > "$new_file"

# Check the syntax only. This does not touch the running network.
"$validator" --syntax-check -a --interfaces "$new_file"

cp -p "$interfaces_file" "${interfaces_file}.before-first-boot-vlan"
mv -f "$new_file" "$interfaces_file"
echo "Configured ${vlan_interface} with DHCP. It takes effect when networking starts."
