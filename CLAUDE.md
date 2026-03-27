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
- `local` — MariaDB lokal, Port 9091
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

## Befehle

```bash
# Dev-Modus starten
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Tests ausführen
./mvnw verify

# Local mit MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Git-Workflow

- **Default-Branch:** `master`
- **Tooling:** `gh` CLI für alle GitHub-Operationen (PRs, Issues, etc.)
- **Branching:** Für jedes Feature/Fix einen eigenen Branch erstellen
  - Naming: `feature/<kurzbeschreibung>` oder `fix/<kurzbeschreibung>`
  - Branch von `master` abzweigen
- **Pull Requests:** Änderungen immer über PRs in `master` mergen, kein direkter Push
  - `gh pr create` zum Erstellen
  - `gh pr merge --squash` zum Mergen (saubere History)
  - Nach Merge lokalen Branch aufräumen: `git switch master && git pull && git branch -d <branch>`
- **Commits:** Aussagekräftige deutsche Commit-Messages
- **Vor PR:** Tests mit `./mvnw verify` sicherstellen

## Design Spec

`docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
