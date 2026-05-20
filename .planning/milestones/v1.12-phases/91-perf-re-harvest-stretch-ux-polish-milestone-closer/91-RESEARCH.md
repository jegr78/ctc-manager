# Phase 91: PERF Re-Harvest, Stretch UX Polish & Milestone Closer — Research

**Researched:** 2026-05-20
**Domain:** CI measurement harvesting (PERF-06) + Java-25 sealed-exception hierarchy on Google API I/O surface (UX-01) + milestone-closure docs (v1.12 closer)
**Confidence:** HIGH for PERF-06 mechanics (Phase 86 86-06-PLAN.md is a directly-reusable template) + HIGH for UX-01 controller/template touchpoints (read at source) + HIGH for closer-doc shapes (MILESTONES.md v1.11 entry + release/import runbooks read in full) + MEDIUM for the `GoogleJsonResponseException` mapping behaviour (Google API client lib is on classpath; concrete mapping rules confirmed via official `google-api-java-client` docs, but operator-error-message wording remains discretionary).

## Summary

Phase 91 ships three sequential plans on the active branch `gsd/v1.12-driver-import-and-test-perf` per [[inline-sequential-execution]] + [[wave-pause]]. All thirteen decisions in `91-CONTEXT.md` are locked; the planner's job is to translate them into atomic task lists, not to re-open them.

