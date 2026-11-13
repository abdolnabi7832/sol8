FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create persistent storage directories for H2 DB and JSON/CSV logs
RUN mkdir -p /app/data/h2db /app/data/Excel_Logs

# Copy compiled executable fat JAR
COPY target/forex-intelligence-suite-1.0.0-SNAPSHOT.jar app.jar

# Dynamic PORT exposed by Railway (defaults to 8080 if running standalone)
ENV PORT=8080
EXPOSE ${PORT}

# Production JVM optimizations for cloud containers
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dfile.encoding=UTF-8", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
