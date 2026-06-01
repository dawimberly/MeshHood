# Sign an agency alert and inject it into a debug MeshHood build over adb.
# Usage: inject_agency.cmd "Shelter open at City Hall until 8pm"

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Message
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

$json = & python "$PSScriptRoot\sign_agency.py" $Message
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$json = $json.Trim()

$b64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($json))

Write-Host "Injecting agency alert..." -ForegroundColor Cyan
& $adb shell am broadcast -a com.meshhood.DEBUG_INJECT_ENVELOPE --es envelope_json_b64 $b64 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "Inject failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Done - check the feed on your phone." -ForegroundColor Green