- **Plan 91-01 (PERF-06)** opens the v1.12 milestone Draft PR EARLY so the Draft-PR HEAD SHA = the SHA that `workflow_dispatch` runs target (D-17 trigger-equivalence). It then triggers 5 sequential `workflow_dispatch` ci.yml runs serialised via `gh run watch` (mandatory because `ci.yml on.concurrency.cancel-in-progress: true` would otherwise kill earlier runs), harvests the E2E-step wallclock from each, drops min+max, computes median-of-3, records the result in a new `docs/test-performance.md § PERF-06 Re-Harvest` section, swaps `STATE.md § Baselines to Preserve` from `23:00` to the new median per D-04. Phase 86 `86-06-PLAN.md` Tasks 1+2 are a near-verbatim template. Variance >20 % is documented as a footnote, not auto-retried (D-03). No-improvement outcome ships v1.12 anyway as a Phase-90-style OR-branch (D-10b).
- **Plan 91-02 (UX-01)** lands a sealed `GoogleApiException` hierarchy (4 typed permits: `Transient`, `Auth`, `NotFound`, `Permission`) in a new `org.ctc.dataimport.exception` package, refactors `GoogleSheetsService` + `GoogleCalendarService` to map `IOException` / `GeneralSecurityException` / `GoogleJsonResponseException` (HTTP 401/403/404/5xx) into typed throws, updates the two user-trigger controllers (`DriverSheetImportController` + `RaceController#createCalendarEvent`) to translate to flash-attribute `errorMessage` + new `errorCategory`, appends `.error-badge` + 4 BEM-modifier CSS classes to `admin/css/admin.css`, and creates `docs/operations/google-integration.md` shaped like `release-runbook.md`. Existing `IllegalArgumentException` URL-format guards remain UNCHANGED (D-06 — they are client-input-validation errors, not Google-API errors).
- **Plan 91-03 (Closer)** updates `MILESTONES.md` with a v1.12 entry mirroring the v1.11 shape (lines 3-44 of MILESTONES.md), updates `README.md § Test Performance` + Backup section pointers, finalises the v1.12 milestone PR body per the D-07b composite shape (REQ-ID table + narrative + numbers + CI run links — directly modelled on the v1.11 PR #122 body verified via `gh pr view 122`), and flips the Draft PR → Ready-for-review.

**Primary recommendation:** Treat `86-06-PLAN.md` as the canonical task-list template for Plan 91-01; treat the BackupArchiveException-with-Reason-enum + BackupImportException pattern as the **structural** reference for typed exceptions in this codebase (NOT the "sealed" idiom — there is no existing `sealed`/`permits` use in `src/main/java`; CONTEXT.md's "v1.10 Backup phases already used this idiom" claim is technically wrong — but `sealed` works fine on Java 25 + Spring Boot 4 and is the planner's default per D-06). Use the existing `.alert-error` flash-rendering pattern (layout.html line 86 + driver-import.html line 8) as the surface that the badge `<span>` inserts next to.

## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01: UX-01 lands IN v1.12 as Plan 91-02, after PERF-06.** Stretch wording resolved IN. Three sequential plans (PERF-06 → UX-01 → Closer); descope is no longer a branch.

**D-02: Three plans, sequential inline on `gsd/v1.12-driver-import-and-test-perf`.** Per [[inline-sequential-execution]] + [[wave-pause]]. User-feedback pause after each plan merge.
- Plan 91-01 = PERF-06 CI Re-Harvest + Draft-PR open.
- Plan 91-02 = UX-01 typed-exception hierarchy + flash UX + google-integration.md.
- Plan 91-03 = Milestone Closer.

**D-03: Variance >20 % handling — accept n=5 + document as "observed variance" footnote, do NOT auto-trigger a 2nd 5-run block.** Phase 91 has no hard wallclock gate (D-10b below), so outlier-filtering does not justify 90+ extra CI minutes.

**D-10b: "No measurable reduction" outcome — document as a Phase-90-style OR-branch and ship v1.12 anyway.** The "stretch ≥30 % to ≤16:00" wording in Success Criterion #2 is observational, not a blocker.

**D-04: Baseline-swap shape — replace 23:00 in `STATE.md § Baselines to Preserve` with the new median.** Historical 23:00 preserved in `PROJECT.md § Key Decisions` (new trend-row) + `docs/test-performance.md § PERF-06 Re-Harvest` ("vs. 23:00 v1.11 baseline" reference).

**D-05: Hybrid Draft-PR shape — open early as Draft, re-harvest on PR-branch, finalize at Closer.** PR opens immediately AFTER `91-01-PLAN.md` is committed and BEFORE the 5 workflow_dispatch runs. Rolling PR-body via `gh pr edit --body-file` at 91-02 + finalised at 91-03. Draft → Ready-for-review flip at end of Plan 91-03.

**D-06: Hierarchical sealed-base exception family.**
- `org.ctc.dataimport.exception.GoogleApiException` — abstract `sealed` base, extends `IOException`.
- `TransientGoogleApiException permits` only — network/5xx/rate-limit.
- `AuthGoogleApiException` — expired/invalid OAuth token (401 + 403-auth).
- `NotFoundGoogleApiException` — missing sheet ID / calendar ID (404).
- `PermissionGoogleApiException` — 403 access-denied (token valid, resource not shared).
- Mapper helper next to the exception classes (`GoogleApiExceptionMapper` static method or equivalent — planner picks).

**D-07: Error rendering — flash-attribute pattern `errorMessage` + `errorCategory`.** `errorCategory` is one of `TRANSIENT`, `AUTH`, `NOT_FOUND`, `PERMISSION`. Templates render `<span class="error-badge error-badge--{lowercase-kebab}">…</span>` via CSS in `admin/css/admin.css` (CLAUDE.md § CSS Guidelines — no inline styles).

**D-08: GoogleCalendarService UX scope — same typed-hierarchy throws, consumer-driven UX surface.** User-trigger paths get the flash UX; background-trigger paths log at WARN + use existing graceful-fallback. Plan 91-02 must audit calendar-sync call sites and apply UX only where appropriate.

**D-09: `docs/operations/google-integration.md` — operations-runbook shape** mirroring `release-runbook.md` + `import-runbook.md`. Sections: `# Setup` / `## Error Categories` (4-row table) / `## Troubleshooting`.

**D-10: Standard gates apply, no tightening, no loosening.** JaCoCo line coverage ≥ 88.88 %, SpotBugs `BugInstance = 0`, CodeQL gate-step exit 0, `EXPORT_ORDER = 24`, `SCHEMA_VERSION = 1`, Flyway V1-V7 immutable.

**D-11: Nyquist VALIDATION strict — Phase 91 self-validate + retroactive check across all 4 v1.12 phases via `/gsd-validate-phase` before `/gsd-complete-milestone`.** Strict over v1.11's Option-A inline pattern.

**D-12: 3-seed Failsafe verification on UX-01 ITs after Plan 91-02 refactor.** Command pattern: `./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}`.

**D-07b: Composite PR body — Summary table + Narrative bullets + CI-Run links + Coverage/SpotBugs/CodeQL summary.** Finalised in Plan 91-03. 15 REQ rows (CLEAN-01..03, DOCS-01, DRIV-01..02, REL-01..02, PERF-01..06, UX-01). Per-phase narrative bullets (88/89/90/91). Bottom section = numbers.

**D-13: Production code touched only in Plan 91-02 (UX-01).** Plans 91-01 + 91-03 are pure docs + CI-harvest + PR-mechanics — `src/main/java/**` is git-clean across both. Plan 91-02 touches `src/main/java/org/ctc/dataimport/exception/` (new package, 4 classes + 1 mapper), `GoogleSheetsService.java`, `GoogleCalendarService.java`, `DriverSheetImportController.java`, `RaceController.java` (the calendar trigger only), Thymeleaf templates, and `admin/css/admin.css`. NO `application*.yml` changes, NO Flyway migration.

### Claude's Discretion

- Exact wording of `docs/test-performance.md § PERF-06 Re-Harvest` paragraph framing + table layout.
- Exact wording of `docs/operations/google-integration.md` prose (D-09 locks the shape; planner picks language).
- Exact `GoogleApiException` package location — `org.ctc.dataimport.exception` is default; `org.ctc.exception.google` acceptable.
- Exact mapper utility class name and shape (static method vs. builder vs. method-on-service-base).
- Exact CSS class names for the 4 category badges — `error-badge` + `error-badge--transient/auth/not-found/permission` is the default BEM-ish shape.
- Whether `errorCategory` flash-attribute is a `String` or an `enum` (Thymeleaf renders both; String is simpler).
- Whether `sealed permits` syntax (Java 17+ idiom) or classic abstract + final subclasses — `sealed` is more idiomatic on Java 25 (default per D-06).
- Fingerprint-sidecar retention shape for the 5 CI runs — `.test-perf-logs/91-01-ci-run-{1..5}/` or skip.
- `MILESTONES.md` v1.12 entry wording (shape locked by the v1.11 entry).

### Deferred Ideas (OUT OF SCOPE)

- Test-module-split extraction (`ctc-manager-tests` Maven artifact) — Phase 90 PERF-05 D-05 deferred; v1.13 re-evaluates against PERF-06 CI median surfaced here.
- Secondary cluster consolidation (backup-exception 12-class, admin-security 12-class, AdminWorkflowE2E 7-class) — Phase 90 D-01 conservative; v1.13 re-evaluates.
- Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class `db.migration.**` cluster.
- Production `application*.yml` changes (D-14 Phase 89 / D-09 Phase 90 binding).
- Calendar-sync UX surface RE-PLATFORMING beyond the audited consumers (D-08 keeps background-trigger paths graceful).
- CI-side Testcontainers reuse enabling.
- Any new SpotBugs `<Match>` entry, unless the new exception classes surface `EI_EXPOSE_REP*` or `SE_BAD_FIELD` — in which case the response is a targeted `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST pattern.
- `@ControllerAdvice` typed-exception handler extraction (UX-01 catches at the controller method level for category-specific UX; a v1.13 cleanup could extract if a third controller starts needing the pattern).
- Retry-with-backoff for `TransientGoogleApiException` (D-07 surfaces "retry" wording but does NOT auto-retry).
- OAuth re-link UI flow (`AuthGoogleApiException` documents the operator action; in-UI wizard is future).
- Sheet-ID lookup helper (verifier endpoint).

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PERF-06 | Post-implementation CI 5-run median re-harvested per D-17 trigger-equivalence (5 `workflow_dispatch` runs on the v1.12 milestone PR-branch HEAD SHA, drop min+max, median of 3); new baseline replaces 23:00 in `docs/test-performance.md` and in `STATE.md § Baselines to Preserve`; variance within established 20 % tolerance. | § "Plan 91-01 PERF-06 Mechanics" — `gh workflow run` + `gh run watch` pattern from Phase 86 86-06-PLAN.md (concrete command shape, run-ID capture, median-of-3 computation, variance footnote per D-03). |
| UX-01 | `DriverSheetImportService.preview()` + `execute()` + calendar-sync paths distinguish transient (network/5xx) vs. permanent (invalid sheet ID, auth-token expired, permissions); user-facing form errors include a category badge backed by a small typed-exception hierarchy; documented in `docs/operations/google-integration.md`. | § "Plan 91-02 UX-01 Mechanics" — sealed-hierarchy shape (D-06), mapping rules for `IOException` / `GeneralSecurityException` / `GoogleJsonResponseException` HTTP 401/403/404/5xx, flash-attribute pattern (D-07), `RaceCalendarService` consumer audit (D-08), `google-integration.md` shape (D-09). |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Trigger 5 `workflow_dispatch` CI runs (PERF-06) | Operator/Tooling (gh CLI) | — | The runs themselves execute on GitHub-hosted runners; Plan 91-01 only CONSUMES `gh workflow run` + `gh run watch`. `ci.yml` is unchanged. |
| Compute median + record in docs | Documentation (`docs/test-performance.md`) | — | Pure docs touch in `docs/test-performance.md` + `STATE.md` + `PROJECT.md`. |
| Typed exception hierarchy (UX-01 carrier) | Service layer (`org.ctc.dataimport.exception`) | — | Exception classes are domain-adjacent; sealed-base extends `IOException` so existing `throws IOException` signatures stay backward-compatible. |
| Map `IOException`/`GoogleJsonResponseException` → typed subtype | Service layer (Google client wrappers) | — | The mapping helper lives next to the exception classes; `GoogleSheetsService` + `GoogleCalendarService` synchronized client-builder methods catch + rethrow via the helper. |
| Translate typed exception → flash UX | Controller layer (`admin.controller.*`) | — | Per CLAUDE.md § Controller & DTO Patterns: controllers own flash-attribute population + redirect. The flash key `errorCategory` lands here, NOT in the service layer (services know the category but not the user-visible message). |
| Render badge | Thymeleaf templates + `admin.css` | — | Templates already render `${errorMessage}` from flash (layout.html:86 + driver-import.html:8); Plan 91-02 inserts a `<span class="error-badge error-badge--{category}">` next to the existing message element. CSS classes are BEM-shaped modifiers on the canonical `.error-badge` block. |
| Document operator response | `docs/operations/google-integration.md` | — | New runbook-shaped doc, mirrors `release-runbook.md` + `import-runbook.md`. |
| v1.12 milestone entry | `.planning/MILESTONES.md` | — | Plan 91-03 inserts the v1.12 entry at the top of MILESTONES.md (append-at-top convention per the v1.11/v1.10/v1.9/v1.8 entries). |
| v1.12 PR body composite | `gh pr edit --body-file` (GitHub remote) | — | Rolling edit lands on the PR opened by Plan 91-01; finalised by Plan 91-03 per D-07b. |

## Project Constraints (from CLAUDE.md)

The planner must produce plans that comply with these CLAUDE.md directives (treated with same authority as locked decisions):

- **Communication German / Documentation+Code+UI English.** All new doc prose and the four new exception messages MUST be English.
- **Test coverage ≥ 82 % line (gate); v1.11 baseline 88.88 %.** Plan 91-02 must add tests that hold this (D-10).
- **Flyway V1-V7 immutable.** Phase 91 ships NO new migration.
- **OSIV remains enabled.** Plan 91-02 controllers do not need lazy-init workarounds.
- **Backward compatibility on URLs/endpoints.** Plan 91-02 does not change endpoint paths.
- **Sequential PR-merges with rebase after squash-merge.** Plan 91-01 / 91-02 / 91-03 all merge into the same v1.12 milestone PR via squash; no separate per-plan PRs are opened.
- **No inline styles on buttons / always use CSS classes from `admin.css`.** Plan 91-02 badges use the new `.error-badge` + 4 BEM modifiers — verified that no existing `.error-badge*` class exists in `admin/css/admin.css` (grep returned 0 hits).
- **DTOs instead of entities in controllers (no Mass Assignment).** Plan 91-02 changes controller catch blocks only; no new form binding.
- **`/gsd-` skill invocation prefix (DOCS-01 from Phase 88).** Plan 91-03 doc-edits use dash form only.
- **Branch protection / Subagent rules.** Active branch is `gsd/v1.12-driver-import-and-test-perf`; no branch switching, no `git stash`, no `git checkout`, no `git reset` in any plan task. Tasks run inline per [[inline-sequential-execution]] (no subagents spawned).
- **`./mvnw clean test-compile` first when diagnosing compile errors** (per [[clean-maven-build-authority]]). After Plan 91-02 refactor, run `./mvnw clean test-compile` BEFORE claiming completion.
- **Static Analysis SpotBugs.** New typed exceptions should not trigger SpotBugs `EI_EXPOSE_REP*` or `SE_BAD_FIELD` patterns (they have no mutable collection getters and no implicit serialisation); if any finding lands, response is `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST pattern (NOT a new `<Match>` entry).
- **CodeQL gate-step exit 0 on PR HEAD SHA.** No new sources of `path-injection` / `SSRF` / `BCrypt-N/A` patterns expected; if any new alert with `security-severity ≥ 7.0` lands, the 3-layer FP suppression invariant applies.
- **No local git tags.** Plan 91-03 does NOT run `git tag` locally — the CI release workflow handles tagging post-merge per [[no-local-git-tags]].
- **`@Tag("integration")` on new ITs** (if any). UX-01 may need a new IT for the controller catch-translate behaviour; CONTEXT D-12 estimates the touch radius covers existing `*DriverSheet*` + `*Calendar*` ITs without requiring new IT classes — planner confirms during task authoring.
- **Use `Write` tool for file creation, never bash heredoc.** Applies to plans editing `docs/operations/google-integration.md`, `MILESTONES.md`, and the PR-body file.

## Standard Stack

### Core (consumed, not added)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `google-api-client` | 2.9.0 (pinned in `pom.xml`) | Provides `GoogleJsonResponseException` (the HTTP-aware Google API error type) and `GoogleNetHttpTransport` | Already on classpath via `GoogleSheetsService` + `GoogleCalendarService`. UX-01 D-06 mapper inspects `GoogleJsonResponseException.getStatusCode()` / `.getDetails()` to choose `AuthGoogleApiException` (401, 403-auth) vs. `PermissionGoogleApiException` (403-perm) vs. `NotFoundGoogleApiException` (404) vs. `TransientGoogleApiException` (5xx). |
| `google-api-services-sheets` | `v4-rev20250106-2.0.0` | Sheets v4 API client | Already on classpath. |
| `google-api-services-calendar` | `v3-rev20250115-2.0.0` | Calendar v3 API client | Already on classpath. |
| Spring `RedirectAttributes` | Spring Boot 4.0.6 | Flash-attribute population | Already in use across controllers (search `redirectAttributes.addFlashAttribute` returns 30+ hits). D-07 extends with a new `errorCategory` key — no infrastructure change. |
| Java 25 `sealed` / `permits` syntax | Java 25 (Eclipse Temurin) | Sealed-base exception hierarchy | Native language feature; Spring Boot 4 + Hibernate 7 work fine with sealed classes. NO existing `sealed` use in `src/main/java/org/ctc/**` (grep returned 0 hits), so this is a fresh-introduction pattern but uncontroversial on Java 25. |
| `gh` CLI | latest (operator-installed) | `gh workflow run`, `gh run list`, `gh run view`, `gh run watch`, `gh pr create --draft`, `gh pr edit --body-file`, `gh pr ready` | Already used across the project (release workflow, PR mechanics, Phase 86 PERF-05 harvest). Plan 91-01 + 91-03 consume directly. |

### Supporting (already in place — no new additions)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `lombok` | 1.18.46 (managed) | `@Slf4j @RequiredArgsConstructor` on the existing services | Service-class annotations stay; the new exception classes are NOT Lombok (constructors are explicit `super(msg, cause)`, matching `BackupImportException` + `BackupArchiveException` shape). |
| `Slf4j` | via Lombok | Logging at WARN/ERROR in services and at WARN in `RaceCalendarService` background-trigger paths (D-08) | Background-trigger paths log + fallback; user-trigger paths flash UI per D-07. |
| `AssertJ` | Spring Boot managed | Unit + IT assertions | Plan 91-02 unit tests for the mapper helper + service-level catch blocks use `assertThatThrownBy(...).isInstanceOf(AuthGoogleApiException.class)`. |
| Thymeleaf 3.1.5 (pinned) | per Phase 71 pinning | Template rendering | Plan 91-02 inserts `<span th:if="${errorCategory}" th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|" class="error-badge" th:text="${errorCategory}">` (or equivalent) next to the existing `${errorMessage}` block. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Sealed exception hierarchy | Flat single class + Category enum | CONTEXT D-06 REJECTED: loses compile-time catch typing; harder to reason about retry-vs-don't-retry policy at the service-layer. The `BackupArchiveException(Reason reason, …)` enum-pattern that already exists in the codebase is the structural alternative — it's the simpler shape — but D-06 picks sealed for compile-time exhaustiveness on `try { … } catch (AuthGoogleApiException …) catch (TransientGoogleApiException …)` sites. |
| Sealed extends `IOException` | Sealed extends `RuntimeException` | CONTEXT D-06 picks `extends IOException` so existing `throws IOException` method signatures on `GoogleSheetsService` + `GoogleCalendarService` work without breaking changes. RuntimeException would force every caller to drop `throws IOException`. |
| Mapper as static helper method | Builder / method-on-service-base / @ControllerAdvice | CONTEXT Discretion gives planner choice. Recommendation: static helper `GoogleApiExceptionMapper.from(IOException)` lives next to the exception classes — simplest, matches `BackupArchiveException` static-throw idiom, no Spring bean ceremony. `@ControllerAdvice` extraction is explicitly DEFERRED per CONTEXT § Deferred Ideas ("if a third controller starts needing the same pattern"). |
| `errorCategory` as Java enum | `errorCategory` as String | CONTEXT Discretion gives planner choice. Recommendation: String (one of "TRANSIENT", "AUTH", "NOT_FOUND", "PERMISSION") — Thymeleaf renders String more cleanly into `class="error-badge--${#strings.toLowerCase(errorCategory)}"`; the enum exists at the Java side for compile-time safety but its `name()` is what lands in the flash. |
| `GoogleApiException.Category` nested enum | Standalone enum | Recommendation: nested enum `public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }` with `public abstract Category category();` on the sealed base. Each subtype overrides — compile-time guarantees the 4 categories are exhaustively mapped. |
| New IT classes for UX-01 | Extend existing `DriverSheetImportControllerExceptionTest` + add 1 controller IT for `RaceController#createCalendarEvent` | Plan 91-02 should extend existing exception-path tests rather than create a new test class per category — the existing classes already cover the controller catch shape. |

**Installation:** No new dependencies. All required libraries are already on classpath (`google-api-client` 2.9.0 ships `GoogleJsonResponseException` natively).

**Version verification:** No new packages to verify. `google-api-client` 2.9.0 was last reviewed in `pom.xml` (Renovate-managed under the "Google API clients" group name per Phase 84).

## Package Legitimacy Audit

Not applicable — Phase 91 installs no new external packages. All Plan 91-02 imports come from libraries already on classpath:
- `com.google.api.client.googleapis.json.GoogleJsonResponseException` (from `google-api-client` 2.9.0)
- `java.io.IOException` / `java.security.GeneralSecurityException` (JDK)
- `org.springframework.web.servlet.mvc.support.RedirectAttributes` (Spring Boot 4.0.6)

No `npm`/`pip`/`cargo` involvement. The `gh` CLI consumed by Plan 91-01 + 91-03 is an operator-installed tool (not project dependency).

## Architecture Patterns

### Data Flow — Plan 91-01 PERF-06 Re-Harvest

```
[Operator] --(gh workflow run ci.yml --ref gsd/v1.12-driver-import-and-test-perf)--> GitHub Actions
                                                                                       |
                                                                                       v
                                                                                 (5x sequential)
                                                                                       |
                                                                                       v
[Operator] --(gh run watch <RUN_ID> --exit-status)--> blocks until run completes
                                                                                       |
                                                                                       v
[Operator] --(gh run view <RUN_ID> --log | grep "Run E2E Tests" step duration)----> 5 wallclocks captured
                                                                                       |
                                                                                       v
                                                                       [drop min + max, median of middle 3]
                                                                                       |
                                                                                       v
              [docs/test-performance.md § PERF-06 Re-Harvest] <-- write median + variance % + 5 run-id links
                                                                                       |
                                                                                       v
                              [STATE.md § Baselines to Preserve] <-- swap 23:00 → new median (D-04)
                                                                                       |
                                                                                       v
                                  [PROJECT.md § Key Decisions] <-- new trend row (D-04)
```

### Data Flow — Plan 91-02 UX-01

```
[GoogleSheetsService / GoogleCalendarService method body]
      try { client.execute() }
      catch (GoogleJsonResponseException e) { throw GoogleApiExceptionMapper.from(e); }
      catch (IOException e) { throw GoogleApiExceptionMapper.from(e); }
      catch (GeneralSecurityException e) { throw new AuthGoogleApiException("...", e); }
                                                          |
                                                          v   (typed sealed subtype propagates)
                                                          |
                                                          v
[DriverSheetImportController#preview / #execute  |  RaceController#createCalendarEvent]
      try { service.call(); }
      catch (AuthGoogleApiException e) {
          redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
          redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
          return "redirect:/admin/...";
      }
      catch (NotFoundGoogleApiException e) { ... "Sheet not found — check ID" ... "NOT_FOUND" ... }
      catch (PermissionGoogleApiException e) { ... "Access denied — share the sheet with the service account" ... "PERMISSION" ... }
      catch (TransientGoogleApiException e) { ... "Connection problem — retry" ... "TRANSIENT" ... }
                                                          |
                                                          v
[Thymeleaf template (driver-import.html, race-detail.html, etc.)]
      <div th:if="${errorMessage}" class="alert alert-error mb-md">
          <p>
              <span th:if="${errorCategory}"
                    th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
                    class="error-badge"
                    th:text="${errorCategory}"></span>
              <span th:text="${errorMessage}"></span>
          </p>
      </div>
                                                          |
                                                          v
[admin/css/admin.css — new BEM modifier rules]
      .error-badge { … common shape: small pill, uppercase, padded … }
      .error-badge--transient  { background: <warning-bg> … }
      .error-badge--auth       { background: <danger-bg>  … }
      .error-badge--not-found  { background: <info-bg>    … }
      .error-badge--permission { background: <danger-bg>  … }
```

### Recommended Project Structure

```
src/main/java/org/ctc/dataimport/
├── exception/                                   # NEW package
│   ├── GoogleApiException.java                  # sealed abstract base, extends IOException, Category enum nested
│   ├── TransientGoogleApiException.java         # final permits target
│   ├── AuthGoogleApiException.java              # final
│   ├── NotFoundGoogleApiException.java          # final
│   ├── PermissionGoogleApiException.java        # final
│   └── GoogleApiExceptionMapper.java            # static helper (or rename per planner)
├── GoogleSheetsService.java                     # CHANGED — throws typed subtypes
├── GoogleCalendarService.java                   # CHANGED — throws typed subtypes
└── DriverSheetImportService.java                # may change — `preview()` + `execute()` re-throw or propagate

src/main/java/org/ctc/admin/controller/
├── DriverSheetImportController.java             # CHANGED — catch typed subtypes, set errorCategory
└── RaceController.java                          # CHANGED at `#createCalendarEvent` method only

src/main/java/org/ctc/domain/service/
└── RaceCalendarService.java                     # propagates typed subtypes (or maps if needed); no UX here

src/main/resources/templates/admin/
├── layout.html                                  # CHANGED — add badge `<span>` adjacent to ${errorMessage}
├── driver-import.html                           # CHANGED — same badge insertion
└── race-detail.html                             # CHANGED if calendar-trigger UX surfaces inline (currently flash via redirect goes through layout.html)

src/main/resources/static/admin/css/
└── admin.css                                    # APPENDED — .error-badge + 4 BEM modifiers (after existing .badge rules at line 346)

docs/operations/
└── google-integration.md                        # NEW — Setup / Error Categories / Troubleshooting

src/test/java/org/ctc/dataimport/
├── exception/                                   # NEW
│   ├── GoogleApiExceptionMapperTest.java        # unit tests for the mapper (one per category)
│   └── GoogleApiExceptionTest.java              # optional: sealed-hierarchy invariants
├── GoogleSheetsServiceTest.java                 # CHANGED — assert typed throws
└── GoogleCalendarServiceTest.java               # CHANGED — assert typed throws

src/test/java/org/ctc/admin/controller/
└── DriverSheetImportControllerExceptionTest.java  # CHANGED — assert flash errorCategory keys land
```

### Pattern 1: Sealed-base exception with category enum

**What:** Sealed-base `GoogleApiException` extends `IOException`, permits 4 final subtypes; nested `Category` enum gives compile-time exhaustiveness.

**When to use:** UX-01 — every Google API I/O failure that is NOT a client-side input validation error.

**Example shape:**

```java
// Source: D-06 + existing BackupArchiveException structural template at src/main/java/org/ctc/backup/exception/BackupArchiveException.java
package org.ctc.dataimport.exception;

import java.io.IOException;

public abstract sealed class GoogleApiException extends IOException
    permits TransientGoogleApiException, AuthGoogleApiException,
            NotFoundGoogleApiException, PermissionGoogleApiException {

    public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }

    protected GoogleApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract Category category();
}

