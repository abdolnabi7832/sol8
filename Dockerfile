# ==============================================================================
# FOREX INTELLIGENCE SUITE v7.0 — ENTERPRISE CLOUD & RAILWAY DOCKERFILE
# Multi-Stage Build: Spring Boot 3.3.5 + WineHQ Stable + Windows Python in Wine
# + Xvfb + Openbox + MetaTrader 5 Portable
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
    WINEDLLOVERRIDES="mscoree=d;mshtml=d" \
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
        cabextract \
        unzip \
        xz-utils \
        tar \
        python3 \
        python3-pip \
        python3-pil \
        fonts-dejavu-core \
        fonts-liberation \
        curl \
        wget \
        gnupg2 \
        ca-certificates \
        procps && \
    mkdir -pm755 /etc/apt/keyrings && \
    curl -fsSL https://dl.winehq.org/wine-builds/winehq.key -o /etc/apt/keyrings/winehq-archive.key && \
    curl -fsSL https://dl.winehq.org/wine-builds/ubuntu/dists/jammy/winehq-jammy.sources -o /etc/apt/sources.list.d/winehq-jammy.sources && \
    apt-get update && \
    apt-get install -y --install-recommends winehq-stable && \
    curl -fsSL -o /usr/local/bin/winetricks https://raw.githubusercontent.com/Winetricks/winetricks/master/src/winetricks && \
    chmod +x /usr/local/bin/winetricks && \
    rm -rf /var/lib/apt/lists/*

# 2. Initialize Wine64 Prefix + VC++ Runtime (vcrun2022) + Windows Python 3.12 + MetaTrader5 Package
# Pre-building inside Docker layers guarantees fast deterministic boots on Railway.
RUN WINE_CMD="$(command -v wine || command -v wine64)" && \
    xvfb-run -a "$WINE_CMD" wineboot --init && wineserver -w && \
    "$WINE_CMD" reg add "HKEY_CURRENT_USER\\Software\\Wine" /v Version /t REG_SZ /d "win10" /f && \
    "$WINE_CMD" reg add "HKEY_CURRENT_USER\\Software\\Wine\\WineDbg" /v ShowCrashDialog /t REG_DWORD /d 0 /f && \
    "$WINE_CMD" reg add "HKEY_LOCAL_MACHINE\\Software\\Microsoft\\Windows NT\\CurrentVersion\\AeDebug" /v Auto /t REG_SZ /d "0" /f && \
    (xvfb-run -a winetricks -q --unattended vcrun2022 || xvfb-run -a winetricks -q --unattended vcrun2019 || true) && \
    wineserver -w && \
    curl -fsSL -o /tmp/python-setup.exe "https://www.python.org/ftp/python/${PYTHON_VERSION}/python-${PYTHON_VERSION}-amd64.exe" && \
    xvfb-run -a "$WINE_CMD" /tmp/python-setup.exe /quiet InstallAllUsers=1 PrependPath=1 Include_doc=0 Include_tcltk=0 Include_test=0 && \
    wineserver -w && \
    rm -f /tmp/python-setup.exe && \
    if [ -f "/root/.wine/drive_c/Program Files/Python312/python.exe" ]; then WINE_PY='C:\Program Files\Python312\python.exe'; else WINE_PY="python"; fi && \
    xvfb-run -a "$WINE_CMD" "$WINE_PY" -m pip install --no-cache-dir --upgrade pip MetaTrader5 && \
    wineserver -w && \
    xvfb-run -a "$WINE_CMD" "$WINE_PY" -c "import MetaTrader5 as mt5; print('WINE_MT5_READY', mt5.__version__)" && \
    wineserver -w

# 3. Install Linux Python requirements
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# 4. Pre-extract MT5 Portable Bundle & native runtime DLLs
COPY mt5_bundle.tar.xz /app/mt5_bundle.tar.xz
COPY mt5_runtime_dlls/ /app/mt5_runtime_dlls/
RUN mkdir -p /app/mt5 && \
    tar -xf /app/mt5_bundle.tar.xz -C /app/mt5 && \
    rm -f /app/mt5_bundle.tar.xz && \
    rm -rf /app/mt5/config && \
    cp -n /app/mt5_runtime_dlls/*.dll /app/mt5/ 2>/dev/null || true

# 5. Copy Data, Bridges, Daemon, Launcher & Spring Boot JAR
COPY data/ /app/data/
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
