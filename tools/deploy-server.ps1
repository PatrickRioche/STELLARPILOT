param(
    [string]$PiHost = "astroberry@192.168.1.46",
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
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Package = Join-Path $env:TEMP "stellarpilot-server-deploy.tar.gz"
$RemoteScriptLocal = Join-Path $env:TEMP "stellarpilot-remote-deploy.sh"
$RemotePackage = "/tmp/stellarpilot-server-deploy.tar.gz"
$RemoteScript = "/tmp/stellarpilot-remote-deploy.sh"

Push-Location $RepoRoot
try {
    Write-Host "[StellarPilot] Server deployment from PC" -ForegroundColor Cyan
    Write-Host "[StellarPilot] Repo : $RepoRoot"
    Write-Host "[StellarPilot] Pi   : $PiHost"

    $branch = (git branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read local Git branch."
    }

    $commit = (git rev-parse --short HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read local Git commit."
    }

    Write-Host "[StellarPilot] Local branch : $branch"
    Write-Host "[StellarPilot] Commit       : $commit"

    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue

    # Git is used only on the PC. git archive includes tracked files only,
    # so Windows .venv, caches, captures and runtime data are never deployed.
    Invoke-Checked git archive --format=tar.gz --output=$Package HEAD server

    if (-not (Test-Path $Package)) {
        throw "Deployment archive was not created: $Package"
    }

    $sizeMb = [math]::Round((Get-Item $Package).Length / 1MB, 2)
    Write-Host "[StellarPilot] Clean archive : $sizeMb MB"

    $remoteBody = @'
#!/usr/bin/env bash
set -euo pipefail

PACKAGE="__REMOTE_PACKAGE__"
STAGE="/tmp/stellarpilot-server-stage"
DEPLOY="__DEPLOY_DIR__"

log() { printf '[StellarPilot] %s\n' "$*"; }
fail() { printf '[StellarPilot][ERROR] %s\n' "$*" >&2; exit 1; }

log "Cleaning legacy temporary files"
# Some legacy deployment folders were created as root. sudo is required once
# to remove them safely before creating a fresh user-owned staging directory.
sudo rm -rf \
    /tmp/stellarpilot-v060 \
    /tmp/stellarpilot-v060.tar.gz \
    "$STAGE"
mkdir -p "$STAGE"

test -f "$PACKAGE" || fail "Remote archive missing: $PACKAGE"

tar -xzf "$PACKAGE" -C "$STAGE"

test -f "$STAGE/server/requirements.txt" || fail "requirements.txt missing from archive"
test -f "$STAGE/server/systemd/stellarpilot-server.service" || fail "systemd service missing from archive"

command -v python3 >/dev/null || fail "python3 missing"
command -v rsync >/dev/null || fail "rsync missing"
command -v indi_getprop >/dev/null || fail "indi_getprop missing"
command -v solve-field >/dev/null || fail "astrometry.net / solve-field missing"

log "Deploying to $DEPLOY"
mkdir -p "$DEPLOY"

# .venv = persistent Linux Python environment.
# data  = persistent scientific captures and calibration references.
# Neither may be deleted during a normal update.
rsync -a --delete \
    --exclude '.venv/' \
    --exclude 'data/' \
    "$STAGE/server/" "$DEPLOY/"

cd "$DEPLOY"

if [ ! -x .venv/bin/python ]; then
    log "Creating Linux venv"
    python3 -m venv .venv
fi

log "Updating Python dependencies"
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt

log "Checking Python dependency consistency"
.venv/bin/pip check

log "Checking Python imports"
.venv/bin/python - <<'PY'
import fastapi
import numpy
import astropy
import PIL
import scipy
import requests
print("Python imports OK")
print("requests=", requests.__version__)
PY

log "Updating systemd service"
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

log "Checking service"
if ! systemctl is-active --quiet stellarpilot-server; then
    systemctl --no-pager -l --full status stellarpilot-server || true
    journalctl -u stellarpilot-server -n 80 --no-pager || true
    fail "stellarpilot-server is not active"
fi

log "Checking /health"
curl --fail --silent --show-error http://127.0.0.1:8000/health
printf '\n'

log "Checking V0.6 API"
curl --fail --silent --show-error http://127.0.0.1:8000/openapi.json | python3 -c '
import json, sys
api = json.load(sys.stdin)
version = api.get("info", {}).get("version")
print("version=", version)
paths = api.get("paths", {})
required = ["/mount/goto-mount-frame", "/mount/sync", "/mount/status"]
missing = [path for path in required if path not in paths]
if version != "0.6.0-poc":
    print("expected_version=0.6.0-poc")
    raise SystemExit(2)
if missing:
    print("missing_routes=", ",".join(missing))
    raise SystemExit(3)
print("routes_v06=OK")
'

log "Cleaning staging"
rm -rf "$STAGE"
rm -f "$PACKAGE" "__REMOTE_SCRIPT__"

log "Deployment complete"
'@

    $remoteBody = $remoteBody.Replace("__REMOTE_PACKAGE__", $RemotePackage)
    $remoteBody = $remoteBody.Replace("__REMOTE_SCRIPT__", $RemoteScript)
    $remoteBody = $remoteBody.Replace("__DEPLOY_DIR__", $DeployDir)
    $remoteBody = $remoteBody -replace "`r`n", "`n"

    # UTF-8 without BOM for Bash.
    [System.IO.File]::WriteAllText(
        $RemoteScriptLocal,
        $remoteBody,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "[StellarPilot] Uploading package and deploy script"
    Invoke-Checked scp $Package $RemoteScriptLocal "${PiHost}:/tmp/"

    Write-Host "[StellarPilot] Running remote deployment"
    & ssh -t $PiHost "bash $RemoteScript"
    if ($LASTEXITCODE -ne 0) {
        throw "Remote deployment failed with exit code $LASTEXITCODE"
    }

    Write-Host "[StellarPilot] Server V0.6 deployed successfully." -ForegroundColor Green
}
finally {
    Pop-Location
    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue
}
