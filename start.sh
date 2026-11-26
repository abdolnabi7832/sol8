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

    printf "[Common]\r\nLogin=%s\r\nPassword=%s\r\nServer=%s\r\nNewsEnable=0\r\nEventsEnable=0\r\nPortable=1\r\n[Charts]\r\nMaxBars=100000\r\nPrintColor=0\r\n[Experts]\r\nEnabled=1\r\nAllowLiveTrading=1\r\nAllowDllImport=1\r\nAccount=0\r\nProfile=0\r\nChart=0\r\nApi=0\r\n[StartUp]\r\nLogin=%s\r\nPassword=%s\r\nServer=%s\r\nAutoConfiguration=false\r\nShutdownTerminal=No\r\n" \
        "$MT5_LOGIN" "$MT5_PASSWORD" "$MT5_SERVER" "$MT5_LOGIN" "$MT5_PASSWORD" "$MT5_SERVER" > /app/mt5/start.ini
}

write_mt5_configs

# Helper that returns 0 only if a NON-ZOMBIE matching process is alive (ignores <defunct> zombies)
is_proc_alive() {
    ps -eo stat=,args= | awk -v pat="$1" 'substr($1,1,1) != "Z" && index($0, pat) > 0 && $0 !~ /awk/ { found=1; exit 0 } END { exit (found ? 0 : 1) }'
}

# Detect Wine executable (prefer wine64 9.0 first)
WINE_BIN=""
for candidate in "/opt/wine-stable/bin/wine64" "/opt/wine-stable/bin/wine" "/usr/bin/wine64" "/usr/lib/wine/wine64" "/usr/bin/wine" "wine64" "wine"; do
    if [ -x "$candidate" ] || command -v "$candidate" >/dev/null 2>&1; then
        WINE_BIN="$candidate"
        break
    fi
done

echo "🍷 Using Wine binary: $WINE_BIN ($($WINE_BIN --version 2>/dev/null || true))"

# 3. Launch MetaTrader 5 Terminal64 + Native Wine Python Bridge + Zombie-Aware Watchdog
if [ -n "$WINE_BIN" ]; then
    (
      echo "🍷 Wine Version: $($WINE_BIN --version 2>&1)" > /tmp/mt5_terminal.log
      # Fallback initialization if prefix missing; normally pre-initialized in Dockerfile
      export WINEDLLOVERRIDES="mscoree,mshtml="
      if [ ! -f /root/.wine/drive_c/windows/system32/kernel32.dll ]; then
          echo "🍷 Initializing fresh Wine64 Prefix in /root/.wine..." >> /tmp/mt5_terminal.log
          rm -rf /root/.wine
          $WINE_BIN wineboot --init >> /tmp/mt5_terminal.log 2>&1 || true
          wineserver -w 2>/dev/null || sleep 6
          $WINE_BIN reg add "HKEY_CURRENT_USER\Software\Wine" /v Version /t REG_SZ /d "win10" /f >/dev/null 2>&1 || true
          $WINE_BIN reg add "HKEY_CURRENT_USER\Software\Wine\WineDbg" /v ShowCrashDialog /t REG_DWORD /d 0 /f >/dev/null 2>&1 || true
          $WINE_BIN reg add "HKEY_LOCAL_MACHINE\Software\Microsoft\Windows NT\CurrentVersion\AeDebug" /v Auto /t REG_SZ /d "0" /f >/dev/null 2>&1 || true
      fi

      export WINEDLLOVERRIDES="mscoree,mshtml=;vcruntime140,vcruntime140_1,msvcp140=n,b"
      export MT5_TERMINAL_PATH="Z:\\app\\mt5\\terminal64.exe"

      launch_terminal() {
          echo "🚀 Launching MetaTrader 5 Terminal64 ($WINE_BIN /portable /config:start.ini) at $(date -u +%FT%TZ)..." >> /tmp/mt5_terminal.log
          (
              cd /app/mt5 || exit 1
              WINEDEBUG="err+all,-ole,-quartz" "$WINE_BIN" terminal64.exe /portable /config:start.ini >> /tmp/mt5_terminal.log 2>&1
              echo "⚠️ [Exit] terminal64.exe exited rc=$? at $(date -u +%FT%TZ)" >> /tmp/mt5_terminal.log
          ) &
      }

      WINE_PY="/app/winepy/python.exe"
      [ ! -f "$WINE_PY" ] && WINE_PY="python"

      launch_bridge() {
          echo "⚡ Launching Native Wine Python MT5 HTTP Bridge on Port ${MT5_BRIDGE_PORT} (${WINE_PY}) via PTY at $(date -u +%FT%TZ)..." >> /tmp/mt5_wine_bridge.log
          (
              cd /app || exit 1
              PYTHONLEGACYWINDOWSSTDIO=1 PYTHONIOENCODING=utf-8 PYTHONUNBUFFERED=1 WINEDEBUG=-all \
              python3 -u /app/wine_pty.py "$WINE_BIN" "$WINE_PY" /app/mt5_wine_bridge.py --port "${MT5_BRIDGE_PORT}" >> /tmp/mt5_wine_bridge.log 2>&1
          ) &
      }

      launch_terminal
      sleep 14
      launch_bridge
      sleep 4

      # Self-healing Watchdog (reaps/ignores zombie processes & restarts terminal64 or bridge if either exits)
      while true; do
        sleep 10
        if ! is_proc_alive "terminal64.exe"; then
          echo "⚠️ [Watchdog] terminal64.exe not alive, re-writing Config/common.ini (Api=0, Enabled=1) and restarting..." >> /tmp/mt5_terminal.log
          write_mt5_configs
          launch_terminal
          sleep 14
        fi
        if ! is_proc_alive "mt5_wine_bridge.py"; then
          echo "⚠️ [Watchdog] mt5_wine_bridge.py not alive, restarting..." >> /tmp/mt5_wine_bridge.log
          launch_bridge
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
