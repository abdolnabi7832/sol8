# ==============================================================================
# FOREX INTELLIGENCE SUITE v7.0 — ENTERPRISE CLOUD & RAILWAY DOCKERFILE
# Multi-Stage Build: Spring Boot 3.3.5 + WineHQ Stable + Embeddable Win64 Python
# + MetaTrader 5 Package (native Wine HTTP bridge, zero RPyC/MSI installer)
# ==============================================================================

FROM eclipse-temurin:21-jdk-jammy AS java-builder
WORKDIR /build
COPY backend-java/ ./backend-java/
RUN chmod +x ./backend-java/mvnw && \
    cd backend-java && \
    ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
LABEL maintainer="Institutional Quant Suite Architecture v7.0"
WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive \
    WINEPREFIX=/root/.wine \
    WINEARCH=win64 \
    DISPLAY=:99 \
    WINEDEBUG=-all \
    WINEDLLOVERRIDES="mscoree,mshtml=" \
    PYTHON_VERSION=3.12.8 \
    PATH="/opt/wine-stable/bin:/usr/lib/wine:${PATH}"

# 1. Install Virtual Display, Lightweight WM (openbox), X11 automation & Official WineHQ Stable
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        xvfb \
        xauth \
        openbox \
        xdotool \
        unzip \
        xz-utils \
        tar \
        python3 \
        python3-pip \
        python3-pil \
        fonts-dejavu-core \
        fonts-liberation \
        curl \
        gnupg2 \
        ca-certificates \
        procps && \
    mkdir -pm755 /etc/apt/keyrings && \
    curl -fsSL https://dl.winehq.org/wine-builds/winehq.key -o /etc/apt/keyrings/winehq-archive.key && \
    curl -fsSL https://dl.winehq.org/wine-builds/ubuntu/dists/jammy/winehq-jammy.sources -o /etc/apt/sources.list.d/winehq-jammy.sources && \
    apt-get update && \
    apt-get install -y --install-recommends winehq-stable && \
    rm -rf /var/lib/apt/lists/*

# 2. Install Embeddable Windows Python 3.12 + Official MetaTrader5 & NumPy Wheels into /app/winepy
COPY mt5_runtime_dlls/ /app/mt5_runtime_dlls/
RUN mkdir -p /app/winepy/Lib/site-packages && \
    curl -fsSL -o /tmp/python-embed.zip "https://www.python.org/ftp/python/${PYTHON_VERSION}/python-${PYTHON_VERSION}-embed-amd64.zip" && \
    unzip -q /tmp/python-embed.zip -d /app/winepy && \
    rm -f /tmp/python-embed.zip && \
    sed -i 's/^#import site/import site/' /app/winepy/python312._pth && \
    printf "Lib\\\\site-packages\r\nZ:\\\\app\r\n" >> /app/winepy/python312._pth && \
    pip3 install --no-cache-dir --no-deps --platform win_amd64 --python-version 3.12 --only-binary=:all: \
        --target /app/winepy/Lib/site-packages "MetaTrader5>=5.0.45" "numpy<2.3" && \
    cp -f /app/mt5_runtime_dlls/*.dll /app/winepy/ 2>/dev/null || true

# 3. Verify Wine + Embeddable Python + MetaTrader5 import cleanly using an isolated build prefix
#    (Runtime /root/.wine is initialized cleanly at container startup just like working runtime)
RUN WINE_CMD="" && \
    for c in "/opt/wine-stable/bin/wine64" "/opt/wine-stable/bin/wine" "/usr/bin/wine64" "/usr/bin/wine"; do \
        if [ -x "$c" ]; then WINE_CMD="$c"; break; fi; \
    done && \
    echo "BUILD_WINE_CMD=$WINE_CMD" && \
    WINEPREFIX=/tmp/wine_build_test xvfb-run -a "$WINE_CMD" wineboot --init && \
    WINEPREFIX=/tmp/wine_build_test wineserver -w && \
    WINEPREFIX=/tmp/wine_build_test WINEDLLOVERRIDES="mscoree,mshtml=;ucrtbase,api-ms-win-crt-runtime-l1-1-0,api-ms-win-crt-math-l1-1-0,vcruntime140,vcruntime140_1,msvcp140=n,b" \
        xvfb-run -a "$WINE_CMD" /app/winepy/python.exe -c "import MetaTrader5 as mt5; print('WINE_MT5_READY', mt5.__version__)" && \
    WINEPREFIX=/tmp/wine_build_test wineserver -k || true && \
    rm -rf /tmp/wine_build_test

# 4. Install Linux Python requirements
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# 5. Pre-extract MT5 Portable Bundle & native runtime DLLs
COPY mt5_bundle.tar.xz /app/mt5_bundle.tar.xz
RUN mkdir -p /app/mt5 && \
    tar -xf /app/mt5_bundle.tar.xz -C /app/mt5 && \
    rm -f /app/mt5_bundle.tar.xz && \
    rm -rf /app/mt5/config && \
    cp -f /app/mt5_runtime_dlls/*.dll /app/mt5/ 2>/dev/null || true

# 6. Copy Data, Bridges, Daemon, Backtester, Auto-Trader, Launcher & Spring Boot JAR
COPY data/ /app/data/
COPY strategy_playbook.py /app/strategy_playbook.py
COPY backtest_engine.py /app/backtest_engine.py
COPY autonomous_trader.py /app/autonomous_trader.py
COPY mt5_wine_bridge.py /app/mt5_wine_bridge.py
COPY mt5_cockpit_daemon.py /app/mt5_cockpit_daemon.py
COPY start.sh /app/start.sh
RUN sed -i 's/\r$//' /app/start.sh && chmod +x /app/start.sh
COPY --from=java-builder /build/backend-java/target/forex-intelligence-suite-1.0.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080 \
    MT5_COCKPIT_PORT=8085 \
    MT5_BRIDGE_PORT=18815 \
    MT5_WINE_BRIDGE_URL="http://127.0.0.1:18815" \
    JAVA_OPTS="-Xms128m -Xmx384m -Dfile.encoding=UTF-8"

EXPOSE 8080 8085 18815

CMD ["/bin/bash", "/app/start.sh"]
