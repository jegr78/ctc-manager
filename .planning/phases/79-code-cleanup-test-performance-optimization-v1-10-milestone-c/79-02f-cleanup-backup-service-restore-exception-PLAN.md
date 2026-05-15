---
phase: 79
plan: 02f
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/backup/service/**
  - src/test/java/org/ctc/backup/service/**
  - src/main/java/org/ctc/backup/restore/**
  - src/test/java/org/ctc/backup/restore/**
  - src/main/java/org/ctc/backup/exception/**
  - src/test/java/org/ctc/backup/exception/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "`BackupImportService` `@Transactional` boundary semantics (wipe + restore + AuditingEntityListener bypass) are unchanged"
    - "All 24 `EntityRestorer` SPI implementations remain registered (Spring-injected list)"
    - "JdbcTemplate.batchUpdate auditing-bypass rationale comments stay (Schutzwort: `auditing`, `AuditingEntityListener`)"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 3 atomic commits"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.backup\\.(service|restore|exception) package"
  key_links:
    - from: "EntityRestorer SPI"
      to: "BackupImportService.restoreAll()"
      via: "List<EntityRestorer> Spring injection"
      pattern: "List<EntityRestorer>"
---

<objective>
Wave 2 cleanup sweep — the heaviest backup-side packages (Phase 75 + Phase 74 source-of-pain code):

1. `org.ctc.backup.service` — 7 files (import count 2). `BackupExportService`, `BackupImportService` (906 LOC, largest single file in v1.10 — primary extract-method candidate per RESEARCH §"Reusable Assets"), `BackupArchiveService` (639 LOC, second-largest), `BackupStagingCleanup`, etc.
2. `org.ctc.backup.restore` — 27 files (1 SPI interface + 1 Noop + 25 `*Restorer` implementations).
3. `org.ctc.backup.exception` — 6 files (`BackupImportException`, `AutoBackupBeforeImportException`, `ZipBombException`, `ZipSlipException`, etc.).

Output: 1-3 atomic per-package commits. Cleanup classes per D-02; `backup.service` is the largest-yield extract-method target in the entire phase.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-CONTEXT.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-RESEARCH.md
@.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02a-cleanup-leaf-admin-controller-backup-leaves-PLAN.md
@CLAUDE.md
@.planning/codebase/STRUCTURE.md

<interfaces>
**Phase 75 wipe + restore semantics (FROZEN — verhaltens-erhaltend constraint):**

- `BackupImportService.execute(UUID)`:
  - Single `@Transactional` boundary
  - Step 0.5: synchronous auto-export to `data/.import-backups/<ts>/auto-backup-before-import.zip` (Phase 76 SECU-07) BEFORE any DB write
  - Step 1: `wipeAllTables()` — native SQL DELETE in FK-reverse order via `EntityManager.createNativeQuery()` (TRUNCATE FORBIDDEN — MariaDB auto-commits)
  - Step 1.5: `UPDATE teams SET parent_team_id = NULL` (decouple self-FK)
  - Step 2: `restoreAll()` — `List<EntityRestorer>` injected, called in `EXPORT_ORDER`
  - Step 2.5: `em.flush() + em.clear()` (drop L1 cache)
  - Step 3: post-commit `BackupImportPostCommitListener` triggers upload-tree stage-and-rename

DO NOT touch any of these step boundaries. Extract-method INSIDE a step is allowed (e.g., extract the FK-reverse-DELETE loop into a private helper) — but the step ORDER and the `@Transactional` boundary are frozen.

**Auditing-bypass invariant (Phase 75 source-of-truth, Schutzwort-protected):**
- `JdbcTemplate.batchUpdate(...)` calls in `*Restorer` implementations MUST stay (bypasses `AuditingEntityListener` so imported `created_at`/`updated_at` survive verbatim).
- Comments adjacent to these calls contain `auditing`/`AuditingEntityListener` Schutzwort hits — preserve verbatim.

**EntityRestorer SPI invariant:**
- The SPI interface `EntityRestorer` is the contract for the 24+ restorers. Method signature `restore(BackupArchiveReader reader, ImportContext ctx)` (or similar — confirm via `head src/main/java/org/ctc/backup/restore/EntityRestorer.java`) is frozen.
- Every concrete `*Restorer` class is `@Component` (Spring-injected). D-04 forbids deletion of `@Component` annotations.
- Pre-flight count: `grep -l "@Component" src/main/java/org/ctc/backup/restore/*Restorer.java | wc -l` — record N_restorers_before. Post-flight: count MUST match.

**ZIP-hardening invariant (Phase 74 SECU-01..03):**
- `ZipSlipException` + `ZipBombException` types are thrown from `BackupArchiveService.extract*()` paths. D-04 forbids deletion.
- Per-entry 50 MB / total 500 MB / 50.000 entries caps are configurable — comments explaining the values stay.

**Multipart-handler invariant (Phase 74 SECU-04):**
- `BackupUploadExceptionHandler` `@ControllerAdvice` handles `MaxUploadSizeExceededException` separately from `GlobalExceptionHandler` (RESEARCH risk #2 mixed-return-type rationale). D-04 forbids deletion.

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block.
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): All `@Component`/`@Service` restorers are Spring-injected. Reflection-equivalent: do NOT delete any `*Restorer` class. JdbcTemplate.batchUpdate calls bypass auditing-listener — Schutzwort-protected.
- Phase 75 step boundaries (`@Transactional`, wipe-order, restore-order, post-commit) are FROZEN. Extract-method INSIDE a step is allowed; cross-step refactors are FORBIDDEN.
- Each package gets EXACTLY ONE commit.
</critical_constraints>

<test_impact>
- Packages touched: `backup.service` (7 files), `backup.restore` (27 files), `backup.exception` (6 files) = 40 source files
- Test classes likely touched: ~50 unit + IT tests, including `BackupExportServiceIT`, `BackupArchiveServiceIT`, `BackupRoundTripIT`, `BackupImportExecuteIT`, `BackupArchiveExtractUploadsIT`, `BackupImportRollbackIT`, `BackupImportMariaDbSmokeIT` + 24 per-restorer unit tests + per-restorer ITs (TeamRestorerIT, etc.)
- Mockito stub updates: SOME — services with mocked `BackupArchiveReader` or `JdbcTemplate` may need stub adjustments IF extract-method changes the call sequence visible to the mock. Mockito-strict-mode risk: if a stub becomes unused after extract-method, the test fails fast. Fix by removing the unused stub.
- Bridge-only test deletions: NONE expected — all per-restorer tests assert behavior, not bridge an old API.
- Estimated test edit count: 5-20 (mostly comment-thinning + a few Mockito stub realignments after extract-method)
- JaCoCo impact: backup.service + backup.restore are comprehensively tested. Cleanup is comment-dominant + extract-method (preserves line coverage). ~0 delta expected. Watch for accidental dead-code deletion that would drop coverage; D-04 (b) (lifecycle annotation check) catches most cases.
- `BackupImportService` 906-LOC — primary extract-method target. Expect 3-8 private-helper extractions; expect 0-5 unused Mockito stubs to clean up in tests.
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.backup.service package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/service/**, src/test/java/org/ctc/backup/service/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/service/BackupImportService.java` (906 LOC)
    - `src/main/java/org/ctc/backup/service/BackupExportService.java`
    - `src/main/java/org/ctc/backup/service/BackupArchiveService.java` (639 LOC)
    - Remaining files in the package (~4 more)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup with PRIMARY FOCUS on extract-method for the two largest files (`BackupImportService` 906 LOC, `BackupArchiveService` 639 LOC). SPECIFIC PROCEDURE:

1. **Comment-thinning pass:** strip Phase-N prefixes (`// Phase 75 D-07:`, `// IMPORT-05:`, `// SECU-07:`) where the line is Schutzwort-clean. Class-Javadoc condensed to 1-3 lines per D-12.

2. **Dead-code pass:** D-04 conservative. The only deletion candidates are private helpers with grep-zero refs. `@Transactional`-bearing methods STAY regardless. `@Component`/`@Service` STAY.

3. **Extract-method pass (PRIMARY YIELD):**
   - `BackupImportService.execute(UUID)`: extract `performWipe()`, `performRestore()`, `performAutoBackup()`, `decoupleTeamSelfFk()` as private helpers IF the existing method has clearly separable phases. PRESERVE the single `@Transactional` boundary on `execute(UUID)` itself — helpers are private and inherit the transaction.
   - `BackupArchiveService.writeZip()`: extract `writeManifestEntry()`, `writeEntityJsonEntries()`, `writeUploadsMirror()` IF the existing method has clearly separable phases.
   - Apply CD-02 discretion at &gt;30 LOC when readability clearly improves.

4. **Logic-simplification pass:** apply CD-03 carefully. The FK-reverse-DELETE loop in `wipeAllTables()` typically has side effects (statement count tracking, audit logging) — leave as-is. Streams over loops only for pure transformations.

5. **Verify Phase 75 step ordering invariant** before committing:
```
grep -nE "auto.*backup|wipe|restore|flush|clear|post.commit" src/main/java/org/ctc/backup/service/BackupImportService.java | head -20
```
The step ORDER (auto-backup → wipe → wipe-self-FK → restore → flush/clear → post-commit) MUST be visible in execute(UUID) in this sequence.

Stage `git add src/main/java/org/ctc/backup/service/ src/test/java/org/ctc/backup/service/`. Commit `refactor(79): cleanup org.ctc.backup.service package — comment-thinning + extract-method (large files)` with the 4-counter body. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT` with failing test names. Pay special attention to `BackupRoundTripIT` and `BackupImportExecuteIT` — these are the integration gates for the wipe+restore behavior.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.service package" &amp;&amp; grep -q "@Transactional" src/main/java/org/ctc/backup/service/BackupImportService.java</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit lands
    - `@Transactional` annotation on `execute(...)` preserved
    - Phase 75 step ordering visible in `execute(UUID)`: auto-backup → wipe → wipe-self-FK → restore → flush/clear → post-commit-event
    - No Schutzwortliste keyword deleted (especially `auditing`, `AuditingEntityListener`, `auto-commit`)
    - No `@Component`/`@Service` annotation deleted
  </acceptance_criteria>
  <done>Single commit lands; tests GREEN; Phase 75 step semantics + transaction boundary preserved.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.backup.restore package (1 commit) — 24+ Restorers</name>
  <files>src/main/java/org/ctc/backup/restore/**, src/test/java/org/ctc/backup/restore/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/restore/EntityRestorer.java` (SPI interface)
    - One representative file per Phase-75 cluster:
      - `SeasonRestorer.java` (Season-cluster, plan 75-03)
      - `RaceResultRestorer.java` (Match/Race-cluster, plan 75-04)
      - `PlayoffRestorer.java` (Playoff/GT7-cluster, plan 75-05)
      - `TeamRestorer.java` (2-pass restorer pattern — confirm the 2-pass pattern is preserved)
    - Mirror test files for the same representatives
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup uniformly to all `*Restorer.java` files. SPECIFIC PROCEDURE:

1. **Pre-flight invariant capture:**
```
grep -l "@Component" src/main/java/org/ctc/backup/restore/*Restorer.java | wc -l
```
Record `N_restorers_before`.

2. **Comment-thinning:** Standard D-09 + Schutzwortliste rules. Most restorers have similar boilerplate Javadoc that can be condensed.

3. **Dead-code:** D-04 forbids deletion of any `*Restorer` class (Spring-injected). Within each restorer, private helpers with grep-zero refs may be deleted.

4. **Extract-method:** The 2-pass restorers (`TeamRestorer`, `SeasonTeamRestorer`, `PlayoffMatchupRestorer`) have larger `restore()` methods that may benefit from `restoreFirstPass()`/`restoreSecondPass()` extraction. Apply CD-02 discretion.

5. **Logic-simplification:** Restorer `restore()` methods typically iterate over JSON-deserialized records and call `jdbcTemplate.batchUpdate(...)`. Comments adjacent to `batchUpdate` MUST stay (Schutzwort: `auditing`, `AuditingEntityListener`). Loop → stream rewrites are allowed only for pure transformations (none in the batchUpdate path — leave loops).

6. **Post-flight invariant verification:**
```
grep -l "@Component" src/main/java/org/ctc/backup/restore/*Restorer.java | wc -l
```
MUST equal `N_restorers_before`. If different → STOP, undo the deletion.

Stage `git add src/main/java/org/ctc/backup/restore/ src/test/java/org/ctc/backup/restore/`. Commit `refactor(79): cleanup org.ctc.backup.restore package — 24+ restorers, batchUpdate-bypass preserved` with body including `Restorer count unchanged: N_before == N_after = <count>`. `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`. Watch `BackupImportRollbackIT` + per-restorer unit tests + `TeamRestorerIT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.restore package" &amp;&amp; git log -1 --pretty=%B | grep -q "Restorer count unchanged"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit lands
    - Restorer count invariant: N_before == N_after
    - `jdbcTemplate.batchUpdate` call count is unchanged across the package (`grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/*.java | awk -F: '{s+=$2} END{print s}'` matches the pre-flight count)
    - No Schutzwortliste keyword deleted (`auditing`, `AuditingEntityListener`, `auto-commit`)
    - The 2-pass pattern in `TeamRestorer`/`SeasonTeamRestorer`/`PlayoffMatchupRestorer` is preserved (2 phases still distinguishable in the source)
  </acceptance_criteria>
  <done>Single commit lands; tests GREEN; restorer SPI count + batchUpdate count + 2-pass pattern invariants hold.</done>
</task>

<task type="auto">
  <name>Task 3: Cleanup org.ctc.backup.exception package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/exception/**, src/test/java/org/ctc/backup/exception/**</files>
  <read_first>
    - All `.java` files in `src/main/java/org/ctc/backup/exception/` (6 files: `BackupImportException`, `AutoBackupBeforeImportException`, `ZipBombException`, `ZipSlipException`, etc.)
    - Mirror test files
    - Plan 02a `<interfaces>` for the 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup. SPECIFIC INVARIANTS: every exception class is THROWN from at least one service (Phase 74/75/76 code paths). D-04 forbids deletion. Exception messages may be condensed if they contain `// Phase 75 D-07` prefixes — strip the prefix, keep the message text intact. `serialVersionUID` fields stay if present (Java serialization contract). Constructors (`SomethingException(String)`, `SomethingException(String, Throwable)`) STAY — they are caller-API.

Stage `git add src/main/java/org/ctc/backup/exception/ src/test/java/org/ctc/backup/exception/`. Commit `refactor(79): cleanup org.ctc.backup.exception package` with the 4-counter body. SKIP commit if no eligible edits (exception classes are usually terse). `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.exception package` lands (or SUMMARY records "no eligible edits")
    - No exception class deleted
    - No constructor signature changed
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Commit lands (or skipped); tests GREEN; exception class set + constructor signatures intact.</done>
</task>

</tasks>

<verification>
- Up to 3 atomic commits land on `gsd/v1.10-platform-and-backup` for `service`/`restore`/`exception`
- `./mvnw test` BUILD SUCCESS after each
- Phase 75 step ordering preserved in `BackupImportService.execute(UUID)`
- 24+ `*Restorer` `@Component` classes are all present (count invariant)
- `jdbcTemplate.batchUpdate` call sites unchanged
- No exception class deleted or renamed
</verification>

<success_criteria>
- 1-3 atomic per-package commits land
- `./mvnw test` BUILD SUCCESS
- All Phase 75/74 step + restorer-SPI + auditing-bypass invariants hold
- All Schutzwort keywords intact
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02f-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, N_restorers_before == N_restorers_after invariant, batchUpdate count, Phase 75 step-ordering visibility check.
</output>
