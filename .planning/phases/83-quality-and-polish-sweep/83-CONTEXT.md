# Phase 83: Quality and Polish Sweep - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Resolve the four v1.9/v1.10 carryover tech-debt items (QUAL-01..QUAL-04) and ship a deliverable test-script + result-slot for the post-deploy UAT-02 legacy-season visual smoke (QUAL-05). All changes land directly on the milestone branch `gsd/v1.11-tooling-and-cleanup` — **no new feature/fix sub-branches for this phase** (see D-21).

**In scope (NEW work):**

- **QUAL-01** — Add `@OrderBy("season.year ASC, season.startDate ASC")` to `Driver.seasonDrivers` so `driver-detail.html`'s Season-Assignment chips render in ascending year order. SC#1 requires both a domain repository IT and a Playwright smoke test.
- **QUAL-02** — Widen `DevDataSeeder` from `@Profile("dev")` to `@Profile({"dev", "local"})` so `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` auto-seeds via `TestDataService.seed()` (idempotent — early-return when `seasonRepository.count() > 0`). The "separate Saison-2023 fixture path" mentioned in SC#2 refers to the external bootstrap requirement, NOT to the 2023-specific code inside `TestDataService` (which is the canonical seed and stays).
- **QUAL-03** — Add a per-group `<select>` to `matchday-generator.html` (conditional `th:if="${phase.layout.name() == 'GROUPS'}"`) and wire it through `MatchdayGeneratorForm` so `SeasonController#generate` (line 251) no longer hardcodes `groupId=null`. For SINGLE-layout seasons the field stays hidden and the post body carries `groupId=null` (current behaviour preserved). Verified by a Playwright E2E test on Season 2023 (GROUPS-layout, Group A + Group B).
- **QUAL-04** — Replace `resolvedPhase.getGroups()` lazy access at `StandingsController:138` with a full `StandingsViewService.buildView(phase, group, seasonId)` returning a `StandingsView` record (groups, standings, ranking, flags). Controller becomes plumbing-only. Removes OSIV-lazy traversal in controller code per CLAUDE.md "Keep Controllers Thin".
- **QUAL-05** — Create `docs/uat/UAT-02-legacy-season-smoke.md` documenting the post-deploy smoke procedure (production URL, expected screenshots, pass/fail criteria) AND reserve an empty result slot in the v1.11 milestone-audit artifact. Phase-83 closes when the procedure-doc + result-slot exist — the live execution happens after v1.11 production deploy and is recorded then.
- **Phase-end verification** — single `./mvnw verify -Pe2e` run on the milestone branch confirming JaCoCo ≥ 82 % (target: hold v1.10 baseline 87.80 % ± 0.5 pp), SpotBugs gate green, all Surefire + Failsafe + Playwright E2E green.

**Out of scope (deliberate):**

- Modifying any existing Flyway `V*.sql` migration (CLAUDE.md invariant).
- Adding a new Flyway migration — QUAL-01..04 touch only Java code, templates, application.yml is not affected, and existing tables already contain the columns needed.
- Adding new CSS classes or inline-styles — QUAL-03 form change reuses existing `form-group` / `form-check` classes (per CLAUDE.md "No Inline Styles").
- Touching `DemoDataSeeder` profile annotation — stays `@Profile("demo")` (QUAL-02 explicitly scoped to DevDataSeeder).
- Refactoring other OSIV-lazy spots beyond `StandingsController:138` (e.g., other controllers may still have lazy access) — Phase-83 scope is exactly QUAL-04 line 138. Broader OSIV-cleanup is a future milestone.
- Bumping `BackupSchema.SCHEMA_VERSION` or touching backup wire contract — out of Phase-83 scope (Phase 82 already locked the guard).
- Executing UAT-02 against a live production deploy during the phase — UAT-02 is post-deploy verification by design (see QUAL-05 above).
- Creating per-phase `feature/quality-and-polish-sweep` branch — all commits land on `gsd/v1.11-tooling-and-cleanup` per `feedback_milestone_branch.md` (D-21).

</domain>

<decisions>
## Implementation Decisions

### Branch & Commit Strategy

