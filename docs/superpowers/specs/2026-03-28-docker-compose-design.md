# Docker Compose Umgebung — Design Spec

## Kontext

Die CTC-Manager-Anwendung soll in einer isolierten Docker-Umgebung lauffaehig sein — sowohl fuer lokales Testen (App + MariaDB im Container) als auch fuer spaetere Produktions-Deployments. Bisher gibt es keine Docker-Dateien im Projekt.

## Entscheidungen

- **Ansatz:** Zwei separate Compose-Files (lokal + Prod-Override)
- **Build:** Multi-Stage Dockerfile (Maven-Build + JRE-Runtime)
- **DB-Konfiguration:** Eigenes Spring-Profil `docker` (statt Env-Variable-Override)
- **Volumes:** Named Volumes fuer DB-Daten, Uploads und Site-Output

## Neue Dateien

| Datei | Zweck |
|---|---|
| `Dockerfile` | Multi-Stage Build: Maven → JRE Runtime |
| `docker-compose.yml` | Lokale Umgebung: App + MariaDB |
| `docker-compose.prod.yml` | Prod-Override: Externe DB, Port 8080 |
| `.dockerignore` | Build-Kontext minimieren |
| `.env.example` | Dokumentation der Prod-Env-Variablen |
| `src/main/resources/application-docker.yml` | Spring-Profil fuer Docker-Netzwerk |

## Neue Dependency

### Spring Boot Actuator

`spring-boot-starter-actuator` wird als Dependency in `pom.xml` hinzugefuegt. Damit steht `/actuator/health` als Healthcheck-Endpoint bereit, der auch den DB-Verbindungsstatus prueft.

Konfiguration in `application.yml` (global, fuer alle Profile):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

## Dockerfile

### Stage 1: Build

- **Base Image:** `eclipse-temurin:25-jdk` (oder fruehestes verfuegbares JDK 25 Image)
- **Working Dir:** `/build`
- **Dependency Caching:** Zuerst nur `pom.xml` und `.mvn/` kopieren, dann `./mvnw dependency:go-offline` ausfuehren. Dadurch werden Dependencies gecached und nur bei pom.xml-Aenderungen neu geladen.
- **Source Copy:** Danach `src/` kopieren
- **Build:** `./mvnw package -DskipTests` erzeugt das executable JAR

### Stage 2: Runtime

- **Base Image:** `eclipse-temurin:25-jre` (schlanker, kein Compiler)
- **Zusaetzliche Pakete:** `curl` installieren (fuer Healthcheck)
- **Working Dir:** `/app`
- **User:** Non-root User `ctc` (UID 1000) fuer Sicherheit
- **Verzeichnisse:** `/app/uploads` und `/app/ctc-site-output` erstellen, Ownership an `ctc`
- **JAR:** Aus Stage 1 kopieren nach `/app/ctc-manager.jar`
- **Entrypoint:** `java -jar ctc-manager.jar`
- **Expose:** Port 8080 (Default-Port)

## Spring-Profil: docker

Neues `application-docker.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mariadb://db:3306/ctcdb
    username: ctc
    password: ctc
    driver-class-name: org.mariadb.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    locations: classpath:db/migration

app:
  upload-dir: /app/uploads

ctc:
  site:
    output-dir: /app/ctc-site-output
```

**Wichtig:** DB-Host ist `db` — der Service-Name im Docker-Netzwerk.

## docker-compose.yml (Lokal)

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

### MariaDB-Initialisierung

Das offizielle MariaDB-Image erstellt beim ersten Start automatisch:
- Datenbank `ctcdb` (via `MARIADB_DATABASE`)
- User `ctc` mit Passwort `ctc` (via `MARIADB_USER` / `MARIADB_PASSWORD`)
- Volle Rechte fuer User `ctc` auf `ctcdb`

Kein Init-Script noetig. Flyway uebernimmt die Schema-Migration beim App-Start.

## docker-compose.prod.yml (Eigenstaendig)

Eigenstaendiges Compose-File fuer Produktion — kein Override, kein db-Service:

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

**Aufruf:**

```bash
# Image vorher bauen
docker compose build

# Produktion starten (mit .env oder exportierten Variablen)
docker compose -f docker-compose.prod.yml up
```

Das Image wird lokal mit `docker compose build` (aus `docker-compose.yml`) gebaut und dann im Prod-File als `ctc-manager:latest` referenziert.

## .dockerignore

```text
target/
.git/
.idea/
.vscode/
docs/
*.md
.env
.env.*
node_modules/
```

## .env.example

```env
# Produktions-Datenbank
DATABASE_URL=jdbc:mariadb://your-db-host:3306/ctcdb
DATABASE_USERNAME=ctc_prod
DATABASE_PASSWORD=changeme
```

## Upload/Site-Output Pfade

Das `docker`-Profil setzt absolute Pfade (`/app/uploads`, `/app/ctc-site-output`). Diese muessen mit den bestehenden Properties harmonieren:

- In `application.yml` steht aktuell `upload-dir: uploads` (relativ) und `site-output-dir: ${user.home}/ctc-site-output`
- Das `docker`-Profil ueberschreibt diese mit absoluten Container-Pfaden
- Die Volume-Mounts im Compose-File zeigen auf dieselben Pfade

Die Properties existieren bereits als `app.upload-dir` (Default: `uploads`) und `ctc.site.output-dir` (Default: `${user.home}/ctc-site-output`). Das docker-Profil ueberschreibt sie mit absoluten Container-Pfaden.

## Verifikation

1. **Lokaler Test:**

   ```bash
   docker compose up --build
   # App erreichbar unter http://localhost:8080
   # MariaDB erreichbar unter localhost:3307
   ```

2. **Healthchecks pruefen:** `docker compose ps` zeigt beide Services als `healthy`
3. **Actuator pruefen:** `curl http://localhost:8080/actuator/health` zeigt DB-Status
4. **DB pruefen:** Via MariaDB-Client (`mysql -h 127.0.0.1 -P 3307 -u ctc -p ctcdb`) verifizieren, dass Flyway-Migrationen gelaufen sind
5. **Uploads testen:** Datei hochladen, Container neu starten, pruefen ob Datei noch da ist
6. **Site-Output:** Statische Seite generieren, pruefen ob Output im Volume landet
7. **Prod-Modus:** `docker compose -f docker-compose.prod.yml up` mit gesetzten Env-Variablen testen
