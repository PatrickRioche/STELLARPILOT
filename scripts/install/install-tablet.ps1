param([string]$ApkPath = "$env:LOCALAPPDATA\StellarPilot\android-build\app\outputs\apk\device\debug\app-device-debug.apk")
$ErrorActionPreference = "Stop"
function Find-Adb {
    $sdkAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) { return $sdkAdb }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "ADB introuvable. Installer Android Platform Tools."
}
$adb = Find-Adb
if (-not (Test-Path $ApkPath)) { throw "APK introuvable : $ApkPath" }
$devices = & $adb devices
$authorized = $devices | Select-String -Pattern "^\S+\s+device$"
if (-not $authorized) { Write-Host $devices; throw "Aucune tablette Android autorisee." }
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw "Echec adb install." }
Write-Host "Installation terminee. Backend device : http://10.42.0.1:8000/"
