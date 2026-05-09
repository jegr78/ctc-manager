---
plan_id: 61-gap-04
phase: 61-cleanup-quality-gate
title: Javadoc audit — domain.repository + domain.service public APIs
wave: 1
gap_closure: true
autonomous: true
depends_on: []
files_modified:
  - src/main/java/org/ctc/domain/repository/SeasonRepository.java
  - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/main/java/org/ctc/domain/service/SeasonPhaseService.java
  - src/main/java/org/ctc/domain/service/SeasonManagementService.java
  - src/main/java/org/ctc/domain/service/PlayoffService.java
  - src/main/java/org/ctc/domain/service/PlayoffSeedingService.java
  - src/main/java/org/ctc/domain/service/MatchdayService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/RaceCalendarService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/SwissPairingService.java
requirements: [MIGR-06, QUAL-01, QUAL-02, QUAL-03]
must_haves:
  - "No Javadoc contradicts current behavior (audit IN-03 + IN-05 from 61-REVIEW.md confirmed)"
  - "Public methods that have non-obvious WHY (e.g., phase-vs-season scoping, group-resolution) HAVE Javadoc"
  - "No new Javadoc boilerplate added (CLAUDE.md: only when WHY is non-obvious)"
  - "./mvnw test stays GREEN with no behavior change"
---

<objective>
Audit and fix Javadoc on public methods of `domain.repository` + `domain.service`. The cascade migration redefined
the semantics of several finders (e.g., `MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` is now
phase-spanning, which surfaced as CR-01) and shifted scoring/format ownership from Season to SeasonPhase.
Javadoc that was correct pre-Phase-56 is now misleading. This addresses gap **G5 — missing/wrong javadoc**
for the heaviest layer.

Purpose: G5 cleanup focused on the two highest-impact areas (repository finders + service public methods).
The 61-REVIEW.md INFO findings IN-03 (SeasonRepository) and IN-05 (StandingsService.calculateBuchholzScoresForPhase)
are explicit examples — both must be addressed here.

Output: all listed files have accurate Javadoc on public methods where WHY is non-obvious. Test gate stays green.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md
@.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md
@.planning/phases/61-cleanup-quality-gate/61-REVIEW.md
@CLAUDE.md

**CLAUDE.md hard rule (binding):** "default to no comments" — do NOT add Javadoc boilerplate to every public
method. Only add Javadoc when the method's WHY is non-obvious from name + signature.

**G5 has two sub-tasks:**
1. **FIX wrong Javadoc** — methods whose existing Javadoc contradicts current behavior (post-cascade drift).
   The 61-REVIEW.md IN-03 + IN-05 findings are the smoking gun:
   - IN-03: `SeasonRepository` finders lack post-V6 Javadoc explaining phase indirection
   - IN-05: `StandingsService.calculateBuchholzScoresForPhase` has unused `groupId` parameter
   Both indicate Javadoc drift across the layer.
2. **ADD Javadoc where WHY is non-obvious** — only on methods like:
   - Phase-vs-season scoping decisions (`findByPhaseIdOrderBySortIndexAsc` vs `findBySeasonIdOrderBySortIndexAsc` —
     when do you use which?)
   - Group-resolution semantics (`findGroupForDriverInPhase`)
   - Buchholz / Swiss-pairing algorithm choices

   DO NOT add `/** Returns the user. */`-style boilerplate. If the method name says it, no Javadoc.

**Convenience-Getter Javadoc (D-02):** `Matchday.getSeason()` and `Playoff.getSeason()` Javadoc was already cleaned
in 61-gap-01 (kept the contract sentence, dropped the bridge-history footnote). Do not re-touch those entities
here — this plan is repository + service only.
</context>

<tasks>

