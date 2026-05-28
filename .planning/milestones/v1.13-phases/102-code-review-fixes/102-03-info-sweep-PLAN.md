---
phase: 102-code-review-fixes
plan: 03
type: execute
wave: 3
depends_on:
  - 102-02
files_modified:
  - src/main/**
  - src/test/**
  - src/main/resources/db/migration/V8__discord_global_config.sql
  - src/main/resources/db/migration/V9__teams_discord_role_id.sql
  - src/main/resources/db/migration/V13__discord_provisional_phase_aware.sql
  - src/main/resources/db/migration/V15__matchday_pairings_template_fields.sql
  - src/main/java/org/ctc/discord/DiscordTimestamps.java
  - src/main/java/org/ctc/admin/dto/SeasonForm.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/resources/templates/admin/season-edit.html
  - src/main/java/org/ctc/backup/mixin/DiscordGlobalConfigMixIn.java
  - src/main/java/org/ctc/backup/mixin/DiscordPostMixIn.java
autonomous: true
requirements:
  - REVIEW-FIX-03

must_haves:
  truths:
    - "ZERO comment-pollution markers remain in src/main, src/test, and src/main/resources/db/migration after this plan ships"
    - "Dead code surfaces removed: DiscordTimestamps.clock() package-private accessor (if confirmed unused), SeasonForm.discordRaceResultsThreadId field, SeasonForm.discordStandingsThreadId field"
    - "Jackson annotation alignment applied per the info findings — @JsonInclude(NON_NULL), @JsonIgnoreProperties where REVIEW.md lists them"
    - "Markdown-link escape for `)` in URLs covered as part of the Discord style cleanup (if not already in 102-02 Task 4)"
    - "Existing tests stay green — no test code logic changes, only pollution removal + dead-code deletions + style annotations"
    - "Flyway migration checksums stay stable for V8/V9/V13/V15 (executable SQL byte-identical; only header comment preamble edited per CONTEXT non-goals)"
  artifacts:
    - path: "(no new files created)"
      provides: "Plan 102-03 is a sweep — modifies existing files only"
      contains: "n/a"
  key_links:
    - from: "blanket grep oracle in CONTEXT.md <specifics>"
      to: "all files in src/main src/test src/main/resources/db/migration"
      via: "grep -rnE '^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )' returns 0 lines"
      pattern: "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )"
---

<objective>
Close all 52 info findings across Phase 92-99 + 101 via mechanical text-edits: comment-pollution removal, dead-code deletion, Jackson annotation alignment, minor style consistency. Per CONTEXT D-02, all 52 close in this plan; the only acceptable closure-without-fix is "demonstrably inapplicable" judgment recorded in SUMMARY.md (e.g., a legitimate WHY-comment per CLAUDE.md).

Per CONTEXT D-04, this plan **skips the per-plan `/gsd-code-review` step** — info findings are mechanical text-edits with near-zero regression risk; their close-loop is the full Phase-102 review in Plan 102-04.

Purpose: Apply the CLAUDE.md "No Comment Pollution" convention milestone-wide so v1.13 ships on a clean codebase. The blanket grep oracle in CONTEXT.md `<specifics>` is the authoritative acceptance criterion — it must return 0 lines after this plan ships.

Output: 5 atomic commits (sweep src/main, sweep src/test, sweep migration headers, dead-code removal, style annotation alignment). NO new tests in this plan — info findings don't move behavior.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/102-code-review-fixes/102-CONTEXT.md
@.planning/phases/92-carry-forwards-cleanup/92-REVIEW.md
@.planning/phases/93-discord-foundation/93-REVIEW.md
@.planning/phases/94-team-roles-match-channel-lifecycle/94-REVIEW.md
@.planning/phases/95-match-channel-posts/95-REVIEW.md
@.planning/phases/96-provisional-graphic-forum-threads/96-REVIEW.md
@.planning/phases/97-matchday-level-posts/97-REVIEW.md
@.planning/phases/98-polish-e2e-docs-close/98-REVIEW.md
@.planning/phases/99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref/99-REVIEW.md
@.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-REVIEW.md
@CLAUDE.md

<execution_notes>
Inline-sequential on `gsd/v1.13-discord-integration` per CONTEXT D-05. Atomic commit per task per CONTEXT D-07.

**Authoritative oracle** (CONTEXT.md `<specifics>`): after this plan ships,
```
grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration
```
must return 0 lines. Whitespace before the marker tolerated; marker after literal `//`, `--`, or `#` not.

Per CONTEXT non-goals: **no new Flyway migrations**. V8/V9/V13/V15 file-header comment-pollution is text-edit only — Flyway hashes executable SQL, not full-file comment preamble. Verify checksum stability via dev-profile re-run before each commit. **Use `./scripts/app.sh start dev`** (NOT `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`) per project memory `feedback_dev_server_via_app_sh.md` and CLAUDE.md "Development Approach" — `app.sh` loads `.env.dev` correctly. If `.env.dev` is absent locally, Flyway startup against the H2 dev profile still proves checksum stability because the Flyway init runs before any `.env`-dependent code path; but `app.sh` is the canonical entry per project convention.

Per CLAUDE.md "No Comment Pollution": legitimate non-obvious WHY-comments are PRESERVED. The grep oracle does not match generic WHY comments — it matches phase / plan / D-NN / finding-ID markers specifically.

Per CONTEXT D-02: if during execution a finding is judged "demonstrably inapplicable" (e.g., a comment-marker turns out to be a legitimate WHY comment that happens to mention "Phase NN"), the executor records the rationale in SUMMARY.md and skips that specific edit. The grep oracle stays clean because the regex requires the marker to FOLLOW the `//` / `--` / `#` literal directly — a legitimate WHY comment containing the word "Phase" mid-sentence does not match.
</execution_notes>
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Comment-pollution sweep — src/main/ (production source)</name>
  <files>src/main/java/**/*.java, src/main/resources/templates/**/*.html, src/main/resources/application*.yml</files>
  <read_first>
    - .planning/phases/102-code-review-fixes/102-CONTEXT.md `<specifics>` block (the grep oracle)
    - CLAUDE.md "Conventions / No Comment Pollution" (the hard-banned marker list)
    - Output of `grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main` — this is the work-list
  </read_first>
  <action>
    Run the grep oracle scoped to `src/main` to enumerate every offending line:
    `grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main`

    For each matched line:
    1. Read the surrounding context (2 lines above + below).
    2. Decide: delete the comment line OR rewrite to a legitimate non-obvious WHY-comment per CLAUDE.md "Default: no comments. Allowed (rare): single-line WHY".
    3. Default action: DELETE. Rewrite only when the comment carries genuinely non-obvious WHY content that survives without the phase/finding marker.

    Also remove file-header restatement blocks per CLAUDE.md "Hard-banned in source files / File-header comment blocks restating what the file does or repeating conventions". Examples: `Compatible with H2 + MariaDB`, `DO NOT mutate this file after release`, full-file-header Javadoc that restates the class name.

    Also remove `Added for X`, `used by Y`, `called from Z` cross-references — they are greppable.

    Per CLAUDE.md "When refactoring, remove pollution from touched files." — this is the milestone-wide application of that rule.

    Single atomic commit at the end. Subject: `chore(102-03): remove phase/plan/finding-ID comment-pollution markers in src/main`.
  </action>
  <verify>
    <automated>grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main | wc -l | grep -E "^[[:space:]]*0$"</automated>
  </verify>
  <acceptance_criteria>
    - The grep oracle scoped to `src/main` returns 0 lines.
    - `./mvnw test-compile` exits 0 (no compile breakage from comment-removal).
    - No `// Phase`, `// Plan`, `// D-NN`, `// UAT-N`, `// WR-NN`, `// CR-NN`, `// IN-NN`, `// BL-NN`, `// Wave N` markers remain in any .java, .html, or .yml file under `src/main`.
    - One atomic commit on the milestone branch.
  </acceptance_criteria>
  <done>Comment-pollution sweep of production source complete; commit subject above.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Comment-pollution sweep — src/test/ (test source)</name>
  <files>src/test/java/**/*.java, src/test/resources/**/*.yml</files>
  <read_first>
    - Output of `grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/test`
    - CLAUDE.md "Conventions / No Comment Pollution" (hard-banned set applies equally to tests per the convention text)
  </read_first>
  <action>
    Same algorithm as Task 1, scoped to `src/test`. Default action DELETE; rewrite only for legitimate non-obvious WHY.

    Special case: BDD-style `// given / // when / // then` comments are LEGITIMATE per CLAUDE.md "Test Naming". The oracle regex does not match them (they start with `//` followed by `given/when/then`, NOT `Phase/Plan/D-/UAT/WR/CR/IN/BL/Wave`).

    Special case: tag-instruction comments like `// Tag: integration` would not match the regex either; preserve them only if they document non-obvious behavior. Default delete.

    Single atomic commit at the end. Subject: `chore(102-03): remove phase/plan/finding-ID comment-pollution markers in src/test`.
  </action>
  <verify>
    <automated>grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/test | wc -l | grep -E "^[[:space:]]*0$"</automated>
  </verify>
  <acceptance_criteria>
    - The grep oracle scoped to `src/test` returns 0 lines.
    - `./mvnw test-compile` exits 0.
    - `// given / // when / // then` BDD body-comments preserved (verify by sampling 3 test files — each has its existing BDD structure intact).
    - One atomic commit on the milestone branch.
  </acceptance_criteria>
  <done>Comment-pollution sweep of test source complete.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 3: Comment-pollution sweep — Flyway migration headers (V8 / V9 / V13 / V15)</name>
  <files>src/main/resources/db/migration/V8__discord_global_config.sql, src/main/resources/db/migration/V9__teams_discord_role_id.sql, src/main/resources/db/migration/V13__discord_provisional_phase_aware.sql, src/main/resources/db/migration/V15__matchday_pairings_template_fields.sql</files>
  <read_first>
    - All four migration files in full (they're small; read each completely so the executor knows where the executable SQL begins and the header preamble ends)
    - CLAUDE.md "Do Not Modify Flyway Migrations" (the executable SQL stays byte-identical; only header preamble `--` comments are edited)
    - CLAUDE.md "Development Approach" + project memory `feedback_dev_server_via_app_sh.md` (dev-server invocation goes through `./scripts/app.sh start dev`, NOT direct `./mvnw spring-boot:run` — `app.sh` loads `.env.dev` correctly)
    - CONTEXT.md non-goals (Flyway hashes executable SQL, not full-file comment preamble — verify checksum stability via `app.sh` dev-profile re-run)
    - Output of `grep -nE "^\\s*--\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main/resources/db/migration/V{8,9,13,15}*.sql`
  </read_first>
  <action>
    For each of the 4 migration files:
    1. Identify the boundary between the header comment preamble (top-of-file `-- ` lines) and the first executable SQL statement (`CREATE TABLE`, `ALTER TABLE`, etc.).
    2. Strip all `-- Phase / -- Plan / -- D-NN / -- WR-NN / -- CR-NN / -- IN-NN / -- BL-NN / -- Wave N` lines from the header preamble.
    3. Preserve any legitimate WHY-comments (e.g., `-- Compatibility note: H2 + MariaDB checked` — but per CLAUDE.md "Hard-banned in source files", file-header restatements like that are ALSO banned; default delete unless the comment carries genuinely non-obvious WHY content).
    4. Leave executable SQL completely untouched. Do NOT reformat indentation, do NOT change column order, do NOT touch statement terminators.
    5. After EACH file edit, run a dev-profile re-run sanity check via `./scripts/app.sh start dev` (canonical entry per project memory `feedback_dev_server_via_app_sh.md`). Observe the Flyway startup logs; if Flyway reports `MigrationChecksumMismatch`, revert the file and try a smaller edit. If the SQL stayed byte-identical, the checksum stays stable. Stop the server with `./scripts/app.sh stop dev` between iterations. If `.env.dev` is missing locally, the Flyway init still runs against H2 before any `.env`-dependent code path, so checksum stability is still provable — but `app.sh` remains the canonical entry.

    Single atomic commit. Subject: `docs(102-03): remove phase/plan/finding-ID comment markers from V8/V9/V13/V15 migration headers (checksum-safe)`. Use `docs(...)` not `chore(...)` because the change is documentation-text only.
  </action>
  <verify>
    <automated>grep -nE "^\\s*--\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main/resources/db/migration/V8__*.sql src/main/resources/db/migration/V9__*.sql src/main/resources/db/migration/V13__*.sql src/main/resources/db/migration/V15__*.sql | wc -l | grep -E "^[[:space:]]*0$"</automated>
  </verify>
  <acceptance_criteria>
    - The grep oracle scoped to the 4 migration files returns 0 lines.
    - Flyway runs cleanly: `./scripts/app.sh start dev` does NOT report `MigrationChecksumMismatch` in the startup logs (start, observe logs, stop with `./scripts/app.sh stop dev`).
    - Executable SQL byte-identical (verify by `git diff src/main/resources/db/migration/V8__*.sql` showing only header-comment line removals, never SQL statement changes).
    - One atomic commit.
  </acceptance_criteria>
  <done>Migration header pollution sweep complete; Flyway checksums stable.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 4: Dead-code removal — DiscordTimestamps.clock() + SeasonForm orphan fields</name>
  <files>src/main/java/org/ctc/discord/DiscordTimestamps.java, src/main/java/org/ctc/admin/dto/SeasonForm.java, src/main/java/org/ctc/admin/controller/SeasonController.java, src/main/resources/templates/admin/season-edit.html, src/main/resources/templates/admin/season-form.html</files>
  <read_first>
    - src/main/java/org/ctc/discord/DiscordTimestamps.java (focus `clock()` at line 44 — package-private accessor; if not referenced from any test or source, it's dead)
    - src/main/java/org/ctc/admin/dto/SeasonForm.java (focus `discordRaceResultsThreadId` at line 30, `discordStandingsThreadId` at line 33)
    - src/main/java/org/ctc/admin/controller/SeasonController.java (any write/read of those fields)
    - src/main/resources/templates/admin/season-edit.html + season-form.html (any binding to those fields — Thymeleaf `${seasonForm.discordRaceResultsThreadId}` or `th:field`)
    - .planning/phases/95-match-channel-posts/95-REVIEW.md (95 WR-03 — already partially closed in 102-02 Task 6 by neutralizing the controller write; this task DELETES the field)
    - .planning/phases/93-discord-foundation/93-REVIEW.md (any IN finding flagging `clock()` as dead)
  </read_first>
  <action>
    Per CLAUDE.md "Grep All Usages Before Refactor": for each candidate dead symbol, `grep -rn` the entire src/ to confirm zero usages.

    Sub-A — `DiscordTimestamps.clock()`:
    1. `grep -rn "DiscordTimestamps.*clock\\|\\.clock()" src/`.
    2. If 0 hits (or only the declaration line itself), DELETE the method.
    3. If hits exist outside the file itself, DO NOT delete; record the finding as "demonstrably inapplicable" in SUMMARY.md with the grep output as evidence.

    Sub-B — `SeasonForm.discordRaceResultsThreadId`:
    1. `grep -rn "discordRaceResultsThreadId" src/`.
    2. Confirm: no template binds it (`th:field="*{discordRaceResultsThreadId}"` returns 0); no controller reads it; only the controller WRITE (already neutralized by 102-02 Task 6) and the field declaration remain.
    3. DELETE the field declaration in `SeasonForm`, the controller WRITE line (if still present after 102-02), and any setter call. Also delete the `discordRaceResultsThreadId` reference in `org.ctc.domain.service.SeasonManagementService` if it survived 102-02 — `grep -rn "discordRaceResultsThreadId" src/main` should return 0 lines after this task.

    Sub-C — `SeasonForm.discordStandingsThreadId`:
    1. Same algorithm as Sub-B (including the parallel cleanup in `SeasonManagementService`).

    Any other dead-code surface flagged in the IN findings (read each `### IN-NN` heading in the 9 REVIEW.md files; cross-check candidates): apply the same grep-then-delete discipline.

    Single atomic commit. Subject: `chore(102-03): remove dead-code surfaces (DiscordTimestamps.clock, SeasonForm.discord*ThreadId orphan fields)`.
  </action>
  <verify>
    <automated>./mvnw test-compile && ./mvnw test -Dtest=SeasonFormTest,SeasonControllerTest,DiscordTimestampsTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - The verify command exits 0.
    - `grep -rn "DiscordTimestamps.*clock\\|\\.clock()" src/` shows zero outside the file itself (or the file shows the method removed).
    - `grep -rn "discordRaceResultsThreadId\\|discordStandingsThreadId" src/` returns 0 lines.
    - Existing tests for SeasonForm, SeasonController, DiscordTimestamps stay green.
    - One atomic commit.
  </acceptance_criteria>
  <done>Dead-code surfaces removed; commit subject above.</done>
</task>

<task type="auto" tdd="false">
  <name>Task 5: Style annotation alignment + remaining info findings</name>
  <files>src/main/java/org/ctc/backup/mixin/DiscordGlobalConfigMixIn.java, src/main/java/org/ctc/backup/mixin/DiscordPostMixIn.java, plus any other files identified by the IN findings (e.g., Jackson annotations, tab-vs-space alignment in src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java)</files>
  <read_first>
    - All 9 REVIEW.md files' `### IN-NN` headings (the full IN finding list)
    - Each cited file referenced by the IN finding's `Location` field
  </read_first>
  <action>
    Enumerate every `### IN-NN` heading across the 9 REVIEW.md files. For each finding NOT covered by Tasks 1-4 (comment-pollution, migration headers, dead-code):
    1. Read the REVIEW.md section in full (Description + Suggested Fix).
    2. Apply the suggested fix verbatim where possible. Examples per 102-CONTEXT.md `<specifics>`:
       - `@JsonInclude(NON_NULL)` / `@JsonIgnoreProperties` alignment on backup mixins
       - Markdown-link escape for `)` in URLs (if not already covered by 102-02 Task 4)
       - Tab-vs-space indent alignment (e.g., 98 IN-05 — `MatchdayPairingsGraphicService` uses TABs)
       - Test-fixture cleanup
       - JavaDoc-vs-exception-type mismatch (101 IN-03)
       - Log-injection risk in path-formatted log messages (101 IN-05)
       - any other small-style finding listed in the IN sections
    3. If a finding is judged "demonstrably inapplicable" (per CONTEXT D-02), record the rationale in SUMMARY.md and skip.

    Group commits by phase or by category, NOT one commit per info finding (per CONTEXT.md `<specifics>` "Plan 102-03 sweep commit cadence" — avoid 50+ trivial commits). Suggested grouping: one commit for Jackson/style annotations, one commit for log-injection + safety adjustments, one commit for indent/spacing cleanups. Final task target: 1-3 commits.

    Commit subject form: `style(102-03): align Jackson annotations + minor style — IN-NN,IN-NN,...` listing finding-IDs.
  </action>
  <verify>
    <automated>./mvnw test -Dtest=BackupSchemaGuardTest -DfailIfNoTests=true</automated>
  </verify>
  <acceptance_criteria>
    - The verify command exits 0.
    - Every `### IN-NN` finding across the 9 REVIEW.md files has either: (a) a corresponding code change in this commit-group, OR (b) a "demonstrably inapplicable" rationale recorded in SUMMARY.md with grep evidence.
    - 1-3 atomic commits on the milestone branch.
    - No new marker comments (the blanket grep oracle still returns 0 on the full repo scope).
  </acceptance_criteria>
  <done>All remaining 52 info findings closed (or recorded inapplicable); commit subjects per the grouping above.</done>
</task>

</tasks>

<verification>
After all 5 tasks land:
1. **Authoritative oracle** — `grep -rnE "^\\s*(//|--|#)\\s*(Phase |Plan |D-[0-9]|UAT-|WR-|CR-|IN-|BL-|Wave )" src/main src/test src/main/resources/db/migration` MUST return 0 lines.
2. `./mvnw test-compile` exits 0.
3. `./scripts/app.sh start dev` starts cleanly — no `MigrationChecksumMismatch` from Flyway in the startup logs. Stop with `./scripts/app.sh stop dev` after the check (per project memory `feedback_dev_server_via_app_sh.md` and CLAUDE.md "Development Approach"). Direct `./mvnw spring-boot:run` is forbidden as a dev-server entry in this repo.
4. Per CONTEXT D-04 — Plan 102-03 **SKIPS** the per-plan `/gsd-code-review` step. Close-loop happens in Plan 102-04.
5. Then commit the SUMMARY.md.
</verification>

<success_criteria>
- All 52 info findings closed (or recorded inapplicable with rationale).
- Blanket grep oracle returns 0 lines.
- Flyway checksums stable for V8/V9/V13/V15 (no new V16 migration created per CONTEXT non-goals); verified via `./scripts/app.sh start dev` startup logs.
- Existing tests stay green.
- 5-7 atomic commits total (1 per Task 1-4, 1-3 for Task 5).
</success_criteria>

<output>
Create `.planning/phases/102-code-review-fixes/102-03-SUMMARY.md` capturing:
- Per-task commit SHA + grep-oracle output (showing 0 lines after each task).
- For Task 5: per-finding closure status (fixed | inapplicable + rationale).
- Total finding-count: closed=N, inapplicable=M, where N+M=52.
- Per-plan review status: "skipped per CONTEXT D-04 (full review happens in 102-04)".
- Note: Flyway-sanity-check used `./scripts/app.sh start dev` (not direct `./mvnw spring-boot:run`) per project memory.
</output>
</content>
</invoke>