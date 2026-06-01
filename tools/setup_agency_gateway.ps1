# Configure dev agency signing key in local.properties for the gateway APK.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$props = Join-Path $root "local.properties"
$devKey = "/qYKxWEA2+QNx0m+s+lsQODpEJ8+Vd0XUmPYIcFxsTs="

Write-Host "MeshHood agency gateway setup" -ForegroundColor Cyan
Write-Host "Project: $root"

$lines = @()
if (Test-Path $props) {
    $lines = Get-Content $props | Where-Object { $_ -notmatch '^\s*AGENCY_SIGNING_KEY\s*=' }
} else {
    $sdk = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $sdk) {
        $lines += "sdk.dir=$($sdk -replace '\\','/')"
    }
}

$lines += "AGENCY_SIGNING_KEY=$devKey"
$lines | Set-Content -Encoding UTF8 $props

Write-Host ""
Write-Host "Saved AGENCY_SIGNING_KEY to local.properties (dev demo-county-em key)." -ForegroundColor Green
Write-Host "Matches tools\sign_agency.py and assets\agency_trust.json." -ForegroundColor Green
Write-Host ""
Write-Host "Next: .\install_gateway.cmd" -ForegroundColor Cyan
