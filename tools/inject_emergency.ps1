# Inject a mesh-only test emergency (no SMS) into a debug MeshHood build over adb.
# Usage: inject_emergency.cmd
#        inject_emergency.cmd 34.4358 -119.8276

param(
    [double]$Lat = 34.43580,
    [double]$Lon = -119.82764,
    [string]$From = "TestNeighbor"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host "adb not found. Install Android SDK platform-tools." -ForegroundColor Red
    exit 1
}

$device = & $adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1
if (-not $device) {
    Write-Host "No adb device. Plug in USB or run connect-wifi.cmd first." -ForegroundColor Red
    exit 1
}

$latStr = "{0:F5}" -f $Lat
$lonStr = "{0:F5}" -f $Lon
$mapsUrl = "https://www.google.com/maps/search/?api=1&query=$latStr,$lonStr"
$text = "NEED HELP - at $latStr, $lonStr`nOpen in Maps: $mapsUrl"
$id = [guid]::NewGuid().ToString("N")

$envelope = @{
    v    = 1
    type = "broadcast"
    id   = $id
    ttl  = 8
    ts   = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    from = $From
    text = $text
} | ConvertTo-Json -Compress

$b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($envelope))

Write-Host "Injecting test emergency (mesh-only, no SMS)..." -ForegroundColor Cyan
Write-Host "  From: $From"
Write-Host "  Coords: $latStr, $lonStr"
Write-Host "  Maps: $mapsUrl"
Write-Host ""

& $adb shell am broadcast -n com.meshhood/.debug.DebugInjectReceiver -a com.meshhood.DEBUG_INJECT_ENVELOPE --es envelope_json_b64 $b64 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "Inject failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Done - check the feed on your phone (red Emergency card)." -ForegroundColor Green
Write-Host "Tap 'Open in Google Maps' or the maps link in the message body." -ForegroundColor Green
