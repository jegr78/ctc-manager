---
plan_id: 61-gap-01
phase: 61-cleanup-quality-gate
title: Stale comments — domain.model + domain.repository + domain.service
wave: 1
gap_closure: true
autonomous: true
depends_on: []
files_modified:
  - src/main/java/org/ctc/domain/model/Season.java
  - src/main/java/org/ctc/domain/model/Matchday.java
  - src/main/java/org/ctc/domain/model/Playoff.java
  - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
  - src/main/java/org/ctc/domain/repository/MatchRepository.java
  - src/main/java/org/ctc/domain/repository/RaceRepository.java
  - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
  - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonRepository.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/PlayoffService.java
  - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
  - src/main/java/org/ctc/domain/service/SeasonPhaseService.java
  - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  - src/main/java/org/ctc/domain/service/MatchdayService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/SwissPairingService.java
  - src/main/java/org/ctc/domain/service/RaceCalendarService.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "grep -rn -E 'Wave [0-9]+ cascade|cascade migration|transitional bridge|bridge field' src/main/java/org/ctc/domain/ returns 0 lines"
  - "grep -rn -E 'Phase 6[01] (MIGR-06|WR-[0-9]+|CR-[0-9]+|IN-[0-9]+|D-[0-9]+)' src/main/java/org/ctc/domain/ returns 0 lines"
  - "Pure WHAT-comments removed where the identifier already explains itself; only WHY remains (CLAUDE.md)"
  - "./mvnw test stays GREEN with no behavior change"
  - "JaCoCo line coverage stays at or above 82% (D-21)"
---

<objective>
Remove stale Phase-narrative comments from all 78 files in `src/main/java/org/ctc/domain/` (model, repository, service). The cascade migration (Phase 56-61) added comments referencing concrete tasks/PRs/incidents (`Phase 61 MIGR-06`, `D-22`, `WR-07`, `CR-01`, `Wave 2 cascade`, `transitional bridge field`) that violate CLAUDE.md guidance: "Don't explain WHAT the code does ... don't reference the current task, fix, or callers — those belong in the PR description and rot as the codebase evolves." This plan addresses gap **G2 — stale comments** for the `domain` layer.

Purpose: clean up the highest-density area (~437 D-XX hits codebase-wide; domain alone holds the cluster of MIGR-06 narratives). Comment-only changes — zero behavior risk.

Output: all listed files cleaned of phase-narrative comments. Test gate stays green.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@CLAUDE.md

**CLAUDE.md hard rules (binding):**
- "default to no comments" — only add WHY when non-obvious
- "validate only at system boundaries" — internal services trust contracts
- Test coverage minimum 82%

**What counts as STALE (REMOVE):**
1. Phase narrative: `// Phase 61 MIGR-06: ...`, `// Phase 60 D-43 ...`, `// D-22: ...`, `// CR-01: ...`, `// WR-07: ...`, `// IN-05: ...`
2. Cascade-history: `// Wave 2 cascade migration ...`, `// transitional bridge field`, `// formerly bridged via ...`
3. Pure WHAT: `// returns the user`, `// loops over teams` — when the next line shows the same in code
4. Caller refs: `// used by SeasonController.save`, `// added for the import flow`, `// only invoked from sitegen`
5. Obsolete `@deprecated` Javadoc tags whose target was already removed in Phase 61 (verify by grep — no caller in `src/main` or `src/test`)

**What to KEEP:**
1. Genuinely non-obvious WHY: `// Top-N Buchholz tie-break — fewer ties than direct head-to-head when groups overlap` (algorithm rationale)
2. External API contracts: `// GT7 race name truncation — sheet returns max 32 chars per Google Sheets API quota`
3. Spec/issue references that name a permanent contract: `// CTC scoring rules: see docs/specs/2026-03-29-scoring-legs-design.md §3.2`
4. Convenience-getter rationale (D-02 keeps these as first-class API): `Convenience getter — derives season via getPhase().getSeason()` IS allowed, but DROP the trailing `// MIGR-06 bridge column removed in V6` part — that's history, not contract.
</context>

<tasks>

