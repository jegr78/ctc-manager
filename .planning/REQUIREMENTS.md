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
|---------|--------|
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

Empty initially. Populated by the roadmapper agent during phase mapping.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DRIV-01 | TBD | Pending |
| DRIV-02 | TBD | Pending |
| PERF-01 | TBD | Pending |
| PERF-02 | TBD | Pending |
| PERF-03 | TBD | Pending |
| PERF-04 | TBD | Pending |
| PERF-05 | TBD | Pending |
| PERF-06 | TBD | Pending |
| CLEAN-01 | TBD | Pending |
| UX-01 | TBD | Pending |

**Coverage (pre-roadmap):**
- v1.12 requirements: 10 total (9 must-have + 1 stretch)
- Mapped to phases: 0 (roadmapper-pending)
- Unmapped: 10 ⚠ (expected pre-roadmap)

---

*Requirements defined: 2026-05-18*
*Last updated: 2026-05-18 after initial v1.12 definition*
