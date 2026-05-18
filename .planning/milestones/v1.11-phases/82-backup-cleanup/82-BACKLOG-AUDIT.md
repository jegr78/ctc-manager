# Phase 82 — REVIEW.md Backlog Audit

BACK-02 requires each Phase-75 REVIEW.md item to be traceable to one atomic commit referencing the ID.
Per CONTEXT.md D-01, this is interpreted item-centric: the commit may exist anywhere in master history,
not necessarily as a new Phase-82 commit. 9 items were pre-resolved during Phase 75 and squash-merged to
master via PR #121 (7 Info/Warning WR-02..WR-08 plus 2 Critical CR-01..CR-02 included for completeness);
5 items were resolved by Phase 82 directly (WR-01 + IN-01..IN-04). Cumulative coverage: 12/12 Info/Warning
items (8 WR + 4 IN). The 2 Critical items (CR-01, CR-02) are listed for reference only — they fall outside
the BACK-02 "12 Info/Warning" count.

## Pre-resolved in Phase 75 (master via PR #121)

| REVIEW ID | Status | Commit SHA | Subject |
|-----------|--------|------------|---------|
| CR-01 | resolved in Phase 75 (merged via PR #121) | `b39d003f` | fix(75): CR-01 preserve LinkedHashMap insertion order in audit event |
| CR-02 | resolved in Phase 75 (merged via PR #121) | `4212a3d9` | fix(75): CR-02 sweep orphan uploadsTarget before Step-1 revert |
| WR-02 | resolved in Phase 75 (merged via PR #121) | `34cbecbb` | fix(75): WR-02 entityCount counts only entities that contributed rows |
| WR-03 | resolved in Phase 75 (merged via PR #121) | `2f326b61` | fix(75): WR-03 report audit-write failure in BackupImportException + flash |
| WR-04 | resolved in Phase 75 (merged via PR #121) | `76a9b520` | fix(75): WR-04 make staging .meta corruption explicit in audit source_filename |
| WR-05 | resolved in Phase 75 (merged via PR #121) | `f2e9125d` | fix(75): WR-05 open backup ZIP once via ZipFile (eliminate 24x rescans) |
| WR-06 | resolved in Phase 75 (merged via PR #121) | `a310e4eb` | fix(75): WR-06 surface BackupArchiveException reason in import failure flash |
| WR-07 | resolved in Phase 75 (merged via PR #121) | `930b0788` | fix(75): WR-07 pre-check MAX_ENTRIES before writing extracted upload file |
| WR-08 | resolved in Phase 75 (merged via PR #121) | `ef38ca5d` | fix(75): WR-08 catch Throwable in execute() so OOM still writes audit row |

All 9 commits are squash-merged into master via PR #121 (commit `45aabfd0`).

## Resolved by Phase 82

| REVIEW ID | Status | Commit SHA | Subject |
|-----------|--------|------------|---------|
| WR-01 | resolved in Phase 82 | `c5c9e609` | fix(82): WR-01 extract BackupExecutedByResolver bean |
| IN-01 | resolved in Phase 82 | `491801bd` | chore(82): IN-01 remove no-op @RequiredArgsConstructor (18 restorers) |
| IN-02 | resolved in Phase 82 | `da05d5da` | style(82): IN-02 align restorer annotation order (@Slf4j @Component first) |
| IN-03 | resolved in Phase 82 | `6934044b` | fix(82): IN-03 warn on missing ZIP data entry |
| IN-04 | resolved in Phase 82 | `2dd10775` | fix(82): IN-04 profile-isolate import-backups-dir |

Order matches CONTEXT.md D-02 commit choreography.

---

All SHAs validated via `git log --oneline` on master (CR-01..WR-08) and on the Phase-82 feature branch (WR-01, IN-01..IN-04) as of 2026-05-17. BACK-02 status: complete (12/12 Info/Warning items mapped).
