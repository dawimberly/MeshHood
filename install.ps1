# Install app on phone (USB or Wi-Fi adb - run connect-wifi.cmd first for Wi-Fi)
# Usage: connect-wifi.cmd OR plug in USB, then: install.cmd

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Get-AdbPath {
    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $adb) { return $adb }
    return $null
}

function Initialize-Adb {
    $adb = Get-AdbPath
    if (-not $adb) {
        Write-Host "adb not found. Install Android SDK platform-tools." -ForegroundColor Red
        return $null
    }
    $env:Path = "$(Split-Path $adb);$env:Path"
    return $adb
}

function Get-AdbDeviceState {
    $lines = & adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_.Trim() }
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+(\S+)') {
            return @{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
    return $null
}

function Wait-AdbDevice {
    param([int]$Seconds = 45)

    & adb kill-server | Out-Null
    Start-Sleep -Seconds 1
    & adb start-server | Out-Null

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $device = Get-AdbDeviceState
        if ($device -and $device.State -eq "device") {
            Write-Host "Phone ready: $($device.Serial)" -ForegroundColor Green
            return $true
        }
        if ($device -and $device.State -eq "unauthorized") {
            Write-Host "Unlock phone and tap Allow on the USB debugging prompt." -ForegroundColor Yellow
        } elseif ($device -and $device.State -eq "offline") {
            Write-Host "Phone is offline - replug USB or toggle USB debugging, then wait..." -ForegroundColor Yellow
        } else {
            Write-Host "Waiting for phone (USB or Wi-Fi adb)..." -ForegroundColor Yellow
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Show-AdbHelp {
    Write-Host ""
    Write-Host "Could not talk to your phone over adb." -ForegroundColor Red
    Write-Host ""
    Write-Host "USB:" -ForegroundColor Cyan
    Write-Host "  1. Unlock phone, use a data USB cable"
    Write-Host "  2. Developer options -> USB debugging ON"
    Write-Host "  3. Tap Allow on the debugging prompt"
    Write-Host "  4. Run install.cmd again"
    Write-Host ""
    Write-Host "Wi-Fi (no cable):" -ForegroundColor Cyan
    Write-Host "  1. Phone: Wireless debugging ON"
    Write-Host "  2. PC: .\connect-wifi.cmd"
    Write-Host "  3. PC: .\install.cmd"
    Write-Host ""
}

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }
    foreach ($candidate in @(
            "$env:LOCALAPPDATA\Programs\Android\Android Studio\jbr",
            "$env:ProgramFiles\Android\Android Studio\jbr",
            ${env:ProgramFiles(x86)} + "\Android\Android Studio\jbr"
        )) {
        if ($candidate -and (Test-Path "$candidate\bin\java.exe")) {
            return $candidate
        }
    }
    return $null
}

$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
    Write-Host "Using Java: $javaHome"
} else {
    Write-Host "Warning: Java not found on PATH."
}

Write-Host ""
Write-Host "=== MeshHood install ===" -ForegroundColor Cyan
Write-Host "Project: $PWD"
Write-Host ""

$adb = Initialize-Adb
$apk = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
$hasDevice = $false

if ($adb) {
    $hasDevice = Wait-AdbDevice
    if (-not $hasDevice) {
        Show-AdbHelp
        exit 1
    }
}

& "$PSScriptRoot\gradlew.bat" assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Build failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

if ($hasDevice) {
    Write-Host ""
    Write-Host "Installing APK on phone..." -ForegroundColor Cyan
    & adb install -r $apk
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "adb install failed. Restarting adb and retrying once..." -ForegroundColor Yellow
        if (Wait-AdbDevice -Seconds 20) {
            & adb install -r $apk
        }
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "Install failed (exit $LASTEXITCODE). Common fixes:" -ForegroundColor Red
        Write-Host "  - Unlock phone and keep screen on during install"
        Write-Host "  - Replug USB cable or run connect-wifi.cmd"
        Write-Host "  - Settings -> Developer options -> Revoke USB debugging authorizations, then Allow again"
        exit $LASTEXITCODE
    }

    Write-Host ""
    Write-Host "Opening MeshHood on phone..." -ForegroundColor Cyan
    & adb shell am start -n com.meshhood/.MainActivity | Out-Null
} else {
    Write-Host ""
    Write-Host "Built $apk" -ForegroundColor Green
    Write-Host "No adb device - run connect-wifi.cmd or plug in USB, then install.cmd again." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
