# Phase 90: PERF Consolidation & Module-Split Decision - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Consume Phase 89's PERF-02 cache-key fingerprint data to consolidate the highest-fragmentation IT cluster onto a shared Spring TCF context (PERF-03), pre-emptively wire Testcontainers `withReuse(true)` on the two existing MariaDB-backed ITs (PERF-04), and lock the test-module-split verdict for v1.12 (PERF-05). Three sequential plans on `gsd/v1.12-driver-import-and-test-perf` per [[inline-sequential-execution]] + [[wave-pause]].

In scope:
- PERF-03 — Conservative cluster consolidation: introduce a custom composed annotation `@CtcDevSpringBootContext` (`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`) and apply it to the `db.migration.**` cluster (V3MigrationTest + V5MigrationTest + V6MigrationTest [Surefire fork] + V4MigrationSmokeIT + V7DataImportAuditMigrationIT [Failsafe fork] = 5 test classes that hold the Top-1 hash bucket `9cefac4c` per 89-03 PERF-02 output, plus any other classes the aggregator output proves share the same `WebMergedContextConfiguration` shape — see D-01). `@Tag("integration")` and `@Transactional` remain on subclasses where currently present (V4MigrationSmokeIT, V7DataImportAuditMigrationIT). `V4MigrateSeasonsToPhasesIT` is OUT of scope — uses a programmatic Flyway harness, not `@SpringBootTest`.
- PERF-03 acceptance — 3-seed Failsafe verification (1234/5678/9999) on `db.migration.**` ITs + 1 Surefire-routed seed-stable run on `db.migration.**` Tests + before/after PERF-02 cache-key aggregator delta recorded in `docs/test-performance.md § PERF-03 Cluster` + bundled 3-run local idle measurement (Wave-5) in `90-01-SUMMARY.md`.
- PERF-04 — Add `.withReuse(true)` to both existing `MariaDBContainer<>` instantiations in `BackupImportMariaDbSmokeIT` and `BackupRoundTripIT`; document the `~/.testcontainers.properties` opt-in line for developers in `docs/test-performance.md § PERF-04 Testcontainers Reuse` + a one-paragraph pointer in `README.md § Test Performance`. CI behavior unchanged (Testcontainers default disables reuse without the operator file; D-04 invariant matches Phase 89 D-14 production-safe pattern).
- PERF-05 — Defer-with-explicit-blockers verdict. `docs/test-performance.md § Test-Module-Split Decision` populated with the three blockers (TestDataService cross-boundary, IDE-friction-risk per [[clean-maven-build-authority]], no hard cumulative-effect data yet) + the re-evaluation trigger (post-PERF-06 CI median in Phase 91; v1.13 owns the next decision point).
- Wave-5 local measurement bundled into `90-01-SUMMARY.md` (PERF-03's plan), mirroring Phase 89 D-02 honest-reporting pattern: 3 `./mvnw clean verify -Pe2e` idle runs, median Maven + context-load count + JaCoCo + cache-key cluster diff vs. 89-03 Top-5 baseline. CI-authoritative re-harvest stays deferred to PERF-06 (Phase 91).

Out of scope (deferred to later phases):
- Secondary cluster consolidation (backup-exception 12 classes, admin-security 12 classes, AdminWorkflowE2E 7 classes) — Phase 86 Lesson on blind consolidation; v1.13 re-evaluates if PERF-06 CI re-harvest leaves a gap.
- Maven sub-module extraction (`ctc-manager-tests` artifact) — explicitly DEFERRED, not rejected (D-03).
- PERF-06 CI 5-run re-harvest — Phase 91.
- UX-01 Google-API typed-exception hierarchy — Phase 91 stretch.
- Production `application*.yml` changes (D-14 from Phase 89 carries forward: test-only system properties never touch production deployment).
- `CLAUDE.md` edits (test-invocation pattern unchanged; [[test-call-optimization]] still applies).

</domain>

<decisions>
## Implementation Decisions

### PERF-03 Cluster Scope

- **D-01: Conservative — `db.migration.**` cluster only (V5+V4 standout hash `9cefac4c` from 89-03 PERF-02 hand-off).** The Top-1 hash bucket reported in `89-03-SUMMARY.md § PERF-02 Top-5 cluster output` is `9cefac4c -- 29 occurrences across 29 classes (score=841)` with the `db.migration.V5MigrationTest` display marker; the Top-4 hash `f524774b -- 10 occurrences across 10 classes (score=100)` carries the `db.migration.V4MigrationSmoke` marker. The "29 + 10 = 39 occurrences" figure in `89-03-SUMMARY.md § Phase 90 closure` is the aggregator's `beforeTestClass` event count, NOT a unique-class count — it overcounts because every IT method that triggers a `@DirtiesContext`-style rebuild registers another event against the same hash. The actual `db.migration` test class count is 5 (V3MigrationTest, V5MigrationTest, V6MigrationTest, V4MigrationSmokeIT, V7DataImportAuditMigrationIT; V4MigrateSeasonsToPhasesIT excluded per its programmatic-Flyway-harness shape). The 24+ extra "occurrences" reported by the aggregator suggest additional classes outside `db.migration` are landing in the same hash bucket (likely other `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` Surefire-routed Tests). The planner MUST re-run `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` against a fresh `target/test-perf/` (post-`./mvnw clean verify -Pe2e`) and list every class in the `9cefac4c` and `f524774b` buckets in `90-01-SUMMARY.md` before refactoring. The acceptance is "every class currently in those two buckets now wears `@CtcDevSpringBootContext`", not "exactly 39 classes". REJECTED: Moderate (+1 secondary cluster) and Aggressive (all top-4) — Phase-86 Lesson on blind consolidation re-introducing fragmentation in the other direction; v1.13 owns the next consolidation pass if PERF-06 data justifies it.

### PERF-03 Mechanism

- **D-02: Custom composed annotation `@CtcDevSpringBootContext` packs `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` into one annotation, applied to every class identified in D-01.** Subclasses keep `@Tag("integration")` (Failsafe-routed ITs only) and `@Transactional` (V4MigrationSmokeIT only) on the test class itself — the composed annotation does NOT include `@Tag`/`@Transactional` because their presence varies per cluster member (Surefire `*Test.java` files are untagged, Failsafe `*IT.java` files are `@Tag("integration")`). The annotation lives at `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java`; meta-annotation pattern via Spring's `@AliasFor` follows established Spring TCF idiom (no custom resolver required — `MergedAnnotations` walks composed annotations natively, producing an identical `MergedContextConfiguration` hash for every class that wears it). REJECTED: abstract `BaseDevSpringBootIT` super-class (forces 5+ classes to `extends` + `@Transactional`-mix would force Inheritance-splitting), shared `@TestConfiguration` via `@Import` (adds beans but does NOT change the `MergedContextConfiguration` cache-key components — wrong tool for cache-key reduction).

- **D-02b: Cache-key reduction proof = aggregator diff before/after.** Plan 90-01 runs `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` twice: (1) once on the v1.12 PR-branch head before the refactor (this re-uses the existing Phase-89-produced fingerprint files OR a fresh run; planner picks), (2) once after the refactor lands. The Top-5 output sections are copied into `90-01-SUMMARY.md` side-by-side; the acceptance is that the `db.migration` cluster's hash count drops (the two distinct `9cefac4c` + `f524774b` keys collapse into a single key), and the per-class display markers in the consolidated bucket include every class identified by D-01. No "X% wallclock reduction" gate — D-05 (Phase 89 D-02 carry-forward) keeps measurement honest-and-observational.

### PERF-03 Test-Isolation Verification

- **D-03: 3-seed Failsafe verification on `db.migration.**` ITs after consolidation.** Command pattern carried forward from Phase 89 D-13:
  ```
  ./mvnw verify -Dit.test='db.migration.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}
  ```
  One run per seed; all three must pass. Plus a single Surefire-routed seed-stable run (`./mvnw test -Dtest='db.migration.**'`) to fence Surefire-side regression. Reason: Surefire fork shares the same `@CtcDevSpringBootContext` cache key with Failsafe — but each fork holds its own context cache, so the verification matters per-fork. Test isolation risk in this cluster is low (Flyway runs migrations on `@SpringBootTest` startup; `@Transactional` rollback on V4MigrationSmokeIT keeps DB state clean), but [[no-flaky-dismissal]] requires empirical proof, not assumption.

### PERF-04 Testcontainers Reuse

- **D-04: Add `.withReuse(true)` to the two existing `MariaDBContainer<>` declarations in `BackupImportMariaDbSmokeIT` and `BackupRoundTripIT`; CI behavior unchanged.** Both ITs sit behind `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` gates and are skipped on CI by default (CI runs `-Pe2e` but does not set `docker.available=true` — see Phase 77 D-05). When the gate IS set on a developer machine with `~/.testcontainers.properties` containing `testcontainers.reuse.enable=true`, the container survives JVM exit and re-attaches on the next run (~5-7 s saved per IT per session). When the file is absent, Testcontainers ignores `.withReuse(true)` and cold-starts as today. Zero impact on CI, opt-in for developers. README.md `§ Test Performance` adds a one-paragraph pointer; `docs/test-performance.md § PERF-04 Testcontainers Reuse` documents the `~/.testcontainers.properties` opt-in line plus the verification command (`docker ps` shows a long-lived `testcontainers-ryuk-…` companion + a labelled `mariadb:11` instance between consecutive runs).

- **D-04b: Production `application*.yml` and `BackupStagingCleanup` stay untouched.** Same invariant as Phase 89 D-14: test-only configuration never bleeds into production deployment.

### PERF-05 Test-Module-Split Verdict

- **D-05: Defer with explicit blockers; v1.13 owns the next decision-point.** Acceptance form is the explicit `defer` branch of REQUIREMENTS.md PERF-05. `docs/test-performance.md § Test-Module-Split Decision` writes:
  - **Verdict:** Defer (v1.12 does not extract `src/test/java/` into a separate Maven module).
  - **Blocker 1 — TestDataService cross-boundary:** `org.ctc.admin.TestDataService` lives in `src/main/java` (`@Profile({"dev","local"})` per v1.11 QUAL-02) but seeds test data; a clean test-module split would force either (a) duplicating fixtures into the test module, (b) keeping `TestDataService` in main with a circular dep on the test module, or (c) a third "fixtures" module — all three options are net negative ergonomics.
  - **Blocker 2 — IDE-friction risk:** Phase 80 deferred-items.md (2026-05-16) and [[clean-maven-build-authority]] document IDE-JDT-cache pathologies that Maven restructures historically amplify; the single-module project has been stable through 87 phases.
  - **Blocker 3 — No hard cumulative-effect data:** Phase 89 Wave-4 already landed -10.4 % local; PERF-04 (Testcontainers reuse) is dev-only; the authoritative CI cumulative-effect is unknown until PERF-06 (Phase 91) re-harvests. Splitting modules before knowing whether the cumulative effect closes the 23:00 vs 7:50 gap risks substantial restructure for marginal gain.
  - **Re-evaluation trigger:** v1.13 milestone planning consults `docs/test-performance.md § Test-Module-Split Decision`; if PERF-06 CI median lands materially below 23:00 but still above the 7:50 historical gate AND no other architectural lever surfaces, the v1.13 milestone-discuss workflow re-opens the decision against a hard data baseline.
  REJECTED: `reject` outcome (closes optionality for v1.13; not justified absent data); `proceed` outcome (substantial v1.12 scope expansion + the 3 blockers above + Phase-86-style overshoot likely).

### Plan Structure

- **D-06: Three plans, sequential inline.** Mirror Phase 89 D-01 pattern:
  - **Plan 90-01 — PERF-03 (meta-annotation + cluster refactor + 3-seed verification + Wave-5 local measurement + cache-key diff).** Highest-risk plan (annotation-refactor across the Top-1 cache cluster); goes first. Bundles the local 3-run idle measurement into its SUMMARY (D-07).
  - **Plan 90-02 — PERF-04 (`.withReuse(true)` on both MariaDB containers + README + docs).** Lowest-risk plan; small surface area; gated behind `docker.available=true`.
  - **Plan 90-03 — PERF-05 (`docs/test-performance.md § Test-Module-Split Decision` populated with defer-verdict + 3 blockers + re-evaluation trigger).** Pure docs plan; no code change.
  Sequential per [[wave-pause]] + [[inline-sequential-execution]]; user-feedback pause after each plan merge. REJECTED: 2-plan bundle (PERF-04+PERF-05 in one commit obscures forensics — code change + docs change should not mix) and single-plan everything (atomic-revert hostile, SUMMARY would mix three independent themes).

### Measurement Cadence

- **D-07: Wave-5 local idle measurement bundled into Plan 90-01 SUMMARY.** 3 `./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` runs per Phase 86 D-09 idle protocol. Records: Maven `Total time` median, bash `real` median, JaCoCo line coverage ≥ 88.88 % (Phase 89 baseline), context-load count (Phase 89 baseline median 55), aggregator Top-5 cluster diff before/after. Delta computed vs. Phase 89 Wave-4 09:19 median. NO hard local wallclock gate (mirrors Phase 89 D-02 honest-reporting). PERF-06 (Phase 91) remains the authoritative CI re-harvest against the 23:00 v1.11 baseline.

### Quality Gates (carry-forward from Phase 89 D-15)

- **D-08: Standard gates apply, no tightening, no loosening.** JaCoCo line coverage ≥ 88.88 % (v1.11 baseline; Phase 89 Wave-4 held 0.8902). SpotBugs `BugInstance` count = 0 (blocking). CodeQL gate-step exit 0 on PR HEAD SHA. `EXPORT_ORDER` = 24 entities. `BackupSchema.SCHEMA_VERSION` = 1. No new SpotBugs `<Match>` entries expected — the new `@CtcDevSpringBootContext` annotation should not surface `EI_EXPOSE_REP*` or `DM_DEFAULT_ENCODING` patterns; if it does, targeted `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST pattern.

### Production Behavior

- **D-09: No production code touched in Phase 90.** Scope is purely test-shape (annotation refactor + Testcontainers test-IT config + docs). `src/main/java/**` is git-clean across all three plans (assertion in each Plan-N SUMMARY). The PERF-03 meta-annotation lives under `src/test/java/org/ctc/testsupport/` (alongside `ContextLoadCountListener`, `ContextCacheKeyFingerprintListener`, `SitegenTestDir`).

### Claude's Discretion

- Exact wording of `docs/test-performance.md § PERF-03 Cluster` prose and Top-5 diff table format — planner picks the shape that reads well alongside the existing § PERF-02 Forensics section.
- Whether the cache-key aggregator runs twice from scratch (re-run `./mvnw clean verify -Pe2e` to produce fresh `target/test-perf/` fingerprint sidecars before AND after the refactor) OR re-uses the most recent Phase-89 Wave-4 fingerprint output for the "before" snapshot — planner picks based on freshness-vs-runtime trade-off; the "after" snapshot MUST be from a post-refactor run.
- Whether `@CtcDevSpringBootContext` lives at `org.ctc.testsupport.CtcDevSpringBootContext` (canonical testsupport location) or under a sub-package like `org.ctc.testsupport.context` — planner picks; default is the canonical location.
- Whether to include a defensive `@MustBeClosed`-style Javadoc on `@CtcDevSpringBootContext` warning future authors NOT to add `@DirtiesContext` to subclasses (which would defeat the cache-key consolidation) — planner picks based on whether existing CONVENTIONS.md guidance suffices.
- `docs/test-performance.md § PERF-04 Testcontainers Reuse` exact paragraph wording within the constraints (D-04: opt-in line, verification command, zero-CI-impact framing).
- The post-PERF-03 fingerprint sidecar handling — keep them as Phase-90 forensic evidence in `.test-perf-logs/90-01-wave5-run-{1,2,3}/` (mirroring Phase 89 Wave-4 evidence retention) OR allow `target/` to wipe them — planner picks.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 90: PERF Consolidation & Module-Split Decision" — goal, Depends-on (Phase 89), Requirements (PERF-03, PERF-04, PERF-05), Success Criteria 1-5
- `.planning/REQUIREMENTS.md` lines 22-25 — PERF-03, PERF-04, PERF-05 full requirement text
- `.planning/PROJECT.md` § "Current Milestone: v1.12 Driver-Import Gap-Closure & Test Performance Round 2" — Phase-90 forward-path framing; § "Key Decisions" — Phase 86 PERF-04 OR-branch precedent
- `.planning/STATE.md` § "Active Milestone — v1.12" + § "Baselines to Preserve" (JaCoCo ≥ 88.88 %, CI E2E median 23:00, SpotBugs 0, CodeQL exit 0, `EXPORT_ORDER` 24, SCHEMA_VERSION 1, Flyway V1-V7 immutable)

### Phase 89 Hand-off (PRIMARY INPUT)

- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-03-SUMMARY.md` § "PERF-02 Top-5 cluster output (Phase 90 PERF-03 hand-off)" — Top-1 hash `9cefac4c` (V5MigrationTest cluster), Top-4 hash `f524774b` (V4MigrationSmokeIT cluster). LOAD-BEARING for PERF-03 cluster identification (D-01).
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-CONTEXT.md` — D-07/D-08 (PERF-02 listener architecture + sidecar marker format), D-10 (`Integer.toHexString(mcc.hashCode())` 8-char hex hash), D-13 (3-seed Failsafe verification command pattern), D-14 (production yml + BackupStagingCleanup invariant — carries to D-09 here), D-15 (quality-gate baseline — carries to D-08 here)
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-02-SUMMARY.md` — `ContextCacheKeyFingerprintListener` implementation + spring.factories registration; `scripts/test-perf/aggregate-fingerprints.sh` shape
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-01-SUMMARY.md` — Per-fork system-property pattern (informs whether new ITs in this phase need similar treatment — answer is no, PERF-03's refactor changes annotations not paths)

### Phase 86 Anchor & Lessons

- `docs/test-performance.md` (entire file) — § PERF-02 Forensics (aggregator usage + sample output), § Post-Optimization Wallclock (Wave 4) (Phase 89 baseline 09:19 / 55 context loads), § v1.12 Forward Path (Lever 2 description anchors PERF-03; Lever 3 anchors PERF-04; "v1.12 forward path" sentence frames PERF-05 module-split as next-tier lever), § CI Results (PERF-05) (23:00 v1.11 baseline)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md` § "v1.12 Forward Path" — Lever 2 risk text ("Risk of accidentally widening the cache surface and re-introducing the shared-singleton mutation issues that Plan 02 fixed; requires per-fork context fingerprinting tool that doesn't exist yet — extend ContextLoadCountListener to dump cache-key hashes") is the canonical Phase-86 Lesson that D-01 + D-02 must respect
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-02-SUMMARY.md` — Plan-02 cache-key-fragmentation lesson (per-class `@DynamicPropertySource` split one shared key into seven). PERF-03's targeted consolidation prevents the same regression in the reverse direction (collapse rather than fragment).

### Code Surface (PERF-03 touchpoints)

- `src/test/java/db/migration/V3MigrationTest.java` (Surefire, `@SpringBootTest + @ActiveProfiles("dev")`) — PERF-03 refactor target; 2 annotations swap to 1
- `src/test/java/db/migration/V5MigrationTest.java` (Surefire) — Top-1 cache-key holder per 89-03; same refactor
- `src/test/java/db/migration/V6MigrationTest.java` (Surefire) — same refactor
- `src/test/java/db/migration/V4MigrationSmokeIT.java` (Failsafe, `@Tag("integration") + @Transactional` preserved) — refactor swaps 2 annotations for 1; `@Tag + @Transactional` stay on the class
- `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` (Failsafe, `@Tag("integration")` preserved) — same shape as V4MigrationSmokeIT minus `@Transactional`
- `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` — OUT of scope (programmatic Flyway harness, no `@SpringBootTest`)
- `src/test/java/org/ctc/testsupport/` — new `CtcDevSpringBootContext.java` lives here (D-02; alongside `ContextLoadCountListener`, `ContextCacheKeyFingerprintListener`, `SitegenTestDir`)
- Any additional classes the planner identifies via D-02b aggregator output as sharing hash `9cefac4c` or `f524774b` — refactor scope per D-01

### Code Surface (PERF-04 touchpoints)

- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` lines (search for `new MariaDBContainer<>("mariadb:11")`) — add `.withReuse(true)`
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` lines (search for `new MariaDBContainer<>("mariadb:11")`) — add `.withReuse(true)`
- `README.md` § "Test Performance" — extend with PERF-04 opt-in pointer paragraph
- `docs/test-performance.md` — new § "PERF-04 Testcontainers Reuse" (D-04 details)

### Code Surface (PERF-05 touchpoints)

- `docs/test-performance.md` — new § "Test-Module-Split Decision" (D-05 full content)
- NO code change in PERF-05; doc-only plan

### Aggregator Tooling

- `scripts/test-perf/aggregate-fingerprints.sh` (executable) — already shipped in Phase 89; consumed by PERF-03 for before/after cluster diff (D-02b)
- `target/test-perf/context-loads-{PID}-fingerprints.txt` — PERF-02 sidecar marker format (Phase 89 D-08); D-02b consumes the cluster output extracted by the aggregator

### Testing & Build Conventions

- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" — `@Tag` on subclass, NOT on the composed annotation (`@CtcDevSpringBootContext` does NOT include `@Tag`); § "Integration Testing" — `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` is the canonical IT shape this phase consolidates
- `.planning/codebase/TESTING.md` § "Test Invocation Discipline" + CLAUDE.md "Test-Aufrufe optimieren" — single final `./mvnw verify -Pe2e` per phase plus targeted `-Dit.test=db.migration.**` 3-seed runs
- `CLAUDE.md` § "Static Analysis" — targeted `@SuppressFBWarnings` only (D-08)
- `CLAUDE.md` § "CodeQL SAST" — 3-layer FP suppression invariant if any new finding lands

### Memory Cross-References

- [[clean-maven-build-authority]] — drives PERF-05 D-05 Blocker 2
- [[wave-pause]] + [[inline-sequential-execution]] — drive D-06 sequential-three-plans pattern
- [[no-flaky-dismissal]] — drives D-03 empirical 3-seed verification
- [[test-call-optimization]] — drives D-08 standard-gates-only stance

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`scripts/test-perf/aggregate-fingerprints.sh`** (Phase 89, executable, shellcheck-clean) — consumed by D-02b for before/after cluster-diff; usage `aggregate-fingerprints.sh target/test-perf 10` produces Top-N output.
- **`ContextCacheKeyFingerprintListener`** (`src/test/java/org/ctc/testsupport/`, registered via `src/test/resources/META-INF/spring.factories`) — Phase 89 already wires per-context fingerprint capture; PERF-03 needs zero new instrumentation, just consumes the existing sidecar output.
- **3-seed Failsafe verification pattern** — Phase 89 D-13 + Phase 86 precedent (`-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}`). Direct copy-forward to D-03.
- **Phase 86 D-09 idle protocol** — `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` × 3, logs to `.test-perf-logs/`. Direct copy-forward to D-07 Wave-5 measurement.
- **Existing `MariaDBContainer<>("mariadb:11")` declarations** (`BackupImportMariaDbSmokeIT.java`, `BackupRoundTripIT.java`) — PERF-04 D-04 surface; both use the static `@Container` + `@DynamicPropertySource` shape, both are gated behind `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` so CI is unaffected.
- **`@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")`** — the canonical Spring-context IT shape across 106 `@SpringBootTest`-bearing test files (`grep -lc "@SpringBootTest" src/test/java | wc -l` = 106). D-01 + D-02 only touch the 5 `db.migration` classes; the remaining 100+ are NOT part of this phase but the composed annotation could be applied to them in v1.13 if PERF-06 data justifies wider sweep.

### Established Patterns

- **Composed-annotation idiom in Spring TCF** — Spring's `MergedAnnotations` walks composed annotations natively; no custom resolver or `@AliasFor` ceremony required as long as the meta-annotation declares `@SpringBootTest(classes = ...) + @ActiveProfiles(...)` directly on its body and is itself `@Retention(RUNTIME) + @Target(TYPE)`. Cache-key reduction works because `MergedContextConfiguration.hashCode()` (the Spring TCF cache bucket function — Phase 89 D-10) treats two test classes wearing the composed annotation as producing identical merged configs, so they collide into a single hash.
- **`@DirtiesContext` discipline** — Phase 86 D-04 + Phase 89 carries-forward: per-method `@DirtiesContext` is acceptable on latch-dependent ITs only. None of the 5 `db.migration` cluster members use `@DirtiesContext`; adding one would defeat D-01's consolidation goal.
- **`testsupport` package convention** — all cross-cutting test infrastructure (`ContextLoadCountListener`, `ContextCacheKeyFingerprintListener`, `SitegenTestDir`) lives in `src/test/java/org/ctc/testsupport/`. `CtcDevSpringBootContext` follows that convention.
- **`docs/test-performance.md` append-only structure** — each phase adds new `§ ...` sections without rewriting earlier sections. PERF-03 → `§ PERF-03 Cluster`, PERF-04 → `§ PERF-04 Testcontainers Reuse`, PERF-05 → `§ Test-Module-Split Decision`.

### Integration Points

- **Spring TCF context cache (`org.springframework.test.context.cache.DefaultContextCache`)** — D-01 + D-02 reduce cache-key cardinality by making 5 distinct `MergedContextConfiguration` instances collapse into one shared bucket. Per-fork cache (Surefire holds its own, Failsafe `default-it` holds another — Phase 89 D-11 elevated `default-it` `forkCount=2`) means the consolidation pays off in both forks independently.
- **`META-INF/spring.factories`** — no edit needed; `ContextCacheKeyFingerprintListener` is already registered (Phase 89 D-07).
- **pom.xml Surefire/Failsafe config** — no edit needed; D-03 verification uses existing `-Dit.test='db.migration.**' -Dsurefire.runOrder=random` pattern.
- **`~/.testcontainers.properties` operator file** — D-04 documents the opt-in line `testcontainers.reuse.enable=true`; the file itself is per-developer-machine, never checked in, never CI-relevant.
- **`docs/test-performance.md` reader contract** — README.md § "Test Performance" already points at this file (Phase 89 Plan 89-03); PERF-04 + PERF-05 sections extend the surface without touching the README pointer.

</code_context>

<specifics>
## Specific Ideas

- **D-01 hash-bucket-population audit MUST land before refactor.** The planner's first task in Plan 90-01 is to re-run `scripts/test-perf/aggregate-fingerprints.sh target/test-perf 10` against a fresh `target/test-perf/` and enumerate EVERY class display marker in buckets `9cefac4c` and `f524774b`. The refactor scope = that enumerated list (not a hardcoded "5 classes" assumption). Reason: the 89-03 aggregator output truncates display markers at 200 chars; the underlying bucket may include classes outside `db.migration.**` (e.g. Surefire-routed `@SpringBootTest + @ActiveProfiles("dev")` tests in other packages). The acceptance is "every class currently in those buckets now wears `@CtcDevSpringBootContext`".
- **D-04 README paragraph anchor.** README.md § "Test Performance" already points at `docs/test-performance.md`; PERF-04's README addition is a single line under that existing section, e.g.: "Developers can enable Testcontainers MariaDB reuse by setting `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` — see [Test Performance § PERF-04 Testcontainers Reuse](docs/test-performance.md#perf-04-testcontainers-reuse) for the verification protocol." Exact wording is planner's discretion (D-04).
- **D-05 verdict prose.** The `docs/test-performance.md § Test-Module-Split Decision` section opens with a one-sentence verdict line ("**Verdict (v1.12):** Defer — re-evaluate in v1.13 against PERF-06 CI re-harvest baseline.") followed by the three numbered blockers, the re-evaluation trigger, and a "Why not reject?" paragraph that captures the optionality-preservation rationale (this phase's decision must be re-openable in v1.13; rejecting now would foreclose it).
- **Wave-5 evidence retention** — `.test-perf-logs/90-01-wave5-run-{1,2,3}.log` mirrors Phase 89 Wave-4 evidence directory shape. Plan 90-01 SUMMARY references the log directory but does not require its contents to be committed (gitignored under `.test-perf-logs/`).

</specifics>

<deferred>
## Deferred Ideas

- **Secondary cluster consolidation** — backup-exception (hash `499c01dd`, 12 classes), admin-security (`2cb78737`, 12 classes), AdminWorkflowE2E (`5ff2b420`, 7 classes). Each could wear a separate composed annotation OR could be folded under `@CtcDevSpringBootContext` if their `@ActiveProfiles` match. v1.13 re-evaluates against PERF-06 CI data; not in Phase 90 scope per D-01 (Conservative).
- **Maven sub-module extraction** (`ctc-manager-tests` artifact) — explicitly DEFERRED via PERF-05 D-05; v1.13 owns the next decision-point.
- **Wider `@CtcDevSpringBootContext` adoption** — the composed annotation could replace `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` across all ~100 IT classes that use the canonical shape. Out of scope for Phase 90 (D-01 conservative); could ship as a "great-easy-fix" sweep in v1.13 cleanup.
- **`@CtcLocalSpringBootContext` sister annotation** — for `@ActiveProfiles("local")` consumers (e.g., `BackupImportMariaDbSmokeIT`, `BackupRoundTripIT`). Out of scope; only useful if a future phase identifies a local-profile cache-key cluster.
- **PERF-06 CI authoritative re-harvest** — Phase 91 owns it (5 `workflow_dispatch` runs on milestone PR-branch, median of 3 per D-17). Phase 90's local Wave-5 (D-07) is observational only.
- **UX-01 Google-API typed-exception hierarchy** — Phase 91 stretch.
- **PERF-04 CI-side reuse enabling** — explicitly NOT in scope (D-04 invariant: CI behavior unchanged). A future phase could investigate `testcontainers.reuse.enable=true` on GitHub-hosted runners, but the cold-start cost is per-job (one container per workflow run) and savings would be small relative to the 23:00 CI median.

### Reviewed Todos (not folded)

None — `gsd-sdk query todo.match-phase 90` returned 0 matches.

</deferred>

---

*Phase: 90-PERF Consolidation & Module-Split Decision*
*Context gathered: 2026-05-19*
