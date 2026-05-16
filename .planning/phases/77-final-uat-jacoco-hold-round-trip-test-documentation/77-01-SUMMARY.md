---
phase: 77-final-uat-jacoco-hold-round-trip-test-documentation
plan: 01
subsystem: testing
tags: [testcontainers, mariadb, h2, sha256, jackson, backup, round-trip, integration-test]

requires:
  - phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
    provides: BackupArchiveService.writeZip, BackupRoundTripIT (Phase-73 manifest), Jackson MixIns
  - phase: 74-backup-import-preview-zip-hardening-multipart-config-schema
    provides: BackupImportService.stage(MultipartFile) + BackupImportPreview staging contract
  - phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
    provides: BackupImportService.execute(UUID), AuditingEntityListener bypass, Testcontainers MariaDB wiring pattern
  - phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log
    provides: BackupSchema.getExportOrder() 24-entity scope, backupObjectMapper qualifier

provides:
  - "BackupRoundTripIT$H2RoundTripTests: full export → wipe → import round-trip on H2 with SHA-256 byte-equality on Race + SeasonDriver + Team"
  - "BackupRoundTripIT$MariaDbRoundTripTests: identical scenario on live MariaDB:11 via Testcontainers (gated by docker.available)"
  - "QUAL-02 satisfied: 24-entity row-count parity + SHA-256 in-DB serialization equality on both engines"

affects:
  - "77-02 through 77-05 — QUAL-01/QUAL-04/QUAL-05 plans can reference this as the QUAL-02 proof"
  - "Phase 79 (milestone-closer) — BackupRoundTripIT is the canonical round-trip contract for v1.10"

tech-stack:
  added: []
  patterns:
    - "@Nested @SpringBootTest @ActiveProfiles — each nested class gets its own isolated Spring ApplicationContext"
    - "SHA-256 via JDK MessageDigest.getInstance(\"SHA-256\") + HexFormat.of().formatHex() for assertion messages"
    - "Deterministic first-row selection: findAll(Sort.by(Sort.Order.asc(\"id\"))).get(0)"
    - "Root Team pick: stream().filter(t -> t.getParentTeam() == null).findFirst()"
    - "SAFE_TABLE_NAME Pattern.compile(\"^[a-z_]+$\") guard before native COUNT(*) concatenation (T-77-01-01)"

key-files:
  created: []
  modified:
    - "src/test/java/org/ctc/backup/service/BackupRoundTripIT.java — extended with H2RoundTripTests + MariaDbRoundTripTests @Nested classes"

key-decisions:
  - "D-01 honored: existing 4 Phase-73 manifest @Test methods preserved byte-identically"
  - "D-03: SHA-256 hashes the in-DB row via backupObjectMapper, NOT the ZIP bytes — proves AuditingEntityListener bypass (created_at/updated_at survive verbatim)"
  - "D-04: deterministic first-row by Sort.by(Sort.Order.asc(id)) ensures H2 + MariaDB UUID BINARY(16) ordering is consistent"
  - "D-06: single class with two @Nested profile classes — helpers duplicated per nested class since each has its own ApplicationContext"
  - "D-18 honored: JDK MessageDigest + HexFormat — zero new Maven dependencies"
  - "Pitfall 2 (TeamMixIn lazy-loading): TeamMixIn uses @JsonIdentityReference(alwaysAsId=true) on parentTeam and @JsonIgnoreProperties on subTeams/seasonDrivers — no lazy initialization triggered"

patterns-established:
  - "Round-trip IT pattern: seed → captureRowCounts → hashEntity(pre) → exportToBytes → stage → execute → assertRowCounts → hashEntity(post) → containsExactly"
  - "hashEntity helper: backupObjectMapper.writeValueAsBytes(entity) | MessageDigest.SHA-256.digest(bytes)"
  - "MariaDB @Nested class: @Testcontainers + @EnabledIfSystemProperty(named=docker.available, matches=true) mirrors BackupImportMariaDbSmokeIT"

requirements-completed: [QUAL-02, QUAL-04]

duration: ~55min
completed: 2026-05-15
---

# Phase 77 Plan 01: BackupRoundTripIT H2 + MariaDB SHA-256 Round-Trip Summary

