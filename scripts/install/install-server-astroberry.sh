#!/usr/bin/env bash
set -euo pipefail
log(){ printf '[StellarPilot] %s\n' "$*"; }
die(){ printf '[StellarPilot][ERREUR] %s\n' "$*" >&2; exit 1; }
[[ "$(id -u)" -ne 0 ]] || die "Executer avec l'utilisateur astroberry, pas sudo."
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
SOURCE_SERVER="$REPO_ROOT/server"
DEPLOY_DIR="${STELLARPILOT_DEPLOY_DIR:-$HOME/stellarpilot-server}"
[[ -d "$SOURCE_SERVER/app" ]] || die "Source serveur introuvable: $SOURCE_SERVER"
command -v python3 >/dev/null || die "python3 introuvable"
command -v rsync >/dev/null || { sudo apt-get update; sudo apt-get install -y rsync; }
if ! command -v indi_getprop >/dev/null; then
  log "Installation de indi-bin..."
  sudo apt-get update
  sudo apt-get install -y indi-bin || die "Installer INDI/Astroberry avant de continuer."
fi
if ! command -v solve-field >/dev/null || ! command -v wcsinfo >/dev/null; then
  log "Installation d'astrometry.net..."
  sudo apt-get update
  sudo apt-get install -y astrometry.net || die "Installer astrometry.net."
fi
if ! find /usr/share/astrometry -type f -name 'index-*.fits' -print -quit 2>/dev/null | grep -q .; then
  log "ATTENTION: aucun index astrometry.net detecte dans /usr/share/astrometry."
fi
mkdir -p "$DEPLOY_DIR"
rsync -a --delete --exclude '.venv' --exclude '__pycache__' "$SOURCE_SERVER/" "$DEPLOY_DIR/"
cd "$DEPLOY_DIR"
[[ -x .venv/bin/python ]] || python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt
.venv/bin/pip install numpy astropy Pillow
.venv/bin/python - <<'PY2'
import fastapi, numpy, astropy, PIL
print('Imports Python OK')
PY2
sudo cp "$DEPLOY_DIR/systemd/stellarpilot-server.service" /etc/systemd/system/stellarpilot-server.service
sudo mkdir -p /etc/systemd/system/stellarpilot-server.service.d
sudo tee /etc/systemd/system/stellarpilot-server.service.d/device.conf >/dev/null <<'EOF'
[Service]
Environment=STELLARPILOT_MODE=device
Environment=PYTHONUNBUFFERED=1
EOF
sudo systemctl daemon-reload
sudo systemctl enable stellarpilot-server
sudo systemctl restart stellarpilot-server
sleep 2
systemctl --no-pager -l status stellarpilot-server || true
curl --fail --silent --show-error http://127.0.0.1:8000/health
printf '\n'
log "Installation serveur terminee."
