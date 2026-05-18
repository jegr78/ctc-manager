# Phase 82: Backup Cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-16
**Phase:** 82-backup-cleanup
**Areas discussed:** Scope alignment (7 of 12 already done), WR-01 + IN-01..IN-04 fix design, New tests for BACK-01/03/05, Commit strategy + test cadence

---

## Scope Alignment

The Phase 82 ROADMAP/SC say "12 items resolved with one atomic commit per item, each commit referencing the REVIEW.md ID". During scout, 7 of the 12 items (WR-02..WR-08) were discovered to be already fixed and merged on master via the v1.10 PR #121. The strict reading of BACK-02 breaks down — Phase 82 cannot meaningfully recommit work that's already in master.

| Option | Description | Selected |
|--------|-------------|----------|
| Re-verify only — 5 new commits + 1 audit doc | Phase 82 commits only WR-01 + IN-01..IN-04 (5 fix commits) + BACK-01/03/05 (3 test commits) + 1 `82-BACKLOG-AUDIT.md` mapping 7 already-resolved items to their commit SHAs. SC#2 interpreted item-centric (each item is traceable to one atomic commit somewhere in master) rather than phase-centric. | ✓ |
| Re-commit all 12 | Cherry-pick + amend 7 pre-resolved commits as fresh Phase-82 commits. 7 no-op diffs, confusing history. | |
| Update SC + ROADMAP first | Edit the ROADMAP SC#2 to reflect the 5+3 reality before proceeding. Heavier process, requires a separate docs commit upstream of the feature branch. | |

**User's choice:** Re-verify only — 5 new commits + 1 audit doc
**Notes:** Clean, honest, no busywork. The 7 pre-resolved items already trace to atomic `fix(75): WR-XX` commits on master via PR #121; the audit doc points to them.

---

## WR-01 + IN-01..IN-04 Fix Design

### WR-01: executedBy duplication

| Option | Description | Selected |
|--------|-------------|----------|
| `@Component BackupExecutedByResolver` bean | New bean in `org.ctc.backup.audit`, injected into both BackupImportService and DataImportAuditService via `@RequiredArgsConstructor`. Pure Spring DI, testable in isolation. | ✓ |
| Static helper in `BackupExecutorContext` utility class | Package-private static method, no Spring DI. Less idiomatic, loses mocking flexibility. | |
| Move single implementation into DataImportAuditService only | Remove from BackupImportService entirely. Eliminates dup but breaks success-path event.executedBy() field reliability. | |

**User's choice:** Inject `@Component BackupExecutedByResolver` bean
**Notes:** Matches CLAUDE.md "Services: `@RequiredArgsConstructor`" convention. One unit test covering 4 resolution branches (dev/local profile, callerOverride, auth, fallback).

### IN-01 + IN-02: Restorer annotation cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| 1 commit IN-01 (18 files) + 1 commit IN-02 (6 files) | Pure cosmetic refactoring, one atomic commit per REVIEW.md ID. | ✓ |
| Combine into 1 commit "chore(82): restorer annotation cleanup (IN-01 + IN-02)" | Both items touch the same files — combine. Violates BACK-02 literally. | |
| Per-restorer commit (18-24 commits) | Strictest atomicity, excessive churn. | |

**User's choice:** 1 commit IN-01 + 1 commit IN-02
**Notes:** Order IN-01 first (strip the no-op annotation), then IN-02 (align remaining annotation order) so the IN-02 diff is minimal.

### IN-03: Missing ZIP entry policy

| Option | Description | Selected |
|--------|-------------|----------|
| WARN log + return 0 | Soft tolerance preserves Javadoc contract; operators see corruption signal in logs and audit row's restoredCounts. | ✓ |
| Fail-loud IllegalStateException | Strict round-trip guarantee; rejects manually-trimmed backups. | |

**User's choice:** WARN log + return 0
**Notes:** Matches existing Javadoc "absent data files are not a hard error" semantics. Test asserts WARN log emission via OutputCaptureExtension.

### IN-04: Profile-isolate import-backups-dir

| Option | Description | Selected |
|--------|-------------|----------|
| Profile-isolate the dir like staging-dir | `data/${spring.profiles.active:dev}/import-backups`. Mirrors staging-dir pattern. Clean. | ✓ |
| Append staging UUID to timestamp directory name | `<ts>-<stagingUuid>` under same root. Cheaper but still cross-profile collidable at parent. | |
| Both — profile-isolate AND UUID-suffix | Defense in depth, overkill for single-operator usage. | |

**User's choice:** Profile-isolate the dir like staging-dir
**Notes:** Mirrors existing staging-dir pattern. Old artifact path stays where it is (24h retention); README path note documents the change.

---

## New Tests (BACK-01 / BACK-03 / BACK-05)

### BACK-01: Schema-version + export-order guard

| Option | Description | Selected |
|--------|-------------|----------|
| New `BackupSchemaGuardTest.java` unit test | Net-new file, two `@Test` methods, no Spring context (or minimal slice). | ✓ |
| Extend existing `BackupSchemaTest.java` if one exists | Add to existing test class if present. | |
| Inline into BackupRoundTripIT | Add to the existing IT alongside BACK-05 check. Couples both guards, wastes IT Spring context startup. | |

**User's choice:** New `BackupSchemaGuardTest.java` unit test
**Notes:** Pure constant-pin guard. Cheapest possible test class. Plain unit test, untagged.

### BACK-03: ZipEntry-open count measurement

