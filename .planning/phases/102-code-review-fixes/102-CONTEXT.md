# Phase 102: Code-Review Fixes (v1.13 closeout) — Context

**Gathered:** 2026-05-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Address every finding from the milestone-wide code-review pass executed on
2026-05-28 across phases 92-101. Input artifacts are the 10 `*-REVIEW.md`
reports on disk:

- `.planning/phases/92-carry-forwards-cleanup/92-REVIEW.md`
- `.planning/phases/93-discord-foundation/93-REVIEW.md`
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md`
- `.planning/phases/95-match-channel-posts/95-REVIEW.md`
- `.planning/phases/96-provisional-graphic-forum-threads/96-REVIEW.md`
- `.planning/phases/97-matchday-level-posts/97-REVIEW.md`
- `.planning/phases/98-polish-e2e-docs-close/98-REVIEW.md`
- `.planning/phases/99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref/99-REVIEW.md`
- `.planning/phases/100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option/100-REVIEW.md`
- `.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md`

Aggregate scope: **9 critical/blocker + 58 warning + 52 info = 119 findings**.
Phase 102 closes all of them so v1.13 ships on a fully reviewed and
remediated codebase. `/gsd-complete-milestone v1.13` is BLOCKED until this
phase ships green per CLAUDE.md "In-Milestone Polish" + the new
"Code-Review Before Phase Close" discipline.

**Explicit non-goals (out of scope for Phase 102):**

- **No new capabilities.** Pure remediation — fixing findings only. Any
  finding flagged as "missing feature X" is closed by adding the safety net
  (e.g., regression test, defensive guard), not by building feature X.
- **No re-scope of Phase 92-101 deliverables.** Findings that suggest
  "POST-NN should also do Y" are deferred unless Y is the literal closure
  of the finding's invariant.
- **No new Flyway migrations.** Comment-pollution removal in `V8/V9/V13/V15`
  file headers is text-edit only; the migration scripts themselves stay
  byte-identical at SQL-statement level (Flyway checksum stability is
  preserved because Flyway hashes the executable SQL, not full-file comment
  preamble — verify with a localdev re-run before each commit).
- **No new milestone PR.** PR #130 already tracks this branch; Phase 102
  commits accumulate on `gsd/v1.13-discord-integration`. PR body update
  happens AFTER Phase 102 closes, per CLAUDE.md "Milestone PR Already
  Exists" rolling-summary discipline.
- **No `/gsd-complete-milestone v1.13` execution.** That is the immediate
  next step *after* Phase 102 ships, not a Phase 102 task.
- **No `v1.13.0` git tag.** Release CI creates this post-merge; never
  manually per CLAUDE.md "No Local Git Tags".
- **Phase 100 REVIEW.md** is excluded from the fix-scope of plan 102-01..03
  (its 2 warning + 6 info findings already landed pre-Phase-101 and were
  triaged as acceptable at the time). It IS re-checked by the close-loop
  pass in Plan 102-04 if any Phase 102 edit touches one of those files.

</domain>

<decisions>
## Implementation Decisions

### Plan-Splitting Strategy

- **D-01: Split by severity, 4 plans.** Sharpest risk-grading; lets the
  highest-risk fixes (the 9 critical) land first in the smallest reviewable
  diff, with regression-fence tests clustered for cross-finding pattern
  consistency.
  - **Plan 102-01 — Critical/Blocker (9 findings + 9 regression tests).**
    All ship-blocking fixes from the milestone summary (see `<code_context>`
    for finding→file mapping). Each fix is paired with at least one
    regression-fence test asserting the invariant the finding violated.
    First plan to land; merged before 102-02 starts.
  - **Plan 102-02 — Warning (58 findings).** Includes the 4 controller-thin
    refactor extracts per D-03 below. Mostly mechanical; each refactor
    keeps the existing tests green plus adds an asserting unit test on the
    new service method.
  - **Plan 102-03 — Info (52 findings).** Comment-pollution sweep (source
    files, Flyway-migration headers, test-class Javadoc), dead-code removal
    (`DiscordTimestamps.clock()`, `SeasonForm.discordRaceResultsThreadId`,
    `SeasonForm.discordStandingsThreadId` orphan fields, etc.), Jackson
    annotation alignment, minor style consistency. Largely mechanical.
  - **Plan 102-04 — Close-loop `/gsd-code-review 102` + remediation.** Full
    Phase-102 diff re-reviewed by `gsd-code-reviewer`; any new findings
    that surface get a remediation sub-pass within this plan. Plan closes
    only when `gsd-code-reviewer` returns `clean` (zero critical + zero
    warning) on the Phase-102 diff scope.

### Info-Findings Scope

- **D-02: Close all 52 info findings in Plan 102-03.** Comment-pollution +
  dead-code + Flyway-migration-header blocks are already hard-banned by
  CLAUDE.md "No Comment Pollution"; style-items (Jackson annotations,
  markdown-link escaping for `)` in URLs, etc.) are low-effort and align
  with the "komplett sauberer Abschluss" milestone close. The 52nd finding
  is in scope unless during execution the planner can demonstrate the
  finding is invalid (e.g., a comment that's a legitimate non-obvious WHY
  comment per CLAUDE.md "Default: no comments. Allowed (rare): single-line
  WHY"). Such judgment calls require an explicit one-line rationale in the
  Plan 102-03 SUMMARY.md (not a STATE.md deferral — the finding is closed
  by being declared inapplicable, not by being postponed).

### Refactoring-Style Warnings

- **D-03: Close controller-thin refactor warnings in 102.** Phase 102 lands
  service-method extractions for:
  - **`MatchController.detail`** — 40-line model-population block extracted
    to `MatchService.buildMatchDetailModel(MatchId)` (or analogous;
    planner finalises the boundary). Controller stays under 15 lines of
    model-population code per CLAUDE.md "Keep Controllers Thin".
  - **`SeasonController.populateDiscordIntegrationModel`** — 45-line helper
    extracted to `SeasonManagementService` or a dedicated
    `DiscordSeasonViewService`.
  - **`MatchdayController` staleness helpers** — 4 helpers totalling 60+
    lines walking match→races→results trees, plus the `seasonTeamRepository`
    field used only by staleness logic. Extract into the
    `StandingsService.staleSinceSnapshot(...)`-style API (or fresh helper
    service if cleaner; planner decides).

  All three refactors are mechanical (extract method, move callers, keep
  contract identical). Existing tests remain green; each extracted method
  gets at least one new unit test asserting its boundary.

  Other warnings that look like refactors but actually are surgical:
  `DiscordPostRef.SeasonRef.applyTo` drops `phaseId` (1-line set
  `target.phaseId = this.phaseId`), `parallel resolve` of
  `resolveAnnouncementChannelId` (extract local-variable in
  `populateMatchdayDiscordModel`), markdown-link escape (1-line replace in
  `streamerField` builder). These belong in Plan 102-02 as point fixes,
  not as service extractions.

### Close-Loop Cadence

- **D-04: `/gsd-code-review 102` runs per-plan AND at end.** Each of Plans
  102-01 and 102-02 spawns a focused review pass on the plan diff (via
  `/gsd-code-review 102 --files=<comma-separated-files>` listing only the
  files the plan modified) immediately after the plan's `./mvnw verify`
  green, BEFORE the plan SUMMARY.md is committed. Findings on the per-plan
  diff are folded back into the same plan (no separate remediation
  sub-plan); the plan only commits SUMMARY.md once review is clean. Plan
  102-04 then runs the full Phase-102 review across the cumulative diff
  (typically 30-50 files) and handles cross-plan interactions.

  - **Per-plan diff scope ≠ full phase scope.** Per-plan review reads only
    the touched files for that plan; it cannot catch cross-plan
    regressions (e.g., a Plan 102-02 warning fix accidentally undoing a
    Plan 102-01 critical fix in a different file). Plan 102-04 closes
    that gap by reviewing the full Phase-102 diff.
  - **No mid-plan spot-checks.** No grep-based "checkpoint" reviews mid-plan;
    full reviewer agent only. Spot-checks have insufficient coverage for
    the milestone-close bar.
  - **Plan 102-03 (info sweep) skips per-plan review.** Info findings are
    mechanical text-edits; their close-loop is the full Phase-102 review
    in 102-04, where any regression surfaces. Saves agent tokens on a plan
    where the risk of a critical regression is near-zero.

### Execution Discipline (locked from prior phases — re-stated for the planner)

- **D-05: Inline-sequential execution.** Per CLAUDE.md "Subagent Rules" and
  the v1.13 `feedback_chain_inline_milestones.md` memory: NO parallel-wave
  subagent dispatch via `gsd-executor`. The orchestrator works inline,
  task-by-task, atomic-commit-per-task, on `gsd/v1.13-discord-integration`.
  Subagents are allowed only for READ-ONLY review passes
  (`gsd-code-reviewer`) — never for code edits.
- **D-06: `--interactive` is mandatory** on any `/gsd-execute-phase 102`
  invocation. Auto-mode is forbidden for the milestone-close phase.
- **D-07: Atomic commit per task within a plan.** Each finding's fix +
  regression test = one commit. Plan 102-01 will produce ~18 commits
  (9 fixes × 2: fix-commit + test-commit, OR co-located fix+test commits
  if conventional). Conventional Commit subject form per finding:
  `fix(<phase>): <one-line summary> — <REVIEW finding ID>`. Example:
  `fix(94): defend matchLabel against bye matches — CR-01`.
- **D-08: Branch stays `gsd/v1.13-discord-integration`.** Per CLAUDE.md
  "Milestone Branch First" — milestone branch is HARD-LOCKED. No feature
  sub-branches. Any subagent dispatched (read-only reviewers only)
  receives the branch lock + no-stash/no-checkout in its prompt per
  CLAUDE.md "Branch Protection".
- **D-09: End-of-phase gate** is `./mvnw clean verify -Pe2e` green +
  SpotBugs 0 + JaCoCo line coverage ≥ 88.88 % + Plan 102-04 returns
  `clean` from `gsd-code-reviewer`. Failure on any of those four blocks
  phase close.

### Test Discipline

- **D-10: TDD-Red/Green for each Plan 102-01 fix.** Per-task loop:
  1. Write regression-fence test that reproduces the finding's failure
     mode (e.g., bye-match-in-pairings query → `assertThat(...)` on the
     resulting NPE / wrong-value).
  2. Run `./mvnw test -Dtest=<TestClass>#<testMethod>` — must FAIL with
     the expected reason.
  3. Apply the fix.
  4. Re-run `./mvnw test -Dtest=<TestClass>` — must PASS.
  5. Run targeted Failsafe for any IT impacted:
     `./mvnw verify -Dit.test=<ITClass> -DfailIfNoTests=false`.
  6. Commit fix + test (atomic).
