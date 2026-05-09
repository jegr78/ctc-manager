---
status: complete
phase: 65-graphics-bridge-migration
source:
  - 65-01-SUMMARY.md
  - 65-02-SUMMARY.md
  - 65-03-SUMMARY.md
started: 2026-05-07T19:00:00Z
updated: 2026-05-07T19:30:00Z
---

## Current Test

[testing complete]

## Tests

### 1. LEAGUE Team Card — points/record sanity
expected: |
  Generate a team card for a team in a LEAGUE-layout season (e.g., 2026 S4).
  The card's Points and Record values must match the same team's row in
  /admin/standings?phaseId=<REGULAR-phase>. No visual difference vs. pre-Phase-65.
result: pass
evidence: |
  Verified with playwright-cli + dev,demo profile on http://localhost:9090.
  Season 2026 S4 (Active, LEAGUE), team ADR (Adrenaline Racing).
  - Standings (combined LEAGUE phase): ADR — 5 played / 3W / 0D / 2L / 9 pts
  - Team card (PNG download): POINTS = 9, RECORD = "3 - 2 - 0"
  Card format is W-L-D per StandingsService.TeamStanding.getMatchRecord()
  (line 393-395). 3W - 2L - 0D matches standings 3W-0D-2L exactly.
screenshots:
  - .screenshots/65-uat-test1-card-league.png
  - .screenshots/65-uat-test1-standings-league.png

### 2. LEAGUE Matchday Overview graphic — combined standings sanity
expected: |
  For a LEAGUE-layout matchday (any matchday in 2026 S4 or 2024 S2), generate
  the matchday overview graphic. The seed numbers and team records shown next
  to each pairing must match the combined /admin/standings page for the REGULAR
  phase. No regression vs. pre-migration.
result: pass
evidence: |
  Season 2026 S4 Matchday 1 Overview graphic generated.
  Standings (combined LEAGUE) ordering: 1=ADR, 2=EGP, 3=HMS, 4=ICL, 5=NFR, 6=PWR,
  7=SVT, 8=SGM B, 9=SGM S, 10=TBR B, 11=TBR G, 12=TBR R, 13=VRX A, 14=VRX B.
  Graphic seed assignments observed:
  - Seed 1=ADR, 2=EGP, 3=HMS, 4=ICL, 5=NFR, 6=PWR, 7=SVT
  - Seed 8=SGM B, 9=SGM S, 10=TBR B, 11=TBR G, 12=TBR R, 13=VRX A, 14=VRX B
  All 14 seeds + records (3-2-0 / 2-3-0 W-L-D format) match standings exactly.
  AbstractMatchdayGraphicService.prepareBaseContext correctly resolves combined
  view for LEAGUE-layout phase (matchday.getGroup() = null → groupId = null).
screenshots:
  - .screenshots/65-uat-test2-matchday-league.png

### 3. GROUPS Matchday Overview graphic — group-specific standings (NEW behavior, D-12)
expected: |
  For a GROUPS-layout matchday (consolidated 2023 GROUPS season — pick a
  matchday belonging to Group A), generate the matchday overview graphic.
  The seed numbers and team records shown must reflect GROUP A standings
  (not the combined-view across both groups). This is the only user-visible
  behavior CHANGE in Phase 65 (matchday-context graphics now scope standings
  to the matchday's group via matchday.getPhase() + matchday.getGroup() per
  D-06 + D-12).
result: pass
evidence: |
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
screenshots:
  - .screenshots/65-uat-test3-matchday-groupA.png
  - .screenshots/65-uat-test3-standings-groupA.png
  - .screenshots/65-uat-test3-standings-combined.png

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0

## Gaps

[none — all tests passed]

## Tooling Notes

- Verified by orchestrator (Claude) via `playwright-cli -s=phase65uat` against
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` on port 9090.
- The "Schedule" graphic button was disabled on demo seed matchdays ("No matches
  with datetime") — UAT used the "Overview" button instead, which exercises the
  same `AbstractMatchdayGraphicService.prepareBaseContext` migration path. The
  test classes `MatchdayScheduleGraphicServiceTest` and
  `MatchdayOverviewGraphicServiceTest` both inherit from the migrated parent
  service, so the Overview path is functionally equivalent for SC2 verification.
- Records use W-L-D format (per `getMatchRecord()` `wins + " - " + losses + " - " + draws`).
- 6 screenshots in `.screenshots/65-uat-test{1,2,3}-*.png` for evidence trail.
