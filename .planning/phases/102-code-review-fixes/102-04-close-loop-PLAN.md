---
phase: 102-code-review-fixes
plan: 04
type: execute
wave: 4
depends_on:
  - 102-03
files_modified:
  - .planning/phases/102-code-review-fixes/102-REVIEW.md
autonomous: false
requirements:
  - REVIEW-FIX-01
  - REVIEW-FIX-02
  - REVIEW-FIX-03

must_haves:
  truths:
    - "./mvnw clean verify -Pe2e exits 0 — Surefire + Failsafe + Playwright E2E all green"
    - "SpotBugs BugInstance count remains 0 (gated by the verify run)"
    - "JaCoCo line coverage ≥ 88.88 % (the in-milestone aspiration baseline; the 82 % pom gate is the hard floor)"
    - "gsd-code-reviewer returns clean (zero critical + zero warning) on the full Phase-102 cumulative diff (~30-50 files)"
    - "102-REVIEW.md authored capturing the close-loop result (the Phase-102 historical record per CONTEXT D-13)"
    - "If remediation tasks surface during the close-loop, they land inline in this plan (added during execution) AND a second review pass confirms clean before SUMMARY.md commits"
  artifacts:
    - path: ".planning/phases/102-code-review-fixes/102-REVIEW.md"
      provides: "Close-loop review record for Phase 102 — first pass result, remediation list (if any), final clean confirmation"
      contains: "Phase 102 Close-Loop Review"
  key_links:
    - from: ".planning/phases/102-code-review-fixes/102-REVIEW.md"
      to: "the Phase-102 cumulative diff on gsd/v1.13-discord-integration"
      via: "documents the gsd-code-reviewer pass(es) on git diff origin/master..HEAD limited to Phase-102 commits"
      pattern: "Phase 102 Close-Loop Review"
---

<objective>
Run the milestone-close gate for Phase 102 per CONTEXT D-04 (full-phase close-loop) and D-09 (end-gate). Two acceptance bars MUST be green:

1. `./mvnw clean verify -Pe2e` exits 0 (Surefire + Failsafe + Playwright E2E + SpotBugs + JaCoCo coverage gate).
2. `gsd-code-reviewer` returns `clean` (zero critical + zero warning) on the full Phase-102 cumulative diff (~30-50 files).

Per CONTEXT D-11: this is the ONE AND ONLY `clean verify -Pe2e` for the entire phase. Plans 102-01/02/03 ran targeted tests only.

Per CONTEXT D-13: this plan AUTHORS `.planning/phases/102-code-review-fixes/102-REVIEW.md` — a NEW close-loop review record. It does NOT delete or rewrite the 10 input `*-REVIEW.md` files from phases 92-101 (those are historical record).

`autonomous: false` — this plan has an orchestrator-pause point (Task 2 dispatches `gsd-code-reviewer` and the orchestrator decides whether to proceed to SUMMARY OR to insert remediation tasks).

Output: 1-N commits depending on remediation needs. Minimum: 102-REVIEW.md + SUMMARY.md. If findings surface: per-finding fix commits + a second review pass before SUMMARY.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/102-code-review-fixes/102-CONTEXT.md
@.planning/phases/102-code-review-fixes/102-01-critical-fixes-PLAN.md
@.planning/phases/102-code-review-fixes/102-02-warning-fixes-refactors-PLAN.md
@.planning/phases/102-code-review-fixes/102-03-info-sweep-PLAN.md
@CLAUDE.md

<execution_notes>
Inline-sequential on `gsd/v1.13-discord-integration` per CONTEXT D-05. The `gsd-code-reviewer` subagent dispatch (Task 2) is READ-ONLY per CLAUDE.md "Subagent Rules" — read-only research/review agents are the only permitted subagent category in v1.13.

Per CONTEXT non-goals: this plan does NOT execute `/gsd-complete-milestone v1.13`. That command is the immediate NEXT step AFTER Phase 102 ships. The PR description refresh also happens AFTER milestone close per CLAUDE.md "Milestone PR Already Exists" rolling-summary discipline.