- **D-11: One single `./mvnw clean verify -Pe2e`** at phase end (Plan
  102-04). Per CLAUDE.md "Targeted Tests during TDD-Red/Green" — clean
  verify is the canonical end-gate, not run per-plan (cost: ~10 min × 4
  plans = 40 min saved; cost: late detection of cross-plan regressions
  is bounded because Plan 102-04 close-loop catches them).
- **D-12: Mocking discipline preserved.** Per CLAUDE.md
  `feedback_wiremock_vs_real_api`: never `@MockitoBean DiscordPostService`
  in transactional ITs that exercise the auto-post hook path. Plan 102-01
  regression-fence tests for the schedule-edit hook (Phase 95 CR-02) must
  use the real Spring `@Transactional` proxy + WireMock-backed
  DiscordWebhookClient.

### Documentation Maintenance

- **D-13: Per-Phase REVIEW.md files left intact.** The 10 `*-REVIEW.md`
  files are historical record of the 2026-05-28 review pass. Phase 102
  does NOT delete or rewrite them. Plan 102-04's close-loop REVIEW.md is
  a NEW file at `.planning/phases/102-code-review-fixes/102-REVIEW.md`,
  separate from the input set.
- **D-14: No CLAUDE.md edits in Phase 102.** The "Code-Review Before Phase
  Close" rule already landed in commit `d4022d6d` before this phase
  started. If a Phase 102 finding suggests a new convention rule, it
  follows the v1.13 precedent (memory entry first, promote to CLAUDE.md
  only when stable across milestones) — but the bar is high; default is
  "fix the code, don't add a rule."

