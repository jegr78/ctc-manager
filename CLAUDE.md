# CTC Manager

Community Team Cup — Gran Turismo Racing League Manager

## Tech Stack

- Java 25, Spring Boot 4.x, Maven
- Thymeleaf (Admin-UI), MariaDB/H2, Flyway
- JUnit 5, Mockito, Playwright, Jsoup

## Sprache

Deutsch für Kommunikation und Dokumentation. Code, Kommentare und UI-Texte auf Englisch.

## Befehle

```bash
# Dev-Modus starten
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev-Modus mit GT7-Demodaten (Autos, Strecken, Bilder)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Tests ausfuehren (Unit + Integration)
./mvnw verify

# Tests ausfuehren inkl. Playwright E2E
./mvnw verify -Pe2e

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

## Spring Profiles

- `dev` — H2 In-Memory, Port 9090 (Entwicklung, Tests)
- `dev,demo` — Wie `dev`, importiert beim Start alle GT7-Autos und -Strecken (mit Bildern) fuer manuelles Testen
- `local` — MariaDB lokal, Port 9091
- `docker` — MariaDB im Docker-Netzwerk (Host `db`), Port 8080
- `prod` — Cloud DB (Environment Variables)

## Package-Struktur

- `de.ctc.domain.model` — JPA Entities
- `de.ctc.domain.repository` — Spring Data Repositories
- `de.ctc.domain.service` — Geschaeftslogik (Scoring, Standings, Rankings)
- `de.ctc.admin.controller` — Admin CRUD Controller
- `de.ctc.admin.dto` — Form/Display DTOs
- `de.ctc.sitegen` — Statische Seitengenerierung
- `de.ctc.dataimport` — CSV/Bild-Import
- `de.ctc.gt7sync` — GT7 Auto/Strecken-Scraping und Sync

## Key Files

- `CtcManagerApplication.java` — Entry Point
- `TestDataService.java` — DevDataSeeder: Erstellt Teams, Seasons, Drivers, Scoring-Presets beim Start (dev-Profil)
- `ScoringService.java` — Punkteberechnung (konfigurierbar via RaceScoring) + Score-Aggregation auf Match/PlayoffMatchup
- `StandingsService.java` — Team-Tabelle (Match-basiert, nutzt MatchScoring)
- `V1__initial_schema.sql` — Konsolidiertes DB-Schema (noch nicht veroeffentlicht)
- `layout.html` — Thymeleaf Admin-Layout mit Sidebar (Fragment-Pattern: `th:replace="~{admin/layout :: layout(...)}"`)

## Architektur-Prinzipien

- **Keine Fallback-Berechnungen:** Wenn abgeleitete Daten fehlen, keine Workarounds in Templates oder Controllern einbauen. Stattdessen Datenmodell und Service-Architektur analysieren und die Ursache beheben — Daten muessen an der richtigen Stelle konsistent geschrieben werden.
- **Thymeleaf Templates schlank halten:** Keine komplexe Logik (SpEL-Expressions, Collection-Projektionen, verschachtelte Bedingungen) in Templates. Berechnungen und Datenaufbereitung gehoeren in den Controller oder Service — Templates nur fuer Darstellung.

## OSIV (Open Session in View)

Bewusst aktiviert (`spring.jpa.open-in-view=true`). Die Hibernate-Session bleibt bis zum Ende des HTTP-Requests offen, damit Thymeleaf lazy-geladene Felder rendern kann. Korrekt fuer diese Admin-Anwendung mit serverseitigem Rendering — kein Lazy-Init-Workaround in Controllern noetig.

## Entwicklungsansatz

- **TDD (Test-Driven Development):** Tests zuerst schreiben, dann Implementierung. Red → Green → Refactor.
- **BDD (Behavior-Driven Development):** Playwright E2E Tests beschreiben das erwartete Verhalten aus Nutzersicht.
- Reihenfolge bei neuen Features: Unit Tests → Implementierung → Integration Tests → E2E Tests
- Superpowers-Skill `superpowers:test-driven-development` nutzen

## Git-Workflow

- **Default-Branch:** `master`
- **Tooling:** `gh` CLI fuer alle GitHub-Operationen (PRs, Issues, etc.)
- **Branching:** Fuer jedes Feature/Fix einen eigenen Branch erstellen
  - Naming: `feature/<kurzbeschreibung>` oder `fix/<kurzbeschreibung>`
  - Branch von `master` abzweigen
- **Pull Requests:** Aenderungen immer ueber PRs in `master` mergen, kein direkter Push
  - `gh pr create --assignee jegr78` zum Erstellen (immer jegr78 zuweisen)
  - `gh pr merge --squash` zum Mergen (saubere History)
  - Nach Merge lokalen Branch aufraeumen: `git switch master && git pull && git branch -d <branch>`
- **Commits:** Aussagekraeftige deutsche Commit-Messages
- **Vor PR:**
  1. Tests lokal mit `./mvnw verify` sicherstellen
  2. Code-Review der eigenen Aenderungen durchfuehren (superpowers:code-reviewer)
  3. Gefundene Issues beheben und erneut testen
- **Nach PR:**
  1. CI-Build pruefen: `gh run list --branch <branch>` / `gh run view <run-id>`
  2. Bei CI-Failure: Logs analysieren (`gh run view --log-failed`), fixen, pushen
  3. PR darf erst gemergt werden wenn CI gruen ist

## References

- Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
- Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