<task id="1" name="Audit + remove stale comments in domain.model + domain.repository">
  <action>
    For each file in `domain/model/` and `domain/repository/`, open it, identify stale comments per the
    classification in `<context>` above, and delete them. Concrete targets observed during planning:

    - `Season.java:93` — strip the trailing "alongside MIGR-06 (matchday.season_id bridge column removed in V6)" prose; keep the convenience-getter Javadoc (purpose) but drop the historical bridge-column footnote
    - `Matchday.java`, `Playoff.java` — same: keep `Convenience getter — derives season via getPhase().getSeason().` but drop "The matchdays.season_id bridge column was dropped in V6 (MIGR-06)" sentence
    - `MatchdayRepository.java:13`, `:23`, `:29`, `:32` — drop the four `// Phase 61 MIGR-06: ...` / `// Phase 60: count-only variant ...` / `// D-18: Delete-guard ...` / `// Phase 60 D-28: Group delete-guard ...` lines (the method names already explain WHAT; phase tags are stale)
    - `PlayoffRepository.java:13`, `:18` — drop `// Phase 61 MIGR-06: ...` and `// D-22: phase-aware finder (Phase 60 UI cutover will switch over)` (the cutover happened; the comment is now historical noise)
    - `MatchRepository.java`, `RaceRepository.java`, `RaceLineupRepository.java`, `RaceResultRepository.java` — drop all `// Phase 61 MIGR-06: post-V6 matchdays.season_id is gone — resolve via matchday.phase.season.id.` markers; the JPQL/finder methods are self-describing
    - `SeasonRepository.java:14` — drop `// Phase 61 MIGR-06: raceScoring + matchScoring moved to SeasonPhase; @EntityGraph ...` if the @EntityGraph annotation is self-evident; KEEP only if an `@EntityGraph` value matters and the WHY is non-obvious

    For each removal, leave a one-line replacement comment **only** if the WHY is non-obvious by reading the surrounding code. Default: just remove. Do NOT modify any code outside Javadoc/single-line comment regions.

    After all removals in this group: run `./mvnw test -Dtest='Season*Test,Matchday*Test,Playoff*Test,Race*Test,*RepositoryTest'` to confirm no compilation errors (Javadoc cleanups can break if a `{@link}` references a now-deleted decision).

    Commit message: `docs(61-gap): remove stale phase-narrative comments from domain.model + domain.repository`
  </action>
  <read_first>
    - src/main/java/org/ctc/domain/model/Season.java
    - src/main/java/org/ctc/domain/model/Matchday.java
    - src/main/java/org/ctc/domain/model/Playoff.java
    - src/main/java/org/ctc/domain/model/SeasonPhase.java (read-only — confirm no narrative there)
    - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
    - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
    - src/main/java/org/ctc/domain/repository/MatchRepository.java
    - src/main/java/org/ctc/domain/repository/RaceRepository.java
    - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
    - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
    - src/main/java/org/ctc/domain/repository/SeasonRepository.java
    - .planning/phases/61-cleanup-quality-gate/61-CONTEXT.md (D-02 Convenience-Getter contract — must NOT be removed)
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase 6[01] MIGR-06|D-22|D-28|CR-01" src/main/java/org/ctc/domain/model src/main/java/org/ctc/domain/repository | grep -v '^#'` returns 0 lines
    - `grep -rn -E "Wave [0-9]+ cascade|cascade migration|transitional bridge|bridge field" src/main/java/org/ctc/domain/model src/main/java/org/ctc/domain/repository | grep -v '^#'` returns 0 lines
    - `grep -c "Convenience getter" src/main/java/org/ctc/domain/model/Matchday.java` returns 1 (the contract MUST stay per D-02)
    - `grep -c "Convenience getter" src/main/java/org/ctc/domain/model/Playoff.java` returns 1
    - `./mvnw test -Dtest='*RepositoryTest,Season*Test,Matchday*Test,Playoff*Test'` is BUILD SUCCESS
    - `git diff --stat HEAD~1` shows comment-only changes (no `.java` line additions other than removed-then-replaced text); `git diff HEAD~1 -- '*.java' | grep '^+[^+]' | grep -vE "^\+\s*\*|^\+\s*//|^\+\s*$"` returns 0 lines (no NEW non-comment code introduced)
  </acceptance_criteria>
</task>

