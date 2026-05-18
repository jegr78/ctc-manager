# CTC Manager

Community Team Cup — Gran Turismo Racing League Manager

---

## Language

* **Communication:** German
* **Documentation, Code, Comments, and UI Texts:** English

## Constraints

* **Test Coverage:** Minimum 82% line coverage must be maintained.
* **Flyway:** Do not change the existing V1 migration; only new V2+ migrations.
* **Profiles:** Auth only for `prod`/`docker`; `dev`/`local` remains without auth.
* **OSIV:** Remains enabled — only use `@EntityGraph` annotations for optimization.
* **Backward Compatibility:** No breaking changes to existing URLs/endpoints.
* **Playwright:** Remains a compile-scope dependency (Runtime usage for graphics).

## Technology Stack

* **Runtime:** Java 25 (Eclipse Temurin), Maven via `./mvnw`, Spring Boot 4.x
* **DB:** MariaDB (prod/local/docker), H2 (dev/test), Flyway Migrations
* **UI:** Thymeleaf (server-side rendering), no frontend build tool
* **Testing:** JUnit 5, Mockito, Playwright (E2E + Graphic generation)
* **External APIs:** Google Sheets (Race Import), Google Calendar, Jsoup (GT7 Scraping)
* **Build:** Surefire (Unit/Integration), Failsafe + `-Pe2e` (E2E), JaCoCo (Coverage)

## Commands

```bash
# Start Dev Mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev Mode with GT7 demo data (cars, tracks, images)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Run tests (Unit + Integration + JaCoCo Coverage)
./mvnw verify

# Run tests including Playwright E2E
./mvnw verify -Pe2e

# OpenRewrite: preview recipe-driven refactoring (no file changes)
./mvnw -Prewrite rewrite:dryRun

# OpenRewrite: apply recipes to source files in place
./mvnw -Prewrite rewrite:run

# Open Coverage Report
open target/site/jacoco/index.html

# Local with MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Install Playwright Chromium (for Team Card generation + E2E tests)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Docker: Local environment (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Build image only
docker compose build

# Docker: Production (external DB, configure .env)
docker compose -f docker-compose.prod.yml up -d
```

## Spring Profiles

* `dev` — H2 In-Memory, Port 9090 (Development, Testing)
* `dev,demo` — Same as `dev`, imports all GT7 cars and tracks (with images) on startup for manual testing.
* `local` — Local MariaDB, Port 9091
* `docker` — MariaDB within the Docker network (Host `db`), Port 8080
* `prod` — Cloud DB (Environment Variables)

## Architectural Principles

* **Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.
* **Keep Controllers Thin:** Controllers are only responsible for HTTP handling (receiving requests, calling services, filling models/redirects/flash attributes). No business logic or direct repository access in controllers. Business logic belongs in service classes (`domain.service` or `admin.service`).
* **DTOs instead of Entities in Controllers:** Always bind form inputs (POST/save) via Form DTOs (`admin.dto`), never JPA entities directly via `@ModelAttribute` — protection against Mass Assignment. For template display (GET), entities may be passed to the model (OSIV is active).
* **No Fallback Calculations:** If derived data is missing, do not implement workarounds in templates or controllers. Instead, analyze the data model and service architecture and fix the root cause — data must be written consistently in the correct place.
* **Keep Thymeleaf Templates Lean:** No complex logic (SpEL expressions, collection projections, nested conditions) in templates. Calculations and data preparation belong in the service — templates are for presentation only.
* **No Inline Styles on Buttons:** Instead of `style="..."` on `.btn` elements, always use CSS classes from `admin.css` (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). When refactoring inline styles to CSS classes, always check JavaScript that sets `element.className = '...'` — the new classes must be added there as well.
* **Isolate Test Data Completely:** E2E test data in `TestDataService` must use separate entities with a test prefix (e.g., `T-ALF`, `Test_Alpha_1`, `Test-Season 2026`). Never use real teams, drivers, or seasons for automated tests — these collide with manual tests on imported data.
* **Tag Tests by Category (`@Tag`):** Surefire/Failsafe routing is `@Tag`-based, not filename-based. Every new test class MUST be tagged: `@Tag("integration")` for `*IT.java` (Spring-context ITs), `@Tag("e2e")` for Playwright tests in `org.ctc.e2e.*`. Plain unit tests stay untagged. `@Nested` inner classes inherit their parent's tag. See `.planning/codebase/TESTING.md` "Test Categorization (`@Tag`)" for the full convention. An untagged `*IT.java` will silently run in Surefire under the wrong fork config and may race on shared state.
* **RaceLineup is Source of Truth:** For driver-team assignments (especially sub-teams), always prioritize `RaceLineup`; use `SeasonDriver` only as a fallback for seasons without races. The CSV import determines the correct assignment.
* **Do Not Modify Flyway Migrations:** Existing `V*__*.sql` files must never be changed after release (Flyway checks checksums). Schema changes must always be a new migration file: `V{N}__{short_description}.sql` (snake_case, English). Maintain H2 + MariaDB compatibility.