Per CONTEXT D-14: no CLAUDE.md edits in Phase 102. If the close-loop reviewer surfaces a new "convention rule" candidate, it's recorded in SUMMARY.md as a memory-promotion candidate for the v1.13 RETROSPECTIVE — not edited into CLAUDE.md here.
</execution_notes>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Run full end-of-phase verification — ./mvnw clean verify -Pe2e</name>
  <files>(no source files modified; this task is a verification gate)</files>
  <read_first>
    - CLAUDE.md "Build & Test Discipline" (the canonical end-gate is `./mvnw clean verify -Pe2e`, never plain verify, never with -DskipTests)
    - CONTEXT.md D-09 + D-11 (end-of-phase gate definition)
  </read_first>
  <action>
    Run `./mvnw clean verify -Pe2e` in the repo root. Expected runtime ~17-20 min (CI median 17:39 ± 20 % per ROADMAP success criterion).

    On failure:
    - Per CLAUDE.md "No Flaky Dismissal": every failure is a REGRESSION until proven otherwise via `git stash` + isolated re-run.
    - Per CLAUDE.md "Build & Test Discipline / Clean Maven Build is the Source of Truth": if Maven reports `"Unresolved compilation problem"`, that's an Eclipse JDT signature (NOT javac) — first diagnostic step is `./mvnw clean test-compile`, never edit source first.
    - Diagnose root cause; fix inline (add a remediation task to this plan; atomic-commit per fix); re-run the full `./mvnw clean verify -Pe2e` until green.

    SpotBugs gate: medium+HIGH `BugInstance` count must be 0 (gated by the verify run via `spotbugs-maven-plugin` 4.9.8.3 + `findsecbugs-plugin` 1.14.0).

    JaCoCo gate: line coverage must remain ≥ 88.88 % (the in-milestone aspiration baseline). If coverage drops, the verify run still passes (the pom hard floor is 82 %), but the SUMMARY.md must record the regression and the executor decides whether to add a coverage-recovery task inline.
  </action>
  <verify>
    <automated>./mvnw clean verify -Pe2e</automated>
  </verify>
  <acceptance_criteria>
    - `./mvnw clean verify -Pe2e` exits 0.
    - `target/site/jacoco/index.html` shows line coverage ≥ 88.88 % (verify via `grep -E "Total.*[0-9]+\\s*%" target/site/jacoco/index.html | head -2` OR by parsing `target/site/jacoco/jacoco.csv` per CLAUDE.md COV-01 precedent).
    - No `BugInstance` reported (verify via `target/spotbugsXml.xml` line count of `<BugInstance` elements = 0).
    - Surefire + Failsafe + Playwright e2e suites all green.
  </acceptance_criteria>
  <done>End-of-phase test gate green. No commit in this task (no source files modified). If remediation was needed, those fixes already landed as inline atomic commits before this task's final acceptance.</done>
</task>

