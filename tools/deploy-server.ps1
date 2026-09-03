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
$RemotePackage = "/tmp/stellarpilot-server-deploy.tar.gz"

Push-Location $RepoRoot
try {
    Write-Host "[StellarPilot] Déploiement serveur depuis le PC" -ForegroundColor Cyan
    Write-Host "[StellarPilot] Repo : $RepoRoot"
    Write-Host "[StellarPilot] Pi   : $PiHost"

    $branch = (git branch --show-current).Trim()
    $commit = (git rev-parse --short HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire l'état Git local."
    }

    Write-Host "[StellarPilot] Branche locale : $branch"
    Write-Host "[StellarPilot] Commit         : $commit"

    Remove-Item $Package -Force -ErrorAction SilentlyContinue

    # Git est utilisé uniquement sur le PC pour produire une archive propre.
    # Ainsi .venv Windows, __pycache__, data runtime et fichiers non suivis ne
    # peuvent pas contaminer le Raspberry Pi.
    Invoke-Checked git archive --format=tar.gz --output=$Package HEAD server

    if (-not (Test-Path $Package)) {
        throw "Archive de déploiement non créée : $Package"
    }

    $sizeMb = [math]::Round((Get-Item $Package).Length / 1MB, 2)
    Write-Host "[StellarPilot] Archive propre : $sizeMb MB"

    Invoke-Checked scp $Package "${PiHost}:$RemotePackage"

    $remoteScript = @'
set -euo pipefail

PACKAGE="__REMOTE_PACKAGE__"
STAGE="/tmp/stellarpilot-server-stage"
DEPLOY="__DEPLOY_DIR__"

log() { printf '[StellarPilot] %s\n' "$*"; }

log "Nettoyage du staging"
rm -rf "$STAGE"
mkdir -p "$STAGE"

tar -xzf "$PACKAGE" -C "$STAGE"

test -f "$STAGE/server/requirements.txt"
test -f "$STAGE/server/systemd/stellarpilot-server.service"

command -v python3 >/dev/null
command -v rsync >/dev/null
command -v indi_getprop >/dev/null || {
    echo "[StellarPilot][ERREUR] indi_getprop absent" >&2
    exit 20
}
command -v solve-field >/dev/null || {
    echo "[StellarPilot][ERREUR] astrometry.net / solve-field absent" >&2
    exit 21
}

log "Déploiement vers $DEPLOY"
mkdir -p "$DEPLOY"

# Ne jamais effacer l'environnement Python Linux ni les données scientifiques.
rsync -a --delete \
    --exclude '.venv/' \
    --exclude 'data/' \
    "$STAGE/server/" "$DEPLOY/"

cd "$DEPLOY"

if [ ! -x .venv/bin/python ]; then
    log "Création du venv Linux"
    python3 -m venv .venv
fi

log "Mise à jour des dépendances Python"
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt

log "Installation / mise à jour du service systemd"
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

log "Vérification du service"
systemctl --no-pager -l --full status stellarpilot-server | head -n 20 || true

log "Vérification /health"
curl --fail --silent --show-error http://127.0.0.1:8000/health
printf '\n'

log "Vérification API V0.6"
curl --fail --silent --show-error http://127.0.0.1:8000/openapi.json | python3 -c '
import json, sys
api = json.load(sys.stdin)
print("version=", api.get("info", {}).get("version"))
paths = api.get("paths", {})
required = ["/mount/goto-mount-frame", "/mount/sync", "/mount/status"]
missing = [path for path in required if path not in paths]
if missing:
    print("routes_manquantes=", ",".join(missing))
    raise SystemExit(2)
print("routes_v06=OK")
'

rm -rf "$STAGE"
rm -f "$PACKAGE"
log "Déploiement terminé"
'@

    $remoteScript = $remoteScript.Replace("__REMOTE_PACKAGE__", $RemotePackage)
    $remoteScript = $remoteScript.Replace("__DEPLOY_DIR__", $DeployDir)
    $remoteScript = $remoteScript -replace "`r`n", "`n"

    $remoteScript | & ssh $PiHost "bash -s"
    if ($LASTEXITCODE -ne 0) {
        throw "Le déploiement distant a échoué avec le code $LASTEXITCODE"
    }

    Write-Host "[StellarPilot] Serveur déployé avec succès." -ForegroundColor Green
}
finally {
    Pop-Location
    Remove-Item $Package -Force -ErrorAction SilentlyContinue
}
