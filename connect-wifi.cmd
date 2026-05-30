@echo off
REM Always use this file (not connect-wifi.ps1) - bypasses PowerShell script block.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect-wifi.ps1" %*
exit /b %ERRORLEVEL%