// final subtype example:
package org.ctc.dataimport.exception;

public final class AuthGoogleApiException extends GoogleApiException {
    public AuthGoogleApiException(String message, Throwable cause) { super(message, cause); }
    @Override public Category category() { return Category.AUTH; }
}
```

### Pattern 2: GoogleJsonResponseException → typed-subtype mapper

**What:** A static helper that inspects `GoogleJsonResponseException.getStatusCode()` and (where needed) `.getDetails().getErrors().get(0).getReason()` to pick the right typed subtype.

**Mapping rules (CITED from official `google-api-java-client` docs at https://googleapis.dev/java/google-api-client/latest/com/google/api/client/googleapis/json/GoogleJsonResponseException.html):**

| HTTP status | `.getDetails().getErrors().get(0).getReason()` (or null) | Maps to |
|-------------|---------------------------------------------------------|---------|
| 401 | any | `AuthGoogleApiException` |
| 403 | `"authError"`, `"invalidCredentials"`, `"unauthorized"` | `AuthGoogleApiException` |
| 403 | `"forbidden"`, `"insufficientPermissions"`, anything else / null | `PermissionGoogleApiException` |
| 404 | any | `NotFoundGoogleApiException` |
| 408, 429, 500, 502, 503, 504 | any | `TransientGoogleApiException` |
| Other | any | `TransientGoogleApiException` (default — be lenient on rare codes) |

For `IOException` that is NOT a `GoogleJsonResponseException` (network timeout, DNS failure, socket reset): map to `TransientGoogleApiException`.

For `GeneralSecurityException` (caught by the existing `getSheetsClient` / `getCalendarClient` synchronized builder at `GoogleSheetsService.java:176` and `GoogleCalendarService.java:114`): map to `AuthGoogleApiException` (credentials are broken / unreadable).

Existing `IllegalArgumentException` URL-format guards at `GoogleSheetsService.java:138` + `:154`: **do NOT re-classify** — these are client-side input-validation errors, not Google API errors. The controllers already handle them in the existing `IllegalArgumentException` catch (DriverSheetImportController.java:61); Plan 91-02 leaves that catch in place.

Existing `IllegalStateException` for "credentials not configured" at `GoogleSheetsService.java:160` + `GoogleCalendarService.java:98`: keep as `IllegalStateException` (not a Google API error — it's an availability-check failure). DriverSheetImportController.java:61 already catches `IllegalStateException`; leave that branch in place.

```java
// Source: D-06 + Google API client lib docs
package org.ctc.dataimport.exception;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class GoogleApiExceptionMapper {

    private GoogleApiExceptionMapper() {}

    public static GoogleApiException from(IOException e) {
        if (e instanceof GoogleJsonResponseException gjre) {
            int status = gjre.getStatusCode();
            return switch (status) {
                case 401 -> new AuthGoogleApiException(authMessage(gjre), gjre);
                case 403 -> isAuthReason(gjre)
                        ? new AuthGoogleApiException(authMessage(gjre), gjre)
                        : new PermissionGoogleApiException(permissionMessage(gjre), gjre);
                case 404 -> new NotFoundGoogleApiException(notFoundMessage(gjre), gjre);
                case 408, 429, 500, 502, 503, 504 -> new TransientGoogleApiException(transientMessage(gjre), gjre);
                default -> new TransientGoogleApiException(transientMessage(gjre), gjre);
            };
        }
        // Non-JSON IOException: network / socket / timeout
        return new TransientGoogleApiException("Network problem talking to Google: " + e.getMessage(), e);
    }

    public static AuthGoogleApiException from(GeneralSecurityException e) {
        return new AuthGoogleApiException(
            "Could not authenticate with Google (credentials unreadable): " + e.getMessage(), e);
    }

    private static boolean isAuthReason(GoogleJsonResponseException e) { /* inspect getDetails() */ }
    // ... message-builder helpers
}
```

### Pattern 3: Controller catch-translate to flash UX

**What:** Each user-trigger controller method that wraps a Google service call replaces its existing `catch (IOException ...)` block with 4 typed catches (most-specific first) that set both `errorMessage` and `errorCategory` flash attributes.

**Touchpoints (verified via grep):**

| Controller | Method | Current state | Plan 91-02 change |
|------------|--------|---------------|-------------------|
| `DriverSheetImportController#preview` (line 39-67) | catches `IOException` (line 56-60) → adds `model.errorMessage` | 1 IOException catch | Split into 4 typed catches (most-specific first); add `errorCategory` to model |
| `DriverSheetImportController#execute` (line 69-114) | catches `IllegalStateException | DataAccessException` (line 109-112); IO is wrapped in `IllegalStateException` by the service (line 108 of DriverSheetImportService) | Indirect IO | Plan must decide: (a) unwrap `IllegalStateException.getCause()` if it's a `GoogleApiException` and rethrow OR (b) refactor `DriverSheetImportService.execute()` line 108 to NOT wrap and let typed exceptions propagate. Recommendation: refactor `execute()` to declare `throws IOException` (it can — `@Transactional` allows checked declarations) and propagate the typed subtype directly. |
| `RaceController#createCalendarEvent` (line 187-196) | catches `IOException | IllegalStateException` (line 192-194) → flash with prefix `"Calendar: " + msg` | 1 combined catch | Split into 4 typed catches for `IOException` subtypes; keep `IllegalStateException` for availability/duration-config errors (NOT a Google API error). |

**Background-trigger consumers (D-08 — log + fallback, NO flash UX):**

| Caller | What it does | Plan 91-02 stance |
|--------|--------------|-------------------|
| `RaceService` (line 32) — references `raceCalendarService` | Unverified at research time. Planner audits in Plan 91-02 Task 1. | If RaceService calls `createOrUpdateCalendarEvent` from a non-user-triggered context (e.g., a scheduled task or post-save hook), log at WARN and swallow; no UX surface change. |
| `RaceCalendarService#createOrUpdateCalendarEvent` (line 28) — `@Transactional`, throws `IOException` | Currently throws `IOException`; `RaceController#createCalendarEvent` catches it. | Plan 91-02 changes the throws clause to `throws GoogleApiException` (or leaves `IOException` since GoogleApiException extends IOException — backward-compatible signature). Caller flashes UX. |

**Audit of `RaceCalendarService` consumers** (grep against `src/main/java/`, verified):

| Caller | File:line | User-trigger? | UX surface |
|--------|-----------|---------------|------------|
| `RaceController#createCalendarEvent` | `RaceController.java:187` | YES — `@PostMapping("/{id}/create-calendar-event")` from a form button in `race-detail.html:73` | Apply D-07 flash UX |
| `RaceService` (constructor field at `:32`) | `RaceService.java:32` | UNKNOWN — Plan 91-02 must read the file and decide | Planner audits |

The `RaceService` audit is the load-bearing task in Plan 91-02 Wave-2 Task 1 — without it the planner can't decide whether to apply flash UX at one or two controllers.

### Anti-Patterns to Avoid

