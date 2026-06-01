@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_agency_gateway.ps1"
exit /b %ERRORLEVEL%
