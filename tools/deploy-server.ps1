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
$WorkRoot = Join-Path $env:TEMP "stellarpilot-server-package"
$Stage = Join-Path $WorkRoot "stage"
$TrackedTar = Join-Path $WorkRoot "tracked-server.tar"
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

    $versionPath = Join-Path $RepoRoot "VERSION"
    if (-not (Test-Path $versionPath)) {
        throw "VERSION file missing: $versionPath"
    }

    $version = (Get-Content $versionPath -Raw).Trim()
    $buildTimestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")

    Write-Host "[StellarPilot] Local branch : $branch"
    Write-Host "[StellarPilot] Commit       : $commit"
    Write-Host "[StellarPilot] Version      : $version"
    Write-Host "[StellarPilot] Build UTC    : $buildTimestamp"

    Remove-Item $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $Stage -Force | Out-Null

    # Git is used only on the PC. Package exactly HEAD so local runtime data,
    # captures and the Windows virtual environment can never leak into a kit.
    Invoke-Checked git archive --format=tar --output=$TrackedTar HEAD server
    Invoke-Checked tar -xf $TrackedTar -C $Stage

    $StagedServer = Join-Path $Stage "server"
    $ManifestPath = Join-Path $StagedServer "catalog_sources\SOURCE_MANIFEST.json"

    if (Test-Path $ManifestPath) {
        Write-Host "[StellarPilot] Fetching pinned stellar catalogue sources on PC"
        $manifest = Get-Content $ManifestPath -Raw | ConvertFrom-Json
        $sourceDir = Split-Path $ManifestPath -Parent

        foreach ($source in $manifest.sources) {
            $destination = Join-Path $sourceDir $source.file
            Write-Host "[StellarPilot]   $($source.file)"
            Invoke-WebRequest `
                -Uri $source.url `
                -OutFile $destination `
                -UseBasicParsing

            if (-not (Test-Path $destination)) {
                throw "Stellar catalogue source missing after download: $destination"
            }

            if ((Get-Item $destination).Length -le 0) {
                throw "Empty stellar catalogue source: $destination"
            }
        }
    }

    # The final update kit is self-contained. The Pi needs no Internet access
    # to build/use the catalogue at runtime.
    Invoke-Checked tar -czf $Package -C $Stage server

    if (-not (Test-Path $Package)) {
        throw "Deployment archive was not created: $Package"
    }

    $sizeMb = [math]::Round((Get-Item $Package).Length / 1MB, 2)
    Write-Host "[StellarPilot] Self-contained archive : $sizeMb MB"

    $remoteBody = @'
#!/usr/bin/env bash
set -euo pipefail

PACKAGE="__REMOTE_PACKAGE__"
STAGE="/tmp/stellarpilot-server-stage"
DEPLOY="__DEPLOY_DIR__"
VERSION="__VERSION__"
BUILD_TIMESTAMP="__BUILD_TIMESTAMP__"
GIT_SHA="__GIT_SHA__"
BRANCH="__BRANCH__"

log() { printf '[StellarPilot] %s\n' "$*"; }
fail() { printf '[StellarPilot][ERROR] %s\n' "$*" >&2; exit 1; }

log "Cleaning temporary deployment files"
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

# Persistent Raspberry Pi state is deliberately preserved.
# .venv = Linux Python environment
# data  = captures, calibrations and catalog.sqlite3
rsync -a --delete \
    --exclude '.venv/' \
    --exclude 'data/' \
    "$STAGE/server/" "$DEPLOY/"

cat > "$DEPLOY/BUILD_INFO.json" <<EOF
{
  "service": "stellarpilot-server",
  "version": "$VERSION",
  "build_timestamp": "$BUILD_TIMESTAMP",
  "git_sha": "$GIT_SHA",
  "branch": "$BRANCH",
  "dirty": false
}
EOF

cd "$DEPLOY"

if [ ! -x .venv/bin/python ]; then
    log "Creating Linux venv"
    python3 -m venv .venv
fi

log "Updating Python dependencies"
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r requirements.txt

log "Checking StellarPilot Python dependencies"
.venv/bin/python - <<'PY'
from importlib.metadata import PackageNotFoundError, distribution, version
from packaging.requirements import Requirement
from packaging.version import Version

expected_exact = {
    "fastapi": "0.116.1",
    "uvicorn": "0.35.0",
    "pydantic": "2.11.7",
    "pytest": "8.4.1",
    "httpx": "0.28.1",
}

for package, expected in expected_exact.items():
    actual = version(package)
    if actual != expected:
        raise SystemExit(f"{package}: expected {expected}, found {actual}")
    print(f"{package}={actual}")

requests_version = version("requests")
if Version(requests_version) < Version("2.32.4"):
    raise SystemExit(f"requests>=2.32.4 required, found {requests_version}")
print(f"requests={requests_version}")

try:
    indiweb = distribution("indiweb")
except PackageNotFoundError:
    print("indiweb=not-installed (not required by StellarPilot server)")
else:
    print(f"indiweb={indiweb.version}")
    for raw_requirement in indiweb.requires or []:
        requirement = Requirement(raw_requirement)
        if requirement.name.lower() == "requests" and requests_version not in requirement.specifier:
            raise SystemExit(
                f"indiweb requires {requirement}, requests={requests_version}"
            )

import astropy
import fastapi
import numpy
import PIL
import requests
import scipy
print("StellarPilot dependency check OK")
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
sleep 3

log "Checking service"
if ! systemctl is-active --quiet stellarpilot-server; then
    systemctl --no-pager -l --full status stellarpilot-server || true
    journalctl -u stellarpilot-server -n 100 --no-pager || true
    fail "stellarpilot-server is not active"
fi

log "Checking /health"
curl --fail --silent --show-error http://127.0.0.1:8000/health
printf '\n'

log "Checking /build"
curl --fail --silent --show-error http://127.0.0.1:8000/build
printf '\n'

log "Checking API version and mount routes"
curl --fail --silent --show-error http://127.0.0.1:8000/openapi.json | python3 -c '
import json, sys
api = json.load(sys.stdin)
version = api.get("info", {}).get("version")
paths = api.get("paths", {})
required = ["/mount/goto-mount-frame", "/mount/sync", "/mount/status"]
missing = [path for path in required if path not in paths]
expected = "__VERSION__"
print("version=", version)
if version != expected:
    print("expected_version=", expected)
    raise SystemExit(2)
if missing:
    print("missing_routes=", ",".join(missing))
    raise SystemExit(3)
print("routes_v06=OK")
'

log "Checking offline stellar catalogue"
curl --fail --silent --show-error http://127.0.0.1:8000/catalog/status | python3 -c '
import json, sys
status = json.load(sys.stdin)
star_count = int(status.get("types", {}).get("star", 0))
print("stellar_count=", star_count)
if star_count < 4000:
    raise SystemExit("stellar catalogue did not load")
'

curl --fail --silent --show-error \
    'http://127.0.0.1:8000/catalog/search?q=Vega&object_type=star' | python3 -c '
import json, sys
result = json.load(sys.stdin)
names = [obj.get("name") for obj in result.get("objects", [])]
print("Vega search=", names[:3])
if not names:
    raise SystemExit("Vega missing from catalogue")
'

log "Cleaning staging"
rm -rf "$STAGE"
rm -f "$PACKAGE" "__REMOTE_SCRIPT__"

log "Deployment complete"
'@

    $remoteBody = $remoteBody.Replace("__REMOTE_PACKAGE__", $RemotePackage)
    $remoteBody = $remoteBody.Replace("__REMOTE_SCRIPT__", $RemoteScript)
    $remoteBody = $remoteBody.Replace("__DEPLOY_DIR__", $DeployDir)
    $remoteBody = $remoteBody.Replace("__VERSION__", $version)
    $remoteBody = $remoteBody.Replace("__BUILD_TIMESTAMP__", $buildTimestamp)
    $remoteBody = $remoteBody.Replace("__GIT_SHA__", $commit)
    $remoteBody = $remoteBody.Replace("__BRANCH__", $branch)
    $remoteBody = $remoteBody -replace "`r`n", "`n"

    [System.IO.File]::WriteAllText(
        $RemoteScriptLocal,
        $remoteBody,
        [System.Text.UTF8Encoding]::new($false)
    )

    Write-Host "[StellarPilot] Uploading self-contained package"
    Invoke-Checked scp $Package $RemoteScriptLocal "${PiHost}:/tmp/"

    Write-Host "[StellarPilot] Running remote deployment"
    & ssh -t $PiHost "bash $RemoteScript"
    if ($LASTEXITCODE -ne 0) {
        throw "Remote deployment failed with exit code $LASTEXITCODE"
    }

    Write-Host "[StellarPilot] Server deployed successfully." -ForegroundColor Green
}
finally {
    Pop-Location
    Remove-Item $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $Package -Force -ErrorAction SilentlyContinue
    Remove-Item $RemoteScriptLocal -Force -ErrorAction SilentlyContinue
}
