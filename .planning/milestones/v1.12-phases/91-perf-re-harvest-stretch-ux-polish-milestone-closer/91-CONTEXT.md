# Phase 91: PERF Re-Harvest, Stretch UX Polish & Milestone Closer - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Authoritatively measure the cumulative wallclock effect of Phases 88-90 via 5
consecutive `workflow_dispatch` CI runs on the v1.12 milestone PR-branch
(D-17 trigger-equivalence with `pull_request` / `push to master`), land the
Google-API typed-exception hierarchy + categorized error UX (UX-01) on top,
then close v1.12 with the milestone PR. Three sequential plans on
`gsd/v1.12-driver-import-and-test-perf` per [[inline-sequential-execution]] +
[[wave-pause]] (decision D-08 below).

In scope:
- PERF-06 — 5 `workflow_dispatch` CI runs on the v1.12 milestone PR-branch
  HEAD SHA (the Draft-PR opened by Plan 91-01 satisfies D-17 trigger-
  equivalence: PR-branch `workflow_dispatch` ≡ post-merge master CI because
  `ci.yml` executes identical steps regardless of trigger event); drop min +
  max from the 5 E2E-step wallclock values, compute median of the middle 3,
  record in `docs/test-performance.md § PERF-06 Re-Harvest`; baseline-swap
  (D-04 below) replaces the 23:00 v1.11 number in `STATE.md § Baselines to
  Preserve`. Variance handling per D-03 (no auto-2nd-block; observe + ship).
- UX-01 — Hierarchical sealed `GoogleApiException` base + 4 subs
  (`TransientGoogleApiException` for network/5xx, `AuthGoogleApiException` for
  expired/invalid OAuth tokens, `NotFoundGoogleApiException` for missing sheet
  IDs, `PermissionGoogleApiException` for 403 access-denied). Both
  `GoogleSheetsService` (driver-import path) and `GoogleCalendarService`
  (calendar-sync path) throw the typed exceptions; `DriverSheetImportService`
  + `RaceCalendarService` (or their controller consumers) translate to
  flash-attribute `errorMessage` + new `errorCategory` for category-badge
  rendering. `docs/operations/google-integration.md` is created as an
  operations-runbook-shaped doc (D-06 below) mirroring `release-runbook.md`
  shape.
- Milestone closer — `MILESTONES.md` gains a v1.12 entry (shipped-date,
  phase range 88-91, total plan count, REQ-IDs satisfied); `README.md §
  Test Performance` and `README.md` Backup section pointers update to the
  v1.12 baseline + PR; `docs/test-performance.md` § Post-Optimization
  Wallclock receives the new authoritative CI median; v1.12 milestone PR
  finalized on `gsd/v1.12-driver-import-and-test-perf` with the composite
  PR-body shape per D-07.
- Plan 91-01 opens the Draft PR EARLY (right after Plan 91-01 PERF-06 plan
  authoring is complete) so the 5 `workflow_dispatch` runs target the same
  HEAD SHA that `pull_request` events validate; PR-body is rolling
  (`gh pr edit`) per [[pr-description-update]], finalized at Plan 91-03.

Out of scope (deferred to later phases / not Phase-91 scope):
- Test-module-split extraction (`ctc-manager-tests` Maven artifact) — Phase
  90 PERF-05 D-05 deferred; v1.13 owns the next decision point against the
  PERF-06 CI median surfaced here.
- Secondary cluster consolidation (backup-exception 12-class, admin-security
  12-class, AdminWorkflowE2E 7-class hash buckets) — Phase 90 D-01
  Conservative; v1.13 re-evaluates against PERF-06 data.
- Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class
  `db.migration.**` cluster — Phase 90 deferred.
- Production `application*.yml` changes — D-14 from Phase 89 / D-09 from
  Phase 90 still binding: test-only configuration never bleeds into
  production deployment. UX-01 touches `src/main/java` (typed exceptions +
  controller mapping) but no yml; no Flyway migration.
- Calendar-sync UX surface RE-PLATFORMING (where the calendar trigger lives
  in the admin UI) — D-05 below keeps `GoogleCalendarService` throwing the
  typed hierarchy but downstream `RaceCalendarService` integration stays
  service-only-and-graceful where there is no direct user-trigger. UX
  surface for calendar trigger paths flagged for Plan 91-02 review.
