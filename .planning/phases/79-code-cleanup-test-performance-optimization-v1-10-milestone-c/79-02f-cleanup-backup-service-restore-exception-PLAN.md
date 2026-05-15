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

**scope_sanity: 27-files-task split per checker iteration 2.** Original Task 2 (`backup.restore` — 27 source files in a single task) exceeded the 15-files warning threshold. Task 2 is split into Task 2a (Season/Driver/Team-cluster + SPI + Noop/Reserve, ~12 restorer classes) and Task 2b (Race/Playoff/Calendar/GT7-cluster, ~15 restorer classes). Each split sub-task produces its OWN atomic commit, each followed by `./mvnw test`. The combined restorer-count invariant (`N_restorers_before == N_restorers_after`) is verified at the end of Task 2b (after the second commit), not after each sub-task — because Task 2a alone may temporarily move classes between sub-clusters (the SPI interface is one file; deletion of a `*Restorer` is forbidden by D-04 anyway). The batchUpdate call-count invariant is verified at the end of EACH sub-task.
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
  <name>Task 2a: Cleanup org.ctc.backup.restore Cluster A (Season/Driver/Team + SPI + Noop) — 1 commit, ~12 classes</name>
  <files>src/main/java/org/ctc/backup/restore/{EntityRestorer.java,NoopRestorer.java,Reserve*Restorer.java,Season*Restorer.java,Driver*Restorer.java,Team*Restorer.java,Manufacturer*Restorer.java,Track*Restorer.java}, src/test/java/org/ctc/backup/restore/{Season*,Driver*,Team*,Manufacturer*,Track*,Noop*,Reserve*}</files>
  <read_first>
    - `src/main/java/org/ctc/backup/restore/EntityRestorer.java` (SPI interface)
    - `SeasonRestorer.java` (Season-cluster, plan 75-03 representative)
    - `TeamRestorer.java` (2-pass restorer pattern — confirm the 2-pass pattern is preserved)
    - `SeasonTeamRestorer.java` (2-pass restorer pattern — same)
    - Mirror test files for the same representatives
    - Plan 02a `<interfaces>` for the 4-pass procedure
    - This plan's `<test_impact>` block — see "scope_sanity: 27-files-task split" rationale
  </read_first>
  <action>
**scope_sanity rationale:** This is half of the original Task 2 split per checker iteration 2 ("27 source files in a single task exceeds the 15-files warning threshold"). Cluster A covers the Season/Driver/Team domain (~12 classes) PLUS the SPI interface + Noop/Reserve restorers. Cluster B (Task 2b) covers Race/Playoff/Calendar/GT7. Both produce their own atomic commit.

Apply the 4-pass cleanup uniformly to the Cluster A `*Restorer.java` files. SPECIFIC PROCEDURE:

1. **Pre-flight invariant capture (whole-package — both Task 2a and Task 2b refer back to this):**
```
grep -l "@Component" src/main/java/org/ctc/backup/restore/*Restorer.java | wc -l
```
Record `N_restorers_before` in a temp note (e.g., `/tmp/79-02f-restorer-count.txt`). This is shared with Task 2b — do NOT re-measure at the start of Task 2b.

Also capture the per-package `batchUpdate` count for Cluster A baseline:
```
ls src/main/java/org/ctc/backup/restore/ | grep -E "^(EntityRestorer|NoopRestorer|Reserve.*Restorer|Season.*Restorer|Driver.*Restorer|Team.*Restorer|Manufacturer.*Restorer|Track.*Restorer)\.java$" > /tmp/79-02f-cluster-a.txt
xargs -a /tmp/79-02f-cluster-a.txt -I{} grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/{} | awk '{s+=$1} END{print s}'
```
Record `batchUpdate_count_A_before`.

2. **Comment-thinning:** Standard D-09 + Schutzwortliste rules. Most restorers have similar boilerplate Javadoc that can be condensed.

3. **Dead-code:** D-04 forbids deletion of any `*Restorer` class (Spring-injected). Within each restorer, private helpers with grep-zero refs may be deleted.