- **Catching `GoogleApiException` at the service layer.** Services THROW the typed subtype; they do not CATCH it. Controllers (at the user-trigger boundary) catch + translate. This mirrors CLAUDE.md § Controller & DTO Patterns: "Controllers are only responsible for HTTP handling".
- **Adding the category badge as an inline style.** CLAUDE.md § CSS Guidelines is explicit: CSS classes from `admin.css`, no inline styles. The new badge classes go in `admin/css/admin.css` after the existing `.badge` rule (line 346).
- **Wrapping `IllegalArgumentException` URL-format errors as `GoogleApiException`.** Those are client input-validation errors. D-06 explicitly preserves the existing behaviour.
- **Adding the typed catches to a global `@ControllerAdvice`.** Phase 91 catches at the controller-method level for category-specific UX. `@ControllerAdvice` extraction is DEFERRED.
- **Triggering all 5 PERF-06 runs in parallel.** `ci.yml on.concurrency.cancel-in-progress: true` would kill earlier runs. Sequential `gh run watch <RUN_ID> --exit-status` between triggers is mandatory.
- **Recording the median locally and merging without CI evidence.** D-11 (CI is source of truth) — the 5 run IDs MUST land in the PR body and in `docs/test-performance.md`.
- **Auto-retrying a 2nd 5-run block on variance >20 %.** D-03 explicitly rejects this.
- **Tagging v1.12 locally with `git tag`.** The CI release workflow handles tagging post-merge per [[no-local-git-tags]].

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP status → typed exception mapping | A custom HTTP client wrapper | Inspect `GoogleJsonResponseException.getStatusCode()` directly in the mapper | The Google API client already raises `GoogleJsonResponseException` with the status code and `.getDetails()` reason populated. A wrapper would parse JSON twice. |
| Retry-with-backoff for transient failures | Custom retry loop in the service | NOTHING — D-07 surfaces the category to the user with a "retry" message but does not auto-retry. A future phase can add a wrapper if user feedback demands it. | Auto-retry would change the semantics of the user's POST and risk double-imports. |
| CI run timing extraction | A custom log parser script | `gh run view <id> --log 2>&1 \| grep "Run E2E Tests"` and read the duration from the GitHub Actions step-level field, OR use `gh run view --json jobs` and parse step durations from the JSON | Phase 86 86-06-PLAN.md Task 1 Step 3 already demonstrates the pattern; Plan 91-01 reuses verbatim. |
| Median-of-3 computation | A custom sort+slice utility | Inline bash: `printf '%s\n' "$v1" "$v2" "$v3" "$v4" "$v5" \| sort -n \| sed -n '2,4p'` → take middle line | n=5 with min/max dropped is 3 values; mean=median by definition; trivial inline. |
| Sealed exception infrastructure | A new annotation processor | Native Java 25 `sealed`/`permits` | No tooling needed; `javac` enforces the closed hierarchy. |

**Key insight:** Phase 91 is heavy on **consume-don't-build**. The PERF-06 task list is operator-driven gh CLI consumption; the UX-01 hierarchy is a small native-Java sealed-class addition with a tiny mapper helper. No new libraries, no new infrastructure, no new patterns this codebase hasn't seen before (BackupImportException/BackupArchiveException are the existing typed-exception precedent).

## Plan 91-01 PERF-06 Mechanics

### gh CLI sequential workflow_dispatch + run-watch pattern

Phase 86 86-06-PLAN.md Task 1 Step 2 + Step 3 are the canonical template. Below is the version adapted for the v1.12 branch:

```bash
# Pre-condition: 91-01-PLAN.md is committed AND the v1.12 milestone Draft PR is open
BRANCH=gsd/v1.12-driver-import-and-test-perf

# Step 1: Open the Draft PR (D-05 — opens BEFORE the 5 runs so HEAD SHA is canonical)
gh pr create --draft \
    --base master \
    --head "$BRANCH" \
    --title "feat(v1.12): driver-import gap-closure & test performance round 2" \
    --body "v1.12 milestone PR (work in progress) — PERF-06 measurement landing in this commit. Final composite body finalises at Plan 91-03."
# Note: the PR's pull_request event will auto-trigger a 6th CI run; concurrency.cancel-in-progress
# means it'll get cancelled if the first workflow_dispatch run is already in flight,
# OR the workflow_dispatch sequence below will cancel it. Either is fine — only the
# 5 explicit workflow_dispatch runs feed PERF-06.

# Step 2: Confirm workflow_dispatch trigger is wired and the PR-branch is HEAD
grep -q "workflow_dispatch:" .github/workflows/ci.yml && echo "PASS: workflow_dispatch present"
gh pr view --json state,mergeable,headRefName,headRefOid

# Step 3: Sequentially trigger 5 ci.yml runs on the milestone PR branch, waiting for each
for i in 1 2 3 4 5; do
    echo "=== Triggering run $i/5 ==="
    gh workflow run ci.yml --ref "$BRANCH"
    sleep 5  # let GitHub register the run
    RUN_ID=$(gh run list --workflow=ci.yml --branch="$BRANCH" --event=workflow_dispatch --limit=1 --json databaseId --jq '.[0].databaseId')
    echo "Watching run $RUN_ID..."
    gh run watch "$RUN_ID" --exit-status
done

# Step 4: Extract E2E-step wallclock from each of the 5 workflow_dispatch runs
for id in $(gh run list --workflow=ci.yml --branch="$BRANCH" --event=workflow_dispatch --status=success --limit=5 --json databaseId --jq '.[].databaseId'); do
    echo "=== Run $id ==="
    # E2E step is the GitHub-Actions step running `./mvnw verify -Pe2e …`. The duration
    # is exposed via the step-level JSON; fallback is `grep "Run E2E Tests" --log`.
    gh run view "$id" --log 2>&1 | grep -E "BUILD SUCCESS|\[INFO\] Total time:" | tail -5
done

# Step 5: Drop min + max from the 5 values; median of middle 3
# Manual: sort the 5 mm:ss values, take rows 2-4, the middle row is the median.

# Step 6: Variance check (D-03)
# variance% = (max - min) / median * 100
# If variance > 20 %: document as a "**Observed variance:** X %" footnote in
# docs/test-performance.md § PERF-06 Re-Harvest. Do NOT auto-trigger a 2nd block.
```

### `docs/test-performance.md § PERF-06 Re-Harvest` shape

The new section appends to `docs/test-performance.md` after the existing § CI Results (PERF-05) section (line 183-end) and before the § Context Load Counts section (line 235+). The shape mirrors § CI Results (PERF-05) — see lines 200-231 of `docs/test-performance.md` for the canonical template.

```markdown
## PERF-06 Re-Harvest (Phase 91)

5 consecutive `workflow_dispatch` CI runs on the v1.12 milestone Draft PR branch
`gsd/v1.12-driver-import-and-test-perf` (head SHA `<sha>`), harvested per D-10 (5 runs,
drop min+max, median of 3) and D-17 (PR-branch CI ≡ post-merge master CI: ci.yml
runs identical steps for `pull_request`, `push`, and `workflow_dispatch` triggers;
PR-branch harvest closes Phase 91 inside the v1.12 milestone PR without an orphan
post-merge commit).

| Run | Run ID | E2E step wallclock | Seconds | Notes |
| --- | ------ | ------------------ | ------- | ----- |
| 1   | [link] | mm:ss | NNN | kept |
| 2   | [link] | mm:ss | NNN | kept |
| 3   | [link] | mm:ss | NNN | kept |
| 4   | [link] | mm:ss | NNN | dropped — min |
| 5   | [link] | mm:ss | NNN | dropped — max |

**CI Median (v1.12 baseline):** **mm:ss** (NNNs; middle 3 = a/b/c, median = b)
**Variance:** **X %** ((max − min) / median; within D-10 20 % tolerance — no
second 5-run block needed [OR] **observed variance** above 20 %; documented as a
spike per Phase 91 CONTEXT D-03 — no auto-retry).
**Comparison vs v1.11 baseline (23:00):** ΔX % ([reduction|regression|no-change])
**Cumulative levers landed:** Phase 89 PERF-01 per-fork backup-staging-dir +
PERF-02 fingerprint listener; Phase 90 PERF-03 composed `@CtcDevSpringBootContext`
+ PERF-04 Testcontainers `.withReuse(true)` opt-in + PERF-05 module-split defer
verdict.

[Optional "No-Improvement Outcome" subsection per D-10b — Phase-90 OR-branch
shape — if the new median is NOT materially below 23:00. Contains: (a) empirical
median, (b) Phase 86/89/90 lever-by-lever recap, (c) hypothesized causes for
absent CI improvement, (d) v1.13 next forward path.]
```

### `STATE.md § Baselines to Preserve` swap (D-04)

The current STATE.md line at `.planning/STATE.md:142`:

```markdown
- `./mvnw verify -Pe2e` CI median (E2E step): **23:00** (v1.11 baseline; v1.12 Round-2 target TBD post-PERF-01..03, re-harvested in Phase 91 PERF-06)
```

Replace with:

```markdown
- `./mvnw verify -Pe2e` CI median (E2E step): **mm:ss** (v1.12 baseline, Phase 91 PERF-06 — 5-run median, drop min+max; was 23:00 in v1.11)
```

### `PROJECT.md § Key Decisions` new row (D-04)

Append a row to the "Key Decisions" table (currently ends around line 264 of PROJECT.md):

```markdown
| PERF-06 v1.12 CI baseline re-harvested (Phase 91 D-17) | 5 `workflow_dispatch` runs on the v1.12 milestone PR-branch HEAD SHA; D-17 trigger-equivalence (PR-branch = post-merge master because ci.yml runs identical steps regardless of trigger); median of middle 3 after drop min+max | ✓ v1.12 (CI median **mm:ss**, was 23:00 in v1.11) |
```

## Plan 91-02 UX-01 Mechanics

### Existing user-trigger surfaces (verified at source)

| File:line | Method | Operation | Plan 91-02 change |
|-----------|--------|-----------|-------------------|
| `DriverSheetImportController.java:39-67` | `preview(String sheetUrl, Model model)` | POST `/admin/drivers/import/preview` | Replace single `catch (IOException e)` (line 56-60) with 4 typed catches; add `errorCategory` to Model |
| `DriverSheetImportController.java:69-114` | `execute(...)` | POST `/admin/drivers/import/execute` | Requires `DriverSheetImportService.execute()` (`DriverSheetImportService.java:99`) refactor — currently wraps `IOException` in `IllegalStateException` at line 108; change to `throws IOException` propagation OR unwrap cause in controller |
| `RaceController.java:187-196` | `createCalendarEvent(UUID id, ...)` | POST `/admin/races/{id}/create-calendar-event` | Replace combined `catch (IOException | IllegalStateException e)` with: 4 typed catches for `GoogleApiException` subtypes + 1 catch for `IllegalStateException` (availability/duration-config — not Google-API) |

### Existing background-trigger consumers (audit required, D-08)

| File:line | Caller | User-triggered? (verified) |
|-----------|--------|----------------------------|
| `RaceService.java:32` | `private final RaceCalendarService raceCalendarService;` field | UNKNOWN at research time. Plan 91-02 Wave-2 Task 1 reads `RaceService.java` and decides whether to (a) flash UX (if user-triggered via a controller) or (b) log+swallow (if background/scheduled). |
| `RaceCalendarService.java:23` | `isCalendarAvailable()` — read-only, no Google call | N/A — no Google API call here |
| `RaceCalendarService.java:28` | `createOrUpdateCalendarEvent(raceId)` — calls `googleCalendarService.{create,update}Event()` | THROWS up to caller. The only verified caller is `RaceController#createCalendarEvent` (user-triggered). The `RaceService` field reference at `:32` is the unknown — planner audits. |

### Existing exception-handling shape (verified)

Existing `IOException` handling at `DriverSheetImportController.java:56-60`:

```java
} catch (IOException e) {
    log.error("Error reading Google Sheet for driver import", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Could not read the Google Sheet. Check the URL and service account credentials.");
    return "admin/driver-import";
}
```

This block needs to be split into 4 typed catches. After Plan 91-02:

```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Authentication problem — re-link Google account");
    model.addAttribute("errorCategory", "AUTH");
    return "admin/driver-import";
} catch (NotFoundGoogleApiException e) {
    log.error("Google Sheet not found", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Sheet not found — check ID");
    model.addAttribute("errorCategory", "NOT_FOUND");
    return "admin/driver-import";
} catch (PermissionGoogleApiException e) {
    log.error("Permission denied on Google Sheet", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Access denied — share the sheet with the service account");
    model.addAttribute("errorCategory", "PERMISSION");
    return "admin/driver-import";
} catch (TransientGoogleApiException e) {
    log.warn("Transient Google API failure during sheet preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/driver-import";
}
```

The existing `catch (IllegalArgumentException | IllegalStateException e)` block (line 61-65) **stays unchanged** — those are client-input + availability errors, not Google API errors per D-06.

### `admin/css/admin.css` insertion

The file is at `src/main/resources/static/admin/css/admin.css` (NOT `src/main/resources/static/admin.css` as CONTEXT.md says — note the corrected path).

Existing related classes:
- `.alert` at line 153 + `.alert-success` / `.alert-error` / `.alert-warning` at lines 159-163 — these are the flash-message container classes (used by layout.html:86 + driver-import.html:8).
- `.badge` at line 346 + `.badge-active` / `.badge-inactive` / `.badge-warning` at lines 355-357 — these are the existing **entity-status badges** (e.g., season is-active state). They are NOT in conflict with the new `.error-badge` block but the planner should be aware of the visual idiom they follow (rounded pill, ~12px font, inline-block).

Plan 91-02 appends after the existing `.badge*` rules (around line 358):