- CI-side Testcontainers reuse enabling — Phase 90 deferred (out of scope).
- Any new SpotBugs `<Match>` entry — the new typed exceptions should not
  surface `EI_EXPOSE_REP*` or `SE_BAD_FIELD`; if they do, targeted
  `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST
  pattern (D-09 below).

</domain>

<decisions>
## Implementation Decisions

### Phase Scope & Sequencing

- **D-01: UX-01 lands IN v1.12 as Plan 91-02, after PERF-06.** The roadmap-
  level "(stretch — descopable to v1.13 if PERF over budget)" wording is
  resolved IN. UX-01 ships as the second of three sequential plans in
  Phase 91. Rationale: UX-01 has been outstanding since v1.11 deferred-
  items; the Google-API error surface is narrow (4 typed exceptions, 2
  Service classes, 2 controller paths, 1 new docs/operations file), bounded,
  and orthogonal to PERF measurement. PERF-06 budget concern is addressed
  by sequencing: 91-01 PERF-06 runs FIRST so a budget overrun would surface
  before UX-01 begins. REJECTED: defer to v1.13 (keeps user-facing pain
  point open without justification given small surface area); conditional
  on PERF-06 median (introduces decision gate complexity mid-phase without
  meaningful cost-saving — UX-01 is small enough that "always IN" is
  cleaner than "maybe IN").

- **D-02: Three plans, sequential inline.** Mirrors Phase 90 D-06 + Phase
  89 D-01 pattern. Order:
  - **Plan 91-01 — PERF-06 CI Re-Harvest + measurement docs.** Opens the
    v1.12 milestone Draft PR EARLY (Plan-1 deliverable, BEFORE the 5
    `workflow_dispatch` runs) so the Draft-PR HEAD SHA is the canonical
    measurement target per D-17. Runs 5 sequential `workflow_dispatch` CI
    runs via `gh workflow run ci.yml --ref gsd/v1.12-driver-import-and-
    test-perf` with `gh run watch` between triggers (concurrency-cancel-
    in-progress workaround per Phase 86 D-17 precedent). Records median
    in `docs/test-performance.md § PERF-06 Re-Harvest`; swaps `STATE.md §
    Baselines to Preserve` per D-04. Variance handling per D-03. PR-body
    is rolling — first edit lands when 91-01 opens the Draft.
  - **Plan 91-02 — UX-01 typed-exception hierarchy + flash UX +
    google-integration.md.** Lands the 4-class sealed hierarchy under
    `src/main/java/org/ctc/dataimport/exception/` (or equivalent canonical
    location — planner picks), refactors `GoogleSheetsService` +
    `GoogleCalendarService` to throw the typed exceptions, updates
    `DriverSheetImportController` (preview + execute) + the calendar-
    sync consumer to translate to flash-attribute `errorMessage` +
    `errorCategory`, adds the category-badge rendering in the relevant
    Thymeleaf template fragments, creates
    `docs/operations/google-integration.md` per D-06 shape. PR-body
    edited again at 91-02 merge.
  - **Plan 91-03 — Milestone Closer.** Updates `MILESTONES.md` (v1.12
    entry per the v1.11 entry shape), `README.md § Test Performance` +
    `README.md` Backup section pointers, finalizes v1.12 milestone PR
    body per D-07 (Summary-Tabelle + Narrative + CI-Links + Coverage),
    flips Draft → Ready-for-review.
  Sequential per [[wave-pause]] — user-feedback pause after each plan
  merge before the next plan begins. REJECTED: 2-plan-bundle (UX-01 +
  Closer in one commit cluster — bad forensics, Phase 90 D-06 explicit
  rejection of this pattern); single atomic Closer plan (atomic-revert
  hostile, Phase 90 D-06 explicit rejection).

### PERF-06 Methodology

- **D-03: Variance >20 % handling — accept n=5 + document the spike as
  "observed variance" in `docs/test-performance.md § PERF-06 Re-Harvest`,
  do NOT auto-trigger a 2nd 5-run block.** Pragmatic honest-observational
  posture, consistent with Phase 89 D-02 / Phase 90 D-05. If 5 runs land
  with variance >20 %, the median of the middle 3 is still reported as the
  v1.12 baseline; a `**Observed variance:** X %` footnote captures the
  spread. Operator (the user) retains the option to manually trigger a
  6th run on a separate branch session if the spike is judged a runner-
  outlier rather than a real signal. Rationale: D-10 from Phase 86 was
  written for the 7:50 hard-gate context (v1.11 PERF-05) where outlier-
  filtering mattered; Phase 91 has NO hard gate (Success Criterion #2 only
  requires "any measurable reduction" or documented "no-improvement"
  per D-10b below). REJECTED: auto 2nd 5-run block (90+ min additional
  CI minutes for a measurement, not a feature; cost not justified
  without a hard-gate trigger); manual-block-on-spike (introduces a
  procedural pause point that disrupts sequential inline flow).

- **D-10b: "No measurable reduction" outcome — document Phase 90 OR-branch
  + ship v1.12 anyway.** Per Success Criterion #2 wording ("explicit
  'no-improvement' outcome is documented as a Phase 90 OR-branch with the
  next forward path"). If the new CI median is NOT materially below 23:00,
  `docs/test-performance.md § PERF-06 Re-Harvest` adds a "No-Improvement
  Outcome" subsection with: (a) the empirical median number, (b) Phase 86
  / 89 / 90 lever-by-lever recap of what landed and where, (c) hypothesized
  causes for absent CI improvement (cache cold-start dominance, runner
  variance hiding gains, etc.), (d) next forward path for v1.13 (test-
  module-split re-evaluation, runner change, additional cache-key
  consolidation). v1.12 STILL closes — the milestone delivers the levers,
  not a gate-pass. Success Criterion #2's "stretch ≥ 30 % reduction to ≤
  16:00" is observational only; not a blocker. REJECTED: block-close-and-
  re-investigate (scope creep, milestone-stuck pattern that v1.11 explicitly
  avoided via Option-A); "minimum threshold = any measurable reduction"
  wording change (Success Criterion #2 already permits this, no extra
  fence needed).

- **D-04: Baseline-swap shape — replace 23:00 in `STATE.md § Baselines to
  Preserve` with the new median; historical 23:00 carried in
  `PROJECT.md § Key Decisions` + `docs/test-performance.md § Post-
  Optimization Wallclock`.** STATE.md has ONE "CI E2E median" line;
  Plan 91-01 edits it to point at the new value (e.g., "v1.12 baseline:
  XX:XX (5-run median, drop min+max)"). The 23:00 v1.11 reference is
  preserved in two places: (a) PROJECT.md Key-Decisions table gets a new
  row "v1.12 CI E2E median = XX:XX (was 23:00 in v1.11)" or equivalent
  trend-line entry; (b) `docs/test-performance.md` keeps its existing
  Wave-3/Wave-4/Wave-5/Phase-86 CI Results sections untouched (append-
  only structure per Phase 90 pattern), and the new § PERF-06 Re-Harvest
  section explicitly references "vs. 23:00 v1.11 baseline (Phase 86
  closure)". REJECTED: append-both-baselines in STATE.md (STATE.md
  grows with every PERF milestone; not signal-density-friendly);
  hybrid replace + reference line (mid-ground for no real gain — the
  reference is already preserved in PROJECT.md + test-performance.md).

### PR Mechanics

- **D-05: Hybrid Draft-PR shape — open early as Draft, re-harvest on PR-
  branch, finalize at Closer.** Plan 91-01 opens the v1.12 milestone PR
  as a `gh pr create --draft` against `master` immediately after the
  91-01-PLAN.md is committed (before the 5 workflow_dispatch runs). This
  ensures (a) the PR HEAD SHA = `gsd/v1.12-driver-import-and-test-perf`
  HEAD = the SHA `workflow_dispatch` runs target (D-17 equivalence holds);
  (b) the `pull_request` event triggers `ci.yml` automatically for the
  91-01 commit, providing a 6th (validation) CI run alongside the 5
  workflow_dispatch runs — concurrency.cancel-in-progress on the workflow
  means the most-recent run wins; the explicit workflow_dispatch runs
  are isolated from the auto-triggered pull_request run via the
  `gh run watch` serialization pattern (Phase 86 D-17). PR-body is
  rolling: 91-01 lands a placeholder body ("v1.12 milestone PR (work in
  progress) — PERF-06 measurement landing in this commit"), 91-02
  appends the UX-01 section via `gh pr edit --body-file`, 91-03 writes
  the final composite body per D-07. The PR flips Draft → Ready-for-
  review at the end of Plan 91-03. REJECTED: PR-at-end (loses
  pull_request CI validation; loses D-17 SHA-equivalence cleanliness;
  user-feedback dead until the very end); PR-before-PERF-06 = same as
  Hybrid but without explicit Draft-state (loses the social signal that
  the PR is not yet ready for review and may flake while PERF-06 runs).

### UX-01 Shape

- **D-06: Hierarchical sealed-base exception family.**
  - `org.ctc.dataimport.exception.GoogleApiException` — abstract `sealed`
    base, extends `IOException` (so existing throws-IOException method
    signatures in `GoogleSheetsService` + `GoogleCalendarService` work
    without breaking changes). Constructor takes `String message,
    Throwable cause`.
  - `TransientGoogleApiException permits` only — represents retry-able
    failures (network timeout, 5xx, rate-limit). Mapped from `IOException`
    subtypes with no Google-specific status, or `GoogleJsonResponseException`
    with HTTP 5xx.
  - `AuthGoogleApiException` — expired/invalid OAuth token or missing
    credentials. Mapped from `GoogleJsonResponseException` HTTP 401/403
    when the response details indicate auth failure (vs. permission).
  - `NotFoundGoogleApiException` — missing sheet ID or missing calendar
    ID. Mapped from `GoogleJsonResponseException` HTTP 404.
  - `PermissionGoogleApiException` — 403 access-denied where the OAuth
    token IS valid but the resource is not shared with the principal.
    Mapped from `GoogleJsonResponseException` HTTP 403 when the response
    details indicate permission denial (vs. auth).
  Mapping helper: a small static `GoogleApiExceptionMapper` (or method
  on a `GoogleApiClientSupport` utility — planner picks) that takes a
  caught `IOException` / `GeneralSecurityException` / `GoogleJsonResponseException`
  and returns the right typed subtype. Lives next to the exception classes.
  REJECTED: flat single class with category enum (loses compile-time
  catch typing, harder to reason about retry-vs-don't-retry policy at
  service-layer); 2-tier with category enum on Permanent (introduces
  enum + class hierarchy mix — pick one model).

  Sealed-classes work fine on Java 25 + Spring Boot 4; this is the same
  pattern v1.10 Backup phases already used (`BackupValidationException`
  shape).

- **D-07: Error rendering — flash-attribute pattern `errorMessage` +
  `errorCategory`.** Consistent with `CLAUDE.md` § Controller & DTO
  Patterns ("Flash attributes: `successMessage` / `errorMessage`"). New
  flash key: `errorCategory` (String, one of `TRANSIENT`, `AUTH`,
  `NOT_FOUND`, `PERMISSION`). Controllers catch the typed exception in
  the existing try/catch around `googleSheetsService.*` / driver-import
  preview/execute, set both `errorMessage` (user-facing message, e.g.,
  "Connection problem — retry") and `errorCategory` (badge category),
  then redirect. Template renders a CSS-class-driven badge:
  `<span class="error-badge error-badge--auth">...</span>` (CSS lives
  in `admin.css` per CLAUDE.md § CSS Guidelines, no inline styles).
  Calendar-sync surface follows the same pattern where there IS a
  direct user-trigger; where there isn't (background sync), see D-08.
  REJECTED: `BindingResult.reject()` form-field-error (current controllers
  use flash, switching pattern is unjustified churn); dedicated error-card
  Thymeleaf fragment (over-engineered for 4 categories on 2 endpoints).

- **D-08: GoogleCalendarService UX scope — same typed-hierarchy throws,
  consumer-driven UX surface.** `GoogleCalendarService.createEvent()` +
  `updateEvent()` throw the typed exceptions. Consumers
  (`RaceCalendarService` + any controller / scheduled trigger) catch +
  decide: if there IS a direct user trigger (admin-form-submit), translate
  to flash `errorMessage` + `errorCategory` per D-07; if there is NO
  user trigger (background sync, scheduled task), log at `WARN` + use
  the existing graceful-fallback path. Plan 91-02 audits the calendar-
  sync call sites in Plan-1 task to enumerate user-trigger vs.
  background-trigger paths and applies UX only where appropriate. Service
  layer (`GoogleCalendarService`) is uniform — the variance lives in
  consumers, not in the exception hierarchy. REJECTED: driver-import-only
  scope (leaves calendar-sync user-trigger paths with generic IOException
  messages — UX inconsistency); both services + calendar log-and-fallback
  always (defers calendar-trigger UX even when there IS a user trigger,
  same gap as scope-reduction option).

- **D-09: `docs/operations/google-integration.md` — operations-runbook
  shape.** Mirrors `docs/operations/release-runbook.md` +
  `docs/operations/import-runbook.md`. Sections:
  - `# Google Integration Runbook`
  - `## Setup` — credentials file path (per `application*.yml`
    `google.sheets.credentials-path`), OAuth scope, refresh-token
    rotation; brief reference to v1.8 driver-import bootstrap.
  - `## Error Categories` — table with columns `Category | Symptom
    (user-visible) | Root cause | Operator action`. Four rows for
    `TRANSIENT`, `AUTH`, `NOT_FOUND`, `PERMISSION`.
  - `## Troubleshooting` — common patterns (expired refresh token, sheet
    not shared with service account, sheet renamed/moved, calendar API
    quota exhaustion). Each pattern links back to its Error Category
    row.
  Operator-orientiert. REJECTED: pure reference (loses operator-action
  guidance — biggest UX-debt the user surfaced); full guide (scope creep;
  the existing import-runbook + release-runbook are also focused-shape).

