# Requirements: CTC Manager v1.12 Driver-Import Gap-Closure & Test Performance Round 2

**Defined:** 2026-05-18
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Milestone v1.12 Requirements

Requirements for the v1.12 release. Each maps to a roadmap phase. Carry-forwards from v1.11 are absorbed below.

### Driver-Import Gap-Closure (DRIV)

Closes 2 v1.11-deferred debug sessions (fully diagnosed in `.planning/debug/deferred/`). Both bugs surfaced during the Phase 70 driver-import refactor and were deferred from v1.11 because that milestone was scoped to tooling/tech-debt.

- [ ] **DRIV-01**: `DriverSheetImportService.resolveTeamByShortName(shortName)` evolves to a season-aware variant `resolveTeamByShortName(shortName, SeasonPhase regularPhase)` that, on multi-match shortName collision, prefers the candidate with a `PhaseTeam(regularPhase.id, candidate.id)` entry over the parent bucket; falls back to the existing parent-precedence rule only when no candidate has a PhaseTeam in the target REGULAR phase
- [ ] **DRIV-02**: `DriverSheetImportService.buildTabPreview` skips `TEAM_NOT_IN_REGULAR_PHASE` warning emission and the PhaseTeam lookup entirely when `regularPhase.getLayout() != PhaseLayout.GROUPS`; `TabPreview` record gains a per-tab `usesGroups` flag so non-GROUPS tabs in mixed previews render `—` instead of "⚠ No group" badges; tests `DriverSheetImportServiceTest#16` and `#17` are inverted to assert the new contract (no warning for LEAGUE) and a new test asserts warning IS emitted for GROUPS with missing PhaseTeam

### Test Performance Round 2 (PERF)

Closes the v1.11 PERF-FUTURE-01 forward path. v1.11 PERF-04 accepted the ≥30 % wallclock target as an OR-branch (CI median 23:00 ≫ target ≤7m 50s; architectural blocker). v1.12 implements the 3-lever forward path documented in `docs/test-performance.md § v1.12 Forward Path` plus a test-module-split decision-point.

- [ ] **PERF-01**: `app.backup.staging-dir` resolves to a per-fork variant using the Surefire/Failsafe fork-numbering system property (`${surefire.forkNumber}` or equivalent) so backup ITs can run with Failsafe `forkCount>1C` without staging-dir races; `BackupStagingCleanup` startup listener respects the per-fork path; verified by a per-fork-dir assertion IT and a re-run of the existing backup IT suite under elevated `forkCount`
- [ ] **PERF-02**: `org.ctc.testsupport.ContextLoadCountListener` is extended to dump per-context cache-key hashes (Spring TCF `ContextCache.MergedContextConfiguration.hashCode()` or equivalent) into the existing PID-keyed `target/test-perf/context-loads-{PID}.txt` markers, enabling post-run aggregation of which cache keys fragment the cache; the aggregator includes a sample script in `docs/test-performance.md` that groups loads by hash and lists the highest-fragmentation clusters
- [ ] **PERF-03**: At least one IT cluster identified by PERF-02 fingerprinting is consolidated onto a shared `@ContextConfiguration` (new `BaseFailsafeIT` super-class or a shared `@TestConfiguration` per cluster) with no test-isolation regression and a measurable cache-key reduction recorded in `docs/test-performance.md`
- [ ] **PERF-04**: Testcontainers `~/.testcontainers.properties` reuse is wired (`testcontainers.reuse.enable=true`) and at least one MariaDB-backed IT (existing or net-new) exercises it; CI runs continue to use cold-start container (reuse disabled on CI per Testcontainers default) without regression; setup documented in `docs/test-performance.md` and README
- [ ] **PERF-05**: A `docs/test-performance.md § Test-Module-Split Decision` section is added that captures the v1.12 verdict on splitting `src/test/java/` into independent Maven modules (one of: `proceed` with extraction plan + acceptance criteria, `defer` with the explicit blockers, or `reject` with the rationale); if `proceed`, the extraction itself ships as part of this requirement and CI passes against the new module layout
- [ ] **PERF-06**: Post-implementation, the CI 5-run median is re-harvested per D-17 trigger-equivalence (5 `workflow_dispatch` runs on the v1.12 milestone PR-branch, drop min+max, median of 3), and the new baseline replaces 23:00 in `docs/test-performance.md` and in `STATE.md` Baselines section; variance must stay within the established 20 % tolerance

### Cleanup (CLEAN)

