# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

# Copy Maven Wrapper and pom.xml for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached as long as pom.xml does not change)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

# Install curl for health check + Chromium dependencies for Playwright
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r ctc && useradd -r -g ctc ctc

WORKDIR /app

# Create directories for uploads and site output
RUN mkdir -p /app/uploads /app/ctc-site-output /app/logs && chown -R ctc:ctc /app

# Copy JAR from build stage
COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-*.jar /app/ctc-manager.jar

# Install Playwright Chromium browser (for team card generation)
ENV PLAYWRIGHT_BROWSERS_PATH=/app/.playwright
RUN java -cp /app/ctc-manager.jar -Dloader.main=com.microsoft.playwright.CLI \
    org.springframework.boot.loader.launch.PropertiesLauncher install chromium \
    && chown -R ctc:ctc /app/.playwright

USER ctc

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ctc-manager.jar"]
