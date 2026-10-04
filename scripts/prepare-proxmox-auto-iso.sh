#!/usr/bin/env bash

# Prepare a PVE installer with a longer DHCP discovery window and an HTTP answer URL.
# The source ISO is read-only. A new output image is published only after verification.
# Run as a normal user; sudo preserves ownership and attributes in the installer filesystem.
set -euo pipefail

readonly DEFAULT_ANSWER_URL='http://proxmox-auto-install-assistant.lan.homelab:8080/answer'
readonly DEFAULT_DHCP_TIMEOUT=120
readonly INSTALLER_IMAGE_PATH='/pve-installer.squashfs'

work_dir=''

usage() {
    printf '%s\n' \
        'Usage: prepare-proxmox-auto-iso.sh SOURCE_ISO OUTPUT_ISO [ANSWER_URL] [DHCP_TIMEOUT]' \
        '' \
        "Default URL: $DEFAULT_ANSWER_URL" \
        "Default DHCP timeout: $DEFAULT_DHCP_TIMEOUT seconds" \
        '' \
        'Ubuntu prerequisites: xorriso, squashfs-tools, proxmox-auto-install-assistant' \
        'Run as your normal user; sudo is required for filesystem metadata preservation.' \
        '' \
        'Example:' \
        '  bash scripts/prepare-proxmox-auto-iso.sh original.iso auto-dhcp120.iso'
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

check_dependencies() {
    local dependency
    local dependencies=(
        xorriso unsquashfs mksquashfs proxmox-auto-install-assistant
        sudo realpath dirname awk sed grep mktemp cmp ln
    )

    for dependency in "${dependencies[@]}"; do
        command -v "$dependency" >/dev/null || fail "Missing command: $dependency"
    done
}

validate_settings() {
    [[ -f "$source_iso" ]] || fail 'Source ISO must be a regular file.'
    [[ ! -e "$output_iso" && ! -L "$output_iso" ]] || fail 'Output already exists; choose a new filename.'
    [[ "$answer_url" =~ ^https?://[^[:space:]]+$ ]] || fail 'Answer URL must use HTTP or HTTPS without whitespace.'
    [[ "$dhcp_timeout" =~ ^[1-9][0-9]*$ ]] || fail 'DHCP timeout must be a positive integer.'

    output_dir=$(dirname -- "$output_iso")
    [[ -d "$output_dir" && -w "$output_dir" ]] || fail 'Output directory must exist and be writable.'
}

# GRUB identifies this ISO using its original volume timestamp UUID.
# Detect it rather than hardcoding the UUID from one particular ISO release.
read_boot_uuid() {
    awk '
        $1 == "-volume_date" && $2 == "uuid" {
            gsub(/\047/, "", $3)
            print $3
        }
    '
}

cleanup() {
    # Extraction creates root-owned files, so cleanup needs the same privileges.
    if [[ -n "$work_dir" && -d "$work_dir" ]]; then
        sudo rm -rf -- "$work_dir" || printf 'Temporary files remain at %s\n' "$work_dir" >&2
    fi
}

extract_installer() {
    printf '\n[1/5] Extracting installer filesystem\n'

    xorriso -osirrox on -indev "$source_iso" \
        -extract "$INSTALLER_IMAGE_PATH" "$work_dir/original.squashfs"

    sudo unsquashfs -d "$work_dir/installer" "$work_dir/original.squashfs"
}

update_dhcp_timeout() {
    local dhcp_config="$work_dir/installer/etc/dhcp/dhclient.conf"
    local timeout_count

    printf '\n[2/5] Setting DHCP discovery timeout to %s seconds\n' "$dhcp_timeout"

    # Fail if the configuration layout changes instead of silently making no change.
    sudo test -f "$dhcp_config" || fail 'Installer DHCP configuration was not found.'
    timeout_count=$(sudo awk '
        /^[[:space:]]*timeout[[:space:]]+[0-9]+[[:space:]]*;[[:space:]]*$/ { count++ }
        END { print count+0 }
    ' "$dhcp_config")
    [[ "$timeout_count" == 1 ]] || fail 'Expected exactly one simple DHCP timeout directive.'

    sudo sed -i -E \
        "s/^[[:space:]]*timeout[[:space:]]+[0-9]+[[:space:]]*;[[:space:]]*$/timeout $dhcp_timeout;/" \
        "$dhcp_config"
}

rebuild_iso() {
    printf '\n[3/5] Rebuilding installer filesystem and ISO\n'

    # These compression settings match the inspected PVE 9.2-1 installer image.
    sudo mksquashfs "$work_dir/installer" "$work_dir/pve-installer.squashfs" \
        -noappend \
        -comp zstd \
        -Xcompression-level 19 \
        -b 1048576

    # Rebuild BIOS/UEFI boot metadata for the new file locations. The original
    # HFS+ hybrid layout causes overlapping GPT entries, so omit Apple HFS+ support.
    sudo xorriso -abort_on FAILURE -return_with FAILURE 32 \
        -indev "$source_iso" \
        -outdev "$work_dir/dhcp.iso" \
        -map "$work_dir/pve-installer.squashfs" "$INSTALLER_IMAGE_PATH" \
        -boot_image any replay \
        -hfsplus off \
        -volume_date uuid "$iso_uuid"
}

configure_auto_install() {
    printf '\n[4/5] Configuring HTTP answer source\n'

    # Keep the assistant's sidecar and staging files out of the caller's directory.
    pushd "$work_dir" >/dev/null

    proxmox-auto-install-assistant prepare-iso "$work_dir/dhcp.iso" \
        --fetch-from http \
        --url "$answer_url" \
        --output "$work_dir/auto.iso" \
        --tmp "$work_dir"

    popd >/dev/null
}

verify_iso() {
    local verified_config final_report final_uuid

    printf '\n[5/5] Verifying final ISO contents and boot entries\n'

    # Read back the actual ISO, not just the files used to create it.
    xorriso -osirrox on -indev "$work_dir/auto.iso" \
        -extract "$INSTALLER_IMAGE_PATH" "$work_dir/verified.squashfs" \
        -extract /auto-installer-mode.toml "$work_dir/verified-mode.toml"

    cmp "$work_dir/pve-installer.squashfs" "$work_dir/verified.squashfs"
    cmp "$work_dir/auto-installer-mode.toml" "$work_dir/verified-mode.toml"

    verified_config=$(unsquashfs -cat "$work_dir/verified.squashfs" etc/dhcp/dhclient.conf)
    grep -Fxq "timeout $dhcp_timeout;" <<< "$verified_config" \
        || fail 'Final DHCP timeout verification failed.'

    final_report=$(xorriso -indev "$work_dir/auto.iso" -report_el_torito cmd)
    final_uuid=$(read_boot_uuid <<< "$final_report")

    [[ "$final_uuid" == "$iso_uuid" ]] || fail 'Final ISO boot UUID changed.'
    grep -Fq 'platform_id=0x00' <<< "$final_report" || fail 'Final ISO has no BIOS boot entry.'
    grep -Fq 'platform_id=0xef' <<< "$final_report" || fail 'Final ISO has no UEFI boot entry.'
}

main() {
    local boot_report

    if [[ ${1:-} == --help || ${1:-} == -h ]]; then
        usage
        return 0
    fi

    if (( $# < 2 || $# > 4 )); then
        usage >&2
        return 1
    fi

    check_dependencies

    # Build settings are shared by the named steps below; scratch values stay local.
    source_iso=$(realpath -e -- "$1")
    output_iso=$(realpath -m -- "$2")
    answer_url=${3:-$DEFAULT_ANSWER_URL}
    dhcp_timeout=${4:-$DEFAULT_DHCP_TIMEOUT}

    validate_settings

    boot_report=$(xorriso -indev "$source_iso" -report_el_torito cmd)
    iso_uuid=$(read_boot_uuid <<< "$boot_report")
    [[ "$iso_uuid" =~ ^[0-9]{16}$ ]] || fail 'Could not determine a unique original ISO boot UUID.'

    sudo -v

    # Use the output filesystem so the verified image can be published with a hard link.
    work_dir=$(mktemp -d "$output_dir/.proxmox-iso-build.XXXXXX")
    trap cleanup EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM

    extract_installer
    update_dhcp_timeout
    rebuild_iso
    configure_auto_install
    verify_iso

    # Hard linking publishes the finished image without overwriting an existing path.
    ln -- "$work_dir/auto.iso" "$output_iso"

    printf '\nCreated: %s\nAnswer URL: %s\nDHCP timeout: %s seconds\n' \
        "$output_iso" "$answer_url" "$dhcp_timeout"
}

main "$@"