- **D-01:** **All Phase-83 commits land directly on the milestone branch `gsd/v1.11-tooling-and-cleanup`.** No per-phase `feature/...`-Branch. Confirmed by user 2026-05-17 ("auf aktuellem Branch bleiben, keine neuen Branches/PRs für diesen Meilenstein"). Reinforced in `feedback_milestone_branch.md` — Phase-CONTEXT.md files from Phase 80 / 82 (which DID suggest sub-branches) are historical mistakes, not precedents. Planner MUST NOT propose any `git checkout -b`.
- **D-02:** **One atomic commit per QUAL-ID + one verification commit.** Commit messages reference the requirement ID per Phase 75/82 precedent. Final shape:
  1. `feat(83): QUAL-01 add @OrderBy season.year ASC on Driver.seasonAssignments`
  2. `test(83): QUAL-01 SeasonAssignmentOrderIT + driver-detail Playwright smoke`
  3. `chore(83): QUAL-02 widen DevDataSeeder profile to {dev,local}`
  4. `feat(83): QUAL-03 add per-group selector to matchday generator form`
  5. `test(83): QUAL-03 Season-2023 per-group matchday-generate E2E`
  6. `refactor(83): QUAL-04 StandingsViewService + StandingsView DTO (controller thin)`
  7. `test(83): QUAL-04 StandingsControllerIT covers ViewService injection path`
  8. `docs(83): QUAL-05 UAT-02 procedure + milestone-audit slot`
  9. `docs(83): verification report (./mvnw verify -Pe2e green, JaCoCo ≥ 87.30 %)`
  Planner reserves the right to merge QUAL-01 code + test into ONE commit if the test already accompanies the feature in TDD shape — same for QUAL-03 and QUAL-04 (preference is "test-with-feature" atomic units, falling back to separate commits when the test naturally lags). The Planner picks per item.
- **D-03:** **Test cadence per commit:** Targeted tests only, NO full `./mvnw verify` between intermediate commits (per `feedback_test_call_optimization`).
  - QUAL-01 commits → `./mvnw test -Dtest='SeasonAssignmentOrderIT,*DriverDetailIT*' -DfailIfNoTests=false` + `./mvnw verify -Pe2e -Dit.test='DriverDetailSmokeE2E*'`
  - QUAL-02 commit → `./mvnw test -Dtest='DevDataSeeder*' -DfailIfNoTests=false` + manual `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` smoke (or `@ActiveProfiles("local")` IT if MariaDB testcontainers are wired)
  - QUAL-03 commits → `./mvnw test -Dtest='SeasonController*,MatchdayGenerator*' -DfailIfNoTests=false` + `./mvnw verify -Pe2e -Dit.test='MatchdayGenerator*E2E'`
  - QUAL-04 commits → `./mvnw test -Dtest='StandingsView*,StandingsController*'` + `./mvnw verify -DfailIfNoTests=false`
  - QUAL-05 commit → docs-only, no Java compile — skip per-commit tests
  - **Phase-end:** ONE final `./mvnw verify -Pe2e` confirming SpotBugs gate green + 1011+ tests + 36 Playwright E2E pass + JaCoCo ≥ 87.30 % (87.80 % v1.10 baseline - 0.5 pp comfort buffer; gate is 82 %).

### QUAL-01: Driver-Detail Season-Assignment Chip Order

- **D-04:** **`@OrderBy("season.year ASC, season.number ASC")` annotation** on `Driver.seasonDrivers` (entity field `private List<SeasonDriver> seasonDrivers` at `Driver.java:37`). Hibernate emits the `ORDER BY` in the lazy-fetch SQL — JPA-managed, minimal-invasive, no service-layer change. Secondary sort by `season.number` covers split seasons in the same year (e.g., Season 2024-1 vs. Season 2024-2). ROADMAP-SC#1 wording (`ORDER BY year ASC`) matches the primary sort.
  **Correction 2026-05-17 (post-research):** Initial draft used `season.startDate` — that field does NOT exist on `Season` (entity has only `year`, `number`, `name`, `description`, `active` per `Season.java:25-37`). Secondary sort moved to `season.number`.
- **D-05:** **`driver-detail.html` template untouched.** Already iterates `${driver.seasonDrivers}`; the annotation ensures the iteration order is correct without template changes. No `th:sort` / no chip-list refactor needed.
- **D-06:** **Repository-IT** in `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` (tag: `@Tag("integration")`). Seeds a Driver with 3 SeasonDriver rows referencing Seasons (2024-1, 2023-1, 2025-1), reloads the driver via the repository, asserts `seasonDrivers` list-order matches `[2023-1, 2024-1, 2025-1]`. Tests the JPA `@OrderBy` is respected on the SQL layer, NOT a Java-side `Comparator.comparing` mirage. Single `@Test`.
- **D-07:** **Playwright smoke** in `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java` (tag: `@Tag("e2e")`). Navigates to `/admin/drivers/{id}` of a driver with multi-season chips, asserts chip-list text-order matches ascending year. Reuses existing E2E fixture (Test-prefix driver from TestDataService). Single `@Test`.
- **D-08:** **No DB migration.** `@OrderBy` is a JPA-runtime annotation, no schema change. CLAUDE.md Flyway invariant respected.

### QUAL-02: DevDataSeeder Profile Widening