</decisions>

<canonical_refs>
## Canonical References

**MUST read before planning (downstream agents):**

- `.planning/phases/92-carry-forwards-cleanup/92-REVIEW.md`
- `.planning/phases/93-discord-foundation/93-REVIEW.md`
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md`
- `.planning/phases/95-match-channel-posts/95-REVIEW.md`
- `.planning/phases/96-provisional-graphic-forum-threads/96-REVIEW.md`
- `.planning/phases/97-matchday-level-posts/97-REVIEW.md`
- `.planning/phases/98-polish-e2e-docs-close/98-REVIEW.md`
- `.planning/phases/99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref/99-REVIEW.md`
- `.planning/phases/100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option/100-REVIEW.md`
- `.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md`
- `.planning/milestones/v1.13-ROADMAP.md` — Phase 102 section (success criteria)
- `.planning/STATE.md` — Phase 102 current position + locked decisions
- `CLAUDE.md` — full file; specifically:
  - "Architectural Principles" (keep-controllers-thin, no-fallback-calculations, score-aggregation-on-result-save, RaceLineup-source-of-truth, grep-all-usages-before-refactor)
  - "Conventions / No Comment Pollution" (the hard-banned marker set)
  - "GSD Workflow Discipline / In-Milestone Polish" (why Phase 102 exists)
  - "GSD Workflow Discipline / Code-Review Before Phase Close" (new rule; binds Phase 102)
  - "Subagent Rules / Inline Sequential" (no parallel waves)
  - "Build & Test Discipline" (clean verify, WireMock vs Real-API, no flaky dismissal)
- `.planning/codebase/CONVENTIONS.md` — naming + layering patterns
- `.planning/codebase/TESTING.md` — test categorization, regression-fence patterns

**Domain references for specific findings:**

- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` — Phase
  94 CR-02 (orphan-channel cleanup) traces back to design § 3.6
  (channel-creation audit-cleanup composition).
