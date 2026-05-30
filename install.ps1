# Install app on phone (USB or Wi-Fi adb - run connect-wifi.cmd first for Wi-Fi)
# Usage: connect-wifi.cmd OR plug in USB, then: install.cmd

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

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

& "$PSScriptRoot\gradlew.bat" installDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Build failed (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adb) {
    $devices = & $adb devices | Select-String "device$"
    if ($devices) {
        Write-Host ""
        Write-Host "Opening MeshHood on phone..." -ForegroundColor Cyan
        & $adb shell am start -n com.meshhood/.MainActivity | Out-Null
    } else {
        Write-Host ""
        Write-Host "APK installed. No adb device - open MeshHood manually or run connect-wifi.cmd" -ForegroundColor Yellow
    }
} else {
    Write-Host ""
    Write-Host "APK installed. adb not found - open MeshHood manually on the phone." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
