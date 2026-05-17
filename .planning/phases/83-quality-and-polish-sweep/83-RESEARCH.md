# Phase 83: Quality and Polish Sweep — Research

**Researched:** 2026-05-17
**Domain:** Spring Boot 4 + Thymeleaf admin polish; JPA `@OrderBy`, profile widening, controller-thin refactor, UAT procedure
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** All Phase-83 commits land directly on milestone branch `gsd/v1.11-tooling-and-cleanup`. NO per-phase feature/...-branch.
- **D-02:** One atomic commit per QUAL-ID (+ one verification commit). 9-commit final shape per CONTEXT.md.
- **D-03:** Per-commit targeted tests only; ONE final `./mvnw verify -Pe2e` before phase-close.
- **D-04:** `@OrderBy("season.year ASC, season.startDate ASC")` on `Driver.seasonDrivers` (field at `Driver.java:37`).
- **D-05:** `driver-detail.html` template untouched (annotation drives iteration order).
- **D-06:** Repository-IT in `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java`, `@Tag("integration")`.
- **D-07:** Playwright smoke `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java`, `@Tag("e2e")`.
- **D-08:** No DB migration (JPA-runtime annotation).
- **D-09:** `DevDataSeeder` annotation changes from `@Profile("dev")` to `@Profile({"dev", "local"})`.
- **D-10:** No removal of "Saison-2023 fixture path" inside `TestDataService`. The 2023-specific blocks at TestDataService.java:170-179, 325-331, 473-485, 925-948 are the CANONICAL seed.
- **D-12:** `TestDataService.seed()` idempotency early-return at line 69-72 (already present).
- **D-13:** `DevDataSeeder` keeps SiteGeneratorService call for both `dev` AND `local`.
- **D-14:** DemoDataSeeder unchanged — `@Profile("demo")` stays.
- **D-16:** Add conditional `<select name="groupId">` to `matchday-generator.html`, rendered only when `phase.layout == GROUPS`.
- **D-17:** `MatchdayGeneratorForm` gains `private UUID groupId;` field (no `@NotNull` — service-layer validation already exists).
- **D-18:** `SeasonController.java:251` changes from `null` to `form.getGroupId()`.
- **D-19:** `generateForm` GET handler at `SeasonController.java:226-238` exposes `phase` to template.
- **D-20:** Playwright E2E in `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java`, `@Tag("e2e")`.
- **D-21:** New record `StandingsView` at `src/main/java/org/ctc/admin/dto/StandingsView.java` (9 fields).
- **D-22:** New service `StandingsViewService` at `src/main/java/org/ctc/domain/service/StandingsViewService.java`, single public method `buildView(...)`.
- **D-23:** `StandingsController` becomes thin (~25 lines plumbing).
- **D-24:** Existing `StandingsService`, `DriverRankingService`, `SeasonPhaseService`, `SeasonManagementService` remain unchanged.
- **D-25:** Test impact for QUAL-04 — unit test on `StandingsViewService` (6-7 methods), existing `StandingsControllerTest` updates.
- **D-26:** `docs/uat/UAT-02-legacy-season-smoke.md` procedure document.
- **D-27:** Empty result-slot in v1.11 milestone-audit artifact (location TBD per Discretion).
- **D-28:** Phase-83 closes without live UAT-02 execution.
- **D-29:** No code change for QUAL-05 (pure docs commit).
- **D-30:** Phase-end verification commit shape — `83-VERIFICATION.md`.
- **D-31:** No PR creation in Phase-83.

### Claude's Discretion

- Whether QUAL-01 code commit + test commit are merged into one atomic commit or split (default: split).
- Whether the QUAL-02 `DevDataSeederLocalProfileIT` (D-15) is added or dropped.
- QUAL-03 `generateForm` GET uses `findRegularPhase(id)` (existing) or `findRegularPhaseWithGroups(id)` (eager-fetch) — depends on OSIV behaviour.
- QUAL-04 chooses Option (a) flat-model-unfurl vs Option (b) template-rewrite-to-`${view.X}`.
- QUAL-04 `StandingsControllerTest` updates separate commit or same.
- Final wording of `docs/uat/UAT-02-legacy-season-smoke.md`.
- Whether QUAL-05 result-slot lives in `STATE.md`, a new `.planning/milestone-audits/v1.11-UAT-02.md`, or appended to `.planning/milestones/v1.11-MILESTONE-AUDIT.md` (when that exists).

### Deferred Ideas (OUT OF SCOPE)

- Broader OSIV-lazy-access cleanup in other controllers (defer to v1.12+).
- QUAL-03 Generate-All-Groups single-button shortcut.
- Auto-execution of UAT-02 via Playwright against staging.
- `DriverRepositoryOrderIT` extension to a generic "ordering contract IT" pattern.
- DemoDataSeeder profile widening.
- StandingsView template-rewrite if planner picks Option (a).

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QUAL-01 | Driver-detail Season-Assignment chips render ascending year order; verified by repo IT + Playwright smoke | JPA `@OrderBy` semantics §"Standard Stack"; `DriverRepository` shape §"Existing Code Insights"; repo-IT precedent §"State of the Art" (SeasonPhaseRepositoryIT) |
| QUAL-02 | `DevDataSeeder` is `@Profile({"dev","local"})` so live-MariaDB UAT bootstraps without external fixture path | Spring `@Profile` multi-value semantics §"Standard Stack"; `TestDataService` profile-gating pitfall §"Common Pitfalls #1"; Runtime State Inventory §"local-profile" |
| QUAL-03 | Per-group matchday UI affordance for GROUPS-layout phases; verified by Playwright E2E on Season 2023 | Thymeleaf `th:if` semantics §"Standard Stack"; `MatchdayGeneratorService` already accepts non-null groupId §"Existing Code Insights"; OSIV `phase.groups` traversal §"Pitfall #3" |
| QUAL-04 | `StandingsController` lazy-collection access replaced by service-layer call returning full view object | Java records + OSIV §"Architecture Patterns"; existing services (`StandingsService`, `DriverRankingService`, `SeasonPhaseService`, `SeasonManagementService`) compose without modification §"Code Examples"; SpotBugs `EI_EXPOSE_REP` already pre-suppressed for `admin.dto` package §"SpotBugs Interaction" |
| QUAL-05 | UAT-02 procedure document + empty milestone-audit result slot; live execution after v1.11 deploy | Existing audit precedent at `.planning/milestones/v1.10-MILESTONE-AUDIT.md` §"State of the Art"; no `docs/uat/` directory exists today (greenfield, planner sets shape) |
</phase_requirements>

## Summary

Phase 83 is a focused polish sweep: four small Java code changes (one JPA annotation, one profile annotation widening, one form field + controller line, one controller refactor + new service + DTO record), supported by 4-5 new tests (1 IT + 2 E2E + 1 unit + optional 1 profile-IT) and one docs commit (UAT-02 procedure + empty result slot). No new packages, no new Flyway migrations, no `pom.xml` changes, no `lombok.config` changes, no `application*.yml` changes. The existing codebase already has every supporting primitive: `MatchdayGeneratorService.generate(phaseId, groupId, ...)` already validates and threads a non-null `groupId`; `admin.dto` package already has a blanket `EI_EXPOSE_REP/REP2` SpotBugs suppression so `StandingsView` will gate-clean without new suppressions; `@SpringBootTest` + `@Transactional` + `@Tag("integration")` is the locked-in pattern for repository-ITs (Phase 58 explicitly rejected `@DataJpaTest` slice and that precedent is hard-coded in three existing repo-IT comments). The single real research insight is `Pitfall #1` — **widening DevDataSeeder to `{"dev","local"}` without also widening `TestDataService` causes a `NoSuchBeanDefinitionException` at app start on `local`**, because TestDataService is itself `@Profile("dev")`-gated. Researcher confirmed this from source. The other key non-obvious finding is that there is NO external Saison-2023 bootstrap artifact in the repo (no `import.sql`, no Flyway callbacks, no `data/local/*.sql`, no README "Local Development" SQL block, no operator runbook referring to a separate seed step) — confirming that the "separate Saison-2023 fixture path" wording in ROADMAP-SC#2 refers to the work that would have been needed if widening was NOT chosen, i.e. D-10 in CONTEXT.md is correct. **Pitfall #2 is also significant — `season.startDate` (D-04 secondary sort key) does NOT exist on the Season entity; planner must substitute `season.number`.**