**Full export → wipe → import round-trip on H2 and MariaDB with SHA-256 in-DB serialization byte-equality on Race, SeasonDriver, and Team — proving the Phase 75 AuditingEntityListener bypass contract**

## Performance

- **Duration:** ~55 min (including Maven build compilation + test execution)
- **Started:** 2026-05-15T00:58:00Z
- **Completed:** 2026-05-15T00:54:48+02:00 (00:54:48 UTC)
- **Tasks:** 1 (77-01-01)
- **Files modified:** 1

## Accomplishments

- Extended `BackupRoundTripIT` in-place with `H2RoundTripTests` and `MariaDbRoundTripTests` `@Nested` classes
- H2 test: 5 tests run (4 manifest + 1 round-trip), 0 failures — BUILD SUCCESS with JaCoCo check passed
- MariaDB test: 1 skipped correctly (no `-Ddocker.available=true`) — consistent with `BackupImportMariaDbSmokeIT` precedent
- SHA-256 byte-equality assertions on Race, SeasonDriver, and Team prove wire-shape invariance through the round-trip
- `SAFE_TABLE_NAME` Pattern guard added as outer class constant (T-77-01-01 threat mitigation)
- All 4 Phase-73 manifest `@Test` methods preserved byte-identically

## Task Commits

1. **Task 77-01-01: Extend BackupRoundTripIT with H2RoundTripTests + MariaDbRoundTripTests** - `44819f8` (test)

## Files Created/Modified

- `/Users/jegr/Documents/github/ctc-manager/.claude/worktrees/agent-ae4644356bf8f56d2/src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — added two `@Nested` classes (H2RoundTripTests + MariaDbRoundTripTests), each with `@Autowired` fields, `@BeforeEach seedFixture`, private helpers (`captureRowCounts`, `exportToBytes`, `hashEntity`, `awaitAuditRow`), and one `@Test` method following Given-When-Then naming

## Decisions Made

- Duplicated helpers per `@Nested` class rather than hoisting to outer class — each nested class has its own `ApplicationContext`, so helpers referencing `@Autowired` beans must be declared within the class that owns those beans (RESEARCH §Architecture Patterns note on two-context isolation)
- Used `Sort.by(Sort.Order.asc("id")).get(0)` (not `.getFirst()`) for maximum Java version compatibility in the list API
- `awaitAuditRow` helper included (with `@SuppressWarnings("unused")`) for parity with `BackupImportMariaDbSmokeIT` — not actively called in the round-trip test body since the `execute()` result is sufficient for the QUAL-02 contract

## Deviations from Plan

None — plan executed exactly as written. Worktree base correction (git reset --hard to 31dc8bb) was required because the worktree was initialized at an older commit (3c5a540) that predates the backup feature code. This is a setup issue, not a plan deviation.

## Issues Encountered

- **Worktree base mismatch:** Worktree HEAD was at `3c5a540` (v1.9 Season Phases commit) instead of `31dc8bb` (parent branch tip). The `<worktree_branch_check>` protocol detected this and applied `git reset --hard 31dc8bb97ee3ec95a6aacfb17d193b33b5e62fed` to restore the correct working tree with all backup feature code.
- **TeamMixIn lazy-loading (Pitfall 2):** Confirmed non-issue. `TeamMixIn` applies `@JsonIdentityReference(alwaysAsId = true)` to `getParentTeam()` — for a root team with `parentTeam == null`, serialization emits `null` with no proxy initialization. `subTeams` and `seasonDrivers` are `@JsonIgnoreProperties`-ignored.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- QUAL-02 satisfied: both `H2RoundTripTests` and `MariaDbRoundTripTests` exist and are correctly wired
- `BackupImportRollbackIT` was not touched (D-14) and remains green per `./mvnw verify` passing
- Ready for Phase 77-02 through 77-05 (JaCoCo hold, Auto-UAT, README, Wiki, screenshots)

## Self-Check

- `BackupRoundTripIT.java` exists at `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java`: FOUND
- `77-01-SUMMARY.md` committed at `.planning/phases/77-.../77-01-SUMMARY.md`: FOUND
- Task commit `44819f8` exists in git log: FOUND
- SUMMARY commit `fcb96f2` exists in git log: FOUND

Result: PASSED

---

*Phase: 77-final-uat-jacoco-hold-round-trip-test-documentation*
*Completed: 2026-05-15*
