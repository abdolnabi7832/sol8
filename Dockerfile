# ==============================================================================
# FOREX INTELLIGENCE SUITE v7.0 — ENTERPRISE CLOUD & RAILWAY DOCKERFILE
# Multi-Stage Institutional Build (Spring Boot 3.3.5 + Python MT5 Headless Daemon)
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

# Install Python 3 + Pillow for Headless MT5 Cockpit & Dark-Mode Chart Rendering
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip python3-pil curl && \
    rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir -r /app/requirements.txt || true

# Copy Data Logs, MT5 Headless Daemon & Compiled Spring Boot JAR
COPY data/ /app/data/
COPY mt5_cockpit_daemon.py /app/mt5_cockpit_daemon.py
COPY --from=java-builder /build/backend-java/target/forex-intelligence-suite-1.0.0-SNAPSHOT.jar /app/app.jar

ENV PORT=8080
ENV MT5_COCKPIT_PORT=8085
ENV JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8"

EXPOSE 8080 8085

# Launch Python Headless MT5 Cockpit Daemon (8085) + Spring Boot Command Center (8080)
CMD ["sh", "-c", "python3 /app/mt5_cockpit_daemon.py & exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
