@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_maps.ps1" %*
exit /b %ERRORLEVEL%
