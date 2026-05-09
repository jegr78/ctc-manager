---
phase: 65-graphics-bridge-migration
verified: 2026-05-08T12:00:00Z
status: passed
score: "3/3 UAT tests PASS · SC1 grep gate (calculateStandings(seasonId) usage) = 0 · JaCoCo line coverage 87.8% (gate 82%)"
overrides_applied: 0
authored: retroactive (Phase 69 SC2 milestone-closure bookkeeping per D-08)
source_of_truth:
  - .planning/phases/65-graphics-bridge-migration/65-UAT.md
  - .planning/phases/65-graphics-bridge-migration/65-01-SUMMARY.md
  - .planning/phases/65-graphics-bridge-migration/65-02-SUMMARY.md
  - .planning/phases/65-graphics-bridge-migration/65-03-SUMMARY.md
gaps: []
deferred: []
human_verification: []
---

# Phase 65: Graphics Bridge Migration — Verification Report

Phase 65 replaced the legacy `calculateStandings(UUID seasonId)` bridge with the canonical `calculateStandings(phaseId, groupId)` call sites across 5 graphics services (`AbstractMatchdayGraphicService`, `TeamCardService`, `OverlayGraphicService`, `SettingsGraphicService`, `LineupGraphicService`), the sitegen integration test, and the admin layer; deleted the bridge method declaration; inlined the private `calculateBuchholzScores` helper; and removed the dead `SwissPairingService.calculateBuchholz` public API. This `65-VERIFICATION.md` is a retroactive artifact authored 2026-05-08 in Phase 69 SC2 (milestone closure hygiene): Phase 65 originally shipped on `65-UAT.md status: complete` per project precedent (UAT.md is the BDD-style behavior contract for this phase). The trigger for this retroactive authoring is the `v1.9-MILESTONE-AUDIT.md` 2026-05-08 entry for `65-graphics-bridge-migration`: _"No 65-VERIFICATION.md artifact — phase ships with 65-UAT.md status: complete instead. ROADMAP marks ✅ shipped 2026-05-07. Bookkeeping debt only."_

This report mirrors `65-UAT.md` UAT entries verbatim (3/3 PASS) and adds the SC1 grep proof + JaCoCo 87.8% line-coverage figure cited in the audit. No new evidence claims beyond what the UAT and the 3 plan SUMMARYs (`65-01`, `65-02`, `65-03`) already document.

## Goal Achievement

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: bridge `calculateStandings(UUID seasonId)` removed | VERIFIED | `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` = 0 (Plan 65-03 acceptance gate; live result at Phase 65 close: 0) |
| 2 | SC2: LEAGUE behavior preservation (graphics pixel-identical) | VERIFIED | UAT Tests 1 + 2 PASS — see UAT entries below |
| 3 | D-12: GROUPS layout uses group-scoped standings (NEW behavior) | VERIFIED | UAT Test 3 PASS — Group A standings filtered correctly (seed assignments confirm group-scope, not combined-scope) |
| 4 | SC3 / QUAL-01 reinforcement: JaCoCo line coverage ≥ 82% | VERIFIED | 87.8% measured at Phase 65 close (5.8 pp headroom; `pom.xml` `<minimum>0.82</minimum>` unchanged) |

## UAT Test Results (3/3 PASS — mirrored from 65-UAT.md)

### Test 1 — LEAGUE Team Card — points/record sanity

- **Expected:**
  Generate a team card for a team in a LEAGUE-layout season (e.g., 2026 S4).
  The card's Points and Record values must match the same team's row in
  /admin/standings?phaseId=<REGULAR-phase>. No visual difference vs. pre-Phase-65.
- **Result:** pass
- **Evidence:**
  Verified with playwright-cli + dev,demo profile on http://localhost:9090.
  Season 2026 S4 (Active, LEAGUE), team ADR (Adrenaline Racing).
  - Standings (combined LEAGUE phase): ADR — 5 played / 3W / 0D / 2L / 9 pts
  - Team card (PNG download): POINTS = 9, RECORD = "3 - 2 - 0"
  Card format is W-L-D per StandingsService.TeamStanding.getMatchRecord()
  (line 393-395). 3W - 2L - 0D matches standings 3W-0D-2L exactly.
- **Screenshots:**
  - `.screenshots/65-uat-test1-card-league.png`
  - `.screenshots/65-uat-test1-standings-league.png`

### Test 2 — LEAGUE Matchday Overview graphic — combined standings sanity

