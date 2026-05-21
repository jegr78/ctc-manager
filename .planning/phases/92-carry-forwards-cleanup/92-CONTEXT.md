# Phase 92: Carry-Forwards & Cleanup - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Close the 5 v1.12 audit carry-forward findings (UX-01, COV-01, CLEAN-01,
DOCS-01, BOOK-01) on a clean baseline before v1.13 Discord migrations
(Flyway V8-V12) land. Four sequential plans on
`gsd/v1.13-discord-integration` per [[feedback-inline-sequential-execution]]
+ [[feedback-wave-pause]]. Phase 92 is the FIRST phase of v1.13 and unblocks
Phase 93 (Discord Foundation).

In scope:

- **UX-01 (Plan 92-01)** — Extend the Phase 91 typed-catch + `errorCategory`
  flash + badge UX pattern from `DriverSheetImportController` +
  `RaceController` (calendar branches) to `CsvImportController` (the 3rd
  Google-Sheets-consuming controller). Re-closes the T-91-02-IL info-leak
  threat for the third consumer. Whitelisted `getUserMessage()` only —
  never `e.getMessage()` echoed into flash content. 4 error categories
  rendered with the existing `admin.css` BEM error-badge classes from
  Phase 91 D-07.
- **COV-01 (Plan 92-02)** — New `RaceControllerCalendarTest` (Mockito
  `@WebMvcTest`) covering the 4 calendar branches that drove the JaCoCo
  Δ−0.44 pp regression + extended ITs on `GoogleSheetsServiceIT` +
  `GoogleCalendarServiceIT` for the `IOException` defensive `catch`
  paths in `GoogleApiExceptionMapper`. JaCoCo line coverage returns to
  ≥ 88.88 % (v1.11 baseline).
- **CLEAN-01 (Plan 92-03)** — Add a new `exec-maven-plugin` execution
  `assumptions-fence` (Phase `validate`, parallel to existing
  `template-fragment-call-guard`) with the tightened grep predicate
  `org\.junit\.jupiter\.api\.Assumptions` so the fence ignores AssertJ
  `Assumptions.assumeThat` (introduced by Phase 89 PERF-01 in
  `BackupStagingDirPerForkIT`). 2 unit tests against the grep wrapper:
  1 synthetic positive (JUnit `Assumptions.assumeFalse`) + 1 synthetic
  negative (AssertJ `Assumptions.assumeThat`).
- **DOCS-01 (Plan 92-04, part A)** — Retroactive
  `89-VERIFICATION.md`, `90-VERIFICATION.md`, `91-VERIFICATION.md` under
  `.planning/milestones/v1.12-phases/{89,90,91}-*/` following the standard
  VERIFICATION.md template (Phase Goal Recap + Goal-Backward Walk-Through
  + Verification Outcome). Substance derived from existing VALIDATION.md
  + per-plan SUMMARY.md. v1.11 precedent: commit `2e84fd57`.
- **BOOK-01 (Plan 92-04, part B)** — Flip 7 stale `[ ]` checkboxes
  (PERF-01..06 + UX-01) to `[x]` and 4 stale `Pending` traceability rows
  to `Resolved` in `.planning/milestones/v1.12-REQUIREMENTS.md` to match
  the post-merge state already captured in v1.12 MILESTONE-AUDIT.md.

Out of scope (deferred / not Phase-92 scope):

- **DRY refactor of typed-catch + flash pattern** — `GoogleApiFlashTranslator`
  helper is NOT extracted (D-02 below); 3× copy of the catch-block stays.
  v1.14 backlog item if a 4th Google-API-consuming controller emerges.
- **Refactor of existing `DriverSheetImportController` /
  `RaceController` catch blocks** — Plan 92-01 ONLY adds the pattern to
  `CsvImportController`; no churn on the two already-converted controllers.
- **Production code beyond UX-01 surface** — Plan 92-01 touches
  `CsvImportController.java` + the templates `admin/import.html` +
  `admin/import-preview.html` (where the badge renders). `application*.yml`
  untouched (D-13 Phase 91 carry-forward).