<task type="checkpoint:decision" gate="blocking">
  <name>Task 2: Orchestrator dispatches gsd-code-reviewer over Phase-102 cumulative diff</name>
  <files>(no source files modified — orchestrator-driven step)</files>
  <decision>Did the close-loop review return clean (zero critical + zero warning) on the full Phase-102 diff?</decision>
  <context>
    Per CONTEXT D-04, this is the **orchestrator-level dispatch** of `/gsd-code-review 102` over the entire Phase-102 cumulative diff. The plan does NOT spawn the reviewer agent itself; the orchestrator dispatches it and inspects the returned findings.

    Diff scope: `git diff --name-only origin/master..HEAD -- 'src/**' '.planning/phases/102-code-review-fixes/**'` — typically 30-50 files per CONTEXT.md `<specifics>` "Plan 102-04 close-loop expected agent run".

    Expected reviewer runtime: ~10-15 minutes at standard depth.

    Per CONTEXT D-04, the reviewer agent has access to: the entire Phase-102 cumulative diff, the 10 per-phase REVIEW.md files (read-only), CLAUDE.md (the convention source of truth), and the 4 Phase-102 PLAN.md files (so it knows what was intentional).
  </context>
  <options>
    <option id="clean">
      <name>Clean — zero critical + zero warning</name>
      <pros>Phase 102 closes; proceed to Task 3 (write 102-REVIEW.md) + Task 4 (SUMMARY.md commit).</pros>
      <cons>None — this is the goal state.</cons>
    </option>
    <option id="remediation-needed">
      <name>Findings surfaced — inline remediation required</name>
      <pros>Catches cross-plan regressions that per-plan reviews missed (e.g., a 102-02 warning fix that accidentally undid a 102-01 critical fix in a different file).</pros>
      <cons>Phase 102 stays open until remediation lands; the executor (NOT a subagent per CONTEXT D-05) writes the fix inline in this plan, atomic-commits per finding, then the orchestrator re-dispatches `gsd-code-reviewer` for a second pass.</cons>
    </option>
  </options>
  <resume-signal>
    If clean: type "clean — proceed to Task 3".
    If remediation needed: type "findings: <count>; proceed to inline remediation" — the orchestrator will then insert remediation tasks AFTER Task 2 and BEFORE Task 3.
  </resume-signal>
  <action>
    Orchestrator: spawn `/gsd-code-review 102` with the full Phase-102 cumulative diff as scope. Capture the resulting REVIEW output (typically a finding list with severity per item).

    If the output contains ANY critical or warning finding, surface them to the user via this checkpoint and BLOCK until remediation lands. Inline remediation = the orchestrator (working inline-sequential per CONTEXT D-05) adds remediation tasks to this PLAN.md as new `<task>` elements, executes them, atomic-commits per finding, then re-dispatches the reviewer.

    If clean: proceed.

    Per CONTEXT.md `<specifics>` "Plan 102-04 SUMMARY.md records both review passes" — the SUMMARY captures the first-pass findings (if any) AND the final clean pass.
  </action>
  <verify>
    <automated>echo "manual checkpoint — orchestrator dispatches gsd-code-reviewer and inspects the output; no automated test"</automated>
  </verify>
  <acceptance_criteria>
    - `gsd-code-reviewer` returned `clean` (zero critical + zero warning) on the most recent pass.
    - If remediation was needed: every reviewer-reported finding has a corresponding inline commit on the milestone branch BEFORE the clean pass landed.
  </acceptance_criteria>
  <done>Reviewer clean confirmed by orchestrator; proceeds to Task 3.</done>
</task>

<!-- Inline remediation tasks inserted 2026-05-28 after first reviewer pass surfaced 3 Warning + 2 Info findings.
     Per plan: "Inline remediation = the orchestrator (working inline-sequential per CONTEXT D-05) adds remediation
     tasks to this PLAN.md as new <task> elements, executes them, atomic-commits per finding, then re-dispatches
     the reviewer." User direction 2026-05-28: "Alle 3 Warnungen und beide Info Funde beheben." -->

