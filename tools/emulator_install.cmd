@echo off
REM Build and install consumer debug on a running Android emulator, then open MainActivity.
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
    echo adb not found. Install Android SDK platform-tools.
    exit /b 1
)

set "EMULATOR="
for /f "tokens=1" %%a in ('"%ADB%" devices 2^>nul ^| findstr /r /i "emulator-[0-9][0-9]*[ 	]*device"') do (
    set "EMULATOR=%%a"
    goto :found
)

echo No emulator running. Start an AVD in Android Studio ^(Device Manager^), then run this script again.
exit /b 1

:found
echo Using emulator !EMULATOR!
set "ANDROID_SERIAL=!EMULATOR!"

if exist "%LOCALAPPDATA%\Programs\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android\Android Studio\jbr"
) else if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo === MeshHood consumer - emulator install ===
echo Project: %CD%
echo.

call gradlew.bat assembleConsumerDebug installConsumerDebug
if errorlevel 1 (
    echo Build/install failed.
    exit /b 1
)

echo.
echo Opening MeshHood on emulator...
"%ADB%" -s !EMULATOR! shell am start -n com.meshhood/.MainActivity
echo Done.