| Option | Description | Selected |
|--------|-------------|----------|
| Spy/wrap ZipFile via SecurityManager-free counter | In-source `@VisibleForTesting AtomicInteger zipOpenCounter` field on BackupImportService. 3 lines of production code, package-private, permanently testable. | ✓ |
| Bytecode instrumentation via Mockito.mockStatic on ZipFile | No production-code change, brittle to JDK updates. | |
| Indirect proof via `restoreAll` invocation count | Doesn't directly measure ZipEntry-opens. Less precise. | |

**User's choice:** In-source counter
**Notes:** Three-line production change. Tests live in same package (`org.ctc.backup.service`) for package-private field access. No new Mockito dependency.

### BACK-05: 24-entity row-count parity

| Option | Description | Selected |
|--------|-------------|----------|
| Iterate `getExportOrder()` + `JdbcTemplate` row count | Single `@Test` per `@Nested` class, 24 iterations, detailed failure message. Single source of truth. | ✓ |
| Hand-write 24 assertion lines using typed `repository.count()` | Type-safe but verbose. Brittle when entities are added. | |
| Generic JpaContext metamodel iteration | Cleanest API, heavy boilerplate, overkill. | |

**User's choice:** Iterate `getExportOrder()` + `JdbcTemplate`
**Notes:** Single source of truth. Future entities pick up automatically; BACK-01 guard size check signals the bump moment.

---

## Commit Strategy + Test Cadence

### Commit order

| Option | Description | Selected |
|--------|-------------|----------|
| Code fixes first, then tests, then audit doc | 5 fix commits → 3 test commits → 1 audit doc → STATE/manifest. Tests verify the fixes (BACK-03 verifies WR-05 stays fixed; BACK-05 verifies cleanups didn't drop rows). | ✓ |
| Tests first (TDD-strict) | BACK-01/03/05 first, then fix commits. Cleanest red-green narrative but BACK-03/05 have no red state (WR-05/CR-01 already merged). | |
| Each fix paired with its targeted test | Interleaved. Doubles commit count to ~14. | |

**User's choice:** Code fixes first, then tests, then audit doc
**Notes:** 9-10 commits total. BACK tests at the end serve as the final lock-in of the cleanup.

### Test cadence per commit

| Option | Description | Selected |
|--------|-------------|----------|
| Targeted `-Dtest`/`-Dit.test` per commit + ONE final `./mvnw verify -Pe2e` | Per CLAUDE.md feedback_test_call_optimization. Saves ~25min vs full-verify-per-commit. | ✓ |
| `./mvnw verify` after every commit | Strictest. Burns ~1.5h of redundant test time. | |
| `./mvnw test` (Surefire only) per commit + final `verify -Pe2e` | Cheaper than full verify, misses IT-only regressions. | |

**User's choice:** Targeted `-Dtest`/`-Dit.test` per commit + ONE final `mvnw verify -Pe2e`
**Notes:** Aligns with established v1.11 cadence. MariaDB smoke runs via GitHub workflow `mariadb-migration-smoke.yml` or local `-Dmariadb.smoke=true` before raising PR.

### SpotBugs gate risk

| Option | Description | Selected |
|--------|-------------|----------|
| Run `spotbugs:check` after each commit that touches main/java | WR-01 (new bean), IN-03 (changed log level), IN-04 (yaml — no impact). Restorer cleanups unlikely to flip patterns. ~4 SpotBugs runs total, ~1-2min each. | ✓ |
| Rely on final `mvnw verify -Pe2e` to catch SpotBugs regressions | Cheapest. Late discovery + rework cost on regression. | |

**User's choice:** Run `spotbugs:check` after each main/java commit
**Notes:** Planner has discretion to skip the SpotBugs run for IN-01/IN-02 if the diff is annotation-only (no new bytecode patterns).

---

## Claude's Discretion

- Final wording of `82-BACKLOG-AUDIT.md` (markdown table mapping ID → SHA → one-line evidence).
- README path-change note wording for IN-04 (single sentence vs short paragraph).
- Whether to add the IN-02 annotation-order convention to CLAUDE.md or leave as a one-time normalisation.
- Whether the BACK-01 guard test needs Spring context (`@SpringBootTest` slice) or stays pure (no context).
- Whether the BACK-03 IT can reuse the existing Saison-2023 GROUPS fixture or needs a fresh minimal one.
- Exact log-captor library for IN-03 test (OutputCaptureExtension recommended).
- Branch name for the Phase 82 feature branch.
- Per-commit `spotbugs:check` cadence — skip for annotation-only commits if planner judges safe.

## Deferred Ideas

- CLAUDE.md annotation-order convention as a CI gate (escalate only if drift recurs).
- `BackupSchema` auto-discovery of entities via Hibernate Metamodel (BACK-01 guard remains pin-based; auto-discovery is the future safety net).
- `82-BACKLOG-AUDIT.md` formalised as a project-wide phase template (do once, evaluate adoption).
- Existing `data/.import-backups/<old-path>/` artifact migration script (one-shot rename; Phase 999 backlog).
- Bumping `BackupSchema.SCHEMA_VERSION` to 2 (explicitly forbidden by SC#1; dedicated future migration phase).
- `BackupRestoreZipOpenCountIT` `@Nested` MariaDB variant (only if MariaDB-specific ZipFile lifecycle drift surfaces).
- Promoting `BackupExecutedByResolver` to a general `ExecutorContextResolver` (single-use today).