- `docs/operations/discord-integration.md` — Phase 95 CR-02 (schedule auto-edit)
  is mentioned operator-facing in § 7 Stage 5.
- `feedback_wiremock_vs_real_api.md` (auto-memory) — provides the bug-class
  pattern for Phase 95 CR-02 (auto-post hook publish path).
- `project_phase_95_auto_post_hook_fallback.md` (auto-memory) — Option E
  (`@TransactionalEventListener AFTER_COMMIT`) decision history.

</canonical_refs>

<code_context>
## Code Context

### Finding → File Mapping (Plan 102-01 input — 9 critical/blocker)

Each entry: `REVIEW-ID — file:line — one-line invariant the regression test must pin`.

1. **92 CR-01** — `src/main/java/org/ctc/dataimport/CsvImportController.java:154-159` and `:265`
   — Google-reachable `IllegalArg|IllegalState` arms emit only the whitelisted
   `getUserMessage()`, never `e.getMessage()`. Regression test asserts the
   surfaced flash text equals the whitelisted constant.
2. **94 CR-01 / 95 CR-01** (same root) —
   `src/main/java/org/ctc/discord/web/DiscordPostController.java:71` (`matchLabel`)
   — bye matches render without NPE; `awayTeam != null ? … : "Bye"` pattern
   per `MatchController.detail:107`. Regression IT seeds a bye match and
   asserts `GET /admin/discord/posts` returns 200.
3. **94 CR-02** —
   `src/main/java/org/ctc/discord/service/DiscordChannelService.java:85-114`
   — webhook-create-fail path issues the same cleanup DELETE that
   audit-fail uses. WireMock IT stubs webhook-fail and asserts the
   subsequent DELETE was sent.
4. **95 CR-02** — `src/main/java/org/ctc/domain/service/RaceService.java`
   `saveRace(...)` — when `race.setDateTime(newDate)` changes the field,
   publish `MatchScheduleFieldsChangedEvent`. Test covers both
   dateTime-change → event-published and dateTime-unchanged → no-event.
5. **98 BL-01** —
   `src/main/java/org/ctc/discord/service/DiscordPostService.java`
   `canPostMatchdayPairings` + `canPostMatchdaySchedule` — use the
   `allNonByeMatchesFinal`-style guard, not vacuous `allMatch` on empty /
   all-BYE matchdays. Test seeds empty + all-BYE matchdays and asserts
   pre-flight returns false.
6. **98 BL-02** —
   `src/main/java/org/ctc/admin/TestDataService.java` `seedFullMatchdayLifecycle`
   — uses a distinct shortName from the regular dev-seed `T-ALF`
   (e.g. `T-ALC` for "Test Alpha Lifecycle"). E2E setup asserts no
   `IncorrectResultSizeDataAccessException` on `TeamRepository.findByShortName`.
7. **101 CR-01** —
   `src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java`
   `V1_TABLES_24` list — use plural JPA table names (`race_scorings` /
   `match_scorings`) so the synthetic ZIP actually exercises the
   lenient-import path. Assertion proves the v1 archive was *read* and the
   Discord tables stayed empty *because* they were absent from v1, not
   because the lookup failed.