<task type="auto" tdd="false">
  <name>Task 2-R1: Extract HexColor sanitizer and apply to TeamManagementService.save (close-loop W1)</name>
  <files>src/main/java/org/ctc/domain/util/HexColor.java, src/main/java/org/ctc/domain/service/TeamManagementService.java, src/main/java/org/ctc/domain/service/SeasonManagementService.java, src/test/java/org/ctc/domain/util/HexColorTest.java</files>
  <action>
    Reviewer Warning 1: Phase 102-03 commit `17570d75` introduced `sanitizeHexColor(...)` in `SeasonManagementService` to close 97 IN-04 (CSS-injection vector on the `th:style` color attributes), but the parallel `Team.primaryColor/secondaryColor/accentColor` ingress in `TeamManagementService.save` (lines 230-232 UPDATE path + 238-240 CREATE path) writes raw form input straight to the entity. The `Team`-level colors flow into five `th:style` injection points in `standings-render.html`, `matchday-pairings-render.html`, `matchday-results-render.html`, `playoff-round-results-render.html`, and `matchday-overview-render.html`.

    Hoist the sanitizer to a new shared utility `org.ctc.domain.util.HexColor` with a single `sanitize(String)` method (returns the validated hex value or null). Apply it from both `TeamManagementService.save` (CREATE + UPDATE) AND `SeasonManagementService.updateSeasonTeam`. Add `HexColorTest` covering: null, blank, untrimmed, `#fff`, `#ffffff`, `#ffffff80` (8-digit), invalid characters, injection payloads.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=HexColorTest,TeamManagementServiceTest -DfailIfNoTests=false</automated>
  </verify>
  <acceptance_criteria>
    - `HexColor.sanitize` exists; covers the 8 cases above.
    - `TeamManagementService.save` calls `HexColor.sanitize` on the three color fields on BOTH the CREATE and UPDATE paths.
    - `SeasonManagementService.updateSeasonTeam` calls `HexColor.sanitize` (now via the shared util — the inline private method + regex constant are removed).
    - Targeted tests pass; no behavior change for valid inputs.
    - One atomic commit: `fix(102-04): apply hex-color sanitizer to TeamManagementService.save — close-loop W1`.
  </acceptance_criteria>
  <done>HexColor util landed; both ingress paths sanitize.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2-R2: Null-guard team-slug in postRaceResultToForumThread filename (close-loop W2)</name>
  <files>src/main/java/org/ctc/discord/service/DiscordPostService.java, src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadFilenameTest.java</files>
  <action>
    Reviewer Warning 2: `DiscordPostService.postRaceResultToForumThread` (lines 895-898) composes the upload filename via `race.getMatch().getHomeTeam().getShortName() + "-vs-" + race.getMatch().getAwayTeam().getShortName()` when `race.getMatch() != null`. The pre-flight `canPostRaceResultToForum(race, config)` does not verify the parent `Match` has home/away teams assigned. A race attached to a partially-filled match (CSV-result import before pairings are finalized, or team unassigned after results entered) would crash with NPE at line 896 or 897.

    Guard each team access. If either home or away team is null, fall back to `matchSlug = "race"` (the existing fallback for `race.getMatch() == null`). Add a unit test pinning the null-team-path produces the safe filename without throwing.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=DiscordPostServiceForumThreadFilenameTest -DfailIfNoTests=false</automated>
  </verify>
  <acceptance_criteria>
    - `matchSlug` computation null-guards `match.getHomeTeam()` AND `match.getAwayTeam()`.
    - New unit test seeds a `Race` with a `Match` whose home team is null AND a separate scenario whose away team is null; asserts no NPE and `matchSlug` falls back to the safe default.
    - One atomic commit: `fix(102-04): null-guard team-slug in postRaceResultToForumThread — close-loop W2`.
  </acceptance_criteria>
  <done>Filename composition cannot NPE on partial-match data.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2-R3: Migrate all sidebar links in layout.html to activeRoute via GlobalModelAdvice (close-loop W3)</name>
  <files>src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java, src/main/java/org/ctc/discord/web/DiscordConfigController.java, src/main/java/org/ctc/discord/web/DiscordPostController.java, src/main/resources/templates/admin/layout.html, src/test/java/org/ctc/admin/controller/GlobalModelAdviceActiveRouteTest.java</files>
  <action>
    Reviewer Warning 3: Phase 102 partially migrated the sidebar from `${title.contains(...)}` substring-matching to an explicit `activeRoute` model attribute — but only for the two Discord links (lines 80-81 of `layout.html`). The remaining sidebar links (47-76) still rely on `title.contains` which has known bugs (e.g., "Race" matches "Race-Scoring" page title; "Team" matches "Team Cards" so an explicit `!title.contains('Team Cards')` exclusion is needed; "Car" matches "Team Cards" with the same exclusion). The motivating bug from Phase 102 (Discord links both lighting up) likely existed for other link pairs too.

    Centralize the URL-prefix → activeRoute mapping in `GlobalModelAdvice` so EVERY admin request automatically gets the correct `activeRoute` model attribute. Order matters: more specific prefixes first (e.g., `/admin/tools/team-cards` before `/admin/teams`). Migrate all 16 sidebar links in `layout.html` to the new pattern. Remove the per-controller `model.addAttribute("activeRoute", ...)` calls in `DiscordConfigController` (2 sites) and `DiscordPostController` (1 site).

    Add a unit test exercising `GlobalModelAdvice.activeRoute(HttpServletRequest)` for the full sidebar URL set + an unmapped URL → null. Existing `DiscordPostFilterControllerIT` test asserting `activeRoute=discord-posts` must continue to pass (the global advice resolves the same value).
  </action>
  <verify>
    <automated>./mvnw test -Dtest=GlobalModelAdviceActiveRouteTest -DfailIfNoTests=false &amp;&amp; ./mvnw verify -Dit.test=DiscordPostFilterControllerIT -DfailIfNoTests=false</automated>
  </verify>
  <acceptance_criteria>
    - `GlobalModelAdvice` exposes a `@ModelAttribute("activeRoute")` method that maps `HttpServletRequest.getRequestURI()` to one of: seasons, matchdays, races, playoffs, teams, drivers, cars, tracks, race-scorings, match-scorings, standings, power-rankings, import, gt7-sync, team-cards, template-editors, backup, discord-config, discord-posts, or null.
    - All 16 sidebar `<a>` elements in `layout.html` use `${activeRoute == 'X' ? 'active' : ''}`.
    - No `model.addAttribute("activeRoute", ...)` lines remain in any controller.
    - `GlobalModelAdviceActiveRouteTest` exercises the full mapping.
    - One atomic commit: `refactor(102-04): migrate sidebar links to activeRoute pattern — close-loop W3`.
  </acceptance_criteria>
  <done>Sidebar activation is centralized and url-driven; no cross-page false-positives.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2-R4: log.warn on team1==null skip in recomputeMatchScoresFromAllLegs (close-loop I1)</name>
  <files>src/main/java/org/ctc/domain/service/ScoringService.java</files>
  <action>
    Reviewer Info 1: `ScoringService.recomputeMatchScoresFromAllLegs` (lines 62 + 85) silently skips when the Match `homeTeam` or PlayoffMatchup `team1` is null. The skip is intentional (the score cannot be aggregated without team identity) but a future stale-score report from this branch is hard to diagnose. Add `log.warn` lines on both skip paths surfacing the matchId / matchupId and the missing team-field name, so operators can see at a glance why scores stayed stale.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=ScoringServiceTest -DfailIfNoTests=false</automated>
  </verify>
  <acceptance_criteria>
    - Both skip-branches emit a `log.warn` with parameterized `{}` placeholders identifying the match / matchup and the missing field.
    - Existing `ScoringServiceTest` cases continue to pass.
    - One atomic commit: `chore(102-04): log.warn on null-team skip in recomputeMatchScoresFromAllLegs — close-loop I1`.
  </acceptance_criteria>
  <done>Stale-score diagnostics improved.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2-R5: Explicit save in recomputeMatchScoresFromAllLegs (close-loop I2)</name>
  <files>src/main/java/org/ctc/domain/service/ScoringService.java</files>
  <action>
    Reviewer Info 2: `ScoringService.recomputeMatchScoresFromAllLegs` mutates `match.setHomeScore/AwayScore` and `matchup.setHomeScore/AwayScore` and relies on Hibernate dirty-checking inside the `@Transactional` context to persist. The reliance is implicit and survives only because the caller (`RaceService.saveResults`) is also `@Transactional`. Inject `MatchRepository` and `PlayoffMatchupRepository`, then call `matchRepository.save(match)` / `playoffMatchupRepository.save(matchup)` explicitly after the mutation. Persistence intent becomes visible at the callsite; the change is behavior-neutral inside the existing transaction boundary.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=ScoringServiceTest -DfailIfNoTests=false &amp;&amp; ./mvnw verify -Dit.test=*Scoring* -DfailIfNoTests=false</automated>
  </verify>
  <acceptance_criteria>
    - `ScoringService` constructor injects `MatchRepository` + `PlayoffMatchupRepository` via the existing `@RequiredArgsConstructor`.
    - Both score-mutation branches end with an explicit `save(...)` call.
    - Existing tests continue to pass (no behavior change).
    - One atomic commit: `chore(102-04): explicit save in recomputeMatchScoresFromAllLegs — close-loop I2`.
  </acceptance_criteria>
  <done>Persistence intent visible at the callsite.</done>
