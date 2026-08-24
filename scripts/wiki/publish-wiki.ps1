param([string]$Repository = "PatrickRioche/STELLARPILOT")
$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$wikiSource = Join-Path $repoRoot "docs\wiki"
if (-not (Test-Path (Join-Path $wikiSource "Home.md"))) { throw "Source Wiki introuvable." }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI gh requis." }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw "Git requis." }
gh repo edit $Repository --enable-wiki
$temp = Join-Path $env:TEMP "stellarpilot-wiki-publish"
if (Test-Path $temp) { Remove-Item $temp -Recurse -Force }
$wikiUrl = "https://github.com/$Repository.wiki.git"
git clone $wikiUrl $temp
if ($LASTEXITCODE -ne 0) { throw "Creer d'abord la page Home dans le Wiki GitHub, puis relancer." }
Get-ChildItem $temp -Force | Where-Object { $_.Name -ne ".git" } | Remove-Item -Recurse -Force
Copy-Item (Join-Path $wikiSource "*") $temp -Recurse -Force
Push-Location $temp
try {
    git add .
    git diff --cached --check
    if (-not (git status --porcelain)) { Write-Host "Aucune modification."; return }
    git commit -m "docs: publish StellarPilot wiki"
    git push origin master
} finally { Pop-Location }
Write-Host "Wiki publie : https://github.com/$Repository/wiki"
