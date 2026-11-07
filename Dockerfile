# Stage 1: Build
# Pinned to -noble: Playwright does not support Ubuntu 26.04 yet.
FROM eclipse-temurin:25.0.5_8-jdk-noble AS dependencies

WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B \
    && ./mvnw dependency:copy-dependencies -DincludeGroupIds=com.microsoft.playwright \
       -DincludeScope=runtime -DoutputDirectory=/playwright -B

FROM dependencies AS build

# The validate phase needs config/ (Checkstyle) and scripts/ (build guards).
COPY config config
COPY scripts scripts
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
# Pinned to -noble: Playwright does not support Ubuntu 26.04 yet.
FROM eclipse-temurin:25.0.5_8-jre-noble AS browser-runtime

LABEL org.ctc.recovery.version="1"

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r ctc && useradd -r -g ctc ctc

WORKDIR /app

# Volume mount points; a fresh named volume inherits this content and ownership.
RUN mkdir -p /app/data/uploads /app/data/site /app/logs && chown -R ctc:ctc /app

ENV PLAYWRIGHT_BROWSERS_PATH=/app/.playwright
RUN --mount=type=bind,from=dependencies,source=/playwright,target=/opt/ctc-playwright \
    java -cp '/opt/ctc-playwright/*' com.microsoft.playwright.CLI install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/* \
    && chown -R ctc:ctc /app/.playwright

USER ctc

RUN --mount=type=bind,from=dependencies,source=/playwright,target=/opt/ctc-playwright \
    java -cp '/opt/ctc-playwright/*' com.microsoft.playwright.CLI screenshot \
    --browser chromium --timeout 30000 about:blank /tmp/ctc-chromium-smoke.png \
    && test -s /tmp/ctc-chromium-smoke.png && rm /tmp/ctc-chromium-smoke.png

FROM browser-runtime AS runtime

COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-*.jar /app/ctc-manager.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ctc-manager.jar"]