</task>

<task type="checkpoint:decision" gate="blocking">
  <name>Task 2-R6: Re-dispatch gsd-code-reviewer for second-pass clean confirmation</name>
  <files>(no source files modified — orchestrator-driven step)</files>
  <decision>Does the re-dispatched reviewer return clean on the post-remediation Phase-102 cumulative diff?</decision>
  <action>
    After Tasks 2-R1..R5 have landed atomic commits on `gsd/v1.13-discord-integration`, the orchestrator re-dispatches `gsd-code-reviewer` over the updated Phase-102 cumulative diff (`d6b5ab01..HEAD`). Required verdict: CLEAN. Any new finding requires another remediation cycle (Tasks 2-R7+ inserted analogously).

    The second-pass review prompt MUST reference this PLAN.md's remediation tasks so the reviewer focuses on whether the 5 fixes are correctly applied AND does not re-flag the same first-pass findings as still-open.
  </action>
  <verify>
    <automated>echo "manual checkpoint — orchestrator dispatches gsd-code-reviewer second pass"</automated>
  </verify>
  <acceptance_criteria>
    - Second-pass reviewer returns `clean` (zero critical + zero warning) on the post-remediation diff.
    - If not clean, additional remediation tasks land before proceeding to Task 3.
  </acceptance_criteria>
  <done>Reviewer clean on second pass; proceeds to Task 3.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 3: Author 102-REVIEW.md — the close-loop historical record</name>
  <files>.planning/phases/102-code-review-fixes/102-REVIEW.md</files>
  <read_first>
    - Sample existing REVIEW.md format from phase 101: `.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md`
    - CONTEXT D-13 (Phase 102's close-loop REVIEW.md is a NEW file separate from the input set)
    - The reviewer output from Task 2 (first pass + second pass if applicable)
  </read_first>
  <action>
    Create `.planning/phases/102-code-review-fixes/102-REVIEW.md` following the existing REVIEW.md template structure. Sections:
    1. **Header** — Phase number (102), title, date, scope ("Close-loop review of the Phase-102 cumulative diff on `gsd/v1.13-discord-integration`").
    2. **Diff Scope** — total files changed (from `git diff --name-only origin/master..HEAD`), commits-included count, top-level directories touched.
    3. **First-Pass Findings** — list of findings from Task 2's first reviewer pass (if any). For each: `### {SEVERITY}-{NN}: {one-line title}` + Location + Description + Suggested Fix + Resolution-status.
    4. **Remediation** — for each first-pass finding, the commit SHA that closed it + a 1-line note.
    5. **Second-Pass Result** — if a second pass was needed, the result (must be clean).
    6. **Final Verification Outcome** — confirmation that `./mvnw clean verify -Pe2e` is green + SpotBugs 0 + JaCoCo ≥ 88.88 % + reviewer clean.
    7. **Memory-Promotion Candidates** — per CONTEXT D-14 + the Deferred Ideas section, any new pattern observations recorded for the v1.13 RETROSPECTIVE (not promoted to CLAUDE.md here).
    8. **Closure Note** — `Phase 102 closed; v1.13 milestone unblocks /gsd-complete-milestone v1.13`.

    Per CLAUDE.md "Documentation Maintenance" — this is a planning document, NOT a source file; the "No Comment Pollution" hard-bans (which target src/main, src/test, Flyway migrations) don't apply here. Phase / Plan markers are LEGITIMATE in `.planning/phases/**/*-REVIEW.md` files.

    Per CLAUDE.md "Language" — DOCS in English (this file is documentation/planning record).

    Commit: `docs(102): author close-loop REVIEW.md`.
  </action>
  <verify>
    <automated>test -f .planning/phases/102-code-review-fixes/102-REVIEW.md && grep -E "^# Phase 102|^## " .planning/phases/102-code-review-fixes/102-REVIEW.md | wc -l | grep -E "^[[:space:]]*[5-9]$|^[[:space:]]*[1-9][0-9]+$"</automated>
  </verify>
  <acceptance_criteria>
    - `.planning/phases/102-code-review-fixes/102-REVIEW.md` exists.
    - The file has at least 5 top-level headings (header + 4 of the 8 sections above; not all 8 are mandatory if e.g. there were no first-pass findings).
    - The "Final Verification Outcome" section explicitly states `./mvnw clean verify -Pe2e` result + JaCoCo coverage % + reviewer clean confirmation.
    - One atomic commit.
  </acceptance_criteria>
  <done>102-REVIEW.md authored and committed; Phase 102 historical record complete.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 4: Commit Plan 102-04 SUMMARY.md and update STATE.md</name>
  <files>.planning/phases/102-code-review-fixes/102-04-SUMMARY.md, .planning/STATE.md</files>
  <read_first>
    - The existing SUMMARY.md files from 102-01/02/03 (template reference)
    - .planning/STATE.md (current position; needs update to reflect Phase 102 closed)
    - $HOME/.claude/get-shit-done/templates/summary.md (template)
  </read_first>
  <action>
    Create `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` per template:
    - Plan goal (close-loop verification)
    - Tasks completed (Task 1 verify result, Task 2 review result + remediation count, Task 3 REVIEW.md commit SHA)
    - End-gate metrics (`mvn clean verify -Pe2e` exit, JaCoCo coverage %, SpotBugs BugInstance count, test count delta, reviewer result)
    - Final commit list for Phase 102 (all 4 plans combined: count + range of SHAs)

    Update `.planning/STATE.md` Phase 102 entry: position → closed, with the SUMMARY date. Update Phase 102 ROADMAP entry checkbox `[ ]` → `[x]` in `.planning/milestones/v1.13-ROADMAP.md`.

    Per CLAUDE.md "Git Workflow": ONE atomic commit containing both file edits. Subject: `docs(102): close Phase 102 — SUMMARY.md + STATE/ROADMAP checkbox flips`.
  </action>
  <verify>
    <automated>test -f .planning/phases/102-code-review-fixes/102-04-SUMMARY.md && grep -q "\\[x\\] \\*\\*Phase 102:" .planning/milestones/v1.13-ROADMAP.md</automated>
  </verify>
  <acceptance_criteria>
    - `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` exists with the standard SUMMARY structure.
    - `.planning/STATE.md` reflects Phase 102 closed.
    - `.planning/milestones/v1.13-ROADMAP.md` Phase 102 entry checkbox is `[x]`.
    - One atomic commit.
  </acceptance_criteria>
  <done>Phase 102 closed; `/gsd-complete-milestone v1.13` is unblocked per CLAUDE.md "Code-Review Before Phase Close".</done>
</task>

</tasks>

<verification>
After all 4 tasks land:
1. `./mvnw clean verify -Pe2e` exits 0 (the canonical end-gate per CLAUDE.md "Build & Test Discipline").
2. `gsd-code-reviewer` returned `clean` on the most recent Phase-102 cumulative-diff pass.
3. `.planning/phases/102-code-review-fixes/102-REVIEW.md` exists.
4. `.planning/STATE.md` + `.planning/milestones/v1.13-ROADMAP.md` reflect Phase 102 closed.
5. v1.13 milestone is unblocked: the next user action is `/gsd-complete-milestone v1.13`.
</verification>

<success_criteria>
- All 4 ROADMAP success criteria for Phase 102 satisfied:
  1. All 9 critical/blocker findings closed (verified by Plan 102-01 + close-loop).
  2. All 58 warning findings closed (verified by Plan 102-02 + close-loop).
  3. All 52 info findings closed (verified by Plan 102-03 + close-loop).
  4. `gsd-code-reviewer 102` returns clean.
- Baselines preserved: `./mvnw clean verify -Pe2e` green; JaCoCo line coverage ≥ 88.88 %; SpotBugs BugInstance count remains 0; CI E2E median within 17:39 ± 20 %.
- STATE.md Deferred Items section reviewed (Task 4 prompt; any item that turned out to be closable as part of this fix-phase is closed).
- PR description refresh — NOT in this plan per CONTEXT non-goals; happens AFTER milestone close.
</success_criteria>

<output>
Create `.planning/phases/102-code-review-fixes/102-04-SUMMARY.md` capturing the close-loop result + STATE/ROADMAP updates. The NEW Phase-102 historical record lives in `.planning/phases/102-code-review-fixes/102-REVIEW.md` per CONTEXT D-13.
</output>
