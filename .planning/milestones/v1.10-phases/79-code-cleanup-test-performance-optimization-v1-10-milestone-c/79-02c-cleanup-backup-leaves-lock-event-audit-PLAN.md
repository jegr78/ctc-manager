---
phase: 79
plan: 02c
type: execute
wave: 2
depends_on: [79-01]
files_modified:
  - src/main/java/org/ctc/backup/lock/**
  - src/test/java/org/ctc/backup/lock/**
  - src/main/java/org/ctc/backup/event/**
  - src/test/java/org/ctc/backup/event/**
  - src/main/java/org/ctc/backup/audit/**
  - src/test/java/org/ctc/backup/audit/**
autonomous: true
requirements: [D-01, D-02, D-03, D-04, D-09, D-10, D-11, D-12, D-13]

must_haves:
  truths:
    - "`ImportLockService` ReentrantLock + `BlockingRestoreFailureInjector.Config` Schutzwortliste-protected comments (`race`, `thread-safe`, `CountDownLatch`) survive"
    - "Spring lifecycle annotations on event listeners (`@TransactionalEventListener`, `@EventListener`) are preserved (D-04)"
    - "`DataImportAuditService` `@Transactional(propagation = REQUIRES_NEW)` boundary is unchanged"
    - "`./mvnw test` BUILD SUCCESS after each per-package commit"
  artifacts:
    - path: "git log on branch gsd/v1.10-platform-and-backup"
      provides: "Up to 3 atomic commits, one per cleaned package"
      pattern: "refactor\\(79\\): cleanup org\\.ctc\\.backup\\.(lock|event|audit) package"
  key_links:
    - from: "Cleaned source files"
      to: "Phase 76 ImportLock + Phase 75 audit semantics"
      via: "behavior-preserving cleanup"
      pattern: "ReentrantLock|@TransactionalEventListener|REQUIRES_NEW"
---

<objective>
Wave 2 cleanup sweep — three small backup-leaf packages (each Early in RESEARCH ordering):

1. `org.ctc.backup.lock` — 3 files (`ImportLockService`, `ImportLockBannerAdvice`, `ImportLockedWriteRejector`). Phase 76 SECU-05/06 code. Schutzwort-heavy: `race`, `thread-safe`, `ReentrantLock`, `CountDownLatch` (in tests). Comment-thinning is the dominant cleanup class.
2. `org.ctc.backup.event` — 1 file (`BackupImportPostCommitListener`). Phase 75. `@TransactionalEventListener(phase = AFTER_COMMIT)` is a Spring lifecycle annotation → D-04 hands-off.
3. `org.ctc.backup.audit` — 3 files (`DataImportAudit` entity, `DataImportAuditRepository`, `DataImportAuditService`). Phase 72 SCHEMA. JPA entity, repository, and `REQUIRES_NEW`-bounded service.

Output: 1-3 atomic per-package commits, each followed by green `./mvnw test`. Cleanup classes per D-02; revert-on-RED rule.
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

<interfaces>
**Schutzwort hotspots in these packages (expect grep hits — preserve):**

- `org.ctc.backup.lock`: comments containing `race`, `thread-safe`, `ReentrantLock`, `CountDownLatch` (in test mirrors), `concurrent`, `deadlock`
- `org.ctc.backup.event`: comments containing `transaction`, `AFTER_COMMIT`, `auditing`, `AuditingEntityListener` (rationale for post-commit pattern)
- `org.ctc.backup.audit`: comments containing `auditing`, `AuditingEntityListener` (bypass rationale), `auto-commit`, `MariaDB`, `H2`, `LONGTEXT` (portable JSON column)

**D-04 forbid-list for these packages (DO NOT DELETE):**
- `@Service`, `@Component`, `@Configuration`, `@Repository` (all 3 packages have these)
- `@Transactional`, `@Transactional(propagation = REQUIRES_NEW)`
- `@EventListener`, `@TransactionalEventListener` and their `phase`/`condition`/`fallbackExecution` attributes
- `@Entity`, `@MappedSuperclass`, `@PrePersist`, `@PreUpdate`, `@PostLoad`, `@PrePersist` (entity lifecycle)
- All JPA-required no-arg constructors on `DataImportAudit` (even if `grep` shows zero refs — JPA reflection)

**Cleanup procedure** inherits from Plan 02a `<interfaces>` block (4 passes; Schutzwortliste; commit pattern; revert-on-RED).
</interfaces>
</context>

<critical_constraints>
- You are working on branch `gsd/v1.10-platform-and-backup`. Do NOT run `git stash`, `git checkout`, `git reset`, or switch branches.
- Implement ONLY the tasks listed below. If you find that other files need changes, report `NEEDS_CONTEXT` instead of expanding scope.
- After each per-package commit, run `./mvnw test` and verify it passes BEFORE moving to the next package.
- Schutzwortliste (D-13): Comments containing any of these words must NOT be deleted: MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, FIXME, deadlock, OSIV, Lombok, Unsafe, transitiv, transitive, pitfall, auto-commit, auditing, AuditingEntityListener.
- Dead-code removal rule (D-04): Only delete when (a) grep finds zero references AND (b) no Spring/JPA/Jackson lifecycle annotation AND (c) not a JPA no-arg constructor / Jackson public setter. Reflection-invoked methods survive automatically. On uncertainty → leave it.
- Each package gets EXACTLY ONE commit. Empty-edit packages SKIP the commit entirely (no empty commits).
</critical_constraints>

<test_impact>
- Packages touched: `org.ctc.backup.lock` (3 files), `org.ctc.backup.event` (1 file), `org.ctc.backup.audit` (3 files) — total 7 source files
- Test classes likely touched (mirror): `ImportConcurrentLockIT`, `ImportLockBannerAdviceIT`, `ImportLockedPostRejectorIT` (the 3 lock ITs that hold `@DirtiesContext` from Plan 01), `BackupImportPostCommitIT`, `DataImportAuditServiceTest`, `DataImportAuditSerializationTest`, `BackupSchemaExclusionIT`, `V7DataImportAuditMigrationIT`
- Mockito stub updates: NONE — services use real Spring transactions, not mocks
- Bridge-only test deletions: NONE
- `@DirtiesContext` invariant: Plan 01 verified all 10 annotations are mandatory; this plan MUST NOT delete the `@DirtiesContext` annotation on `ImportConcurrentLockIT` / `ImportLockBannerAdviceIT` / `ImportLockedPostRejectorIT` even if a cleanup pass tempts (the comment EXPLAINING why is also Schutzwort-protected: `race`, `CountDownLatch`).
- Estimated test edit count: 0-5 comment-thinning edits, no behavioral test changes
- JaCoCo impact: ~0 (comment-dominant cleanup)
</test_impact>

<tasks>

<task type="auto">
  <name>Task 1: Cleanup org.ctc.backup.lock package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/lock/**, src/test/java/org/ctc/backup/lock/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/lock/ImportLockService.java`
    - `src/main/java/org/ctc/backup/lock/ImportLockBannerAdvice.java`
    - `src/main/java/org/ctc/backup/lock/ImportLockedWriteRejector.java` (or its actual filename — confirm via `ls src/main/java/org/ctc/backup/lock/`)
    - Mirror test files in `src/test/java/org/ctc/backup/`
    - Plan 02a `<interfaces>` for the canonical 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup (Plan 02a `<interfaces>` procedure) to all `.java` files under `src/main/java/org/ctc/backup/lock/` + their test mirrors. SPECIFIC INVARIANTS:

- ImportLockService: keep `ReentrantLock` field declaration verbatim. Comments adjacent to `tryLock()` / `unlock()` / `holdCount()` MUST be preserved if they contain `race`, `thread-safe`, `concurrent`, `deadlock`.
- ImportLockBannerAdvice: `@ControllerAdvice` annotation stays. `@ModelAttribute` methods are Spring-reflection-invoked → D-04 forbids deletion.
- ImportLockedWriteRejector: `HandlerInterceptor` `preHandle()` cannot be deleted (Spring lifecycle).
- The 3 `@DirtiesContext` annotations in the lock-IT mirror tests MUST NOT be touched.

Stage `git add src/main/java/org/ctc/backup/lock/ src/test/java/org/ctc/backup/`. Use `git status` to confirm no other paths sneak in (test path mirror is broader so cherry-pick to only the lock-related test files). Commit `refactor(79): cleanup org.ctc.backup.lock package` with the 4-counter body. Run `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT` with failing test names.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; git log -1 --pretty=%B | grep -q "refactor(79): cleanup org\.ctc\.backup\.lock package" &amp;&amp; grep -q "@DirtiesContext" src/test/java/org/ctc/backup/it/ImportConcurrentLockIT.java</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.lock package` lands
    - 3 `@DirtiesContext` annotations on `ImportConcurrentLockIT` / `ImportLockBannerAdviceIT` / `ImportLockedPostRejectorIT` are PRESENT and unchanged
    - No Schutzwortliste keyword deleted (especially `race`, `thread-safe`, `CountDownLatch`)
    - `ReentrantLock` field in `ImportLockService` unchanged
  </acceptance_criteria>
  <done>Single commit lands; `./mvnw test` GREEN; lock-IT `@DirtiesContext` invariant holds.</done>
</task>

<task type="auto">
  <name>Task 2: Cleanup org.ctc.backup.event package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/event/**, src/test/java/org/ctc/backup/event/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/event/BackupImportPostCommitListener.java` (or actual filename)
    - Mirror test files for post-commit listener
    - Plan 02a `<interfaces>` for the canonical 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to the single file in `src/main/java/org/ctc/backup/event/` + its test mirror. SPECIFIC INVARIANTS:

- `@TransactionalEventListener(phase = AFTER_COMMIT)` and its `phase` attribute MUST stay (Spring lifecycle annotation, D-04 forbids deletion). Comments explaining WHY post-commit (file-system mutations outside JPA transaction) MUST be preserved — they likely contain `auditing` or `transaction` Schutzwort hits.
- If the package has no cleanup-eligible edits (1-file package, ~40-80 LOC, possibly already terse): SKIP the commit and record "no eligible edits" in the SUMMARY. Empty commits are forbidden.

Stage `git add src/main/java/org/ctc/backup/event/ src/test/java/org/ctc/backup/event/` (or skip if no edits). Commit `refactor(79): cleanup org.ctc.backup.event package` with the 4-counter body. Run `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; grep -q "@TransactionalEventListener" src/main/java/org/ctc/backup/event/*.java</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Either a commit `refactor(79): cleanup org.ctc.backup.event package` exists OR the SUMMARY records "no eligible edits" for this package
    - `@TransactionalEventListener` and `phase = AFTER_COMMIT` (or `TransactionPhase.AFTER_COMMIT`) attribute are PRESENT in the listener class
    - No Schutzwortliste keyword deleted
  </acceptance_criteria>
  <done>Either a commit lands or the package is documented as skipped; `./mvnw test` GREEN; Spring lifecycle annotation invariant holds.</done>
</task>

<task type="auto">
  <name>Task 3: Cleanup org.ctc.backup.audit package (1 commit)</name>
  <files>src/main/java/org/ctc/backup/audit/**, src/test/java/org/ctc/backup/audit/**</files>
  <read_first>
    - `src/main/java/org/ctc/backup/audit/DataImportAudit.java` (entity)
    - `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java`
    - `src/main/java/org/ctc/backup/audit/DataImportAuditService.java`
    - Mirror test files
    - Plan 02a `<interfaces>` for the canonical 4-pass procedure
  </read_first>
  <action>
Apply the 4-pass cleanup to the 3 files under `src/main/java/org/ctc/backup/audit/` + their test mirror. SPECIFIC INVARIANTS:

- `DataImportAudit` is a `@Entity` — D-04 forbids deletion of: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@Enumerated`, `@PrePersist`, `@PreUpdate`, and the no-arg constructor (JPA reflection). The `auditing`/`AuditingEntityListener` Schutzwort hits MUST be preserved (rationale for why this entity does NOT use `@EntityListeners(AuditingEntityListener.class)` — it carries IMPORTED timestamps, not project-time timestamps).
- `DataImportAuditRepository extends JpaRepository&lt;DataImportAudit, UUID&gt;` — no methods to extract; class-Javadoc may be condensed.
- `DataImportAuditService.write(...)` is `@Transactional(propagation = REQUIRES_NEW)` — keep the annotation verbatim. Comments explaining `REQUIRES_NEW` (audit row survives outer rollback) MUST be preserved (Schutzwort: `auditing`).
- `LONGTEXT` JSON column comments (H2 + MariaDB portability rationale) MUST be preserved (Schutzwort: `MariaDB`, `H2`).

Stage `git add src/main/java/org/ctc/backup/audit/ src/test/java/org/ctc/backup/audit/`. Confirm via `git status`. Commit `refactor(79): cleanup org.ctc.backup.audit package` with the 4-counter body. Run `./mvnw test -Dspring.profiles.active=dev`. RED → revert + `NEEDS_CONTEXT`.
  </action>
  <verify>
    <automated>./mvnw test -Dspring.profiles.active=dev -q 2>&amp;1 | tail -3 | grep -q "BUILD SUCCESS" &amp;&amp; grep -q "REQUIRES_NEW" src/main/java/org/ctc/backup/audit/DataImportAuditService.java &amp;&amp; grep -q "@Entity" src/main/java/org/ctc/backup/audit/DataImportAudit.java</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw test` BUILD SUCCESS
    - Commit `refactor(79): cleanup org.ctc.backup.audit package` lands (or SUMMARY records "no eligible edits")
    - `@Transactional(propagation = REQUIRES_NEW)` annotation preserved on the audit-write method
    - `@Entity` + JPA no-arg constructor preserved on `DataImportAudit`
    - No Schutzwortliste keyword deleted (especially `auditing`, `MariaDB`, `H2`)
  </acceptance_criteria>
  <done>Commit lands (or skipped + documented); `./mvnw test` GREEN; JPA lifecycle + REQUIRES_NEW invariants hold.</done>
</task>

</tasks>

<verification>
- Up to 3 atomic commits on `gsd/v1.10-platform-and-backup` for `lock`/`event`/`audit`
- `./mvnw test` BUILD SUCCESS at HEAD
- The 3 `@DirtiesContext` lock-IT annotations are unchanged
- `@TransactionalEventListener(phase = AFTER_COMMIT)` on post-commit listener is unchanged
- `@Transactional(propagation = REQUIRES_NEW)` on `DataImportAuditService` is unchanged
- `@Entity` + JPA no-arg constructor on `DataImportAudit` are unchanged
</verification>

<success_criteria>
- 1-3 atomic per-package commits land
- `./mvnw test` BUILD SUCCESS
- All Schutzwort + Spring/JPA lifecycle invariants hold
- Branch unchanged
</success_criteria>

<output>
After completion, create `.planning/phases/79-code-cleanup-test-performance-optimization-v1-10-milestone-c/79-02c-SUMMARY.md` per `@$HOME/.claude/get-shit-done/templates/summary.md`. Include: per-package commit SHAs, N/M/P/Q counters, lifecycle-annotation-preservation proofs, Schutzwort-grep result.
</output>