<task id="1" name="Audit + fix Javadoc on domain.repository finders">
  <action>
    For each of the 4 repository files in `files_modified`, read the public finder methods. For each method:

    1. **If existing Javadoc contradicts current behavior** → fix it. Concrete cases observed:
       - `MatchdayRepository.findBySeasonIdOrderBySortIndexAsc` — CR-01 fix history shows this is phase-spanning.
         Add Javadoc explicitly: "Returns ALL matchdays for a season across ALL phases (REGULAR + PLAYOFF),
         ordered by sortIndex. Use `findByPhaseIdOrderBySortIndexAsc` when you need phase-scoped results
         (e.g., for sortIndex computation in REGULAR-only flows — see `MatchdayService.createInline`)."
       - `MatchdayRepository.findByPhaseIdOrderBySortIndexAsc` — counterpart Javadoc: "Returns matchdays scoped
         to a single SeasonPhase. Preferred for sortIndex computation (avoids cross-phase collisions)."
       - `SeasonRepository` finders (IN-03 explicit) — add Javadoc on every finder explaining what the result
         contains given the post-V6 schema (e.g., `findByYearAndNumber` is the canonical season identity lookup).
       - `PlayoffRepository.findBySeasonId` — explain that this works via `playoff.phase.season.id` after V6.
       - `SeasonPhaseRepository.findBySeasonIdAndPhaseType` — explain the REGULAR-vs-PLAYOFF lookup contract.

    2. **If existing Javadoc is correct + non-obvious** → keep verbatim.
    3. **If method has no Javadoc + the name fully describes WHAT** (e.g., `existsByName`, `findById`) → leave
       alone. Do NOT add boilerplate.

    Pattern to copy (Spring Data convention — see existing methods in same file):
    ```java
    /**
     * Returns matchdays scoped to a single SeasonPhase, ordered by sortIndex ascending.
     * Preferred over {@link #findBySeasonIdOrderBySortIndexAsc} for sortIndex computation
     * to avoid cross-phase collisions (REGULAR + PLAYOFF can both contain "Round 1" labels).
     */
    List<Matchday> findByPhaseIdOrderBySortIndexAsc(UUID phaseId);
    ```

    Commit message: `docs(61-gap): fix Javadoc on domain.repository finders post-V6`
  </action>
  <read_first>
    - All 4 repository files in files_modified
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW.md (IN-03 finding — exact wording)
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md (CR-01 fix — establishes the phase-vs-season finder semantics)
    - src/main/java/org/ctc/domain/service/MatchdayService.java (createInline — caller of phase-scoped finder)
  </read_first>
  <acceptance_criteria>
    - `grep -B1 "List<Matchday> findByPhaseIdOrderBySortIndexAsc" src/main/java/org/ctc/domain/repository/MatchdayRepository.java | head -3` shows a Javadoc block above the method
    - `grep -B1 "List<Matchday> findBySeasonIdOrderBySortIndexAsc" src/main/java/org/ctc/domain/repository/MatchdayRepository.java | head -3` shows a Javadoc block above the method (clarifying it's phase-spanning)
    - `grep -B1 "findByYearAndNumber" src/main/java/org/ctc/domain/repository/SeasonRepository.java | head -3` shows a Javadoc block (or the method is so trivial that no Javadoc is needed — document this choice in SUMMARY)
    - `./mvnw test -Dtest='*RepositoryTest'` is BUILD SUCCESS
    - No new method bodies modified: `git diff HEAD~1 --stat src/main/java/org/ctc/domain/repository/` shows only Javadoc-line additions (lines start with `+ *` or `+ /**` or `+ */`)
  </acceptance_criteria>
</task>

<task id="2" name="Audit + fix Javadoc on domain.service public methods">
  <action>
    For each of the 10 service files in `files_modified`, audit public methods. Apply the same classification
    (fix wrong → fix; non-obvious + missing → add; obvious → leave).

    Concrete observed targets:

    `StandingsService.java`:
    - `calculateBuchholzScoresForPhase(UUID phaseId, UUID groupId)` — IN-05 finding: the `groupId` parameter
      is currently unused. Audit: is it actually used in any caller path? If not, the Javadoc must either
      (a) document why the parameter is reserved (e.g., for future per-group Buchholz), or (b) drop the parameter
      entirely. **DECISION GATE:** If grep finds 0 callers passing a non-null groupId, propose dropping the
      parameter (separate plan / separate commit) and document in 61-gap-04-SUMMARY.md as `NEEDS_CONTEXT` for
      the orchestrator. Do NOT remove parameters in this plan (it's Javadoc-only). Document with: `// FIXME(61-gap-04): groupId currently unused — see SUMMARY for follow-up`.
    - `calculateStandingsForPhase` — add Javadoc clarifying the per-phase scoping contract.

    `SeasonPhaseService.java`:
    - `findRegularPhase(UUID seasonId)` — heavily used post-cascade; add Javadoc: "Returns the canonical REGULAR
      phase for a season. Throws EntityNotFoundException if no REGULAR phase exists (impossible post-V4 per
      Phase 57 D-06)." — note the parenthetical references a permanent invariant, not phase history.
    - `findByIdWithGroups`, `findByIdWithMatchdays` — explain why distinct fetch strategies exist.

    `SeasonManagementService.java`:
    - `save(SeasonForm form)` — explain that REGULAR phase auto-creates with default scoring (Phase 58 D-19
      establishes the contract; Javadoc states the contract WITHOUT phase-tag prefix).
    - Other public methods: audit + apply rules.

    `PlayoffService.java`:
    - `createPlayoff` — explain auto-creates the PLAYOFF phase atomically (this is a permanent contract worth
      documenting — non-obvious from method name).
    - `getPlayoffTeams` — Javadoc must reflect the post-V6 single-source-of-truth (only `playoff.getSeason().getTeams()`).

    `PlayoffSeedingService.java`:
    - `autoSeedBracket` — algorithmic rationale (Top-N from REGULAR standings) is non-obvious; Javadoc add.

    `MatchdayService.java`:
    - `createInline` — Javadoc note about phase-scoped sortIndex (the CR-01 fix is now a permanent contract).

    `MatchService.java`, `RaceCalendarService.java`, `DriverRankingService.java`, `SwissPairingService.java` —
    audit each public method against the rules.

    Commit message: `docs(61-gap): fix and add Javadoc on domain.service public APIs`
  </action>
  <read_first>
    - All 10 service files in files_modified
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW.md (IN-05 finding wording)
    - .planning/phases/61-cleanup-quality-gate/61-REVIEW-FIX.md (CR-01 contract — phase-scoped finders are the new norm)
    - CLAUDE.md "Architectural Principles" — controllers thin, services own logic
  </read_first>
  <acceptance_criteria>
    - All public methods on `StandingsService`, `SeasonPhaseService`, `PlayoffService`, `PlayoffSeedingService`
      that have non-obvious WHY have Javadoc. Manual sample check: `grep -B5 "public.*createPlayoff\|public.*findRegularPhase\|public.*autoSeedBracket\|public.*calculateBuchholzScoresForPhase" src/main/java/org/ctc/domain/service/*.java` shows Javadoc above each.
    - The IN-05 issue is explicitly addressed: either Javadoc documents the parameter's purpose, OR a `FIXME(61-gap-04)` comment is in place AND the SUMMARY records a NEEDS_CONTEXT item for the orchestrator.
    - `./mvnw test -Dtest='org.ctc.domain.service.*Test'` is BUILD SUCCESS
    - Test count UNCHANGED (Javadoc-only changes)
    - `git diff HEAD~1 -- 'src/main/java/org/ctc/domain/service/' | grep '^+' | grep -vE "^\+\+\+|^\+\s*\*|^\+\s*/\*\*|^\+\s*\*/|^\+\s*$|^\+\s*//"` returns 0 lines (no new code added; only Javadoc + comment lines)
  </acceptance_criteria>
</task>

<task id="3" name="Verify Javadoc-tooling integrity (no doclint regressions)">
  <action>
    After Tasks 1-2, run a Javadoc generation pass to catch any malformed `{@link}` references or unresolved
    tags introduced during the audit:

    ```bash
    ./mvnw javadoc:javadoc -Dquiet=true 2>&1 | tail -50
    ```

    Acceptable: BUILD SUCCESS or BUILD SUCCESS WITH WARNINGS where the warnings predate this plan (record
    pre-plan baseline by running `./mvnw javadoc:javadoc` BEFORE Task 1 and saving the warning count).

    If new doclint errors are introduced, fix them in this commit. If only warnings (and not new ones), proceed.

    Final test gate:
    ```bash
    ./mvnw test
    ```
    Expected: BUILD SUCCESS, 1172 tests run (or whatever the post-CR-01 baseline was — must not decrease).

    No commit if no fixes needed. If fixes needed:
    Commit message: `docs(61-gap): repair Javadoc tooling regressions from G5 sweep`
  </action>
  <read_first>
    - Output of pre-task `./mvnw javadoc:javadoc` baseline
    - Outputs of Tasks 1 + 2
  </read_first>
  <acceptance_criteria>
    - `./mvnw javadoc:javadoc -Dquiet=true` returns BUILD SUCCESS (no new errors vs. baseline)
    - `./mvnw test` returns BUILD SUCCESS, test count >= 1172
    - JaCoCo threshold unchanged: `grep -c '<minimum>0.82</minimum>' pom.xml` returns 1
  </acceptance_criteria>
</task>

</tasks>

<verification>
- ./mvnw test BUILD SUCCESS at end of plan
- ./mvnw javadoc:javadoc returns no new errors
- IN-05 (StandingsService groupId) explicitly addressed (Javadoc + NEEDS_CONTEXT to orchestrator if param truly unused)
- IN-03 (SeasonRepository post-V6 docs) explicitly addressed
</verification>

<success_criteria>
1. Repository finders have post-V6 Javadoc clarifying phase-vs-season scope
2. Service public methods with non-obvious WHY have Javadoc; obvious ones do not (no boilerplate)
3. IN-03 + IN-05 from 61-REVIEW.md are addressed
4. Javadoc tooling builds clean
</success_criteria>

<output>
Create `.planning/phases/61-cleanup-quality-gate/61-gap-04-SUMMARY.md` listing:
- Methods documented (count)
- Methods left undocumented (count + rationale: "name describes WHAT")
- IN-05 disposition (Javadoc-only vs NEEDS_CONTEXT for parameter removal)
- Final test + javadoc gate result
</output>