8. **101 CR-02** —
   `src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java`
   (and the 5 sibling restorers) — guard `row.get("X").asText()` chains
   for the 6 NOT-NULL columns (`guildId`, `announcementWebhookUrl`,
   `raceResultsForumChannelId`, `standingsForumChannelId`, `vsEmojiName`,
   `currentMatchCategoryId`). Missing field → `BackupArchiveException(MANIFEST_INVALID, ...)`,
   never NPE. Per-field guard tests cover the 6 columns.

### Refactor Extract Targets (Plan 102-02 input — controller-thin warnings)

- `src/main/java/org/ctc/admin/controller/MatchController.java`
  `detail(...)` — 40+ lines of model population → extract to
  `MatchService.buildMatchDetailModel(matchId)` returning a
  `MatchDetailViewModel` record. Controller becomes 5-line dispatch.
- `src/main/java/org/ctc/admin/controller/SeasonController.java`
  `populateDiscordIntegrationModel(...)` — 45-line helper → extract to
  `SeasonManagementService.buildDiscordIntegrationModel(seasonId)` or
  fresh `DiscordSeasonViewService` (planner decides).
- `src/main/java/org/ctc/admin/controller/MatchdayController.java` —
  4 staleness helpers (60+ lines) + `seasonTeamRepository` field used
  only by them → extract to `StandingsService.snapshotMatchdayStaleness(...)`
  or a fresh helper service (planner decides). `seasonTeamRepository`
  field deleted from controller.

### Established Patterns (re-use, don't rebuild)

- **Bye-match defensive pattern** —
  `MatchController.detail:107` already shows `awayTeam != null ? … : "Bye"`.
  All bye-match fixes mirror this exact pattern.
- **Cleanup-DELETE-on-fail composition** —
  `DiscordChannelService.audit-fail` path is the template for the
  webhook-create-fail extension (Phase 94 CR-02). Same try-catch shape,
  same cleanup-DELETE call.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** —
  `DiscordAutoPostListener.onMatchPreviewFieldsChanged` is the template
  for the new schedule-fields-changed listener (Phase 95 CR-02 fix). REQUIRES_NEW
  + AFTER_COMMIT semantics carry over.
- **`BackupArchiveException(MANIFEST_INVALID, ...)`** — already raised in
  `BackupImportService` for shape errors; restorers extend that pattern
  for missing-NOT-NULL guards (Phase 101 CR-02 fix).
- **Plural JPA table names** — `EXPORT_ORDER` lists the authoritative
  names. Use it as the source of truth for the V1_TABLES list reshape
  (Phase 101 CR-01 fix).