```css
/* Google API error categories (Phase 91 / UX-01) */
.error-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-right: 8px;
    vertical-align: middle;
}
.error-badge--transient  { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
.error-badge--auth       { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.error-badge--not-found  { background: var(--success-bg); color: #66bb6a; border: 1px solid #2e7d32; }
.error-badge--permission { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
```

(Exact colour values are illustrative — planner discretion. The point is the BEM modifier shape + alignment with existing tokens.)

**Confirmed:** `grep "error-badge" src/main/resources/static/admin/css/admin.css` returns 0 hits — no collision.

### Template insertion shape

The badge needs to be visible whenever `errorCategory` is set in flash/model. The minimal change is in `layout.html` (where most flash messages land) + `driver-import.html` (where the form re-renders without redirect). Verified shape at `layout.html:86`:

```html
<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}"></div>
```

Plan 91-02 replaces this with:

```html
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
```

Same change at `driver-import.html:8-10`. `race-detail.html` consumes via layout.html flash so does not need direct edits.

### `docs/operations/google-integration.md` shape

Mirrors `docs/operations/release-runbook.md` + `docs/operations/import-runbook.md` (both read in full). Concrete structure:

```markdown
# CTC Manager — Google Integration Runbook

Audience: operator (`@jegr78`) of the CTC Manager Google API integration (driver
import + race calendar sync). This runbook covers credentials setup, error
category responses, and troubleshooting flows.

**Cross-references:**
- `application*.yml` — `google.sheets.credentials-path`, `google.calendar.id`
- `org.ctc.dataimport.GoogleSheetsService` — driver-import Google Sheets client
- `org.ctc.dataimport.GoogleCalendarService` — race calendar client
- `org.ctc.dataimport.exception.GoogleApiException` — sealed typed-exception hierarchy

---

## Section 1 — Setup

### Service account credentials

1. Create a Google Cloud service account in the [Google Cloud Console](https://console.cloud.google.com/iam-admin/serviceaccounts).
2. Enable the **Google Sheets API** and **Google Calendar API** on the project.
3. Download the JSON key file and place it at the path configured in `application-{profile}.yml`:
   ```yaml
   google:
     sheets:
       credentials-path: /etc/ctc-manager/google-credentials.json
     calendar:
       id: <calendar-id-from-google-calendar-settings>
   ```
4. Share the target Google Sheet **and** the target Calendar with the service account
   email (`service-account-name@project-id.iam.gserviceaccount.com`). The Sheet must be
   shared as **Editor** (or **Viewer** if read-only suffices); the Calendar must allow
   **Make changes to events**.
5. OAuth scope reference: `SheetsScopes.SPREADSHEETS_READONLY` for sheets,
   `CalendarScopes.CALENDAR_EVENTS` for calendar (already wired in the service
   constructors — no operator action needed).

### Verification

- Start the app: the log line `Google Sheets integration available (credentials: ...)`
  confirms the credentials path was resolved.
- Visit `/admin/drivers/import` — the "Google Sheets available" indicator confirms the
  service-account key is readable.

---

## Section 2 — Error Categories

Every Google API failure surfaces in the admin UI as a flash error with a
category badge. The badge is one of four values:

| Category   | User-visible message                                | Root cause                                                                 | Operator action                                                                                                                                       |
|------------|-----------------------------------------------------|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| TRANSIENT  | Connection problem — retry                         | Network timeout, HTTP 5xx, Google API rate-limit                           | Wait 30-60 s and retry. If the badge persists for >5 min, check Google API status at <https://status.cloud.google.com>.                              |
| AUTH       | Authentication problem — re-link Google account    | Expired OAuth token, malformed credentials JSON, `GeneralSecurityException` | Re-download the service-account JSON key and replace the file at `google.sheets.credentials-path`. Restart the app to pick up the new credentials.    |
| NOT_FOUND  | Sheet not found — check ID                         | Sheet ID typo, sheet was deleted, calendar ID does not exist               | Verify the URL pasted into the import form. Cross-check the Sheet still exists at <https://sheets.google.com>.                                       |
| PERMISSION | Access denied — share the sheet with the service account | Sheet/Calendar is not shared with the service account principal            | Open the Sheet → Share → add the service-account email as Editor. For Calendar, open Settings → Share with specific people.                          |

The exact user-visible message strings above match the controller's flash text
verbatim. Any future change to a flash string MUST update this table in the same
commit (Update-on-Triage discipline).

---

## Section 3 — Troubleshooting

### "Authentication problem" badge appears immediately on every request

Likely the credentials JSON has expired or was rotated server-side. Re-issue the
key (Section 1 Step 3) and verify the service account is still active in
the Cloud Console IAM page.

### "Sheet not found" appears for a sheet I just confirmed exists

Most common cause: the import URL was pasted with the user's view URL
(`/edit#gid=…`) but the regex in `GoogleSheetsService.extractSpreadsheetId`
recognises only the canonical `/spreadsheets/d/{id}/…` form. If the URL has been
heavily edited or copied from a mobile app, re-copy the link from the canonical
Google Sheets "Share" → "Copy link" affordance.

If the URL is correct, the next likely cause is that the sheet was renamed AND
moved into a folder the service account cannot reach. Re-share at the sheet level
explicitly.

### "Access denied" but the sheet is "Anyone with the link can view"

The Google API's service-account flow does NOT use the link-share permission
even when set to "anyone". Each Sheet must be explicitly shared with the
service-account email. Re-share at Editor (or Viewer for sheets, Editor for
calendars where event creation is needed).

### "Connection problem" badge persists across multiple retries

1. Confirm the JVM has network egress to `*.googleapis.com:443`.
2. Check `https://status.cloud.google.com` for active incidents on Google
   Sheets / Calendar APIs.
3. If neither, capture the stack-trace from the app log (the `GoogleApiException`
   cause chain carries the underlying `GoogleJsonResponseException` or
   `java.net.SocketException` — see the SLF4J ERROR/WARN line emitted at the
   relevant controller method).
4. Rate-limit (HTTP 429): the underlying cause is throttling; back off for
   2-5 minutes before retrying. The driver-import path makes one Sheet API call
   per tab plus one metadata call; for sheets with >20 tabs this can exhaust the
   per-minute quota on the default service-account tier.

### Calendar event creation works, but the event has the wrong time zone

The calendar service hard-codes `Europe/London` as the time zone
(`GoogleCalendarService.java:31`). This is intentional (the league operates in
that zone). If the league time zone ever changes, update the `TIME_ZONE`
constant in `GoogleCalendarService` (a code change, not an operator action) and
re-deploy.

---

**Last updated:** 2026-05-20 (Phase 91 / UX-01).
```

## Plan 91-03 Closer Mechanics

### `MILESTONES.md` v1.12 entry shape

The v1.11 entry at `.planning/MILESTONES.md` lines 3-44 is the template. The v1.12 entry inserts at the top (above line 3), pushing v1.11 down. Structural fields:

```markdown
## v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (Shipped: YYYY-MM-DD)

