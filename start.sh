#!/bin/bash
set -e

echo "=========================================================="
echo "ðŸš€ FOREX INTELLIGENCE SUITE v7.0 â€” CLOUD RUNTIME STARTUP"
echo "=========================================================="

# 1. Start Virtual X11 Display
echo "ðŸ–¥ï¸ Starting Xvfb Virtual Framebuffer on DISPLAY=:99..."
Xvfb :99 -screen 0 1280x1024x24 -ac &
export DISPLAY=:99
export WINEDEBUG=-all
sleep 2

# 2. Check for Wine Windows Python
WINE_PYTHON=""
if [ -f "$WINEPREFIX/drive_c/Program Files/Python310/python.exe" ]; then
    WINE_PYTHON="$WINEPREFIX/drive_c/Program Files/Python310/python.exe"
elif [ -f "$WINEPREFIX/drive_c/users/root/AppData/Local/Programs/Python/Python310/python.exe" ]; then
    WINE_PYTHON="$WINEPREFIX/drive_c/users/root/AppData/Local/Programs/Python/Python310/python.exe"
fi

if [ -n "$WINE_PYTHON" ]; then
    echo "âš¡ Found Wine Windows Python at $WINE_PYTHON"
    echo "ðŸš€ Launching MT5 Cockpit Daemon via Wine Windows Python (Direct MT5 Bridge)..."
    wine "$WINE_PYTHON" /app/mt5_cockpit_daemon.py &
else
    echo "â˜ï¸ Wine Python not detected. Launching via Linux Python (Cloud Gateway Engine)..."
    python3 /app/mt5_cockpit_daemon.py &
fi

# 3. Launch Spring Boot Java Command Center
echo "ðŸ’Ž Launching Spring Boot Institutional Command Center on port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar
