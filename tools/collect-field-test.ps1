param(
    [string]$PackageName = "fr.stellarpilot.app",
    [string]$ServerBaseUrl = "http://10.42.0.1:8000",
    [string]$PiHost = "",
    [string]$ObservationDate = (Get-Date -Format "yyyy-MM-dd")
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputRoot = Join-Path $PSScriptRoot "..\artifacts\field-tests\$stamp"
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

Write-Host "StellarPilot field-test collector"
Write-Host "Output: $outputRoot"

function Write-TextFile {
    param(
        [string]$Path,
        [string[]]$Content
    )

    $Content | Out-File -FilePath $Path -Encoding utf8
}

# -----------------------------------------------------------------------------
# ADB state
# -----------------------------------------------------------------------------
try {
    $adbDevices = & adb devices -l 2>&1
    Write-TextFile -Path (Join-Path $outputRoot "adb-devices.txt") -Content $adbDevices
} catch {
    Write-Warning "adb indisponible: $($_.Exception.Message)"
}

# -----------------------------------------------------------------------------
# Android logs relevant to tonight's chain
# -----------------------------------------------------------------------------
try {
    $logcat = & adb logcat -d 2>&1
    $filtered = $logcat | Select-String -Pattern (
        "StellarPilot|StellarPreview|StellarBahtinov|Mount|OnStep|INDI|" +
        "astrometr|solve|tracking|GOTO|SYNC|AndroidRuntime"
    )

    $logcat | Out-File -FilePath (Join-Path $outputRoot "android-logcat-full.txt") -Encoding utf8
    $filtered | Out-File -FilePath (Join-Path $outputRoot "android-logcat-filtered.txt") -Encoding utf8
} catch {
    Write-Warning "Collecte logcat impossible: $($_.Exception.Message)"
}

# -----------------------------------------------------------------------------
# Bahtinov JSONL exported by the debug application
# -----------------------------------------------------------------------------
$remoteBahtinov = "/sdcard/Android/data/$PackageName/files/bahtinov-references"
$localBahtinov = Join-Path $outputRoot "bahtinov-references"

try {
    New-Item -ItemType Directory -Force -Path $localBahtinov | Out-Null
    & adb shell ls -la $remoteBahtinov 2>&1 |
        Out-File -FilePath (Join-Path $outputRoot "bahtinov-remote-listing.txt") -Encoding utf8

    & adb pull $remoteBahtinov $localBahtinov 2>&1 |
        Out-File -FilePath (Join-Path $outputRoot "bahtinov-adb-pull.txt") -Encoding utf8
} catch {
    Write-Warning "Journal Bahtinov non récupéré via adb: $($_.Exception.Message)"
}

# -----------------------------------------------------------------------------
# Current server diagnostics
# -----------------------------------------------------------------------------
$endpoints = @(
    "/status",
    "/mount/status",
    "/mount/time",
    "/time/synchronization"
)

foreach ($endpoint in $endpoints) {
    try {
        $safeName = $endpoint.TrimStart('/').Replace('/', '-')
        $uri = "$($ServerBaseUrl.TrimEnd('/'))$endpoint"
        $response = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
        $response |
            ConvertTo-Json -Depth 20 |
            Out-File -FilePath (Join-Path $outputRoot "$safeName.json") -Encoding utf8
    } catch {
        Write-Warning "Endpoint $endpoint indisponible: $($_.Exception.Message)"
    }
}

# -----------------------------------------------------------------------------
# Raspberry Pi astrometry archive (optional)
# -----------------------------------------------------------------------------
if (-not [string]::IsNullOrWhiteSpace($PiHost)) {
    try {
        $date = [datetime]::ParseExact(
            $ObservationDate,
            "yyyy-MM-dd",
            [System.Globalization.CultureInfo]::InvariantCulture
        )
        $year = $date.ToString("yyyy")
        $remoteAstrometry = "/home/astroberry/stellarpilot-server/data/astrometry/assistant-3/$year/$ObservationDate"
        $localAstrometry = Join-Path $outputRoot "pi-astrometry-$ObservationDate"
        New-Item -ItemType Directory -Force -Path $localAstrometry | Out-Null

        Write-Host "Copie archive Pi: ${PiHost}:$remoteAstrometry"
        & scp -r "${PiHost}:$remoteAstrometry" $localAstrometry 2>&1 |
            Out-File -FilePath (Join-Path $outputRoot "pi-scp.txt") -Encoding utf8
    } catch {
        Write-Warning "Archive Pi non récupérée: $($_.Exception.Message)"
    }
}

# -----------------------------------------------------------------------------
# Human-readable summary
# -----------------------------------------------------------------------------
$summary = @(
    "StellarPilot 0.6 field test",
    "Collected: $(Get-Date -Format o)",
    "Observation date: $ObservationDate",
    "Package: $PackageName",
    "Server: $ServerBaseUrl",
    "PiHost: $PiHost",
    "",
    "Expected validation chain:",
    "- RA motor PASS",
    "- DEC motor PASS",
    "- astrometry solved",
    "- OnStep SYNC synced",
    "- star GOTO completed",
    "- sidereal tracking confirmed",
    "- Bahtinov references recorded"
)

Write-TextFile -Path (Join-Path $outputRoot "README.txt") -Content $summary

Write-Host ""
Write-Host "Collecte terminée."
Write-Host "Dossier: $outputRoot"
