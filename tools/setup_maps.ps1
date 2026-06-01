# Add Google Maps SDK key to local.properties and rebuild.
# Usage:
#   setup_maps.cmd                    — show SHA-1 + open Cloud Console
#   setup_maps.cmd AIzaSy...          — save key, install app

param(
    [Parameter(Position = 0)]
    [string]$ApiKey
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Props = Join-Path $Root "local.properties"
$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$Keytool = Join-Path $JavaHome "bin\keytool.exe"

Write-Host ""
Write-Host "=== MeshHood Maps setup ===" -ForegroundColor Cyan
Write-Host ""

if (Test-Path $Keytool) {
    $sha1 = & $Keytool -list -v `
        -keystore "$env:USERPROFILE\.android\debug.keystore" `
        -alias androiddebugkey -storepass android -keypass android 2>$null |
        Select-String "SHA1:" | ForEach-Object { $_.Line.Trim() }
    Write-Host "Debug SHA-1 (for API key restriction):" -ForegroundColor Yellow
    Write-Host "  $sha1"
    Write-Host ""
    Write-Host "Package name: com.meshhood" -ForegroundColor Yellow
    Write-Host ""
}

if (-not $ApiKey) {
    Write-Host "Steps:" -ForegroundColor Cyan
    Write-Host "  1. Enable Maps SDK for Android (browser opening...)"
    Write-Host "  2. Credentials -> Create API key -> Restrict to Android app"
    Write-Host "     Package: com.meshhood  +  SHA-1 above"
    Write-Host "  3. Run:  tools\setup_maps.cmd YOUR_API_KEY"
    Write-Host ""
    Start-Process "https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com"
    Start-Sleep -Milliseconds 800
    Start-Process "https://console.cloud.google.com/apis/credentials"
    exit 0
}

$ApiKey = $ApiKey.Trim()
if ($ApiKey.Length -lt 20) {
    Write-Host "That doesn't look like a valid API key." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $Props)) {
    @"
sdk.dir=C\:\\Users\\Owner\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=$ApiKey
"@ | Set-Content -Path $Props -Encoding UTF8
} else {
    $lines = Get-Content $Props
    $found = $false
    $out = foreach ($line in $lines) {
        if ($line -match '^\s*MAPS_API_KEY\s*=') {
            $found = $true
            "MAPS_API_KEY=$ApiKey"
        } else {
            $line
        }
    }
    if (-not $found) {
        $out = $out + "MAPS_API_KEY=$ApiKey"
    }
    $out | Set-Content -Path $Props -Encoding UTF8
}

Write-Host "Saved MAPS_API_KEY to local.properties" -ForegroundColor Green
Write-Host "Building and installing..." -ForegroundColor Cyan
Set-Location $Root
& "$Root\install.cmd"
exit $LASTEXITCODE
