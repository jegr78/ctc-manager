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

# Tests ausfuehren (Unit + Integration + JaCoCo Coverage)
./mvnw verify

# Tests ausfuehren inkl. Playwright E2E
./mvnw verify -Pe2e

# Coverage-Report oeffnen
open target/site/jacoco/index.html

# Local mit MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Playwright Chromium installieren (fuer Team Card Generierung + E2E Tests)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

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

- `org.ctc.domain.model` — JPA Entities
- `org.ctc.domain.repository` — Spring Data Repositories
- `org.ctc.domain.service` — Geschaeftslogik (Scoring, Standings, Rankings)
- `org.ctc.admin.controller` — Admin CRUD Controller
- `org.ctc.admin.dto` — Form/Display DTOs
- `org.ctc.admin.service` — Graphic Services (Playwright-basierte Bildgenerierung)
- `org.ctc.sitegen` — Statische Seitengenerierung
- `org.ctc.dataimport` — CSV/Bild-Import
- `org.ctc.gt7sync` — GT7 Auto/Strecken-Scraping und Sync

## Key Files

- `CtcManagerApplication.java` — Entry Point
- `TestDataService.java` — DevDataSeeder: Erstellt Teams, Seasons, Drivers, Scoring-Presets beim Start (dev-Profil)
- `ScoringService.java` — Punkteberechnung (konfigurierbar via RaceScoring) + Score-Aggregation auf Match/PlayoffMatchup + isDriverInTeam() (RaceLineup Source of Truth)
- `StandingsService.java` — Team-Tabelle (Match-basiert, nutzt MatchScoring)
- `SeasonManagementService.java` — Team/Car/Track-Pool-Verwaltung fuer Seasons
- `TeamManagementService.java` — Team-Detail-Daten, Farb/Logo-Propagation an Sub-Teams
- `BaseEntity.java` — @MappedSuperclass mit createdAt/updatedAt (JPA Auditing)
- `V1__initial_schema.sql` — Konsolidiertes DB-Schema (eingefroren seit v1.0.0)
- `layout.html` — Thymeleaf Admin-Layout mit Sidebar (Fragment-Pattern: `th:replace="~{admin/layout :: layout(...)}"`)

## Architektur-Prinzipien

