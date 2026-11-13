@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Forex Intelligence Suite - Institutional Command Center v7.0
color 0B

echo ===============================================================================
echo   FOREX INTELLIGENCE SUITE - INSTITUTIONAL COMMAND CENTER v7.0
echo   Anton Kreil 6-Pillar Framework ^| MT5 Algorithmic Cockpit ^| Railway Ready
echo ===============================================================================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1"
set LAUNCH_EXIT=%ERRORLEVEL%

if %LAUNCH_EXIT% neq 0 (
    echo.
    echo ===============================================================================
    echo   [ERROR] Application launcher exited with code %LAUNCH_EXIT%.
    echo   Press any key to close this terminal...
    echo ===============================================================================
    pause >nul
)
