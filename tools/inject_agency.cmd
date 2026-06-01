@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0inject_agency.ps1" %*
exit /b %ERRORLEVEL%
