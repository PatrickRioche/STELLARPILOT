#!/usr/bin/env bash
set -euo pipefail
log(){ printf '[StellarPilot GPS] %s\n' "$*"; }
die(){ printf '[StellarPilot GPS][ERREUR] %s\n' "$*" >&2; exit 1; }
[[ "$(id -u)" -eq 0 ]] || die "Executer avec sudo."
TARGET_USER="${SUDO_USER:-astroberry}"
GPS_DEVICE="${1:-}"
if [[ -z "$GPS_DEVICE" ]]; then
  for candidate in /dev/serial/by-id/* /dev/ttyACM* /dev/ttyUSB*; do
    [[ -e "$candidate" ]] && { GPS_DEVICE="$candidate"; break; }
  done
fi
[[ -n "$GPS_DEVICE" ]] || die "Aucun GPS detecte."
[[ -e "$GPS_DEVICE" ]] || die "Peripherique inexistant: $GPS_DEVICE"
apt-get update
apt-get install -y gpsd gpsd-clients
usermod -aG dialout "$TARGET_USER"
CONFIG=/etc/default/gpsd
if [[ -f "$CONFIG" ]]; then
  BACKUP="${CONFIG}.bak.stellarpilot.$(date +%Y%m%d-%H%M%S)"
  cp -a "$CONFIG" "$BACKUP"
  log "Sauvegarde: $BACKUP"
fi
cat >"$CONFIG" <<EOF
START_DAEMON="true"
USBAUTO="true"
DEVICES="$GPS_DEVICE"
GPSD_OPTIONS="-n"
GPSD_SOCKET="/var/run/gpsd.sock"
EOF
systemctl daemon-reload
systemctl enable gpsd.socket
systemctl restart gpsd.socket
systemctl restart gpsd.service || true
sleep 3
systemctl status gpsd.socket --no-pager -l || true
systemctl status gpsd.service --no-pager -l || true
ss -ltnp | grep 2947 || true
ps -ef | grep '[g]psd' || true
fuser -v "$GPS_DEVICE" || true
timeout 8s gpspipe -w -n 10 || true
log "Configuration terminee. Verifier avec: cgps -s"
