# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

# Maven Wrapper und pom.xml kopieren fuer Dependency-Caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Dependencies herunterladen (gecached solange pom.xml sich nicht aendert)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Source kopieren und bauen
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre

# curl fuer Healthcheck installieren
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Non-root User erstellen
RUN groupadd -r ctc && useradd -r -g ctc -u 1000 ctc

WORKDIR /app

# Verzeichnisse fuer Uploads und Site-Output
RUN mkdir -p /app/uploads /app/ctc-site-output && chown -R ctc:ctc /app

# JAR aus Build-Stage kopieren
COPY --from=build --chown=ctc:ctc /build/target/ctc-manager-*.jar /app/ctc-manager.jar

USER ctc

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ctc-manager.jar"]
