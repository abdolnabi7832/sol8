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
export PATH="/opt/wine-stable/bin:/usr/lib/wine:${PATH}"

export MT5_LOGIN="${MT5_LOGIN:-57452535486}"
export MT5_PASSWORD="${MT5_PASSWORD:-1zQbW!Xc}"
export MT5_SERVER="${MT5_SERVER:-GTCGlobalTrade-Demo}"
export MT5_BRIDGE_PORT="${MT5_BRIDGE_PORT:-18815}"
export MT5_WINE_BRIDGE_URL="${MT5_WINE_BRIDGE_URL:-http://127.0.0.1:${MT5_BRIDGE_PORT}}"

# 1. Start Xvfb Virtual Framebuffer & wait until X11 socket is ready
echo "🖥️  Starting Xvfb Virtual Framebuffer on :99..."
rm -f /tmp/.X99-lock
Xvfb :99 -ac -nolisten tcp -screen 0 1280x800x24 >/tmp/xvfb.log 2>&1 &
for i in $(seq 1 20); do
    if [ -e /tmp/.X11-unix/X99 ]; then
        break
    fi
    sleep 0.5
done

# Lightweight window manager prevents Wine GUI focus/dialog hangs in headless containers
if command -v openbox >/dev/null 2>&1; then
    openbox >/tmp/openbox.log 2>&1 &
    sleep 1
fi

# 2. Ensure MT5 directory exists and remove any lowercase 'config' dir ambiguity
mkdir -p /app/mt5/Config
rm -rf /app/mt5/config 2>/dev/null || true

write_mt5_configs() {
    # Api=0 means external Python API trading is ALLOWED (unchecked in Tools -> Options -> Expert Advisors)
    printf "[Common]\r\nLogin=%s\r\nPassword=%s\r\nServer=%s\r\nNewsEnable=0\r\nEventsEnable=0\r\nPortable=1\r\n[Charts]\r\nMaxBars=100000\r\nPrintColor=0\r\n[Experts]\r\nAllowDllImport=1\r\nAllowLiveTrading=1\r\nEnabled=1\r\nAccount=0\r\nProfile=0\r\nChart=0\r\nApi=0\r\nWebRequest=0\r\n" \
        "$MT5_LOGIN" "$MT5_PASSWORD" "$MT5_SERVER" > /app/mt5/Config/common.ini

    printf "[Common]\r\nLogin=%s\r\nPassword=%s\r\nServer=%s\r\nNewsEnable=0\r\nEventsEnable=0\r\nPortable=1\r\n[Charts]\r\nMaxBars=100000\r\nPrintColor=0\r\n[Experts]\r\nEnabled=1\r\nAllowLiveTrading=1\r\nAllowDllImport=1\r\nAccount=0\r\nProfile=0\r\nChart=0\r\nApi=0\r\n[StartUp]\r\nShutdownTerminal=No\r\n" \
        "$MT5_LOGIN" "$MT5_PASSWORD" "$MT5_SERVER" > /app/mt5/start.ini
}

write_mt5_configs

# Detect Wine executable (prefer wine64 first, identical to working a8f19d9 deployment)
WINE_BIN=""
for candidate in "/opt/wine-stable/bin/wine64" "/opt/wine-stable/bin/wine" "/usr/bin/wine64" "/usr/lib/wine/wine64" "/usr/bin/wine" "wine64" "wine"; do
    if [ -x "$candidate" ] || command -v "$candidate" >/dev/null 2>&1; then
        WINE_BIN="$candidate"
        break
    fi
done

echo "🍷 Using Wine binary: $WINE_BIN ($($WINE_BIN --version 2>/dev/null || true))"

