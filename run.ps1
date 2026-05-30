# Run MeshHood: install + open on phone, or show next steps if no device.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$hasDevice = $false
if (Test-Path $adb) {
    $lines = & $adb devices 2>$null | Select-String "device$"
    $hasDevice = $null -ne $lines
}

Write-Host ""
Write-Host "=== RUN MESH ===" -ForegroundColor Cyan

if ($hasDevice) {
    & "$PSScriptRoot\install.ps1"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "No phone detected (USB or Wi-Fi adb)." -ForegroundColor Yellow
Write-Host ""
Write-Host "Option A - Wi-Fi (no cable):" -ForegroundColor Cyan
Write-Host "  Phone: Developer options -> Wireless debugging -> ON"
Write-Host "  PC:    .\connect-wifi.cmd"
Write-Host "  Then:  .\run.cmd"
Write-Host ""
Write-Host "Option B - USB:"
Write-Host "  Plug in phone, USB debugging on, then  .\run.cmd"
Write-Host ""
Write-Host "Option C - PC sim (phone + PC over BLE):"
Write-Host "  1. Open MeshHood on phone (Bluetooth on)"
Write-Host "  2. cd tools"
Write-Host "  3. pip install -r requirements.txt"
Write-Host "  4. python pc_two_phone_sim.py"
Write-Host ""
exit 1
