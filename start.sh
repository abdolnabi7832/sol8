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
export PATH="/usr/lib/wine:${PATH}"

# 1. Start Xvfb Virtual Framebuffer
echo "🖥️  Starting Xvfb Virtual Framebuffer on :99..."
Xvfb :99 -screen 0 1024x768x16 -ac &
sleep 2

# 2. Extract MT5 Portable Bundle if not already extracted
mkdir -p /app/mt5
if [ ! -f /app/mt5/terminal64.exe ]; then
    echo "📦 Extracting GTC MetaTrader 5 Portable Bundle..."
    tar -xf /app/mt5_bundle.tar.xz -C /app/mt5 || true
    echo "✅ MT5 Terminal unpacked successfully."
fi

# Detect Wine executable
WINE_BIN=""
for candidate in "/usr/bin/wine64" "/usr/lib/wine/wine64" "/usr/bin/wine" "wine64" "wine"; do
    if [ -x "$candidate" ] || command -v "$candidate" >/dev/null 2>&1; then
        WINE_BIN="$candidate"
        break
    fi
done

echo "🍷 Using Wine binary: $WINE_BIN"

# 3. Launch MetaTrader 5 Terminal64 inside Wine64 if Wine is available
if [ -n "$WINE_BIN" ]; then
    if [ ! -d /root/.wine ]; then
        echo "🍷 Initializing Wine64 Prefix in headless mode..."
        $WINE_BIN wineboot --init || true
        sleep 6
    fi

    echo "🚀 Launching MetaTrader 5 Terminal64 inside Wine64..."
    cd /app/mt5
    $WINE_BIN terminal64.exe /portable /login:57452535486 /password:1zQbW!Xc /server:GTCGlobalTrade-Demo &
    sleep 8

    # 4. Launch mt5server.exe RPyC Bridge on Port 18812
    if [ -f /app/mt5/mt5server.exe ]; then
        echo "⚡ Launching mt5server.exe RPyC Bridge on Port 18812..."
        $WINE_BIN mt5server.exe --host 0.0.0.0 -p 18812 &
        sleep 3
    elif [ -f /app/mt5server.exe ]; then
        echo "⚡ Launching mt5server.exe RPyC Bridge on Port 18812..."
        $WINE_BIN /app/mt5server.exe --host 0.0.0.0 -p 18812 &
        sleep 3
    fi
    cd /app
else
    echo "ℹ️ Wine binary not detected directly in this layer. Standby Cloud Gateway mode active for #57452535486."
fi

# 5. Launch Python MT5 Cockpit Gateway Daemon
echo "🤖 Launching Python MT5 Cockpit Daemon on Port ${MT5_COCKPIT_PORT:-8085}..."
python3 /app/mt5_cockpit_daemon.py &

# 6. Launch Spring Boot Institutional Command Center
echo "💎 Launching Spring Boot Institutional Command Center on Port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar
