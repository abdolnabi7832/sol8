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

# Install Pure Wine64, Xvfb, XZ utils, and system monitoring utilities
RUN echo "deb http://archive.ubuntu.com/ubuntu/ jammy universe" >> /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        wine64 \
        xvfb \
        xz-utils \
        tar \
        python3 \
        python3-pip \
        python3-pil \
        curl \
        wget \
        procps \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Download standalone mt5server.exe bridge (19.4 MB) directly in cloud
RUN wget -q https://github.com/lucas-campagna/mt5linux/releases/download/server-1.1.1/mt5server.exe -O /app/mt5server.exe

# Install Python requirements (including mt5linux & rpyc)
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# Copy MT5 Portable Bundle, Data Logs, Daemon & Compiled JAR
COPY mt5_bundle.tar.xz /app/mt5_bundle.tar.xz
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