**Primary recommendation:** Execute the 9-commit shape from CONTEXT.md D-02 as-is, with two refinements: (1) replace `season.startDate` with `season.number` in the `@OrderBy` annotation for QUAL-01 (Pitfall #2); (2) include `TestDataService` profile widening in the same QUAL-02 commit as `DevDataSeeder` (Pitfall #1). For QUAL-03 OSIV: use `seasonPhaseService.findRegularPhase(id)` (or reuse `formData.phase()` already returned by `getFormData`) and rely on OSIV for `phase.groups` lazy traversal. For QUAL-04: pick Option (a) (controller unfurls `view.X` to flat model attrs) for minimal template surface area and zero risk to the 4 existing `StandingsControllerTest` assertions.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Driver season-assignment list ordering | Domain Model (entity field) | DB (Hibernate emits `ORDER BY` in SQL) | JPA `@OrderBy` is a declarative entity-level concern — keeps service/template code unchanged |
| Profile-driven seeder activation | Spring Configuration (annotation) | Application Startup (CommandLineRunner) | `@Profile` is the canonical Spring mechanism; no service-layer change required |
| Per-group matchday form field | Frontend Template (Thymeleaf) + DTO (Form) | API Controller (binds `groupId`) | UI affordance lives in template; DTO carries it; controller threads to existing service param |
| Standings view assembly | Service Layer (new `StandingsViewService`) | DTO (new `StandingsView` record) | OSIV-free traversal must happen inside a `@Transactional` service method, not in the controller |
| UAT procedure documentation | Docs (`docs/uat/`) + Planning (`.planning/milestones/`) | — | Operator artifact, not application code |

## Standard Stack

### Core

No new dependencies. Phase 83 reuses what's already in `pom.xml`:

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | `@Profile`, `@Controller`, MVC, `@SpringBootTest` | Already the project framework; locked by v1.10 milestone `[VERIFIED: pom.xml dependency block]` |
| Hibernate ORM | (Boot 4-managed) | JPA `@OrderBy` annotation on `@OneToMany` | Already used in `Season.phases`, `SeasonPhase.groups`, `SeasonPhase.matchdays`, `Season.cars`, `Season.tracks` with identical syntax `[VERIFIED: grep src/main/java/org/ctc/domain/model/]` |
| Thymeleaf | 3.1.5.RELEASE | `th:if` conditional fragment | Already used 100+ times across `src/main/resources/templates/admin/`; pinned by CVE-2026-40478 mitigation `[VERIFIED: REQUIREMENTS.md DEPS-04]` |
| Jakarta Validation | (Boot-managed) | Form DTO validation | `MatchdayGeneratorForm` already uses `@Min(1)`; no new annotation needed `[VERIFIED: src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java]` |
| Lombok | (Boot-managed) | `@Getter @Setter @NoArgsConstructor` on DTO; `@Slf4j @Service @RequiredArgsConstructor` on service | Project convention `[CITED: .planning/codebase/CONVENTIONS.md §Lombok Usage]` |
| Playwright | 1.58.0 | E2E test browser | Already used by 8 existing `@Tag("e2e")` classes under `src/test/java/org/ctc/e2e/` `[VERIFIED: ls output]` |
| JUnit 5 + Mockito | (Boot-managed) | Unit tests for `StandingsViewService` | Standard `@ExtendWith(MockitoExtension.class)` pattern `[CITED: .planning/codebase/TESTING.md]` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ | (Boot-managed) | Repo-IT + service-unit-test fluent assertions | Default per project convention |
| MockMvc | (Boot-managed) | Updates to `StandingsControllerTest` after QUAL-04 refactor | Already used by `StandingsControllerTest.java:28` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@OrderBy` on entity field | Sorted-finder method in `DriverRepository` | Annotation is declarative, applies to every Driver fetch (including OSIV template fetch — exactly what driver-detail.html needs). A custom finder would only work when explicitly called and would leave `driver.getSeasonDrivers()` unsorted. **Rejected — CONTEXT.md D-04.** |
| `StandingsView` record | `StandingsView` class with Lombok `@Getter` | Records are immutable and the project moved to records in v1.10 (per CONTEXT.md "DTO record over class"). **Choose record per project precedent.** |
| `@DataJpaTest` slice for `DriverRepositoryOrderIT` | Full `@SpringBootTest` | Phase 58 explicitly evaluated `@DataJpaTest` and rejected it — see Open Question 1 comment hardcoded in three repo-IT files. **Choose `@SpringBootTest` per project precedent.** |

**Installation:** None — no `pom.xml` changes.

**Version verification:** No new packages installed. The existing `Hibernate`, `Spring`, `Thymeleaf`, `Playwright` versions are all locked by Boot 4.0.6 and pinned per existing milestone decisions `[VERIFIED: pom.xml on milestone branch]`.

## Package Legitimacy Audit

> **Not applicable** — Phase 83 installs no external packages. No `pom.xml` modification. No npm/PyPI/cargo dependency added. Skipped per protocol "Required whenever this phase installs external packages."

## Architecture Patterns

### System Architecture Diagram

```
                          QUAL-01 (Driver Ordering)
HTTP GET /admin/drivers/{id}
        |
        v
DriverController.detail(id)
        |
        v
DriverRepository.findById(id)
        |
        v
[OSIV transaction OPEN]  <-- Hibernate session held until response committed
        |
        v
Thymeleaf renders driver-detail.html
        |
        v
${driver.seasonDrivers} iteration  <-- triggers lazy SQL
        |
        v
SELECT * FROM season_drivers sd
   JOIN seasons s ON sd.season_id=s.id
   WHERE sd.driver_id = ?
   ORDER BY s.season_year ASC, s.season_number ASC  <-- @OrderBy emits this
```

```
                          QUAL-02 (Profile Widening)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
        |
        v
Spring Application Context starts (profile=local)
        |
        +-- DevDataSeeder           @Profile({"dev","local"})  <-- ACTIVATES
        |        | (CommandLineRunner)
        |        v
        |   testDataService.seed()
        |        |
        |        +-- TestDataService @Profile({"dev","local"}) <-- MUST also widen (Pitfall #1)
        |        |        |
        |        |        +-- (seasonRepository.count() > 0 ? early-return : seed Season 2023 ...)
        |        |
        |        +-- teamCardService.generateAllCards()
        |                 |
        |                 +-- TeamCardService @Service <-- already activates on every profile (NO @Profile)
        |
        +-- siteGeneratorService.generate() (try/catch — non-fatal)
```

```
                          QUAL-03 (Per-Group Matchday Form)
HTTP GET /admin/seasons/{id}/generate
        |
        v
SeasonController.generateForm(id)
        |
        +-- matchdayGeneratorService.getFormData(id)   (existing; returns Season + SeasonPhase)
        |
        v
Thymeleaf renders matchday-generator.html
        |
        +-- th:if="${phase != null and phase.layout.name() == 'GROUPS'}"  <-- conditional <select>
        |        |
        |        v
        |   <select name="groupId">                         (NEW)
        |     th:each="g : ${phase.groups}"
        |   </select>
        |
        v
User submits POST /admin/seasons/{id}/generate
        |
        v
SeasonController.generate(id, @Valid form, BindingResult)
        |
        v
matchdayGeneratorService.generate(regular.getId(), form.getGroupId(), rounds, hAA)
                                                    ^^^^^^^^^^^^^^^^
                              (was hardcoded null at line 251 — QUAL-03 fixes this)
```

```
                          QUAL-04 (StandingsView Refactor)
HTTP GET /admin/standings?phase=&group=&seasonId=
        |
        v
StandingsController.standings(phase, group, seasonId, Model)
        |
        +-- seasonManagementService.findAll()       (existing — unchanged)
        +-- standingsViewService.buildView(phase, group, seasonId, isAlltime)  <-- NEW
        |        |
        |        v
        |   StandingsViewService (@Service @Transactional(readOnly=true))
        |        |
        |        +-- Resolves: alltime | explicit phase | legacy seasonId | active season fallback | no params
        |        +-- Composes: StandingsService.calculate(...) | DriverRankingService.calculate(...)
        |        |                                                 SeasonPhaseService.findAllPhases(...)
        |        +-- Reads phase.getGroups() INSIDE the @Transactional service method (OSIV-free at controller)
        |        |
        |        v
        |   returns StandingsView record
        |
        v
controller unfurls view to flat model attrs (Option a) OR template reads ${view.X} (Option b)
        |
        v
admin/standings.html renders
```

### Recommended Project Structure

No new packages. Files added/modified:

```
src/main/java/org/ctc/
├── admin/
│   ├── DevDataSeeder.java                              (annotation edit — QUAL-02)
│   ├── TestDataService.java                            (annotation edit — QUAL-02 Pitfall #1)
│   ├── controller/
│   │   ├── SeasonController.java                       (form field wiring — QUAL-03)
│   │   └── StandingsController.java                    (refactor to thin plumbing — QUAL-04)
│   └── dto/
│       ├── MatchdayGeneratorForm.java                  (add groupId field — QUAL-03)
│       └── StandingsView.java                          (NEW record — QUAL-04)
├── domain/
│   ├── model/
│   │   └── Driver.java                                 (add @OrderBy — QUAL-01)
│   └── service/
│       └── StandingsViewService.java                   (NEW service — QUAL-04)
src/main/resources/templates/admin/
└── matchday-generator.html                             (add conditional group <select> — QUAL-03)
src/test/java/org/ctc/
├── domain/
│   ├── repository/
│   │   └── DriverRepositoryOrderIT.java                (NEW — QUAL-01)
│   └── service/
│       └── StandingsViewServiceTest.java               (NEW — QUAL-04 unit)
├── e2e/
│   ├── DriverDetailSmokeE2E.java                       (NEW — QUAL-01)
│   └── MatchdayGeneratorGroupsE2E.java                 (NEW — QUAL-03)
└── admin/controller/
    └── StandingsControllerTest.java                    (UPDATE existing — QUAL-04)
docs/uat/                                                (NEW directory)
└── UAT-02-legacy-season-smoke.md                       (NEW — QUAL-05)
.planning/milestones/                                    (existing)
└── v1.11-UAT-02.md                                     (NEW result slot — QUAL-05, planner picks vs. STATE.md subsection)
```

### Pattern 1: JPA `@OrderBy` on a `@OneToMany` collection with nested field path

**What:** JPA 2.1+ allows the `@OrderBy` annotation to reference fields on the **target entity** of a `@OneToMany` association. Hibernate translates the JPQL-like path into a SQL `ORDER BY` clause emitted alongside the lazy collection fetch.

**When to use:** When the iteration order of a lazily-fetched collection must be deterministic and the order can be expressed in terms of fields on the target entity (or its `@ManyToOne` parents). Avoids reshuffling collections in service code or Thymeleaf.

**Example:**
```java
// Source: src/main/java/org/ctc/domain/model/Season.java:49-54 (project precedent)
@ManyToMany
@JoinTable(name = "season_cars",
        joinColumns = @JoinColumn(name = "season_id"),
        inverseJoinColumns = @JoinColumn(name = "car_id"))
@OrderBy("manufacturer ASC, name ASC")
private List<Car> cars = new ArrayList<>();
```

```java
// Source: src/main/java/org/ctc/domain/model/SeasonPhase.java:64-66 (project precedent)
@OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("sortIndex ASC")
private List<SeasonPhaseGroup> groups = new ArrayList<>();
```

```java
// QUAL-01 application — note the NESTED field path (season.year, season.number):
// Source: src/main/java/org/ctc/domain/model/Driver.java:36-37 (to be modified)
@OneToMany(mappedBy = "driver")
@OrderBy("season.year ASC, season.number ASC")  // NEW — D-04 corrected per Pitfall #2
private List<SeasonDriver> seasonDrivers = new ArrayList<>();
```

> **`@OrderBy` nested-path support is a Hibernate-extension over the JPA spec.** The JPA spec proper restricts `@OrderBy` to fields directly on the target entity. Hibernate has historically supported the dotted-path syntax referring to `@ManyToOne` parents, and Spring Boot 4.x ships Hibernate that supports this. **VERIFIED** by the field naming: `SeasonDriver.season` is a `@ManyToOne(fetch=FetchType.LAZY)` to `Season`, and `Season` has `year` (mapped to `season_year`) + `number` (mapped to `season_number`). `[CITED: Hibernate User Guide §Fetching ordering; src/main/java/org/ctc/domain/model/SeasonDriver.java:25-27; src/main/java/org/ctc/domain/model/Season.java]`

### Pattern 2: Multi-value `@Profile` annotation

**What:** Spring's `@Profile` annotation accepts a `String[]` value. A bean is registered when ANY of the listed profiles is active.

**When to use:** When a configuration component (seeder, security config, data provider) should activate on multiple profile names but stay deactivated on others.

**Example:**
```java
// Source: src/main/java/org/ctc/admin/OpenSecurityConfig.java:12 (project precedent)
@Profile({"dev", "local"})
public class OpenSecurityConfig { ... }
```

```java
// Source: src/main/java/org/ctc/admin/SecurityConfig.java:12 (project precedent)
@Profile({"prod", "docker"})
public class SecurityConfig { ... }
```

```java
// QUAL-02 application:
// src/main/java/org/ctc/admin/DevDataSeeder.java:12
@Profile({"dev", "local"})  // CHANGED from @Profile("dev")
```

### Pattern 3: Java record DTO for service-layer view assembly

**What:** Immutable record carrying a fully-resolved view state from a service method to a controller. The record's accessors are compiler-generated and return the field reference directly (no defensive copy).

**When to use:** When a controller would otherwise have to call multiple service methods AND traverse lazy collections to assemble its model. The record is created inside a `@Transactional` service method (lazy collections resolved while session is open) and consumed in the controller (after the service call returns, session may be closed).

**Example:**
```java
// Source: src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java:213-214 (project precedent)
public record GeneratorFormData(Season season, SeasonPhase phase, int teamCount, int optimalRounds) {}
```

```java
// Source: src/main/java/org/ctc/domain/service/SeasonPhaseService.java:52,57-58 (project precedent)
public record Assignment(UUID teamId, boolean included, UUID groupId) {}
public record RosterEditorState(Set<UUID> assignedTeamIds,
                                Map<UUID, UUID> currentGroupByTeamId) {}
```

```java
// QUAL-04 application — full StandingsView record per CONTEXT.md D-21:
// Source: src/main/java/org/ctc/admin/dto/StandingsView.java (NEW)
public record StandingsView(
    SeasonPhase phase,
    List<SeasonPhaseGroup> groups,
    List<StandingsService.TeamStanding> standings,         // <-- existing inner class (see Pitfall #5)
    List<DriverRankingService.DriverRanking> driverRanking,
    UUID selectedGroupId,
    boolean hasRegularPhase,
    boolean combinedView,
    boolean showGroupColumn,
    boolean showBuchholz
) {}
```

### Anti-Patterns to Avoid

- **Adding a custom `findByDriverIdOrderByYearAsc` finder to `DriverRepository`:** The annotation approach is declarative and applies to every `Driver` fetch — including the implicit fetch when Thymeleaf reads `${driver.seasonDrivers}` under OSIV. A custom finder would only work when explicitly invoked.
- **Hand-rolling a `Comparator.comparing(...)` sort in the controller or template:** Java-side sort defeats the whole point of `@OrderBy` (which is to push ordering to the DB and have it apply uniformly). Templates with comparators violate "Keep Thymeleaf Templates Lean".
- **Adding `@NotNull` to `MatchdayGeneratorForm.groupId`:** Would reject SINGLE-layout POSTs (where the field is intentionally null). Validation is layered: the service method already throws `IllegalArgumentException` for the GROUPS+null case (`MatchdayGeneratorService.java:49-51`).
- **Touching existing Flyway migrations:** `CLAUDE.md` invariant. Phase 83 needs no schema change.
- **Adding `@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})` to `StandingsView`:** The `admin.dto` package already has a blanket `<Match>` in `config/spotbugs-exclude.xml` covering this exact pattern. Adding a per-class annotation is redundant.
- **Calling `phase.getGroups().size()` in the controller GET handler as an OSIV pre-warm hack:** OSIV already keeps the session open through render; Thymeleaf will resolve `phase.groups` naturally.
- **Bumping `BackupSchema.SCHEMA_VERSION` or modifying any backup MixIn class:** Out of phase scope; Phase 82 locked that with a guard test.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Stable ordering of `Driver.seasonDrivers` for display | Java `Comparator` in service or template | JPA `@OrderBy` annotation on the entity field | Declarative; applies to every fetch path; consistent with `Season.cars`, `SeasonPhase.groups`, `SeasonPhase.matchdays` precedents |
| Profile-gated bean activation | Bean factory with manual `@Conditional(...)` | Spring `@Profile({"dev","local"})` | Project already uses this pattern in 3 other places; identical semantics |
| Resolving a multi-branch view (alltime / phase / seasonId / active) from controller | Long `if/else` chain in `@GetMapping` method | Dedicated service method returning record DTO | Per `CLAUDE.md` "Keep Controllers Thin"; existing precedent at `MatchdayGeneratorService.getFormData(...)` + `SeasonManagementService.getEditFormData(...)` |
| Eager-fetch of `phase.groups` for template iteration | Custom JPQL with `JOIN FETCH` | Rely on OSIV (already enabled per CLAUDE.md invariant) | Project explicitly enables OSIV for exactly this case (admin templates); `season-detail.html` already does this without issue |
| `StandingsView` record exposing mutable `List<>` accessors | Defensive `Collections.unmodifiableList(...)` wrappers | Trust the SpotBugs package-level `<Match>` for `admin.dto` already covers it | Adds zero defensive value (the record is consumed within a single request scope under OSIV) and the suppression already exists |

**Key insight:** every primitive Phase 83 needs already exists. The only "new code" is glue: an `@OrderBy` line, a profile annotation widening (×2 classes per Pitfall #1), a form field + template conditional, and a refactor moving lazy-collection access from the controller down into a new service method that composes existing services. No hand-rolled algorithms, no new repositories, no new validators.

## Runtime State Inventory

> Phase 83 includes a profile widening (QUAL-02) and a minor JPA annotation change (QUAL-01). Strictly speaking these are code edits, but the profile widening triggers a new runtime bootstrap path on `local` that must be inventoried.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — QUAL-01 `@OrderBy` is a SELECT-time ORDER BY; no stored field changes. QUAL-02 widening: existing `local`-profile MariaDB databases already contain data (or are empty); `TestDataService.seed()` short-circuits at `seasonRepository.count() > 0`. | None |
| Live service config | None. No Google Calendar / Sheets / Tailscale / Cloudflare config references "DevDataSeeder" or "Season 2023" by name. | None |
| OS-registered state | None. No Windows Task Scheduler / pm2 / launchd / systemd entries identified — project deploys via `docker compose`, the container runs `java -jar` directly, no host-OS registration is involved. | None |
| Secrets / env vars | None. Env vars referenced: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CALENDAR_ID` — none of these reference profile names or seeder names by string match. | None |
| Build artifacts | None. The four code edits do not affect compiled artefact identity beyond the `.class` files themselves. `target/site/jacoco/index.html` regenerates on each `./mvnw verify`. | None |

**External Saison-2023 bootstrap audit (D-11 mandate):**

- `README.md` — searched for `Saison|Season 2023|bootstrap|fixture|local profile setup`. **No match** for a Saison-2023 bootstrap step or operator-runnable SQL. The "Quick Start" block mentions `dev`, `dev,demo` profiles only; no `local` setup section exists.
- `docs/` — exhaustive grep across `docs/operations/`, `docs/superpowers/`. Existing references to "Season 2023" are in design specs about historical CTC seasons (`docs/superpowers/specs/`), not as runtime bootstrap.
- `docs/operations/` — `import-runbook.md` (backup recovery doc) only; no Saison-2023 bootstrap step.
- `data/local/` — directory contains `logs/` and `uploads/` only. No `*.sql`, no `import.sql`, no SQL seed file.
- `data/dev/` — directory contains `backup-staging/`, `import-backups/`, `logs/`, `uploads/`. No SQL seed file.
- `src/main/resources/db/migration/` — only V1, V2, V3, V7 Flyway migrations (4 files). All are schema-DDL, no INSERT-data statements that would seed Season 2023.
- `src/main/resources/db/` — no `afterMigrate*.sql` / `beforeMigrate*.sql` Flyway callback scripts exist.
- `application-local.yml` — declarative config only (port, MariaDB URL/creds, Flyway location). No `flyway.placeholders` or `data.sql` reference.
- `application*.yml` (all profiles) — no reference to a Saison-2023 seeded path.

**Conclusion:** No external Saison-2023 bootstrap artifact exists. D-10 in CONTEXT.md is correct: the ROADMAP-SC#2 wording "no longer requires a separate Saison-2023 fixture path" refers to the *operator-workaround that would have been required* if widening DevDataSeeder was rejected. The annotation widening alone satisfies SC#2. **No additional file deletion or migration in QUAL-02 commit.** `[VERIFIED: exhaustive grep + ls of repo]`

**`local`-profile activation impact:** After QUAL-02, running `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` against an empty MariaDB will execute `TestDataService.seed()` which **writes 4 seasons (Season 2023 + Season 2024-Regular + Season 2026 + Season 2024-EmptyPhase), associated teams, drivers, matchdays, results, and team-card images into MariaDB.** This is desirable behaviour (the whole point of SC#2). Operators of `local` profile against a non-empty MariaDB get the early-return no-op.

## Common Pitfalls

### Pitfall 1: Widening `DevDataSeeder` without also widening `TestDataService`

**What goes wrong:** Spring fails to start the `local` profile with `NoSuchBeanDefinitionException: No qualifying bean of type 'org.ctc.admin.TestDataService' available`.

**Why it happens:** `DevDataSeeder` declares `private final TestDataService testDataService;` and `private final SiteGeneratorService siteGeneratorService;`. Today both deps exist under `dev`; `TestDataService` is `@Profile("dev")`-gated at `src/main/java/org/ctc/admin/TestDataService.java:40`, while `SiteGeneratorService` has no `@Profile` annotation (so it's universal). When DevDataSeeder activates on `local`, Spring tries to inject `TestDataService` and fails because no bean exists under that profile. Additionally, `TestDataService` itself references `TeamCardService` (which has no `@Profile` — verified at `src/main/java/org/ctc/admin/service/TeamCardService.java:30`); that part is safe.

**How to avoid:** Same commit (QUAL-02) MUST widen TestDataService alongside DevDataSeeder:
```java
// src/main/java/org/ctc/admin/TestDataService.java:40
@Profile({"dev", "local"})  // CHANGED from @Profile("dev")
```
Researcher confirmed only these two classes have the `dev`-only annotation in `src/main/java/org/ctc/admin/` (grep matches lines `DevDataSeeder.java:12` and `TestDataService.java:40` only). `SiteGeneratorService` and `TeamCardService` carry NO `@Profile` annotation so they are always available — no change needed there.

**Warning signs:** App-start fails on `local` profile with `UnsatisfiedDependencyException` rooted in DevDataSeeder constructor. Visible in the very first run of QUAL-02 if planner only edits `DevDataSeeder.java`.

### Pitfall 2: Wrong field name in `@OrderBy` path (D-04 correction)

**What goes wrong:** Hibernate throws `org.hibernate.query.SemanticException: Could not interpret path expression 'season.startDate'` at app start, blocking every test.

**Why it happens:** D-04 specifies `@OrderBy("season.year ASC, season.startDate ASC")`. `Season.java` field list (verified line-by-line at lines 24-37) declares: `name`, `year`, `number`, `description`, `active`. **No `startDate` field on Season.** The `startDate` field lives on `SeasonPhase` (`SeasonPhase.java:44`), NOT on `Season`. Hibernate will fail at startup when resolving the path expression.

**How to avoid:** Replace `season.startDate` with `season.number`:
- **Recommended:** `@OrderBy("season.year ASC, season.number ASC")` — uses a real Season field; matches the human-meaningful intuition (Season 2024-1 before Season 2024-2 before Season 2024-3).
- **Alternative:** `@OrderBy("season.year ASC")` only — sufficient for ROADMAP-SC#1 wording ("ORDER BY year ASC") but loses determinism across same-year seasons.

**Recommended:** `@OrderBy("season.year ASC, season.number ASC")` — preserves intent of D-04 (deterministic within-year ordering) using a field that exists. **Planner MUST apply this correction in the QUAL-01 implementation.** `[VERIFIED: src/main/java/org/ctc/domain/model/Season.java fields list]`

**Warning signs:** Hibernate startup error during the very first `./mvnw test` run after the annotation edit. Caught immediately by `DriverRepositoryOrderIT` if planner runs it first.

### Pitfall 3: `phase.groups` lazy initialisation in the GET form handler

**What goes wrong:** Thymeleaf renders `matchday-generator.html` and hits `th:each="g : ${phase.groups}"`. If the controller has already committed its `@Transactional` boundary (services typically wrap reads in `@Transactional(readOnly=true)`) and the controller is being tested **without** OSIV (e.g., a unit test bypassing the Spring filter chain), the lazy traversal would throw `LazyInitializationException`.

**Why it happens:** OSIV holds the session through the entire HTTP request via `OpenEntityManagerInViewFilter`. In MockMvc / `@SpringBootTest` integration tests, OSIV is active by default — no issue. In **plain unit tests** that drive the controller directly with mocks, OSIV is NOT active — but the controller doesn't have to handle that case because real HTTP traffic always flows through OSIV.

**How to avoid:** Default approach — use `seasonPhaseService.findRegularPhase(id)` (or reuse `formData.phase()` already returned by `getFormData`) and rely on OSIV at runtime. Verified safe by `season-detail.html` already iterating `${groups}` from `SeasonPhase` via similar code paths. If a unit-style controller test bypasses OSIV and reaches `phase.getGroups()`, the test fixture must pre-load the collection (Hibernate `Hibernate.initialize(...)`) or use a real `@SpringBootTest`. **No service-level change required.**

**Warning signs:** A test class without `@SpringBootTest` that tries to assert on the form template. Project precedent uses `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")` for controller tests — under that combination, OSIV is active and the traversal works.

### Pitfall 4: `MatchdayGeneratorGroupsE2E` colliding with `DevDataSeeder`-seeded Season 2023

**What goes wrong:** The E2E test navigates to `/admin/seasons/{2023-id}/generate` and submits a GROUPS-layout matchday generation. **Season 2023 seeded by `TestDataService` already has matchdays** (it's the canonical GROUPS+PLAYOFF fixture per `TestDataService` javadoc lines 28-30). `MatchdayGeneratorService.generate(...)` checks `if (!existing.isEmpty()) throw new IllegalStateException("Phase/group already has matchdays — delete them first")` at line 63-65.

**Why it happens:** The seeded Season 2023 is pre-populated with matchdays per-group as part of the GROUPS+PLAYOFF canonical seed. A "generate matchdays" submission against that season's REGULAR phase WILL throw `IllegalStateException` because matchdays already exist.

**How to avoid:** Two viable shapes for the QUAL-03 E2E:
1. **Use Test-prefix Season** — same approach as `GroupsSeasonE2ETest` (year=2099, season "Test-GROUPS Season 2099", teams T-GA-1/T-GA-2/T-GB-1/T-GB-2). Create the fixture in `@BeforeEach`. Existing precedent: `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java`. **RECOMMENDED.**
2. **Drop matchdays first** — `@BeforeEach` cleans up Season 2023's existing matchdays. Brittle: contaminates the seeded fixture for other tests running in the same `@SpringBootTest` context.

**RECOMMENDED:** Option 1 — clone the `GroupsSeasonE2ETest` setup pattern (test-prefix `T-MGEN-` names, year=2098, idempotent cleanup in `@BeforeEach`). The new E2E exercises the form UI (the group `<select>` appears and submits correctly) — that's the ROADMAP-SC#3 requirement, independent of whether the underlying season was DevDataSeeder-seeded or test-created.

**Warning signs:** First `./mvnw verify -Pe2e -Dit.test=MatchdayGeneratorGroupsE2E` run fails with redirect to `/admin/seasons/{id}/generate` carrying flash-error "Phase/group already has matchdays — delete them first".

### Pitfall 5: `StandingsView.standings` typing — package-qualified inner class

**What goes wrong:** `StandingsView` record references `StandingsService.TeamStanding` and `DriverRankingService.DriverRanking`. These are **public static inner classes** (verified: `StandingsService.java:292`, `DriverRankingService.java:212`), NOT records and NOT top-level classes. The record field declarations work fine (`List<StandingsService.TeamStanding>` is valid), but a careless import in `StandingsView.java` could shadow names.

**Why it happens:** The "obvious" top-level types named `StandingsRow` or `DriverRankingRow` mentioned in CONTEXT.md D-21 **do not exist** in the codebase. The actual types are the package-qualified inner classes above.

**How to avoid:** Plan the record declaration as:
```java
package org.ctc.admin.dto;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.service.DriverRankingService.DriverRanking;
import org.ctc.domain.service.StandingsService.TeamStanding;

public record StandingsView(
    SeasonPhase phase,
    List<SeasonPhaseGroup> groups,
    List<TeamStanding> standings,
    List<DriverRanking> driverRanking,
    UUID selectedGroupId,
    boolean hasRegularPhase,
    boolean combinedView,
    boolean showGroupColumn,
    boolean showBuchholz
) {}
```

Static-import-of-inner-class is idiomatic Java. The `standings.html` template's existing `${standing.team.shortName}`, `${standing.played}`, `${standing.points}`, `${standing.buchholz}`, `${standing.group.name}` accesses all resolve against `TeamStanding`'s existing getters (verified at lines 292-339 of StandingsService) — no template adjustments needed under Option (a).

**Warning signs:** Compile error `cannot find symbol class StandingsRow` if the planner copies D-21 verbatim without resolving the type names.

### Pitfall 6: `StandingsControllerTest` already asserts on flat model attributes

**What goes wrong:** `StandingsControllerTest` (verified at `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java:69-119`) calls `.andExpect(model().attributeExists("seasons", "standings", "driverRanking", "selectedSeason"))` and `.andExpect(model().attribute("selectedSeasonId", inactiveSeason.getId().toString()))`. These attribute names are FLAT (not nested under a `view` namespace). Option (b) of D-23 (refactor template to `${view.X}`) breaks these 4 existing assertions.

**Why it happens:** Existing test was written against the current flat-model controller. Migration to `${view.X}` requires updating model attribute names in the controller AND in the test assertions.

**How to avoid:** Pick Option (a) — controller unfurls `view.X` to flat model attrs after calling `standingsViewService.buildView(...)`. Existing tests stay green without modification. The controller stays thin (just unfurl + delegate), the lazy traversal moves into the service. **Best of both worlds.** Code shape:
```java
// QUAL-04 — controller stays thin (Option a)
@GetMapping
public String standings(@RequestParam(required = false) UUID phase,
                        @RequestParam(required = false) UUID group,
                        @RequestParam(required = false) String seasonId,
                        Model model) {
    model.addAttribute("seasons", seasonManagementService.findAll());

    boolean isAlltime = "alltime".equals(seasonId);
    var view = standingsViewService.buildView(phase, group, seasonId, isAlltime);

    // Unfurl view to flat model attrs — keeps existing tests + template untouched
    model.addAttribute("isAlltime", isAlltime);
    model.addAttribute("phase", view.phase());
    model.addAttribute("groups", view.groups());
    model.addAttribute("standings", view.standings());
    model.addAttribute("driverRanking", view.driverRanking());
    model.addAttribute("selectedGroupId", view.selectedGroupId());
    model.addAttribute("hasRegularPhase", view.hasRegularPhase());
    model.addAttribute("combinedView", view.combinedView());
    model.addAttribute("showGroupColumn", view.showGroupColumn());
    model.addAttribute("showBuchholz", view.showBuchholz());
    model.addAttribute("selectedSeasonId", seasonId);
    // ... preserve selectedSeason / allPhases attrs the way the existing controller does
    return "admin/standings";
}
```

**Warning signs:** Existing `StandingsControllerTest` failures with messages like "Model attribute 'standings' does not exist" if planner picks Option (b) without sweeping the test assertions.

### Pitfall 7: `@OrderBy` with nested path requires the parent to be loaded

**What goes wrong:** `@OrderBy("season.year ASC, season.number ASC")` on `Driver.seasonDrivers` causes Hibernate to emit an SQL ORDER BY that references `seasons.season_year` and `seasons.season_number`. Hibernate must add an implicit JOIN to `seasons` in the lazy-fetch SQL. **This works**, but means each `driver.getSeasonDrivers()` access triggers a JOIN'd SELECT rather than the simpler "SELECT * FROM season_drivers WHERE driver_id=?" query — slightly more expensive.

**Why it happens:** Standard Hibernate behaviour. Performance impact is negligible for typical driver pages (a Driver has at most ~10 SeasonDrivers).

**How to avoid:** Acceptable as-is. If profiling later shows it as hot, switch to a sorted-finder pattern OR add `@BatchSize(size = 25)` on Driver.

**Warning signs:** Logs show `Hibernate: SELECT ... FROM season_drivers sd LEFT JOIN seasons s ON sd.season_id=s.id WHERE sd.driver_id=? ORDER BY s.season_year, s.season_number` instead of the simpler unsorted SELECT. **Expected and desired.**

## Code Examples

### Adding `@OrderBy` with nested-path sort key

```java
// Source: src/main/java/org/ctc/domain/model/Driver.java (modify line 36-37)
@OneToMany(mappedBy = "driver")
@OrderBy("season.year ASC, season.number ASC")
private List<SeasonDriver> seasonDrivers = new ArrayList<>();
```
(Note: deviates from D-04 by replacing `season.startDate` with `season.number` per Pitfall #2.)

### Multi-profile annotation

```java
// Source: src/main/java/org/ctc/admin/DevDataSeeder.java (modify line 12)
@Profile({"dev", "local"})
```

```java
// Source: src/main/java/org/ctc/admin/TestDataService.java (modify line 40 per Pitfall #1)
@Profile({"dev", "local"})
```

### Thymeleaf conditional form section

```html
<!-- Source: src/main/resources/templates/admin/matchday-generator.html (insert after line 21) -->
<div class="form-group" th:if="${phase != null and phase.layout.name() == 'GROUPS'}">
    <label for="groupId">Group</label>
    <select id="groupId" th:field="*{groupId}" required>
        <option value="">-- Select group --</option>
        <option th:each="g : ${phase.groups}" th:value="${g.id}" th:text="${g.name}"></option>
    </select>
</div>
```
(`required` only when the `th:if` renders the field — SINGLE-layout POSTs never see the field so HTML5 `required` doesn't fire.)

### Form DTO extension

```java
// Source: src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java (add field)
@Getter
@Setter
@NoArgsConstructor
public class MatchdayGeneratorForm {

    @Min(1)
    private int numberOfRounds;

    private boolean homeAndAway;

    private UUID groupId;   // NEW — null for LEAGUE layout, non-null for GROUPS layout
}
```

### Controller form GET handler (add phase model attr)

```java
// Source: src/main/java/org/ctc/admin/controller/SeasonController.java (modify generateForm at line 227)
@GetMapping("/{id}/generate")
public String generateForm(@PathVariable UUID id, Model model) {
    var formData = matchdayGeneratorService.getFormData(id);
    var season = formData.season();
    var phase = formData.phase();                                    // NEW (reuse — no extra query)
    var form = new MatchdayGeneratorForm();
    Integer rounds = phase != null ? phase.getTotalRounds() : null;  // null-safe — getFormData returns phase=null when no REGULAR phase
    form.setNumberOfRounds(rounds != null ? rounds : formData.optimalRounds());
    model.addAttribute("season", season);
    model.addAttribute("phase", phase);                              // NEW
    model.addAttribute("generatorForm", form);
    model.addAttribute("teamCount", formData.teamCount());
    model.addAttribute("optimalRounds", formData.optimalRounds());
    return "admin/matchday-generator";
}
```
> **Optimisation:** `matchdayGeneratorService.getFormData(id)` already returns a `GeneratorFormData(Season season, SeasonPhase phase, int teamCount, int optimalRounds)` record (verified `MatchdayGeneratorService.java:213-214`). Reuse `formData.phase()` instead of a second `seasonPhaseService.findRegularPhase(id)` call. Replaces existing line 231's `seasonPhaseService.findRegularPhase(id).getTotalRounds()` and saves one query.

### Controller POST handler (thread groupId)

```java
// Source: src/main/java/org/ctc/admin/controller/SeasonController.java:251 (single-line change)
matchdayGeneratorService.generate(regular.getId(), form.getGroupId(), form.getNumberOfRounds(), form.isHomeAndAway());
```

### Repository-IT for `@OrderBy` (project-precedent shape)

```java
// Source: src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java (NEW)
package org.ctc.domain.repository;

import java.util.List;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class DriverRepositoryOrderIT {

    @Autowired private DriverRepository driverRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private SeasonDriverRepository seasonDriverRepository;

    @Test
    void givenDriverWithMultiYearSeasonDrivers_whenFetched_thenSeasonDriversOrderedByYearAsc() {
        // given — 3 seasons across years 2025, 2023, 2024 (deliberately unordered)
        var s2025 = seasonRepository.save(new Season("Phase83-Order-2025", 2025, 1));
        var s2023 = seasonRepository.save(new Season("Phase83-Order-2023", 2023, 1));
        var s2024 = seasonRepository.save(new Season("Phase83-Order-2024", 2024, 1));
        var team = teamRepository.save(new Team("Phase83 Order Team", "P83-ORD"));
        var driver = driverRepository.save(new Driver("phase83_orderdrv", "Order Test Driver"));

        // Persist SeasonDriver rows in non-sorted insert order
        seasonDriverRepository.save(new SeasonDriver(s2025, driver, team));
        seasonDriverRepository.save(new SeasonDriver(s2023, driver, team));
        seasonDriverRepository.save(new SeasonDriver(s2024, driver, team));

        // when — re-fetch driver and read seasonDrivers
        var reloaded = driverRepository.findById(driver.getId()).orElseThrow();
        List<Integer> years = reloaded.getSeasonDrivers().stream()
                .map(sd -> sd.getSeason().getYear())
                .toList();

        // then — Hibernate emits ORDER BY season.year ASC, season.number ASC
        assertThat(years).containsExactly(2023, 2024, 2025);
    }
}
```

### Playwright smoke for driver-detail chip order

```java
// Source: src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java (NEW)
package org.ctc.e2e;

import org.ctc.domain.repository.DriverRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class DriverDetailSmokeE2E extends PlaywrightConfig {

    @Autowired private DriverRepository driverRepository;

    @BeforeEach void setUp() { setupPage(); }
    @AfterEach  void tearDown() { teardownPage(); }

    @Test
    void givenSeededDriverWithMultiYearAssignments_whenOpenDetail_thenChipsInAscendingYear() {
        // given — pick any seeded driver from DevDataSeeder fixture with >=2 season assignments
        var driver = driverRepository.findAll().stream()
                .filter(d -> d.getSeasonDrivers().size() >= 2)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "No seeded driver with >=2 SeasonDriver rows — fixture broken"));

        // when
        page.navigate(url("/admin/drivers/" + driver.getId()));

        // then — chip text-order matches ascending year (parse "<year> | #<n> | <name>" prefix)
        var chips = page.locator(".chip-list .chip");
        int n = chips.count();
        int previousYear = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            String text = chips.nth(i).innerText();
            int year = Integer.parseInt(text.substring(0, 4));   // displayLabel starts with year
            org.junit.jupiter.api.Assertions.assertTrue(year >= previousYear,
                    "Chip " + i + " year " + year + " < previous " + previousYear);
            previousYear = year;
        }
    }
}
```

### `StandingsViewService` skeleton

```java
// Source: src/main/java/org/ctc/domain/service/StandingsViewService.java (NEW)
package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.StandingsView;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsViewService {

    private final StandingsService standingsService;
    private final DriverRankingService driverRankingService;
    private final SeasonManagementService seasonManagementService;
    private final SeasonPhaseService seasonPhaseService;

    @Transactional(readOnly = true)
    public StandingsView buildView(UUID phaseParam, UUID groupParam, String seasonIdParam, boolean isAlltime) {
        if (isAlltime) {
            return new StandingsView(
                    null, List.of(),
                    standingsService.calculateAlltimeStandings(),
                    driverRankingService.calculateAlltimeRanking(),
                    null, false, false, false, false);
        }

        SeasonPhase resolved = null;
        UUID resolvedSeasonId = null;

        if (phaseParam != null) {
            resolved = seasonPhaseService.findById(phaseParam);
            resolvedSeasonId = resolved.getSeason().getId();
        } else if (seasonIdParam != null && !seasonIdParam.isBlank()) {
            try {
                resolvedSeasonId = UUID.fromString(seasonIdParam);
                resolved = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR).orElse(null);
            } catch (IllegalArgumentException ignored) {
                log.debug("Invalid season ID format: {}", seasonIdParam);
            }
        } else {
            var active = seasonManagementService.findActiveSeason().orElse(null);
            if (active != null) {
                resolvedSeasonId = active.getId();
                resolved = seasonPhaseService.findByType(resolvedSeasonId, PhaseType.REGULAR).orElse(null);
            }
        }

        if (resolved == null) {
            return new StandingsView(null, List.of(), List.of(), List.of(),
                    null, false, false, false, false);
        }

        boolean isGroupsLayout = resolved.getLayout() == PhaseLayout.GROUPS;
        boolean groupSelected = groupParam != null;
        boolean combined = isGroupsLayout && !groupSelected;
        boolean showGroupCol = combined;
        boolean showBuchholz = resolved.getFormat() == SeasonFormat.SWISS && groupSelected;

        var standings = showBuchholz
                ? standingsService.calculateStandingsWithBuchholz(resolved.getId(), groupParam)
                : standingsService.calculateStandings(resolved.getId(), groupParam);

        return new StandingsView(
                resolved,
                resolved.getGroups(),                           // lazy traversal INSIDE @Transactional
                standings,
                driverRankingService.calculateRankingForPhase(resolved.getId()),
                groupParam,
                true,
                combined,
                showGroupCol,
                showBuchholz);
    }
}
```

> **OSIV-free traversal verification:** `phase.getGroups()` access happens inside this `@Transactional(readOnly=true)` service method, where the Hibernate session is open by virtue of the transaction. The controller never touches a lazy collection — it consumes the already-materialised `view.groups()` `List<SeasonPhaseGroup>`. ROADMAP-SC#4 satisfied.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| File-name routing for `*IT.java` to Failsafe | `@Tag("integration")` / `@Tag("e2e")` annotation routing | Phase 79 D-05 (2026-05-15) | Phase 83 new test classes MUST carry the correct `@Tag` |
| Controller assembles model from multiple service calls + lazy traversal | Service returns a single view DTO record; controller unfurls | Phase 60-69 milestones (v1.9 era) — see `MatchdayGeneratorService.getFormData(...)` | Phase 83 QUAL-04 applies this pattern to StandingsController |
| Lombok-generated entity getters trigger `EI_EXPOSE_REP` | `lombok.config` `lombok.extern.findbugs.addSuppressFBWarnings=true` + package-level `<Match>` in `config/spotbugs-exclude.xml` | Phase 81 (2026-05-16) | Phase 83 `StandingsView` record auto-inherits package-level `<Match>` for `org.ctc.admin.dto` — zero new suppressions |
| Per-phase feature/fix branches | Direct commits on milestone branch (per `feedback_milestone_branch`) | 2026-05-17 user correction | Phase 83 stays on `gsd/v1.11-tooling-and-cleanup` |
| Full `./mvnw verify` after every commit | Targeted `-Dtest=` / `-Dit.test=` between, ONE final `verify -Pe2e` per phase | Phase 79 D-08 | Phase 83 test cadence per CONTEXT.md D-03 |

**Deprecated/outdated:**
- `@DataJpaTest` slice for repository-ITs — three existing repo-IT files carry the comment `// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1` (Phase 58 RESEARCH). DriverRepositoryOrderIT MUST follow this precedent.
- Hardcoded `null` `groupId` at `SeasonController:251` — Phase 83 QUAL-03 removes it.
- `StandingsController` 113-line `standings(...)` method — Phase 83 QUAL-04 reduces it to ~25 lines.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Hibernate (Spring Boot 4.0.6) supports nested-path `@OrderBy("season.year ASC, season.number ASC")` on a `@OneToMany` field | Pattern 1 | DriverRepositoryOrderIT fails at startup with `SemanticException`; planner falls back to `@OrderBy("season.year ASC")` alone (still satisfies SC#1 wording). LOW risk — Hibernate has supported this since 5.x. `[ASSUMED]` |
| A2 | `MatchdayGeneratorService.generate(...)` IllegalArgumentException for GROUPS+null currently reaches the user as a flash error (not a 500) | QUAL-03 controller change | If currently rendered as 500, QUAL-03 looks regressed. **Verified** at `SeasonController.java:253-256` catches `IllegalStateException | IllegalArgumentException` and routes to flash — no risk. `[VERIFIED: source read]` |
| A3 | `local` profile MariaDB instance available locally during QUAL-02 manual smoke (operator has Docker + a `ctcdb` MariaDB running on localhost:3306 with user `ctc`) | D-30 verification evidence | Manual smoke step skipped if no MariaDB; the D-15 `DevDataSeederLocalProfileIT` slice-test (if added) covers bean activation without MariaDB. Discretionary. `[ASSUMED]` |
| A4 | Phase 83 test additions stay within v1.10 JaCoCo baseline 87.80% ± 0.5pp | Coverage discipline | If new tests increase the line-coverage delta beyond +0.5pp, planner accepts the boost (gate is min 82%, not a ceiling). If coverage drops, planner investigates. Estimated delta: +0.05 to +0.20 pp (5 new tests covering ~50-80 new LOC in StandingsViewService + StandingsView). `[ASSUMED]` |
| A5 | The QUAL-03 E2E can finish in <30s wall-clock against the seeded `dev`-profile DB | Test wallclock budget | If slower, may bump Phase 86 wallclock target. Existing `GroupsSeasonE2ETest` runs ~25-30s; a simpler "navigate to form + submit + assert redirect" should be faster. `[ASSUMED]` |
| A6 | `DriverRanking` and `TeamStanding` inner classes' accessors satisfy `standings.html`'s existing `${standing.X}` / `${ranking.X}` reads after the QUAL-04 refactor | QUAL-04 Option (a) zero-template-change claim | Verified: standings.html reads `standing.team.shortName`, `standing.played`, `standing.points`, `standing.buchholz`, `standing.group.name`, `ranking.driver.psnId`, `ranking.team.shortName`, `ranking.racesCount`, `ranking.bestPosition`, `ranking.averagePoints`, `ranking.totalPoints`. All resolve against the existing getter shape on `TeamStanding` and `DriverRanking` (verified at StandingsService.java:292-339, DriverRankingService.java:212-260). `[VERIFIED: source read]` |
| A7 | `D-04`'s `season.startDate` field exists on Season entity | Pitfall #2 | **CONFIRMED WRONG.** Season has no `startDate` field. Planner MUST replace with `season.number` (recommended) or drop the secondary sort. `[VERIFIED: source read]` |

**If this table is empty:** N/A — Phase 83 carries 7 entries; A1, A3, A4, A5 are `[ASSUMED]`, the rest are `[VERIFIED]`.

## Open Questions

1. **Where does the v1.11 UAT-02 result-slot live?**
   - What we know: `.planning/milestones/v1.x-MILESTONE-AUDIT.md` is the established pattern (8 such files exist for v1.0..v1.10). `.planning/milestone-audits/` directory does NOT exist. `docs/uat/` directory does NOT exist.
   - What's unclear: whether v1.11's audit will exist as a single `.planning/milestones/v1.11-MILESTONE-AUDIT.md` file (created at milestone close after Phase 87), or whether a per-phase `.planning/milestone-audits/v1.11-UAT-02.md` is acceptable.
   - Recommendation: **Option A (preferred):** Add a `Pending UATs` subsection to `.planning/STATE.md` at Phase-83 close, with the empty UAT-02 row. The v1.11 MILESTONE-AUDIT.md (created at milestone close per existing pattern) will absorb the row at audit time. Lightest weight, no new directory, no new file. **Option B:** Create `.planning/milestones/v1.11-UAT-02.md` as a standalone file. More discoverable but adds a 1-purpose file. Planner picks per discretion.

2. **Does `MatchdayGeneratorGroupsE2E` need pre-seeded GROUPS-Saison fixtures, or can it create them inline?**
   - What we know: `GroupsSeasonE2ETest.java` (existing) creates its own GROUPS-Saison fixture in `@BeforeEach` (Test-prefix names, idempotent cleanup). Total wall-clock ~25-30s including Playwright browser start.
   - What's unclear: whether the planner wants `MatchdayGeneratorGroupsE2E` to clone the full setup or to be a thinner test that operates only on the FORM (not the full matchday-generation lifecycle).
   - Recommendation: Thin test — create a Test-prefix GROUPS-Saison with 4 teams + 2 groups + 1 REGULAR phase (no matchdays), navigate to the generate-form, assert the group `<select>` appears, submit with `groupId=group_A.id`, assert redirect with success flash. Don't assert downstream matchday creation (that's `MatchdayGeneratorServiceTest`'s job). Target wall-clock < 15s.

3. **Should DevDataSeederLocalProfileIT (D-15) be added?**
   - What we know: Minimal IT shape `applicationContext.getBean(DevDataSeeder.class) != null` under `@ActiveProfiles("local")` — but `local` profile requires MariaDB JDBC URL pointing at `localhost:3306` (per `application-local.yml`). H2 testcontainers won't satisfy this URL.
   - What's unclear: whether a `@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:local-it")` shim is acceptable to override the MariaDB URL during the IT, or whether to skip the test.
   - Recommendation: **Skip.** The annotation change is one-line and human-eye-reviewable. The `DevDataSeederLocalProfileIT` would either (a) require a MariaDB testcontainer (expensive — adds ~10s wall-clock), or (b) override the JDBC URL to H2 (defeats the test purpose — it's no longer testing the `local` profile authentically). The QUAL-02 verification commit's manual `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` smoke (D-30) provides authentic proof.

## Environment Availability

> Phase 83 is primarily code/config-only changes. External-dependency surface is minimal but non-zero (Java 25, Maven, Chromium for Playwright E2Es). Quick audit:

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | Compile + run all phases | (assume yes — Phase 82 just ran `./mvnw verify -Pe2e` green per 82-VERIFICATION.md) | 25.x | — |
| Maven Wrapper (`./mvnw`) | All build/test commands | yes (`mvnw` checked into repo root) | 3.9.x | — |
| Chromium (Playwright) | QUAL-01 + QUAL-03 E2E tests | (assumed pre-installed per CLAUDE.md `## Commands` Playwright install line) | 1.58.0 | `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` per CLAUDE.md |
| MariaDB localhost:3306 | QUAL-02 manual `local` smoke (D-30) | optional (operator-dependent) | 10.x or 11.x | If absent: skip the manual `local` smoke, rely on annotation-edit + visual inspection. The Phase-end `./mvnw verify -Pe2e` covers the bean-wiring on `@ActiveProfiles("dev")` regardless. |
| Docker | docker-compose stack for local MariaDB testing | optional | — | Same fallback as MariaDB above |

**Missing dependencies with no fallback:** none.

**Missing dependencies with fallback:** MariaDB / Docker — QUAL-02 manual `local` smoke deferred to operator with Docker; the test gate stays green without it.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + AssertJ + Playwright 1.58.0 + Spring Boot 4 MockMvc |
| Config file | `pom.xml` (Surefire lines 266-289, Failsafe lines 290-314, JaCoCo 315-371, SpotBugs 372-405) |
| Quick run command | `./mvnw test -Dtest=<ClassName> -DfailIfNoTests=false` (unit/integration) |
| Full suite command | `./mvnw verify -Pe2e` (Surefire + Failsafe default-it + Failsafe e2e-it) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QUAL-01 | `Driver.seasonDrivers` re-fetch returns rows ordered ascending by `season.year`, then `season.number` | integration (repo-IT) | `./mvnw test -Dtest=DriverRepositoryOrderIT -DfailIfNoTests=false` | ❌ Wave 0 — `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` |
| QUAL-01 | Driver-detail admin page renders chips in ascending-year order | e2e | `./mvnw verify -Pe2e -Dit.test=DriverDetailSmokeE2E -DfailIfNoTests=false` | ❌ Wave 0 — `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java` |
| QUAL-02 | `DevDataSeeder` bean is registered under `local` profile (and `dev` profile still works) | manual + optional integration (D-15 discretion) | Manual: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` → check log for `Seed data created` / `Seed data already present, skipping` line | Optional Wave 0 — `src/test/java/org/ctc/admin/DevDataSeederLocalProfileIT.java`; planner skips per Open Question 3 |
| QUAL-02 | `dev` profile end-to-end run unchanged | existing E2E suite | `./mvnw verify -Pe2e` (catches any regression) | ✅ — existing 8 E2E classes regress-guard |
| QUAL-03 | `matchday-generator.html` renders the group `<select>` only for GROUPS-layout phases | integration (controller test via MockMvc) | `./mvnw test -Dtest=SeasonController*` (asserts `model().attributeExists("phase")`; planner adds an assertion that for a GROUPS-layout phase, the rendered HTML contains `name="groupId"`) | ✅ existing `SeasonController*Test`; ❌ new MockMvc assertion in same file |
| QUAL-03 | End-to-end matchday generation submission with `groupId` parameter on a Test-prefix GROUPS season | e2e | `./mvnw verify -Pe2e -Dit.test=MatchdayGeneratorGroupsE2E -DfailIfNoTests=false` | ❌ Wave 0 — `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java` |
| QUAL-03 | `MatchdayGeneratorService.generate(...)` already validates non-null groupId for GROUPS | unit | `./mvnw test -Dtest=MatchdayGeneratorService*` (existing; regression check only) | ✅ existing |
| QUAL-04 | `StandingsViewService.buildView(...)` resolves each branch correctly (alltime / explicit phase / legacy seasonId / active fallback / no-match) | unit | `./mvnw test -Dtest=StandingsViewServiceTest -DfailIfNoTests=false` | ❌ Wave 0 — `src/test/java/org/ctc/domain/service/StandingsViewServiceTest.java` |
| QUAL-04 | `StandingsController.standings(...)` model attribute names + view name unchanged after refactor | integration | `./mvnw test -Dtest=StandingsControllerTest -DfailIfNoTests=false` | ✅ existing `StandingsControllerTest.java` — file exists; assertions need to stay green under Option (a) |
| QUAL-04 | Standings page visual output unchanged (existing standings rendering) | manual + existing-E2E-coverage | `./mvnw verify -Pe2e` (catches template regression); operator visual check on `/admin/standings?phase=...` post-refactor | ✅ — relies on existing E2E + manual review |
| QUAL-05 | Procedure doc readable + result-slot exists | docs review | `git diff --stat docs/uat/UAT-02-legacy-season-smoke.md` + `git diff .planning/STATE.md` (or `.planning/milestones/v1.11-UAT-02.md`) | ❌ Wave 0 — `docs/uat/UAT-02-legacy-season-smoke.md`; ❌ Wave 0 — result-slot file/section |

### Sampling Rate

- **Per task commit:** Targeted `-Dtest=` / `-Dit.test=` per CONTEXT.md D-03 (no full `verify` between commits).
- **Per wave merge:** N/A — Phase 83 doesn't use wave-merge orchestration; the 5 QUAL-IDs are sequential and one-commit-each.
- **Phase gate:** `./mvnw verify -Pe2e` green before `83-VERIFICATION.md` is written; JaCoCo ≥ 87.30% (87.80% v1.10 baseline − 0.5pp comfort buffer); SpotBugs `BugInstance size 0`.

### Wave 0 Gaps

- ❌ `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` — covers QUAL-01 (`@OrderBy` correctness)
- ❌ `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java` — covers QUAL-01 (visual chip-order)
- ❌ `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java` — covers QUAL-03 (form UI + submit)
- ❌ `src/test/java/org/ctc/domain/service/StandingsViewServiceTest.java` — covers QUAL-04 (branch resolution)
- (Optional) ❌ `src/test/java/org/ctc/admin/DevDataSeederLocalProfileIT.java` — covers QUAL-02 (bean activation under `local`). Planner skips per Open Question 3.
- ✅ `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java` — exists; QUAL-04 updates assertions in-place
- ✅ Framework + test infrastructure (JUnit 5, Mockito, AssertJ, Playwright, MockMvc) — all present, no new dependency

*Framework install:* none — all needed test infrastructure already wired in `pom.xml`.

## Security Domain

> Phase 83 does not introduce auth, session, access-control, input-validation, or cryptography surfaces beyond what already exists. The only user-visible HTTP surface change is QUAL-03's added `groupId` form field, which is treated as a UUID and validated server-side by `seasonPhaseGroupRepository.findById(groupId).orElseThrow(...)` at `MatchdayGeneratorService.java:76-77`. No SQL injection vector (UUID typing); no XSS vector (server-side rendering); no auth change.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | n/a — Phase 83 touches no auth surface |
| V3 Session Management | no | n/a |
| V4 Access Control | no | n/a — admin routes already gated by `SecurityConfig` (prod/docker) / `OpenSecurityConfig` (dev/local) |
| V5 Input Validation | yes | UUID typing for `groupId`; service-layer `EntityNotFoundException` on missing group; `@Valid` + `BindingResult` on form |
| V6 Cryptography | no | n/a |

### Known Threat Patterns for Spring Boot 4 + Thymeleaf admin app

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Mass assignment via form binding | Tampering | Form DTOs (not entities) for POST — `MatchdayGeneratorForm` already follows. QUAL-03 adds a `groupId` field to the DTO; not the entity. ✅ |
| Template SpEL injection | Injection | Thymeleaf 3.1.5 pinned (CVE-2026-40478 mitigation per DEPS-04). Phase 83 templates use `th:if`, `th:text`, `th:value`, `th:field`, `th:each` only — no `[[...]]` inline expressions, no dynamic-expression evaluation. ✅ |
| Lazy-collection traversal exposing entity internals | Information Disclosure | OSIV bounded to admin templates; QUAL-04 explicitly moves lazy traversal OUT of the controller and into a `@Transactional(readOnly=true)` service method. ✅ |
| Profile leakage (dev seeder activating in prod) | Tampering | `DemoDataSeeder` stays `@Profile("demo")` (never on prod); `DevDataSeeder` widens to `{"dev","local"}` — NOT to `{"dev","local","prod","docker"}`. Production runs `prod` / `docker` profiles which DON'T match. ✅ |

## Sources

### Primary (HIGH confidence)

- `src/main/java/org/ctc/domain/model/Driver.java:36-37` — current `@OneToMany seasonDrivers` field (target for QUAL-01)
- `src/main/java/org/ctc/domain/model/Season.java:24-37` — verifies `season.startDate` does NOT exist; `season.number` is the correct secondary sort key (Pitfall #2)
- `src/main/java/org/ctc/domain/model/SeasonPhase.java:64-66` — project-precedent `@OrderBy("sortIndex ASC")` shape
- `src/main/java/org/ctc/admin/DevDataSeeder.java:12` — current `@Profile("dev")` (target for QUAL-02 widening)
- `src/main/java/org/ctc/admin/TestDataService.java:40,67-80` — `@Profile("dev")` (must widen per Pitfall #1) + idempotent seed() pattern
- `src/main/java/org/ctc/admin/OpenSecurityConfig.java:12` — precedent for `@Profile({"dev","local"})`
- `src/main/java/org/ctc/admin/controller/SeasonController.java:226-258` — QUAL-03 touch surface (generateForm GET + generate POST)
- `src/main/java/org/ctc/admin/controller/StandingsController.java:32-163` — QUAL-04 refactor surface
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java:42-77,213-214` — confirms groupId param flow + `GeneratorFormData` record precedent
- `src/main/java/org/ctc/domain/service/StandingsService.java:292-339` — `TeamStanding` inner class shape (record field types)
- `src/main/java/org/ctc/domain/service/DriverRankingService.java:212-260` — `DriverRanking` inner class shape
- `src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java` — DTO shape for QUAL-03 extension
- `src/main/resources/templates/admin/matchday-generator.html` — QUAL-03 template touch surface
- `src/main/resources/templates/admin/standings.html` — QUAL-04 template attribute reads (verifies Option a viability)
- `config/spotbugs-exclude.xml` — confirms `admin.dto` package-level `EI_EXPOSE_REP/REP2` suppression already covers `StandingsView`
- `src/test/java/org/ctc/domain/repository/SeasonPhaseRepositoryIT.java:1-30` — repo-IT precedent (`@SpringBootTest` not `@DataJpaTest`)
- `src/test/java/org/ctc/e2e/GroupsSeasonE2ETest.java` — closest E2E precedent for GROUPS-layout fixture setup
- `src/test/java/org/ctc/e2e/PlaywrightConfig.java` — base class for both new E2E tests
- `src/test/java/org/ctc/admin/controller/StandingsControllerTest.java:69-119` — existing assertion shapes (Option a vs b decision)
- `.planning/codebase/CONVENTIONS.md` — Lombok / DTO / Controller patterns
- `.planning/codebase/TESTING.md` — `@Tag` routing + Test Invocation Discipline
- `.planning/codebase/ARCHITECTURE.md` — OSIV semantics + layer separation
- `.planning/phases/82-backup-cleanup/82-VERIFICATION.md` — v1.10 baseline 87.80% JaCoCo + 1655 tests + 36 E2E counts
- `.planning/milestones/v1.10-MILESTONE-AUDIT.md` — confirms no external Saison-2023 bootstrap artifact (validates D-10)
- `CLAUDE.md` — project conventions + invariants

### Secondary (MEDIUM confidence)

- Spring Boot 4 `@Profile` multi-value semantics — verified by 4 existing in-repo usages (`OpenSecurityConfig`, `SecurityConfig` `{"prod","docker"}`, `DemoDataSeeder` single-value)
- JPA `@OrderBy` nested-path support — verified by Hibernate's documented behaviour and 4 in-repo usages (Season.cars, Season.tracks, SeasonPhase.groups, SeasonPhase.matchdays); the *nested-path* variant for QUAL-01 is consistent with Hibernate's documented extension over the JPA spec proper

### Tertiary (LOW confidence)

- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; everything used is already in the project
- Architecture: HIGH — pattern matches 4 existing in-repo precedents per pattern
- Pitfalls: HIGH — Pitfall #1 + #2 are both verified by source reads; Pitfall #4 confirmed by reading `TestDataService` javadoc and `MatchdayGeneratorService.generate(...)` pre-existing matchday check; Pitfall #6 confirmed by reading the existing controller test assertions

**Research date:** 2026-05-17
**Valid until:** 2026-06-16 (30 days — Phase 83 touches stable code paths with no fast-moving external surface)

## Project Constraints (from CLAUDE.md)

The following CLAUDE.md directives apply to Phase 83 implementation. The planner MUST verify compliance:

- Test coverage minimum 82% line coverage maintained (target hold 87.80% ± 0.5pp).
- Do not change existing Flyway V1.. migrations — Phase 83 needs no migration.
- Profiles: auth only for prod/docker; QUAL-02 widening DevDataSeeder to `local` stays within OpenSecurityConfig's `{"dev","local"}` permit-all surface (no auth regression).
- OSIV remains enabled — QUAL-04 explicitly moves lazy traversal into a `@Transactional` service method (controller stays OSIV-free at point of execution).
- No breaking changes to existing URLs/endpoints — Phase 83 changes only INTERNAL controller method bodies; no URL/route change.
- Playwright stays compile-scope — Phase 83 adds 2 new E2E tests; no scope change.
- All controllers delegate to services — QUAL-04 enforces this for StandingsController.
- No business logic in templates — QUAL-03 conditional uses `phase.layout.name() == 'GROUPS'` (enum-name comparison only; no computation).
- DTOs for POST — QUAL-03 `MatchdayGeneratorForm` already a DTO; adds field, not entity.
- No fallback calculations in templates/controllers — QUAL-04 explicitly removes the controller's data-shaping fallback paths.
- Templates lean (no SpEL projections / nested conditions) — QUAL-03 + QUAL-04 templates use simple `th:if` and `th:text`.
- No inline styles on buttons — Phase 83 adds no buttons; QUAL-03 form group reuses existing `form-group` class.
- Isolate test data completely (test-prefix entities) — `DriverRepositoryOrderIT` uses `Phase83-Order-*` names; `MatchdayGeneratorGroupsE2E` uses `T-MGEN-*` / Test-prefix Season 2098 names.
- Tag tests by category (`@Tag`) — all 4 new test classes tagged correctly.
- RaceLineup is Source of Truth — not touched by Phase 83.
- TDD: write tests first, then implementation; BDD given/when/then naming — applied to all new tests.
- Visual verification with playwright-cli for UI changes — QUAL-03 template change (visible new `<select>`) requires Playwright visual check at the discretion of the planner (or relies on the new E2E + screenshots in `.screenshots/`).
- JaCoCo minimum documented in `pom.xml` — verified at line 358 `<minimum>0.82</minimum>`.
- Default branch master; PRs only; Conventional Commits — Phase 83 closes WITHOUT a PR per D-31; commits use `feat(83): ...`, `test(83): ...`, `chore(83): ...`, `refactor(83): ...`, `docs(83): ...` per CONTEXT.md D-02.
- Subagent rules — if planner dispatches subagents per QUAL-ID, prompts MUST name `gsd/v1.11-tooling-and-cleanup` and forbid branch switching.
- SpotBugs gate + suppression discipline — verified `admin.dto` package-level suppression covers `StandingsView`. No new entry to `config/spotbugs-exclude.xml` required.
- `lombok.config` invariant — NOT touched by Phase 83.

---

*Phase: 83-quality-and-polish-sweep*
*Research completed: 2026-05-17*
