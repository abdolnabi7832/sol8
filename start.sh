#!/bin/bash
set -e

echo "=========================================================="
echo "🚀 FOREX INTELLIGENCE SUITE v7.0 — CLOUD RUNTIME STARTUP"
echo "=========================================================="

# 1. Launch Python MT5 Cockpit Gateway Daemon in background
echo "⚡ Launching MT5 Cockpit Daemon on port ${MT5_COCKPIT_PORT:-8085}..."
python3 /app/mt5_cockpit_daemon.py &
DAEMON_PID=$!
echo "✅ MT5 Cockpit Daemon active (PID: $DAEMON_PID)"

# 2. Launch Spring Boot Institutional Command Center
echo "💎 Launching Spring Boot Command Center on port ${PORT:-8080}..."
exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar
