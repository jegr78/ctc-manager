# Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation - Context

**Gathered:** 2026-05-14
**Status:** Ready for planning

<domain>
## Phase Boundary

The QUAL-deliverable phase for v1.10. Phase 77 produces four narrow deliverables and does NOT close the milestone:

1. **Round-Trip-IT extension (QUAL-02):** Extend the existing `BackupRoundTripIT` (today: manifest-only, Phase 73 contract) with new `@Test` methods that drive a full `export → wipe → import → assert per-table row counts equal + SHA-256 byte-equality on 3 sample entities`. Single class with two `@Nested` profile classes — `H2Tests` (`@ActiveProfiles("dev")`) and `MariaDbTests` (`@ActiveProfiles("local")` + Testcontainers `MariaDBContainer`). Both engines run the full round-trip including SHA-256.
2. **JaCoCo hold (QUAL-01):** Measure current line coverage, document the number in `77-AUTO-UAT.md`, KEEP the `pom.xml` minimum at `0.82` (locked, do NOT raise). v1.9-Baseline 87.02 %; the comfort buffer absorbs the Phase 72-76 new code paths.
3. **Rollback-IT verification (QUAL-04):** Phase 75's `BackupImportRollbackIT` is part of the final `./mvnw verify -Pe2e` run and is BUILD SUCCESS. NO extension or rework of that IT — Phase 77 only verifies it stays passing.
4. **README + GitHub-Wiki + Screenshots (QUAL-05):**
   - README "Backup & Restore" section = short overview (~30-50 lines) + cross-links to `docs/operations/import-runbook.md` (Phase 76) and the GitHub Wiki page. Avoids duplication by design.
   - GitHub Wiki page (`ctc-manager.wiki.git` — separate git repo, NOT in the main repo): Step-by-step Export + Import + Schema-Version + Recovery from `data/.import-backups/<ts>/`. Phase 77 pushes the page directly to the wiki repo via `gh` CLI in a plan step.
   - 3 playwright-cli screenshots embedded in the Wiki page: `/admin/backup` (export button + import form), the Preview-Screen (with acknowledged checkbox), the read-only banner state during a running import. Stored at `.screenshots/77/`.

**Out of scope** (deferred to **Phase 79 = the new milestone-closer**, see Specifics + Deferred):
- v1.10 milestone audit (`v1.10-MILESTONE-AUDIT.md`).
- v1.10-REQUIREMENTS.md final-state update.
- Milestone archive (`/gsd-complete-milestone`).
- Code cleanup (clean-code refactoring, comment thinning).
- Test performance optimization.
- `pom.xml` version bump (`1.8.0-SNAPSHOT` → release tag) — explicitly NOT in Phase 77 NOR Phase 79; separate release workflow afterwards.
- HUMAN-UAT (`77-HUMAN-UAT.md`) — replaced by AUTO-UAT only (D-13).
- Backup feature extensions (per-season selectivity, verify-only mode, checksum file) — v1.11+ per REQUIREMENTS "Future Requirements".

</domain>

<decisions>
## Implementation Decisions

### Round-Trip-IT Shape (resolves QUAL-02)

- **D-01: Extend `BackupRoundTripIT` in-place.** Keep the existing 4 manifest-only `@Test` methods (Phase 73 contract — still valuable, still passing). Add new `@Test` methods inside two `@Nested` classes for the Phase-77 full round-trip. The existing Javadoc already anchors this: *"Phase 77 will extend it into a full export → wipe → import → re-assert round-trip"*. Update the class-level Javadoc to reflect Phase 77's scope (full round-trip + SHA-256 + dual-engine).
- **D-02: Sample entities = `Race` + `SeasonDriver` + `Team`.** Three-entity selection chosen to cover the three most distinct restore code paths:
  - **`Race`** — largest table in dev fixture, exercises 500-row `JdbcTemplate.batchUpdate` ordering (Phase 75 D-07).
  - **`SeasonDriver`** — multi-FK composite-key entity, exercises FK coercion in the restorer setter (Phase 75 D-08).
  - **`Team`** — self-FK entity, exercises the 2-pass NULL-then-UPDATE restore pattern (Phase 75 D-06).
- **D-03: SHA-256 hashes the Jackson tree via `backupObjectMapper` of the in-DB row.** Per sample entity ID, the IT (a) queries the row pre-export, (b) serializes via `@Qualifier("backupObjectMapper")` → `byte[]`, (c) computes `MessageDigest.getInstance("SHA-256").digest(byte[])`. Post-import, queries the same ID, re-serializes via the same mapper, re-hashes. Assert `Arrays.equals(preHash, postHash)`. This proves BOTH the on-disk wire shape AND that `AuditingEntityListener` bypass preserved `created_at` / `updated_at` verbatim (Phase 75 D-04 contract). MixIn `@JsonIgnore` rules apply identically to both serializations — only "wire fields" enter the hash.
- **D-04: Row pick = first row by natural ordering.** Deterministic across H2 + MariaDB:
  - `Race`: `raceRepository.findAll(Sort.by(Order.asc("id"))).getFirst()` — smallest UUID by `BINARY(16)` ordering (consistent on both engines).
  - `SeasonDriver`: `seasonDriverRepository.findAll(Sort.by("seasonId", "driverId")).getFirst()` — composite-key first.
  - `Team`: parent-team (`parentTeam == null`) with smallest id — picked via `teamRepository.findAll(Sort.by("id"))` then `stream().filter(t -> t.getParentTeam() == null).findFirst()`. Probes the 2-pass restore's pass-1 (NULL parent) state-survival.
  - Rationale: zero coupling to fixture-shape beyond "dev profile has data". If `DevDataSeeder` ever changes Order, the IT still picks SOME first row deterministically.

### MariaDB Execution Path (resolves QUAL-02 "H2 AND MariaDB")

- **D-05: Testcontainers in main `ci.yml` — NOT a touch to `mariadb-migration-smoke.yml`.** Honors the `mariadb-migration-smoke.yml is sacred` memory and mirrors the Phase 75 D-16 / `BackupImportMariaDbSmokeIT` precedent. The IT runs via Failsafe's default `*IT.java` pattern on every PR + master push; Testcontainers auto-detects the host Docker daemon on Linux/macOS dev machines and on GitHub Actions Linux runners. REQUIREMENTS wording "via mariadb-migration-smoke.yml-CI-Workflow" is REFRAMED in the CONTEXT (not literally honored) — the user-visible outcome (MariaDB CI gate) is identical via the Testcontainers path.
- **D-06: Single class — `BackupRoundTripIT` — with two `@Nested` profile classes.** Pattern mirrors `SecurityIntegrationTest` from `TESTING.md` §"Nested Test Classes". Layout:
  ```java
  @SpringBootTest
  class BackupRoundTripIT {

      // Existing 4 manifest @Test methods at class-level — Phase 73 contract.

      @Nested
      @SpringBootTest
      @ActiveProfiles("dev")
      class H2RoundTripTests {
          @Test
          void givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch() { ... }
      }

      @Nested
      @SpringBootTest
      @ActiveProfiles("local")
      @Testcontainers
      class MariaDbRoundTripTests {
          @Container static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11");
          @DynamicPropertySource
          static void mariaProps(DynamicPropertyRegistry r) { /* same as Phase 75 BackupImportMariaDbSmokeIT */ }

          @Test
          void givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch() { ... }
      }
  }
  ```
  Phase 75 separated `BackupImportExecuteIT` (H2) and `BackupImportMariaDbSmokeIT` (MariaDB) into TWO classes. Phase 77 deliberately consolidates because the round-trip scenario body is identical between engines (DRY at the test level — the engine difference is purely `@ActiveProfiles` + Testcontainers wiring). Planner extracts shared setup helpers (`exportZip()`, `wipeAllTables()`, `executeImport()`, `assertRowCountsEqual()`, `assertSampleHashesEqual()`) into private methods or a small `RoundTripScenario` test-support class.
