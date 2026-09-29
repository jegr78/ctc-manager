# Stage 1: Build
# Pinned to -noble: Playwright does not support Ubuntu 26.04 yet.
FROM eclipse-temurin:25.0.4_7-jdk-noble AS build

WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# The validate phase needs config/ (Checkstyle) and scripts/ (build guards).
COPY config config
COPY scripts scripts
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
# Pinned to -noble: Playwright does not support Ubuntu 26.04 yet.
FROM eclipse-temurin:25.0.4_7-jre-noble

# curl for the healthcheck, the rest for Playwright's Chromium.
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r ctc && useradd -r -g ctc ctc

WORKDIR /app

# Volume mount points; a fresh named volume inherits this ownership.
RUN mkdir -p /app/uploads /app/ctc-site-output /app/data /app/logs && chown -R ctc:ctc /app

COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-*.jar /app/ctc-manager.jar

ENV PLAYWRIGHT_BROWSERS_PATH=/app/.playwright
RUN java -cp /app/ctc-manager.jar -Dloader.main=com.microsoft.playwright.CLI \
    org.springframework.boot.loader.launch.PropertiesLauncher install chromium \
    && chown -R ctc:ctc /app/.playwright

USER ctc

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ctc-manager.jar"]