- **Discord migrations (Flyway V8-V12)** — Phase 93+ scope.
- **New SpotBugs `<Match>` entries** — UX-01 + COV-01 + CLEAN-01 should
  not surface `EI_EXPOSE_REP*` or `SE_BAD_FIELD`; targeted
  `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST
  pattern only if a finding actually surfaces.
- **`.planning/REQUIREMENTS.md` top-level traceability rows** — BOOK-01
  scope is ONLY `.planning/milestones/v1.12-REQUIREMENTS.md`; top-level
  REQUIREMENTS.md v1.13 rows stay `Pending` until each REQ-ID ships.

</domain>

<decisions>
## Implementation Decisions

### DOCS-01 Scope

- **D-01: All 3 retroactive VERIFICATION.md files written in Plan 92-04.**
  `89-VERIFICATION.md` + `90-VERIFICATION.md` + `91-VERIFICATION.md` all
  authored in this phase. Substance derived from existing VALIDATION.md +
  per-plan SUMMARY.md — no new validation work, only file-shape
  compliance with the standard template (Phase Goal Recap + Goal-Backward
  Walk-Through + Verification Outcome). Effort ~10 min per file = ~30 min
  total. Rationale: closes the goal-backward doc-shape audit-loop fully;
  follows v1.11 precedent commit `2e84fd57`; cheaper now than re-opening
  the audit thread later. REJECTED: only Phase 91 (leaves 89/90 gap
  open without justification); skip entirely (REQ-ID would be closed by
  doc-shape exception note in MILESTONES.md — works but leaves the
  audit-loop semantically open).

### CsvImport Refactor Scope

- **D-02: Accept 3× duplication of the typed-catch + flash + log pattern.**
  Plan 92-01 copies the established pattern from
  `DriverSheetImportController.preview()` (lines 60-92) into
  `CsvImportController.preview()` + `execute()` catch blocks. No
  `GoogleApiFlashTranslator` helper extracted; no churn on
  `DriverSheetImportController` or `RaceController`. Rationale: each
  controller stays self-contained and readable; the 3-class repetition
  is small (4 catch arms × ~6 lines each) and stable. v1.14 backlog
  item `Extract GoogleApiFlashHelper if 4th Google-API consumer
  emerges`. REJECTED: helper-now + apply-everywhere (out-of-spec —
  UX-01 acceptance criteria targets `CsvImportController`, not 3
  controllers; coverage + IT churn risks Phase-92 timebox); helper-now
  + CsvImport-only (introduces unused indirection in a single consumer
  — worst of both worlds).

### COV-01 Test Strategy

- **D-03: Mixed Unit + IT strategy.** Plan 92-02 ships:
  - `RaceControllerCalendarTest.java` as a Mockito-mocked `@WebMvcTest`
    (NOT `@SpringBootTest`) covering 4 calendar-related branches:
    `calendarAvailable`, `hasCalendarEvent`, `canCreateCalendarEvent`
    model attribute paths (GET) + `POST /admin/races/{id}/create-calendar-event`
    success + each of the 4 `GoogleApiException`-subtype catch arms.
    Tag `@Tag("unit")` is not used (project convention: plain unit tests
    stay untagged per CLAUDE.md § Tag Tests by Category).
  - Extended ITs on `GoogleSheetsServiceIT` + `GoogleCalendarServiceIT`
    for the `IOException`-defensive-catch paths inside
    `GoogleApiExceptionMapper` (Java 25 sealed-exhaustiveness on `catch`
    not yet a language feature; the defensive catch needs explicit
    coverage). `@Tag("integration")` per CLAUDE.md.

  Rationale: Mockito `@WebMvcTest` for controllers is fastest path to
  recover the JaCoCo branch coverage on calendar UI logic; ITs for
  service-layer exception mapping verify the real translation (mocked
  Google clients miss the genuine `IOException` → typed-subtype flow).
  Keeps Phase 92 within the 1-2-day estimate and avoids CI E2E budget
  pressure on the v1.12 17:39 baseline. REJECTED: full
  `@SpringBootTest` ITs for everything (Spring-context load slows CI
  beyond the v1.12 PERF-tuning win); pure Mockito everywhere (mocked
  exception flow does not exercise the GoogleApiExceptionMapper code
  paths that actually drove the coverage delta).

### CLEAN-01 Fence Location

- **D-04: `exec-maven-plugin` `assumptions-fence` execution in pom.xml.**
  Plan 92-03 adds a new `<execution id="assumptions-fence">` to the
  existing `exec-maven-plugin` block in `pom.xml` (lines 431-462,
  parallel to `template-fragment-call-guard`). Phase: `validate`.
  Predicate (tightened): grep for
  `^import\s+static\s+org\.junit\.jupiter\.api\.Assumptions\.` OR
  `^import\s+org\.junit\.jupiter\.api\.Assumptions;` across
  `src/test/java/` excluding `src/test/java/org/ctc/build/` (the synthetic
  fixtures, see below). Comment with CLEAN-01 cross-reference.

  Plus 2 unit tests under
  `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java`:
  - Positive: synthetic source file with `import static
    org.junit.jupiter.api.Assumptions.assumeFalse;` written to a
    `@TempDir`; predicate must detect it.
  - Negative: synthetic source file with `import static
    org.assertj.core.api.Assumptions.assumeThat;` written to a
    `@TempDir`; predicate must NOT detect it.

  Rationale: consistent with the PLAT-07 Plan-71
  `template-fragment-call-guard` pattern (D-05 Phase 71); runs locally
  on every `./mvnw verify` (matches the [[feedback-clean-build-only]]
  workflow); CI inherits automatically. REJECTED: standalone
  `scripts/check-assumptions-fence.sh` (2 files instead of 1; no
  historic `scripts/*-fence.sh` precedent); GitHub-Actions-only step
  (violates "CI = source of truth" Phase 86 D-11 principle for
  developer feedback — fence violation should fail local builds, not
  surface only after push).

### Phase Scope & Sequencing (carried forward from Phase 91 D-02)

- **D-05: Four plans, sequential inline on `gsd/v1.13-discord-integration`.**
  Mirrors Phase 91 D-02 + Phase 90 D-06 pattern. Order:
  - **Plan 92-01 — UX-01.** CsvImportController + import.html badge
    rendering. Smallest production-code surface; lands first to clear
    the user-visible UX gap.
  - **Plan 92-02 — COV-01.** Mockito `@WebMvcTest`
    `RaceControllerCalendarTest` + extended `GoogleSheetsServiceIT` +
    `GoogleCalendarServiceIT` ITs. Verify JaCoCo ≥ 88.88 % via
    `target/site/jacoco/jacoco.csv` LINE_MISSED/LINE_COVERED
    computation (per Phase 89 PERF-02 forensics shape).
  - **Plan 92-03 — CLEAN-01.** `pom.xml` `exec-maven-plugin`
    `assumptions-fence` execution + 2 unit tests against the predicate
    in `org.ctc.build` package.
  - **Plan 92-04 — DOCS-01 + BOOK-01.** Bundle: write 3 retroactive
    VERIFICATION.md (DOCS-01) + flip 11 stale markers in
    `milestones/v1.12-REQUIREMENTS.md` (BOOK-01). Pure docs/bookkeeping
    plan; `src/` git-clean (assertion in 92-04-SUMMARY).
  No worktrees, no subagents per [[feedback-inline-sequential-execution]].

### PR Mechanics (carried forward from Phase 91 D-05)

- **D-06: Rolling v1.13 milestone PR opened EARLY as Draft from Plan 92-01
  onward; one PR per milestone, NOT per plan.** Plan 92-01 opens
  `gh pr create --draft --base master --head gsd/v1.13-discord-integration
  --assignee jegr78` with placeholder body
  `v1.13 Discord Integration & Carry-Forwards (work in progress) — Phase
  92 carry-forwards landing`. Body is rolling per
  [[feedback-pr-description-update]]: each plan that ships updates the
  body via `gh pr edit --body-file`. Final composite body shape per
  Phase 91 D-07b (REQ-ID table + per-phase narrative + CI links +
  Coverage/SpotBugs/CodeQL summary) finalized by Plan 98-03 (milestone
  closer). PR flips Draft → Ready-for-review at end of Phase 98.
  Subject: `feat(v1.13): discord integration & carry-forwards` (MINOR
  bump via `feat:` Conventional Commits per
  [[feedback-squash-merge-message]]).

  REJECTED: 1 PR per plan (4 PRs in Phase 92 alone = 28 PRs across the
  milestone = excessive review overhead, breaks [[feedback-sequential-pr-merge]]
  rebase chain after each squash); PR-only-at-milestone-close (loses
  early `pull_request` event CI validation; loses D-17 trigger-equivalence
  cleanliness from Phase 91).

### Quality Gates (carried forward from Phase 91 D-10)

- **D-07: Standard gates apply, no tightening, no loosening.**
  - JaCoCo line coverage ≥ 88.88 % at end of Phase 92 (v1.11 baseline
    restoration is the Phase 92 success criterion; subsequent v1.13
    phases must maintain or improve).
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound check goal).
  - CodeQL gate-step exit 0 on PR HEAD SHA (HIGH/CRITICAL threshold).
  - `EXPORT_ORDER` = 24 entities; `BackupSchema.SCHEMA_VERSION` = 1;
    Flyway V1-V7 immutable (Phase 92 adds NO Flyway migrations).
  - Test count grows by ~10 (COV-01 + CLEAN-01 fixtures): from v1.12
    1696 → v1.13 ~1706 after Phase 92.
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20 %
    tolerance (Phase 92 adds Mockito unit tests only — should be
    sub-30s impact).

### Test Discipline (carried forward from Phase 91 D-11/D-12)

- **D-08: Per-Phase Nyquist VALIDATION.md.** Plans 92-01..92-04 each
  ship with a VALIDATION.md. Phase 92 self-validates via
  `/gsd-validate-phase 92` before `/gsd-execute-phase 93` starts.
  Carries forward v1.12's per-phase discipline (D-11 Phase 91).

- **D-09: Tag every new test class per CLAUDE.md `@Tag` convention.**
  - `RaceControllerCalendarTest` (Mockito unit) → untagged (CLAUDE.md
    rule: plain unit tests stay untagged).
  - `GoogleSheetsServiceIT` / `GoogleCalendarServiceIT` extensions →
    `@Tag("integration")` already present on parent IT classes
    (verify; do not change).
  - `AssumptionsFencePredicateTest` (unit) → untagged.

### Production Behavior

- **D-10: Production code touched only in Plan 92-01.** Plans 92-02
  (test-only), 92-03 (`pom.xml` + new test class), 92-04 (docs +
  bookkeeping) all `src/main/java/**` git-clean (assertion in each
  Plan-N SUMMARY). Plan 92-01 touches:
  `src/main/java/org/ctc/dataimport/CsvImportController.java` (replace
  generic IOException catches with the 4 typed-subtype + sealed-base
  catch arms from Phase 91 D-06 + D-07; whitelisted user messages;
  `errorCategory` flash attribute set on each branch),
  `src/main/resources/templates/admin/import.html` +
  `src/main/resources/templates/admin/import-preview.html` (badge
  rendering using existing `.error-badge--auth/transient/not-found/permission`
  CSS classes from Phase 91 D-07 — verify class names with `admin.css`).
  `application*.yml` untouched (D-13 Phase 91 carry-forward).

### BOOK-01 Verification

- **D-11: Bookkeeping success measured by 2 grep commands.**
  - `grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md`
    must return `0` after Plan 92-04 (currently 4).
  - `grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md`
    must return `0` after Plan 92-04 (currently 7).
  Both checks live in `92-04-VALIDATION.md` per Plan 91 D-11 Nyquist
  pattern.

### Claude's Discretion

- Exact wording of the 3 retroactive VERIFICATION.md files — D-01
  locks the template shape (Phase Goal Recap + Goal-Backward
  Walk-Through + Verification Outcome); planner picks prose, mirrors
  v1.11 precedent commit `2e84fd57` shape.
- Exact CSS class name lookup for the 4 error-badge classes — D-10
  defaults to Phase 91 D-07 BEM shape (`.error-badge--auth`,
  `.error-badge--transient`, `.error-badge--not-found`,
  `.error-badge--permission`); planner verifies actual class names
  against `src/main/resources/static/admin.css` and adjusts if
  Phase 91 settled on a slightly different shape.
- Exact wording of the placeholder PR body opened by Plan 92-01 —
  D-06 locks the rolling-update pattern; planner picks the placeholder
  prose that reads well as a temporary "work in progress" body.
- Whether `RaceControllerCalendarTest` lives at
  `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java`
  (sibling to existing `RaceController` tests) or a more nested
  `.calendar.RaceControllerCalendarTest` — planner picks based on
  the existing `org.ctc.admin.controller` package layout.
- Exact `pom.xml` line placement of the new `assumptions-fence`
  execution within the `exec-maven-plugin` block — D-04 locks the
  block; planner picks ordering (alphabetical id, or after
  `template-fragment-call-guard`).
- Exact bash grep predicate inside the `assumptions-fence` execution
  — D-04 locks the semantic (only JUnit-Jupiter Assumptions trigger);
  planner picks the grep regex shape that matches reliably across
  BSD/GNU grep + handles edge cases (single import, static import,
  multi-import block).
- Whether the 2 unit tests in `AssumptionsFencePredicateTest` invoke
  bash directly (via `ProcessBuilder`) or shell out via the
  `exec-maven-plugin` invocation pattern — planner picks the cleanest
  test shape that runs cross-platform (CI ubuntu-latest, dev darwin).
- Exact wording of `MILESTONES.md` v1.13 entry — Plan 98-03 (milestone
  closer) decides this; Plan 92-04 only touches
  `.planning/milestones/v1.12-REQUIREMENTS.md`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 92: Carry-Forwards & Cleanup" — goal,
  Depends-on (nothing — first phase of v1.13), Requirements (UX-01,
  COV-01, CLEAN-01, DOCS-01, BOOK-01)
- `.planning/REQUIREMENTS.md` lines 7-15 — UX-01, COV-01, CLEAN-01,
  DOCS-01, BOOK-01 full requirement text + acceptance criteria
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 92" — full 5 Success
  Criteria + Phase Dependency Graph (Phase 93 depends on Phase 92)
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — Phase-92
  forward-path framing for the carry-forward bucket
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" (JaCoCo ≥ 88.88 %, CI E2E median 17:39 ± 20 %, SpotBugs 0,
  CodeQL exit 0, `EXPORT_ORDER` 24, SCHEMA_VERSION 1, Flyway V1-V7
  immutable)
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` § 5
  "Phase 92 — Carry-Forwards & Cleanup" — the 4-plan structure (92-01
  UX-01, 92-02 COV-01, 92-03 CLEAN-01, 92-04 DOCS-01+BOOK-01) and
  Phase 92 success criteria

### v1.12 Hand-off (PRIMARY INPUT)

- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-CONTEXT.md`
  — **D-06 (typed-exception hierarchy: `GoogleApiException` sealed base
  + 4 permits `TransientGoogleApiException`, `AuthGoogleApiException`,
  `NotFoundGoogleApiException`, `PermissionGoogleApiException`)**,
  **D-07 (flash-attribute pattern `errorMessage` + `errorCategory`
  with BEM badge CSS classes)**, D-08 (consumer-driven UX surface
  where there IS a direct user trigger), D-09 (operations-runbook
  shape — referenced by `docs/operations/google-integration.md`),
  D-13 (production yml invariant — Plan 92-01 ONLY touches
  controller + templates, no yml)
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-02-PLAN.md`
  (if it exists) — exact shape of the typed-catch implementation in
  `DriverSheetImportController` (the pattern Plan 92-01 copies)
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-02-SUMMARY.md`
  (if it exists) — Plan 91-02 outcome + any deviation notes
- `.planning/milestones/v1.12-MILESTONE-AUDIT.md` (or whichever audit
  file recorded UX-01/COV-01/CLEAN-01/DOCS-01/BOOK-01) — the
  authoritative description of the 5 carry-forward findings; Plan
  92-04 mirrors the audit-state into the v1.12-REQUIREMENTS.md
  bookkeeping flip
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md`
  — D-13 (3-seed Failsafe verification pattern), origin of the AssertJ
  `Assumptions.assumeThat` import in `BackupStagingDirPerForkIT` that
  the CLEAN-01 grep predicate must NOT trigger on

### v1.11 Anchors

- `.planning/MILESTONES.md` § "v1.11 Tooling Infrastructure & Tech-Debt
  Sweep" — entry shape template for the v1.13 milestone closer (Plan
  98-03 references this; Plan 92-04 does not)
- v1.11 commit `2e84fd57` — precedent for retroactive VERIFICATION.md
  authoring (DOCS-01 D-01 mirrors this shape)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md`
  — D-11 (CI = source of truth principle; rationale for D-04 fence
  running locally NOT only in CI)

### Plan 71 Build-Guard Anchor (D-04 pattern)

- `pom.xml` lines 431-462 — existing `exec-maven-plugin`
  `template-fragment-call-guard` execution; D-04 mirrors this shape
  for the new `assumptions-fence` execution
- `.planning/milestones/v1.9-phases/71-*/71-CONTEXT.md` § D-05 + D-12
  (referenced in the pom.xml comment) — Plan 71 PLAT-07 background
  for the build-guard pattern Plan 92-03 reuses

### Code Surface (UX-01 touchpoints — Plan 92-01)

- `src/main/java/org/ctc/dataimport/CsvImportController.java` (~225
  lines) — current 3 generic `IOException | IllegalArgumentException |
  IllegalStateException` catch blocks at lines 56, 115, 204; each
  echoes `e.getMessage()` into the `errorMessage` flash/model
  attribute (T-91-02-IL info-leak). Plan 92-01 replaces with 5 typed
  catch arms (4 permits + sealed-base defensive) per Phase 91 D-06,
  whitelisted `getUserMessage()` only.
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
  lines 60-92 — the reference implementation Plan 92-01 copies (4
  typed catches + defensive sealed-base catch + flash `errorMessage`
  + `errorCategory`).
- `src/main/java/org/ctc/admin/controller/RaceController.java` lines
  193-205 — second reference for the typed-catch pattern (calendar
  branches); shows the `redirect:` flash variant.
- `src/main/resources/templates/admin/import.html` +
  `src/main/resources/templates/admin/import-preview.html` — current
  flash `errorMessage` rendering location; Plan 92-01 adds the
  category-badge `<span class="error-badge error-badge--{auth|transient|not-found|permission}">`
  rendering pattern from Phase 91 D-07.
- `src/main/resources/templates/admin/driver-import.html` — reference
  template for the badge-rendering shape.
- `src/main/resources/static/admin.css` — `.error-badge*` CSS classes
  (verify class names against Phase 91 D-07 settled shape).

### Code Surface (COV-01 touchpoints — Plan 92-02)

- `src/main/java/org/ctc/admin/controller/RaceController.java` lines
  33, 69-71, 193-205 — calendar-related model attributes and POST
  endpoint; Plan 92-02's `RaceControllerCalendarTest` must cover all
  4 branches.
- `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java`
  — the defensive `IOException` catch paths Plan 92-02 ITs exercise.
- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` (182
  lines) + `src/main/java/org/ctc/dataimport/GoogleCalendarService.java`
  (120 lines) — the throws-site for the typed exceptions; ITs verify
  the actual `IOException` → typed-subtype mapping.