- **Expected:**
  For a LEAGUE-layout matchday (any matchday in 2026 S4 or 2024 S2), generate
  the matchday overview graphic. The seed numbers and team records shown next
  to each pairing must match the combined /admin/standings page for the REGULAR
  phase. No regression vs. pre-migration.
- **Result:** pass
- **Evidence:**
  Season 2026 S4 Matchday 1 Overview graphic generated.
  Standings (combined LEAGUE) ordering: 1=ADR, 2=EGP, 3=HMS, 4=ICL, 5=NFR, 6=PWR,
  7=SVT, 8=SGM B, 9=SGM S, 10=TBR B, 11=TBR G, 12=TBR R, 13=VRX A, 14=VRX B.
  Graphic seed assignments observed:
  - Seed 1=ADR, 2=EGP, 3=HMS, 4=ICL, 5=NFR, 6=PWR, 7=SVT
  - Seed 8=SGM B, 9=SGM S, 10=TBR B, 11=TBR G, 12=TBR R, 13=VRX A, 14=VRX B
  All 14 seeds + records (3-2-0 / 2-3-0 W-L-D format) match standings exactly.
  AbstractMatchdayGraphicService.prepareBaseContext correctly resolves combined
  view for LEAGUE-layout phase (matchday.getGroup() = null → groupId = null).
- **Screenshots:**
  - `.screenshots/65-uat-test2-matchday-league.png`

### Test 3 — GROUPS Matchday Overview graphic — group-specific standings (NEW behavior, D-12)

- **Expected:**
  For a GROUPS-layout matchday (consolidated 2023 GROUPS season — pick a
  matchday belonging to Group A), generate the matchday overview graphic.
  The seed numbers and team records shown must reflect GROUP A standings
  (not the combined-view across both groups). This is the only user-visible
  behavior CHANGE in Phase 65 (matchday-context graphics now scope standings
  to the matchday's group via matchday.getPhase() + matchday.getGroup() per
  D-06 + D-12).
- **Result:** pass
- **Evidence:**
  Season 2023 GROUPS phase. Group A — Matchday 1 Overview graphic generated.
  - Group A standings (filtered): 1=ADR, 2=ICL, 3=SVT, 4=HMS, 5=NFR, 6=VRX A
  - Combined standings (cross-group): 1=ADR, 2=EGP, 3=ICL, 4=PWR, 5=SVT,
    6=VRX B, 7=HMS, 8=NFR, 9=SGM B, 10=SGM S, 11=TBR R, 12=VRX A
  Graphic seed assignments observed:
  - Seed 1=ADR, 2=ICL, 3=SVT, 4=HMS, 5=NFR, 6=VRX A
  All 6 seeds match Group A's filtered ranking, NOT the combined ranking.
  If combined-view had been used, the graphic would show ICL=seed 3 (not 2),
  SVT=seed 5 (not 3), and VRX A=seed 12 (not 6). The graphic shows ICL=2,
  SVT=3, VRX A=6 — confirming D-12 group-specific scoping is active.
  Records "3-0-0" / "0-3-0" (W-L-D) match Group A standings exactly.
- **Screenshots:**
  - `.screenshots/65-uat-test3-matchday-groupA.png`
  - `.screenshots/65-uat-test3-standings-groupA.png`
  - `.screenshots/65-uat-test3-standings-combined.png`

### UAT Summary

- total: 3
- passed: 3
- issues: 0
- pending: 0
- skipped: 0

## SC1 Grep Gate

Plan 65-03 acceptance gate: `grep -nR "calculateStandings(seasonId" src/main/java | wc -l` MUST equal 0.

Live result at Phase 65 close: 0. Bridge fully removed; all 11 main-code callers + 5 graphics services pass `(phaseId, groupId)`. Confirmed in `v1.9-MILESTONE-AUDIT.md` "Wiring Summary" table.

## Behavior Preservation Evidence

Records use W-L-D format per `StandingsService.TeamStanding.getMatchRecord()` (lines 393-395 — `wins + " - " + losses + " - " + draws`).

