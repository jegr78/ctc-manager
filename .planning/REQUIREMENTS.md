# Requirements: CTC Manager v1.11 Tooling Infrastructure & Tech-Debt Sweep

**Defined:** 2026-05-16
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## Milestone v1.11 Requirements

Requirements for the v1.11 release. Each maps to a roadmap phase.

### OpenRewrite (REWR)

- [x] **REWR-01**: Developer can invoke `./mvnw -Prewrite rewrite:dryRun` to preview recipe-driven changes against `src/main/java/` without modifying source files
- [x] **REWR-02**: Developer can invoke `./mvnw -Prewrite rewrite:run` to apply approved recipes to source files, producing a reviewable diff
- [x] **REWR-03**: The `rewrite-maven-plugin` is wired in `pom.xml` under `<build><plugins>` with no `<executions>` binding, so it never runs during the default `verify` lifecycle
- [x] **REWR-04**: A project-level `rewrite.yml` activates the `CommonStaticAnalysis` recipe pack and explicitly excludes `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` (the project is already on Boot 4.0.6)
- [x] **REWR-05**: The `CommonStaticAnalysis` recipe pack is applied once as a one-shot cleanup commit on the v1.11 branch with the resulting diff reviewed against Lombok-generated false positives in `org.ctc.domain.model.*`
- [x] **REWR-06**: README "Development" section documents the OpenRewrite invocation pattern (dryRun then run) and the deliberate decision to keep it developer-invoked

### Static Analysis Gate (STAT)

- [ ] **STAT-01**: A project-root `lombok.config` exists with `lombok.addLombokGeneratedAnnotation = true` and `lombok.extern.findbugs.addSuppressFBWarnings = true` so SpotBugs skips Lombok-generated code
- [ ] **STAT-02**: `spotbugs-maven-plugin` is wired into the `verify` phase after the existing JaCoCo execution in `pom.xml`, configured with `<effort>Max</effort>` and `<threshold>Default</threshold>`
- [ ] **STAT-03**: `findsecbugs-plugin` is registered as a SpotBugs plugin dependency so 144 Spring Security-aware bytecode patterns participate in the gate
- [ ] **STAT-04**: A `config/spotbugs-exclude.xml` filter exists with documented suppressions for any intentional patterns (e.g., `MatchdayForm` builder, SSRF blocklist `Set.contains` sanitizer)
- [ ] **STAT-05**: The SpotBugs gate is wired in two atomic commits — first as report-only (`<goal>spotbugs</goal>`) for baseline inventory, then upgraded to blocking (`<goal>check</goal>`) once `config/spotbugs-exclude.xml` covers triaged violations
- [ ] **STAT-06**: `./mvnw verify` fails the build when a new HIGH-priority SpotBugs violation is introduced (verified via a deliberate test-case violation in a throwaway branch)
- [ ] **STAT-07**: CLAUDE.md "Conventions" section gains a paragraph documenting the SpotBugs gate, the suppression-file workflow, and the lombok.config invariant

### Backup Cleanup (BACK)

- [x] **BACK-01**: `BackupSchema.SCHEMA_VERSION` remains at `1` throughout all 12 REVIEW.md fixes — verified by a guard test that asserts the constant value and the 24-entity `EXPORT_ORDER` size
- [x] **BACK-02**: All 12 Phase-75 REVIEW.md Info/Warning items (WR-01..WR-08, IN-01..IN-04) are resolved with one atomic commit per item, each commit referencing the REVIEW.md ID in the message
- [x] **BACK-03**: `restoreOneTable` reads each entity-data ZIP entry once (single streaming pass) instead of opening the ZIP 24× — verified by an IT measuring the ZipEntry-open count
- [x] **BACK-04**: `BackupRoundTripIT` and `BackupImportRollbackIT` both still pass on H2 and MariaDB after every commit in the cleanup sequence
- [x] **BACK-05**: `BackupRoundTripIT` is extended to assert per-entity row counts for ALL 24 entities (not just Race + SeasonDriver + Team) so future cleanup work cannot silently drop rows

### Quality and Polish Sweep (QUAL)

- [x] **QUAL-01**: Driver-detail Season-Assignment chips render in ascending year order, enforced by an explicit `ORDER BY year ASC` on `Driver.seasonAssignments` (verified by a domain repository IT and a Playwright smoke test)
- [x] **QUAL-02**: `DevDataSeeder` is `@Profile({"dev", "local"})` (widened from `dev`-only) so the live-MariaDB UAT bootstrap on `local,demo` no longer requires a separate Saison-2023 fixture path
- [x] **QUAL-03**: `SeasonController.generateMatchdays` exposes a per-group matchday-generation UI affordance for GROUPS-layout phases (no more hardcoded `groupId=null` at `SeasonController.java:251`), verified by a Playwright E2E test on Season 2023
- [x] **QUAL-04**: `StandingsController.java:139` lazy collection access is refactored into an explicit service-layer call that returns a fully-resolved view object (no OSIV-only lazy collection traversal in controller code)
- [x] **QUAL-05**: UAT-02 (legacy season visual smoke against real pre-V4 production data) is executed against the next production deploy and the result recorded in the milestone-audit artifact

### Renovate (DEPS)

- [x] **DEPS-01**: A root-level `renovate.json` exists with `enabledManagers: ["maven", "github-actions", "dockerfile"]` covering `pom.xml`, workflow `uses:` clauses, and Dockerfile base images
- [x] **DEPS-02**: The Mend Renovate GitHub App is installed against `jegr78/ctc-manager` and produces at least one onboarding PR
- [x] **DEPS-03**: `renovate.json` includes a packageRule that pins `com.google.guava:guava` to `-jre` classifier variants only (regex `allowedVersions`) so the Java 25 VarHandle path is preserved
- [x] **DEPS-04**: `renovate.json` includes a packageRule that explicitly DISABLES auto-updates for `org.thymeleaf:thymeleaf` (3.1.5.RELEASE pin is a CVE-2026-40478 mitigation that must not silently roll forward)
- [x] **DEPS-05**: `renovate.json` constrains `java.version` proposals to LTS-only patterns (`^(?:11|17|21|25|29)`) so Renovate never proposes Java 26/27/28 that would misalign with `eclipse-temurin:25-noble` Dockerfile pins
- [x] **DEPS-06**: `renovate.json` groups Spring Boot starters (`spring-boot-*`), Spring Security (`spring-security-*`), Google API clients (`google-api-*`), and Testcontainers (`testcontainers-*`) into single grouped PRs to reduce PR noise
- [x] **DEPS-07**: `renovate.json` enables automerge for `patch` updates that pass CI but requires manual review for `minor` and `major` (especially OpenRewrite recipe-pack bumps, which trigger manual migration runs)
- [x] **DEPS-08**: `dockerfile-noble-pin-guard` CI job continues to pass after Renovate's first Dockerfile-bump PR is opened (proves Renovate respects the suffix-only pin)

### CodeQL SAST (SAST)