**Phases completed:** 4 phases (88-91), ~16 plans, 15/15 requirements satisfied (14 must-have + 1 stretch)
**Diff:** +<X> / −<Y> across <Z> files (<N> commits in milestone range)
**Tests:** <count> tests passing (Surefire + Failsafe + Playwright E2E); JaCoCo line coverage <X.XX> % (gate 82 %, v1.11 baseline 88.88 %, +<delta>)
**Timeline:** <N> days (2026-05-18 → YYYY-MM-DD)
**Branch:** `gsd/v1.12-driver-import-and-test-perf` (PR #<num>)
**Final-gate CI:** Run [<id>](https://github.com/jegr78/ctc-manager/actions/runs/<id>) @ SHA `<sha>` SUCCESS — E2E step <mm:ss> ≤ <ceiling> D-06 ceiling, SpotBugs 0 BugInstance, no Surefire fork-channel corruption
**Audit verdict:** passed (`v1.12-MILESTONE-AUDIT.md`); Nyquist scoreboard compliant 4/0/0

**Key accomplishments:**

- Phase 88 (Build/Release Unblockers, YAGNI Sweep, Doc-Conventions, Driver-Import Gap-Closure) — CLEAN-01..03 (`@Disabled` sweep + SiteGeneratorBaselineRefresh utility + Java-25 AssertJ compile fix), REL-01..02 (release workflow hardening + retroactive v1.10.0 / v1.11.0 publishes + legacy tag cleanup), DOCS-01 (`/gsd-` skill-invocation prefix), DRIV-01..02 (season-aware shortName resolver + GROUPS-layout gate)
- Phase 89 (PERF Instrumentation + Lever 1) — PERF-01 per-fork `app.backup.staging-dir` + `app.backup.import-backups-dir` + `app.upload-dir` refactor enabling Failsafe `default-it forkCount=2 reuseForks=true`; PERF-02 `ContextCacheKeyFingerprintListener` + sidecar marker + `scripts/test-perf/aggregate-fingerprints.sh`; Wave-4 local median 09:19 = -10.4 % vs. Phase-86 10:24 baseline
- Phase 90 (PERF Consolidation + Module-Split Decision) — PERF-03 composed `@CtcDevSpringBootContext` annotation across 19 outer classes, Surefire cluster collapse `9cefac4c`→`baafff8e` (29 events / 13 classes preserved); PERF-04 `.withReuse(true)` on both MariaDB ITs + `~/.testcontainers.properties` opt-in documented; PERF-05 module-split `defer` verdict + 3 explicit blockers + v1.13 re-evaluation trigger
- Phase 91 (PERF Re-Harvest + UX-01 + Closer) — PERF-06 CI 5-run median <mm:ss> harvested per D-17 trigger-equivalence (5 `workflow_dispatch` runs on milestone Draft PR HEAD SHA; drop min+max; variance <X> %); UX-01 sealed `GoogleApiException` hierarchy (4 typed permits: Transient/Auth/NotFound/Permission) + `GoogleApiExceptionMapper` + flash UX with `errorCategory` badge in admin templates + `docs/operations/google-integration.md` runbook; milestone close with composite PR body and v1.12 entry in MILESTONES.md
- JaCoCo line coverage held at <X.XX> %; SpotBugs `BugInstance` 0; CodeQL gate-step exit 0; Flyway V1-V7 unchanged; `EXPORT_ORDER` = 24; `BackupSchema.SCHEMA_VERSION` = 1

**Deferred to next milestone (acknowledged at close):**

- Test-module-split extraction (`ctc-manager-tests` Maven artifact) — Phase 90 PERF-05 `defer` verdict; v1.13 re-evaluates against PERF-06 CI median <mm:ss> surfaced in Phase 91
- Secondary cluster consolidation (backup-exception 12-class, admin-security 12-class, AdminWorkflowE2E 7-class buckets) — Phase 90 D-01 conservative
- Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class `db.migration.**` cluster
- Background-trigger calendar-sync UX surface — UX-01 D-08 keeps non-user-triggered paths in graceful-fallback; future phase if operator demand surfaces
- Retry-with-backoff for `TransientGoogleApiException` — UX surfaces "retry" wording but no auto-retry
- OAuth re-link UI flow / Sheet-ID lookup helper / `@ControllerAdvice` typed-exception handler extraction — all out-of-scope per Phase 91 deferred items

**Post-merge self-resolving (not tech debt):**

- v1.12 milestone PR squash-merge to master (CI release workflow handles `v1.12.0` tag + GitHub Release + Docker images via the hardened workflow from Phase 88 REL-01)

Known deferred items at close: see `STATE.md` Deferred Items + `v1.12-MILESTONE-AUDIT.md`

---
```

### `README.md` updates

Two existing sections need pointer updates:

- **§ Test Performance** — points at `docs/test-performance.md`. The pointer paragraph should mention the new v1.12 baseline (`<mm:ss>` from Phase 91 PERF-06) replacing the v1.11 23:00 figure.
- **Backup section** — references the v1.10 PR / current backup-runbook docs. The update points readers at the new v1.12 PR (#<num>) as the active milestone PR.

Exact wording is planner discretion (the existing README structure is the template).

### v1.12 milestone PR body composite shape (D-07b)

Verified via `gh pr view 122` — the v1.11 PR body is the canonical template. Concrete structural components (from the read v1.11 body):

1. **Top — Status line.** "Ready to merge — X of Y phases complete, milestone audit `passed`, all operator actions done."
2. **Top — Phase summary table.** Columns: `| Phase | Status | What it ships |`. One row per phase (88/89/90/91 for v1.12).
3. **Middle — Per-phase deep narrative.** For Phase 91: the "Phase 91 — PERF Re-Harvest + UX-01 + Closer" section. For Phase 90: a recap section with the same shape. Same for 89/88.
4. **Middle — Inline-closure audit narrative.** If Phase 91 surfaces any inline Nyquist closure (D-11 strict — likely NOT needed since v1.12 has been disciplined), a small "Milestone Audit + Nyquist Closure" subsection with per-phase verification commits.
5. **Bottom — Verification Numbers table.** Columns: `| Check | Value | Notes |`. Rows: JaCoCo line coverage, total tests, SpotBugs count, CodeQL gate active, CI wallclock (E2E step), Nyquist scoreboard, requirements count, milestone audit status.
6. **Bottom — CLAUDE.md updates cumulative.** Bullets for any new sections (UX-01 may not add one — typed-exception hierarchy is structural, not a convention change).
7. **Bottom — Test plan checklist.** Per-phase `./mvnw verify` confirmation, code-review passes, CodeQL gate, per-phase verification artifacts.
8. **Bottom — Notes for Reviewer.** Squash-merge target, large-diff explanation, key technical risks/decisions.
9. **Bottom — Post-merge actions.** UAT-02 live execution, `/gsd-complete-milestone v1.12` execution.

For Phase 91, the body MUST also include:

- **CI run links for PERF-06** — the 5 `workflow_dispatch` run URLs from the harvest (plus the auto-triggered `pull_request` event run that landed when the Draft PR opened).
- **CI baseline-comparison block** — v1.11 23:00 → v1.12 mm:ss (ΔX %).
- **REQ-ID master table** — 15 rows mapping each REQ to its phase + plan + acceptance-evidence commit hash. Planner authoring instruction: pull the per-REQ commit hashes from REQUIREMENTS.md (the `Resolved (...)` annotations) + plan SUMMARY.md per-REQ rows.

The Phase 91 D-07b composite-body finalisation is itself a Plan 91-03 task — the planner sequences the steps:

1. Pull the 5 PERF-06 CI run URLs (`gh pr view <num> --json body` confirms current state).
2. Pull the JaCoCo / SpotBugs / CodeQL / E2E-step final numbers (from the latest CI run on the PR HEAD).
3. Pull the 15-row REQ-ID mapping table from REQUIREMENTS.md Traceability section (lines 88-104 of `.planning/REQUIREMENTS.md`).
4. Compose the body via `gh pr edit <num> --body-file <tempfile>`.
5. Flip Draft → Ready-for-review via `gh pr ready <num>`.

## Runtime State Inventory

Not applicable — Phase 91 is not a rename/refactor/migration phase. UX-01 (Plan 91-02) is purely additive (4 new exception classes, 1 mapper, 2 service refactors, 2 controller catch-block changes, 1 CSS appendage, 1 new doc). No stored data, no live service config, no OS-registered state, no secrets/env vars, no build artifacts are affected.

For completeness, the explicit categorical confirmation:

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | None | None |
| Live service config | None (Google credentials path is config-driven via `google.sheets.credentials-path` — UNCHANGED) | None |
| OS-registered state | None | None |
| Secrets/env vars | None (no env var name changes; `google.sheets.credentials-path` + `google.calendar.id` unchanged) | None |
| Build artifacts | None | None |

## Common Pitfalls

### Pitfall 1: Triggering all 5 PERF-06 runs in parallel
**What goes wrong:** Earlier runs get cancelled mid-execution.
**Why it happens:** `.github/workflows/ci.yml on.concurrency.cancel-in-progress: true` — a new run preempts an in-flight run on the same branch.
**How to avoid:** `gh run watch <RUN_ID> --exit-status` between each `gh workflow run` invocation.
**Warning signs:** `gh run list ... --status=cancelled` returns rows mid-harvest; the harvest produces fewer than 5 successful runs.

### Pitfall 2: Computing median from the wrong field
**What goes wrong:** Total CI wallclock (whole job duration including setup/teardown) instead of E2E-step wallclock.
**Why it happens:** `gh run view --log` shows multiple "Total time" markers (Maven, per-step Actions runner).
**How to avoid:** Filter to the **E2E Tests** step's wallclock specifically. Phase 86 PERF-05 settled on "E2E step wallclock ≈ Maven `Total time` within ±3s on GitHub-hosted runners". Use the step-level metric (visible in `gh run view --json jobs --jq '.jobs[].steps[] | select(.name=="Run E2E Tests") | .conclusion_duration'`) or the Maven `Total time:` line — they should agree within 3 seconds.
**Warning signs:** The 5 wallclock values are all >25 minutes (you're including setup); or the variance is implausibly large.

### Pitfall 3: Auto-retrying a 2nd 5-run block on variance >20 %
**What goes wrong:** 90+ extra CI minutes spent on a measurement, not a feature.
**Why it happens:** Phase 86 D-10 originally specified "if variance >20 %, harvest a 2nd 5-run block." Phase 91 D-03 explicitly OVERRIDES this for Phase 91 (no hard gate; observation suffices).
**How to avoid:** Document the spread as a "**Observed variance:** X %" footnote in `docs/test-performance.md § PERF-06 Re-Harvest`; ship v1.12 anyway. Operator may manually trigger a 6th run on a separate session if a runner-outlier is suspected — but that's a manual decision, not an auto-retry.

### Pitfall 4: Mistakenly classifying `IllegalArgumentException` URL-format errors as `GoogleApiException`
**What goes wrong:** Client-side input-validation errors get the `TRANSIENT` or `AUTH` badge, confusing users about whether to retry or fix the URL.
**Why it happens:** `GoogleSheetsService.extractSpreadsheetId(url)` throws `IllegalArgumentException` for malformed URLs (line 138 + 154). This is NOT a Google API error — it's pre-call input validation.
**How to avoid:** Plan 91-02 leaves the existing `catch (IllegalArgumentException | IllegalStateException e)` block in `DriverSheetImportController:61-65` UNCHANGED. The mapper is invoked only inside the `catch (IOException ...)` block (which is now narrowed to `catch (GoogleApiException ...)` with 4 typed sub-catches).
**Warning signs:** A `GoogleApiException` is thrown from `extractSpreadsheetId`. (It should never happen — that method doesn't catch anything from the Google client.)

### Pitfall 5: Mistakenly removing the existing `IllegalStateException` catch
**What goes wrong:** Operators see no flash UI when Google credentials are misconfigured at boot time.
**Why it happens:** `GoogleSheetsService.getSheetsClient` throws `IllegalStateException("Google Sheets credentials not configured or file not found")` at line 160 — this is an availability-check, not a Google API call. The controller has a `catch (IllegalStateException ...)` branch that surfaces the message via flash.
**How to avoid:** Preserve the existing `IllegalStateException` catches verbatim. The new typed catches sit alongside them, not in place of them.
**Warning signs:** `grep "IllegalStateException" src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` returns 0 hits after Plan 91-02 — should still return ≥1.

### Pitfall 6: Forgetting to update `DriverSheetImportService.execute()` line 108 wrap
**What goes wrong:** Typed Google API exceptions get re-wrapped as generic `IllegalStateException` inside `execute()`, so the controller's typed catches never fire.
**Why it happens:** Line 108 of `DriverSheetImportService.java` wraps `IOException` from `preview()` in a generic `IllegalStateException("Sheet read failed: " + e.getMessage(), e)`.
**How to avoid:** Plan 91-02 either (a) refactors `execute()` to declare `throws IOException` and propagate the typed subtype, or (b) leaves the wrap and changes the controller `catch (IllegalStateException ...)` to unwrap `.getCause()` and instanceof-check for `GoogleApiException`. Recommendation (a) — clean signature propagation, `@Transactional` allows checked exceptions.
**Warning signs:** Plan 91-02 tests show the controller fall-through landing in the generic-error branch when a `GoogleApiException` was thrown from `preview()`.

### Pitfall 7: CSS path mismatch (`admin.css` vs `admin/css/admin.css`)
**What goes wrong:** CSS edit lands in a file that isn't loaded; badges don't render.
**Why it happens:** CONTEXT.md states `admin.css` is at `src/main/resources/static/admin.css`, but the actual path is `src/main/resources/static/admin/css/admin.css` (verified via `find`). The template references `@{/admin/css/admin.css}` at `layout.html:9`.
**How to avoid:** Plan 91-02 edits the file at the verified path. Quick sanity check: `find src/main/resources/static -name admin.css`.

### Pitfall 8: Sealed-class hierarchy compile error from missing `permits` clause member
**What goes wrong:** `javac` rejects the sealed base because a permitted subtype is not actually declared `final` (or `sealed` or `non-sealed`).
**Why it happens:** Java sealed classes require every permitted subtype to be explicitly `final`, `sealed`, or `non-sealed`. All 4 UX-01 subtypes should be `final`.
**How to avoid:** Each of `TransientGoogleApiException`, `AuthGoogleApiException`, `NotFoundGoogleApiException`, `PermissionGoogleApiException` declared `public final class X extends GoogleApiException { ... }`.
**Warning signs:** `./mvnw clean test-compile` fails with "sealed class must declare all subclasses".

### Pitfall 9: Forgetting to mention CONTEXT.md's incorrect "v1.10 Backup phases already used [sealed] idiom" claim
**What goes wrong:** The planner copies the wrong precedent shape (enum-Reason single-class) when D-06 explicitly picks sealed.
**Why it happens:** CONTEXT.md `<code_context>` § Established Patterns says "Sealed exception hierarchies on Java 25 — v1.10 Backup phases already used this idiom" — but the verified backup hierarchy uses `BackupArchiveException(Reason reason, ...)` enum pattern, not sealed. `grep "sealed\|permits" src/main/java/org/ctc/ --include=*.java` returns 0 hits.
**How to avoid:** Planner treats D-06 as authoritative; sealed is fine on Java 25; ignores the inaccurate "precedent exists" framing.

### Pitfall 10: Background-trigger calendar consumers not audited
**What goes wrong:** A scheduled task or post-save hook in `RaceService` calls `RaceCalendarService.createOrUpdateCalendarEvent`, which now throws `GoogleApiException`; the caller doesn't catch it, transaction rolls back, save operation fails for the user (whose primary action was unrelated to calendar).
**Why it happens:** D-08 requires consumer audit but the audit is a Plan 91-02 task, not pre-research.
**How to avoid:** Plan 91-02 Wave-2 Task 1 reads `RaceService.java` (or grep `raceCalendarService\.\|RaceCalendarService` in main source) and explicitly lists each call site as user-trigger (apply UX) or background-trigger (log + swallow with `WARN`).
**Warning signs:** `RaceService` references `raceCalendarService` (`RaceService.java:32`) but planner doesn't enumerate the call sites.

## Code Examples

### Existing flash-attribute pattern (used as Plan 91-02 reference)

```java
// Source: src/main/java/org/ctc/admin/controller/DriverSheetImportController.java:104-112 (existing)
} catch (BusinessRuleException | ValidationException | IllegalArgumentException e) {
    log.error("Error executing driver sheet import", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
} catch (DataIntegrityViolationException e) {
    log.error("Driver sheet import hit DB constraint — transaction rolled back, no rows inserted", e);
    redirectAttributes.addFlashAttribute("errorMessage",
            "Import failed due to a database constraint. Nothing was imported. See server logs for details.");
}
```

### Existing typed-exception precedent (BackupArchiveException enum-Reason pattern — Plan 91-02 alternative reference)

```java
// Source: src/main/java/org/ctc/backup/exception/BackupArchiveException.java
public class BackupArchiveException extends RuntimeException {
    public enum Reason { PATH_TRAVERSAL, ENTRY_TOO_LARGE, ..., NOT_A_ZIP }
    private final Reason reason;
    public BackupArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
```
(Plan 91-02 picks sealed per D-06 — this is the **structural alternative**, not the chosen shape.)

### Existing template flash-render pattern (Plan 91-02 insertion point)

```html
<!-- Source: src/main/resources/templates/admin/layout.html:85-86 (existing) -->
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}"></div>
```

### Phase 86 PERF-05 harvest commands (Plan 91-01 verbatim template)

```bash
# Source: .planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md Task 1 Step 2-3
BRANCH=gsd/v1.12-driver-import-and-test-perf

for i in 1 2 3 4 5; do
    echo "=== Triggering run $i/5 ==="
    gh workflow run ci.yml --ref "$BRANCH"
    sleep 5
    RUN_ID=$(gh run list --workflow=ci.yml --branch="$BRANCH" --event=workflow_dispatch --limit=1 --json databaseId --jq '.[0].databaseId')
    echo "Watching run $RUN_ID..."
    gh run watch "$RUN_ID" --exit-status
done

for id in $(gh run list --workflow=ci.yml --branch="$BRANCH" --event=workflow_dispatch --status=success --limit=5 --json databaseId --jq '.[].databaseId'); do
    echo "=== Run $id ==="
    gh run view "$id" --log 2>&1 | grep -E "BUILD SUCCESS|\[INFO\] Total time:" | tail -5
done
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Throw `IOException` from Google service calls; catch generically in controller; surface "Could not read the Google Sheet" generic message | Throw 4-typed sealed `GoogleApiException` hierarchy; catch specifically; surface category-specific flash + badge | Phase 91 / UX-01 (this phase) | Operator can immediately recognise root cause and pick the right remediation path (re-link, re-share, retry, check ID) without log diving |
| Single-shot `gh workflow run` triggers concurrent runs that cancel each other | Sequential `gh workflow run` + `gh run watch <id> --exit-status` between triggers | Phase 86 PERF-05 (precedent); Phase 91 PERF-06 reuses | Eliminates lost runs in CI baseline harvest; D-17 trigger-equivalence holds |
| Local 3-run idle protocol = authoritative baseline | CI 5-run `workflow_dispatch` harvest = authoritative baseline; local 3-run idle = observational reference | Phase 86 D-11 (CI source of truth) | CI harvests reflect production CI hardware; local runs are dev-machine variance |
| Hard CI wallclock gate (≤7m 50s) | Observational gate ("materially below 23:00"; no-improvement = OR-branch) | Phase 86 PERF-04 (OR-branch precedent); Phase 91 D-10b reuses | Honest measurement; ships levers even when cumulative effect is less than target |
| Append-only `docs/test-performance.md` sections per phase | Same — Phase 91 adds § PERF-06 Re-Harvest section between existing § CI Results (PERF-05) and § Context Load Counts | Phase 90 (precedent for append-only convention) | No rewrite of earlier sections; structural diff stays small |
| Single-branch `gsd/v1.12-driver-import-and-test-perf` for all 4 v1.12 phases | Same | v1.12 D-09 (Phase 88) carries forward | Sequential inline phase execution avoids the worktree-clobber pattern documented in `feedback_worktree_file_clobber` |

**Deprecated/outdated:**
- The "stretch ≥30 % reduction to ≤16:00" Success-Criterion-#2 wording is descriptively-observational only — D-10b made it explicit that NO improvement still ships the milestone.
- The CONTEXT.md claim that "v1.10 Backup phases already used [sealed] idiom" is **inaccurate** (the backup hierarchy uses `RuntimeException + enum Reason` shape, NOT sealed classes). The sealed-on-Java-25 introduction is fresh in Phase 91 — but uncontroversial.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `GoogleJsonResponseException.getDetails().getErrors().get(0).getReason()` returns one of the documented Google API standard reason strings (`authError`, `forbidden`, `insufficientPermissions`, etc.) | "Pattern 2: GoogleJsonResponseException → typed-subtype mapper" | Low. The mapper falls back to `PermissionGoogleApiException` for any 403 whose reason is not recognised — that's the more conservative classification (operator's remediation for "auth vs. permission" 403s is similar in practice — re-link OR re-share). |
| A2 | `RaceService.java:32` (`raceCalendarService` field) is referenced from a controller-driven flow, not a scheduled task | "Audit of `RaceCalendarService` consumers" | Medium. If `RaceService` calls `createOrUpdateCalendarEvent` from a background context (post-save hook on race update), Plan 91-02 must wrap the call in a try/catch that logs + swallows; otherwise a Google API outage would block all race saves. The Plan 91-02 Task 1 audit closes this assumption. |
| A3 | The E2E-step wallclock in CI is approximately equal to Maven `Total time:` within ±3 seconds | "Pitfall 2: Computing median from the wrong field" | Low. Phase 86 PERF-05 verified this empirically across 5 runs (lines 192-198 of `docs/test-performance.md`). Maven owns the entire step duration; the Actions step wrapper adds negligible overhead. |
| A4 | The v1.12 milestone PR has not yet been opened (no existing `gh pr list` row for the v1.12 branch) | "Plan 91-01 PERF-06 Mechanics" Step 1 | Low. `gh pr list --state open` returned empty at research time (2026-05-20). If a Draft PR was opened separately, Plan 91-01 task 1 short-circuits to `gh pr edit` instead of `gh pr create`. |
| A5 | The 5 PERF-06 CI run E2E-step durations will be available via `gh run view --json jobs --jq '.jobs[].steps[] | select(.name=="Run E2E Tests")'` OR via the Maven `Total time:` log line | "Pattern 3: Controller catch-translate to flash UX" | Low. Both fields are reliable in current ci.yml; Phase 86 verified both. |
| A6 | The mapper handles `GeneralSecurityException` separately at the call-site (in `getSheetsClient`/`getCalendarClient`) — these only fire during client-builder init, before any HTTP call | "Pattern 2: GoogleJsonResponseException → typed-subtype mapper" | Low. Verified at source: `GoogleSheetsService.java:176` catches `GeneralSecurityException` and wraps in `IOException`. Plan 91-02 changes the wrap to throw `AuthGoogleApiException` instead — direct one-line edit. |
| A7 | Java 25 + Spring Boot 4.0.6 + Hibernate 7 fully support `sealed` classes for exception hierarchies (no proxy/AOP interference) | "Pattern 1: Sealed-base exception with category enum" | Low. JEP 409 (sealed classes) finalised in Java 17; multi-year proven track record; no proxy interference (exceptions are not Spring beans). |
| A8 | The existing `IOException` declaration on `RaceCalendarService.createOrUpdateCalendarEvent` (line 28) can be widened to `throws GoogleApiException` (a subtype of IOException) without affecting any caller's catch signature | "Existing background-trigger consumers (audit required, D-08)" | Low. `GoogleApiException extends IOException`, so any `catch (IOException ...)` still catches the typed subtype. No source-level breaking change. |
| A9 | The `docs/operations/google-integration.md` runbook can mirror the `release-runbook.md` + `import-runbook.md` shape (3 sections — Setup / Error Categories / Troubleshooting) | "`docs/operations/google-integration.md` shape" | Low. Both existing runbooks confirmed via Read; D-09 explicitly picks this shape. |

## Open Questions

1. **Where does `RaceService` call `RaceCalendarService.createOrUpdateCalendarEvent`?**
   - What we know: `RaceService.java:32` carries a `private final RaceCalendarService raceCalendarService;` field. The constructor injects it.
   - What's unclear: Is the field actually used at a call site? Or is it injected but never invoked (dead code)?
   - Recommendation: Plan 91-02 Wave-2 Task 1 reads `RaceService.java` in full and decides — user-trigger flash UX OR background-trigger log+fallback OR remove the field entirely (if unused).

2. **Should `errorCategory` be an enum or a String at the flash-attribute level?**
   - What we know: Both work. D-07 leaves the choice to the planner. Thymeleaf renders String more cleanly via `th:classappend`. Java callers benefit from compile-time safety with the enum.
   - Recommendation: Strongly-typed enum on the Java side (`GoogleApiException.Category`); call `.name()` at the flash-set site (`addFlashAttribute("errorCategory", e.category().name())`) — String lands in the flash, enum is the source.

3. **Does the v1.12 milestone PR body composite shape (D-07b) include an explicit "Phase 91 — PERF Re-Harvest + UX-01 + Closer" narrative section, OR a single "v1.12 wrap-up" narrative bullet?**
   - What we know: The v1.11 PR body (verified via `gh pr view 122`) has BOTH a phase-summary table AND a deep per-phase narrative section for Phase 87.
   - Recommendation: Mirror v1.11 — both. Phase 91 gets its own deep narrative section because PERF-06 + UX-01 are non-trivially each.

4. **Will the 5 PERF-06 CI runs land within a single dev session, or might they span multiple work sessions?**
   - What we know: Each `./mvnw verify -Pe2e` on CI runs ~23 minutes. 5 sequential runs ≈ 2 hours wallclock minimum.
   - Recommendation: Plan 91-01 task 1 has a `checkpoint:human-verify` gate at the harvest step (Phase 86 Plan-06 Task-1 precedent). Operator may run the harvest across multiple sessions; the median computation is a separate auto task.

5. **Does the PR body table need to include the existing CI run that auto-triggers on `gh pr create --draft` (the `pull_request` event run)?**
   - What we know: D-05 says "PR-body links the 5 workflow_dispatch run URLs from PERF-06 + the PR pull_request CI run URL" — implies both.
   - Recommendation: List all 6 (5 workflow_dispatch + 1 pull_request); mark the pull_request one as "validation run, not part of PERF-06 median".

## Environment Availability

Phase 91 has minimal external dependencies — the operator's local machine needs `gh` CLI (assumed available; used across the v1.12 milestone already) and Maven (assumed available; used in every previous phase). No new external tools.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `gh` CLI | Plan 91-01 (PERF-06 harvest) + Plan 91-03 (PR-body edit + ready flip) | ✓ (confirmed via Phase 86 PERF-05 successful harvest) | ≥ 2.30 | None — `gh` is required for the harvest pattern; without it the workflow_dispatch + run-watch sequence cannot run |
| Maven (`./mvnw`) | Plan 91-02 (`./mvnw clean test-compile` + final `./mvnw verify -Pe2e` per [[clean-maven-build-authority]]) | ✓ (every prior phase used it) | Maven 3.9+ | None |
| GitHub Actions ci.yml `workflow_dispatch` trigger | Plan 91-01 | ✓ (confirmed via `grep "workflow_dispatch:" .github/workflows/ci.yml`) | n/a | None — without `workflow_dispatch`, PERF-06 cannot run on a PR branch; this trigger was added in Phase 86 |
| Network egress to `*.googleapis.com:443` (Plan 91-02 manual smoke if desired) | Plan 91-02 (only if operator runs a UAT smoke against a real sheet) | UNKNOWN | n/a | UAT can be skipped — ITs cover the contract |
| `google-api-client` JAR on classpath | Plan 91-02 typed-exception mapper | ✓ (confirmed in `pom.xml`) | 2.9.0 | None |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

## Validation Architecture

(`workflow.nyquist_validation: true` in `.planning/config.json` — section included.)

Per D-11, each of the three plans ships its own VALIDATION.md. The phase-level `91-VALIDATION.md` is created at the Plan 91-03 SUMMARY (aggregating the three).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito 4.x + Playwright 1.59.0; Surefire `forkCount=2C`; Failsafe `default-it forkCount=2 reuseForks=true` (Phase 89) |
| Config file | `pom.xml` (Surefire lines ~266-309, Failsafe lines ~290-340), `src/test/resources/META-INF/spring.factories` (test listeners) |
| Quick run command | `./mvnw test -Dtest=GoogleApiExceptionMapperTest` (per-class unit test) |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase 91 Requirements → Test Map

PERF-06 (Plan 91-01) — **measurement validation**:

| Req ID | Behavior | Test Type | Automated Command | Evidence Location |
|--------|----------|-----------|-------------------|-------------------|
| PERF-06 | 5 `workflow_dispatch` runs land on v1.12 milestone Draft PR HEAD SHA; median of middle 3 recorded | Manual harvest + docs assertion | `grep -c '### PERF-06 Re-Harvest' docs/test-performance.md` ≥ 1; `grep -c 'CI Median (v1.12 baseline)' docs/test-performance.md` ≥ 1 | `docs/test-performance.md § PERF-06 Re-Harvest` + 5 CI run-IDs in PR body |
| PERF-06 | `STATE.md` Baselines line swapped from 23:00 to new median | Docs assertion | `grep -E 'CI median \(E2E step\): \*\*[0-9]+:[0-9]+\*\*' .planning/STATE.md` matches new value | `.planning/STATE.md:142` |
| PERF-06 | Variance % footnote present (D-03) | Docs assertion | `grep -c 'Variance' docs/test-performance.md` post-edit ≥ 2 (existing PERF-05 + new PERF-06) | `docs/test-performance.md § PERF-06 Re-Harvest` |

UX-01 (Plan 91-02) — **code + IT validation**:

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-01 | 4 sealed-subtype exception classes exist | Compile check | `./mvnw clean test-compile` exit 0; `find src/main/java/org/ctc/dataimport/exception -name '*GoogleApiException.java' \| wc -l` = 5 (1 sealed base + 4 permits) | ❌ → Wave 1 (new package, planner creates) |
| UX-01 | Mapper helper exists with static `from(IOException)` + `from(GeneralSecurityException)` | Compile check + unit test | `./mvnw test -Dtest=GoogleApiExceptionMapperTest` exit 0 | ❌ → Wave 1 (planner creates) |
| UX-01 | Mapper unit tests: 1 per category (4) + edge cases (non-Google IOException → TRANSIENT; null details → fallback) | Unit | `./mvnw test -Dtest=GoogleApiExceptionMapperTest` | ❌ → Wave 1 |
| UX-01 | `GoogleSheetsService` throws typed subtypes on `readRange`/`readRangeFromSheet`/`getSheetNames` | Unit | `./mvnw test -Dtest=GoogleSheetsServiceTest` (extend existing) | ✅ exists, ❌ assertions for typed throws → Wave 1 (extend) |
| UX-01 | `GoogleCalendarService` throws typed subtypes on `createEvent`/`updateEvent` | Unit | `./mvnw test -Dtest=GoogleCalendarServiceTest` | ✅ exists, ❌ assertions → Wave 1 (extend) |
| UX-01 | `DriverSheetImportController#preview` translates 4 typed exceptions to flash `errorMessage` + `errorCategory` | Unit (Mockito + MockMvc) | `./mvnw test -Dtest=DriverSheetImportControllerExceptionTest` (extend existing) | ✅ exists, ❌ assertions for new `errorCategory` flash → Wave 1 (extend) |
| UX-01 | `RaceController#createCalendarEvent` translates 4 typed exceptions to flash | Unit | `./mvnw test -Dtest=RaceControllerTest` (or equivalent — planner identifies) | UNKNOWN — planner audits |
| UX-01 | Badge CSS classes `.error-badge--{transient,auth,not-found,permission}` exist in `admin/css/admin.css` | Build/static check | `grep -c 'error-badge--' src/main/resources/static/admin/css/admin.css` ≥ 5 (1 base + 4 modifiers) | ❌ → Wave 1 |
| UX-01 | Template renders `<span class="error-badge ...">` when `errorCategory` is set | Manual UAT or `TemplateRenderingSmokeIT` extension | manual: trigger an AUTH error in a dev fixture; verify badge | UNKNOWN; if planner wants automated coverage, the existing `TemplateRenderingSmokeIT` is a precedent |
| UX-01 | `docs/operations/google-integration.md` exists with 3 sections | Docs assertion | `test -f docs/operations/google-integration.md && grep -cE '^## (Setup|Error Categories|Troubleshooting)' docs/operations/google-integration.md` = 3 | ❌ → Wave 1 |
| UX-01 | New `errorCategory` flash key documented in Plan 91-02 SUMMARY | Docs assertion | `grep -c 'errorCategory' .planning/milestones/v1.12-phases/91-.../91-02-SUMMARY.md` ≥ 1 | ❌ → Wave 1 |
| UX-01 | 3-seed Failsafe verification passes on `*DriverSheet*,*Calendar*` ITs | IT (3-seed empirical) | `./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234` (×3 seeds 1234/5678/9999) | ✅ ITs exist; ❌ 3-seed runs not yet executed → Wave 1 |
| UX-01 | JaCoCo line coverage ≥ 88.88 % held | Coverage gate | `./mvnw verify` exits 0 with `target/site/jacoco/jacoco.csv` reporting ≥ 0.8888 LINE_RATE | already in pom.xml |
| UX-01 | SpotBugs `BugInstance` count = 0 | Static analysis gate | `./mvnw verify` exits 0 (gate is verify-bound per Phase 81) | already in pom.xml |

Closer (Plan 91-03) — **docs validation**:

| Req ID | Behavior | Test Type | Automated Command | Evidence Location |
|--------|----------|-----------|-------------------|-------------------|
| Closer | `MILESTONES.md` carries a v1.12 entry at the top | Docs assertion | `head -50 .planning/MILESTONES.md \| grep -c 'v1.12'` ≥ 1 | `.planning/MILESTONES.md:3` |
| Closer | `README.md § Test Performance` updated with v1.12 baseline pointer | Docs assertion | `grep -c 'v1.12' README.md` (in Test Performance section) ≥ 1 | `README.md` |
| Closer | `README.md` Backup section updated with v1.12 PR pointer | Docs assertion | Manual section read; `grep -c 'v1.12' README.md` overall increase | `README.md` |
| Closer | v1.12 PR body has the composite shape (REQ-ID table + narrative + numbers + CI run links) per D-07b | Manual review | `gh pr view <num> --json body --jq .body \| wc -l` ≥ 200 lines; spot-check for REQ-ID rows | GitHub remote |
| Closer | Draft → Ready-for-review flip done | API check | `gh pr view <num> --json isDraft --jq .isDraft` returns `false` | GitHub remote |

### Sampling Rate
- **Per task commit (Plan 91-02 only):** `./mvnw test -Dtest=GoogleApiExceptionMapperTest` (mapper unit) — fast, < 30 s
- **Per wave merge (Plan 91-02):** `./mvnw verify -Dit.test='*DriverSheet*,*Calendar*'` — targeted IT scope
- **Phase gate (Plan 91-03):** Full suite `./mvnw verify -Pe2e` green BEFORE `/gsd-verify-work 91` and BEFORE `/gsd-complete-milestone v1.12`. Per [[test-call-optimization]], the single final full verify is the source of truth.

### Wave 0 Gaps
- [ ] `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` — sealed base
- [ ] `src/main/java/org/ctc/dataimport/exception/{Transient,Auth,NotFound,Permission}GoogleApiException.java` — 4 final permits
- [ ] `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` — static mapper helper
- [ ] `src/test/java/org/ctc/dataimport/exception/GoogleApiExceptionMapperTest.java` — covers each category + edge cases
- [ ] CSS append to `src/main/resources/static/admin/css/admin.css` — `.error-badge` + 4 BEM modifiers
- [ ] `docs/operations/google-integration.md` — Setup / Error Categories / Troubleshooting
- [ ] `.planning/MILESTONES.md` v1.12 entry (Plan 91-03)
- [ ] `.planning/STATE.md § Baselines to Preserve` swap (Plan 91-01)
- [ ] `.planning/PROJECT.md § Key Decisions` new trend row (Plan 91-01)
- [ ] `docs/test-performance.md § PERF-06 Re-Harvest` (Plan 91-01)

## Security Domain

(`security_enforcement` is not explicitly set to `false` in `.planning/config.json` — section included.)

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Existing HTTP Basic Auth on prod/docker profiles is unchanged. UX-01's `AuthGoogleApiException` surfaces Google service-account auth failures to the operator — it does NOT modify the app's own auth path. |
| V3 Session Management | no | No session-related code change. |
| V4 Access Control | yes | The Google API integration is bound by service-account credentials configured via `google.sheets.credentials-path` (file-system-resolved). UX-01 does NOT change credential resolution. The `PermissionGoogleApiException` surfaces a 403 from Google's authz layer to the operator UI; this is a downstream-system permission error, not the app's own authorisation. |
| V5 Input Validation | yes | `IllegalArgumentException` URL-format guards in `GoogleSheetsService.extractSpreadsheetId` remain UNCHANGED (D-06 explicit). Plan 91-02 does NOT remove or relax these guards. |
| V6 Cryptography | no | No crypto code change. Service-account key handling stays identical (PEM/JSON read from file, scoped to read-only). |

### Known Threat Patterns for v1.12 + Phase 91 stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Service-account JSON key disclosure via error message | Information Disclosure | Plan 91-02 user-visible flash messages (per D-07) MUST NOT include `e.getMessage()` from auth-failure paths (Google API responses can include path fragments or principal names). Recommendation: hardcoded user-visible strings ("Authentication problem — re-link Google account"), with the underlying cause logged at ERROR but NOT echoed to the UI. |
| 403-reason inspection causes NullPointerException on unknown Google API responses | Tampering / DoS | Defensive: mapper handles `gjre.getDetails() == null` and `errors().isEmpty()` cases by defaulting to `PermissionGoogleApiException`. |
| Stack-trace echoed to user on rare unhandled error | Information Disclosure | The existing GlobalExceptionHandler at `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` handles un-caught exceptions; Plan 91-02 typed catches sit closer to the user-trigger (controller method level), so the GlobalExceptionHandler is the safety net. No new threat surface. |
| New CSS class `.error-badge--auth` carrying `background: var(--danger-bg)` is rendered with attacker-controlled string via `th:text="${errorCategory}"` | XSS | The flash key is always one of 4 hardcoded String literals set in the controller (`"TRANSIENT"`, `"AUTH"`, `"NOT_FOUND"`, `"PERMISSION"`); attackers cannot inject arbitrary values. Even so, `th:text` (NOT `th:utext`) is XSS-safe — Thymeleaf HTML-escapes by default. |
| `docs/operations/google-integration.md` accidentally documents the credentials file path with the real production path | Information Disclosure | Documentation MUST use placeholder paths (e.g., `/etc/ctc-manager/google-credentials.json`) and NOT reference the actual production secret store. |

No new SpotBugs `<Match>` entries expected. No CodeQL FP suppressions expected. If the new `GoogleApiException` hierarchy triggers `EI_EXPOSE_REP*` on Lombok-generated getters (unlikely — no Lombok on these classes), the response is a targeted `@SuppressFBWarnings({"EI_EXPOSE_REP"}, justification="...")` per CLAUDE.md SAST pattern.

## Sources

### Primary (HIGH confidence)
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-CONTEXT.md` — full read; 13 decisions locked
- `.planning/REQUIREMENTS.md` — PERF-06 + UX-01 text (lines 26-28 + 47)
- `.planning/STATE.md` — Baselines to Preserve (line 142 = swap target); Active Milestone (line 50-55); Deferred Items (lines 60-77)
- `.planning/ROADMAP.md` — Phase 91 goal + success criteria (lines 225-235)
- `.planning/PROJECT.md` — Current Milestone v1.12 + Key Decisions table (full read)
- `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-CONTEXT.md` — D-01, D-04, D-08, D-09 carry-forward
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-06-PLAN.md` — verbatim PERF-06 harvest template (Tasks 1 + 2)
- `.planning/MILESTONES.md` — v1.11 entry shape (lines 3-44); v1.12 entry inserts at top
- `docs/test-performance.md` — existing § Post-Optimization Wallclock + § CI Results (PERF-05) + § PERF-04 Testcontainers Reuse + § Test-Module-Split Decision (full read)
- `docs/operations/release-runbook.md` + `docs/operations/import-runbook.md` — runbook-shape templates (full reads)
- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` — refactor target (full read)
- `src/main/java/org/ctc/dataimport/GoogleCalendarService.java` — refactor target (full read)
- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (preview at line 66, execute at line 99) — IO wrap point
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — full read; existing catches at line 56 + 61 + 102 + 105 + 109
- `src/main/java/org/ctc/admin/controller/RaceController.java` line 187-196 — createCalendarEvent endpoint
- `src/main/java/org/ctc/domain/service/RaceCalendarService.java` — full read; consumer of GoogleCalendarService
- `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` + `BackupImportException.java` — existing typed-exception precedent (full reads)
- `src/main/resources/templates/admin/layout.html` line 85-86 — flash render insertion point
- `src/main/resources/templates/admin/driver-import.html` line 8-10 — flash render insertion point
- `src/main/resources/static/admin/css/admin.css` lines 153-163 (.alert classes) + 346-357 (.badge classes) — insertion point context
- `.github/workflows/ci.yml` line 1-18 — workflow_dispatch confirmed wired
- `.planning/codebase/TESTING.md` — full read; @Tag rules, @SpringBootTest canonical shape, Test Invocation Discipline
- `CLAUDE.md` — full project instructions

### Secondary (MEDIUM confidence)
- v1.11 PR #122 body via `gh pr view 122 --json body` — D-07b composite-body shape (verified at fetch time 2026-05-20)
- Google API client library official docs at `googleapis.dev/java/google-api-client/latest/com/google/api/client/googleapis/json/GoogleJsonResponseException.html` — `getStatusCode()`/`getDetails()` contract (CITED but NOT WebFetched live in this session — relying on training-data knowledge of the stable Google API client API)

### Tertiary (LOW confidence)
- None — all claims are sourced from direct file reads or the LOCKED CONTEXT.md.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified on classpath via pom.xml + source-file imports; sealed-class support on Java 25 is uncontroversial
- Architecture: HIGH — touch points verified by reading every refactor-target file; existing exception-handling patterns and flash-attribute pattern read in full
- Pitfalls: HIGH — Pitfalls 1-3 directly inherit Phase 86 86-06-PLAN.md learnings; Pitfalls 4-10 surfaced from direct source-read inspection
- Closer mechanics: HIGH — v1.11 PR body fetched live; v1.11 MILESTONES.md entry read in full; release/import runbooks read in full
- PERF-06 harvest: HIGH — Phase 86 PLAN is a 1:1 template
- UX-01 mapper logic: MEDIUM — `GoogleJsonResponseException` HTTP-code-to-category mapping is sourced from Google API client library docs (training-data); not WebFetched live; mapping rules for 403 reason inspection are conservative defaults

**Research date:** 2026-05-20
**Valid until:** 2026-06-20 (30 days — codebase + tooling stable; no fast-moving dependencies in this phase scope)
