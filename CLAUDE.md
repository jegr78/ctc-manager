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

- `dev` — H2 In-Memory (Entwicklung, Tests)
- `local` — MariaDB lokal
- `prod` — Cloud DB (Environment Variables)

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

## Design Spec

`docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
