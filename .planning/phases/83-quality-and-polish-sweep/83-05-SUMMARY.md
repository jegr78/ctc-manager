---
plan: 83-05
requirements: [QUAL-05]
status: complete
date: 2026-05-17
---

# Plan 83-05 — QUAL-05 UAT-02 Legacy Season Smoke Procedure + Result-Slot

## Outcome

QUAL-05 closes the documentation deliverable for the post-deploy UAT-02 legacy-season visual smoke. Two artefacts now exist (per D-26/D-27/D-28): (a) the executable manual procedure at `docs/uat/UAT-02-legacy-season-smoke.md` with all 8 required sections, and (b) the result-slot at `.planning/STATE.md` under a new `## Pending UATs` H2 — the slot carries the literal `pending — to be executed after v1.11 production deploy` marker that downstream tooling and the v1.11 milestone-close gate will detect. No Java compile, no test, no SpotBugs evidence required (D-29).

The live UAT-02 execution stays a post-deploy task — Phase 83 closes when the doc + slot exist (D-28).

## Files Modified / Created

| File | Change |
|------|--------|
| `docs/uat/UAT-02-legacy-season-smoke.md` | NEW — 8 H2 sections (Purpose, Pre-Conditions, Procedure, Pass Criteria, Fail Handling, Result Template, Recording Location) plus the `# UAT-02:` H1 title. References `.screenshots/uat-02/` 6× per `feedback_screenshots_folder`. English-only per CLAUDE.md `## Language`. Pass-criteria bullet 4 explicitly verifies QUAL-01 chip-order regression on legacy data. |
| `.planning/STATE.md` | Inserted new `## Pending UATs` H2 + `### UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)` subsection between `## Deferred Items` and `## Accumulated Context`. Frontmatter (`completed_phases`, `percent`, `last_updated`) intentionally NOT touched — those are Plan 06's responsibility per Task-2 behavior block. |
| `docs/uat/` | NEW directory (didn't exist before this plan). |

## Tests

| Test | Result |
|------|--------|
| (none) | n/a — docs-only commit per D-29 |

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| `docs/uat/UAT-02-legacy-season-smoke.md` exists | ✅ |
| Procedure doc contains `## Pass Criteria` heading (count = 1) | ✅ |
| Procedure doc contains `## Procedure` heading (count = 1) | ✅ |
| Procedure doc contains `## Fail Handling` heading (count = 1) | ✅ |
| Procedure doc contains `## Result Template` heading (count = 1) | ✅ |
| Procedure doc references `.screenshots/` convention (≥ 1) | ✅ (6 refs to `.screenshots/uat-02/`) |
| Procedure doc has no German UI-text terms (Saison/Fahrer/Gruppe) | ✅ (grep returns empty) |
| `.planning/STATE.md` contains literal `pending — to be executed after v1.11 production deploy` | ✅ |
| `.planning/STATE.md` contains `UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)` | ✅ |
| `.planning/STATE.md` references `docs/uat/UAT-02-legacy-season-smoke.md` | ✅ |
| `.planning/STATE.md` has exactly one `## Pending UATs` H2 | ✅ |
| STATE.md frontmatter progress numbers unchanged (`completed_phases: 3`, `percent: 38`) | ✅ (only body edit; frontmatter unmodified — verification owned by Plan 06) |
| Commit on milestone branch `gsd/v1.11-tooling-and-cleanup` | ✅ |

## Notes

- D-27/D-32 gave the planner discretion between STATE.md vs `.planning/milestone-audits/v1.11-UAT-02.md`. STATE.md was chosen because (a) no `milestone-audits/` directory exists yet (creating it for one slot is overkill), (b) STATE.md is already loaded by GSD tooling for status reporting, and (c) the v1.11 milestone-close gate already reads STATE.md.
- The procedure doc uses `<PRODUCTION_BASE_URL>` as a placeholder. CLAUDE.md only documents the dev/local/docker URLs (`localhost:9090/9091/8080`); the production base URL is not pinned in any committed file. The operator fills the placeholder at execution time.
- Pass-criteria bullet 4 deliberately ties UAT-02 back to QUAL-01 (chip-order ascending year) so the post-deploy smoke also validates that the v1.11 refactor didn't regress on real legacy data — gives a forward-looking regression-net beyond what the existing Playwright `DriverDetailSmokeE2ETest` (Test-prefix fixtures only) can prove.
- MD022/MD032 lint warnings on the STATE.md heading were fixed by adding a blank line between the H3 and its following list (single Edit follow-up).
- No `./mvnw verify` invoked. Per D-29 (pure docs commit) and `feedback_test_call_optimization` (no full-verify on intermediate commits).