### Quality Gates (carry-forward)

- **D-10: Standard gates apply, no tightening, no loosening.** JaCoCo line
  coverage ≥ 88.88 % (v1.11 baseline; Phase 89 Wave-4 held 0.8902, Phase
  90 Wave-5 held 0.8902); SpotBugs `BugInstance` count = 0 (blocking);
  CodeQL gate-step exit 0 on PR HEAD SHA; `EXPORT_ORDER` = 24 entities;
  `BackupSchema.SCHEMA_VERSION` = 1; Flyway V1-V7 immutable. UX-01 adds
  new `src/main/java` code (4 exception classes + 1 mapper utility +
  controller changes); coverage must hold via Plan 91-02 tests covering
  the mapper + each subtype's translation + the flash-attribute population.

### Test Strategy

- **D-11: Nyquist VALIDATION strict — Phase 91 self-validate + retroactive
  check across all 4 v1.12 phases via `/gsd-validate-phase` before
  `/gsd-complete-milestone`.** Plans 91-01, 91-02, 91-03 each ship with
  a Nyquist VALIDATION.md (PERF-06 = measurement validation, UX-01 = code
  validation, Closer = docs/PR-body validation per docs-validation
  precedent). Pre-close gate: `/gsd-validate-phase 88` + `/gsd-validate-phase
  89` + `/gsd-validate-phase 90` + `/gsd-validate-phase 91` all return
  `nyquist_compliant: true`. Phase 88, 89, 90 are already validated (per
  recent commits 850e9a25 + the Phase 87 v1.11 retroactive sweep); 91 is
  the only fresh work. Strict over v1.11's Option-A in-milestone closure
  pattern. Rationale: v1.12 has been disciplined throughout
  (Phase-by-phase validation, no carry-forward debt); closing with
  Option-A would break that discipline. REJECTED: Option-A in-milestone
  closure (precedent exists from v1.11 but introduces ambiguity about
  what "validated" means at milestone-PR-merge time); Nyquist-only-for-
  UX-01 (PERF-06 measurement + Closer docs both benefit from explicit
  validation that the required outputs landed — small extra effort,
  closes the loop).