- [x] **SAST-01**: A standalone `.github/workflows/codeql.yml` workflow runs CodeQL v4 against `language: java-kotlin` on push to master, pull request, and weekly cron
- [x] **SAST-02**: The CodeQL workflow uses a manual `./mvnw compile -DskipTests` build step (not autobuild) so Lombok annotation processing + Playwright dependencies do not break the build
- [x] **SAST-03**: The CodeQL workflow declares `permissions: { security-events: write, contents: read, actions: read }` at the JOB level only (not the workflow level)
- [x] **SAST-04**: First-run findings are triaged into one of three buckets — true positive (fixed), known false positive (suppressed via `.github/codeql/codeql-config.yml` `query-filters` + `// CodeQL FP: <rule-id>` source marker — the originally anticipated `// codeql[...]` directive does not exist in CodeQL Code Scanning per RESEARCH C-02), or accepted risk (documented in `docs/security/sast-acceptance.md`)
- [x] **SAST-05**: The SSRF blocklist (`FileStorageService.storeFromUrl`), ZIP-Slip defenses (`BackupArchiveService.assertEntrySafe`, `BackupImportService.restoreOneTable`), and BCrypt-N/A (no `PasswordEncoder` bean exists — TRACKED DEVIATION per CONTEXT D-05) are explicitly classified per SAST-04 with linked CodeQL alert IDs (or "N/A (filtered)" when `query-filters` excludes the rule before alert upload)
- [x] **SAST-06**: CodeQL findings appear in the GitHub Security tab and the workflow gates PR merges when a new HIGH or CRITICAL finding is introduced (verified by injecting a `java/sql-injection` pattern on throwaway branch `throwaway/sast-06-validation` in draft PR #128 — workflow run #25996558384 exited 1 with `::error::` annotation; PR + branch + alert cleaned up post-verification)

### Test Performance / Wallclock (PERF)

- [ ] **PERF-01**: Every `@DirtiesContext` usage in `src/test/java/` is audited and either removed (after random-order verification) or retained with an explanatory comment naming the specific shared state that requires fresh context (e.g., `ImportLockService` singleton lock state)
- [ ] **PERF-02**: A diagnostic logging pass counts unique Spring `ApplicationContext` initialisations during `./mvnw verify -Pe2e` and the baseline + post-optimization counts are recorded in `docs/test-performance.md`
- [ ] **PERF-03**: At least one repository-only IT is converted from full `@SpringBootTest` to `@DataJpaTest` (slice) without losing assertion coverage, demonstrating the pattern for future conversions
- [ ] **PERF-04**: `./mvnw verify -Pe2e` wallclock is reduced by ≥30% versus the v1.10 baseline of 11m 11s (target ≤7m 50s on the same hardware) OR the architectural blocker is documented with the specific constraint (e.g., MariaDB Testcontainers cold-start) and a forward path for v1.12
- [ ] **PERF-05**: The improved wallclock is verified on CI (GitHub Actions runner) over 3 consecutive runs to filter cold-cache noise; the median is recorded as the new baseline

### Nyquist VALIDATION Closure (VAL)

- [x] **VAL-01**: Approved `*-VALIDATION.md` files exist for phases 72, 73, 74, 75, 76, and 79 with status `approved` in the frontmatter
- [x] **VAL-02**: New `*-VALIDATION.md` files are created for phases 71 (Spring Boot 4.0.6 upgrade) and 78 (Docker noble pin), reaching `approved` status
- [x] **VAL-03**: `/gsd:validate-phase` is executed against each of the 8 phases (71-76, 78, 79) and the produced gap-coverage tests are committed atomically per phase
- [x] **VAL-04**: STATE.md "Deferred Items" no longer lists the Nyquist VALIDATION items at v1.11 close

## Future Requirements

Deferred to v1.12+. Tracked but not in the v1.11 roadmap.

### Test Infrastructure (Architectural)

- **PERF-FUTURE-01**: Split `src/test/java/` into separate Maven modules (unit / integration / e2e) so Spring-context-per-fork structural cost is amortised across parallel module builds (deferred — too disruptive for v1.11; v1.12 candidate after wallclock baseline)

### Tooling Expansion

- **REWR-FUTURE-01**: Add custom CTC-specific OpenRewrite recipes (e.g., enforce `@RequiredArgsConstructor` over manual constructors, enforce `@Slf4j` over manual logger declarations) once the default recipe pack is bedded in
- **STAT-FUTURE-01**: Re-evaluate adding Checkstyle and/or PMD if SpotBugs proves insufficient after one full milestone of operation
- **DEPS-FUTURE-01**: Renovate Dashboard issue with a curated review queue once team grows beyond a single maintainer

## Out of Scope

Explicitly excluded for v1.11.

| Feature | Reason |
|---------|--------|
| Checkstyle gate | Lombok `addLombokGeneratedAnnotation` fix exists but Checkstyle's value (style enforcement) is already covered by CLAUDE.md conventions + code review — adds maintenance burden without proportionate gain |
| PMD gate | Bytecode-level patterns (null deref, resource leaks, SSRF) that matter for this codebase are caught by SpotBugs + find-sec-bugs; PMD adds category-level rule-pack churn risk on plugin upgrades |
| Semgrep CE | Repo is PUBLIC, so CodeQL is free with full cross-function taint tracking — Semgrep CE's single-file analysis cannot trace the multi-step ZIP-Slip + SSRF data flows this project defends against |
| OpenRewrite as a CI gate | Plugin bound to the `verify` lifecycle can silently mutate source files in CI — kept developer-invoked behind `-Prewrite` profile |
| `UpgradeSpringBoot_4_0` recipe execution | Recipe migrates FROM Boot 3 — applying it on an existing Boot 4.0.6 codebase risks regressing the modular starter decomposition |
| Renovate auto-merge for major bumps | Major bumps (esp. Spring Boot, Java) are intentional triggers for manual OpenRewrite migration runs, not unattended PRs |
| GitHub Advanced Security (GHAS) | Not needed — CodeQL is free on public repos |
| Test module split into multi-Maven-module | Architectural change too large for v1.11; deferred as PERF-FUTURE-01 |
| Custom CTC-specific OpenRewrite recipes | Default recipe pack value not yet established; revisit after one milestone — deferred as REWR-FUTURE-01 |
| Wiki QUAL-05 image embed render verification | Self-resolves on PR merge to master (raw.githubusercontent.com/master URLs); not tech debt |
| PLAT-CI-02 release-workflow observation | By-design post-merge behavior; not tech debt |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| REWR-01 | Phase 80 | Complete |
| REWR-02 | Phase 80 | Complete |
| REWR-03 | Phase 80 | Complete |
| REWR-04 | Phase 80 | Complete |
| REWR-05 | Phase 80 | Complete |
| REWR-06 | Phase 80 | Complete |
| STAT-01 | Phase 81 | Pending |
| STAT-02 | Phase 81 | Pending |
| STAT-03 | Phase 81 | Pending |
| STAT-04 | Phase 81 | Pending |
| STAT-05 | Phase 81 | Pending |
| STAT-06 | Phase 81 | Pending |
| STAT-07 | Phase 81 | Pending |
| BACK-01 | Phase 82 | Complete |
| BACK-02 | Phase 82 | Complete |
| BACK-03 | Phase 82 | Complete |
| BACK-04 | Phase 82 | Complete |
| BACK-05 | Phase 82 | Complete |
| QUAL-01 | Phase 83 | Complete |
| QUAL-02 | Phase 83 | Complete |
| QUAL-03 | Phase 83 | Complete |
| QUAL-04 | Phase 83 | Complete |
| QUAL-05 | Phase 83 | Complete |
| DEPS-01 | Phase 84 | Done    |
| DEPS-02 | Phase 84 | Done    |
| DEPS-03 | Phase 84 | Done    |
| DEPS-04 | Phase 84 | Done    |
| DEPS-05 | Phase 84 | Done    |
| DEPS-06 | Phase 84 | Done    |
| DEPS-07 | Phase 84 | Done    |
| DEPS-08 | Phase 84 | Done    |
| SAST-01 | Phase 85 | Complete |
| SAST-02 | Phase 85 | Complete |
| SAST-03 | Phase 85 | Complete |
| SAST-04 | Phase 85 | Complete |
| SAST-05 | Phase 85 | Complete |
| SAST-06 | Phase 85 | Complete |
| PERF-01 | Phase 86 | Pending |
| PERF-02 | Phase 86 | Pending |
| PERF-03 | Phase 86 | Pending |
| PERF-04 | Phase 86 | Pending |
| PERF-05 | Phase 86 | Pending |
| VAL-01 | Phase 87 | Satisfied |
| VAL-02 | Phase 87 | Satisfied |
| VAL-03 | Phase 87 | Satisfied |
| VAL-04 | Phase 87 | Satisfied |

**Coverage:**
- v1.11 requirements: 46 total (6+7+5+5+8+6+5+4)
- Categories: 8 (REWR, STAT, BACK, QUAL, DEPS, SAST, PERF, VAL)
- Mapped to phases: 46
- Unmapped: 0

---
*Requirements defined: 2026-05-16*
*Last updated: 2026-05-16 — traceability table populated by roadmapper*