## OSIV (Open Session in View)

Deliberately enabled (`spring.jpa.open-in-view=true`). The Hibernate session remains open until the end of the HTTP request so that Thymeleaf can render lazy-loaded fields. This is appropriate for this admin application using server-side rendering — no lazy-init workarounds in controllers are necessary.

## Development Approach

* **TDD (Test-Driven Development):** Write tests first, then implementation. Red → Green → Refactor.
* **BDD (Behavior-Driven Development):** Playwright E2E tests describe expected behavior from the user's perspective.
* **Test Naming (Given-When-Then):** All test methods follow the BDD pattern:
  * Method name: `givenContext_whenAction_thenExpectedResult()`
  * Body: `// given` / `// when` / `// then` comments for structuring.
  * For simple tests without preconditions: `whenAction_thenResult()` is allowed.
  * For exception tests: `// when / then` combined for `assertThatThrownBy`.
* **Feature Sequence:** Unit Tests → Implementation → Integration Tests → E2E Tests.
* Use Superpowers skill `superpowers:test-driven-development`.
* **Visual Verification with `playwright-cli`:** For UI changes (templates, CSS), always use `playwright-cli` to verify results visually. Start the dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), then inspect affected pages with `playwright-cli open http://localhost:9090/...` (Desktop + Mobile). Skill: `/playwright-cli`.

## Code Coverage (JaCoCo)

* **Minimum:** documented in `pom.xml`
* **Report:** `target/site/jacoco/index.html` after running `./mvnw verify`.
* **CI:** Automatic PR comment with coverage via `madrapps/jacoco-report`.
* **Adjusting Threshold:** Measure first (`jacoco.csv`), then set the minimum — never guess optimistically.

## Git Workflow

* **Default Branch:** `master`
* **Tooling:** `gh` CLI for all GitHub operations (PRs, Issues, etc.).
* **Branching:** Create a separate branch for every feature/fix.
  * Naming: `feature/<short-description>` or `fix/<short-description>`.
  * Always fetch before branching: `git fetch origin && git checkout -b <branch> origin/master`.
  * Never branch from a local `master` that may be behind remote.
* **Pull Requests:** Always merge changes via PRs into `master`; no direct pushes.
  * `gh pr create --assignee jegr78` to create (always assign to jegr78).
  * `gh pr merge --squash` to merge (keeps history clean).
  * After merge, clean up local branch: `git switch master && git pull && git branch -d <branch>`.
* **Commits:** English commit messages using Conventional Commits prefixes:
  * `feat:` — New feature (Minor bump)
  * `fix:` — Bugfix (Patch bump)
  * `docs:` — Documentation (Patch bump)
  * `chore:` — Maintenance (Patch bump)
  * `refactor:` — Refactoring (Patch bump)
  * `test:` — Tests (Patch bump)
  * `style:` — Formatting/CSS (Patch bump)
  * `perf:` — Performance (Patch bump)
  * `ci:` — CI/CD (No release)
  * `BREAKING CHANGE` in footer → Major bump.
  * Format: `<type>(<optional scope>): <description>`
  * Example: `feat(scoring): add penalty point deduction`
* **Before PR:**
    1. Ensure tests pass locally with `./mvnw verify`.
    2. Perform a code review of your own changes (`superpowers:code-reviewer`).
    3. Fix found issues and re-test.
* **After PR:**
    1. Check CI build: `gh run list --branch <branch>` / `gh run view <run-id>`.
    2. In case of CI failure: Analyze logs (`gh run view --log-failed`), fix, and push.
    3. PR may only be merged once CI is green.

## Subagent Rules

* **Model Selection:** Implementation subagents must use `model: "opus"` or at least `model: "sonnet"`. Haiku is ONLY for read-only tasks (reviews, research). Never for code changes.
* **Branch Protection:** Every subagent prompt must name the active branch and explicitly forbid: no `git stash`, `git checkout`, `git reset`, and no branch switching.
* **Post-Dispatch Validation:** Immediately after EVERY subagent, check: `git branch --show-current`, `git log --oneline -3`, `git diff --stat`. If there is a discrepancy, immediately `git reset --hard` to the last good commit.
* **Plan Adherence:** Subagent prompts must explicitly state: "Implement ONLY Task N. If other files need adjustment, report `NEEDS_CONTEXT` instead of fixing them yourself."
* **Atomic Tasks:** Tasks in the plan must be individually executable. If a change forces multiple tasks, plan them as a single task.
* **Fallback:** If subagents cause issues despite rules, process tasks sequentially yourself.

## References