- **D-12: 3-seed Failsafe verification on UX-01 ITs after Plan 91-02
  refactor.** Same pattern as Phase 90 D-03 + Phase 89 D-13:
  ```
  ./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' \
    -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}
  ```
  Plus a single Surefire-routed seed-stable run for the unit tests
  covering the exception mapper + service-level catch blocks. Reason:
  exception-mapping refactor changes throw shape; downstream consumers
  (controllers) catch the new typed exceptions; small but real test-
  isolation surface deserves empirical verification per
  [[no-flaky-dismissal]].

### Milestone PR Body

- **D-07b: Composite PR body — Summary table + Narrative bullets + CI-Run
  links + Coverage/SpotBugs/CodeQL summary.** Finalized in Plan 91-03.
  Top section: REQ-ID → Phase → Status → Plan → Acceptance markdown
  table (15 rows = 14 must-have + 1 stretch [UX-01] = all REQs from
  REQUIREMENTS.md). Middle section: per-phase narrative bullets (Phase
  88: build/release unblockers + YAGNI sweep + driver-import gap-closure;
  Phase 89: PERF-01/02 per-fork staging + fingerprint listener; Phase 90:
  PERF-03/04/05 cluster consolidation + Testcontainers reuse + module-
  split defer; Phase 91: PERF-06 CI re-harvest + UX-01 + milestone close).
  Bottom section: CI baseline-comparison (v1.11: 23:00 → v1.12: XX:XX),
  JaCoCo line cov (88.88 % → XX.XX %), SpotBugs BugInstance count (0),
  CodeQL gate (exit 0), Phase 90 Wave-5 + Phase 91 PERF-06 cluster diffs
  (delta vs. Phase 89 baseline). CI run links: 5 workflow_dispatch run
  URLs from PERF-06 + the PR pull_request CI run URL. Follows
  [[pr-description-update]] rolling-summary pattern from v1.10 / v1.11.
  REJECTED: per-REQ-ID-table-only (loses readability for non-auditor
  reviewers); narrative-only (loses structured REQ-tracking that
  REQUIREMENTS.md consumers value).