- **D-09:** **`DevDataSeeder` AND `TestDataService` annotation change** from `@Profile("dev")` to `@Profile({"dev", "local"})`. Edits at `src/main/java/org/ctc/admin/DevDataSeeder.java:12` AND `src/main/java/org/ctc/admin/TestDataService.java:40`. Both files must be widened in the SAME atomic commit.
  **Correction 2026-05-17 (post-research):** Initial draft named only DevDataSeeder. Researcher discovered `TestDataService` is also `@Profile("dev")`-gated (line 40), so widening DevDataSeeder alone would cause `NoSuchBeanDefinitionException` at `local`-profile startup (DevDataSeeder's `final TestDataService testDataService` injection would fail to resolve). Both Spring components must be widened together.
- **D-10:** **No removal of "Saison-2023 fixture path" inside `TestDataService`.** Investigation during discuss-phase confirmed the 2023-specific blocks at `TestDataService.java:170-179, 325-331, 473-485, 925-948` are the CANONICAL seed (the consolidated GROUPS-layout fixture covering SC1/SC2/SC3). The ROADMAP-SC#2 wording "without requiring a separate Saison-2023 fixture path" refers to the EXTERNAL bootstrap step that was previously needed when `DevDataSeeder` didn't activate on `local` — i.e., the operator had to manually bootstrap a 2023 season via Admin-UI / SQL. The annotation widening IS the elimination of that external path.
- **D-11:** **Researcher action:** before locking in D-10, grep the repo + README + `docs/` for any external bootstrap reference (e.g., README "## Local Development" section, `data/local/` import scripts, any `import.sql` / Flyway-callback that runs only on `local`). If a separate Saison-2023 bootstrap artifact exists, it gets removed in the same commit as D-09. If nothing is found, D-09 alone satisfies SC#2.
- **D-12:** **`TestDataService.seed()` idempotency safeguard.** `seed()` already early-returns when `seasonRepository.count() > 0` (line 69-72). On `local` profile against MariaDB with pre-existing data, the seeder logs "Seed data already present, skipping" and the bootstrap is a no-op. No new safety check needed.
- **D-13:** **`DevDataSeeder` keeps SiteGeneratorService call** for both `dev` AND `local`. Site-generation under `local` runs against MariaDB-backed data and writes to the configured site output dir (`docs/site` by default per CLAUDE.md, or `target/site` per profile-override if present). Site-gen is `try-catch` wrapped — non-fatal on failure. **Discretion:** if planner discovers `local` profile shouldn't write site output (e.g., to keep `docs/site/` clean of local-dev artifacts), the call site can be made profile-conditional. Default is to leave it.
- **D-14:** **DemoDataSeeder unchanged.** `@Profile("demo")` stays. Combined `dev,demo` and `local,demo` profile combinations still work because `demo` is a separate profile that activates DemoDataSeeder independently.
- **D-15:** **No new test required if the Planner can rely on `@ActiveProfiles("local")` slice IT being added.** A minimal `DevDataSeederLocalProfileIT` asserting the bean is loaded under `local` profile (e.g., `applicationContext.getBean(DevDataSeeder.class)` returns non-null) is the lightest possible coverage. **Discretion:** Planner can drop the test if MariaDB-testcontainer cost is too high — the annotation change is so small that human-eye review suffices.

### QUAL-03: Per-Group Matchday Generation UI

- **D-16:** **Form-side change in `matchday-generator.html`.** Add a `<select name="groupId">` field rendered ONLY when `phase.layout == GROUPS`. Options sourced from `phase.groups` (sorted by `group.name`). Conditional Thymeleaf wrapper:
  ```html
  <div class="form-group" th:if="${phase.layout.name() == 'GROUPS'}">
      <label for="groupId">Group</label>
      <select id="groupId" th:field="*{groupId}" required>
          <option th:each="g : ${phase.groups}" th:value="${g.id}" th:text="${g.name}"></option>
      </select>
  </div>
  ```
- **D-17:** **`MatchdayGeneratorForm` DTO** at `src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java` gains a `private UUID groupId;` field with `@Setter`/`@Getter` via Lombok. For SINGLE-layout submissions, `groupId` stays `null` (current behaviour preserved). NO `@NotNull` validation on the form field — validation is done at the service layer where `MatchdayGeneratorService:49-51` already throws on `GROUPS && groupId==null`. **Why no `@NotNull`:** the form must accept `null` for SINGLE-layout, so a Spring-validation rule would either over-reject or require conditional grouping.
- **D-18:** **Controller call-site update** at `SeasonController.java:251`. Replace `matchdayGeneratorService.generate(regular.getId(), null, ...)` with `matchdayGeneratorService.generate(regular.getId(), form.getGroupId(), ...)`. Single-line change. The service already throws `IllegalArgumentException` for GROUPS+null, surfaces as flash-error via the existing `catch` block at line 253-256 — no new error handling needed.
- **D-19:** **`generateForm` GET endpoint** at `SeasonController.java:226-238` needs to expose `phase.groups` to the template. Add `model.addAttribute("phase", seasonPhaseService.findRegularPhase(id));` so the template can read `${phase.layout}` and iterate `${phase.groups}`. Verify `getGroups()` is not OSIV-lazy in a way that breaks under the new template iteration — if the relationship is lazy, the GET handler must call `phase.getGroups().size()` (or similar) inside the open transaction OR use a service method like `seasonPhaseService.findRegularPhaseWithGroups(id)` (eager-fetch). **Researcher action:** check `SeasonPhase.groups` field for `FetchType.LAZY` and confirm whether OSIV currently makes this work — adjust strategy accordingly.
- **D-20:** **Playwright E2E** in `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java` (tag: `@Tag("e2e")`). Navigates to `/admin/seasons/{2023-id}/generate`, asserts the group `<select>` is rendered with options (Group A, Group B), selects Group A, submits with `numberOfRounds=2 homeAndAway=false`, verifies redirect to `/admin/seasons/{id}` with flash success, verifies matchdays were created tagged to Group A only (NOT Group B). Optional follow-up: repeat for Group B in the same test. Reuses Test-prefix Season 2023 fixture if present, or relies on the regular `2023-1-season-2023` slug (acceptable for E2E since the test does generate ONLY, no destructive operations on existing matchdays — but the planner should confirm no matchday-uniqueness collision against the seeded fixture).

### QUAL-04: StandingsView Service-Layer Refactor

- **D-21:** **New record `StandingsView`** at `src/main/java/org/ctc/admin/dto/StandingsView.java`:
  ```java
  public record StandingsView(
      SeasonPhase phase,
      List<SeasonPhaseGroup> groups,
      List<StandingsRow> standings,
      List<DriverRankingRow> driverRanking,
      UUID selectedGroupId,
      boolean hasRegularPhase,
      boolean combinedView,
      boolean showGroupColumn,
      boolean showBuchholz
  ) {}
  ```
  Field types match what the template currently expects (`StandingsRow`, `DriverRankingRow` per their existing definitions). Carries ALL state the standings.html template reads — Controller no longer needs to call any lazy-collection getter.
- **D-22:** **New service `StandingsViewService`** at `src/main/java/org/ctc/domain/service/StandingsViewService.java` with single public method:
  ```java
  public StandingsView buildView(UUID phaseId, UUID groupId, UUID seasonId, boolean isAlltime);
  ```
  Encapsulates the entire resolution flow currently in `StandingsController#standings` (lines 51-160): alltime branch, explicit-phase branch, legacy-seasonId branch, active-season fallback. Returns a populated `StandingsView` OR a "bare-page" `StandingsView` (with `hasRegularPhase=false`) when no phase resolved. The lazy `phase.getGroups()` access happens INSIDE the service inside the transaction — controller never touches the lazy collection.
- **D-23:** **`StandingsController` becomes thin.** Reduces from ~120 lines of resolution-logic to ~25 lines of plumbing:
  ```java
  @GetMapping
  public String standings(@RequestParam(required = false) UUID phase,
                          @RequestParam(required = false) UUID group,
                          @RequestParam(required = false) String seasonId,
                          Model model) {
      model.addAttribute("seasons", seasonManagementService.findAll());
      boolean isAlltime = "alltime".equals(seasonId);
      UUID seasonUuid = parseSeasonUuid(seasonId);  // helper for legacy bridge
      var view = standingsViewService.buildView(phase, group, seasonUuid, isAlltime);
      model.addAttribute("view", view);
      // legacy field unfurl for templates still reading non-view-prefixed attrs:
      model.addAttribute("phase", view.phase());
      model.addAttribute("hasRegularPhase", view.hasRegularPhase());
      // ... etc, OR refactor standings.html to read ${view.X} directly
      return "admin/standings";
  }
  ```
  **Discretion:** Planner picks between (a) keep the flat model.addAttribute calls and have the controller unfurl the view, or (b) refactor `standings.html` to read `${view.phase}`, `${view.standings}`, etc. Option (a) is one-pass-safe (no template changes), Option (b) is cleaner long-term. The 6-pillar-visual-audit memory + `feedback_template_details` lean towards Option (b) — but it expands the surface area.
- **D-24:** **Existing `StandingsService`, `DriverRankingService`, `SeasonPhaseService`, `SeasonManagementService` remain unchanged.** `StandingsViewService` injects all four and composes. No duplication of standings-calculation logic.
- **D-25:** **Test impact:**
  - **Unit test for `StandingsViewService`** — covers each resolution branch (alltime, explicit phase, legacy seasonId resolving, legacy seasonId not resolving, no params + active season, no params + no active season). Plain JUnit + Mockito, no Spring context. **6-7 test methods** following Given/When/Then naming per CLAUDE.md.
  - **Existing `StandingsControllerIT` updates** — the IT covers the controller-template integration. After refactor, it asserts the controller delegates to `StandingsViewService`. Existing test bodies may need updating to mock/wire the new service. Existing assertions on flash-attributes and model-attribute presence must keep passing (D-23 Option a) OR be updated to read `${view.X}` (D-23 Option b).
  - **No new Playwright test required** — visual behaviour of the standings page is unchanged; existing `StandingsE2E` (if any) gates regressions.

### QUAL-05: UAT-02 Post-Deploy Smoke Procedure

- **D-26:** **Procedure document** at `docs/uat/UAT-02-legacy-season-smoke.md`:
  - Title + purpose (verify legacy pre-V4 season data renders correctly on production after v1.11 deploy)
  - Pre-condition (v1.11 deployed to production; production DB has pre-V4 backup imported)
  - Step-by-step procedure (navigate to specific season URLs, take screenshots, compare against ref images)
  - Pass criteria (no rendering errors, all season chips visible, no `lazy-init exception`-like errors in browser console)
  - Fail handling (rollback path: identify which v1.11 commit caused the regression, file follow-up phase)
  - Result-recording template (date/time, executor, pass/fail, screenshots-path)
- **D-27:** **Milestone-audit result-slot.** Add an empty markdown section to the v1.11 milestone-audit template (or to `.planning/STATE.md` "Pending UATs" subsection):
  ```markdown
  ### UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)
  - **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
  - **Status:** [ ] pending — to be executed after v1.11 production deploy
  - **Result:** _(operator fills after execution)_
  - **Date:** _(operator fills)_
  - **Screenshots:** _(operator links)_
  ```
- **D-28:** **Phase-83 closes without live UAT-02 execution.** Closure criterion = the procedure-doc exists AND the result-slot exists. Live execution is a milestone-close gate, not a phase-close gate. Documented in `STATE.md` post-Phase-83.
- **D-29:** **No code change for QUAL-05.** Pure docs commit. SpotBugs / Surefire / Failsafe / Playwright unaffected.

### Verification Strategy

- **D-30:** **Phase-end verification commit shape:** single `83-VERIFICATION.md` artifact summarising:
  - `./mvnw verify -Pe2e` exit-code 0 + total test count vs. baseline
  - JaCoCo coverage percentage (target: hold 87.80 % ± 0.5 pp)
  - SpotBugs report (no new findings)
  - Manual smoke evidence for QUAL-02 (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local` log snippet showing `Seed data created: ...`)
  - Playwright screenshots from QUAL-01 + QUAL-03 E2E runs (.screenshots/ per `feedback_screenshots_folder`)
- **D-31:** **No PR creation in Phase-83.** The phase closes on the milestone branch. The v1.11 milestone PR (gate to `master`) is created at milestone-close after all phases (83-87) are complete. Per `feedback_milestone_branch` + user 2026-05-17 reinforcement.

### Claude's Discretion

- Whether QUAL-01 code commit + test commit are merged into ONE atomic commit or split. Default: split (feature first, test second, per D-02 numbered shape) — but TDD-shape lets the test land first.
- Whether the QUAL-02 `DevDataSeederLocalProfileIT` (D-15) is added or dropped — annotation change is so small that the human-review-only path is defensible.
- Whether QUAL-03 `generateForm` GET handler uses `findRegularPhase(id)` (existing) or a new `findRegularPhaseWithGroups(id)` (eager-fetch) — depends on OSIV behaviour for the `phase.groups` collection during template iteration.
- Whether QUAL-04 chooses Option (a) flat-model-unfurl or Option (b) template-rewrite-to-${view.X}. Option (b) is cleaner but touches more files.
- Whether QUAL-04 covers `StandingsControllerIT` updates as a separate commit or as part of the same `refactor(83): QUAL-04` commit. Same-commit is preferred (atomicity) — but if the test changes are mechanical-only, the planner can split.
- Final wording of `docs/uat/UAT-02-legacy-season-smoke.md` — markdown template is the goal, planner produces.
- Whether QUAL-05 result-slot lives in `STATE.md` or in a new `.planning/milestone-audits/v1.11-UAT-02.md` artifact. STATE.md is simpler; a dedicated file is more discoverable.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements

- `.planning/ROADMAP.md` §"Phase 83: Quality and Polish Sweep" (lines 242-256) — goal, depends-on (Phase 82), 5 requirement IDs (QUAL-01..QUAL-05), 5 success criteria, UI hint: yes
- `.planning/REQUIREMENTS.md` §"Quality & Polish (QUAL)" (lines 39-43) — QUAL-01..QUAL-05 line items
- `.planning/REQUIREMENTS.md` §"Coverage" (lines 136-140) — QUAL-01..05 → Phase 83 mapping (currently all "Pending")
- `.planning/STATE.md` §"Deferred Items" — original carryover entry listing the four QUAL items (Driver-detail chip order, DevDataSeeder profile widening, per-group matchday UI, StandingsController:139 cleanup, UAT-02)
- `.planning/PROJECT.md` §"Current Milestone: v1.11" — milestone scope; Phase-83 PR target is the milestone branch `gsd/v1.11-tooling-and-cleanup`

### Prior Phase Context (carry-forward)

- `.planning/phases/82-backup-cleanup/82-CONTEXT.md` — backup-cleanup decisions, SpotBugs-suppression discipline, one-commit-per-REVIEW-ID pattern (Phase 82 follows this; Phase 83 inherits the same atomic-commit discipline for QUAL-IDs)
- `.planning/phases/82-backup-cleanup/82-VERIFICATION.md` — v1.10 baseline coverage snapshot for the JaCoCo holding target (87.80 % ± 0.5 pp)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-CONTEXT.md` — SpotBugs gate active on every `verify`; suppressions live in `config/spotbugs-exclude.xml`; Lombok `lombok.config` invariant (do not remove the SpotBugs lines)
- `.planning/phases/80-openrewrite-integration/80-CONTEXT.md` — branch-strategy precedent **noted as historical mistake**: this CONTEXT.md and the Phase 82 D-03 both suggested a per-phase `feature/...` branch — Phase 83 explicitly does NOT follow that precedent per D-01 and user correction 2026-05-17

### Codebase Maps

- `.planning/codebase/TESTING.md` §"Test Categorization (@Tag)" — `*IT.java` requires `@Tag("integration")`; `org.ctc.e2e.*` requires `@Tag("e2e")`; QUAL-01 + QUAL-03 + QUAL-04 tests must follow this
- `.planning/codebase/CONVENTIONS.md` §"Controller & DTO Patterns" — DTOs over Entities in POST forms (QUAL-03 `MatchdayGeneratorForm` already follows; QUAL-04 `StandingsView` record matches the DTO-for-display pattern)
- `.planning/codebase/CONVENTIONS.md` §"Lombok Usage" — `@Slf4j @Component @RequiredArgsConstructor` order on services (QUAL-04 `StandingsViewService` follows this)
- `.planning/codebase/ARCHITECTURE.md` — OSIV-enabled, controllers stay thin, services own business logic (QUAL-04 is a direct application of these principles)
- `.planning/codebase/STRUCTURE.md` — package layout `org.ctc.admin.controller` / `org.ctc.admin.dto` / `org.ctc.domain.service` (QUAL-04 service in `org.ctc.domain.service`, DTO in `org.ctc.admin.dto`)
- `.planning/codebase/STACK.md` — Spring Boot 4.0.6, JUnit 5, Mockito, Playwright; QUAL-01 + QUAL-03 Playwright tests inherit existing E2E pattern

### Live Source (Phase 83 touch list)

- `src/main/java/org/ctc/domain/model/Driver.java`:
  - Line 37 `private List<SeasonDriver> seasonDrivers = new ArrayList<>();` — QUAL-01 adds `@OrderBy("season.year ASC, season.startDate ASC")` immediately above this field
- `src/main/java/org/ctc/admin/DevDataSeeder.java`:
  - Line 12 `@Profile("dev")` — QUAL-02 changes to `@Profile({"dev", "local"})`
- `src/main/java/org/ctc/admin/TestDataService.java`:
  - Line 40 `@Profile("dev")` — **QUAL-02 ALSO changes to `@Profile({"dev", "local"})`** (must be widened together with DevDataSeeder — see D-09 correction)
  - Line 67-72 `@Transactional public void seed()` with `seasonRepository.count() > 0` early-return — idempotency confirmation for QUAL-02 (no change needed)
  - Lines 170-179, 325-331, 473-485, 925-948 (Season 2023 fixture blocks) — these stay, they are the canonical seed
- `src/main/java/org/ctc/admin/controller/SeasonController.java`:
  - Line 226-238 `generateForm` GET — QUAL-03 adds `phase` model attribute for template iteration
  - Line 240-258 `generate` POST — QUAL-03 changes line 251 from `null` to `form.getGroupId()`
- `src/main/java/org/ctc/admin/dto/MatchdayGeneratorForm.java`:
  - Existing DTO — QUAL-03 adds `private UUID groupId;` field
- `src/main/java/org/ctc/domain/service/MatchdayGeneratorService.java`:
  - Line 42-51 `generate(phaseId, groupId, numberOfRounds, homeAndAway)` — already accepts non-null `groupId`; QUAL-03 wires the existing parameter, no service-level change
- `src/main/java/org/ctc/admin/controller/StandingsController.java`:
  - Entire `standings(...)` method (lines 50-163) — QUAL-04 refactors to delegate to `StandingsViewService`
  - Line 138 specifically (`model.addAttribute("groups", resolvedPhase.getGroups());`) — the lazy-access target named in ROADMAP-SC#4
- `src/main/resources/templates/admin/driver-detail.html`:
  - Lines 41-47 — QUAL-01 leaves untouched (annotation drives iteration order)
- `src/main/resources/templates/admin/matchday-generator.html`:
  - Lines 17-28 form-rows — QUAL-03 adds conditional group `<select>` block

### New Files (Phase 83 creates)

- `src/main/java/org/ctc/admin/dto/StandingsView.java` (QUAL-04)
- `src/main/java/org/ctc/domain/service/StandingsViewService.java` (QUAL-04)
- `src/test/java/org/ctc/domain/repository/DriverRepositoryOrderIT.java` (QUAL-01)
- `src/test/java/org/ctc/e2e/DriverDetailSmokeE2E.java` (QUAL-01)
- `src/test/java/org/ctc/e2e/MatchdayGeneratorGroupsE2E.java` (QUAL-03)
- `src/test/java/org/ctc/domain/service/StandingsViewServiceTest.java` (QUAL-04 unit)
- `docs/uat/UAT-02-legacy-season-smoke.md` (QUAL-05)
- Updated milestone-audit slot (QUAL-05) — location decision per D-27/D-32 Discretion: either STATE.md subsection or `.planning/milestone-audits/v1.11-UAT-02.md`

### External (Spring + JPA)

- https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#fetching-ordering — JPA `@OrderBy` semantics for collection ordering (QUAL-01 reference)
- https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles — Spring profile-multi-value annotation syntax `@Profile({"dev","local"})` (QUAL-02 reference)
- https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html#conditional-fragments — Thymeleaf `th:if` conditional rendering (QUAL-03 reference)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`MatchdayGeneratorForm` DTO** — already used as `@ModelAttribute` in `SeasonController#generate`. QUAL-03 extends it with one field; no new DTO needed.
- **`MatchdayGeneratorService.generate(phaseId, groupId, numberOfRounds, homeAndAway)`** — already accepts non-null `groupId` and throws `IllegalArgumentException("GROUPS layout requires non-null groupId")` for the GROUPS+null case (`MatchdayGeneratorService.java:49-51`). QUAL-03 wires existing functionality; no service-level changes.
- **`TestDataService.seed()` idempotency early-return** — covers the QUAL-02 re-run-safety case for `local` profile against pre-populated MariaDB. No new guard logic needed.
- **`StandingsService.calculateStandings(phaseId, groupId)` and `calculateStandingsWithBuchholz(...)`** — already the data-shaping services. QUAL-04 `StandingsViewService` composes these without duplicating their logic.
- **`DriverRankingService.calculateRankingForPhase(phaseId)`** — composed into QUAL-04 `StandingsView`.
- **Existing Playwright E2E base class / fixtures** — `DriverDetailSmokeE2E` and `MatchdayGeneratorGroupsE2E` reuse the established `@Tag("e2e")` + `BasePlaywrightTest` pattern (planner confirms class name).
- **OSIV-aware GET handlers** — `Driver.seasonDrivers` is lazy + accessed in `driver-detail.html`; works today via OSIV. QUAL-01 `@OrderBy` annotation is fully compatible with OSIV.

### Established Patterns

- **One atomic commit per REQ-ID** (Phase 75 + 82 precedent) — Phase 83 follows for QUAL-01..05.
- **DTO record over class** (Java 25 records preferred per recent v1.10 modernisation) — QUAL-04 `StandingsView` uses `record`, not class.
- **Test categorisation by `@Tag`** — CLAUDE.md constraint; QUAL-01 + QUAL-03 + QUAL-04 tests all carry the correct tag.
- **English-only file content** (CLAUDE.md `## Language`) — all new docs, code, comments, log messages in English. Discussion was German; written artifacts are English.
- **No inline styles** (CLAUDE.md "No Inline Styles on Buttons") — QUAL-03 form addition uses existing `form-group` CSS class; no inline style attributes.
- **Form DTOs not entities for POST** — QUAL-03 `MatchdayGeneratorForm` already follows the pattern; the `groupId` field addition is consistent.
- **SpotBugs suppression discipline** — every `<Match>` carries XML rationale per Phase 81 D-09. Phase 83 unlikely to need new suppressions (no new exposable-field patterns) — if any surface (e.g., `StandingsView` record exposing `List<SeasonPhaseGroup>` via accessor), suppress per-field with rationale.
- **GIVEN_WHEN_THEN test naming** — per CLAUDE.md `## Development Approach`; all Phase 83 tests follow.

### Integration Points

- **`pom.xml`** — NO changes. No new dependency for QUAL-01..05.
- **`config/spotbugs-exclude.xml`** — likely no new entries needed. Re-check after QUAL-04 commit if `EI_EXPOSE_REP` flags the `StandingsView` record's list accessors — should not, because records are inherently immutable in their accessor shape. If flagged, suppress per-pattern with rationale per Phase 81.
- **`lombok.config`** — NOT touched. Phase 81 SpotBugs-related lines must stay.
- **`CLAUDE.md`** — NOT touched. No new convention to document. Existing "Keep Controllers Thin" / "No Inline Styles" / "Isolate Test Data Completely" sections already cover Phase 83 work.
- **`application.yml` / `application-{dev,local,docker,prod}.yml`** — NOT touched. QUAL-02 is a Java annotation change, not a YAML change.
- **Flyway `V*.sql`** — NONE. No schema change. CLAUDE.md invariant respected.
- **Existing E2E tests** — Phase 83 adds new E2E tests but does not modify existing ones. Wallclock impact: +2-3 new E2E tests is well within Phase 86's wallclock-reduction headroom.

### What This Phase Does NOT Touch

- Any existing Flyway migration (V1..V32 or whatever current) — CLAUDE.md invariant.
- `DemoDataSeeder` (QUAL-02 explicitly excludes).
- Backup wire contract (`BackupSchema.SCHEMA_VERSION`, `EXPORT_ORDER` size) — Phase 82 locked this with a guard test.
- Any controller other than `SeasonController` and `StandingsController`.
- Any entity other than `Driver` (QUAL-01 `@OrderBy`).
- `pom.xml`, `lombok.config`, `CLAUDE.md`, profile YAML files.
- The 24 Backup MixIn classes, ImportLock services, audit infrastructure.

</code_context>

<specifics>
## Specific Ideas

- **Branch name:** REMAIN ON `gsd/v1.11-tooling-and-cleanup` (the milestone branch). NO new sub-branch. Per `feedback_milestone_branch` + user correction 2026-05-17. Planner MUST NOT propose `git checkout -b feature/...`. This is non-negotiable.
- **PR creation:** NONE during Phase 83. The milestone-PR (`gsd/v1.11-tooling-and-cleanup` → `master`) is created at v1.11 milestone-close after Phase 87 completes.
- **Coverage discipline:** Capture pre-Phase-83 JaCoCo line coverage from Phase 82 verification (87.80 %), assert post-Phase-83 within ±0.5 pp.
- **Per CLAUDE.md `feedback_test_call_optimization`:** NO `./mvnw verify` between fix commits — targeted tests only. ONE final `./mvnw verify -Pe2e` before phase-close.
- **Per CLAUDE.md `feedback_e2e_verification`:** Phase-end verification uses `-Pe2e` to confirm Playwright E2E classpath stays clean. Phase 83 adds 2 new E2E tests (QUAL-01 + QUAL-03).
- **Per CLAUDE.md `feedback_clean_maven_build_authority`:** If any IDE shows a stale compilation error after the QUAL-04 service-refactor, run `./mvnw clean test-compile` BEFORE trusting the IDE.
- **Per CLAUDE.md `feedback_subagent_stability`:** If planner dispatches subagents for the QUAL-IDs, EACH subagent prompt MUST name the active branch `gsd/v1.11-tooling-and-cleanup` AND explicitly forbid `git stash`/`git checkout`/`git reset`/branch switching, AND name the single QUAL-ID it owns (no scope creep into sibling commits).
- **Per CLAUDE.md `feedback_screenshots_folder`:** Playwright artefacts from QUAL-01 + QUAL-03 E2E land in `.screenshots/`, never in the project root.
- **Per CLAUDE.md `feedback_wave_pause`:** During execute-phase, wait for user feedback after each QUAL-ID wave-merge — do not chain through all 5 commits non-stop.
- **Per CLAUDE.md `feedback_no_unnecessary_comments`:** New code (QUAL-04 service + DTO especially) avoids ornamental Javadocs / phase-references in comments — code is self-explanatory.

</specifics>

<deferred>
## Deferred Ideas

- **Broader OSIV-lazy-access cleanup in other controllers** — QUAL-04 scope is limited to `StandingsController:138`. Other controllers may still have lazy collection accesses (e.g., `DriverController#detail`, `MatchController`). A future "OSIV-discipline sweep" phase could grep for all `entity.getCollection()` patterns in controllers. Defer to v1.12+.
- **QUAL-03 Generate-All-Groups single-button shortcut** — Option 3 from discuss-phase Q3 (generate for all groups in one click + per-group override). Considered but rejected for Phase 83 — adds Service-level looping logic. If operators of GROUPS-layout seasons find the per-group click-through cumbersome, escalate to a v1.12 feature.
- **Auto-execution of UAT-02 via Playwright against staging** — if the project ever adds a staging environment that mirrors production, UAT-02 could become an automated check. Phase-83 only addresses the manual procedure. Defer.
- **`DriverRepositoryOrderIT` extension to cover other `@OrderBy` annotations** — Phase 83 verifies the Driver.seasonDrivers ordering. If other entities later get `@OrderBy`, a generic "ordering contract IT" pattern could be extracted. Single-use today.
- **DemoDataSeeder profile widening** — explicitly out of Phase-83 scope. If `local,demo` users want GT7 demo data, they already get it (the `demo` profile is additive). Considered already-correct.
- **StandingsView template-rewrite to `${view.X}` direct access (Option b of D-23)** — if Planner picks Option (a) for Phase 83, the template-rewrite becomes a v1.12 cleanup. Mark with a `TODO(qual-04-followup)` comment? No — per `feedback_no_unnecessary_comments`, NOT a CODE TODO; capture here in CONTEXT.md only.

### Reviewed Todos (not folded)

None — `gsd-sdk query todo.match-phase 83` not invoked during discuss-phase. If pending todos exist that match Phase-83 scope, the planner should re-run `todo.match-phase` and fold relevant items into the plan.

</deferred>

---

*Phase: 83-quality-and-polish-sweep*
*Context gathered: 2026-05-17*
