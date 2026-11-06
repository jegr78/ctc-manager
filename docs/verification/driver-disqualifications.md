# Driver disqualifications verification

Issue #347 follows merged PR #349. A DSQ removes race-position points and advances following drivers for race scoring; qualifying and fastest-lap bonuses remain. Saved finishing positions remain unchanged. Management enters the separate 20 team penalty points manually.

| Criterion | Production path / creation task | Positive proof | Rejection / compatibility proof | Runtime proof |
|---|---|---|---|---|
| D1 | RaceResult state and new shared effective-classification calculation; ScoringService | P3, multiple DSQs, leader with bonuses, all drivers DSQ, clear DSQ | No DSQ preserves old points, including position gaps; independent race/leg | Real H2/MariaDB persisted results and score recomputation |
| D2 | New management disqualification service, form/controller and immutable change history | Set, correct and clear with reason/trusted actor; original position unchanged | Stale revision/results/restore generation, foreign race/result, invalid reason/state, decided playoff | Native HTTP, CSRF, retained binding/domain errors, unchanged rows on rejection |
| D3 | ScoreWriteLockService, PickWriteExecutor and current match/playoff aggregation | Changed race/bonus points reaggregate every leg and correct team totals | Concurrent edits, decided playoff and manual team penalty totals unchanged | H2/MariaDB regular/playoff and concurrency workflows |
| D4 | RaceService result editing, CsvImportService and shared Sheet route | Native edits preserve result identity and DSQ history; permitted result import | Results-only import cannot erase DSQ/current or cleared history; reject before mutation when necessary | Native save, CSV/Sheet overwrite and multi-leg preflight |
| D5 | Race/match/season/lineup/scoring lifecycle guards | Existing valid changes remain available | DSQ history cannot silently move scope, disappear or lose driver ownership | Management/import lifecycle integration tests |
| D6 | Admin/public result views and result graphics using shared classification | DSQ label, retained bonuses, advanced classifications and original-position evidence | Cleared DSQ restores baseline; no private management reason in public output | Desktop/mobile native UI, site generation and actual PNG |
| D7 | DriverRankingService and alltime/statistics consumers | Correct points, wins/podiums/best positions and DSQ result display | No DSQ invents no classification change; no DSQ podium/position statistic | Ranking/sitegen/graphics fixtures and standings/playoff assertions |
| D8 | New V38 migration and backup schema 16; typed export/restorers/preflight | H2/MariaDB fields/history roundtrip and exact references | Schemas 1–15 default to no DSQ; malformed state/history rejects before wipe, including tampered execute | Full archive restore and rollback tests |
| D9 | Read-only data audit | Valid DSQ/history and recomputed points have no finding | Inconsistent DSQ/history yields safe ID-only findings without repair or private reasons | Synthetic corruption, no-write assertions and restore-generation checks |
| D10 | CONTEXT/README/CLAUDE/Wiki, review and delivery gates | Canonical full lifecycle, independent security review, individual falsified guards, green exact-head/current-base CI | No weakened gates, automatic extra team deduction or silent import erasure | Actual merged PR, Wiki readback, issue hierarchy and owned-workspace cleanup |

The matrix describes the implemented production paths and their required proof. TDD and delivery outcomes are recorded in issue #347 and its delivery PR. Effective classification preserves results without DSQ, including pre-existing position gaps; it must not compact unrelated gaps.

## Scoring proof

The first ten cases ran before production scoring changes and produced seven intended assertion failures with zero test errors. A temporary result subtype supplied the new status without adding production behavior; after implementation it was replaced by the real persistent entity flag, retaining every assertion. Shared classification preserves recorded positions and unrelated historical gaps, excludes DSQs from race points and keeps both bonuses. The initial selected verification passed 54 DSQ/scoring/ranking unit cases with zero failures/errors. Selected lifecycles stop at the global coverage floor and are never reported as a complete green build.

## Migration proof

The V38 connection-level tests first produced three intended assertion failures with zero errors against schema 37. With the new migration, all 57 selected scoring/ranking/migration cases pass. Legacy rows retain their recorded position and points and default to no DSQ. Fresh connections enforce DSQ race points, non-null flags and revision constraints. Later H2/MariaDB management and archive cases exercise the complete ORM schema and typed restorers.

## Management, presentation and archive proof

The native editor's result-ID regression first failed at its intended assertion. In-place editing now preserves concrete results, DSQ state and permanent history. Six admission/HTTP cases first failed at assertions with zero errors, then passed with 153 selected unit and eight integration cases. Authentication, CSRF, trusted actors, escaped reasons, retained binding/domain errors and unchanged rejected state are covered.

Ranking and public/graphic/audit cases first produced two and three intended assertion failures respectively, without test errors. The shared classification now excludes DSQs from best-position credit while preserving appearances and bonuses. Default graphics and generated public pages contain DSQ and corrected scores without private correction metadata. Actual PNG review caught a clipped marker, which now has independent space.

Archive TDD ran 48 cases against an empty validation shell, producing 47 intended failures and zero errors. Implemented schema 16 normalization, typed export/restorers and prewipe validation then passed 102 selected unit and 20 schema/topology integration cases. Independent review added two regression cases for nanosecond loss and duplicate race/driver ownership; both first failed at assertions and are fixed.

The complete selected feature run passes 122 unit and 126 integration cases. H2 and MariaDB each execute all 15 legacy schema restores, exact new-history roundtrip and 14 malformed archive cases at staging and tampered execute, with never-wipe assertions. Management tests cover result/lineup stale contexts, concurrent DSQ writers, source-driver merging, CSV/Sheets whole-import rejection, standings/alltime rankings, standalone playoff decisions and restore writer admission.

## Review and remaining delivery gates

Code review and an independent security pass fixed ambiguous positions, guest-removal admission, timestamp precision, duplicate archive ownership, flag-only indirect playoff identity and nullable bye presentation. Both final edge regressions were observed as exactly two assertion failures with zero errors. The independent pass reports no remaining production findings. A scoped history-table layout keeps columns readable on phones. Native/public browser tests cover desktop/mobile with JavaScript disabled and the live points preview with JavaScript enabled; visible public assertions select the responsive view explicitly.

Live `playwright-cli` review covers the native 1.49.0-SNAPSHOT dev server and the exact generated public HTML served with the repository's public assets, on desktop and phone. The internal history scrolls by keyboard within its region while the page width stays at 390 pixels. Visual review caught a wrapped mobile DSQ label; the public position column now reserves four characters and prevents wrapping. A real DOM Range assertion verifies one rendered text line. The corrected layout passes 65 selected unit and both browser cases, and CLI screenshots confirm the readable marker and the 5:82 public score. The first full run was deliberately stopped before this correction and is not counted as final verification.

The PR is the record for the final canonical precommit and committed-head lifecycle counts, SAST/coverage, individually falsified guards, exact reviewed/verified head and current base, seven green CI checks, actual merge, published Wiki and tracker reconciliation. Those gates are required in addition to the targeted proof above.