- **D-07: Both engines run the FULL round-trip + SHA-256.** No "MariaDB cheaper / H2 fuller" split. Catches dialect divergence in the wire format (`BINARY(16)` UUID packing, `LONGTEXT` JSON columns, timestamp precision). Cost: ~1-2 min extra CI per PR for the Testcontainers MariaDB run — accepted (mirrors Phase 75 BackupImportMariaDbSmokeIT's already-paid cost).

### Documentation Layout (resolves QUAL-05)

- **D-08: GitHub Wiki = the live target.** Wiki content lives in the separate `ctc-manager.wiki.git` repo, NOT in `docs/wiki-drafts/` or `docs/site/`. Phase 77 PUSHES the page directly: a plan step clones `https://github.com/jegr78/ctc-manager.wiki.git`, writes `Backup-and-Restore.md` (GitHub-Wiki naming convention: dashes, not slashes), commits and pushes via `gh` CLI (uses the operator's GitHub auth). Verification: `gh api /repos/jegr78/ctc-manager/pages` or `curl https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` returns 200.
- **D-09: README "Backup & Restore" = short overview (~30-50 lines) + cross-links.** Section structure (locked):
  1. One-paragraph description: "v1.10 introduces a full database backup/restore feature accessible via `/admin/backup` …"
  2. **Export** — 3-step quick reference (navigate to `/admin/backup`, click `Export`, download ZIP).
  3. **Import** — 3-step quick reference (upload ZIP, review preview, confirm + execute). Note the `Schema-Version` lock.
  4. **Recovery** — 1-sentence pointer: *"If an import fails or you need to revert, see `docs/operations/import-runbook.md` for step-by-step recovery."*
  5. **Full guide** — link to the GitHub Wiki page. Detailed `data/.import-backups/<ts>/` semantics live in the Wiki + runbook.
  README delegates depth to (a) `docs/operations/import-runbook.md` for operator-facing recovery and (b) the GitHub Wiki for user-facing how-to. No content duplication.
- **D-10: 3 screenshots via `playwright-cli`, stored in `.screenshots/77/`, embedded in Wiki only (not README).**
  - `01-backup-page.png` — `/admin/backup` showing the Export button + Import file picker.
  - `02-preview-screen.png` — `/admin/backup/preview` (or wherever Phase 74 lands) showing the preview row-count table + acknowledged checkbox.
  - `03-import-banner.png` — any admin page (e.g., `/admin/seasons`) with the yellow read-only banner visible during a simulated active import. Banner is rendered in the dev profile by manually flipping `ImportLockService.isLocked()` to true via a temporary @TestComponent OR by triggering a slow import in another tab. Plan picks the simplest reliable approach.
  - Screenshots are committed to the main repo at `.screenshots/77/` (per `feedback_screenshots_folder` memory: "Playwright-Screenshots in .screenshots/ ablegen, nie im Root") and referenced in the Wiki page via absolute GitHub raw URLs (`https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/01-backup-page.png`). Wiki repo does NOT carry binary copies — keeps the wiki repo lean and ensures the screenshots stay in sync with the codebase.

### QUAL-01 — JaCoCo Hold Strategy

- **D-11: `pom.xml` minimum stays at `0.82` — DO NOT raise.** CLAUDE.md "Constraints" locks the minimum at 82%. v1.9-Baseline is 87.02 % (~5 % comfort buffer). Phase 77 measures the current value (one plan step: `./mvnw verify` → read `target/site/jacoco/jacoco.csv`), documents it in `77-AUTO-UAT.md` (e.g., "Measured 85.7 % — 3.7 % buffer over the 82 % gate"). NOT raising avoids future-PR breakage on cosmetic coverage dips and is consistent with `feedback_coverage_strategy` memory.
- **D-12: Coverage exclusions for v1.10 backup code = ZERO additions.** All 22 `<Entity>Restorer` classes (Phase 75) DO have direct unit tests (Phase 75 D-05 / `<Entity>RestorerTest` Surefire tests) — they count toward coverage. Test-only injectors (`NoopRestoreFailureInjector`, `FailAtTableInjector`) are in `src/test/java/` so JaCoCo never measures them. No new exclusions in `pom.xml`'s `<excludes>` block — keeps the exclude list bounded to "production code that genuinely cannot be measured" (Playwright-graphic services, locked from earlier milestones).

### QUAL-04 — Rollback-IT Verification

- **D-13: No HUMAN-UAT; only AUTO-UAT via `playwright-cli`.** The QUAL-02 + QUAL-04 ITs are automated; QUAL-01 is automated via JaCoCo gate; QUAL-05 docs are verified visually by `playwright-cli` opening the README on GitHub + the Wiki page in the browser. Phase 77 ships:
  - `77-AUTO-UAT.md` — checklist of automated verifications:
    1. `./mvnw verify -Pe2e` BUILD SUCCESS on master after merge (with `BackupRoundTripIT` + `BackupImportRollbackIT` both green).
    2. JaCoCo line coverage measured + recorded as a `Measured: NN.N %` line.
    3. `playwright-cli open https://github.com/jegr78/ctc-manager` → README "Backup & Restore" section renders with working cross-links.
    4. `playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` → page exists, 3 screenshots render, all internal links work.
    5. `BackupRoundTripIT$MariaDbRoundTripTests` BUILD SUCCESS in the CI logs (link to the GH Actions run).
  - No `77-HUMAN-UAT.md` written.
- **D-14: `BackupImportRollbackIT` is touched ZERO times in Phase 77.** Phase 75 shipped it; Phase 77's only contract is that it stays passing in the final `verify -Pe2e` run. If a Phase 75 regression surfaces during Phase 77 execution, the planner escalates as a Phase 75 hotfix — not as a Phase 77 deviation.

### Milestone Closure Boundary

- **D-15: Phase 77 is NOT the milestone-closer.** Per user direction during discussion:
  - Phase 78 (Docker Release Image Fix) is already shipped and belongs in v1.10's deliverable set.
  - A **new Phase 79** will be added for: (a) Code Cleanup (Clean-Code refactoring + comment thinning across the Phase 72-76 backup code), (b) Test Performance Optimization (Surefire/Failsafe runtimes, parallel test execution review). Phase 79 becomes the actual v1.10 milestone-closer that runs `/gsd-audit-milestone` + `/gsd-complete-milestone`.
  - **Action item (post-Phase-77, not a Phase 77 plan):** User runs `/gsd-phase add 79 ...` after Phase 77 ships to register Phase 79 in `ROADMAP.md` with the closer-scope.
- **D-16: `pom.xml` version bump is NOT in Phase 77 or Phase 79.** Current version is `1.8.0-SNAPSHOT` (stale — milestone is v1.10). The bump from `1.8.0-SNAPSHOT` → `1.10.0` (or whatever the team's release-tag convention is) belongs to a SEPARATE release workflow / `/gsd-ship` invocation AFTER Phase 79 archives the milestone. Phase 77's pom.xml diff is zero on the `<version>` line; if a planner is tempted to "fix the stale 1.8.0-SNAPSHOT while we're here", they REPORT instead of edit (per `feedback_orchestrator_discipline` "Architektur-Default vs. Process-Gate").

### Wiring vs. Refactor Discipline

- **D-17: Phase 77 touches only the following code paths:**
  - `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — extend with `@Nested` H2 + MariaDB tests (D-01..D-07).
  - `README.md` — add "Backup & Restore" section per D-09.
  - `pom.xml` — measure coverage, ZERO line changes (D-11 / D-12 / D-16 carry-forward).
  - `.screenshots/77/` — 3 new PNG files (D-10).
  - `.planning/phases/77-.../77-AUTO-UAT.md` — automated UAT checklist (D-13).
  - `ctc-manager.wiki.git` (external repo) — new `Backup-and-Restore.md` page (D-08).
- **D-18: No new Maven dependencies.** SHA-256 via JDK `java.security.MessageDigest`. Testcontainers + Failsafe + JaCoCo already wired. JDK `MessageDigest` + `java.util.HexFormat` (Java 17+) for hash hex-rendering in assertion messages.
- **D-19: No new Flyway migration.** Phase 77 reads the existing schema; no DDL changes.
- **D-20: No new templates, CSS, or controllers.** Phase 77 is verification-and-docs only.

### Claude's Discretion

- **CD-01: Exact name of the new `@Nested` classes** — `H2RoundTripTests` + `MariaDbRoundTripTests` recommended (D-06). Planner may pick alternative names if class-name-collision with existing test patterns occurs.
- **CD-02: Helper extraction** — Whether to extract `exportZip()` / `wipeAllTables()` / `executeImport()` / `assertRowCountsEqual()` / `assertSampleHashesEqual()` into a shared `RoundTripScenario` test-support class OR keep as private methods inside `BackupRoundTripIT`. Default: private methods first; extract to support class only if a second IT consumer emerges. Avoids speculative abstraction.
- **CD-03: Read-only banner screenshot mechanism** — `@TestComponent` flipping `ImportLockService.isLocked()` to true is the recommended path (deterministic, fast). Alternative is launching a slow real import in another tab — flakier but more authentic. Planner picks per the time budget for `playwright-cli` orchestration.
- **CD-04: Wiki repo discovery + auth** — Assumed: `https://github.com/jegr78/ctc-manager.wiki.git` exists and the operator has `gh auth` set up. Planner verifies in a first plan step; if the wiki repo does not exist yet, the operator initializes it via the GitHub UI (`Wiki` tab → `Create the first page`) before Phase 77 plans run. This is a one-time setup; not a Phase 77 deliverable.
- **CD-05: README placement** — Insert the "Backup & Restore" section AFTER the existing "Features" section and BEFORE "Quick Start", because it documents a feature operators use (not a dev-loop step). Planner can adjust if the README structure feels off after the edit.
- **CD-06: Screenshot resolution** — `playwright-cli` defaults to viewport 1280×720. Recommended: keep defaults (consistent with Phase 60 `feedback_screenshots_folder` baseline). Planner can override if the banner gets clipped at default height.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation

- `.planning/ROADMAP.md` §"Phase 77" — 5 success criteria locked: `./mvnw verify -Pe2e` BUILD SUCCESS on H2 + `mariadb-migration-smoke.yml` workflow BUILD SUCCESS on MariaDB (note: Phase 77 D-05 reframes this as Testcontainers-in-main-ci.yml without modifying the sacred workflow), JaCoCo ≥ 82 % (D-11), `BackupRoundTripIT` row-count + SHA-256 contract (D-02..D-04), Phase 75's `BackupImportRollbackIT` still green (D-14), README + Wiki page (D-08..D-10).
- `.planning/REQUIREMENTS.md` §QUAL-01 / QUAL-02 / QUAL-04 / QUAL-05 — Acceptance criteria. **NOTE:** REQUIREMENTS.md QUAL-02 mentions "MariaDB-Profile via mariadb-migration-smoke.yml-CI-Workflow"; D-05 of THIS CONTEXT overrides the literal mechanism (Testcontainers in main ci.yml) while preserving the user-visible outcome (MariaDB CI gate green).
- `.planning/STATE.md` §"Key Technical Context" — "No new Maven dependencies" project-constraint; Phase 77 D-18 confirms.
- `.planning/PROJECT.md` §"Backup Wire Contract (v1.10)" — Invariants 1-4 (integer `SCHEMA_VERSION = 1`, manifest-first ZIP, 22-entity scope, ObjectMapper isolation). Phase 77 SHA-256 assertions rely on the wire-contract invariance.

### Prior-phase context (mandatory carry-forward)

- `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/72-CONTEXT.md` — `BackupSchema.SCHEMA_VERSION = 1`, `EXPORT_ORDER` 22-entity scope, `backupObjectMapper` strict mapper. Phase 77 SHA-256 hashes use the same `backupObjectMapper` qualifier so the on-disk wire shape is the hashed surface.
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-CONTEXT.md` — `BackupArchiveService.writeZip(OutputStream, Instant)` shape; Phase 77 calls it for the export-leg of the round-trip. Also the source of the existing `BackupRoundTripIT` Javadoc that Phase 77 D-01 explicitly extends.
- `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — `BackupImportService.stage / reparse / deleteStagingFile` (D-19), staging-file UUID contract. Phase 77 round-trip drives the same `stage → execute` path as the production controller.
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-CONTEXT.md` — `BackupImportService.execute(UUID)` (D-14), `JdbcTemplate.batchUpdate`-bypass + `AuditingEntityListener` preservation (D-04), Testcontainers MariaDB pattern (D-16), `RestoreFailureInjector` extension point (D-13). All four flow into Phase 77: D-02 sample-entity choice mirrors D-05/D-08 restore code-paths, D-03 SHA-256 proves D-04's auditing-bypass contract, D-05/D-06 reuse the Phase 75 Testcontainers wiring, D-14 confirms `BackupImportRollbackIT` stays untouched.
- `.planning/phases/76-operational-hardening-import-lock-read-only-banner-auto-back/76-CONTEXT.md` — `ImportLockService` (D-01), `ImportLockBannerAdvice` (D-11), `admin/layout.html` banner (D-12), `docs/operations/import-runbook.md` (D-22). Phase 77 D-09 cross-links to the runbook; D-10 screenshots the banner via the Phase 76 wiring.
- `.planning/phases/78-docker-release-image-fix/78-CONTEXT.md` — Already SHIPPED. Phase 77 does not consume but Phase 79 (the upcoming milestone-closer) MUST include Phase 78's deliverables in the v1.10 audit.

### Existing code Phase 77 references (extend)

- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — Phase 73 manifest-round-trip IT. Phase 77 D-01 extends with two `@Nested` profile classes (D-06). Existing 4 `@Test` methods stay byte-identical.
- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — Phase 75 Testcontainers + `@DynamicPropertySource` blueprint. Phase 77 D-06 mirrors the wiring verbatim for the `MariaDbRoundTripTests` `@Nested` class.
- `src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java` — Phase 75 rollback IT. Phase 77 D-14 does NOT modify; only verifies BUILD SUCCESS.
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — `writeZip(OutputStream, Instant)` consumed by Phase 77's round-trip export leg.
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — `stage(MultipartFile)` + `execute(UUID)` consumed by Phase 77's round-trip import leg.
- `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` — `@Qualifier("backupObjectMapper")` consumed by Phase 77 D-03's SHA-256 hashing.
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — `getExportOrder()` consumed by Phase 77's row-count parity assertions across all 22 entities.

### Existing code Phase 77 references but does NOT modify

- `pom.xml` — JaCoCo minimum `0.82` stays (D-11), `<version>1.8.0-SNAPSHOT</version>` stays (D-16), exclude block unchanged (D-12).
- `.github/workflows/mariadb-migration-smoke.yml` — SACRED. Phase 77 D-05 explicitly does NOT modify (mirrors Phase 75 BackupImportMariaDbSmokeIT precedent).
- `.github/workflows/ci.yml` — Already runs Failsafe `*IT.java` pattern; Phase 77's new `@Nested` tests are picked up automatically. NO workflow changes.
- `docs/operations/import-runbook.md` — Phase 76 runbook. Phase 77 D-09 cross-links FROM the README; runbook itself stays byte-identical.
- All 22 `<Entity>Restorer` classes (Phase 75) and 22 Jackson MixIns (Phase 73) — read-only consumption by Phase 77's round-trip path.
- `BackupController` + `admin/backup.html` + `admin/backup-confirm.html` — Phase 77 may exercise them in `playwright-cli` for screenshots (D-10) but does NOT edit.

### External references

- **GitHub Wiki repo** — `https://github.com/jegr78/ctc-manager.wiki.git` (clone target for D-08). Phase 77 writes `Backup-and-Restore.md` (GitHub-Wiki naming: dashes for spaces, no `.md` in the URL slug).
- **GitHub raw screenshot URLs** — `https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/<filename>.png` consumed by D-10's Wiki-page image embeds.

### Project conventions (mandatory reading)

- `CLAUDE.md` §"Architectural Principles" — Phase 77 is mostly verification + docs; the only production-adjacent code is the test class extension. Tests follow Given-When-Then naming per CLAUDE.md "Test Naming".
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 % held by D-11; Flyway unchanged by D-19; OSIV irrelevant (no new template work).
- `CLAUDE.md` §"feedback_ui_language" (memory) — README + Wiki content is English-only.
- `CLAUDE.md` §"feedback_screenshots_folder" (memory) — Screenshots land in `.screenshots/77/`, NEVER at the repo root (D-10).
- `CLAUDE.md` §"feedback_e2e_verification" (memory) — Final verification gate is `./mvnw verify -Pe2e` BUILD SUCCESS (D-13 AUTO-UAT step 1).
- `CLAUDE.md` §"feedback_coverage_strategy" (memory) — "82% Minimum, Playwright-Services excluden" — D-11 / D-12 honor verbatim.
- `CLAUDE.md` §"feedback_subagent_stability" + "feedback_orchestrator_discipline" (memory) — D-15 / D-16 explicitly reject the temptation to fold milestone-closure or version-bump into Phase 77.
- `CLAUDE.md` §"feedback_playwright_cli" (memory) — D-10's screenshot generation uses the documented `playwright-cli` skill, NOT bespoke Playwright scripts.
- `CLAUDE.md` §"feedback_pr_workflow" (memory) — Phase 77 ships via a PR; `gh pr create --assignee jegr78` per the standard flow.
- `CLAUDE.md` §"feedback_branch_from_origin" (memory) — Branch `gsd/v1.10-platform-and-backup` is the active feature branch (per git status); Phase 77 plans land on top of it.
- `.planning/codebase/TESTING.md` §"Nested Test Classes" + §"Integration Testing" — Pattern reference for D-06's two-`@Nested` shape.

### External APIs (consulted, not on-disk)

- `java.security.MessageDigest` — `MessageDigest.getInstance("SHA-256")` + `digest(byte[])`. D-03's hashing primitive.
- `java.util.HexFormat` (Java 17+) — `HexFormat.of().formatHex(byte[])` for assertion-message hash rendering.
- `org.testcontainers.containers.MariaDBContainer` — Phase 75-precedent wiring; Phase 77 D-06 reuses the exact `MariaDBContainer<>("mariadb:11")` construction.
- `org.springframework.test.context.DynamicPropertySource` — Phase 75-precedent; D-06 reuses for `spring.datasource.url` / `username` / `password` override.
- `org.junit.jupiter.api.Nested` — D-06's profile-grouping mechanism (mirrors `SecurityIntegrationTest`).
- GitHub CLI — `gh repo clone jegr78/ctc-manager.wiki <tmp>`; `git -C <tmp> commit + push`. D-08's wiki delivery.
- GitHub Wiki naming convention — Page slug `Backup-and-Restore` corresponds to file `Backup-and-Restore.md` and URL `/wiki/Backup-and-Restore`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`BackupRoundTripIT` (Phase 73)** — Already exists; 4 `@Test` methods cover the Phase-73 manifest contract. Class-level Javadoc explicitly anchors the Phase-77 extension. D-01 extends in-place — no rename, no archival. Existing `readEntry(...)` / `countEntriesMatching(...)` private helpers stay; Phase 77 may reuse them inside the new `@Nested` round-trip tests.
- **`BackupImportMariaDbSmokeIT` (Phase 75)** — Testcontainers + `@DynamicPropertySource` blueprint for the live-MariaDB IT. Phase 77 D-06 mirrors the `@Container static MariaDBContainer<>("mariadb:11")` declaration verbatim, including the `rewriteBatchedStatements=true` JDBC URL append (Phase 75 RESEARCH §10).
- **`BackupArchiveService.writeZip(OutputStream, Instant)` (Phase 73)** — Phase 77's round-trip export leg. Same `@Qualifier("backupObjectMapper")` injection.
- **`BackupImportService.stage(MultipartFile) + execute(UUID)` (Phase 74 + 75)** — Phase 77's import leg. The test uses `MockMultipartFile` (per `BackupImportMariaDbSmokeIT` precedent) to wrap the exported ZIP bytes back into the staging-file flow.
- **`backupObjectMapper` qualifier (Phase 72)** — `FAIL_ON_UNKNOWN_PROPERTIES=true` + MixIn-isolated; D-03's SHA-256 reuses this exact mapper so the hashed bytes match the on-disk wire.
- **`BackupSchema.getExportOrder()` (Phase 72)** — 22-entity ordered list; Phase 77 iterates for the row-count parity assertion.
- **22 `<Entity>Restorer` classes + 22 Jackson MixIns (Phase 73 + 75)** — Read-only consumption; their existing test coverage feeds JaCoCo (D-12 no exclusions added).
- **`RestoreFailureInjector` interface + `NoopRestoreFailureInjector` `@Primary` (Phase 75 D-13)** — Phase 77 round-trip uses the production no-op; NO injector override in the new tests (round-trip = happy path).
- **`DataImportAuditRepository` (Phase 72)** — Phase 77 may poll for the success-time audit row after the import leg (mirrors Phase 75 `BackupImportMariaDbSmokeIT`'s 2-second poll).
- **`docs/operations/import-runbook.md` (Phase 76 D-22)** — Phase 77 D-09 README cross-links to this; no edits.
- **`admin/backup.html` + `admin/backup-confirm.html` (Phase 74)** — Existing templates; Phase 77 D-10 screenshots them via `playwright-cli`.
- **`admin/layout.html` banner (Phase 76 D-12)** — Yellow `alert-warning` banner; Phase 77 D-10's 3rd screenshot captures it in the import-active state.

### Established Patterns

- **`@Nested` profile classes** — `SecurityIntegrationTest` precedent (TESTING.md). D-06's H2/MariaDB split mirrors verbatim.
- **`@SpringBootTest` + `@ActiveProfiles("dev")` for H2** + `+ Testcontainers + @ActiveProfiles("local")` for MariaDB — Phase 75 precedent.
- **Given-When-Then test naming** — D-06 method names (`givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch`) follow CLAUDE.md "Test Naming".
- **AssertJ + `Arrays.equals`** — D-03 SHA-256 assertion uses `assertThat(postHash).containsExactly(preHash)` OR `assertThat(Arrays.equals(preHash, postHash)).isTrue()` — planner picks the more diagnostic message format.
- **`MockMultipartFile`** — Phase 75 BackupImportMariaDbSmokeIT precedent for wrapping ZIP bytes back into the staging-file flow.
- **`.screenshots/<phase>/` convention** — `feedback_screenshots_folder` memory + Phase 60+ precedent.
- **`@RequiredArgsConstructor` + `@Slf4j`** — Not applicable (Phase 77 = test-only + docs, no new production classes).

### Integration Points

- **New classes:**
  - None. Phase 77 extends the existing `BackupRoundTripIT` class with `@Nested` inner classes (D-01 / D-06). If a shared `RoundTripScenario` helper emerges, it lives at `src/test/java/org/ctc/backup/service/RoundTripScenario.java` (CD-02 — speculative, only if needed).
- **Extended classes:**
  - `BackupRoundTripIT.java` — adds two `@Nested` classes + private helper methods per CD-02.
- **New files (non-Java):**
  - `.screenshots/77/01-backup-page.png`
  - `.screenshots/77/02-preview-screen.png`
  - `.screenshots/77/03-import-banner.png`
  - `.planning/phases/77-.../77-AUTO-UAT.md` — automated UAT checklist (D-13).
  - (External) `ctc-manager.wiki.git`/`Backup-and-Restore.md` — pushed to wiki repo (D-08).
- **Edited files (non-Java):**
  - `README.md` — adds "Backup & Restore" section per D-09. Section placement: after "Features", before "Quick Start" (CD-05).
- **No template edits, no CSS, no JS** — Phase 77 is test + docs only.
- **No new dependencies** — `MessageDigest` + `HexFormat` are JDK (D-18). Testcontainers + Failsafe + JaCoCo + Playwright already wired.
- **No schema / migration changes** — D-19.
- **No security / auth changes** — Phase 77 tests run under the dev/local profile (no auth on those profiles per CLAUDE.md "Profiles").
- **Tests added (Failsafe IT):**
  - `BackupRoundTripIT$H2RoundTripTests.givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch` — single `@Test`.
  - `BackupRoundTripIT$MariaDbRoundTripTests.givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch` — single `@Test`.
  - Possibly small helper-extraction unit tests under Surefire if `RoundTripScenario` is extracted (CD-02 — speculative).

</code_context>

<specifics>
## Specific Ideas

- **The existing `BackupRoundTripIT` Javadoc is the source of truth for Phase 77's intent.** It already names the extension: *"Phase 77 will extend it into a full export → wipe → import → re-assert round-trip; this plan ships only the manifest leg."* D-01's "extend in-place" choice honors this intent literally — no class rename, no archival.
- **`mariadb-migration-smoke.yml` is sacred.** The workflow header in the file itself says so (verbatim). D-05 explicitly does NOT touch it; the Testcontainers-in-main-ci.yml path is the canonical MariaDB CI mechanism since Phase 75 D-16. REQUIREMENTS.md QUAL-02 wording "via mariadb-migration-smoke.yml-CI-Workflow" is a SLIP; D-05 documents the reframe so a future reviewer doesn't "fix" the IT back to the literal wording.
- **SHA-256 hashes the in-DB → backupObjectMapper-serialization, NOT the data/<entity>.json bytes from the ZIP.** D-03's choice is deliberate: hashing the JSON file from the ZIP would only prove the export pipeline is idempotent (write same JSON twice → same bytes). Hashing the in-DB row through the same `backupObjectMapper` proves BOTH the wire-shape invariance AND that the restored DB row has byte-identical content to the pre-export DB row — the round-trip contract.
- **Sample entities chosen for distinct restore-code-path coverage, not "biggest tables".** D-02 picks Race (batched), SeasonDriver (multi-FK), Team (self-FK 2-pass). RaceResult or RaceLineup would be bigger but redundant with Race for what we're proving. The 3-entity floor in REQUIREMENTS is intentional; D-02 puts the 3 on the highest-risk restore paths.
- **The GitHub Wiki page lives in a separate git repo.** `https://github.com/jegr78/ctc-manager.wiki.git`. Phase 77 pushes via `gh repo clone` + `git push`; the main repo PR does NOT contain the wiki page bytes. Verification gate is "wiki URL returns 200 with expected content" (D-13 AUTO-UAT step 4), not "the wiki .md file is in the PR diff".
- **Screenshots are committed to the MAIN repo at `.screenshots/77/`**, not to the wiki repo. The Wiki page references them via absolute `raw.githubusercontent.com` URLs. Keeps the wiki repo lean; keeps screenshots in lockstep with the codebase that produces them. If a Phase 78+ refactor changes the `/admin/backup` page, the next phase re-runs the screenshot generation and re-commits to `.screenshots/77/` — the Wiki page auto-updates because the URL is stable.
- **Phase 77 is NOT the milestone-closer.** Per user direction during discussion (D-15). The closer is the upcoming Phase 79. This CONTEXT explicitly states the scope boundary so the planner does not get pulled into milestone-audit work mid-phase.
- **`pom.xml` `<version>1.8.0-SNAPSHOT</version>` is stale.** Current value at the time of context-gathering. D-16 explicitly says Phase 77 does NOT fix this; the version bump belongs to a separate release workflow after Phase 79 ships.

</specifics>

<deferred>
## Deferred Ideas

- **Phase 79 — Code Cleanup + Test Performance Optimization (NEW phase, v1.10 milestone-closer).** Per D-15. Scope (preliminary, refined when `/gsd-phase add 79` is run): (a) Clean-code refactoring across the Phase 72-76 backup code (naming, method-length, dead-code removal), (b) Comment thinning (remove redundant Javadoc, keep WHY-comments only per CLAUDE.md "Default to writing no comments"), (c) Test performance optimization (Surefire forks, Failsafe parallel-IT execution, `@DirtiesContext` audit), (d) `/gsd-audit-milestone v1.10` execution, (e) `/gsd-complete-milestone v1.10` archive. The user runs `/gsd-phase add 79 ...` after Phase 77 ships.
- **`pom.xml` version bump from `1.8.0-SNAPSHOT` to `1.10.0`** — D-16 explicitly defers to a separate release workflow AFTER Phase 79. Neither Phase 77 NOR Phase 79 touches the `<version>` tag.
- **Raising JaCoCo minimum above `0.82`** — D-11 keeps the gate at 0.82. If a future milestone wants to ratchet up (e.g., to 0.85), that is a `/gsd-config`-level decision, not a phase-level decision.
- **Per-Saison Export/Import selectivity** — REQUIREMENTS "Future Requirements" §EXPORT-FUT-01 / IMPORT-FUT-01. v1.11+ candidate.
- **SHA-256 checksum file (`manifest.sha256`)** — REQUIREMENTS "Future Requirements" §EXPORT-FUT-02. v1.11+ candidate.
- **Verify-Only Import Mode** — REQUIREMENTS "Future Requirements". v1.11+ candidate.
- **`/admin/backup/history` audit-viewer UI page** — Phase 74/75/76 deferred candidate; v1.11+.
- **`@Scheduled` cleanup of `data/.import-backups/<ts>/`** — Operator-driven per Phase 76 D-22 runbook. v1.11+ if operational pain warrants it.
- **HUMAN-UAT for Phase 77** — D-13 replaces with AUTO-UAT only. If a future operational concern wants an explicit human sign-off step, add a HUMAN-UAT step in Phase 79's milestone-audit checklist instead.
- **Helper extraction (`RoundTripScenario`)** — CD-02 default is "private methods inside `BackupRoundTripIT`"; extract only if a second consumer emerges. Not Phase 77 work.
- **Raising the screenshot resolution** — CD-06 keeps `playwright-cli` defaults (1280×720). If the banner gets clipped, planner adjusts; otherwise no Phase 77 work.

</deferred>

---

*Phase: 77-final-uat-jacoco-hold-round-trip-test-documentation*
*Context gathered: 2026-05-14*