| Test | Behavior | Layout | Result |
|------|----------|--------|--------|
| Test 1 (Team Card) | LEAGUE — combined-phase standings preserved (groupId=null) | LEAGUE (2026 S4) | POINTS=9, RECORD "3 - 2 - 0" matches /admin/standings exactly |
| Test 2 (Matchday Overview) | LEAGUE — 14 combined-phase seeds preserved (matchday.getGroup() = null → groupId = null) | LEAGUE (2026 S4 MD1) | 14 seeds + records (3-2-0 / 2-3-0 W-L-D) match combined standings exactly |
| Test 3 (Matchday Overview) | GROUPS — group-scoped standings (NEW behavior per D-06 + D-12) | GROUPS (2023 Group A MD1) | 6 seeds match Group A filtered ranking, NOT combined; records "3-0-0" / "0-3-0" match Group A standings exactly |

## Per-Plan SUMMARY References

- `65-01-SUMMARY.md` — graphics-service migration: 5 services migrated to canonical API; 5 LEAGUE-regression test methods added (D-11); 3 GROUPS-tests added (D-12); new `SettingsGraphicServiceTest` created from scratch; 8 stub rewrites in `AbstractMatchdayGraphicServiceTest` (D-10); `TeamCardService` constructor signature change (`SeasonPhaseService` injected for `findRegularPhase`).
- `65-02-SUMMARY.md` — Buchholz inlining (D-04a/b): public `SwissPairingService.calculateBuchholz` deleted (~22 lines); orphaned `getPlayedOpponents` overloads removed; `StandingsService.calculateBuchholzScores` private helper inlined into `calculateBuchholzScoresForPhase`; production-code grep gate `calculateStandings(season.getId()|seasonId` reaches 0 in `src/main/java` at end of Wave 2.
- `65-03-SUMMARY.md` — bridge removal + StandingsServiceTest triage (SC1 acceptance gate): `StandingsService.calculateStandings(UUID seasonId)` deleted (~13 lines); 3 bridge-only `StandingsServiceTest` methods deleted (2 duplicate semantics + 1 bridge-delegation); 10 test call sites rewritten to canonical API; `SiteGeneratorServiceIT` negative-verify removed (would no longer compile); `MatchdayScheduleGraphicServiceTest` stub rewritten inline (Rule-1 deviation, missed by Wave 1 inventory). Atomic commit `6523959 refactor(65): remove deprecated calculateStandings(seasonId) bridge`.

## JaCoCo Coverage

- Phase 65 close measurement: **87.8%** line coverage (per `v1.9-MILESTONE-AUDIT.md` line 134; `65-03-SUMMARY.md` reports 5925/6748 lines covered = 87.8%; baseline at Phase 64 close was 85.6%, net gain +2.2 pp from the 8 new D-11/D-12 tests outweighing the 3 deleted bridge tests).
- `pom.xml` `<minimum>0.82</minimum>` threshold unchanged; 5.8 pp headroom above the gate.
- `./mvnw verify` at Plan 65-03 close: 1229 tests, 0 failures, 0 errors, 4 skipped — BUILD SUCCESS.
- Phase-closing `./mvnw verify -Pe2e`: Surefire 1229 / 0 / 0 + Failsafe E2E 31 / 0 / 0 — BUILD SUCCESS.

## Branch Hygiene

All Phase 65 commits landed on `gsd/v1.9-season-phases-groups`:

- Wave 1 (Plan 65-01): `8ad7952 refactor(65): migrate graphics services to phase-aware standings API`, `3051417` (plan summary)
- Wave 2 (Plan 65-02): `190110f refactor(65): delete dead Buchholz API + inline calculateBuchholzScores`
- Wave 3 (Plan 65-03): `6523959 refactor(65): remove deprecated calculateStandings(seasonId) bridge`

No subagent attempted a branch switch or destructive git operation. Per `65-02-SUMMARY.md`: branch invariant verified post-commit each wave.

## Source Documents

- Primary UAT: `.planning/phases/65-graphics-bridge-migration/65-UAT.md` (3/3 PASS; status: complete)
- Plan SUMMARYs: `65-01-SUMMARY.md`, `65-02-SUMMARY.md`, `65-03-SUMMARY.md`
- Audit reference: `.planning/v1.9-MILESTONE-AUDIT.md` (2026-05-08 entry for `65-graphics-bridge-migration`; lines 130-134, 156, 187 confirm 3/3 UAT PASS, SC1 grep = 0, coverage 87.8%)
- Frontmatter shape reference: `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` and the just-authored sibling `64-VERIFICATION.md`

---

_Authored retroactively 2026-05-08 (Phase 69 SC2 — milestone closure hygiene)_
_Sources of truth: 65-UAT.md + 65-01..03-SUMMARY.md + v1.9-MILESTONE-AUDIT.md_
_Branch: gsd/v1.9-season-phases-groups_