<task id="2" name="Audit + remove stale comments in domain.service">
  <action>
    For each of the 25 files in `src/main/java/org/ctc/domain/service/`, repeat the classification + removal procedure
    from Task 1. This subtree holds the highest narrative density (~120+ Phase-tagged comments observed during planning).

    Concrete target patterns (run a `grep -rn -E "Phase [0-9]+|D-[0-9]+|WR-[0-9]+|CR-[0-9]+|MIGR-06|IN-[0-9]+" src/main/java/org/ctc/domain/service` to enumerate):

    - `StandingsService.java` — lines 142-168 region had a `// MIGR-06 cleanup` TODO that 61-02 already acted on; drop any residual `// Phase 60 D-04: phase-aware standings via ...` if the method now ONLY supports the phase path
    - `PlayoffService.java`, `PlayoffSeedingService.java` — drop `// Phase 61 MIGR-06: M:N playoff_seasons is gone. Teams come from the playoff's ...` (line 64) — the `getPlayoffTeams` body is self-evident now
    - `SeasonPhaseService.java` (29 D-XX hits — heaviest file) — keep ONLY comments that describe a non-obvious algorithmic invariant; drop all phase-tagged narrative
    - `SeasonManagementService.java` (12 D-XX hits) — same treatment
    - `MatchService.java`, `MatchdayService.java`, `RaceCalendarService.java` — strip narrative `// Phase 61 ...` / `// D-XX ...` lines wherever the underlying code is self-explanatory
    - `DriverRankingService.java` — line 117 `(QUAL-02 disposition)` — drop the parenthetical (it's a stale traceability tag); keep the substantive WHY before it if non-obvious
    - `SwissPairingService.java` (10 D-XX hits) — same treatment; KEEP genuine algorithm rationale (Swiss-pairing is non-trivial)

    **Edge case — preserve algorithmic WHY:** `SwissPairingService` and `DriverRankingService` contain genuinely non-obvious algorithmic rationale comments. Where a comment explains a non-obvious algorithm choice, KEEP the explanation but DROP only the phase-tag prefix. Example transformation:
    - BEFORE: `// Phase 60 D-15: Top-N Buchholz tie-break — fewer ties than direct head-to-head`
    - AFTER:  `// Top-N Buchholz tie-break — fewer ties than direct head-to-head`

    Commit message: `docs(61-gap): remove stale phase-narrative comments from domain.service`
  </action>
  <read_first>
    - All 25 files in src/main/java/org/ctc/domain/service/
    - .planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md (gap G2 reasoning)
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md (recent CR-01 + WR-NN fixes — DO NOT remove the substance, only stale tags)
    - CLAUDE.md ("default to no comments" guidance)
  </read_first>
  <acceptance_criteria>
    - `grep -rn -E "Phase [56][0-9] (MIGR-06|D-[0-9]+|WR-[0-9]+|CR-[0-9]+|IN-[0-9]+)" src/main/java/org/ctc/domain/service | grep -v '^#'` returns 0 lines
    - `grep -rn -E "Wave [0-9]+ cascade|cascade migration|transitional bridge" src/main/java/org/ctc/domain/service | grep -v '^#'` returns 0 lines
    - `grep -rn -E "QUAL-0[123] disposition|MIGR-06 disposition" src/main/java/org/ctc/domain/service | grep -v '^#'` returns 0 lines
    - `./mvnw test -Dtest='org.ctc.domain.service.*Test'` is BUILD SUCCESS
    - Surefire test count UNCHANGED versus pre-task baseline (`grep -c "@Test" src/test/java/org/ctc/domain/service/*.java` returns same value before and after — comment-only changes must not delete tests)
  </acceptance_criteria>
</task>

<task id="3" name="Verify domain layer overall + run full test gate">
  <action>
    Final verification pass for the domain subtree:

    1. Run a comprehensive grep for stale markers in the domain layer:
       ```bash
       grep -rn -E "Phase [0-9]+ MIGR-06|D-[0-9]+ |WR-[0-9]+|CR-[0-9]+|IN-[0-9]+|Wave [0-9]+ cascade|cascade migration|transitional bridge|bridge field" src/main/java/org/ctc/domain/ | grep -v '^#'
       ```
       Expected: 0 lines. Any residue must be classified (genuine WHY → keep without phase prefix; otherwise → remove).

    2. Run targeted tests covering the touched packages:
       ```bash
       ./mvnw test -Dtest='org.ctc.domain.**'
       ```
       Expected: BUILD SUCCESS.

    3. Confirm JaCoCo line coverage on the domain layer did not change (a comment-only edit cannot affect coverage; this confirms zero accidental code change):
       ```bash
       ./mvnw verify -DskipITs -pl . | tee /tmp/61-gap-01-jacoco.log
       grep -A2 "BUILD SUCCESS\|FAILURE" /tmp/61-gap-01-jacoco.log | head -5
       ```

    4. If all checks pass, no further commit required (work is in Tasks 1-2). If grep finds residue, fix and commit:
       Commit message: `docs(61-gap): final stale-comment sweep in domain (cleanup remainders)`
  </action>
  <read_first>
    - Outputs of Tasks 1 + 2
    - pom.xml (line 241, jacoco minimum 0.82 — must not move)
  </read_first>
  <acceptance_criteria>
    - The combined grep above returns 0 lines (modulo legitimate WHY-only comments that match a substring; manual classification needed for any reported line)
    - `./mvnw test -Dtest='org.ctc.domain.**'` returns BUILD SUCCESS with at least 1100 tests run (baseline: 1172 total before this plan; domain subset is the majority)
    - `pom.xml` `<minimum>0.82</minimum>` is unchanged: `grep -c '<minimum>0.82</minimum>' pom.xml` returns 1
  </acceptance_criteria>
</task>

</tasks>

<verification>
Final phase-level verification (only after Tasks 1-3 complete):
- Surefire-only `./mvnw test` BUILD SUCCESS
- All grep gates above return 0
- Test count delta = 0 (comment changes must not affect test inventory)
- Each commit is atomic (one logical removal cluster per commit)
</verification>

<success_criteria>
1. All 20 listed files have phase-narrative comments removed
2. Convenience-Getter contracts on Matchday + Playoff PRESERVED (D-02 — first-class API; only the historical bridge-column footnote is stripped)
3. ./mvnw test BUILD SUCCESS, test count unchanged
4. pom.xml jacoco threshold unchanged at 0.82
</success_criteria>

<output>
After completion, create `.planning/phases/61-cleanup-quality-gate/61-gap-01-SUMMARY.md` documenting:
- Files touched (count + paths)
- Comment lines removed (rough count via `git diff --stat`)
- Any borderline cases that were KEPT with rationale (e.g., Swiss-pairing algorithm explanations)
- Final test gate result
</output>
