@echo off
chcp 65001 >nul
title FOREX INTELLIGENCE v7.0 — MT5 LIVE LOCAL BRIDGE
color 0b

echo ==============================================================================
echo   FOREX INTELLIGENCE SUITE v7.0 — LOCAL MT5 LIVE RUNNER (WINDOWS NATIVE)
echo ==============================================================================
echo [INFO] Broker Account : #57452535486 (GTCGlobalTrade-Demo)
echo [INFO] Terminal Path  : C:\Program Files\GTC Global Trade MetaTrader 5\terminal64.exe
echo [INFO] Telegram Bot   : @GTC_Aladdin_Bot
echo ==============================================================================
echo.

python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH. Please install Python 3.10+ and re-run.
    pause
    exit /b 1
)

echo [1/3] Checking MetaTrader5 Python connector...
python -c "import MetaTrader5" >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Installing MetaTrader5 package...
    pip install MetaTrader5 requests pillow rpyc
)

echo [2/3] Verifying GTC MetaTrader 5 Terminal connection...
python -c "import MetaTrader5 as mt5; ok = mt5.initialize(path=r'C:\Program Files\GTC Global Trade MetaTrader 5\terminal64.exe'); print('MT5 Terminal Init:', 'SUCCESS' if ok else 'FAILED'); mt5.shutdown()"

echo [3/3] Starting MT5 Cockpit Daemon on Port 8085 with live broker execution...
echo.
python mt5_cockpit_daemon.py

pause
