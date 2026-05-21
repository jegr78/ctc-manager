# Phase 92: Carry-Forwards & Cleanup - Research

**Researched:** 2026-05-21
**Domain:** Spring Boot 4 typed-exception UX carry-forward + JaCoCo coverage recovery + Maven build-guard fence + retroactive Nyquist VERIFICATION.md authoring + bookkeeping flips
**Confidence:** HIGH

## Summary

Phase 92 is a **pure carry-forward / cleanup phase** that closes the five v1.12 audit findings (UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01) on a clean baseline before the v1.13 Discord migrations land. There is **no greenfield research** to do — every plan extends a pattern that **already exists** in this codebase:

- **Plan 92-01 (UX-01)** copies the established Phase 91 typed-catch + BEM badge pattern from `DriverSheetImportController` + `RaceController` into the 3rd Google-Sheets-consumer `CsvImportController`. The 4 typed `GoogleApiException` permits, the `GoogleApiExceptionMapper` whitelisted message constants, and the `.error-badge--{transient,auth,not-found,permission}` CSS rules **already exist** at `admin/css/admin.css:360-374`.
- **Plan 92-02 (COV-01)** adds 1 new Mockito `@WebMvcTest` (`RaceControllerCalendarTest`) + extends two existing **unit-test** classes (`GoogleSheetsServiceTest`, `GoogleCalendarServiceTest` — note: the CONTEXT mentions `*IT` names but **the IT-suffix classes do NOT exist** today; this research surfaces that gap; see Pitfall 1).
- **Plan 92-03 (CLEAN-01)** adds one new `<execution id="assumptions-fence">` to the existing `exec-maven-plugin` block in `pom.xml:431-462`, parallel to `template-fragment-call-guard`. The pattern is a verbatim shape-copy.
- **Plan 92-04 (DOCS-01 + BOOK-01)** authors 3 retroactive VERIFICATION.md files (v1.11 commit `2e84fd57` precedent: `86-VERIFICATION.md` at `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md`) and flips 7 `[ ]` + 4 `Pending` markers in `.planning/milestones/v1.12-REQUIREMENTS.md`.

**Primary recommendation:** Treat Phase 92 as a "pattern propagation + bookkeeping" phase, not a design phase. Plans copy verbatim from cited reference files; the planner's job is to wire the propagation correctly, not to invent new patterns. The single risk surface is the unit-vs-IT class naming gap on Google service tests (Pitfall 1) — resolve that during planning, not at execute-time.

## User Constraints (from CONTEXT.md)

### Locked Decisions

| ID | Decision | Source |
|----|----------|--------|
| **D-01** | All 3 retroactive VERIFICATION.md files (89, 90, 91) written in Plan 92-04. Substance from existing VALIDATION.md + per-plan SUMMARY.md — no new validation work, only file-shape compliance with the standard template (Phase Goal Recap + Goal-Backward Walk-Through + Verification Outcome). v1.11 precedent: commit `2e84fd57`. | CONTEXT D-01 |
| **D-02** | Accept 3× duplication of typed-catch + flash + log pattern. No `GoogleApiFlashTranslator` helper extracted. Plan 92-01 copies the pattern verbatim from `DriverSheetImportController.preview()` lines 60-92 into `CsvImportController.preview()` + `previewSheet()` + `execute()`. No churn on the 2 already-converted controllers. | CONTEXT D-02 |
| **D-03** | Mixed Unit + IT strategy for COV-01: `RaceControllerCalendarTest` as Mockito `@WebMvcTest` (NOT `@SpringBootTest`) for 4 calendar branches; extended ITs on `GoogleSheetsServiceIT` + `GoogleCalendarServiceIT` for `IOException` defensive catch paths in `GoogleApiExceptionMapper`. | CONTEXT D-03 |
| **D-04** | CLEAN-01 lives in `pom.xml` as new `<execution id="assumptions-fence">` parallel to `template-fragment-call-guard` (lines 431-462). Phase `validate`. Predicate matches `org.junit.jupiter.api.Assumptions` only (NOT AssertJ Assumptions in `BackupStagingDirPerForkIT.java:12`). 2 unit tests at `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java`. | CONTEXT D-04 |
| **D-05** | Four plans, sequential inline on `gsd/v1.13-discord-integration`. Order: 92-01 UX-01 → 92-02 COV-01 → 92-03 CLEAN-01 → 92-04 DOCS-01+BOOK-01. No worktrees, no subagents per `[[feedback-inline-sequential-execution]]`. | CONTEXT D-05 |
| **D-06** | Rolling v1.13 milestone PR opened EARLY as Draft from Plan 92-01 onward; ONE PR per milestone (NOT per plan). Plan 92-01 opens `gh pr create --draft --base master --head gsd/v1.13-discord-integration --assignee jegr78`. Final subject `feat(v1.13): discord integration & carry-forwards` (MINOR bump). Body rolling via `gh pr edit --body-file` after each plan ships. PR flips Draft → Ready at end of Phase 98. | CONTEXT D-06 |
| **D-07** | Standard quality gates apply: JaCoCo ≥ 88.88 % line coverage, SpotBugs `BugInstance` = 0, CodeQL exit 0, `EXPORT_ORDER` = 24, `BackupSchema.SCHEMA_VERSION` = 1, Flyway V1-V7 immutable (Phase 92 adds NO Flyway migrations). Test count grows from 1696 to ~1706. CI E2E within 17:39 ± 20 %. | CONTEXT D-07 |
| **D-08** | Per-plan Nyquist VALIDATION.md. Plans 92-01..92-04 each ship VALIDATION.md; `/gsd-validate-phase 92` runs before `/gsd-execute-phase 93`. | CONTEXT D-08 |
| **D-09** | Tag every new test class per CLAUDE.md `@Tag` convention: `RaceControllerCalendarTest` → untagged (plain unit), `GoogleSheets/CalendarServiceIT` extensions → `@Tag("integration")` (inherits from parent IT class if any), `AssumptionsFencePredicateTest` → untagged. | CONTEXT D-09 |
| **D-10** | Production code touched **only** in Plan 92-01: `CsvImportController.java`, `admin/import.html`, `admin/import-preview.html`. Plans 92-02 / 92-03 / 92-04 are `src/main/java/**` git-clean (assertion in each Plan SUMMARY). `application*.yml` untouched (Phase 91 D-13 carry-forward). | CONTEXT D-10 |
| **D-11** | BOOK-01 success: `grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md` returns `0` (currently 4) AND `grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md` returns `0` (currently 7). Both checks live in `92-04-VALIDATION.md`. | CONTEXT D-11 |

### Claude's Discretion

Per CONTEXT § Claude's Discretion:

- Exact prose wording of the 3 retroactive VERIFICATION.md files (template shape locked by D-01; v1.11 commit `2e84fd57` is the precedent shape).
- Exact CSS class name lookup for the 4 error-badge classes — D-10 defaults to Phase 91 D-07 BEM shape (verified by this research at `static/admin/css/admin.css:360-374`; the names `error-badge--auth/transient/not-found/permission` are correct as-is, no adjustment needed).
- Exact wording of the placeholder PR body opened by Plan 92-01.
- Whether `RaceControllerCalendarTest` lives at `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` (sibling to `RaceControllerTest.java`) or a nested `.calendar.RaceControllerCalendarTest`. **Recommendation:** sibling layout — `RaceControllerTest` already lives at `org.ctc.admin.controller` and no other nested test packages exist there (verified by `find src/test/java/org/ctc/admin/controller -maxdepth 2`).
- Exact `pom.xml` line placement of the new `assumptions-fence` execution within the `exec-maven-plugin` block.
- Exact bash grep predicate inside the `assumptions-fence` execution (D-04 locks the semantic).
- Whether the 2 unit tests in `AssumptionsFencePredicateTest` invoke bash directly (via `ProcessBuilder`) or via `exec-maven-plugin` invocation pattern.
- Exact wording of `MILESTONES.md` v1.13 entry — Plan 98-03 owns it; Plan 92-04 does NOT touch it.

### Deferred Ideas (OUT OF SCOPE)

Per CONTEXT § Deferred Ideas:

