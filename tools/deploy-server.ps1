param(
    [string]$PiHost = "astroberry@10.42.0.1",
    [string]$DeployDir = "/home/astroberry/stellarpilot-server"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath a échoué avec le code $LASTEXITCODE"
    }
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Package = Join-Path $env:TEMP "stellarpilot-server-deploy.tar.gz"
$RemoteScriptLocal = Join-Path $env:TEMP "stellarpilot-remote-deploy.sh"
$RemotePackage = "/tmp/stellarpilot-server-deploy.tar.gz"
$RemoteScript = "/tmp/stellarpilot-remote-deploy.sh"

Push-Location $RepoRoot
try {
    Write-Host "[StellarPilot] Déploiement serveur depuis le PC" -ForegroundColor Cyan
    Write-Host "[StellarPilot] Repo : $RepoRoot"
    Write-Host "[StellarPilot] Pi   : $PiHost"

    $branch = (git branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire la branche Git locale."
    }

    $commit = (git rev-parse --short HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire le commit Git local."
    }

    Write-Host "[StellarPilot] Branche locale : $branch"
    Write-Host "[StellarPilot] Commit         : $commit"

    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue

    # Git n'est utilisé que sur le PC. git archive n'inclut que les fichiers
    # suivis : jamais le .venv Windows, les caches, les captures ou les données
    # runtime non suivies.
    Invoke-Checked git archive --format=tar.gz --output=$Package HEAD server

    if (-not (Test-Path $Package)) {
        throw "Archive de déploiement non créée : $Package"
    }

    $sizeMb = [math]::Round((Get-Item $Package).Length / 1MB, 2)
    Write-Host "[StellarPilot] Archive propre : $sizeMb MB"

    $remoteBody = @'
#!/usr/bin/env bash
set -euo pipefail

PACKAGE="__REMOTE_PACKAGE__"
STAGE="/tmp/stellarpilot-server-stage"
DEPLOY="__DEPLOY_DIR__"

log() { printf '[StellarPilot] %s\n' "$*"; }
fail() { printf '[StellarPilot][ERREUR] %s\n' "$*" >&2; exit 1; }

log "Nettoyage des anciens fichiers temporaires"
rm -rf /tmp/stellarpilot-v060 /tmp/stellarpilot-v060.tar.gz
rm -rf "$STAGE"
mkdir -p "$STAGE"

test -f "$PACKAGE" || fail "Archive distante absente: $PACKAGE"

tar -xzf "$PACKAGE" -C "$STAGE"

test -f "$STAGE/server/requirements.txt" || fail "requirements.txt absent de l'archive"
test -f "$STAGE/server/systemd/stellarpilot-server.service" || fail "service systemd absent de l'archive"

command -v python3 >/dev/null || fail "python3 absent"
command -v rsync >/dev/null || fail "rsync absent"
command -v indi_getprop >/dev/null || fail "indi_getprop absent"
command -v solve-field >/dev/null || fail "astrometry.net / solve-field absent"

log "Deploiement vers $DEPLOY"
mkdir -p "$DEPLOY"

# .venv = environnement Python Linux persistant.
# data  = captures scientifiques et calibrations persistantes.
# Aucun des deux ne doit etre supprime lors d'une mise a jour.
rsync -a --delete \
    --exclude '.venv/' \
    --exclude 'data/' \
    "$STAGE/server/" "$DEPLOY/"

cd "$DEPLOY"

if [ ! -x .venv/bin/python ]; then
    log "Creation du venv Linux"
    python3 -m venv .venv
fi

log "Mise a jour des dependances Python"
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt

log "Verification des imports Python"
.venv/bin/python - <<'PY'
import fastapi
import numpy
import astropy
import PIL
import scipy
print("Imports Python OK")
PY

log "Mise a jour du service systemd"
sudo cp systemd/stellarpilot-server.service /etc/systemd/system/stellarpilot-server.service
sudo mkdir -p /etc/systemd/system/stellarpilot-server.service.d
sudo rm -f /etc/systemd/system/stellarpilot-server.service.d/device.conf
sudo tee /etc/systemd/system/stellarpilot-server.service.d/runtime.conf >/dev/null <<'EOF'
[Service]
Environment=PYTHONUNBUFFERED=1
EOF

sudo systemctl daemon-reload
sudo systemctl enable stellarpilot-server >/dev/null
sudo systemctl restart stellarpilot-server
sleep 2

log "Verification du service"
if ! systemctl is-active --quiet stellarpilot-server; then
    systemctl --no-pager -l --full status stellarpilot-server || true
    journalctl -u stellarpilot-server -n 80 --no-pager || true
    fail "stellarpilot-server n'est pas actif"
fi

log "Verification /health"
curl --fail --silent --show-error http://127.0.0.1:8000/health
printf '\n'

log "Verification API V0.6"
curl --fail --silent --show-error http://127.0.0.1:8000/openapi.json | python3 -c '
import json, sys
api = json.load(sys.stdin)
version = api.get("info", {}).get("version")
print("version=", version)
paths = api.get("paths", {})
required = ["/mount/goto-mount-frame", "/mount/sync", "/mount/status"]
missing = [path for path in required if path not in paths]
if version != "0.6.0-poc":
    print("version_attendue=0.6.0-poc")
    raise SystemExit(2)
if missing:
    print("routes_manquantes=", ",".join(missing))
    raise SystemExit(3)
print("routes_v06=OK")
'

log "Nettoyage du staging"
rm -rf "$STAGE"
rm -f "$PACKAGE" "__REMOTE_SCRIPT__"

log "Deploiement termine"
'@

    $remoteBody = $remoteBody.Replace("__REMOTE_PACKAGE__", $RemotePackage)
    $remoteBody = $remoteBody.Replace("__REMOTE_SCRIPT__", $RemoteScript)
    $remoteBody = $remoteBody.Replace("__DEPLOY_DIR__", $DeployDir)
    $remoteBody = $remoteBody -replace "`r`n", "`n"

    # UTF-8 sans BOM pour Bash.
    [System.IO.File]::WriteAllText(
        $RemoteScriptLocal,
        $remoteBody,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "[StellarPilot] Envoi des fichiers vers le Pi"
    Invoke-Checked scp $Package "${PiHost}:$RemotePackage"
    Invoke-Checked scp $RemoteScriptLocal "${PiHost}:$RemoteScript"

    Write-Host "[StellarPilot] Exécution distante"
    & ssh -t $PiHost "bash $RemoteScript"
    if ($LASTEXITCODE -ne 0) {
        throw "Le déploiement distant a échoué avec le code $LASTEXITCODE"
    }

    Write-Host "[StellarPilot] Serveur V0.6 déployé avec succès." -ForegroundColor Green
}
finally {
    Pop-Location
    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue
}
