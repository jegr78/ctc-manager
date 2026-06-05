# Stage 1: Build
# Pinned to -noble: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky). See Phase 78 / .planning/phases/78-docker-release-image-fix/78-CONTEXT.md.
FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /build

# Maven Wrapper und pom.xml kopieren fuer Dependency-Caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Dependencies herunterladen (gecached solange pom.xml sich nicht aendert)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Source kopieren und bauen
# config/ liefert checkstyle.xml (validate-phase Unused-Import-Gate), scripts/ die
# validate-phase Build-Guards (exec-maven-plugin: bash scripts/guards/*.sh) — ohne
# diese Verzeichnisse bricht ./mvnw package ab.
COPY config config
COPY scripts scripts
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
# Pinned to -noble: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky). See Phase 78 / .planning/phases/78-docker-release-image-fix/78-CONTEXT.md.
FROM eclipse-temurin:25-jre-noble

# curl fuer Healthcheck + Chromium-Dependencies fuer Playwright installieren
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

# Non-root User erstellen
RUN groupadd -r ctc && useradd -r -g ctc ctc

WORKDIR /app

# Verzeichnisse fuer Uploads und Site-Output
RUN mkdir -p /app/uploads /app/ctc-site-output /app/logs && chown -R ctc:ctc /app

# JAR aus Build-Stage kopieren
COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-*.jar /app/ctc-manager.jar

# Playwright Chromium-Browser installieren (fuer Team Card Generierung)
ENV PLAYWRIGHT_BROWSERS_PATH=/app/.playwright
RUN java -cp /app/ctc-manager.jar -Dloader.main=com.microsoft.playwright.CLI \
    org.springframework.boot.loader.launch.PropertiesLauncher install chromium \
    && chown -R ctc:ctc /app/.playwright

USER ctc

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ctc-manager.jar"]
