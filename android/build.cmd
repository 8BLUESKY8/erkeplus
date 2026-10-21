@echo off
rem erke++ build entry (ASCII only)
rem Calls build.ps1 with execution policy bypassed.
setlocal
set "SCRIPT=%~dp0build.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" %*
exit /b %ERRORLEVEL%