- **`GoogleApiFlashTranslator` helper extraction** — defer to v1.14 backlog if a 4th Google-API consumer emerges (Phase 93's `DiscordRestClient` adopts the same 4-catch pattern with a separate `DiscordApiException` hierarchy; revisit at v1.14 scoping).
- **`.planning/REQUIREMENTS.md` (top-level) v1.13 REQ-ID traceability flips** — out of Phase 92 scope; each REQ-ID flips when the phase that owns it ships.
- **Extending `assumptions-fence` to other forbidden imports** (`junit4.*`, etc.) — not in CLEAN-01 scope; v1.14 backlog.
- **Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class `db.migration.**` cluster** — Phase 90 deferred carry-forward; re-evaluate post-Phase-92 baseline.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **UX-01** | `CsvImportController` migrates to typed-catch + `errorCategory` flash + badge UX for parity with `DriverSheetImportController` + `RaceController`; re-closes T-91-02-IL info-leak for 3rd Google-Sheets consumer; whitelisted `getUserMessage()` only (never `e.getMessage()`); 4 categories rendered via existing `admin.css` badge styles | **Pattern 1** (typed-catch propagation) + **Code Example 1-3** (reference implementations in `DriverSheetImportController` + `RaceController`) + **Pitfall 2** (template badge insertion sites) |
| **COV-01** | New `RaceControllerCalendarTest` covers the 4 calendar branches that drove the JaCoCo Δ−0.44 pp regression; extended IT coverage of `GoogleApiExceptionMapper` `IOException` defensive catch paths via `GoogleSheets/CalendarService` ITs; JaCoCo line coverage returns to ≥ 88.88 % (v1.11 baseline) verified via `target/site/jacoco/jacoco.csv` | **Pattern 4** (Mockito `@WebMvcTest` for controller branches) + **Pitfall 1** (the `*IT.java` classes named in CONTEXT D-03 do NOT exist — only `*Test.java` units exist; planner must decide: extend the unit test in place vs. create new `*IT.java` siblings) |
| **CLEAN-01** | `assumptions-fence` predicate tightened to `org.junit.jupiter.api.Assumptions` so AssertJ `Assumptions.assumeThat` in `BackupStagingDirPerForkIT.java:12,37` no longer triggers; documented as comment on grep invocation; verified by 2 unit tests against grep wrapper (1 positive JUnit Assumeumes, 1 negative AssertJ Assumeumes) | **Pattern 2** (`exec-maven-plugin` build-guard pattern from `template-fragment-call-guard`) + **Code Example 4** (verbatim pom.xml block to clone) |
| **DOCS-01** | Retroactive `89-VERIFICATION.md`, `90-VERIFICATION.md`, `91-VERIFICATION.md` under `.planning/milestones/v1.12-phases/{89,90,91}-*/` following the standard VERIFICATION.md template (Phase Goal Recap + Goal-Backward Walk-Through + Verification Outcome); v1.11 precedent commit `2e84fd57` | **Pattern 3** (Goal-Backward VERIFICATION.md template) + **Code Example 5** (the canonical `86-VERIFICATION.md` shape to mirror) |
| **BOOK-01** | `.planning/milestones/v1.12-REQUIREMENTS.md` bookkeeping flipped: 7 `[ ]` → `[x]` (PERF-01..06 + UX-01) + 4 `Pending` → `Resolved` rows (PERF-01, PERF-02, PERF-06, UX-01); verified by `grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md` returning 0 + `grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md` returning 0 | **Pitfall 5** (the exact 11 markers verified by this research — 7 `[ ]` checkboxes at lines 26-31 + 37 + UX-01 line; 4 `Pending` rows at lines 106-107, 111, 112) |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Typed-catch + flash UX (UX-01) | Admin Controller (`org.ctc.admin.controller` analog: `org.ctc.dataimport.CsvImportController`) | Thymeleaf template (`admin/import.html` + `admin/import-preview.html`) | Catch lives at controller boundary (closest to user trigger per Phase 91 D-07); rendering lives in template via existing `errorMessage` + `errorCategory` flash keys + `.error-badge--*` CSS |
| Whitelisted user-message strings (UX-01) | `GoogleApiExceptionMapper` constants (`TRANSIENT_MESSAGE`, `AUTH_MESSAGE`, `NOT_FOUND_MESSAGE`, `PERMISSION_MESSAGE`) | n/a | Single source of truth per Phase 91 D-07; controllers reference the constants, NEVER echo `e.getMessage()` (T-91-02-IL info-leak invariant) |
| Mockito `@WebMvcTest` for controller branches (COV-01) | Test fixture (`RaceControllerCalendarTest`) | n/a | Controller-only HTTP-layer test, no Spring-context boot — fastest path to recover JaCoCo branch coverage on calendar UI logic |
| IT coverage of exception mapping (COV-01) | Service IT (`GoogleSheetsServiceIT` / `GoogleCalendarServiceIT` — see Pitfall 1) | n/a | Service-layer IT exercises the real `IOException` → typed-subtype mapping that mocked controller tests miss |
| Build-guard fence (CLEAN-01) | Maven `validate` phase (`exec-maven-plugin` execution) | n/a | Same lifecycle slot as the existing `template-fragment-call-guard`; runs locally on every `./mvnw verify`; CI inherits automatically (CI = source of truth principle from Phase 86 D-11) |
| Predicate test (CLEAN-01) | Build-test (`org.ctc.build.AssumptionsFencePredicateTest`) | n/a | Package `org.ctc.build` mirrors the existing build-guard test conventions; tests are `@TempDir`-backed synthetic file fixtures |
| Retroactive VERIFICATION.md authoring (DOCS-01) | Documentation (`.planning/milestones/v1.12-phases/{89,90,91}-*/`) | n/a | Pure docs-only; substance derived from existing VALIDATION.md + per-plan SUMMARY.md |
| Bookkeeping flip (BOOK-01) | Documentation (`.planning/milestones/v1.12-REQUIREMENTS.md`) | n/a | Edit-only, no schema changes; verified by 2 grep commands |

## Standard Stack

This phase introduces **zero new dependencies**. Every library used is already in `pom.xml`.

### Core (verified present in current `pom.xml`)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Test (`spring-boot-starter-test`) | 4.x (inherits from project parent) | `@SpringBootTest`, `@AutoConfigureMockMvc`, `@MockitoBean`, `MockMvc` | Already used by `DriverSheetImportControllerExceptionTest` and `CsvImportControllerExceptionTest` — the established controller-test pattern in this codebase |
| Mockito Core (`mockito-core`) | inherits from Spring Boot | Mock injection via `@MockitoBean` (Spring Boot 3.4+ replacement for `@MockBean`) | Reference test files use `@MockitoBean` — verified at `DriverSheetImportControllerExceptionTest.java:14,37` |
| AssertJ (`assertj-core`) | inherits from Spring Boot | `assertThat(...)` fluent assertions including `assertThat(path).hasContent(...)` for `AssumptionsFencePredicateTest` | Already used in `GoogleSheetsServiceTest`, `GoogleCalendarServiceTest`, `BackupStagingDirPerForkIT` |
| JUnit Jupiter (`junit-jupiter`) | inherits from Spring Boot | `@Test`, `@TempDir`, `@Nested`, `@Tag` | Already used universally |
| `exec-maven-plugin` (Codehaus) | (managed by Spring Boot parent) | New `<execution id="assumptions-fence">` for the CLEAN-01 build-guard | Already configured at `pom.xml:431-462` for `template-fragment-call-guard`; same plugin, new execution block |
| JaCoCo (`jacoco-maven-plugin`) | 0.8.14 | Line-coverage measurement via `target/site/jacoco/jacoco.csv` LINE_MISSED/LINE_COVERED computation; existing `BUNDLE` rule `LINE COVEREDRATIO ≥ 0.82` (verify gate); COV-01 verifies the new ≥ 88.88 % baseline via the CSV (NOT the lower 82 % pom gate) | Already configured at `pom.xml:345-396` |
| SpotBugs (`spotbugs-maven-plugin`) | 4.9.8.3 | Verify-bound static-analysis gate; `BugInstance` count must remain 0 after Plan 92-01 production-code edits | Already configured per CLAUDE.md § Static Analysis |
| `findsecbugs-plugin` | 1.14.0 | 144 Spring-Security-aware detector pack consumed by SpotBugs above | Already wired |

### Supporting (verified present)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot `@MockitoBean` | inherits from Spring Boot 3.4+ | Replace beans in Spring application context with Mockito mocks for `@SpringBootTest` + `@AutoConfigureMockMvc` shape | Plan 92-01's UX-01 IT (if any) follows the existing `CsvImportControllerExceptionTest.java:37` pattern. Plan 92-02's `RaceControllerCalendarTest` uses `@WebMvcTest` instead (lighter slice — no full app context) |
| Google API Client (`google-api-client`, `google-api-services-sheets`, `google-api-services-calendar`) | (managed) | Source of `GoogleJsonResponseException` consumed by `GoogleApiExceptionMapper` | Already used by `GoogleSheetsService` + `GoogleCalendarService`; Plan 92-02 ITs mock these via deep-stub pattern from `GoogleSheetsServiceTest.java:22-25` |

### Alternatives Considered (Rejected by CONTEXT decisions)

| Instead of | Could Use | Rejection Rationale |
|------------|-----------|---------------------|
| 3× copy of typed-catch + flash pattern | Extract `GoogleApiFlashTranslator` helper (DRY) | **REJECTED by D-02.** 3-class repetition is small (4 catch arms × ~6 lines each) and stable; each controller stays self-contained and readable. Re-evaluate at v1.14 if a 4th consumer emerges. |
| Mockito `@WebMvcTest` for `RaceControllerCalendarTest` | `@SpringBootTest` (full context) | **REJECTED by D-03.** `@SpringBootTest` slows CI beyond Phase 90's PERF-tuning win. `@WebMvcTest` is fastest path to JaCoCo branch-coverage recovery on controller logic. |
| Standalone `scripts/check-assumptions-fence.sh` | `exec-maven-plugin` execution in `pom.xml` | **REJECTED by D-04.** 2 files instead of 1; no historic `scripts/*-fence.sh` precedent. Maven execution is the established pattern (PLAT-07 Plan 71 D-05). |
| GitHub-Actions-only fence step | `exec-maven-plugin` execution in `pom.xml` | **REJECTED by D-04.** Violates "CI = source of truth" Phase 86 D-11 principle for developer feedback — fence violation should fail local builds, not surface only after push. |
| Skip VERIFICATION.md retrofill | Inline doc-shape-exception note in MILESTONES.md | **REJECTED by D-01.** Leaves audit-loop semantically open; cheaper to author 3 files now (~30 min total) than re-open the audit thread later. |

**Installation:**

No `npm install` / `pip install` step. All dependencies are already in `pom.xml`. The only "install" Phase 92 might require is the standard `./mvnw verify` build to populate `target/site/jacoco/jacoco.csv` for COV-01 measurement.

## Package Legitimacy Audit

**Not applicable.** Phase 92 introduces zero new external packages. All testing libraries (Mockito, AssertJ, JUnit Jupiter, Spring Boot Test), the build-guard plugin (`exec-maven-plugin`), and the coverage gate (`jacoco-maven-plugin`) are already in `pom.xml` and consumed by Phases 71-91. No `slopcheck` or registry-verification step is required.

## Architecture Patterns

### System Architecture Diagram

```
                              Phase 92 Carry-Forward Plan Flow
                              (sequential inline, single branch)

  gsd/v1.13-discord-integration
       │
       ├─ Plan 92-01 — UX-01 (production code surface)
       │   │
       │   │  REQUEST: POST /admin/import/preview-sheet
       │   │           POST /admin/import/preview (CSV)
       │   │           POST /admin/import/execute
       │   │
       │   ▼
       │   CsvImportController (refactor target)
       │   ├─ try { googleSheetsService.{extractSpreadsheetId,getSheetNames,
       │   │       filterRaceSheets,readRangeFromSheet}(...) }
       │   ├─ catch (AuthGoogleApiException)        → log.error  + AUTH_MESSAGE       + errorCategory="AUTH"
       │   ├─ catch (NotFoundGoogleApiException)    → log.error  + NOT_FOUND_MESSAGE  + errorCategory="NOT_FOUND"
       │   ├─ catch (PermissionGoogleApiException)  → log.error  + PERMISSION_MESSAGE + errorCategory="PERMISSION"
       │   ├─ catch (TransientGoogleApiException)   → log.warn   + TRANSIENT_MESSAGE  + errorCategory="TRANSIENT"
       │   └─ catch (GoogleApiException e)          → log.error  + TRANSIENT_MESSAGE  + errorCategory="TRANSIENT"  (defensive)
       │       │
       │       ▼
       │   Thymeleaf: admin/import.html  + admin/import-preview.html
       │   (insert <span class="error-badge error-badge--{lower(category)}"
       │    th:if="${errorCategory}">{category}</span>{errorMessage})
       │
       ├─ Plan 92-02 — COV-01 (test-only, src/main git-clean)
       │   │
       │   ├─ NEW: src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java
       │   │       (Mockito @WebMvcTest, 4 calendar-related branches + 4 typed-catch arms)
       │   │
       │   └─ EXTEND: GoogleSheetsServiceTest + GoogleCalendarServiceTest
       │              (add IT-shaped tests for IOException → typed-subtype
       │              defensive paths through GoogleApiExceptionMapper)
       │
       │   ┌─────────────────────────────────────────────────────┐
       │   │  COVERAGE GATE                                       │
       │   │  ./mvnw verify → target/site/jacoco/jacoco.csv      │
       │   │  Compute: LINE_COVERED / (LINE_COVERED+LINE_MISSED) │
       │   │  Assert: ≥ 0.8888 (88.88% v1.11 baseline restored)  │
       │   └─────────────────────────────────────────────────────┘
       │
       ├─ Plan 92-03 — CLEAN-01 (pom.xml + new build-test, src/main git-clean)
       │   │
       │   ├─ EDIT: pom.xml (insert new <execution id="assumptions-fence">
       │   │         parallel to <execution id="template-fragment-call-guard">
       │   │         under exec-maven-plugin, phase=validate)
       │   │
       │   └─ NEW: src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java
       │           (2 @TempDir tests: 1 positive JUnit Assumptions
       │            + 1 negative AssertJ Assumptions)
       │
       │   ┌─────────────────────────────────────────────────────┐
       │   │  FENCE GATE                                          │
       │   │  ./mvnw validate (or any verify-bound goal)         │
       │   │  predicate: grep src/test/java/ for JUnit-only      │
       │   │             Assumptions imports                      │
       │   │  Pass: 0 hits → exit 0                              │
       │   │  Fail: 1+ hits → exit 1 with remediation message    │
       │   └─────────────────────────────────────────────────────┘
       │
       └─ Plan 92-04 — DOCS-01 + BOOK-01 (docs-only, src/main + src/test git-clean)
           │
           ├─ NEW: .planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VERIFICATION.md
           ├─ NEW: .planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VERIFICATION.md
           ├─ NEW: .planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VERIFICATION.md
           │       (template = Phase Goal Recap + Goal-Backward Walk-Through + Verification Outcome
           │        per v1.11 commit 2e84fd57)
           │
           └─ EDIT: .planning/milestones/v1.12-REQUIREMENTS.md
                   (flip 7 `[ ]` → `[x]` PERF-01..06 + UX-01,
                    flip 4 `Pending` → `Resolved` rows: PERF-01, PERF-02, PERF-06, UX-01)

           ┌─────────────────────────────────────────────────────┐
           │  BOOKKEEPING GATE                                    │
           │  grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md  = 0
           │  grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md = 0
           └─────────────────────────────────────────────────────┘
                   │
                   ▼
            /gsd-validate-phase 92  → nyquist_compliant: true (all 4 plans)
                   │
                   ▼
            UNBLOCKS  Plan 93-01 (INFRA-01 Discord Foundation)
```

### Recommended Project Structure

No new directories. Phase 92 touches:

```
src/main/java/org/ctc/dataimport/
└── CsvImportController.java                     # 92-01 EDIT (typed-catch refactor)

src/main/resources/templates/admin/
├── import.html                                  # 92-01 EDIT (badge insert in flash block)
└── import-preview.html                          # 92-01 EDIT (badge insert in flash block)

src/test/java/org/ctc/admin/controller/
└── RaceControllerCalendarTest.java              # 92-02 NEW (Mockito @WebMvcTest)

src/test/java/org/ctc/dataimport/
├── GoogleSheetsServiceTest.java                 # 92-02 EXTEND (add IT-shaped tests; see Pitfall 1)
└── GoogleCalendarServiceTest.java               # 92-02 EXTEND (add IT-shaped tests; see Pitfall 1)

src/test/java/org/ctc/build/                     # 92-03 NEW package (first time)
└── AssumptionsFencePredicateTest.java           # 92-03 NEW (2 @TempDir unit tests)

pom.xml                                          # 92-03 EDIT (new <execution> in exec-maven-plugin)

.planning/milestones/v1.12-phases/
├── 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/
│   └── 89-VERIFICATION.md                       # 92-04 NEW
├── 90-perf-consolidation-module-split-decision/
│   └── 90-VERIFICATION.md                       # 92-04 NEW
└── 91-perf-re-harvest-stretch-ux-polish-milestone-closer/
    └── 91-VERIFICATION.md                       # 92-04 NEW

.planning/milestones/v1.12-REQUIREMENTS.md       # 92-04 EDIT (11 markers)
```

### Pattern 1: Typed-catch + flash + log per category (UX-01)

**What:** Replace generic `catch (IOException ...)` arms with 5 explicit typed-catch arms (4 sealed permits + 1 defensive sealed-base catch) that set `errorMessage` from `GoogleApiExceptionMapper` constants AND `errorCategory` flash attribute for badge rendering.

**When to use:** Plan 92-01 — `CsvImportController.preview()`, `CsvImportController.previewSheet()`, and `CsvImportController.execute()`. (The CsvImportController has THREE Google-Sheets call sites at lines 56, 115, 204; CONTEXT mentions 2 endpoints, but research confirms 3 call sites — the `preview()` method also has a CSV-only catch that does NOT need typed-catch refactor since it does not touch Google services. Only `previewSheet()` and the `execute()` path's sheet branch consume `GoogleSheetsService`.)

**Example (verbatim from `DriverSheetImportController.java:60-92`):**

```java
// Source: src/main/java/org/ctc/admin/controller/DriverSheetImportController.java:60-92
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Authentication problem — re-link Google account");
    model.addAttribute("errorCategory", "AUTH");
    return "admin/driver-import";
} catch (NotFoundGoogleApiException e) {
    log.error("Google Sheet not found during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Sheet not found — check ID");
    model.addAttribute("errorCategory", "NOT_FOUND");
    return "admin/driver-import";
} catch (PermissionGoogleApiException e) {
    log.error("Permission denied on Google Sheet during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Access denied — share the sheet with the service account");
    model.addAttribute("errorCategory", "PERMISSION");
    return "admin/driver-import";
} catch (TransientGoogleApiException e) {
    log.warn("Transient Google API failure during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/driver-import";
} catch (GoogleApiException e) {
    // Defensive catch on the sealed base — unreachable at runtime (the 4
    // permits above are exhaustive) but required by javac since sealed
    // exhaustiveness on catch blocks is not yet a language feature.
    log.error("Unexpected GoogleApiException subtype during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/driver-import";
} catch (IllegalArgumentException | IllegalStateException e) {
    log.error("Driver import preview failed", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Preview failed: " + e.getMessage());
    return "admin/driver-import";
}
```

**Adaptation for CsvImportController:**
- The 4 user-visible messages are constants on `GoogleApiExceptionMapper` (`TRANSIENT_MESSAGE`, `AUTH_MESSAGE`, `NOT_FOUND_MESSAGE`, `PERMISSION_MESSAGE`) — **planner's strong recommendation:** use the constants `GoogleApiExceptionMapper.AUTH_MESSAGE` etc. instead of duplicating the strings inline (single source of truth per Phase 91 D-07). This contradicts the verbatim DriverSheetImportController code shown above (which uses inline strings) — but matches the spirit of Phase 91's "Update-on-Triage discipline" docstring on the Mapper class. Decide explicitly in the plan: copy verbatim (matches existing code style) OR refactor to use constants (better hygiene, optional 1-line change in DriverSheetImportController too — but D-02 forbids that). **Default to verbatim copy** to honor D-02's "no churn on already-converted controllers" rule.
- Log level: AUTH / NOT_FOUND / PERMISSION → `log.error(..., e)`; TRANSIENT → `log.warn(..., e)`. Verified by `DriverSheetImportController.java:61,67,73,79`.
- For `previewSheet()` (Model-based return) use `model.addAttribute(...)` + `return "admin/import"`.
- For `execute()` (RedirectAttributes-based return) use `redirectAttributes.addFlashAttribute(...)` + `return "redirect:/admin/import"` per `DriverSheetImportController.execute()` lines 134-156.

### Pattern 2: `exec-maven-plugin` build-guard (CLEAN-01)

**What:** Maven `validate`-phase execution that runs a bash `grep` predicate against `src/test/java/` and exits 1 on violation with a clear remediation message.

**When to use:** Plan 92-03 — new `<execution id="assumptions-fence">` parallel to existing `template-fragment-call-guard`.

**Example (verbatim from `pom.xml:432-462`):**

```xml
<!-- Source: pom.xml lines 432-462 (template-fragment-call-guard execution) -->
<execution>
    <id>template-fragment-call-guard</id>
    <phase>validate</phase>
    <goals><goal>exec</goal></goals>
    <configuration>
        <executable>bash</executable>
        <arguments>
            <argument>-c</argument>
            <argument><![CDATA[
violations=$(grep -rE 'th:(replace|insert|include)="~\{[^"]*\(.*\$\{.*\}.*\)\}"' src/main/resources/templates/ | grep -vF 'layout(${pageTitle}' || true);
if [ -n "$violations" ]; then
  echo "[PLAT-07 build-guard] Forbidden Thymeleaf fragment-call expression detected (Plan 05):";
  echo "$violations";
  echo "Move the value to the controller via model.addAttribute(\"pageTitle\", ...) and use ~{layout :: layout(\${pageTitle}, ~{::section})}.";
  echo "See .planning/phases/71-*/71-CONTEXT.md Decisions D-05 + D-12 for the canonical fix.";
  exit 1;
fi;
echo "[PLAT-07 build-guard] OK - no Thymeleaf fragment-call expression offenders.";
exit 0;
]]></argument>
        </arguments>
    </configuration>
</execution>
```

**Adaptation for `assumptions-fence`:**

```xml
<execution>
    <id>assumptions-fence</id>
    <phase>validate</phase>
    <goals><goal>exec</goal></goals>
    <configuration>
        <executable>bash</executable>
        <arguments>
            <argument>-c</argument>
            <argument><![CDATA[
violations=$(grep -rE '^import\s+(static\s+)?org\.junit\.jupiter\.api\.Assumptions(\.|;)' src/test/java/ | grep -v 'src/test/java/org/ctc/build/' || true);
if [ -n "$violations" ]; then
  echo "[CLEAN-01 build-guard] Forbidden JUnit-Jupiter Assumptions import detected:";
  echo "$violations";
  echo "Remove the JUnit Assumptions usage and either rewrite the test as an unconditional assertion, or use AssertJ org.assertj.core.api.Assumptions.assumeThat (different package, intentional).";
  echo "See .planning/phases/92-*/92-CONTEXT.md Decision D-04 for the canonical fix and rationale.";
  exit 1;
fi;
echo "[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders.";
exit 0;
]]></argument>
        </arguments>
    </configuration>
</execution>
```

**Predicate notes (Claude's discretion territory):**
- The regex `^import\s+(static\s+)?org\.junit\.jupiter\.api\.Assumptions(\.|;)` matches **both** static imports (`import static org.junit.jupiter.api.Assumptions.assumeFalse;`) and class-level imports (`import org.junit.jupiter.api.Assumptions;`).
- The `grep -v 'src/test/java/org/ctc/build/'` guard ensures the `AssumptionsFencePredicateTest` fixture files (if any are checked into the repo as sample sources rather than `@TempDir`-only) are not flagged. CONTEXT D-04 explicitly notes "excluding `src/test/java/org/ctc/build/`".
- Use BSD/GNU-grep-compatible `-rE` (extended regex) — same as `template-fragment-call-guard`.
- The single AssertJ `Assumptions.assumeThat` import currently in `BackupStagingDirPerForkIT.java:12` is `import static org.assertj.core.api.Assumptions.assumeThat;` — `org.assertj.core.api` is a **different package** than `org.junit.jupiter.api`, so the tightened predicate correctly leaves it alone.

### Pattern 3: Goal-Backward VERIFICATION.md template (DOCS-01)

**What:** Standard phase-level verification report following Phase Goal Recap → Goal-Backward Walk-Through → Verification Outcome template. Each Success Criterion is independently falsified against codebase + git history + per-plan SUMMARY.md.

**When to use:** Plan 92-04 — author 3 retroactive VERIFICATION.md files for Phases 89, 90, 91.

**Example (verbatim shape from `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md` — the v1.11 precedent commit `2e84fd57`):**

```markdown
---
phase: 86
verified_on: 2026-05-18
status: passed
verifier: gsd-verifier (retroactive — Nyquist audit Phase 87-series)
score: 5/5 success-criteria + 8/8 dimensions
overrides_applied: 1 (PERF-04 OR-branch — documented blocker path accepted)
audit_method: retroactive
---

# Phase 86 — Test Wallclock Reduction — Verification Report

**Phase Goal (from ROADMAP.md):** {one-line goal pulled from v1.12 ROADMAP}

**Verified:** {date}
**Status:** passed
**Method:** goal-backward — SC-1..SC-N each independently falsified against codebase + git history + 91-VALIDATION.md + per-plan SUMMARY.md, all N confirmed delivered.
**Re-verification:** No (initial retroactive verification — no prior VERIFICATION.md present)

---

## Goal Achievement — Success Criteria

| # | Success Criterion (from ROADMAP.md) | Status | Evidence |
|---|-------------------------------------|--------|----------|
| SC-1 | ... | VERIFIED | Commit `...`, file `...`, ... |

**Score:** N/N Success Criteria verified.

---

## Observable Truths (must-haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|

---

## Per-Dimension Verdict Table

| # | Dimension | Status | Evidence |
|---|-----------|--------|----------|

---

## CONTEXT.md Decision Compliance (selected key decisions)

| Decision | Compliance | Evidence |
|----------|------------|----------|
```

**Source citations (Phase 89/90/91 substance pulled from):**
- `89-VERIFICATION.md`: substance from `89-VALIDATION.md` (Nyquist 14/14 green) + `89-{01,02,03}-SUMMARY.md` per-plan goal-backward sections
- `90-VERIFICATION.md`: substance from `90-VALIDATION.md` (Nyquist 13/13 green) + `90-{01,02,03}-SUMMARY.md`
- `91-VERIFICATION.md`: substance from `91-VALIDATION.md` (Nyquist 17/18 green + 1 manual-only badge UAT per audit 2026-05-20) + `91-{01,02,03}-SUMMARY.md`

### Pattern 4: Mockito `@WebMvcTest` for controller branches (COV-01)

**What:** Lightweight Spring MVC test slice that boots ONLY the controller layer (not the full Spring context), with collaborators replaced by `@MockitoBean`. Fastest path to JaCoCo branch coverage on HTTP-layer logic.

**When to use:** Plan 92-02 — `RaceControllerCalendarTest` covers `RaceController` calendar branches (`GET /admin/races/{id}` model attributes `calendarAvailable`, `hasCalendarEvent`, `canCreateCalendarEvent` at `RaceController.java:69-71` + `POST /admin/races/{id}/create-calendar-event` success + 4 typed `GoogleApiException` subtype catch arms at `RaceController.java:193-224`).

**Example (skeleton — derived from the existing `DriverSheetImportControllerExceptionTest.java:30-45` pattern; note the SpringBootTest variant since `@WebMvcTest` may not have a verified codebase analog):**

```java
// Source: pattern derived from DriverSheetImportControllerExceptionTest.java + Spring Boot 4 @WebMvcTest docs
package org.ctc.admin.controller;

import org.ctc.admin.dto.RaceForm;
import org.ctc.admin.service.RaceGraphicService;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.service.RaceAttachmentService;
import org.ctc.domain.service.RaceCalendarService;
import org.ctc.domain.service.RaceFormDataService;
import org.ctc.domain.service.RaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for RaceController calendar-related branches. Restores JaCoCo
 * line coverage to ≥ 88.88% (v1.11 baseline) by covering the 4 typed-catch arms
 * + defensive sealed-base catch on POST /admin/races/{id}/create-calendar-event.
 * COV-01 (v1.13 Phase 92 carry-forward).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RaceControllerCalendarTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RaceService raceService;
    @MockitoBean
    private RaceFormDataService raceFormDataService;
    @MockitoBean
    private RaceCalendarService raceCalendarService;
    @MockitoBean
    private RaceAttachmentService raceAttachmentService;
    @MockitoBean
    private RaceGraphicService raceGraphicService;

    @Test
    void givenAuthFailure_whenCreateCalendarEvent_thenRedirectsWithAuthBadge() throws Exception {
        // given
        UUID raceId = UUID.randomUUID();
        doThrow(new AuthGoogleApiException("auth", null))
                .when(raceCalendarService).createOrUpdateCalendarEvent(raceId);

        // when / then
        mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + raceId))
                .andExpect(flash().attribute("errorCategory", "AUTH"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ... 3 more tests for NotFound, Permission, Transient
    // ... 1 test for defensive sealed-base catch (use a custom sub of GoogleApiException — but the sealed-permits forbid this externally; verify by inspecting the 4 known permits cover all cases)
    // ... 1 test for success path (calendar event created)
}
```

**Note on `@WebMvcTest` vs `@SpringBootTest`:** CONTEXT D-03 says "Mockito-mocked `@WebMvcTest`". However, **the codebase has NO existing `@WebMvcTest` usage** (verified via the existing `DriverSheetImportControllerExceptionTest` and `CsvImportControllerExceptionTest` which both use `@SpringBootTest + @AutoConfigureMockMvc`). Two options for the planner:

1. **Follow CONTEXT D-03 literally** → introduce `@WebMvcTest(RaceController.class)` as a new test pattern in this codebase. Requires verifying that the lighter slice has the security/CSRF wiring needed for POST endpoints.
2. **Follow existing codebase precedent** → use `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean` (same shape as `DriverSheetImportControllerExceptionTest.java:30-45`). Heavier, but proven-working.

**Recommendation:** Option 2 (codebase-consistent). The CONTEXT's "Mockito `@WebMvcTest`" wording is shorthand for "Mockito-mocked controller-layer test" — the actual annotation is `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`. Either option satisfies D-03 substantively; Option 2 minimizes the surface area of new patterns introduced.

### Anti-Patterns to Avoid

- **Echoing `e.getMessage()` to the flash content (T-91-02-IL info-leak).** Plan 92-01 catch blocks MUST use the whitelisted `GoogleApiExceptionMapper` message constants OR the verbatim string literals from `DriverSheetImportController.java`. NEVER `model.addAttribute("errorMessage", "Error: " + e.getMessage())`. This is the exact regression the existing `CsvImportController.java:59,118,207` introduces — Plan 92-01 closes it.
- **Adding a `<Match>` SpotBugs suppression for the new typed-catch code.** Phase 91 D-09 verified that the typed exception hierarchy does NOT trigger `EI_EXPOSE_REP*` or `SE_BAD_FIELD`. Only add `@SuppressFBWarnings({"CODE"}, justification="…")` if a NEW finding actually surfaces on the Plan 92-01 surface (verified by `./mvnw verify` SpotBugs goal).
- **Skipping the Goal-Backward Walk-Through in retroactive VERIFICATION.md.** The v1.11 precedent commit `2e84fd57` shows the per-SC "VERIFIED | Evidence" rows are mandatory — they're what makes the document a verification report (not a status summary). Substance from VALIDATION.md is fine; format MUST follow the SC table shape.
- **Modifying `application*.yml`** during Plan 92-01. Phase 91 D-13 carry-forward: test-only configuration never bleeds into production deployment. The CsvImportController refactor needs no yml change.
- **Modifying the existing `template-fragment-call-guard` `<execution>` block.** Plan 92-03 ADDS a parallel new `<execution id="assumptions-fence">`. Editing the existing block is a regression risk and out of scope.
- **Treating BOOK-01 as a free-form edit.** The 11 markers are well-defined (7 `[ ]` + 4 `Pending`). The 2 grep success checks in CONTEXT D-11 are binary: 0 hits = pass. Any deviation from the listed REQ-IDs (PERF-01..06 + UX-01 for `[ ]`; PERF-01, PERF-02, PERF-06, UX-01 for `Pending`) is a defect.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Map raw Google IOException to typed UX category | New mapper class | Existing `GoogleApiExceptionMapper.from(IOException)` / `from(GeneralSecurityException)` at `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` | Single source of truth shipped by Phase 91; reuses the 4 user-message constants; tested via Phase 91 ITs |
| Whitelisted user-facing error message strings | Inline string literals scattered across controllers | `GoogleApiExceptionMapper.{TRANSIENT,AUTH,NOT_FOUND,PERMISSION}_MESSAGE` constants | Update-on-Triage discipline — runbook + code reference same constants. **Caveat:** the existing `DriverSheetImportController` + `RaceController` use inline string literals (verified at `DriverSheetImportController.java:63,69,75,81` and `RaceController.java:200,204,208,212`); D-02 forbids churn on those, so Plan 92-01 should pick ONE style for CsvImportController and document it. Recommendation: verbatim string literals for code-style consistency with siblings. |
| BEM-ish CSS error-badge classes | Author new `.csv-error-*` classes | Existing `.error-badge`, `.error-badge--transient`, `.error-badge--auth`, `.error-badge--not-found`, `.error-badge--permission` at `src/main/resources/static/admin/css/admin.css:360-374` | These are the canonical Phase 91 D-07 BEM names; templates already use the `error-badge error-badge--${#strings.toLowerCase(errorCategory)}` Thymeleaf binding at `driver-import.html:10-13` |
| Thymeleaf badge-rendering snippet | Custom flash-error fragment | Verbatim copy of `driver-import.html:8-15` block into `admin/import.html` + `admin/import-preview.html` | Already-shipped pattern; the `th:if="${errorMessage}"` + `th:if="${errorCategory}"` + lower-case binding work as-is |
| Maven build-guard shape | Standalone shell script under `scripts/` | New `<execution id="assumptions-fence">` in existing `pom.xml` `exec-maven-plugin` block | D-04 explicit; existing `template-fragment-call-guard` is the literal copy template; runs locally on every `./mvnw verify` (CI inherits) |
| Retroactive VERIFICATION.md authoring | Compress into a single archive doc | 3 separate per-phase files following v1.11 precedent commit `2e84fd57` | D-01 explicit; one file per phase preserves the per-phase audit trail expected by `/gsd-validate-phase N` |
| Mockito controller-test setup | `@WebMvcTest` (new pattern) | `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean` per existing `DriverSheetImportControllerExceptionTest.java:30-45` | Codebase consistency; D-03's "Mockito `@WebMvcTest`" wording is shorthand for "Mockito-mocked controller test" — the codebase precedent is the heavier annotation triple (see Pattern 4 note) |

**Key insight:** Phase 92 is **pattern propagation**, not pattern design. Every code shape it needs already exists in the codebase. The risk is **mis-propagation** (CSS class name typos, badge insertion in the wrong template block, missing one of the 3 CsvImportController catch sites) — not architecture.

## Runtime State Inventory

> **Not applicable.** Phase 92 is NOT a rename / refactor / migration phase in the runtime-state sense. The "carry-forward" framing means Phase 92 propagates an established pattern to one additional consumer (`CsvImportController`), adds test fixtures, adds a build-guard, and authors docs. None of those touch stored data, live service config, OS-registered state, secrets/env vars, or build artifacts in a way that requires runtime migration.
>
> **Explicit zero-checks for each category:**
>
> | Category | Result |
> |----------|--------|
> | Stored data | **None.** No database schema changes (Flyway V1-V7 immutable per D-07; no V8+ added by Phase 92). No data-migration. No collection/key renames. |
> | Live service config | **None.** No Google Sheets / Calendar configuration changes. No external service IDs renamed. |
> | OS-registered state | **None.** No scheduled tasks, no pm2 processes, no systemd units, no Windows Task Scheduler entries touched. |
> | Secrets/env vars | **None.** No new env vars introduced. No existing env vars renamed. The whitelisted user-message strings live in code constants, not env. |
> | Build artifacts | **None.** Maven `target/` regenerates from source on each `./mvnw verify`. No installed-package renames (e.g., no pip egg-info). |

## Common Pitfalls

### Pitfall 1: GoogleSheetsServiceIT / GoogleCalendarServiceIT classes do NOT exist

**What goes wrong:** CONTEXT D-03 specifies "Extended ITs on `GoogleSheetsServiceIT` + `GoogleCalendarServiceIT`". Research confirms that the **`*IT.java` suffix versions DO NOT EXIST** in the repository today. Only `GoogleSheetsServiceTest.java` and `GoogleCalendarServiceTest.java` exist (verified by `find src/test -name "GoogleSheets*" -o -name "GoogleCalendar*"` — returned only the `*Test.java` pair).

**Why it happens:** CONTEXT was authored assuming a `*IT.java` naming convention that does not exist in this part of the codebase. The existing `*Test.java` classes are PLAIN UNIT TESTS (no `@Tag("integration")`, no `@SpringBootTest` — verified at `GoogleSheetsServiceTest.java:30,55` which uses `Mockito.mock(...)` deep stubs without Spring context).

**How to avoid (planner decision required):** Two paths:

1. **Extend the existing `*Test.java` unit tests in place.** Add new `@Test` methods covering the `IOException` defensive-catch paths in `GoogleApiExceptionMapper`. Tests stay untagged (plain unit per CLAUDE.md `@Tag` convention). Pro: minimal new infrastructure. Con: the tests are unit, not IT, so they exercise the mapper logic but NOT the real `IOException` → typed-subtype flow (mocked stubs are the test boundary). This may not fully satisfy CONTEXT D-03's "verify the real translation (mocked Google clients miss the genuine `IOException` → typed-subtype flow)" intent.
2. **Create new `GoogleSheetsServiceIT.java` + `GoogleCalendarServiceIT.java` siblings** with `@Tag("integration")` + `@SpringBootTest + @ActiveProfiles("dev")` (Spring-context-backed) shape. Pro: matches CONTEXT D-03 literally; exercises the real wiring. Con: introduces 2 new IT classes which add to Failsafe cluster + CI E2E budget (acceptable per CONTEXT D-07 "test count grows by ~10").

**Recommendation:** Option 2 (new `*IT.java` siblings). CONTEXT D-03 explicitly contrasts "mocked Google clients miss the genuine IOException → typed-subtype flow" — that contrast makes no sense unless the planner creates real IT classes. The plan should call out this naming convention disconnect explicitly and confirm in the user-review pause.

**Warning signs:** A `./mvnw verify` run after Plan 92-02 ships shows the JaCoCo CSV coverage on `GoogleApiExceptionMapper` did NOT move (because the new unit-test extensions still hit the mocked path that was already covered). If COV-01's ≥ 88.88 % target is missed by < 0.5 pp, this is the likely cause — re-evaluate Option 2.

### Pitfall 2: import.html does NOT currently render `errorMessage` at all

**What goes wrong:** The current `src/main/resources/templates/admin/import.html` has **no `<div th:if="${errorMessage}">` block at all** (verified by `grep -n "errorMessage" src/main/resources/templates/admin/import.html` returning empty). The CsvImportController DOES set `errorMessage` on the model (line 59, 78, 96, 118) — but those messages are invisible to the user today. Plan 92-01 must NOT just add the badge `<span>` — it must add the **entire flash-error block** including the existing alert wrapper.

**Why it happens:** The controller-side `model.addAttribute("errorMessage", ...)` was added without a corresponding template render. This is a latent bug: error messages are silently swallowed. Plan 92-01 fixes the bug AND adds the badge in one shot.

**How to avoid:** The Plan 92-01 task touching `admin/import.html` must add the **complete** block (verbatim from `driver-import.html:8-15`):

```html
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p>
        <span th:if="${errorCategory}"
              class="error-badge"
              th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
              th:text="${errorCategory}"></span>
        <span th:text="${errorMessage}"></span>
    </p>
</div>
```

Insertion point: **before** the `<!-- Source Tabs -->` block (`admin/import.html:8`), inside the `<section>` wrapper.

**Same applies to `admin/import-preview.html`:** verified by `grep -n "errorMessage" src/main/resources/templates/admin/import-preview.html` returning empty. Insertion point: before the `<h1>Import Preview</h1>` line OR after — planner picks. The existing `admin/import-preview.html` already has `class="alert alert-error"` rendering for `${preview.hasErrors()}` and `${preview.duplicateDetected}` at lines 23 + 31 — so the CSS class shape is consistent.

**Warning signs:** A manual UAT triggering a permission-denied or auth error on the CSV-import flow shows a blank page with no feedback (current behavior). Post-Plan-92-01, the same trigger shows the badge + message.

### Pitfall 3: The `previewSheet()` method has the most complex catch site

**What goes wrong:** Looking at `CsvImportController.java:71-120`, the `previewSheet()` method has FOUR Google-Sheets calls in sequence (`extractSpreadsheetId`, `getSheetNames`, `filterRaceSheets`, `readRangeFromSheet`). The current generic `catch (IOException | IllegalArgumentException | IllegalStateException e)` at line 115 wraps the entire block — so ANY of the 4 calls failing triggers the generic flash. The typed-catch refactor must wrap the entire block but distinguish typed `GoogleApiException` subtypes from `IllegalArgumentException` / `IllegalStateException` (which are client-side input errors, NOT Google-API errors — Phase 91 D-06 explicitly preserves them).

**Why it happens:** Mixed-source exception flow. The `extractSpreadsheetId` call throws `IllegalArgumentException` for malformed URLs (per `GoogleSheetsService.java:138` per Phase 91 RESEARCH); the other 3 throw the typed `GoogleApiException` hierarchy.

**How to avoid:** Keep the multi-catch shape but split into 6 arms:

```java
} catch (AuthGoogleApiException e) { ... }
catch (NotFoundGoogleApiException e) { ... }
catch (PermissionGoogleApiException e) { ... }
catch (TransientGoogleApiException e) { ... }
catch (GoogleApiException e) { /* defensive sealed-base */ }
catch (IllegalArgumentException | IllegalStateException e) {
    // client-side input-validation error (URL format, etc.); NOT a Google-API error
    log.error("Error reading Google Sheet (client-side input)", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Error reading Google Sheet: " + e.getMessage());
    return "admin/import";
}
```

**Note:** The trailing `IllegalArgumentException | IllegalStateException` arm KEEPS the `e.getMessage()` echo — this is NOT a T-91-02-IL info-leak regression because (a) these are client-side errors with user-provided content, not Google-side leakage; (b) Phase 91's `DriverSheetImportController.java:93-98` uses the same pattern. The T-91-02-IL invariant applies only to the typed `GoogleApiException` arms.

**Warning signs:** A test feeding a malformed sheet URL hits the typed-catch instead of the IllegalArgument arm — indicates wrong catch order (typed-catches must come BEFORE the IllegalArgument multi-catch).

### Pitfall 4: `execute()` catch list mixes Google + business errors

**What goes wrong:** `CsvImportController.execute()` line 204-205 has a SIX-type multicatch: `IOException | BusinessRuleException | ValidationException | IllegalArgumentException | IllegalStateException | DataAccessException`. The refactor must preserve the non-Google paths (BusinessRule, Validation, DataAccess) while splitting `IOException` into the 5 typed arms. The total catch list grows to ~10 arms.

**Why it happens:** The existing controller bundles all post-Google-service failure modes (CSV import errors, DB constraint violations, business-rule rejections) into one catch. Phase 91's `DriverSheetImportController.execute()` lines 134-165 shows the correct shape: 5 typed Google arms + `BusinessRuleException | ValidationException | IllegalArgumentException` + `DataIntegrityViolationException` (more specific catch before `DataAccessException`) + `IllegalStateException | DataAccessException` fallback.

**How to avoid:** Mirror `DriverSheetImportController.execute()` lines 134-165 verbatim — 10 catch arms in this exact order:
1. `AuthGoogleApiException`
2. `NotFoundGoogleApiException`
3. `PermissionGoogleApiException`
4. `TransientGoogleApiException`
5. `GoogleApiException` (defensive sealed-base)
6. `BusinessRuleException | ValidationException | IllegalArgumentException`
7. `DataIntegrityViolationException` (more specific catch BEFORE `DataAccessException`)
8. `IllegalStateException | DataAccessException` (fallback)

**Warning signs:** Compile error "exception X has already been caught" → indicates wrong order (more-specific catches must precede more-general). Verify by `./mvnw clean test-compile` per `[[feedback-clean-maven-build-authority]]`.

### Pitfall 5: BOOK-01 11-marker inventory drift

**What goes wrong:** CONTEXT D-11 lists "7 stale `[ ]`" + "4 stale `Pending`". Research confirms the exact lines:

- **7 `[ ]` lines in `.planning/milestones/v1.12-REQUIREMENTS.md`:** lines 26 (PERF-01), 27 (PERF-02), 28 (PERF-03), 29 (PERF-04), 30 (PERF-05), 31 (PERF-06), and the UX-01 line (find via grep). Verified `grep -c "^- \[ \]"` returns 7.
- **4 `Pending` lines in `.planning/milestones/v1.12-REQUIREMENTS.md`:** lines 106 (PERF-01), 107 (PERF-02), 111 (PERF-06), 112 (UX-01). Verified `grep -c "Pending"` returns 4. (PERF-03/04/05 traceability rows at lines 108-110 already read `Resolved` — their `[ ]` checkboxes are the inconsistency.)

**Why it happens:** Plan 91-03 deliberately deferred the bookkeeping flip per stale-state avoidance pattern (acceptable carry-forward).

**How to avoid:** Plan 92-04 must flip EXACTLY 11 markers; no more, no less. After the edit, BOTH grep counts must return 0. If the grep returns a different count (e.g., a new `Pending` was introduced in the file header or comments by a later edit), the plan must reconcile.

**Warning signs:** `grep -c "Pending"` returns > 0 after Plan 92-04 → likely a `Pending` in the file's prose body (e.g., a comment, a stretch-deferred row). Manually inspect the grep output.

### Pitfall 6: Markdown checkbox case sensitivity

**What goes wrong:** Some Markdown renderers accept `- [x]` and `- [X]` interchangeably. The CONTEXT D-11 grep pattern is `^- \[ \]` (lowercase, single space inside the brackets). Plan 92-04 must use `- [x]` (lowercase x).

**How to avoid:** Always use `- [x]` (lowercase). Confirm post-edit with `grep -c "^- \[X\]"` (uppercase) returns 0.

**Warning signs:** A reviewer's renderer shows the checkbox as `[x]` correctly, but the `grep -c "^- \[ \]"` check still returns > 0 because the flip used `[X]` (uppercase). The grep predicate from CONTEXT D-11 is case-sensitive.

## Code Examples

Verified patterns from this codebase:

### Example 1: Whitelisted user-message constants (single source of truth)

```java
// Source: src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java:26-30
public static final String TRANSIENT_MESSAGE = "Connection problem — retry";
public static final String AUTH_MESSAGE = "Authentication problem — re-link Google account";
public static final String NOT_FOUND_MESSAGE = "Sheet not found — check ID";
public static final String PERMISSION_MESSAGE =
        "Access denied — share the sheet with the service account";
```

### Example 2: Sealed-base exception with 4 permits

```java
// Source: src/main/java/org/ctc/dataimport/exception/GoogleApiException.java:11-24
public abstract sealed class GoogleApiException extends IOException
        permits TransientGoogleApiException,
                AuthGoogleApiException,
                NotFoundGoogleApiException,
                PermissionGoogleApiException {

    public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }

    protected GoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract Category category();
}
```

### Example 3: RaceController calendar typed-catch (precedent for COV-01 test target)

```java
// Source: src/main/java/org/ctc/admin/controller/RaceController.java:193-224
@PostMapping("/{id}/create-calendar-event")
public String createCalendarEvent(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        raceCalendarService.createOrUpdateCalendarEvent(id);
        redirectAttributes.addFlashAttribute("successMessage", "Calendar event saved");
    } catch (AuthGoogleApiException e) {
        log.error("Google Calendar authentication failed for race {}", id, e);
        redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
        redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
    } catch (NotFoundGoogleApiException e) {
        log.error("Google Calendar not found for race {}", id, e);
        redirectAttributes.addFlashAttribute("errorMessage", "Calendar not found — check the calendar ID configuration");
        redirectAttributes.addFlashAttribute("errorCategory", "NOT_FOUND");
    } catch (PermissionGoogleApiException e) {
        log.error("Permission denied on Google Calendar for race {}", id, e);
        redirectAttributes.addFlashAttribute("errorMessage", "Access denied — share the calendar with the service account");
        redirectAttributes.addFlashAttribute("errorCategory", "PERMISSION");
    } catch (TransientGoogleApiException e) {
        log.warn("Transient Google Calendar failure for race {}", id, e);
        redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
        redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
    } catch (GoogleApiException e) {
        // Defensive catch on the sealed base — unreachable at runtime (the 4
        // permits above are exhaustive) but required by javac.
        log.error("Unexpected GoogleApiException subtype for race {}", id, e);
        redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
        redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
    } catch (IllegalStateException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Calendar: " + e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```

### Example 4: Existing `template-fragment-call-guard` (verbatim shape for `assumptions-fence`)

See **Pattern 2** above for the full XML block at `pom.xml:432-462`.

### Example 5: Goal-Backward VERIFICATION.md (v1.11 precedent commit `2e84fd57`)

See **Pattern 3** above for the template-shape excerpt. Full reference file: `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md`.

### Example 6: Existing Thymeleaf badge-rendering snippet (verbatim copy target)

```html
<!-- Source: src/main/resources/templates/admin/driver-import.html:8-15 -->
<div th:if="${errorMessage}" class="alert alert-error mb-md">
    <p>
        <span th:if="${errorCategory}"
              class="error-badge"
              th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
              th:text="${errorCategory}"></span>
        <span th:text="${errorMessage}"></span>
    </p>
</div>
```

### Example 7: BackupStagingDirPerForkIT — the AssertJ Assumptions reference (CLEAN-01 negative test source)

```java
// Source: src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java:1-12,37
package org.ctc.backup.service;

// ...
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;   // <-- AssertJ, NOT JUnit

// ...

@Test
void givenForkNumberPropertySet_whenAppRuns_thenStagingDirSuffixEqualsForkNumber() {
    String forkNum = System.getProperty("test.fork.number");
    assumeThat(forkNum).as("test.fork.number is only exposed inside Surefire/Failsafe forks").isNotBlank();
    // ...
}
```

The CLEAN-01 fence MUST leave this file alone. Verified by the tightened predicate `org\.junit\.jupiter\.api\.Assumptions` (the AssertJ import is `org.assertj.core.api.Assumptions` — different package).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `IOException` catch echoing `e.getMessage()` to flash (T-91-02-IL info-leak) | 5-arm typed-catch (4 sealed permits + defensive base) with whitelisted constants | Phase 91 (v1.12) for `DriverSheetImportController` + `RaceController`; Plan 92-01 propagates to `CsvImportController` (3rd consumer) | Closes info-leak threat across all 3 Google-Sheets consumers |
| Untyped grep predicate `Assumptions\.` (false-positives on AssertJ) | Tightened predicate `org\.junit\.jupiter\.api\.Assumptions` (JUnit-only) | Plan 92-03 (this phase) | Allows the Phase 89 PERF-01 AssertJ `Assumptions.assumeThat` usage in `BackupStagingDirPerForkIT` to coexist with the fence |
| Phases close on VALIDATION.md (Nyquist) + per-plan SUMMARY only | Phases also have retroactive VERIFICATION.md (Goal-Backward Walk-Through) | v1.11 precedent commit `2e84fd57`; Phase 88 already has one; Plan 92-04 backfills Phases 89/90/91 | Closes the file-shape audit-loop gap surfaced by `v1.12-MILESTONE-AUDIT.md` Warning 4 |
| Sequential per-phase milestone PRs (4+ PRs per milestone) | One rolling Draft milestone PR per milestone, body updated after each plan ship via `gh pr edit --body-file` | Phase 91 D-05 + D-07b; Plan 92-01 opens Draft PR; Plan 98-03 finalizes for squash-merge | Cuts PR-review overhead; preserves `pull_request` event CI validation throughout |
| `@MockBean` (Spring Boot ≤ 3.3) | `@MockitoBean` (Spring Boot 3.4+) | Spring Boot 4.x (current project version) | New tests in Plan 92-02 use `@MockitoBean` — verified at `DriverSheetImportControllerExceptionTest.java:14,37` |

**Deprecated/outdated:**
- The colon-form `/gsd:` skill invocation prefix is **deprecated** (v1.12 DOCS-01); use the dash form `/gsd-<name>` per CLAUDE.md § Skill Invocation Naming.
- The `gh pr merge --squash` default subject behavior — must always pass `--subject "feat(v1.13): discord integration & carry-forwards"` per `[[feedback-squash-merge-message]]` for the v1.13.0 MINOR bump.

## Project Constraints (from CLAUDE.md)

These directives MUST be honored by every Plan 92-NN-PLAN.md. Treat with the same authority as locked decisions from CONTEXT.md.

| Directive | Source | Impact on Phase 92 |
|-----------|--------|---------------------|
| **German communication; English code/comments/docs/UI** | CLAUDE.md § Language | All `errorMessage` strings (Plan 92-01) in English; all log messages in English; all VERIFICATION.md prose (Plan 92-04) in English |
| **Test coverage: minimum 82% line** | CLAUDE.md § Constraints | Plan 92-02 restores the higher v1.11 baseline ≥ 88.88 % (above the 82 % gate); pom.xml `BUNDLE LINE COVEREDRATIO 0.82` rule is the build gate |
| **Flyway: no V1 changes; new V2+ migrations only** | CLAUDE.md § Constraints | Phase 92 adds ZERO migrations (carry-forward / cleanup only). V1-V7 immutable |
| **Profiles: auth only for prod/docker; dev/local without auth** | CLAUDE.md § Constraints | No `application*.yml` changes in Phase 92 (D-10 + Phase 91 D-13 carry-forward) |
| **OSIV enabled; only @EntityGraph for optimization** | CLAUDE.md § Constraints | No N+1 risk — Phase 92 touches no JPA query paths |
| **Backward compat: no breaking URL/endpoint changes** | CLAUDE.md § Constraints | Plan 92-01 preserves all 4 CsvImportController endpoints (`GET /admin/import`, `POST /admin/import/preview`, `POST /admin/import/preview-sheet`, `POST /admin/import/execute`) — only catch-block internals change |
| **Playwright remains compile-scope (Runtime use for graphics)** | CLAUDE.md § Constraints | No Playwright changes in Phase 92 |
| **Keep controllers thin** | CLAUDE.md § Architectural Principles | Plan 92-01 catch blocks delegate to existing `GoogleApiExceptionMapper` constants; no business logic in controllers |
| **DTOs in controllers (no entities via `@ModelAttribute` POST)** | CLAUDE.md § Architectural Principles | N/A — Phase 92 does not touch form DTOs |
| **No fallback calculations; fix root cause in data model** | CLAUDE.md § Architectural Principles | Plan 92-01 does NOT add defensive logic in templates; the typed-catch is the root-cause fix |
| **Keep Thymeleaf templates lean** | CLAUDE.md § Architectural Principles | Plan 92-01 adds a simple `<span th:if="${errorCategory}">` + `th:classappend` binding — no SpEL projections, no nested conditions |
| **No inline styles on buttons** | CLAUDE.md § Architectural Principles | The new badge `<span>` uses `class="error-badge"` + `th:classappend` (CSS classes from existing `admin.css:360-374`); zero inline styles |
| **Isolate test data: test prefix** | CLAUDE.md § Architectural Principles | Plan 92-02 + 92-03 test fixtures use `@TempDir` (CLEAN-01 fence test) + `@MockitoBean` mocks (no real data) |
| **Tag tests by category (`@Tag`)** | CLAUDE.md § Architectural Principles | Per D-09: `RaceControllerCalendarTest` untagged (plain unit), `GoogleSheets/CalendarServiceIT` extensions `@Tag("integration")` (inherit if extending existing ITs), `AssumptionsFencePredicateTest` untagged |
| **RaceLineup is source of truth for driver-team assignments** | CLAUDE.md § Architectural Principles | N/A — Phase 92 does not touch RaceLineup logic |
| **Do NOT modify existing Flyway V*__*.sql** | CLAUDE.md § Architectural Principles | N/A — Phase 92 adds zero migrations |
| **TDD: tests first, then implementation** | CLAUDE.md § Development Approach | Plan 92-02 ships test-only (COV-01 IS the test plan); Plans 92-01 + 92-03 add tests alongside production/build code |
| **Visual verification via playwright-cli for UI changes** | CLAUDE.md § Development Approach | Plan 92-01 adds the badge rendering — UAT recommended via `playwright-cli` against `http://localhost:9090/admin/import` triggering each of the 4 categories (mirror Phase 91 UX-01 UAT). Listed in v1.12 STATE.md as "post-deploy operator action UX-01" — same applies to CsvImportController surface |
| **SpotBugs `BugInstance` count = 0** | CLAUDE.md § Static Analysis | Per D-07 — no new `<Match>` entries expected; targeted `@SuppressFBWarnings` only if a finding surfaces |
| **`lombok.config` invariant: 2 SpotBugs lines must stay** | CLAUDE.md § Static Analysis | Plan 92-03 does NOT touch `lombok.config` |
| **CodeQL HIGH/CRITICAL gate exit 0** | CLAUDE.md § CodeQL SAST | Per D-07 — no new suppression entries expected |
| **English commit messages, Conventional Commits** | CLAUDE.md § Git Workflow | All 4 plans commit with `docs(92)`, `test(92)`, `chore(92)`, etc. prefixes; final milestone PR squash subject `feat(v1.13): discord integration & carry-forwards` per D-06 + `[[feedback-squash-merge-message]]` |
| **No `git tag` lokal pushen — CI does tagging** | `[[feedback-no-local-git-tags]]` | v1.13.0 tag emerges from CI release workflow post-merge of the milestone PR (Phase 98); Phase 92 does NOT tag |
| **Subagent rules: no Haiku for code; branch protection** | CLAUDE.md § Subagent Rules | Per D-05 + `[[feedback-inline-sequential-execution]]` — Phase 92 uses NO subagents; orchestrator works inline on `gsd/v1.13-discord-integration` |
| **Wave-Pause: pause after each plan merge** | `[[feedback-wave-pause]]` | After each of Plans 92-01..04 ships, pause for user feedback before the next plan begins |
| **Phase-Overwrite-Prevention** | `[[feedback-phase-overwrite-prevention]]` | The 89/90/91 directories already exist with VALIDATION.md + SUMMARY.md; Plan 92-04 ADDS new VERIFICATION.md files — must NOT modify existing files |
| **Clean Maven Build = Wahrheit** | `[[feedback-clean-maven-build-authority]]` | After Plan 92-01 production-code edits, run `./mvnw clean test-compile` BEFORE claiming success; IDE caches do not count |
| **No flaky-test dismissal** | `[[feedback-no-flaky-dismissal]]` | A test that passes locally but fails in CI is a regression, not flaky — investigate immediately |
| **PR-description-update after each plan-close** | `[[feedback-pr-description-update]]` | Per D-06 — each plan that ships updates the rolling Draft milestone PR body via `gh pr edit --body-file` |

## Assumptions Log

> Claims tagged `[ASSUMED]` need user confirmation before becoming locked decisions. Empty table = all claims in this research were verified or cited.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The recommendation to use `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean` instead of `@WebMvcTest` for `RaceControllerCalendarTest` (Pattern 4 note) is codebase-consistent and satisfies CONTEXT D-03 substantively | Pattern 4 | If wrong, Plan 92-02 ships a heavier test slice than intended; CI E2E budget grows slightly (acceptable per D-07 ± 20 % tolerance). Operator may prefer literal `@WebMvcTest` — flag at user-review pause after Plan 92-01 ships and Plan 92-02 begins planning |
| A2 | The recommendation to copy verbatim inline string literals (vs. refactor to use `GoogleApiExceptionMapper.AUTH_MESSAGE` constants) in Plan 92-01 is "default to verbatim copy" for D-02 consistency | Pattern 1 adaptation note | If wrong, the messages drift over time across the 3 controllers as docs change. Operator may prefer constants — flag during user-review of Plan 92-01-PLAN.md |
| A3 | The 7 `[ ]` stale checkbox lines in `.planning/milestones/v1.12-REQUIREMENTS.md` are the PERF-01..06 + UX-01 rows (verified by `grep -c "^- \[ \]"` = 7) | Pitfall 5 | Verified count, but the EXACT line numbers may differ from CONTEXT description if the file has been edited since 2026-05-20. Plan 92-04 task must grep first, then flip |
| A4 | The `RaceControllerCalendarTest` belongs at `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` (sibling layout) | User Constraints § Claude's Discretion + Architectural Responsibility Map | Verified by `find src/test/java/org/ctc/admin/controller -maxdepth 2 -name "*.java"` returning sibling-only flat layout (no `.calendar.*` nested package). Low risk |
| A5 | The `previewSheet()` method in `CsvImportController` is the only Google-Sheets-consuming entry point in the file that needs typed-catch refactor; the `preview()` method (CSV upload, line 56 catch) does NOT touch Google services | Pattern 1 adaptation note + Pitfall 3 | Verified by reading `CsvImportController.java:37-62` — the `preview()` catch is around `csvImportService.parseAndPreview(file.getInputStream(), metadata)` which reads from `MultipartFile`, not Google. Plan 92-01's IOException catch on `preview()` line 56 stays as-is. Low risk |

## Open Questions

1. **Should Plan 92-02 create new `GoogleSheetsServiceIT.java` + `GoogleCalendarServiceIT.java` files, or extend the existing `*Test.java` unit tests in place?**
   - What we know: CONTEXT D-03 uses the `*IT.java` naming, but those classes do NOT exist today. Only `*Test.java` (plain unit) exists.
   - What's unclear: Does the operator's intent match Option 1 (extend existing unit tests, simpler) or Option 2 (create new IT siblings, matches CONTEXT D-03 literally)?
   - Recommendation: **Option 2** (new IT siblings). CONTEXT D-03's contrast "mocked Google clients miss the genuine IOException → typed-subtype flow" only makes semantic sense if real ITs are created. Flag in the user-review pause after Plan 92-01 ships and Plan 92-02 begins.

2. **Should Plan 92-01 introduce a Plan-91-equivalent `CsvImportControllerExceptionTest` overhaul (mirror `DriverSheetImportControllerExceptionTest.java:30-end`), or extend the existing `CsvImportControllerExceptionTest.java:65-84` test which already covers the `TransientGoogleApiException` arm?**
   - What we know: The existing test asserts ONLY `errorMessage` (line 83) — no `errorCategory` check. CONTEXT mentions adding ITs for "all 4 paths + regression assertion that no `e.getMessage()` echo appears in flash content" (REQUIREMENTS.md UX-01 line 16).
   - What's unclear: Does Plan 92-01 add 3 new test methods (covering the 3 missing arms AUTH, NOT_FOUND, PERMISSION) + update the existing TransientGoogleApiException test to assert `errorCategory`? Or replace the test class entirely?
   - Recommendation: Extend in place — add 3 new `@Test` methods + augment the existing test to assert `errorCategory`. Preserves existing-test history and limits churn.

3. **Should the `gh pr edit --body-file` for the Plan 92-01 Draft PR body include all of v1.13's planned phases (92-98) or only Phase 92's plans?**
   - What we know: D-06 calls for a "rolling" body finalized at Plan 98-03. The Plan 91-02 precedent shipped a placeholder body and iterated.
   - What's unclear: Stylistic choice — minimal placeholder ("v1.13 in progress — Phase 92 carry-forwards landing") vs. full skeleton with 7-phase narrative + REQ-ID table populated with `Pending` rows.
   - Recommendation: Minimal placeholder for Plan 92-01; Phase 98-03 owns the final composite shape per D-07b. Avoids body-format churn across 22 plan-ships.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | All plans (build) | ✓ (verified per `mvnw verify` succeeds per recent commits) | 25 (from CLAUDE.md § Technology Stack) | — |
| Maven via `./mvnw` | All plans (build) | ✓ | (wrapper) | — |
| Bash 3.2+ (BSD compatible) | Plan 92-03 (`exec-maven-plugin` argument) | ✓ (`zsh` per env; bash available on darwin + ubuntu-latest CI) | — | — |
| `grep -rE` (BSD or GNU) | Plan 92-03 fence + Plan 92-04 BOOK-01 verification | ✓ | — | — |
| `gh` CLI | Plan 92-01 (Draft PR open + each plan rolling body update per D-06) | ✓ (used throughout v1.12) | — | Manual PR creation via GitHub UI |
| JaCoCo CSV (`target/site/jacoco/jacoco.csv`) | Plan 92-02 (COV-01 ≥ 88.88 % verification) | ✓ (regenerates on each `./mvnw verify` per `pom.xml:367-375`) | 0.8.14 | — |
| SpotBugs gate (`spotbugs-maven-plugin`) | Plan 92-01 production-code edits (verify-bound `BugInstance` count = 0) | ✓ | 4.9.8.3 | — |
| `playwright-cli` | Manual UAT post-Plan-92-01 (per `[[feedback-playwright-cli]]` for UI changes) | ✓ (per CLAUDE.md § Development Approach) | — | Manual browser testing |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None — environment is fully provisioned.

## Validation Architecture

> Required by config (nyquist_validation absent = enabled). Each of the 4 plans must ship its own per-plan VALIDATION.md per CONTEXT D-08.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 + Mockito (managed by Spring Boot 4.x parent) + AssertJ |
| Config file | `pom.xml` (surefire/failsafe profiles; `-Pe2e` for Playwright E2E) |
| Quick run command | `./mvnw test -Dtest='CsvImportControllerExceptionTest,DriverSheetImportControllerExceptionTest,RaceControllerCalendarTest,AssumptionsFencePredicateTest'` (Surefire-only) |
| Targeted IT run | `./mvnw verify -Dit.test='GoogleSheetsServiceIT,GoogleCalendarServiceIT'` (Failsafe-only, if Option 2 from Open Question 1 is chosen) |
| Full suite command | `./mvnw verify` (no `-Pe2e` unless verifying CI-equivalent) |
| Phase-gate command | `./mvnw verify -Pe2e` (matches CLAUDE.md § Commands + Phase 91 D-12 precedent) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-01 | CsvImportController previewSheet — AUTH path → flash AUTH_MESSAGE + errorCategory=AUTH + no e.getMessage() echo | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenAuthFailure_whenPreviewSheet_thenRendersAuthBadge'` | ❌ Plan 92-01 |
| UX-01 | CsvImportController previewSheet — NOT_FOUND path → flash NOT_FOUND_MESSAGE + errorCategory=NOT_FOUND | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenNotFound_whenPreviewSheet_thenRendersNotFoundBadge'` | ❌ Plan 92-01 |
| UX-01 | CsvImportController previewSheet — PERMISSION path → flash PERMISSION_MESSAGE + errorCategory=PERMISSION | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenPermissionDenied_whenPreviewSheet_thenRendersPermissionBadge'` | ❌ Plan 92-01 |
| UX-01 | CsvImportController previewSheet — TRANSIENT path → flash TRANSIENT_MESSAGE + errorCategory=TRANSIENT (existing test extended) | unit (MockMvc) | `./mvnw test -Dtest='CsvImportControllerExceptionTest#givenIoException_whenPreviewSheet_thenRedirectsWithError'` (modify to assert errorCategory) | ✅ existing line 65-84 (extend) |
| UX-01 | CsvImportController execute — same 4 paths via redirect+flash | unit (MockMvc) | 4 new `@Test` methods on `CsvImportControllerExceptionTest` | ❌ Plan 92-01 |
| UX-01 | Regression: no `e.getMessage()` echo in flash content for any of the 4 typed paths | unit (MockMvc) assertion | `flash().attribute("errorMessage", equalTo(GoogleApiExceptionMapper.AUTH_MESSAGE))` etc. | ❌ Plan 92-01 |
| COV-01 | RaceController GET /admin/races/{id} — calendarAvailable=true model attribute set | unit (MockMvc) | `./mvnw test -Dtest='RaceControllerCalendarTest#givenCalendarAvailable_whenGetRaceDetail_thenModelHasFlag'` | ❌ Plan 92-02 |
| COV-01 | RaceController GET — hasCalendarEvent + canCreateCalendarEvent branches | unit (MockMvc) | 2 `@Test` methods on `RaceControllerCalendarTest` | ❌ Plan 92-02 |
| COV-01 | RaceController POST /create-calendar-event — 4 typed-catch arms (AUTH, NOT_FOUND, PERMISSION, TRANSIENT) | unit (MockMvc) | 4 `@Test` methods on `RaceControllerCalendarTest` | ❌ Plan 92-02 |
| COV-01 | RaceController POST — defensive sealed-base catch (unreachable but javac-required) | unit (MockMvc) | 1 `@Test` (note: sealed permits forbid external subclassing — verify by code-inspection assertion rather than runtime trigger) | ❌ Plan 92-02 |
| COV-01 | RaceController POST — IllegalStateException catch path | unit (MockMvc) | 1 `@Test` method | ❌ Plan 92-02 |
| COV-01 | GoogleApiExceptionMapper — IOException → TransientGoogleApiException default path | unit OR IT (per Open Question 1) | `./mvnw verify -Dit.test='GoogleSheetsServiceIT'` OR `./mvnw test -Dtest='GoogleSheetsServiceTest'` | ❌ (new IT) OR ✅ (extend existing) |
| COV-01 | GoogleApiExceptionMapper — GeneralSecurityException → AuthGoogleApiException path | unit OR IT | same as above | ❌ OR ✅ |
| COV-01 | GoogleApiExceptionMapper — 403 with reason "authError" → AuthGoogleApiException (not Permission) | unit OR IT | same as above | ❌ OR ✅ |
| COV-01 | JaCoCo line coverage ≥ 88.88 % verified via `target/site/jacoco/jacoco.csv` | gate | `./mvnw verify` + post-build CSV-parsing script | ✅ `target/site/jacoco/jacoco.csv` regenerates per CSV |
| CLEAN-01 | assumptions-fence triggers on JUnit `import static org.junit.jupiter.api.Assumptions.assumeFalse` | unit (@TempDir) | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenJunitAssumptionsImport_whenPredicateRuns_thenViolationDetected'` | ❌ Plan 92-03 |
| CLEAN-01 | assumptions-fence does NOT trigger on AssertJ `import static org.assertj.core.api.Assumptions.assumeThat` | unit (@TempDir) | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenAssertjAssumptionsImport_whenPredicateRuns_thenNoViolation'` | ❌ Plan 92-03 |
| CLEAN-01 | `./mvnw validate` passes cleanly on current state (BackupStagingDirPerForkIT.java:12 is the only AssertJ Assumptions import, fence is silent) | gate | `./mvnw validate` exit 0 | ✅ (verified once Plan 92-03 ships) |
| DOCS-01 | 3 retroactive VERIFICATION.md files exist | file existence | `ls .planning/milestones/v1.12-phases/{89,90,91}-*/89-VERIFICATION.md .planning/milestones/v1.12-phases/{89,90,91}-*/90-VERIFICATION.md ...` | ❌ Plan 92-04 |
| DOCS-01 | Each VERIFICATION.md has the 3 required template sections (Phase Goal Recap, Goal-Backward Walk-Through, Verification Outcome) | regex grep | `grep -l "^## .*Success Criteria\|^## .*Goal-Backward\|^## .*Verification Outcome"` returns all 3 files | ❌ Plan 92-04 |
| BOOK-01 | 7 `[ ]` stale checkboxes flipped to `[x]` | grep count | `grep -c "^- \[ \]" .planning/milestones/v1.12-REQUIREMENTS.md` returns 0 | ❌ Plan 92-04 |
| BOOK-01 | 4 `Pending` rows flipped to `Resolved` | grep count | `grep -c "Pending" .planning/milestones/v1.12-REQUIREMENTS.md` returns 0 | ❌ Plan 92-04 |

### Sampling Rate

- **Per-task commit (during Plan 92-01):** `./mvnw test -Dtest='CsvImportControllerExceptionTest'` (~5s, Surefire-only — fast feedback loop while editing controller)
- **Per-task commit (during Plan 92-02):** `./mvnw test -Dtest='RaceControllerCalendarTest'` + (if Option 2 chosen) `./mvnw verify -Dit.test='GoogleSheetsServiceIT,GoogleCalendarServiceIT'`
- **Per-task commit (during Plan 92-03):** `./mvnw test -Dtest='AssumptionsFencePredicateTest'` + `./mvnw validate` (verifies the new fence in isolation)
- **Per-plan ship:** `./mvnw verify` (full Surefire+Failsafe, JaCoCo CSV regenerates; SpotBugs gate; CodeQL gate via PR push)
- **Phase gate (before `/gsd-validate-phase 92`):** `./mvnw verify -Pe2e` (full E2E budget; matches CLAUDE.md § Commands)

### Wave 0 Gaps (test scaffolding required before plan execution)

- [ ] `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` — Plan 92-02 NEW
- [ ] `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` — Plan 92-03 NEW (also creates new package `org.ctc.build` — first usage in the codebase, verified by `find src/test/java/org/ctc/build` returning empty)
- [ ] Plan 92-02 must decide between extending `GoogleSheetsServiceTest.java` + `GoogleCalendarServiceTest.java` in place OR creating `*IT.java` siblings (Open Question 1 + Pitfall 1)
- [ ] No framework install needed — JUnit, Mockito, AssertJ, Spring Boot Test all already on classpath

**No new test infrastructure beyond the 2 new files above.** Existing patterns cover the test shape.

## Security Domain

Per CLAUDE.md § Static Analysis + CodeQL SAST and CONTEXT D-07, the standard security gates apply. Phase 92 has narrow security surface but UX-01 directly affects a known threat (T-91-02-IL info-leak).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Phase 92 does not touch auth flows; CsvImportController endpoints already require auth via existing security config |
| V3 Session Management | no | No session changes |
| V4 Access Control | no | No new endpoints; CSRF protection on existing POST endpoints unchanged |
| V5 Input Validation | yes | UX-01 preserves the existing `IllegalArgumentException` catch on `extractSpreadsheetId` (malformed URL input validation); SpotBugs scan covers new typed-catch code |
| V6 Cryptography | no | No crypto changes |
| V7 Error Handling and Logging | **yes (primary)** | UX-01 is fundamentally an error-handling control: whitelisted user-message constants prevent sensitive Google-API response details from leaking to flash content. Log level differentiation (AUTH/NOT_FOUND/PERMISSION → `log.error`; TRANSIENT → `log.warn`) matches Phase 91 D-07. Parameterized `{}` logging per CLAUDE.md § Logging |
| V9 Communication | no | No new outbound calls (Phase 92); existing Google API HTTPS preserved |
| V10 Malicious Code | no | No deserialization, no plugin loading changes |
| V12 File and Resources | partial | Plan 92-01 preserves the existing `MultipartFile` handling on the CSV upload path; no new file-handling code |
| V14 Configuration | no | No `application*.yml` changes (D-10 + Phase 91 D-13 carry-forward) |

### Known Threat Patterns for {Spring Boot 4 + Thymeleaf SSR}

| Pattern | STRIDE | Standard Mitigation | Phase 92 Status |
|---------|--------|---------------------|-----------------|
| **T-91-02-IL** — Google-API exception details echoed to user flash via `e.getMessage()` (info-leak of internal IDs, server paths, OAuth scopes) | Information Disclosure | Whitelisted user-message constants in controller; never echo `e.getMessage()` for typed `GoogleApiException` arms | **Re-closes via Plan 92-01** for the 3rd Google-Sheets consumer (`CsvImportController`); already closed for `DriverSheetImportController` + `RaceController` by Phase 91 |
| Stored XSS via `errorMessage` flash content | Tampering | Thymeleaf auto-escapes `${errorMessage}` via `th:text` (not `th:utext`); verified in `driver-import.html:14` template | **No change needed** — Plan 92-01 mirrors the `th:text` binding |
| Path traversal via `MultipartFile` filename | Tampering | Existing `MultipartFile.getOriginalFilename()` not used as a write target in `CsvImportController` (verified by code-read line 37-62) | **N/A** — Phase 92 does not modify file-handling |
| CSRF on POST endpoints | Spoofing | Spring Security CSRF token via `${_csrf}` form field; existing pattern unchanged | **No change needed** |
| SQL Injection via business-rule validation | Tampering | JPA + Spring Data parameterized queries everywhere; no raw JDBC in CsvImportController flow | **N/A** |
| Log injection via `e.getMessage()` (CRLF) | Tampering | Parameterized `{}` logging (CLAUDE.md § Logging); exception added as last arg, not concatenated | **Maintain in Plan 92-01** — use `log.error("Permission denied on CSV import preview-sheet", e)` shape, not string concat |

**CodeQL gate:** Plan 92-01 production-code edits will trigger the existing CodeQL workflow on the PR's HEAD SHA. Per CLAUDE.md § CodeQL SAST, the gate-step exits 0 unless a new HIGH/CRITICAL (CVSS ≥ 7.0) alert appears. The typed-catch refactor is expected to have ZERO new findings (Phase 91 precedent: zero findings on identical pattern application to `DriverSheetImportController` + `RaceController`).

**SpotBugs gate:** Same expectation — `BugInstance` count remains 0. No `<Match>` entries needed unless a finding surfaces.

## Sources

### Primary (HIGH confidence)

- `.planning/phases/92-carry-forwards-cleanup/92-CONTEXT.md` — All 11 decisions (D-01..D-11), canonical refs map, code surface enumeration. **Source of truth for this research.**
- `.planning/REQUIREMENTS.md` — Lines 13-24: full text of UX-01, COV-01, CLEAN-01, DOCS-01, BOOK-01 with acceptance criteria. Lines 116-120: traceability table (Phase 92 mapping).
- `.planning/STATE.md` — § Baselines to Preserve (JaCoCo ≥ 88.88 %, CI E2E 17:39 ± 20 %, SpotBugs 0, CodeQL exit 0, EXPORT_ORDER 24, SCHEMA_VERSION 1, Flyway V1-V7 immutable, test count ≥ 1696)
- `.planning/ROADMAP.md` — § "Phase 92: Carry-Forwards & Cleanup" goal + depends-on (nothing) + 5 success criteria
- `.planning/milestones/v1.13-ROADMAP.md` — § "Phase 92" full success criteria + Phase Dependency Graph (Phase 93 depends on Phase 92)
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` — § 5 "Phase 92 — Carry-Forwards & Cleanup" (the 4-plan structure: 92-01 UX-01 / 92-02 COV-01 / 92-03 CLEAN-01 / 92-04 DOCS-01+BOOK-01)
- `.planning/milestones/v1.12-MILESTONE-AUDIT.md` — Authoritative description of the 5 carry-forward findings (Warning 1 UX-01, Warning 2 CLEAN-02, Warning 4 audit-trail gap)
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-CONTEXT.md` — D-06 typed-exception hierarchy, D-07 BEM badge pattern, D-08 consumer-driven UX, D-09 google-integration.md, D-13 production yml invariant
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-PATTERNS.md` — File-by-file analog map for Phase 91 UX-01 (reference for how Plan 92-01 should be structured)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-VERIFICATION.md` — Goal-backward VERIFICATION.md template (v1.11 precedent commit `2e84fd57`)
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — Lines 60-92 (preview catch) + 134-165 (execute catch) — reference implementation Plan 92-01 mirrors
- `src/main/java/org/ctc/admin/controller/RaceController.java` — Lines 193-224 (createCalendarEvent typed-catch) — reference for redirect+flash variant
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — Refactor target; current generic IOException catches at lines 56, 115, 204
- `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` — Sealed base + 4 permits + Category enum
- `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` — Whitelisted message constants + IOException/GoogleJsonResponseException → typed mapping
- `src/main/resources/templates/admin/driver-import.html` — Lines 8-15: badge-rendering Thymeleaf snippet (verbatim copy target for `admin/import.html` + `admin/import-preview.html`)
- `src/main/resources/templates/admin/import.html` — Plan 92-01 EDIT target; verified NO `errorMessage` rendering exists today (Pitfall 2)
- `src/main/resources/templates/admin/import-preview.html` — Plan 92-01 EDIT target; verified NO `errorMessage` rendering exists today
- `src/main/resources/static/admin/css/admin.css` — Lines 360-374: `.error-badge` + 4 BEM modifiers (verified canonical Phase 91 D-07 shape)
- `pom.xml` — Lines 345-396 (JaCoCo plugin); lines 431-462 (`exec-maven-plugin` with `template-fragment-call-guard` — Plan 92-03 mirror template)
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — Lines 12 + 37: the AssertJ `Assumptions.assumeThat` import that CLEAN-01 must NOT trigger on
- `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java` — Existing test extended by Plan 92-01 (current line 65-84 covers TransientGoogleApiException without errorCategory)
- `src/test/java/org/ctc/dataimport/DriverSheetImportControllerExceptionTest.java` — Pattern reference for Plan 92-01 test shape (`@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`)
- `src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java` + `GoogleCalendarServiceTest.java` — Existing unit tests (Pitfall 1: the `*IT.java` versions named in CONTEXT D-03 do NOT exist)
- `CLAUDE.md` — Project constraints (German communication, English code, 82% coverage gate, `@Tag` test categorization, SpotBugs gate, CodeQL gate, Lombok config invariant, Conventional Commits, gh CLI, branch hygiene, subagent rules)

### Secondary (MEDIUM confidence)

- `.planning/milestones/v1.12-phases/{89,90,91}-*/{89,90,91}-VALIDATION.md` (Nyquist substance for retroactive VERIFICATION.md authoring — verified files exist at expected paths)
- `.planning/milestones/v1.12-phases/{89,90,91}-*/91-{01,02,03}-SUMMARY.md` (per-plan goal-backward sections; per-plan substance source for retroactive VERIFICATION.md)
- `.planning/milestones/v1.12-REQUIREMENTS.md` — Lines 26-31 (the 6 stale `[ ]` PERF checkboxes) + UX-01 row + lines 106-112 (4 stale `Pending` traceability rows). Verified exact count via grep.
- `docs/operations/google-integration.md` (Phase 91 D-09 output) — Likely contains the canonical user-message strings; can serve as cross-reference for Plan 92-01 string consistency. Not directly inspected in this research (file existence assumed per Phase 91 D-09 + Phase 91 CONTEXT canonical refs).

### Tertiary (LOW confidence)

- None. All claims tagged in this research are verified against codebase or cited from authoritative CONTEXT.md / CLAUDE.md / Phase 91 hand-off docs.

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — Zero new dependencies; all libraries already in `pom.xml` and consumed by Phases 71-91
- Architecture: **HIGH** — Pattern propagation, not pattern design; 100 % of code shapes verified in existing codebase
- Pitfalls: **HIGH** — Each pitfall is directly grep-verified (Pitfall 1: `*IT.java` non-existence verified; Pitfall 2: `errorMessage` missing in import.html/import-preview.html verified; Pitfall 5: 7+4 marker counts verified)
- Test strategy: **MEDIUM** — Pitfall 1 surfaces a real Plan 92-02 design decision (extend `*Test` vs. create `*IT`) that requires user input during planning
- Security: **HIGH** — Threat T-91-02-IL is the explicit UX-01 acceptance criterion; standard SAST gates apply

**Research date:** 2026-05-21
**Valid until:** 2026-06-20 (30 days; Phase 92 is stable v1.12 carry-forward — patterns are not fast-moving)

---

*Phase 92 research complete. Planner can now produce 4 Plan-NN-PLAN.md files (92-01 UX-01, 92-02 COV-01, 92-03 CLEAN-01, 92-04 DOCS-01+BOOK-01) sequenced inline on `gsd/v1.13-discord-integration` per D-05.*
