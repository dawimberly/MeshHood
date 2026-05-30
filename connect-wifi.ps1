# Pair and connect your phone over Wi-Fi for adb install (no USB cable).
# Use connect-wifi.cmd (not .ps1 directly) if PowerShell blocks scripts.
#
# Usage:
#   .\connect-wifi.cmd
#   .\connect-wifi.cmd -Pair 192.168.1.50:37123 -Code 123456 -Connect 192.168.1.50:42817

param(
    [string]$Pair = "",
    [string]$Code = "",
    [string]$Connect = ""
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host "adb not found. Install Android SDK platform-tools." -ForegroundColor Red
    exit 1
}

function Test-Address {
    param([string]$Addr)
    return $Addr -match '^\d{1,3}(\.\d{1,3}){3}:\d+$'
}

Write-Host ""
Write-Host "=== MeshHood Wi-Fi adb ===" -ForegroundColor Cyan
Write-Host "Phone and PC must be on the same Wi-Fi network."
Write-Host "Use REAL numbers from your phone screen - not 192.168.x.x placeholders."
Write-Host ""

if ($Pair -and $Code) {
    if (-not (Test-Address $Pair)) {
        Write-Host "Bad pairing address: $Pair" -ForegroundColor Red
        Write-Host "Example: 192.168.1.50:37123"
        exit 1
    }
    Write-Host "Pairing with $Pair ..."
    & $adb pair $Pair $Code
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Pair OK." -ForegroundColor Green
}

if (-not $Connect) {
    Write-Host "STEP 1 - PAIR (first time only)"
    Write-Host "  Phone: Wireless debugging -> Pair device with pairing code"
    Write-Host "  Enter IP:port and 6-digit code shown on phone."
    Write-Host "  Press Enter alone to skip if already paired."
    Write-Host ""
    if (-not $Pair) {
        $Pair = Read-Host 'Pairing address - example 192.168.1.50:37123'
    }
    if ($Pair -and -not $Code) {
        $Code = Read-Host 'Pairing code - 6 digits from phone'
    }
    if ($Pair -and $Code) {
        if (-not (Test-Address $Pair)) {
            Write-Host "Bad pairing address: $Pair" -ForegroundColor Red
            exit 1
        }
        Write-Host "Pairing ..."
        & $adb pair $Pair $Code
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "Pair OK." -ForegroundColor Green
    }
    Write-Host ""
    Write-Host "STEP 2 - CONNECT"
    Write-Host "  Phone: Wireless debugging main screen -> IP address and port"
    Write-Host "  Type that here - NOT run.cmd, NOT a command."
    Write-Host ""
    do {
        $Connect = Read-Host 'Connect address - example 192.168.1.50:42817'
        $Connect = $Connect.Trim()
        if ($Connect -match 'run\.cmd|install|\.\\') {
            Write-Host "That looks like a command. Enter phone IP:port from the phone screen." -ForegroundColor Yellow
            $Connect = ""
        }
    } while ($Connect -and -not (Test-Address $Connect))
}

if (-not $Connect) {
    Write-Host "No connect address entered." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Address $Connect)) {
    Write-Host "Bad connect address: $Connect" -ForegroundColor Red
    Write-Host "Must look like 192.168.1.50:42817"
    exit 1
}

Write-Host "Connecting to $Connect ..."
& $adb connect $Connect
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
& $adb devices -l
$ok = & $adb devices | Select-String "device$"
if ($ok) {
    Write-Host ""
    Write-Host "Connected over Wi-Fi. Now run:" -ForegroundColor Green
    Write-Host "  .\run.cmd"
} else {
    Write-Host ""
    Write-Host "No device yet. Same Wi-Fi? Wireless debugging ON? Correct IP:port?" -ForegroundColor Yellow
    exit 1
}
