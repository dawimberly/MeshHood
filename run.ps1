# Run MeshHood: install + open on phone, or show next steps if no device.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host ""
Write-Host "=== RUN MESH ===" -ForegroundColor Cyan
Write-Host ""

& "$PSScriptRoot\install.ps1"
exit $LASTEXITCODE