- [ ] **CLEAN-01**: The pre-existing `BackupSchemaExclusionIT.java:40` compile error (Java 25 / AssertJ generic-inference) is fixed so `./mvnw verify` exits 0 and JaCoCo CSV generation succeeds; resolution may be either a typed-witness `assertThat(set, Set.class)` style or a targeted `@SuppressWarnings("unchecked")` with rationale comment; the fix lands as the FIRST commit of phase 88 so all PERF measurements run against a clean baseline
- [ ] **CLEAN-02**: YAGNI sweep of `@Disabled` tests and conditional skips whose justifications are speculative or stale: (a) DELETE `StandingsPageGeneratorTest.givenGroupsSwissLayoutSeason_whenGeneratePerGroup_thenShowBuchholzColumn` placeholder method (Phase-62 Plan-5/6 deferral never materialized; GROUPS+SWISS fixture combination is not in `TestDataService` and is not in production scope; method body is empty — zero test value lost); (b) DELETE `DriverSheetImportServiceIT.givenPreExistingDriverNotMatchedByMatcher_whenExecuteNewDriverRow_thenReusesExistingDriver` disabled regression-fence (production code path is structurally unreachable via `DriverMatchingService.findDriver` exact-match short-circuit; the defensive `findByPsnId(psnId).orElseGet(...)` recovery is already exercised by Test #7 cross-tab same-PSN scenario; "future change that bypasses short-circuit" is YAGNI speculation — such a change would land with its own tests); (c) SIMPLIFY `AutoBackupBeforeImportFailureIT.java:198-208` by removing the `if (isWindows())` conditional and `Assumptions.assumeFalse(true, "Windows file-locking...")` skip — keep the POSIX assertion unconditional (codebase has zero other Windows-aware code, CI runs `ubuntu-latest`, dev runs darwin; reinstate the guard only if/when a Windows-dev case actually arises). Verified by `grep -rn "@Disabled" src/test/java` returning only `SiteGeneratorBaselineCaptureTest` post-CLEAN-03 absorbed, and `grep -rn "Assumptions\." src/test/java` returning 0 hits
- [ ] **CLEAN-03**: Refactor `SiteGeneratorBaselineCaptureTest` from the `@Test @Disabled("Run manually to refresh SC4 baselines")` anti-pattern (which forces the developer to "remove `@Disabled`, run, re-add `@Disabled`" on every baseline refresh and gives misleading `@Test` semantics for a maintenance task) into an explicit utility — either a `CommandLineRunner` Spring bean under `src/test/java/org/ctc/sitegen/util/SiteGeneratorBaselineRefresh.java` invoked via `./mvnw exec:java -Dexec.mainClass=...` OR a `main()`-style helper class — preserving Spring-context-bootstrap convenience. The Phase 62 Plan 0/4 baseline-capture procedure documented in `docs/testing/` (or sitegen module README) is updated to reflect the new invocation pattern. Verified: `grep -rn "@Disabled" src/test/java` returns 0 hits after CLEAN-02 + CLEAN-03 land

### Release Workflow (REL)

Closes 4-milestone-long regression in `.github/workflows/release.yml`: every milestone since v1.8 final has failed the release step with `fatal: tag 'vX.Y.0' already exists` (exit 128), so v1.8 (partial), v1.9, v1.10, and v1.11 never produced workflow-generated git tags, GitHub Releases, or `ghcr.io/jegr78/ctc-manager:vX.Y.0` Docker images. pom.xml was manually bumped from `1.8.0-SNAPSHOT` to `1.11.0-SNAPSHOT` (commit 87daec68) to work around the missing automated bump.

- [ ] **REL-01**: `.github/workflows/release.yml` is hardened against the duplicate-tag-pattern that broke 4 consecutive milestone releases: (a) `git describe --tags --abbrev=0` is replaced with `git tag --sort=-version:refname --list 'v[0-9]*.[0-9]*.[0-9]*' | head -1` (strict 3-part SemVer pattern, deterministic, ignores legacy `v1.X` short-form tags); (b) `actions/checkout@v6` is configured with `fetch-tags: true` alongside `fetch-depth: 0`; (c) the version parser defaults `PATCH` to `0` if missing and validates `MAJOR`/`MINOR` are numeric; (d) a pre-`versions:set` idempotency guard `git rev-parse "v${NEW_VERSION}^{}"` short-circuits with a clear `::error::Tag already exists` and exit 1 BEFORE the 19-minute build runs; the fix is verified by a workflow dry-run (manual `workflow_dispatch` on the v1.12 PR-branch with a synthetic `dry-run: true` input that skips push/tag/release steps but exercises the version-determination logic) AND by the next master squash-merge actually producing a v1.12.0 release artifact set
- [ ] **REL-02**: The 2 missed releases v1.10.0 (master @ `45aabfd0`) and v1.11.0 (master @ `598d1431`) are retroactively published — annotated tags pushed, GitHub Release pages generated with auto-notes, JAR artifacts attached, and Docker images `ghcr.io/jegr78/ctc-manager:1.10.0` + `:1.11.0` built+pushed; legacy short-form tags `v1.5`, `v1.6`, `v1.8`, `v1.9` are deleted (they have no `release:` commit in master history and are artefacts that confuse the SemVer-sort lookup); documented in `docs/operations/release-runbook.md`

### Documentation Conventions (DOCS)

- [ ] **DOCS-01**: CLAUDE.md "Conventions" section gains a "Skill Invocation Naming" paragraph documenting that all GSD skills are invoked via `/gsd-<name>` (dash, current canonical syntax), NOT `/gsd:<name>` (colon, deprecated — pre-2026 syntax). Verified by `grep -r "/gsd:" .planning/*.md` returning zero hits in top-level active planning files (PROJECT.md, STATE.md, ROADMAP.md, REQUIREMENTS.md, MILESTONES.md, RETROSPECTIVE.md). Archived `.planning/milestones/v*.x-*.md` files are explicitly out of scope (historical, immutable). The pre-existing initial inline-sweep that landed in commit `<v1.12-start>` cleared the 16 references found 2026-05-18 — DOCS-01 prevents regression via the CLAUDE.md convention note

### User Experience Polish (UX, stretch)

- [ ] **UX-01**: `DriverSheetImportService.preview()` and `execute()` plus the calendar-sync paths in `GoogleCalendarService` distinguish between transient errors (network timeout, 5xx) and permanent errors (invalid sheet ID, auth token expired, permissions); user-facing form errors include a category badge ("Connection problem — retry", "Authentication problem — re-link Google account", "Sheet not found — check ID") backed by a small typed-exception hierarchy; documented in `docs/operations/google-integration.md`

## Future Requirements (v1.13+)

Deferred — acknowledged in `.planning/codebase/CONCERNS.md` "Recommended Actions" but out-of-scope for v1.12.

### Storage Scaling

- **STOR-01**: S3/blob-storage backend for `app.upload-dir` to support multi-instance deployment
- **STOR-02**: Configurable storage adapter (`FileStorageService` interface + local-fs/S3 implementations) selected via Spring profile

### Observability

- **OBS-01**: Response-time dashboards for `StandingsService` and `DriverRankingService` queries on 100+ team seasons
- **OBS-02**: Wallclock baseline for `PlayoffSeedingService.calculatePlayoffBracket()` on large seasons (cache if > 5 s)

### Security Maintenance

- **SEC-01**: Annual CodeQL baseline refresh after major dependency bumps (verify the 3 suppressed FPs remain valid)

## Out of Scope

Explicit exclusions for v1.12. Prevents scope creep.

| Feature | Reason |
| ------- | ------ |
| OAuth2/OIDC | HTTP Basic Auth is sufficient for single-admin app (v1.0 Key Decision) |
| Full Pagination UI | Repository preparation only; no template rework |
| Disable OSIV | Deliberately enabled; `@EntityGraph` only optimization (CLAUDE.md Constraint) |
| Modify Flyway V1 | Checksum-protected; new schema changes are additive V8+ |
| Form Login / User Management | Over-engineered for admin tool |
| Spring Boot 4.1 / Java 26 upgrade | Not yet GA; Renovate + LTS-pin in place |
| New SAST tools (Semgrep, Snyk) | CodeQL covers the security-extended query suite; CLAUDE.md v1.11 D-02 |
| Removal of `@DirtiesContext` in backup-IT lock cluster | Latch-dependent; documented carry-forward in `docs/test-performance.md` |
| New feature work (race-result quick-edit, public-site search, ...) | This is a quality/performance milestone; feature work resumes in v1.13 |

## Traceability

Populated by the roadmapper agent (2026-05-18) — 100 % coverage across 4 phases (88-91).

| Requirement | Phase | Status |
| ----------- | ----- | ------ |
| CLEAN-01 | 88 | Pending |
| CLEAN-02 | 88 | Pending |
| CLEAN-03 | 88 | Pending |
| REL-01 | 88 | Pending |
| REL-02 | 88 | Pending |
| DRIV-01 | 88 | Pending |
| DRIV-02 | 88 | Pending |
| DOCS-01 | 88 | Pending |
| PERF-01 | 89 | Pending |
| PERF-02 | 89 | Pending |
| PERF-03 | 90 | Pending |
| PERF-04 | 90 | Pending |
| PERF-05 | 90 | Pending |
| PERF-06 | 91 | Pending |
| UX-01 | 91 | Pending (stretch — descopable to v1.13 if PERF over budget) |

**Coverage (post-roadmap):**

- v1.12 requirements: 15 total (14 must-have + 1 stretch)
- Mapped to phases: 15 (100 %)
- Unmapped: 0 ✓

---

*Requirements defined: 2026-05-18*
*Last updated: 2026-05-18 — roadmapper mapped all 10 REQ-IDs to phases 88-91; ROADMAP.md drafted, awaiting approval*