- **Controller duenn halten:** Controller sind nur fuer HTTP-Handling zustaendig (Request annehmen, Service aufrufen, Model/Redirect/Flash befuellen). Keine Business-Logik, keine direkten Repository-Zugriffe in Controllern. Geschaeftslogik gehoert in Service-Klassen (`domain.service` oder `admin.service`).
- **DTOs statt Entities in Controllern:** Form-Eingaben (POST/save) immer ueber Form-DTOs (`admin.dto`) binden, nie JPA Entities direkt per `@ModelAttribute` — Schutz gegen Mass Assignment. Fuer Template-Anzeige (GET) duerfen Entities ans Model uebergeben werden (OSIV ist aktiv).
- **Keine Fallback-Berechnungen:** Wenn abgeleitete Daten fehlen, keine Workarounds in Templates oder Controllern einbauen. Stattdessen Datenmodell und Service-Architektur analysieren und die Ursache beheben — Daten muessen an der richtigen Stelle konsistent geschrieben werden.
- **Thymeleaf Templates schlank halten:** Keine komplexe Logik (SpEL-Expressions, Collection-Projektionen, verschachtelte Bedingungen) in Templates. Berechnungen und Datenaufbereitung gehoeren in den Service — Templates nur fuer Darstellung.
- **Keine Inline-Styles auf Buttons:** Statt `style="..."` auf `.btn`-Elementen immer CSS-Klassen aus `admin.css` verwenden (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). Beim Refactoring von Inline-Styles zu CSS-Klassen immer auch JavaScript pruefen, das `element.className = '...'` setzt — dort muessen die neuen Klassen ebenfalls ergaenzt werden.
- **Testdaten komplett isolieren:** E2E-Testdaten in `TestDataService` muessen eigene Entities mit Test-Prefix verwenden (z.B. `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). Nie echte Teams, Fahrer oder Saisons fuer automatisierte Tests nutzen — diese kollidieren mit manuellen Tests auf Import-Daten.
- **RaceLineup ist Source of Truth:** Fuer Fahrer-Team-Zuordnungen (insb. Sub-Teams) immer `RaceLineup` priorisieren, `SeasonDriver` nur als Fallback fuer Saisons ohne Rennen. Der CSV-Import bestimmt die korrekte Zuordnung.
- **Flyway Migrationen nicht aendern:** Bestehende `V*__*.sql` Dateien duerfen nach Release nie mehr geaendert werden (Flyway prueft Checksummen). Schema-Aenderungen immer als neue Migrationsdatei: `V{N}__{kurzbeschreibung}.sql` (snake_case, englisch). H2 + MariaDB Kompatibilitaet beachten.

## OSIV (Open Session in View)

Bewusst aktiviert (`spring.jpa.open-in-view=true`). Die Hibernate-Session bleibt bis zum Ende des HTTP-Requests offen, damit Thymeleaf lazy-geladene Felder rendern kann. Korrekt fuer diese Admin-Anwendung mit serverseitigem Rendering — kein Lazy-Init-Workaround in Controllern noetig.

## Entwicklungsansatz

- **TDD (Test-Driven Development):** Tests zuerst schreiben, dann Implementierung. Red → Green → Refactor.
- **BDD (Behavior-Driven Development):** Playwright E2E Tests beschreiben das erwartete Verhalten aus Nutzersicht.
- **Test-Naming (Given-When-Then):** Alle Testmethoden folgen dem BDD-Pattern:
  - Methodenname: `givenContext_whenAction_thenExpectedResult()`
  - Body: `// given` / `// when` / `// then` Kommentare zur Strukturierung
  - Bei einfachen Tests ohne Precondition: `whenAction_thenResult()` erlaubt
  - Bei Exception-Tests: `// when / then` kombiniert fuer assertThatThrownBy
- Reihenfolge bei neuen Features: Unit Tests → Implementierung → Integration Tests → E2E Tests
- Superpowers-Skill `superpowers:test-driven-development` nutzen
- **Visuelle Pruefung mit `playwright-cli`:** Bei UI-Aenderungen (Templates, CSS) immer `playwright-cli` nutzen um das Ergebnis visuell zu verifizieren. Dev-Server starten (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), dann mit `playwright-cli open http://localhost:9090/...` die betroffenen Seiten inspizieren (Desktop + Mobile). Skill: `/playwright-cli`

## Code Coverage (JaCoCo)

- **Minimum:** 80% Line Coverage (Build bricht bei Unterschreitung)
- **Report:** `target/site/jacoco/index.html` nach `./mvnw verify`
- **CI:** Automatischer PR-Kommentar mit Coverage via `madrapps/jacoco-report`
- **Excludes:** CtcManagerApplication, TestDataService, DemoDataSeeder, TeamCardService, LineupGraphicService, ResultsGraphicService, SettingsGraphicService, OverlayGraphicService, AbstractGraphicService (Playwright-abhaengig)
- **Schwellwert anpassen:** Erst messen (`jacoco.csv`), dann Minimum setzen — nie optimistisch raten

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
- **Commits:** Englische Commit-Messages mit Conventional Commits Prefixen:
  - `feat:` — Neues Feature (Minor-Bump)
  - `fix:` — Bugfix (Patch-Bump)
  - `docs:` — Dokumentation (Patch-Bump)
  - `chore:` — Maintenance (Patch-Bump)
  - `refactor:` — Refactoring (Patch-Bump)
  - `test:` — Tests (Patch-Bump)
  - `style:` — Formatting/CSS (Patch-Bump)
  - `perf:` — Performance (Patch-Bump)
  - `ci:` — CI/CD (kein Release)
  - `BREAKING CHANGE` im Footer → Major-Bump
  - Format: `<type>(<optional scope>): <description>`
  - Beispiel: `feat(scoring): add penalty point deduction`
- **Vor PR:**
  1. Tests lokal mit `./mvnw verify` sicherstellen
  2. Code-Review der eigenen Aenderungen durchfuehren (superpowers:code-reviewer)
  3. Gefundene Issues beheben und erneut testen
- **Nach PR:**
  1. CI-Build pruefen: `gh run list --branch <branch>` / `gh run view <run-id>`
  2. Bei CI-Failure: Logs analysieren (`gh run view --log-failed`), fixen, pushen
  3. PR darf erst gemergt werden wenn CI gruen ist

## Subagent-Regeln

- **Modellwahl:** Implementierungs-Subagents immer mit `model: "opus"` oder mindestens `model: "sonnet"`. Haiku NUR fuer Read-Only-Tasks (Reviews, Recherche). Nie fuer Code-Aenderungen.
- **Branch-Schutz:** Jeder Subagent-Prompt muss den aktiven Branch benennen und explizit verbieten: kein `git stash`, `git checkout`, `git reset`, kein Branch-Wechsel.
- **Post-Dispatch-Validierung:** Nach JEDEM Subagent sofort pruefen: `git branch --show-current`, `git log --oneline -3`, `git diff --stat`. Bei Abweichung sofort `git reset --hard` auf letzten guten Commit.
- **Plan-Treue:** Subagent-Prompt muss explizit sagen: "Implementiere NUR Task N. Wenn andere Dateien angepasst werden muessen, melde NEEDS_CONTEXT statt selbst zu fixen."
- **Atomare Tasks:** Tasks im Plan muessen einzeln lauffaehig sein. Wenn eine Aenderung mehrere Tasks erzwingt, als einen Task planen.
- **Fallback:** Wenn Subagents trotz Regeln Probleme machen, sequentiell selbst abarbeiten.

## References

- Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
- Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
- Release Management Spec: `docs/superpowers/specs/2026-04-03-release-management-design.md`

<!-- GSD:project-start source:PROJECT.md -->
## Project

**CTC Manager — Technical Debt Cleanup**

Systematische Bereinigung aller technischen Schulden im CTC Manager, einer Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Ziel ist eine saubere Codebasis als Grundlage fuer zukuenftige Feature-Entwicklung.

**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

### Constraints

- **Testabdeckung**: 82% Line Coverage Minimum darf nicht unterschritten werden
- **Flyway**: Bestehende V1 Migration nicht aendern, nur neue V2+ Migrationen
- **Profile**: Auth nur fuer prod/docker, dev/local bleiben ohne Auth
- **OSIV**: Bleibt aktiviert — nur @EntityGraph-Annotationen als Optimierung
- **Abwaertskompatibilitaet**: Keine Breaking Changes an bestehenden URLs/Endpoints
- **Playwright**: Bleibt Compile-Scope Dependency (Runtime-Nutzung fuer Graphics)
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

- **Runtime:** Java 25 (Eclipse Temurin), Maven via `./mvnw`, Spring Boot 4.x
- **DB:** MariaDB (prod/local/docker), H2 (dev/test), Flyway Migrations
- **UI:** Thymeleaf (server-side rendering), kein Frontend-Build-Tool
- **Testing:** JUnit 5, Mockito, Playwright (E2E + Grafik-Generierung)
- **Externe APIs:** Google Sheets (Race-Import), Google Calendar, Jsoup (GT7-Scraping)
- **Build:** Surefire (Unit/Integration), Failsafe + `-Pe2e` (E2E), JaCoCo (Coverage)

## Configuration

- Profile-spezifisch: `application-{dev,local,docker,prod}.yml`
- Prod-Env-Vars: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`
- Google Credentials: `google.sheets.credentials-path` (default: `google-credentials.json`)
- Upload-Dir: `app.upload-dir` (default: `data/dev/uploads`)
- Site-Output: `ctc.site.output-dir` (default: `docs/site`)

## CI/CD

- Push/PR to `master`: Build, Tests, Playwright E2E, JaCoCo Coverage PR-Kommentar
- Push `docs/site/**`: GitHub Pages Deployment
- Docker: Multi-stage Dockerfile (JDK build, JRE runtime), non-root user `ctc`, Healthcheck `/actuator/health`
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns

- Domain layer: `org.ctc.domain.model`, `org.ctc.domain.repository`, `org.ctc.domain.service`
- Admin layer: `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`
- Feature modules: `org.ctc.dataimport`, `org.ctc.gt7sync`, `org.ctc.sitegen`
- Entities: singular nouns, PascalCase (`Season`, `RaceScoring`, `PlayoffMatchup`)
- Repositories: `{Entity}Repository` — Services: `{Domain}Service` / `{Domain}ManagementService`
- Controllers: `{Entity}Controller` — DTOs: `{Entity}Form` (form), `{Entity}Dto` (display), `{Entity}Data` (records)
- Methods: camelCase, verb-first (`calculatePoints()`, `addTeamToSeason()`)
- Boolean getters: `isSubTeam()`, `hasSubTeams()`, `isActive()`, `canParse()`
- Variables: camelCase, `var` for obvious types, UUID params `id` (path) / `{entity}Id` (request)
- DB: plural snake_case tables (`seasons`), snake_case columns (`created_at`), join tables `{parent}_{child}`

## Lombok Usage

- Entities: `@Getter @Setter @NoArgsConstructor`, `@ToString(exclude = ...)`, extend `BaseEntity`
- Services/Controllers: `@RequiredArgsConstructor` (constructor injection via `final`), `@Slf4j`

## Controller & DTO Patterns

- Form DTOs mit `@Valid` + `BindingResult`, Entities direkt in GET (OSIV aktiv)
- Flash attributes: `"successMessage"` / `"errorMessage"`

## Error Handling

- `IllegalStateException` for business rules, `IllegalArgumentException` for invalid input
- `orElseThrow()` for entity lookups — keine custom Exceptions
- Controllers fangen `IllegalStateException`, konvertieren zu Flash-Fehlermeldung

## Logging

- `log.info()` fuer State Changes, `log.debug()` fuer Berechnungen
- Immer parameterized `{}`, nie String-Concatenation

## CSS Guidelines

- CSS-Klassen aus `admin.css` statt inline styles: `btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`
- Bei Refactoring: JavaScript `element.className = '...'` mitpruefen
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview

- Three-tier: Controller -> Service -> Repository -> Database
- No REST API -- server-rendered HTML with form submissions and redirects (POST-Redirect-GET)
- Graphic Services: Thymeleaf HTML -> Playwright screenshot -> PNG (`AbstractGraphicService` base class)
- Two-phase import/sync pattern: parse+preview then confirm+execute (CsvImport, Gt7Sync)
- Static site generation: domain data -> Thymeleaf -> HTML files in `docs/site/`

## Key Abstractions

- `BaseEntity` — `@MappedSuperclass` with `createdAt`/`updatedAt` (JPA Auditing), all entities extend this
- `AbstractGraphicService` / `AbstractMatchdayGraphicService` — base classes for Playwright-based image generation
- `RaceScoring` / `MatchScoring` — scoring rules decoupled from code, stored as comma-separated strings in DB
- `SeasonTeam` — join table with per-season rating, color/logo overrides, team succession tracking
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
