# CTC Manager

Community Team Cup — Gran Turismo Racing League Manager

## Projekt

Spring Boot 4.x Admin-Anwendung zur Verwaltung der CTC Rennliga. Generiert eine statische Webseite für GitHub Pages.

## Tech Stack

- Java 25, Spring Boot 4.x, Maven
- Thymeleaf (Admin-UI), MariaDB/H2, Flyway
- JUnit 5, Mockito, Playwright, Jsoup

## Sprache

Deutsch für Kommunikation und Dokumentation. Code und Kommentare auf Englisch.

## Spring Profiles

- `dev` — H2 In-Memory, Port 9090 (Entwicklung, Tests)
- `dev,demo` — Wie `dev`, importiert beim Start alle GT7-Autos und -Strecken (mit Bildern) fuer manuelles Testen
- `local` — MariaDB lokal, Port 9091
- `docker` — MariaDB im Docker-Netzwerk (Host `db`), Port 8080
- `prod` — Cloud DB (Environment Variables)

## OSIV (Open Session in View)

Bewusst aktiviert (`spring.jpa.open-in-view=true`). Die Hibernate-Session bleibt bis zum Ende des HTTP-Requests offen, damit Thymeleaf lazy-geladene Felder rendern kann. Korrekt fuer diese Admin-Anwendung mit serverseitigem Rendering — kein Lazy-Init-Workaround in Controllern noetig.

## Package-Struktur

- `de.ctc.domain.model` — JPA Entities
- `de.ctc.domain.repository` — Spring Data Repositories
- `de.ctc.domain.service` — Geschäftslogik (Scoring, Standings, Rankings)
- `de.ctc.admin.controller` — Admin CRUD Controller
- `de.ctc.admin.dto` — Form/Display DTOs
- `de.ctc.sitegen` — Statische Seitengenerierung
- `de.ctc.dataimport` — CSV/Bild-Import
- `de.ctc.gt7sync` — GT7 Auto/Strecken-Scraping und Sync

## Befehle

```bash
# Dev-Modus starten
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev-Modus mit GT7-Demodaten (Autos, Strecken, Bilder)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Tests ausführen
./mvnw verify

# Local mit MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Docker: Lokale Umgebung (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Nur Image bauen
docker compose build

# Docker: Produktion (externe DB, .env konfigurieren)
docker compose -f docker-compose.prod.yml up -d
```

## Git-Workflow

- **Default-Branch:** `master`
- **Tooling:** `gh` CLI für alle GitHub-Operationen (PRs, Issues, etc.)
- **Branching:** Für jedes Feature/Fix einen eigenen Branch erstellen
  - Naming: `feature/<kurzbeschreibung>` oder `fix/<kurzbeschreibung>`
  - Branch von `master` abzweigen
- **Pull Requests:** Änderungen immer über PRs in `master` mergen, kein direkter Push
  - `gh pr create --assignee jegr78` zum Erstellen (immer jegr78 zuweisen)
  - `gh pr merge --squash` zum Mergen (saubere History)
  - Nach Merge lokalen Branch aufräumen: `git switch master && git pull && git branch -d <branch>`
- **Commits:** Aussagekräftige deutsche Commit-Messages
- **Vor PR:**
  1. Tests lokal mit `./mvnw verify` sicherstellen
  2. Code-Review der eigenen Änderungen durchführen (superpowers:code-reviewer)
  3. Gefundene Issues beheben und erneut testen
- **Nach PR:**
  1. CI-Build prüfen: `gh run list --branch <branch>` / `gh run view <run-id>`
  2. Bei CI-Failure: Logs analysieren (`gh run view --log-failed`), fixen, pushen
  3. PR darf erst gemergt werden wenn CI grün ist

## Design Spec

`docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
