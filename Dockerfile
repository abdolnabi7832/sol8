# ==============================================================================
# FOREX INTELLIGENCE SUITE v7.0 — ENTERPRISE CLOUD & RAILWAY DOCKERFILE
# Multi-Stage Build: Spring Boot 3.3.5 + Wine + Xvfb + MetaTrader 5 + Python Daemon
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

ENV DEBIAN_FRONTEND=noninteractive
ENV WINEPREFIX=/root/.wine
ENV WINEARCH=win64
ENV DISPLAY=:99
ENV WINEDEBUG=-all

# Install Linux Python, Wine, Xvfb, and required utilities
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        wine \
        wine32 \
        wine64 \
        xvfb \
        winbind \
        python3 \
        python3-pip \
        python3-pil \
        curl \
        wget \
        procps \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Install Linux Python requirements (for gateway engine fallback)
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# Initialize Wine 64-bit environment headlessly
RUN winecfg /v win10 > /dev/null 2>&1 || true

# Download Windows Python 3.10 and install silently inside Wine
RUN wget -q https://www.python.org/ftp/python/3.10.11/python-3.10.11-amd64.exe -O /tmp/python-installer.exe && \
    xvfb-run -a wine /tmp/python-installer.exe /quiet InstallAllUsers=1 PrependPath=1 && \
    rm -f /tmp/python-installer.exe || true

# Install MetaTrader 5 Python package & dependencies inside Wine Windows Python
RUN xvfb-run -a wine "C:\\Program Files\\Python310\\python.exe" -m pip install --no-cache-dir --upgrade pip && \
    xvfb-run -a wine "C:\\Program Files\\Python310\\python.exe" -m pip install --no-cache-dir MetaTrader5 Pillow requests || true

# Download official MetaTrader 5 installer & run silent install inside Wine
RUN wget -q https://download.mql5.com/cdn/web/metaquotes.ltd/mt5/mt5setup.exe -O /app/mt5setup.exe || true
RUN xvfb-run -a wine /app/mt5setup.exe /auto || true

# Copy Data, MT5 Daemon, Start Script & Compiled Spring Boot JAR
COPY data/ /app/data/
COPY mt5_cockpit_daemon.py /app/mt5_cockpit_daemon.py
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
COPY --from=java-builder /build/backend-java/target/forex-intelligence-suite-1.0.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080
ENV MT5_COCKPIT_PORT=8085
ENV JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8"

EXPOSE 8080 8085

CMD ["/bin/bash", "/app/start.sh"]