# 3. Launch MetaTrader 5 Terminal64 + Native Wine Python Bridge + Watchdog asynchronously
# so Ports 8085 and 8080 bind immediately within Railway healthcheck window.
if [ -n "$WINE_BIN" ]; then
    (
      # Initialize clean Wine64 prefix BEFORE applying native UCRT overrides
      export WINEDLLOVERRIDES="mscoree,mshtml="
      if [ ! -f /root/.wine/drive_c/windows/system32/kernel32.dll ]; then
          echo "🍷 Initializing fresh Wine64 Prefix in /root/.wine..." >> /tmp/mt5_terminal.log
          rm -rf /root/.wine
          $WINE_BIN wineboot --init >> /tmp/mt5_terminal.log 2>&1 || true
          wineserver -w 2>/dev/null || sleep 6
      fi

      $WINE_BIN reg add "HKEY_CURRENT_USER\Software\Wine" /v Version /t REG_SZ /d "win10" /f >/dev/null 2>&1 || true
      $WINE_BIN reg add "HKEY_CURRENT_USER\Software\Wine\WineDbg" /v ShowCrashDialog /t REG_DWORD /d 0 /f >/dev/null 2>&1 || true
      $WINE_BIN reg add "HKEY_LOCAL_MACHINE\Software\Microsoft\Windows NT\CurrentVersion\AeDebug" /v Auto /t REG_SZ /d "0" /f >/dev/null 2>&1 || true

      # Install native UCRT DLLs (same layout as working a8f19d9) & set overrides AFTER wineboot
      if [ -d /app/mt5_runtime_dlls ]; then
          cp -f /app/mt5_runtime_dlls/*.dll /app/mt5/ 2>/dev/null || true
          cp -f /app/mt5_runtime_dlls/*.dll /app/winepy/ 2>/dev/null || true
          mkdir -p /root/.wine/drive_c/windows/system32
          cp -f /app/mt5_runtime_dlls/*.dll /root/.wine/drive_c/windows/system32/ 2>/dev/null || true
      fi
      export WINEDLLOVERRIDES="mscoree,mshtml=;ucrtbase,api-ms-win-crt-runtime-l1-1-0,api-ms-win-crt-math-l1-1-0,vcruntime140,vcruntime140_1,msvcp140=n,b"

      echo "🚀 Launching MetaTrader 5 Terminal64 ($WINE_BIN /portable /config:start.ini)..." >> /tmp/mt5_terminal.log
      cd /app/mt5
      $WINE_BIN terminal64.exe /portable /config:start.ini >> /tmp/mt5_terminal.log 2>&1 &
      sleep 12

      WINE_PY="/app/winepy/python.exe"
      [ ! -f "$WINE_PY" ] && WINE_PY="python"

      echo "⚡ Launching Native Wine Python MT5 HTTP Bridge on Port ${MT5_BRIDGE_PORT} (${WINE_PY})..." >> /tmp/mt5_wine_bridge.log
      cd /app
      $WINE_BIN "$WINE_PY" /app/mt5_wine_bridge.py --port "${MT5_BRIDGE_PORT}" >> /tmp/mt5_wine_bridge.log 2>&1 &
      sleep 4

      # Self-healing Watchdog (keeps Config/common.ini Api=0 & revives terminal64 or bridge if either exits)
      while true; do
        sleep 10
        if ! pgrep -f "[t]erminal64.exe" >/dev/null 2>&1; then
          echo "⚠️ [Watchdog] terminal64.exe died, re-writing Config/common.ini (Api=0, Enabled=1) and restarting..." >> /tmp/mt5_terminal.log
          write_mt5_configs
          cd /app/mt5 && $WINE_BIN terminal64.exe /portable /config:start.ini >> /tmp/mt5_terminal.log 2>&1 &
          sleep 12
        fi
        if ! pgrep -f "[m]t5_wine_bridge.py" >/dev/null 2>&1; then
          echo "⚠️ [Watchdog] mt5_wine_bridge.py died, restarting..." >> /tmp/mt5_wine_bridge.log
          cd /app && $WINE_BIN "$WINE_PY" /app/mt5_wine_bridge.py --port "${MT5_BRIDGE_PORT}" >> /tmp/mt5_wine_bridge.log 2>&1 &
          sleep 4
        fi
      done
    ) &
else
    echo "⚠️ Wine binary not detected; MT5 native bridge unavailable in this layer."
fi

# 4. Launch Python MT5 Cockpit Gateway Daemon (binds port 8085 immediately)
echo "🤖 Launching Python MT5 Cockpit Daemon on Port ${MT5_COCKPIT_PORT:-8085}..."
cd /app
python3 /app/mt5_cockpit_daemon.py &

# 5. Launch Spring Boot Institutional Command Center
echo "💎 Launching Spring Boot Institutional Command Center on Port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar
