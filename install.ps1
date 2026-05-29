# Build and install MeshHood on a connected Android device.
# Usage (from anywhere):
#   powershell -ExecutionPolicy Bypass -File C:\Users\Owner\AndroidStudioProjects\MeshHood\install.ps1

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
    Write-Host "Warning: Java not found on PATH. Gradle may still work if Android Studio JBR is configured."
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
        Write-Host "APK installed. No USB device detected - open MeshHood manually on the phone." -ForegroundColor Yellow
        Write-Host "Tip: enable USB debugging and run: $adb devices"
    }
} else {
    Write-Host ""
    Write-Host "APK installed. adb not found - open MeshHood manually on the phone." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