### Production Behavior

- **D-13: Production code touched only in Plan 91-02 (UX-01).** PERF-06
  (Plan 91-01) and Closer (Plan 91-03) are pure docs + CI-harvest +
  PR-mechanics — `src/main/java/**` is git-clean across both plans
  (assertion in each Plan-N SUMMARY). Plan 91-02 touches:
  `src/main/java/org/ctc/dataimport/exception/` (new package, 4 classes
  + 1 mapper helper), `GoogleSheetsService.java`, `GoogleCalendarService.java`,
  `DriverSheetImportController.java`, `DriverSheetImportService.java`
  (if any catch-re-throw lives there), `RaceCalendarService.java` (or
  its controller consumer), Thymeleaf templates under
  `src/main/resources/templates/admin/driver-import/` (badge rendering),
  `src/main/resources/static/admin.css` (`.error-badge--auth` etc.).
  `application*.yml` untouched (D-09 Phase 90 carry-forward).

### Claude's Discretion

- Exact wording of `docs/test-performance.md § PERF-06 Re-Harvest`
  paragraph framing + table layout — planner picks the shape that reads
  well alongside the existing § Post-Optimization Wallclock (Wave 3/4) +
  § PERF-03/04/05 sections.
- Exact wording of `docs/operations/google-integration.md` text — D-06
  locks the shape (Setup + Error Categories + Troubleshooting); planner
  picks prose.
- Exact `GoogleApiException` package location — `org.ctc.dataimport.exception`
  is the default; an `org.ctc.exception.google` alternative is acceptable
  if planner identifies a stronger reason (e.g., calendar-non-import
  consumer scope).
- Exact mapper utility class name and shape (`GoogleApiExceptionMapper`
  with static method, or builder pattern, or method-on-service-base) —
  planner picks based on idiomatic Spring code that aligns with the
  existing `dataimport/` package.
- Exact CSS class names for the 4 category badges — `error-badge`,
  `error-badge--transient`, `error-badge--auth`, `error-badge--not-found`,
  `error-badge--permission` is the default BEM-ish shape; planner can
  adjust if `admin.css` has an existing badge convention.
- Whether the `GoogleApiException` hierarchy lives `sealed permits ...`
  syntax (Java 17+ idiom) or as classic abstract + final subclasses —
  planner picks; `sealed` is more idiomatic on Java 25 but classic shape
  is acceptable.
- Whether `errorCategory` flash-attribute is a `String` ("AUTH",
  "TRANSIENT", …) or the `enum GoogleApiException.Category` directly —
  planner picks; Thymeleaf can render both, String is simpler.
- The post-PERF-06 fingerprint-sidecar retention shape — keep them as
  Phase-91 forensic evidence in `.test-perf-logs/91-01-ci-run-{1..5}/`
  (mirroring Phase 90 Wave-5 evidence retention) OR not capture sidecars
  for CI runs (CI artifacts auto-expire, gh CLI download per run) —
  planner picks based on whether CI run logs need local archival.
- `MILESTONES.md` v1.12 entry wording — planner picks based on the
  v1.11 entry shape (lines 3-44 of MILESTONES.md as anchor).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 91: PERF Re-Harvest, Stretch UX Polish & Milestone Closer" — goal, Depends-on (Phase 90), Requirements (PERF-06, UX-01), Success Criteria 1-5
- `.planning/REQUIREMENTS.md` lines 26-28 — PERF-06 + UX-01 full requirement text
- `.planning/PROJECT.md` § "Current Milestone: v1.12 Driver-Import Gap-Closure & Test Performance Round 2" — Phase-91 forward-path framing; § "Key Decisions" — Phase 86 PERF-04 OR-branch precedent (D-10b anchor)
- `.planning/STATE.md` § "Active Milestone — v1.12" + § "Baselines to Preserve" (JaCoCo ≥ 88.88 %, CI E2E median 23:00 — to be swapped per D-04, SpotBugs 0, CodeQL exit 0, `EXPORT_ORDER` 24, SCHEMA_VERSION 1, Flyway V1-V7 immutable)
- `.planning/STATE.md` § "Deferred Items" — UX-01 row "Google Sheets/Calendar generic error messages on auth/network/sheet-id failures" (resolution = this phase)

### v1.12 Hand-off (PRIMARY INPUT)

