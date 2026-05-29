@echo off
REM Run from MeshHood\tools — installs app on connected phone
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\install.ps1"
exit /b %ERRORLEVEL%