- **Service-extract for controller-thin** — Phase 33 (v1.5 "Controller
  Cleanup") established the pattern: controller delegates to a service
  method that returns a view-model record or populated `Map<String, Object>`.
  Reuse that shape; do not introduce a new convention.

### Integration Points

- **PR #130 (milestone PR)** — accumulating; no new PR per CLAUDE.md
  "Milestone PR Already Exists". Body update happens AFTER Phase 102
  closes, listing the 4 Phase-102 plans + total finding-counts + final
  coverage / test-count deltas.
- **`/gsd-code-review 102 --files=...`** — the per-plan close-loop
  invocation pattern (D-04). Plan 102-01 lists ~9 source files + ~9 test
  files; Plan 102-02 lists the refactor-touched controllers/services +
  the other warning files.
- **`/gsd-complete-milestone v1.13`** — the immediate next step after
  Phase 102 closes. Blocked by the new "Code-Review Before Phase Close"
  rule until Plan 102-04 returns `clean`.

</code_context>

<specifics>
## Specific Ideas

- **Plan 102-01 commit cadence.** ~18 commits expected (9 fixes × 2 OR
  9 atomic fix+test). Conventional Commit subject form:
  `fix(<phase>): <one-line> — <REVIEW finding ID>` for the production
  change; `test(<phase>): regression-fence — <REVIEW finding ID>` for the
  test if separate. Example pair:
  `fix(94): defend matchLabel against bye matches — CR-01` +
  `test(94): pin matchLabel bye-handling — CR-01 regression-fence`.
- **Plan 102-02 refactor commits.** Each controller-thin extract = one
  commit using `refactor(<phase>): …` subject. Example:
  `refactor(102-02): extract MatchController.detail model-population to MatchService.buildMatchDetailModel — WR-thin-1`.
  Warning fixes that are point-edits use `fix(102-02): …` or
  `chore(102-02): …` per Conventional-Commit type.
- **Plan 102-03 sweep commit cadence.** Comment-pollution sweep is best
  as 1-2 commits (one for `src/main/`, one for `src/test/` + Flyway
  headers) — avoids 50+ trivial commits. Dead-code removal is its own
  commit per surface (e.g., one commit for `SeasonForm.discordRaceResultsThreadId`
  removal incl. controller + form-bind cleanups). Style-annotation
  alignments cluster by file.
- **Plan 102-04 close-loop expected agent run.** ~30-50 files in scope
  (the cumulative Phase-102 diff); `gsd-code-reviewer` at standard depth
  ≈ 10-15 min. If review surfaces findings, remediation happens inline
  in 102-04 (not as a new plan) and ends with a second review pass
  proving `clean`. Plan 102-04 SUMMARY.md records both review passes.
- **Test count delta forecast.** +18 to +25 new tests in Plan 102-01
  (regression fences). +3 to +5 in 102-02 (extracted-service boundary
  tests). +0 in 102-03 (info sweep is mechanical; no new tests). Total
  Phase-102 delta: roughly **+21 to +30 tests**. Final v1.13 test count
  estimate: 1842 + 30 = **~1872** (vs current 1842 baseline; well above
  v1.12 1696).
- **JaCoCo delta forecast.** Plan 102-01 + 102-02 add coverage on
  previously-uncovered paths (bye-match labels, webhook-fail cleanup,
  schedule-edit hook publication, lenient-v1-acceptance, restorer
  NOT-NULL guards, extracted service methods). Net expected: **+0.5 to
  +1.0 pp**, landing JaCoCo near **89.5 %**. No fallback if it drops:
  the 82 % pom gate is the hard floor; the 88.88 % baseline is the
  in-milestone aspiration.
- **Comment-pollution grep pattern (Plan 102-03 acceptance).** After
  Plan 102-03 ships:
  ```
  grep -rnE "^\s*(//|--|#)\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration
  ```
  must return **0 lines**. Whitespace before the marker tolerated;
  marker after literal `//` / `--` / `#` not. The grep pattern is the
  authoritative oracle.

</specifics>

<deferred>
## Deferred Ideas

### Out-of-scope for Phase 102 (covered elsewhere or by design)

- **`/gsd-complete-milestone v1.13` execution** — separate command after
  Phase 102 ships; archives phases under `milestones/v1.13-phases/`,
  generates RETROSPECTIVE.md, triggers CI release. Phase 102 *prepares*
  the ground; milestone-close *executes* the archive flow.
- **`v1.13.0` git tag + Docker image** — created by release CI post-merge;
  never manually per CLAUDE.md "No Local Git Tags".
- **PR #130 body refresh** — performed manually after Plan 102-04 commits
  per the rolling-summary discipline; not part of any plan.
- **The 4 carry-forward post-deploy operator UATs** (UAT-02 legacy
  season smoke, QUAL-02 local-profile MariaDB smoke, UX-01 driver-import
  badge screenshots, UAT-04 retry if applicable) — pre-existing operator
  actions, audited as cross-milestone debt, survive Phase 102 by design.
- **v1.13 → v1.14 transition planning** — out of scope; v1.13 is "the
  end of planned features" (user verdict 2026-05-28). Any new
  feature-ideas surfaced during Phase 102 review go to
  `gsd-capture` backlog, not into 102-DEFERRED.

### Explicitly out-of-scope findings (none deferred per D-02)

- Per D-02, all 52 info findings are in-scope for Plan 102-03. The only
  acceptable closure-without-fix is the "demonstrably inapplicable"
  judgment in 102-03 SUMMARY.md (e.g., a legitimate WHY-comment). No
  finding is pre-emptively deferred from Phase 102 to v1.14.

### Memory-promotion candidates surfaced by Phase 102

If during execution we observe a new repeatable pattern (e.g., the
service-extract template for controller-thin fixes works cleanly across
3 sites and the planner believes it generalizes), the orchestrator
considers whether to promote it to CLAUDE.md "Conventions" or to a fresh
feedback memory entry. Per D-14, the bar is high — default is "fix the
code, don't add a rule." Memory candidates are recorded in the Phase 102
RETROSPECTIVE prompt during `/gsd-complete-milestone v1.13`.

</deferred>

---

*Phase: 102-code-review-fixes*
*Context gathered: 2026-05-28*
