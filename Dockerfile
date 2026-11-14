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
ENV PATH="/usr/lib/wine:${PATH}"

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
        procps \
        ca-certificates && \
    ln -sf /usr/lib/wine/wine64 /usr/bin/wine64 && \
    ln -sf /usr/lib/wine/wine64 /usr/bin/wine && \
    ln -sf /usr/lib/wine/wineserver64 /usr/bin/wineserver && \
    ln -sf /usr/lib/wine/wineserver64 /usr/lib/wine/wineserver && \
    rm -rf /var/lib/apt/lists/*

# Install Python requirements (including rpyc)
COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# Copy MT5 Portable Bundle, Bridge, Data Logs, Daemon & Compiled JAR
COPY mt5_bundle.tar.xz /app/mt5_bundle.tar.xz
COPY mt5server.exe /app/mt5server.exe
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
