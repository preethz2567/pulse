# =========================================================
# Stage 1: Build & Layer Extraction
# =========================================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy Maven wrapper and POM first
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make wrapper executable
RUN chmod +x mvnw

# Copy project source code and build executable archive
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress

# Extract Spring Boot 3.3 layered JAR using built-in tools mode
RUN java -Djarmode=tools -jar target/pulse-*.jar extract --layers --launcher --destination extracted

# =========================================================
# Stage 2: Minimal Debian-based Runtime
# =========================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install curl for Actuator health checks and clean package cache
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create dedicated non-root user and group
RUN groupadd -r pulse --gid 10001 && \
    useradd -r -g pulse --uid 10001 --no-create-home --shell /bin/bash pulse

# Copy extracted layers in order of change frequency (least frequent to most frequent)
COPY --from=builder --chown=pulse:pulse /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=pulse:pulse /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=pulse:pulse /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=pulse:pulse /workspace/extracted/application/ ./

# Run as non-root user
USER pulse:pulse

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