* Design Spec: `docs/superpowers/specs/2026-03-26-ctc-manager-design.md`
* Scoring/Legs Spec: `docs/superpowers/specs/2026-03-29-scoring-legs-design.md`
* Release Management Spec: `docs/superpowers/specs/2026-04-03-release-management-design.md`
* Architecture: `.planning/codebase/ARCHITECTURE.md`
* Conventions: `.planning/codebase/CONVENTIONS.md`
* Stack: `.planning/codebase/STACK.md`
* Structure: `.planning/codebase/STRUCTURE.md`
* Testing: `.planning/codebase/TESTING.md`
* SAST Acceptance: `docs/security/sast-acceptance.md`
* SAST Workflow: `.github/workflows/codeql.yml`

---

## Configuration

* **Profile-specific:** `application-{dev,local,docker,prod}.yml`
* **Prod Env Vars:** `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID`
* **Google Credentials:** `google.sheets.credentials-path` (default: `google-credentials.json`)
* **Upload Dir:** `app.upload-dir` (default: `data/dev/uploads`)
* **Site Output:** `ctc.site.output-dir` (default: `docs/site`)

## CI/CD

* **Push/PR to `master`:** Build, Tests, Playwright E2E, JaCoCo Coverage PR comment.
* **Push `docs/site/**`:** GitHub Pages Deployment.
* **Docker:** Multi-stage Dockerfile (JDK build, JRE runtime), non-root user `ctc`, healthcheck `/actuator/health`.

---

## Conventions

### Naming Patterns

* **Layers:** `org.ctc.domain.model`, `org.ctc.domain.repository`, `org.ctc.domain.service`, `org.ctc.admin.controller`, `org.ctc.admin.dto`, `org.ctc.admin.service`.
* **Entities:** Singular nouns, PascalCase (`Season`, `RaceScoring`).
* **Repositories:** `{Entity}Repository` — Services: `{Domain}Service`.
* **Controllers:** `{Entity}Controller` — DTOs: `{Entity}Form` (form), `{Entity}Dto` (display).
* **Methods:** camelCase, verb-first (`calculatePoints()`).
* **Booleans:** `isSubTeam()`, `isActive()`.
* **DB:** Plural snake_case tables (`seasons`), snake_case columns (`created_at`).

### Lombok Usage

* **Entities:** `@Getter @Setter @NoArgsConstructor`, `@ToString(exclude = ...)`, extend `BaseEntity`.
* **Services/Controllers:** `@RequiredArgsConstructor` (constructor injection via `final`), `@Slf4j`.
* **Annotation Order:** On Spring components use `@Slf4j @Component @RequiredArgsConstructor` (alphabetical — `@Slf4j` first).

### Controller & DTO Patterns

* Form DTOs with `@Valid` + `BindingResult`, entities directly in GET (OSIV active).
* Flash attributes: `"successMessage"` / `"errorMessage"`.

### Logging

* `log.info()` for state changes, `log.debug()` for calculations.
* Always use parameterized `{}` format.

### CSS Guidelines

* Use CSS classes from `admin.css` instead of inline styles: `btn-xs`, `btn-sm`, etc.

### Static Analysis (SpotBugs + find-sec-bugs)

* **Gate:** `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build). No separate CI job — SpotBugs runs inside the existing `verify` step.
* **Suppressions:** Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment with a code-cross-reference to where the intentional pattern lives. No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")` in source or a `<Match>` entry in the filter file.
* **`lombok.config` invariant:** `lombok.config` at project root sets `lombok.extern.findbugs.addSuppressFBWarnings=true`. Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions — removing them re-introduces ~40–80 `EI_EXPOSE_REP*` false positives from Lombok-generated entity getters.

### CodeQL SAST (Code Scanning)

* **Gate:** `github/codeql-action@v4` runs on push to `master`, on pull_request against `master`, and on Sunday 02:00 UTC cron via `.github/workflows/codeql.yml` (language `java-kotlin`, query suite `security-extended`). The inline-bash SARIF-diff gate-step fails the workflow on the PR job when a new alert with `security-severity >= 7.0` (HIGH or CRITICAL, CVSS-aligned) appears on the head branch but not on the base branch. The weekly cron run skips the gate-step (drift detection only — alerts surface in the Security tab, no red runs).
* **Suppressions** live in `.github/codeql/codeql-config.yml` `query-filters`. Every suppressed finding requires a `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker on the line directly above the protected method or block, AND a matching table row in `docs/security/sast-acceptance.md`. UI dismissals are equally valid but MUST also be reflected in `sast-acceptance.md` (Update-on-Triage discipline — partial-writes are forbidden).
* **Acceptance doc** at `docs/security/sast-acceptance.md` is the single source of truth for SAST triage decisions: per-pattern sections (SSRF | ZIP-Slip | BCrypt-N/A | Others), per-finding rows with Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker. Every suppression PR MUST include a parallel `sast-acceptance.md` edit. The acceptance doc is the human-readable counterpart to the machine-readable `codeql-config.yml`.
