# ==============================================================================
# FOREX INTELLIGENCE SUITE v7.0 — ENTERPRISE CLOUD & RAILWAY DOCKERFILE
# Multi-Stage Build: Spring Boot 3.3.5 + Wine64 + Xvfb + MetaTrader 5 Portable
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
ENV PATH="/opt/wine-stable/bin:/usr/lib/wine:${PATH}"

# Install base tools + official WineHQ Stable (Wine 9/10+) + Xvfb for modern MetaTrader 5
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        xvfb \
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

# Install Python requirements (including rpyc)
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# Copy MT5 Portable Bundle, Runtime DLLs, Data Logs, Daemon & Compiled JAR
COPY mt5_bundle.tar.xz /app/mt5_bundle.tar.xz
COPY mt5_runtime_dlls/ /app/mt5_runtime_dlls/
COPY data/ /app/data/
COPY mt5_cockpit_daemon.py /app/mt5_cockpit_daemon.py
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
COPY --from=java-builder /build/backend-java/target/forex-intelligence-suite-1.0.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080
ENV MT5_COCKPIT_PORT=8085
ENV JAVA_OPTS="-Xms128m -Xmx384m -Dfile.encoding=UTF-8"

EXPOSE 8080 8085 18812

CMD ["/bin/bash", "/app/start.sh"]