- `target/site/jacoco/jacoco.csv` after `./mvnw verify` — the
  authoritative coverage measurement file (LINE_MISSED / LINE_COVERED
  columns); 92-02-VALIDATION.md cites the computed line-coverage
  percentage.

### Code Surface (CLEAN-01 touchpoints — Plan 92-03)

- `pom.xml` lines 431-462 — existing `exec-maven-plugin` block to
  extend; D-04 adds a new `<execution id="assumptions-fence">` parallel
  to `template-fragment-call-guard`.
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java`
  line 12 — the AssertJ `Assumptions.assumeThat` import the tightened
  predicate must NOT trigger on (the canonical negative-test
  reference).
- New file: `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java`
  — 2 unit tests against the grep predicate (synthetic positive +
  synthetic negative); package `org.ctc.build` mirrors the existing
  build-guard test conventions.

### Code Surface (DOCS-01 / BOOK-01 touchpoints — Plan 92-04)

- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VALIDATION.md`
  — substance source for `89-VERIFICATION.md`
- `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VALIDATION.md`
  — substance source for `90-VERIFICATION.md`
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VALIDATION.md`
  — substance source for `91-VERIFICATION.md`
- `.planning/milestones/v1.12-REQUIREMENTS.md` — currently has 7
  stale `[ ]` checkboxes (PERF-01..06 + UX-01) and 4 stale `Pending`
  traceability rows (PERF-01, PERF-02, PERF-06, UX-01); Plan 92-04
  flips all to `[x]` / `Resolved`

### Conventions & Memory Anchors

- `CLAUDE.md` § "Test Naming (Given-When-Then)" + § "Tag Tests by
  Category (`@Tag`)" — every new test class follows the BDD pattern
  + correct tag (`@Tag("integration")` for ITs, untagged for plain
  units)
- `CLAUDE.md` § "Conventions › Logging" — Plan 92-01 catch blocks use
  `log.error(..., e)` parameterized format for AUTH/NOT_FOUND/PERMISSION,
  `log.warn(..., e)` for TRANSIENT (matches Phase 91 DriverSheetImport
  precedent)
- `CLAUDE.md` § "Static Analysis (SpotBugs + find-sec-bugs)" — Plan
  92-01 / 92-02 / 92-03 must keep SpotBugs `BugInstance` count = 0;
  targeted `@SuppressFBWarnings({"CODE"}, justification="…")` only if
  necessary
- `CLAUDE.md` § "CodeQL SAST (Code Scanning)" — gate-step must exit 0
  on PR HEAD SHA; any new finding triggers the
  `docs/security/sast-acceptance.md` parallel-write discipline
- [[feedback-inline-sequential-execution]] memory — D-05 sequential
  execution on `gsd/v1.13-discord-integration` is binding, no worktrees
  or subagents
- [[feedback-pr-description-update]] memory — D-06 rolling PR body
  pattern is binding
- [[feedback-squash-merge-message]] memory — D-06 final PR subject
  `feat(v1.13): discord integration & carry-forwards` is binding for
  the MINOR bump
- [[feedback-wave-pause]] memory — pause for user feedback after each
  plan-commit ship before starting the next plan
- [[feedback-no-flaky-dismissal]] memory — Plan 92-02 tests that pass
  locally but fail in CI are regressions, never deferred
- [[feedback-clean-build-only]] memory — D-04 fence runs in standard
  `./mvnw clean test-compile` + `./mvnw verify -Pe2e`; no skip flags

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`org.ctc.dataimport.exception.*` package (Phase 91)** — `GoogleApiException`
  sealed base + 4 typed subtypes (`TransientGoogleApiException`,
  `AuthGoogleApiException`, `NotFoundGoogleApiException`,
  `PermissionGoogleApiException`) + `GoogleApiExceptionMapper`
  utility. Plan 92-01 consumes ALL of these unchanged — the exception
  hierarchy is the foundation for CsvImport UX parity.
- **`DriverSheetImportController.preview()` lines 60-92** — the
  canonical 5-catch-arm shape (4 permits + sealed-base defensive)
  with `errorMessage` + `errorCategory` flash attributes and
  `log.error(..., e)` / `log.warn(..., e)` per category. Plan 92-01
  copies this verbatim into `CsvImportController.preview()` + `.execute()`.
- **`RaceController` lines 193-205** — second reference for the
  `redirect:` flash variant (matches `CsvImportController.execute()`
  shape).
- **`admin.css` `.error-badge*` BEM classes (Phase 91 D-07)** —
  `.error-badge`, `.error-badge--auth`, `.error-badge--transient`,
  `.error-badge--not-found`, `.error-badge--permission`. Plan 92-01
  template badge rendering reuses these directly.
- **`pom.xml` `exec-maven-plugin` `template-fragment-call-guard`
  (Plan 71 PLAT-07)** — exact pattern Plan 92-03 mirrors for the new
  `assumptions-fence` execution.

### Established Patterns

- **Typed-catch + flash + log per category (Phase 91 D-06/D-07)** —
  AUTH/NOT_FOUND/PERMISSION → `log.error`, TRANSIENT → `log.warn`;
  user-message is whitelisted constant string (NEVER `e.getMessage()`);
  category enum string passed as `errorCategory` flash attribute;
  template renders `<span class="error-badge error-badge--{category-lowercase}">`.
- **Build-guard via exec-maven-plugin (Plan 71)** — bash `grep -rE …`
  in `<![CDATA[…]]>` block, phase `validate`, exit 1 on violation
  with clear remediation message. Plan 92-03 mirrors this for the
  Assumptions fence.
- **Mockito `@WebMvcTest` for controller-only branches** — used
  across `org.ctc.admin.controller.*Test` for fast HTTP-layer
  assertions without Spring-context boot. Plan 92-02
  `RaceControllerCalendarTest` adopts this for the calendar branches.
- **`@Tag("integration")` for `*IT.java` per CLAUDE.md "Test
  Categorization"** — Plan 92-02's IT extensions inherit the tag from
  the parent IT class.
- **Per-Phase Nyquist VALIDATION.md (Phase 91 D-11)** — every plan
  ships with a VALIDATION.md; `/gsd-validate-phase 92` runs before
  Phase 93 starts.
- **Rolling milestone PR with `gh pr edit --body-file` after each
  plan-ship ([[feedback-pr-description-update]])** — Plan 92-01 opens
  Draft PR, 92-02..92-04 + 93-01..98-03 each update the body.

### Integration Points

- **`CsvImportController.preview()` / `execute()` ← `GoogleSheetsService`**
  — current generic `IOException` catch must be replaced with typed
  `GoogleApiException` subtypes. Plan 92-01 verifies that
  `GoogleSheetsService.readRangeFromSheet` / `extractSpreadsheetId`
  / `getSheetNames` / `filterRaceSheets` already throw the typed
  hierarchy (Phase 91 D-13 production code surface — should be done).
- **`RaceController.createCalendarEvent` ← `RaceCalendarService` ←
  `GoogleCalendarService`** — Plan 92-02 `RaceControllerCalendarTest`
  mocks `RaceCalendarService` and verifies each of the 4
  `GoogleApiException`-subtype catch arms sets the right
  `errorMessage` + `errorCategory` flash attribute (per Phase 91 D-08
  consumer-driven UX scope).
- **`pom.xml` `validate` phase ← Plan 92-03 `assumptions-fence`** —
  new execution attached to the same phase as
  `template-fragment-call-guard`; both run before `compile`. Locally
  invokable as `./mvnw validate`.
- **`.planning/milestones/v1.12-REQUIREMENTS.md` ← Plan 92-04** —
  edit-only, no schema changes; verified by 2 grep commands per D-11.
- **`.planning/milestones/v1.12-phases/{89,90,91}-*/` ← Plan 92-04** —
  add 3 new VERIFICATION.md files; no edits to existing files in
  these directories.

</code_context>

<specifics>
## Specific Ideas

- The "3rd Google-Sheets-consuming controller" framing (CsvImport) is
  the key UX-01 anchor — closing T-91-02-IL info-leak for the 3rd
  consumer means the threat is fully closed across the application.
- Phase 91's `RaceController` calendar work was the 2nd consumer.
- The user explicitly chose "3× duplication accepted, no DRY refactor"
  (D-02). If a 4th consumer emerges (e.g., a future Discord-API-bound
  controller using the same typed-catch pattern), revisit the
  `GoogleApiFlashTranslator` extraction decision.
- COV-01's JaCoCo Δ−0.44 pp regression is the only quantitative pain
  point from v1.12 — the 88.88 % v1.11 baseline restoration is
  binding for the entire v1.13 milestone (per Success Criterion 4 in
  v1.13-ROADMAP.md Phase 98).
- CLEAN-01's "synthetic positive AssumeFalse" + "synthetic negative
  AssumeThat" wording in the v1.12 REQUIREMENTS.md is the explicit
  acceptance criterion for the predicate; the 2 unit tests in
  `AssumptionsFencePredicateTest` MUST cover exactly these two cases.

</specifics>

<deferred>
## Deferred Ideas

- **`GoogleApiFlashTranslator` helper extraction** — defer to v1.14
  backlog if a 4th Google-API-consuming controller emerges.
  Tradeoff captured in D-02. If Phase 93's `DiscordRestClient` ends
  up adopting the same 4-catch + flash pattern (it likely will, given
  Phase 93 has its own sealed exception hierarchy `DiscordApiException`
  + 4 permits per INFRA-01), the "extract a typed-catch translator"
  decision becomes relevant at the v1.14 milestone scoping.
- **`.planning/REQUIREMENTS.md` top-level traceability row flips for
  v1.13 REQ-IDs** — out of Phase 92 scope; each REQ-ID flips when
  the phase that owns it ships (per [[feedback-pr-description-update]]
  cadence).
- **Extending the `assumptions-fence` to cover other forbidden imports
  (e.g., `org.springframework.test.context.junit4.*`, JUnit-4 imports)**
  — not in CLEAN-01 scope; v1.14 backlog if SpotBugs / OpenRewrite
  surfaces an analogous fence opportunity.
- **Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's
  5-class `db.migration.**` cluster** — Phase 90 deferred carry-forward;
  re-evaluate against the Phase 92 baseline if the new Mockito
  `@WebMvcTest` shape suggests context-cache opportunities.

</deferred>

---

*Phase: 92-carry-forwards-cleanup*
*Context gathered: 2026-05-20*
