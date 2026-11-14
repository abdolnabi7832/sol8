#!/bin/bash

echo "=========================================================="
echo "🚀 FOREX INTELLIGENCE SUITE v7.0 — CLOUD RUNTIME STARTUP"
echo "=========================================================="

export DEBIAN_FRONTEND=noninteractive
export DISPLAY=:99
export WINEDEBUG=-all
export WINEPREFIX=/root/.wine
export WINEARCH=win64
export WINEDLLOVERRIDES="mscoree,mshtml="

# 1. Start Xvfb Virtual Framebuffer
echo "🖥️  Starting Xvfb Virtual Framebuffer on :99..."
Xvfb :99 -screen 0 1024x768x16 -ac &
sleep 2

# 2. Extract MT5 Portable Bundle if not already extracted
if [ ! -f /app/mt5/terminal64.exe ]; then
    echo "📦 Extracting GTC MetaTrader 5 Portable Bundle..."
    mkdir -p /app/mt5
    tar -xf /app/mt5_bundle.tar.xz -C /app/mt5 || true
    echo "✅ MT5 Terminal unpacked successfully."
fi

# Detect Wine executable
WINE_BIN=$(command -v wine64 || command -v wine)
echo "🍷 Using Wine binary: $WINE_BIN"

# 3. Launch MetaTrader 5 Terminal64 in Portable Mode with GTC Demo Auto-Login
echo "🚀 Launching MetaTrader 5 Terminal64 inside Wine64..."
$WINE_BIN /app/mt5/terminal64.exe /portable /config:/app/mt5/start.ini &
sleep 5

# 4. Launch mt5server.exe RPyC Bridge
if [ -f /app/mt5/mt5server.exe ]; then
    echo "⚡ Launching mt5server.exe RPyC Bridge on Port 18812..."
    $WINE_BIN /app/mt5/mt5server.exe -p 18812 &
    sleep 3
elif [ -f /app/mt5server.exe ]; then
    echo "⚡ Launching mt5server.exe RPyC Bridge on Port 18812..."
    $WINE_BIN /app/mt5server.exe -p 18812 &
    sleep 3
fi

# 5. Launch Python MT5 Cockpit Gateway Daemon
echo "🤖 Launching Python MT5 Cockpit Daemon on Port ${MT5_COCKPIT_PORT:-8085}..."
python3 /app/mt5_cockpit_daemon.py &

# 6. Launch Spring Boot Institutional Command Center
echo "💎 Launching Spring Boot Institutional Command Center on Port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar
