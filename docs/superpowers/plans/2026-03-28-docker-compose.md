# Docker Compose Umgebung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Docker-Compose-Umgebung fuer isoliertes Testen und Produktions-Deployment der CTC-Manager-Anwendung mit MariaDB.

**Architecture:** Multi-Stage Dockerfile (Maven-Build + JRE-Runtime), zwei separate Compose-Files (lokal mit eingebetteter MariaDB, Produktion eigenstaendig mit externer DB). Eigenes Spring-Profil `docker` fuer Container-Netzwerk-Konfiguration.

**Tech Stack:** Docker, Docker Compose, MariaDB 11, Eclipse Temurin JDK/JRE 25, Spring Boot Actuator

---

## File Map

| Aktion | Datei | Zweck |
| --- | --- | --- |
| Create | `Dockerfile` | Multi-Stage Build: Maven → JRE Runtime |
| Create | `docker-compose.yml` | Lokale Umgebung: App + MariaDB |
| Create | `docker-compose.prod.yml` | Produktionsumgebung: Nur App mit externer DB |
| Create | `.dockerignore` | Build-Kontext minimieren |
| Create | `.env.example` | Dokumentation der Prod-Env-Variablen |
| Create | `src/main/resources/application-docker.yml` | Spring-Profil fuer Docker-Netzwerk |
| Modify | `pom.xml` | Spring Boot Actuator Dependency hinzufuegen |
| Modify | `src/main/resources/application.yml` | Actuator Health-Endpoint Konfiguration |

---

### Task 1: Spring Boot Actuator hinzufuegen

**Files:**
- Modify: `pom.xml:20-121` (dependencies)
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Actuator-Dependency in pom.xml hinzufuegen**

Nach dem `spring-boot-starter-webmvc` Block (Zeile 44) einfuegen:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
```

- [ ] **Step 2: Actuator-Konfiguration in application.yml hinzufuegen**

Am Ende von `src/main/resources/application.yml` anfuegen:

```yaml

# Actuator Health-Endpoint (fuer Docker Healthchecks)
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 3: Build pruefen**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS (keine Fehler)

- [ ] **Step 4: Tests ausfuehren**

Run: `./mvnw verify -q`
Expected: Alle Tests gruen, BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git commit -m "Spring Boot Actuator fuer Health-Endpoint hinzufuegen"
```

---

### Task 2: Spring-Profil `docker` erstellen

**Files:**
- Create: `src/main/resources/application-docker.yml`

- [ ] **Step 1: application-docker.yml erstellen**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mariadb://db:3306/ctcdb
    driver-class-name: org.mariadb.jdbc.Driver
    username: ctc
    password: ctc
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    locations: classpath:db/migration

app:
  upload-dir: /app/uploads

ctc:
  site:
    output-dir: /app/ctc-site-output
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application-docker.yml
git commit -m "Spring-Profil docker fuer Container-Netzwerk erstellen"
```

---

### Task 3: Dockerfile erstellen

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Dockerfile erstellen**

```dockerfile
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
```

- [ ] **Step 2: Commit**

```bash
git add Dockerfile
git commit -m "Multi-Stage Dockerfile mit Maven-Build und JRE-Runtime"
```

---

### Task 4: .dockerignore erstellen

**Files:**
- Create: `.dockerignore`

- [ ] **Step 1: .dockerignore erstellen**

```text
target/
.git/
.idea/
.vscode/
docs/
*.md
!mvnw
.env
.env.*
node_modules/
```

Hinweis: `!mvnw` stellt sicher, dass der Maven Wrapper nicht durch die allgemeine Ausschlussregel betroffen ist (mvnw hat keine Endung, aber sicherheitshalber).

- [ ] **Step 2: Commit**

```bash
git add .dockerignore
git commit -m ".dockerignore fuer schlanken Build-Kontext erstellen"
```

---

### Task 5: docker-compose.yml (Lokal) erstellen

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: docker-compose.yml erstellen**

```yaml
services:
  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: ctcdb
      MARIADB_USER: ctc
      MARIADB_PASSWORD: ctc
      MARIADB_ROOT_PASSWORD: root
    volumes:
      - ctc-db-data:/var/lib/mysql
    ports:
      - "3307:3306"
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  app:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: docker
    ports:
      - "8080:8080"
    volumes:
      - ctc-uploads:/app/uploads
      - ctc-site-output:/app/ctc-site-output
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

volumes:
  ctc-db-data:
  ctc-uploads:
  ctc-site-output:
```

- [ ] **Step 2: Commit**

```bash
git add docker-compose.yml
git commit -m "docker-compose.yml fuer lokale Umgebung mit MariaDB"
```

---

### Task 6: docker-compose.prod.yml (Produktion) erstellen

**Files:**
- Create: `docker-compose.prod.yml`
- Create: `.env.example`

- [ ] **Step 1: docker-compose.prod.yml erstellen**

```yaml
services:
  app:
    image: ctc-manager:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: ${DATABASE_URL}
      DATABASE_USERNAME: ${DATABASE_USERNAME}
      DATABASE_PASSWORD: ${DATABASE_PASSWORD}
    ports:
      - "8080:8080"
    volumes:
      - ctc-uploads:/app/uploads
      - ctc-site-output:/app/ctc-site-output
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

volumes:
  ctc-uploads:
  ctc-site-output:
```

- [ ] **Step 2: .env.example erstellen**

```env
# Produktions-Datenbank
DATABASE_URL=jdbc:mariadb://your-db-host:3306/ctcdb
DATABASE_USERNAME=ctc_prod
DATABASE_PASSWORD=changeme
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.prod.yml .env.example
git commit -m "docker-compose.prod.yml und .env.example fuer Produktions-Deployment"
```

---

### Task 7: Lokale Docker-Umgebung testen

- [ ] **Step 1: Docker-Image bauen und Container starten**

Run: `docker compose up --build -d`
Expected: Beide Services starten (db und app)

- [ ] **Step 2: Container-Status pruefen**

Run: `docker compose ps`
Expected: Beide Services `healthy` (ggf. 30-60 Sekunden warten)

- [ ] **Step 3: Actuator Health-Endpoint pruefen**

Run: `curl -s http://localhost:8080/actuator/health | python3 -m json.tool`
Expected: JSON mit `"status": "UP"` und DB-Komponente ebenfalls `UP`

- [ ] **Step 4: App-UI pruefen**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/`
Expected: HTTP 200 oder 302 (Redirect)

- [ ] **Step 5: MariaDB-Zugang pruefen**

Run: `docker compose exec db mariadb -u ctc -pctc ctcdb -e "SHOW TABLES;"`
Expected: Liste der Flyway-migrierten Tabellen (seasons, teams, drivers, etc.)

- [ ] **Step 6: Container stoppen**

Run: `docker compose down`
Expected: Alle Container gestoppt, Volumes bleiben erhalten

- [ ] **Step 7: CLAUDE.md aktualisieren**

In `CLAUDE.md` im Abschnitt "Befehle" die Docker-Befehle ergaenzen:

```bash
# Docker: Lokale Umgebung (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Nur Image bauen
docker compose build

# Docker: Produktion (externe DB, .env konfigurieren)
docker compose -f docker-compose.prod.yml up -d
```

- [ ] **Step 8: Commit**

```bash
git add CLAUDE.md
git commit -m "Docker-Befehle in CLAUDE.md dokumentieren"
```
