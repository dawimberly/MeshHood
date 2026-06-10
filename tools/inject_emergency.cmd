@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0inject_emergency.ps1" %*
exit /b %ERRORLEVEL%