- `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-CONTEXT.md` — D-01 (db.migration cluster + composed annotation pattern), D-04 (Testcontainers reuse), D-05 (test-module-split defer with re-eval trigger pointing here), D-07 (Wave-5 idle protocol), D-08 (standard gates carry-forward = D-10 here), D-09 (production yml invariant = D-13 here)
- `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VALIDATION.md` — Nyquist VALIDATION precedent for Phase-by-Phase discipline (D-11 here)
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md` — D-13 (3-seed Failsafe verification — D-12 here), D-14 (production yml invariant carries to D-13 here), D-02 (honest-observational measurement — D-03 + D-10b here)
- `.planning/milestones/v1.12-phases/88-build-release-unblockers-yagni-sweep-doc-conventions-driver-/88-CONTEXT.md` — Pattern of multi-area scope phase with sequential plans (D-02 here)
- `docs/test-performance.md` § Post-Optimization Wallclock (Wave 3/4/5) — baseline trends Phase 86 → 89 → 90; § CI Results (PERF-05) — 23:00 v1.11 baseline (D-04 swap target); § Test-Module-Split Decision — v1.13 re-eval trigger references PERF-06 from this phase

### Phase 86 Anchor (D-17 / D-10 Pattern)

- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md` — D-10 (5-run methodology + 20 % variance tolerance), D-11 (CI = source of truth), D-17 (PR-branch `workflow_dispatch` ≡ post-merge master CI; concurrency.cancel-in-progress workaround via `gh run watch` serialization)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md` — 5-run harvest plan shape, gh CLI sequential workflow_dispatch + run-watch + median-of-3 computation
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-SUMMARY.md` — Phase 86 PERF-05 final CI median 23:00 (the baseline being swapped)

### v1.11 Milestone Close Anchor

- `.planning/MILESTONES.md` § "v1.11 Tooling Infrastructure & Tech-Debt Sweep (Shipped: 2026-05-18)" — lines 3-44 — v1.12 entry shape template (Plan 91-03)
- v1.11 PR #122 history — referenced in PROJECT.md / RETROSPECTIVE.md; per-REQ-ID + narrative composite body shape precedent for D-07b
- `docs/operations/release-runbook.md` + `docs/operations/import-runbook.md` — D-06 docs shape template
- `.planning/RETROSPECTIVE.md` — v1.11 closure pattern + Option-A precedent (D-11 explicitly rejects Option-A for v1.12)

### Code Surface (UX-01 touchpoints — Plan 91-02)

- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` (182 lines) — `readRange`, `readRangeFromSheet`, `getSheetNames`, `getSheetsClient` throws IOException; refactor to typed-exception throws per D-06; existing `IllegalArgumentException` + `GeneralSecurityException` catch sites map to typed subtypes per D-06 mapper
- `src/main/java/org/ctc/dataimport/GoogleCalendarService.java` (120 lines) — `createEvent`, `updateEvent`, `getCalendarClient` throws IOException; same refactor per D-06 + D-08
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (553 lines) — `preview()` (line 66) + `execute()` (line 99) catch sites; controllers consume per D-07 flash pattern
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — preview + execute endpoints; flash-attribute set per D-07 (the controller is where errorMessage + errorCategory land)
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — potential 2nd consumer if CSV path also touches Google services; planner verifies
- `src/main/java/org/ctc/domain/service/RaceCalendarService.java` — calendar-sync consumer per D-08; user-trigger audit (admin form vs. background trigger) determines UX surface
- `src/main/resources/templates/admin/` (driver-import templates + calendar-sync templates) — badge rendering insertion points; planner identifies exact files
- `src/main/resources/static/admin.css` (or equivalent location per `org.ctc.site` static-asset structure) — `.error-badge` + 4 category-specific BEM modifier classes per D-07
- `src/main/java/org/ctc/dataimport/exception/` — new package; 4 exception classes + 1 mapper helper per D-06

### Code Surface (Plan 91-01 PERF-06 + Plan 91-03 Closer — docs/CI only)

- `docs/test-performance.md` — new § PERF-06 Re-Harvest section (D-03 + D-04 + D-10b output)
- `.planning/STATE.md` § Baselines to Preserve — single-line edit per D-04
- `.planning/MILESTONES.md` — v1.12 entry insertion at top per D-02 (Plan 91-03)
- `.planning/PROJECT.md` § Key Decisions — v1.12 baseline trend row per D-04 (Plan 91-03)
- `README.md` § Test Performance — pointer update per Plan 91-03 deliverable
- `README.md` Backup section — v1.12 PR pointer per Plan 91-03 deliverable
- `docs/operations/google-integration.md` — NEW file created in Plan 91-02 per D-09

### Testing & Build Conventions

- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" — UX-01 ITs (if any new ones) get `@Tag("integration")`; § "Integration Testing" — `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` canonical shape OR `@CtcDevSpringBootContext` (Phase 90) if the UX-01 ITs join the consolidated cluster
- `.planning/codebase/TESTING.md` § "Test Invocation Discipline" + CLAUDE.md "Test-Aufrufe optimieren" — single final `./mvnw verify -Pe2e` per plan plus targeted `-Dit.test=*DriverSheet*,*Calendar*` 3-seed runs (D-12)
- `CLAUDE.md` § "Static Analysis" — targeted `@SuppressFBWarnings` only if any SpotBugs surface (D-10)
- `CLAUDE.md` § "CodeQL SAST" — 3-layer FP suppression invariant if any new finding lands
- `CLAUDE.md` § "Controller & DTO Patterns" — flash-attribute pattern (`errorMessage`/`successMessage`) consumed by D-07; new `errorCategory` flash-key documented in Plan 91-02 SUMMARY
- `CLAUDE.md` § "CSS Guidelines" — no inline styles; CSS classes in `admin.css` (D-07 badge classes follow this rule)

### Tooling & CI

- `.github/workflows/ci.yml` — `workflow_dispatch` event in `on:` block; identical steps regardless of trigger event (D-17 equivalence anchor)
- `gh` CLI patterns — `gh workflow run ci.yml --ref gsd/v1.12-driver-import-and-test-perf`, `gh run list --workflow ci.yml --branch gsd/v1.12-driver-import-and-test-perf --limit 5`, `gh run view <id>`, `gh run watch <id>`; `gh pr create --draft`, `gh pr edit --body-file`, `gh pr ready` (Plan 91-03)
- Phase 86 `86-06-PLAN.md` task list (lines 17-31) — concrete shape for D-05 hybrid Draft-PR + 5-run workflow_dispatch harvest

### Memory Cross-References

- [[inline-sequential-execution]] + [[wave-pause]] — drive D-02 three-plans sequential pattern
- [[pr-description-update]] — drives D-05 rolling PR-body + D-07b composite final body
- [[no-flaky-dismissal]] — drives D-12 empirical 3-seed verification
- [[test-call-optimization]] — drives D-10 + D-12 single final verify
- [[clean-maven-build-authority]] — informs Plan 91-02 development workflow (clean Maven test-compile after exception-hierarchy refactor before claiming success)
- [[branch-from-origin]] — driver for any Phase 91 branch hygiene (no `git stash` / `git checkout` in subagents per CLAUDE.md Subagent Rules — relevant only if Plan 91-02 dispatches subagents, which D-02 default does NOT)
- [[squash-merge-message]] — relevant to final milestone PR merge after Phase 91 close
- [[no-local-git-tags]] — Plan 91-03 + post-close: tagging is the CI Release workflow's job after the v1.12 milestone PR squash-merge

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **Existing `GoogleSheetsService` + `GoogleCalendarService` synchronized client-builder pattern** (`GoogleSheetsService.java:157`, `GoogleCalendarService.java:95`) — both services already wrap `GoogleApi*` clients in `synchronized` lazy builders that catch `GeneralSecurityException` and re-throw as `IOException`. UX-01 D-06 extends this: the same try/catch sites now wrap into `AuthGoogleApiException` (for `GeneralSecurityException`) and the appropriate typed subtype (for `IOException` subtypes), via the new mapper.
- **Existing `IllegalArgumentException` URL-format guard** (`GoogleSheetsService.java:138`, `:154`) — already throws a typed exception with a user-friendly message; UX-01 does NOT re-classify this as `GoogleApiException` (it's a client-side input-validation error, not a Google-API error). Flash-attribute mapping should retain the existing behavior for URL-format errors.
- **Existing `BackupValidationException` shape** (v1.10 Backup phases) — precedent for typed-exception hierarchies in this codebase; D-06's sealed-class approach mirrors it.
- **Existing flash-attribute pattern** — `redirectAttributes.addFlashAttribute("errorMessage", ...)` used throughout `admin/controller/` per CLAUDE.md § Controller & DTO Patterns. D-07 extends with a new `errorCategory` key (no infrastructure change).
- **Existing 3-seed Failsafe verification pattern** — Phase 89 D-13 + Phase 90 D-03 (`-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}`). D-12 direct copy-forward for UX-01.
- **Existing gh CLI workflow_dispatch + run-watch pattern** — Phase 86 `86-06-PLAN.md` Plan body. D-05 direct copy-forward for PERF-06 5-run harvest.
- **Existing Phase 86 D-09 idle-protocol pattern for local measurement** — N/A here; Phase 91 PERF-06 is CI-only authoritative measurement.
- **Existing v1.11 PR #122 composite-body shape** — D-07b template; per-REQ-ID + narrative + numbers.
- **Existing `docs/operations/release-runbook.md` + `import-runbook.md`** — D-09 shape template for the new `google-integration.md`.
- **Existing `MILESTONES.md` v1.11 entry shape** — lines 3-44 of `.planning/MILESTONES.md`. Plan 91-03 inserts a v1.12 entry at the top following the same shape.

### Established Patterns

- **Sealed exception hierarchies on Java 25** — `sealed permits` syntax works; v1.10 Backup phases already used this idiom. D-06 follows that pattern.
- **Flash-attribute redirect pattern** — controllers add `errorMessage` / `successMessage` + redirect; Thymeleaf templates render via `${errorMessage}` (CLAUDE.md § Controller & DTO Patterns). D-07 extends with `errorCategory`.
- **`@ControllerAdvice` exception handlers** — global exception handler exists for un-caught exceptions; UX-01 catches the typed subtypes at the controller method level (closer to the user trigger) for category-specific UX, not at the advice level. The advice continues to handle un-caught fallbacks.
- **`admin.css` BEM-ish modifier convention** — `.btn-xs`, `.btn-sm`, `.btn-lg`, `.btn-tab` (CLAUDE.md § Code & CSS guidelines) — D-07 badge classes follow this idiom (`.error-badge--auth`, `.error-badge--transient`, …).
- **`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`** — canonical IT shape; Phase 90 D-02 introduced `@CtcDevSpringBootContext` composed annotation for the `db.migration.**` cluster. UX-01 ITs (if any new ones) can either use the composed annotation OR the classic 2-annotation pair — planner picks based on whether UX-01 ITs would join an existing cluster.
- **D-17 trigger-equivalence** — `workflow_dispatch` on PR-branch ≡ post-merge master CI because `ci.yml` runs identical steps regardless of trigger event. Phase 86 closed PERF-05 inside its own milestone PR using this; D-05 here follows the same pattern.

### Integration Points

- **Spring `RedirectAttributes`** — controllers inject + populate per D-07; no new beans, no new infrastructure.
- **Thymeleaf template fragments** — driver-import + calendar-sync admin templates extend a base layout that already renders `${errorMessage}` from flash; Plan 91-02 inserts the badge `<span>` next to the existing message element.
- **`admin.css`** — single CSS file consumed by `templates/admin/*.html` per CLAUDE.md § Code & CSS guidelines; Plan 91-02 appends `.error-badge` rule set.
- **CI workflow_dispatch event** — `.github/workflows/ci.yml on.workflow_dispatch:` already enabled; Plan 91-01 only consumes, does not edit the workflow.
- **`gh` CLI** — already used throughout the project (release workflow, PR mechanics); Plan 91-01 + 91-03 consume.

</code_context>

<specifics>
## Specific Ideas

- **D-05 Draft-PR opening moment.** Plan 91-01 opens the Draft PR AFTER 91-01-PLAN.md is committed and BEFORE the 5 workflow_dispatch runs are triggered, in the same task block. Sequence: (a) plan + commit 91-01-PLAN.md, (b) `gh pr create --draft --base master --head gsd/v1.12-driver-import-and-test-perf --title "v1.12 Driver-Import Gap-Closure & Test Performance Round 2"` with a minimal placeholder body, (c) capture PR HEAD SHA, (d) trigger 5 sequential workflow_dispatch runs against that SHA with `gh run watch` serialization, (e) record median + edit PR body via `gh pr edit --body-file`.
- **D-07b PR body table layout.** Markdown table with columns: `REQ-ID | Phase | Status | Plan(s) | Acceptance evidence (commit / file ref)`. Sort by REQ-ID (CLEAN-01..03, DOCS-01, DRIV-01..02, REL-01..02, PERF-01..06, UX-01) so reviewers can scan top-down. Coverage / SpotBugs / CodeQL numbers live in a sub-section below the table; CI run links live below that.
- **D-09 google-integration.md Error Categories table layout.** Markdown table with columns: `Category | User-visible message | Root cause | Operator action`. Four rows (TRANSIENT, AUTH, NOT_FOUND, PERMISSION). Each row's User-visible message matches exactly the string the controller sets in `errorMessage` (single source of truth — the doc is authoritative for the message strings).
- **D-11 Nyquist VALIDATION cadence.** Plans 91-01 + 91-02 + 91-03 each produce a `91-NN-VALIDATION.md` at plan-end (via `/gsd-validate-phase 91 --plan NN` or equivalent). Phase-level `91-VALIDATION.md` aggregates the per-plan validations at Plan 91-03 SUMMARY. Pre-`/gsd-complete-milestone` gate: `/gsd-validate-phase {88,89,90,91}` all return `nyquist_compliant: true` AND the milestone-PR is in "Ready for review" state (D-05 flip).
- **D-12 IT touch radius.** Estimated UX-01 IT classes touched by exception-hierarchy refactor: `DriverSheetImportServiceTest`, `GoogleSheetsServiceTest` (if exists), `GoogleCalendarServiceTest` (if exists), `DriverSheetImportControllerIT`. 3-seed verification command targets `'*DriverSheet*,*Calendar*'` to cover both Surefire (unit) and Failsafe (integration) routes.

</specifics>

<deferred>
## Deferred Ideas

- **Wider `@CtcDevSpringBootContext` adoption beyond `db.migration.**`** — Phase 90 deferred; v1.13 re-evaluates against PERF-06 CI median surfaced here. If PERF-06 shows substantial CI improvement, v1.13 cleanup can ship a wider sweep; if not, v1.13 leaves it alone.
- **Test-module-split (`ctc-manager-tests` Maven artifact)** — Phase 90 PERF-05 D-05 deferred with explicit re-evaluation trigger pointing at PERF-06's median. v1.13 milestone-discuss consults `docs/test-performance.md § Test-Module-Split Decision` against the new baseline.
- **CI-side Testcontainers reuse on GitHub-hosted runners** — Phase 90 D-04 explicit out-of-scope; cold-start cost per-job is small relative to 23:00 CI median; future investigation only if a runner with persistent state becomes available.
- **`@CtcLocalSpringBootContext` sister composed annotation** for `@ActiveProfiles("local")` consumers — Phase 90 deferred; out of v1.12 scope.
- **Background-trigger calendar-sync UX surface** — D-08 keeps non-user-triggered calendar paths in graceful-fallback (no UX). A future phase could re-evaluate if operators ask for visibility into background-sync failures (admin notification surface, audit log, etc.).
- **`@ControllerAdvice` typed-exception handlers** — UX-01 catches at the controller method level for category-specific UX. A v1.13 cleanup could extract the catch-and-translate logic into a `@ControllerAdvice` if a third controller starts needing the same pattern; for now (2 controllers, narrow surface) the duplication is acceptable.
- **OAuth re-link flow in the UI** — D-09 documents the operator action ("re-link Google account") but does not ship an in-UI re-link wizard. If `AuthGoogleApiException` rates trend up in v1.13+, an in-UI re-link flow could ship as a separate phase.
- **Retry-with-backoff for `TransientGoogleApiException`** — D-07 surfaces the category to the user with a "retry" message but does NOT automatically retry. A future phase could add a small retry-with-exponential-backoff wrapper around the Google client calls if user feedback indicates the manual-retry friction is too high.
- **Sheet ID lookup helper (resolve URL → sheet ID via Google's metadata endpoint)** — D-09 documents the operator action ("check ID") but does not ship a verifier. Out of UX-01 scope; v1.13 could add an admin-tool ping endpoint.

### Reviewed Todos (not folded)

None — `gsd-sdk query todo.match-phase 91` not yet run; manual review of `.planning/STATE.md § Deferred Items` confirms UX-01 + PERF-06 are the only Phase-91-relevant items, both folded via REQUIREMENTS.md mapping.

</deferred>

---

*Phase: 91-PERF Re-Harvest, Stretch UX Polish & Milestone Closer*
*Context gathered: 2026-05-20*