4. **Extract-method:** The 2-pass restorers in this cluster (`TeamRestorer`, `SeasonTeamRestorer` if it lives in Cluster A) have larger `restore()` methods that may benefit from `restoreFirstPass()`/`restoreSecondPass()` extraction. Apply CD-02 discretion at >30 LOC.

5. **Logic-simplification:** Restorer `restore()` methods typically iterate over JSON-deserialized records and call `jdbcTemplate.batchUpdate(...)`. Comments adjacent to `batchUpdate` MUST stay (Schutzwort: `auditing`, `AuditingEntityListener`). Loop → stream rewrites are allowed only for pure transformations (none in the batchUpdate path — leave loops).

6. **Post-flight Cluster A invariant verification:**
```
xargs -a /tmp/79-02f-cluster-a.txt -I{} grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/{} | awk '{s+=$1} END{print s}'
```
MUST equal `batchUpdate_count_A_before`. If different → STOP, undo the deletion. The whole-package `@Component` count is re-checked at the end of Task 2b, NOT here (because no class is deletable per D-04 — the invariant is structural).

7. **Stage + commit (Cluster A only):**
```
git add src/main/java/org/ctc/backup/restore/EntityRestorer.java \
        src/main/java/org/ctc/backup/restore/NoopRestorer.java \
        src/main/java/org/ctc/backup/restore/Reserve*Restorer.java \
        src/main/java/org/ctc/backup/restore/Season*Restorer.java \
        src/main/java/org/ctc/backup/restore/Driver*Restorer.java \
        src/main/java/org/ctc/backup/restore/Team*Restorer.java \
        src/main/java/org/ctc/backup/restore/Manufacturer*Restorer.java \
        src/main/java/org/ctc/backup/restore/Track*Restorer.java \
        src/test/java/org/ctc/backup/restore/{Season*,Driver*,Team*,Manufacturer*,Track*,Noop*,Reserve*}
```
Verify `git status` shows ONLY Cluster A files (no Race/Playoff/Calendar/GT7 files). If any extra file is staged → `git reset HEAD <file>` and re-stage carefully.

Commit message: `refactor(79): cleanup org.ctc.backup.restore cluster A (Season/Driver/Team + SPI) — 1/2 of scope_sanity split`. Body includes:
- Cluster A classes touched: ~12 (list them)
- batchUpdate count (Cluster A): unchanged at <N>
- 2-pass pattern preserved in TeamRestorer / SeasonTeamRestorer (if applicable)
- scope_sanity: 27-files-task split per checker iteration 2; Cluster B (Race/Playoff/Calendar/GT7) follows in Task 2b

8. **Run `./mvnw test -Dspring.profiles.active=dev`.** RED → revert + `NEEDS_CONTEXT`. Watch the Season/Team/Driver-specific test classes (`SeasonRestorerTest`, `TeamRestorerIT`, etc.) AND the cross-cluster `BackupRoundTripIT` (which exercises both clusters' restorers).
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.restore cluster A" &amp;&amp; git log -1 --pretty=%B | grep -q "scope_sanity"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.restore cluster A (Season/Driver/Team + SPI) — 1/2 of scope_sanity split` lands
    - Commit body references "scope_sanity: 27-files-task split per checker iteration 2"
    - Cluster A `batchUpdate` count unchanged
    - 2-pass pattern preserved in any Cluster A 2-pass restorers
    - Cluster B files (Race/Playoff/Calendar/GT7) are NOT staged in this commit
    - `/tmp/79-02f-restorer-count.txt` saved for Task 2b
  </acceptance_criteria>
  <done>Cluster A commit lands; tests GREEN; Cluster B files left for Task 2b.</done>
</task>

<task type="auto">
  <name>Task 2b: Cleanup org.ctc.backup.restore Cluster B (Race/Playoff/Calendar/GT7) — 1 commit, ~15 classes</name>
  <files>src/main/java/org/ctc/backup/restore/{Race*Restorer.java,Result*Restorer.java,Playoff*Restorer.java,Matchday*Restorer.java,Match*Restorer.java,Calendar*Restorer.java,Gt7*Restorer.java,GT7*Restorer.java,*RaceResult*Restorer.java,*RaceLineup*Restorer.java}, src/test/java/org/ctc/backup/restore/{Race*,Result*,Playoff*,Matchday*,Match*,Calendar*,Gt7*,GT7*}</files>
  <read_first>
    - `RaceResultRestorer.java` (Match/Race-cluster representative, plan 75-04)
    - `PlayoffRestorer.java` (Playoff/GT7-cluster representative, plan 75-05)
    - `PlayoffMatchupRestorer.java` (2-pass restorer pattern — confirm the 2-pass pattern is preserved)
    - Mirror test files
    - `/tmp/79-02f-restorer-count.txt` and `/tmp/79-02f-cluster-a.txt` from Task 2a (do NOT re-measure whole-package counts — use Task 2a's values for the closing invariant)
    - This plan's `<test_impact>` block — see "scope_sanity: 27-files-task split" rationale
  </read_first>
  <action>
**scope_sanity rationale:** This is the second half of the original Task 2 split. Cluster B covers the Race/Playoff/Calendar/GT7 domain (~15 classes). Cluster A was handled in Task 2a. The combined-package invariants are verified at the END of this task (after Task 2b's commit lands).

Apply the 4-pass cleanup uniformly to the Cluster B `*Restorer.java` files. SPECIFIC PROCEDURE:

1. **Pre-flight Cluster B baseline:**
```
ls src/main/java/org/ctc/backup/restore/ | grep -E "^(Race.*Restorer|Result.*Restorer|Playoff.*Restorer|Matchday.*Restorer|Match.*Restorer|Calendar.*Restorer|Gt7.*Restorer|GT7.*Restorer)\.java$" > /tmp/79-02f-cluster-b.txt
xargs -a /tmp/79-02f-cluster-b.txt -I{} grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/{} | awk '{s+=$1} END{print s}'
```
Record `batchUpdate_count_B_before`.

2. **Comment-thinning:** Standard D-09 + Schutzwortliste rules.

3. **Dead-code:** D-04 forbids deletion of any `*Restorer` class. Within each restorer, private helpers with grep-zero refs may be deleted.

4. **Extract-method:** `PlayoffMatchupRestorer` (2-pass) is the primary CD-02 candidate in this cluster. Apply CD-02 discretion at >30 LOC. `RaceResultRestorer` may also benefit from extracting `restoreLineups()` / `restoreResults()` IF the existing `restore()` has clearly separable phases.

5. **Logic-simplification:** Same rules as Cluster A — `batchUpdate` adjacent comments STAY; loop→stream only for pure transformations.

6. **Post-flight Cluster B invariant verification:**
```
xargs -a /tmp/79-02f-cluster-b.txt -I{} grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/{} | awk '{s+=$1} END{print s}'
```
MUST equal `batchUpdate_count_B_before`.

7. **Combined whole-package invariant verification (the merged Task 2a + Task 2b check):**
```
grep -l "@Component" src/main/java/org/ctc/backup/restore/*Restorer.java | wc -l
```
MUST equal `N_restorers_before` (from Task 2a's `/tmp/79-02f-restorer-count.txt`). If different → STOP, find the missing/extra class, undo the offending edit.

Also verify the whole-package batchUpdate sum:
```
grep -c "batchUpdate" src/main/java/org/ctc/backup/restore/*.java | awk -F: '{s+=$2} END{print s}'
```
MUST equal `batchUpdate_count_A_before + batchUpdate_count_B_before` (sum from Task 2a temp note + Task 2b pre-flight).

8. **Stage + commit (Cluster B only):**
```
git add src/main/java/org/ctc/backup/restore/Race*Restorer.java \
        src/main/java/org/ctc/backup/restore/Result*Restorer.java \
        src/main/java/org/ctc/backup/restore/Playoff*Restorer.java \
        src/main/java/org/ctc/backup/restore/Matchday*Restorer.java \
        src/main/java/org/ctc/backup/restore/Match*Restorer.java \
        src/main/java/org/ctc/backup/restore/Calendar*Restorer.java \
        src/main/java/org/ctc/backup/restore/Gt7*Restorer.java \
        src/main/java/org/ctc/backup/restore/GT7*Restorer.java \
        src/test/java/org/ctc/backup/restore/{Race*,Result*,Playoff*,Matchday*,Match*,Calendar*,Gt7*,GT7*}
```
Verify `git status` shows ONLY Cluster B files.

Commit message: `refactor(79): cleanup org.ctc.backup.restore cluster B (Race/Playoff/Calendar/GT7) — 2/2 of scope_sanity split`. Body includes:
- Cluster B classes touched: ~15 (list them)
- batchUpdate count (Cluster B): unchanged at <N_B>
- Combined invariant: Restorer count unchanged: N_before == N_after = <N_restorers_before>
- Combined batchUpdate count: <N_A> + <N_B> = <total>, unchanged across the package
- 2-pass pattern preserved in PlayoffMatchupRestorer (if applicable)
- scope_sanity: 27-files-task split per checker iteration 2; companion to Task 2a's commit `refactor(79): cleanup org.ctc.backup.restore cluster A (...)`

9. **Run `./mvnw test -Dspring.profiles.active=dev`.** RED → revert + `NEEDS_CONTEXT`. Watch `BackupImportRollbackIT` + per-restorer unit tests + `BackupRoundTripIT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.restore cluster B" &amp;&amp; git log -1 --pretty=%B | grep -q "Restorer count unchanged" &amp;&amp; git log -1 --pretty=%B | grep -q "scope_sanity"</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.restore cluster B (Race/Playoff/Calendar/GT7) — 2/2 of scope_sanity split` lands
    - Commit body references "scope_sanity: 27-files-task split per checker iteration 2"
    - Cluster B `batchUpdate` count unchanged
    - Combined whole-package invariants hold: N_restorers_before == N_restorers_after AND combined batchUpdate count = sum of Cluster A + Cluster B baselines
    - No Schutzwortliste keyword deleted (`auditing`, `AuditingEntityListener`, `auto-commit`)
    - 2-pass pattern preserved in PlayoffMatchupRestorer (if applicable)
  </acceptance_criteria>
  <done>Cluster B commit lands; tests GREEN; combined whole-package restorer-SPI count + batchUpdate count + 2-pass-pattern invariants hold across Task 2a + Task 2b.</done>
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
- Up to 4 atomic commits land on `gsd/v1.10-platform-and-backup`: Task 1 (`backup.service`), Task 2a (`backup.restore` Cluster A — Season/Driver/Team + SPI), Task 2b (`backup.restore` Cluster B — Race/Playoff/Calendar/GT7), Task 3 (`backup.exception`) — Task 2 was split per scope_sanity checker iteration 2 (original 27-files-task exceeded the 15-files warning threshold).
- `./mvnw test` BUILD SUCCESS after each commit
- Phase 75 step ordering preserved in `BackupImportService.execute(UUID)`
- 24+ `*Restorer` `@Component` classes are all present (combined whole-package count invariant verified at the end of Task 2b)
- `jdbcTemplate.batchUpdate` call sites unchanged (per-cluster check at Task 2a / Task 2b + combined check at Task 2b close)
- No exception class deleted or renamed
</verification>

<success_criteria>
- 2-4 atomic per-package commits land (1 for `service`, 2 for `restore` Cluster A + B, 0-1 for `exception`)
- `./mvnw test` BUILD SUCCESS
- All Phase 75/74 step + restorer-SPI + auditing-bypass invariants hold
- All Schutzwort keywords intact
- Branch unchanged
- scope_sanity split (Task 2a + Task 2b) reflected in 79-02f-SUMMARY.md with both commit SHAs and the rationale "27-files-task split per checker iteration 2"
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02f-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs (Task 1 + Task 2a + Task 2b + Task 3), N/M/P/Q counters, N_restorers_before == N_restorers_after invariant (combined whole-package check at Task 2b), per-cluster batchUpdate counts + combined batchUpdate sum, Phase 75 step-ordering visibility check. Document the scope_sanity split rationale: "27-files-task split per checker iteration 2; Task 2 (backup.restore, 27 files) became Task 2a (Cluster A ~12 classes — Season/Driver/Team + SPI + Noop/Reserve) and Task 2b (Cluster B ~15 classes — Race/Playoff/Calendar/GT7)".
</output>
