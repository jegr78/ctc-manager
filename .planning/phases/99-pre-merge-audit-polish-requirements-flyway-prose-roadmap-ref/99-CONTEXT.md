# Phase 99: Pre-merge audit-polish — Context

**Gathered:** 2026-05-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Pre-merge polish to close the `tech_debt`-tier gaps documented in
`.planning/v1.13-MILESTONE-AUDIT.md` (verdict 2026-05-25) BEFORE
`/gsd-complete-milestone v1.13` archives the milestone and PR #130 squash-merges.
All 5 audit-recorded debt items are documentation-shape only — no production
code changes:

1. **REQUIREMENTS.md Flyway-version prose drift** — POST-01 says `V11 discord_post`
   (actual `V12`); FORUM-01 says `V12 seasons.discord_*_thread_id` (actual `V13`).
2. **REQUIREMENTS.md FORUM-01 acceptance text mis-claims a "Create new Thread..."
   modal** that was never built; rewrite to reflect the operator-workflow that
   actually shipped (create-in-Discord → link-existing).
3. **`.planning/ROADMAP.md` v1.13 Progress table stale** — shows 92/94/96/98
   "Not started", 93/97 "In Progress"; reality = all 7 complete.
4. **6 missing top-level `9N-VERIFICATION.md` files** (Phases 92, 94, 95, 96, 97,
   98); only Phase 93 has one. Retrofill per v1.12 DOCS-01 precedent.
5. **Stale phase-level VALIDATION.md frontmatter** on Phases 93 + 95.

**Explicit non-goals (out of scope for Phase 99):**
- Building the FORUM-01 "Create new Thread" admin-UI modal — explicitly not
  built; YAGNI verdict on 2026-05-25 — see D-02 + Plan 99-05 for the matching
  `DiscordRestClient.createThread()` deletion.
- Touching any `src/` production code BEYOND the `createThread` YAGNI deletion
  in Plan 99-05 (method + DTO + IT test — surgical removal of a single unused
  surface). Plans 99-01..04 remain pure Markdown-edits; only 99-05 changes
  `src/`.
- Running `/gsd-validate-phase 95` rollup-stamp — phase-95 frontmatter is fixed
  inline since the per-plan close (`95-04-VALIDATION.md` BUILD SUCCESS) is
  authoritative.
- Creating a new PR or squash-merging — milestone PR #130 already tracks this
  branch; merge happens at `/gsd-complete-milestone v1.13`, not after Phase 99.
- Updating MILESTONES.md v1.13 entry (already authored in Phase 98-07 commit
  `8989bd72`).

</domain>

<decisions>
## Implementation Decisions

### FORUM-01 Modal Scope

- **D-01:** **Acceptance-text correction, no UI build.** Rewrite
  `.planning/REQUIREMENTS.md` line 66 (FORUM-01 acceptance text) so the shipped
  scope reads as:
  - (a) read-only display of currently-linked thread (name + ID) or "not linked"
  - (b) "Link existing Thread..." modal listing threads from the corresponding
    forum-channel (active + archived) via `DiscordForumService.listThreads`
  - (c) "Unlink" button clearing only the DB field
  - **Operator-workflow note**: To add a new thread, the operator creates it
    directly in the Discord forum-channel, then links it via the modal in (b).
    In-app thread-creation is deferred (see Deferred Ideas → v1.14 backlog).
  Delete the entire "(c) Create new Thread... modal with default-name template"
  sub-clause from the current prose; renumber as needed.
- **D-02:** Backend `DiscordRestClient.createThread()` (line 110, verified in
  audit) is **deleted in Plan 99-05 (YAGNI)**, alongside the unused
  `ThreadCreateRequest` DTO (`src/main/java/org/ctc/discord/dto/ThreadCreateRequest.java`)
  and the orphan IT test `given200_whenCreateThread_thenReturnsThread()` in
  `DiscordRestClientIT.java:149-157`. The `Thread` record + `listActiveThreads` /
  `listArchivedThreads` endpoints + private `ThreadList` record remain — they
  are consumed by `DiscordForumService.listThreads()`. User verdict 2026-05-25:
  YAGNI — if v1.14 (or later) builds the in-app create-modal, the method comes
  back via TDD.
- **D-03:** Update audit doc `v1.13-MILESTONE-AUDIT.md` tech_debt-bucket-5 + the
  Requirements-Coverage row for FORUM-01: flip "satisfied (partial)" → "satisfied"
  once the acceptance text matches the shipped surface; replace the
  "v1.14-deferred backend method" note with the YAGNI-deletion record so future
  readers see the consciously-narrowed surface (operator-workflow only) rather
  than a phantom deferral.

### Flyway Prose Drift (POST-01 / FORUM-01)

- **D-04:** **POST-01** (`.planning/REQUIREMENTS.md` line 50): change "Flyway V11
  `discord_post` table" → "Flyway V12 `discord_post` table". The text-string
  change is purely prose; schema + migration filename `V12__...` are
  authoritative and already correct.
- **D-05:** **FORUM-01** (line 66): change "Flyway V12 adds
  `seasons.discord_race_results_thread_id` + `seasons.discord_standings_thread_id`"
  → "Flyway V13 adds `seasons.discord_race_results_thread_id` +
  `seasons.discord_standings_thread_id`". Combined with D-01's acceptance-text
  rewrite in the same edit, single atomic commit per REQ.
- **D-06:** No other REQ-IDs touched — only POST-01 and FORUM-01 had the
  off-by-one drift per the audit. Final grep-check after edit: `grep -n "V1[12]" .planning/REQUIREMENTS.md`
  must reflect the actual migration map (V11 = `matches.discord_channel_archived_at`,
  V12 = `discord_post`, V13 = seasons discord-threads + forum-webhooks).

### ROADMAP.md v1.13 Progress Table Refresh

- **D-07:** Update the v1.13 row in the top-level Progress table
  (`.planning/ROADMAP.md` ~line 284) — change "In flight | -" to
  "Complete | 2026-05-25" (audit date).
- **D-08:** Update the per-phase v1.13 Phase Progress table (~line 287): flip
  all 7 rows to "Complete" with their actual completion dates inferred from
  MILESTONES.md / per-plan SUMMARY commits:
  - Phase 92: `4/4 | Complete | 2026-05-21` (DOCS-01 retrofill commit)
  - Phase 93: `3/3 | Complete | 2026-05-21` (UAT-03 PASS date)
  - Phase 94: `3/3 | Complete | 2026-05-22` (per-phase VALIDATION date)
  - Phase 95: `4/4 | Complete | 2026-05-23` (UAT-05 PASS date)
  - Phase 96: `3/3 | Complete | 2026-05-23` (UAT-06 PASS date `b01af26d`)
  - Phase 97: `3/3 | Complete | 2026-05-24` (UAT-07 PASS date)
  - Phase 98: `7/7 | Complete | 2026-05-25` (UAT-08 16-stage PASS date)
- **D-09:** Phase 99 itself: leave its dedicated `### Phase 99` block alone
  except for filling in `Goal` / `Requirements` / `Plans` once `/gsd-plan-phase 99`
  produces them (out of Phase-99-CONTEXT scope; handled by planner).
- **D-10:** Phase 100 row stays untouched (different milestone scope — Match Day
  Channel Naming Scheme, not part of v1.13).

### 9N-VERIFICATION.md Retrofill (Phases 92/94/95/96/97/98)

- **D-11:** **Scope: all 6 missing files**, written into the still-active
  `.planning/phases/9N-*/9N-VERIFICATION.md` (NOT `.planning/milestones/v1.13-phases/`
  — milestone archive happens later at `/gsd-complete-milestone v1.13`).
- **D-12:** **Template:** v1.12 DOCS-01 precedent
  (`.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VERIFICATION.md`).
  Required frontmatter keys (verbatim, with phase-specific substitutions):
  ```yaml
  ---
  phase: 9N
  verified_on: <today's date, ISO YYYY-MM-DD>
  status: passed
  verifier: gsd-verifier (retroactive — v1.13 carry-forward Phase 99 audit-polish)
  score: <N>/<N> success-criteria + <M>/<M> dimensions
  overrides_applied: 0  # or 1 if 9N-VALIDATION.md notes any
  audit_method: retroactive
  ---
  ```
- **D-13:** **Required body sections** (both mandatory per the Phase 92 DOCS-01
  acceptance grep-checks `92-VALIDATION` rows 92-04-01/02):
  1. `## Goal Achievement — Success Criteria` — cross-reference each SC from
     the milestone-level `v1.13-ROADMAP.md` per-phase block against rows in
     the existing `9N-VALIDATION.md` Per-Task Verification Map AND per-plan
     `9N-NN-SUMMARY.md` shipped-evidence sections. Status column must be
     VERIFIED for every row (or explicitly note any partial/override).
  2. `## Per-Dimension Verdict Table` — 8 standard dimensions per the v1.12
     precedent: Implementation completeness | Test coverage | Documentation |
     Build & test gate | Security / threat model | Code review | Live UAT |
     Integration with surrounding phases. Each dimension: status + evidence.
- **D-14:** **Substance derivation rule** — no new validation work. Substance
  derived strictly from existing artifacts:
  - `9N-VALIDATION.md` (per-task verification map)
  - per-plan `9N-NN-SUMMARY.md` self-check + deliverables sections
  - UAT outcomes from `STATE.md` + `v1.13-MILESTONE-AUDIT.md` live-UAT table
  - `93-VERIFICATION.md` already-existing close (for Phase 93's section
    cross-reference only — its frontmatter still gets refreshed via D-16/D-17,
    but its content is authoritative and not re-derived).
- **D-15:** Phase 93's existing top-level `93-VERIFICATION.md` is NOT
  overwritten — it already exists with substantive 5/5 SC + 8/8 dim. Phase 99
  only refreshes its frontmatter via the VALIDATION.md route (D-16). Skip
  Phase 93 in the retrofill plan.

### Phase 93 + 95 VALIDATION.md Frontmatter

- **D-16:** **Phase 93** (`.planning/phases/93-discord-foundation/93-VALIDATION.md`):
  inline-edit frontmatter only. Set `nyquist_compliant: true` (authoritative
  close lives in `93-VERIFICATION.md` PASS). Add `verified_on: 2026-05-25` if
  missing; add note: `# nyquist refreshed by Phase 99 audit-polish — VERIFICATION.md is authoritative`.
- **D-17:** **Phase 95** (`.planning/phases/95-match-channel-posts/95-VALIDATION.md`):
  inline-edit frontmatter. Set `status: shipped` (from `draft`) AND
  `nyquist_compliant: true`. Audit-doc note: phase-level frontmatter is shipped;
  per-plan `95-04-VALIDATION.md` already has `nyquist_compliant: true` + BUILD
  SUCCESS and remains the per-plan authoritative close.
- **D-18:** **No `/gsd-validate-phase 95` re-run.** Inline-edit only (user
  decision GA-3 option A). Trade-off: faster, no fresh verifier audit, but the
  per-plan close already covers the substance; phase-level frontmatter is the
  only stale signal.

### Plan Structure (5 atomic plans, sequential inline)

- **D-19:** **Plan 99-01 — REQUIREMENTS.md prose-fix.** Single Markdown-edit
  plan. Updates POST-01 (D-04) + FORUM-01 (D-05 + D-01 acceptance rewrite +
  D-03 audit-doc cross-update). Atomic commit. No tests touch.
- **D-20:** **Plan 99-02 — ROADMAP.md v1.13 Progress refresh.** Single
  Markdown-edit plan. Updates top-level Progress table (D-07) + v1.13 Phase
  Progress per-phase table (D-08). Atomic commit. No tests touch.
- **D-21:** **Plan 99-03 — 9N-VERIFICATION.md retrofill (6 files).** Writes
  6 new files: `92-VERIFICATION.md`, `94-VERIFICATION.md`, `95-VERIFICATION.md`,
  `96-VERIFICATION.md`, `97-VERIFICATION.md`, `98-VERIFICATION.md`. Each
  per D-12 + D-13 + D-14. Single atomic commit (`docs(99-03): retroactive top-level VERIFICATION.md for v1.13 phases 92/94-98`).
- **D-22:** **Plan 99-04 — VALIDATION.md frontmatter refresh.** Inline-edits
  `93-VALIDATION.md` (D-16) + `95-VALIDATION.md` (D-17). Atomic commit.
- **D-23:** **Plan 99-05 — YAGNI delete `createThread()` surface.** Surgical
  removal of the unused thread-creation API:
  1. Delete method body + signature `DiscordRestClient.java:110-117` (the
     `createThread(channelId, request)` public method).
  2. Delete unused import `org.ctc.discord.dto.ThreadCreateRequest` from
     `DiscordRestClient.java:13`.
  3. Delete file `src/main/java/org/ctc/discord/dto/ThreadCreateRequest.java`
     (record; sole consumer was `createThread()`).
  4. Delete IT method `given200_whenCreateThread_thenReturnsThread()` from
     `src/test/java/org/ctc/discord/DiscordRestClientIT.java:149-157` plus the
     now-orphan import on line 24.
  5. Verify keepers stay intact: `Thread` record + `listActiveThreads` +
     `listArchivedThreads` + private `ThreadList` record + `DiscordForumService.listThreads()`
     all still compile and behave identically (no production callers of
     `createThread` exist — confirmed via `grep -rn createThread src/`).
  6. Targeted IT run during TDD-green: `./mvnw verify -Dit.test=DiscordRestClientIT -DfailIfNoTests=false`
     before the plan-level commit.
  Atomic commit (`refactor(99-05): delete unused DiscordRestClient.createThread + ThreadCreateRequest (YAGNI)`).
- **D-24:** **Sequential inline, no subagents.** Per CLAUDE.md Subagent Rules
  + `feedback_chain_inline_milestones.md`, `/gsd-execute-phase 99` MUST use
  `--interactive` (NOT `--auto`). Each plan = one commit on
  `gsd/v1.13-discord-integration`. No worktrees. No parallel waves. Plan order
  is recommended 99-01 → 99-02 → 99-03 → 99-04 → 99-05, but they are mutually
  independent (no `depends_on` between them) — the planner may resequence if
  it has a reason; default is the listed order.
- **D-25:** **End-of-phase test gate** — `./mvnw clean verify -Pe2e` required
  per CLAUDE.md "End-of-Phase Verification" rule. Plan 99-05 changes `src/`
  (method + DTO + IT deletion), so the gate is *genuinely* enforcing now —
  not a no-op. Expected: same green baseline as Phase 98 close (2244 tests
  MINUS 1 deleted IT method → 2243 tests; JaCoCo 88.99 % give-or-take a
  decimal for the removed branch; SpotBugs 0; CodeQL gate-step exit 0). If
  the test-count drop is anything other than exactly −1, investigate before
  declaring close.

### PR + Milestone Workflow

- **D-26:** **Milestone PR #130 stays open.** No new PR for Phase 99 — pushes
  to `gsd/v1.13-discord-integration` automatically update PR #130. After Phase
  99 ships all 5 plans, update PR #130 body via `gh pr edit 130 --body "..."`
  with the Phase 99 entry (5 plans ✓, +6 VERIFICATION.md files, prose-fixes,
  ROADMAP refresh, YAGNI deletion).
- **D-27:** **No squash-merge after Phase 99.** Per `feedback_milestone_merge_timing.md`:
  squash-merge only at `/gsd-complete-milestone v1.13`, never after a single
  phase. Phase 99 ships, then operator runs `/gsd-complete-milestone v1.13`
  separately. No `git tag` either — release CI handles tagging post-merge.

### Claude's Discretion

- Date stamps inside retrofilled `9N-VERIFICATION.md` frontmatter use the date
  Plan 99-03 actually runs (typically 2026-05-25 or whichever ISO date the
  executor uses); not back-dated to the original phase close.
- Exact wording of the FORUM-01 "operator-workflow" note in D-01 is up to the
  planner — must communicate (1) no in-app create UI exists, (2) workflow is
  create-in-Discord → link, (3) link-existing modal is the only UI surface.
- 9N-VERIFICATION.md SC count + dimension count per phase derived from the
  respective ROADMAP.md per-phase block + 9N-VALIDATION.md — planner fills the
  numbers, executor cross-references.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-99 audit source-of-truth
- `.planning/v1.13-MILESTONE-AUDIT.md` — verdict 2026-05-25; defines all 5
  tech_debt items + their exact location. PRIMARY ref — re-read in full before
  writing any plan.

### REQUIREMENTS.md prose targets (Plan 99-01)
- `.planning/REQUIREMENTS.md` line 50 (POST-01 — V11 → V12)
- `.planning/REQUIREMENTS.md` line 66 (FORUM-01 — V12 → V13 + acceptance rewrite)

### YAGNI deletion targets (Plan 99-05)
- `src/main/java/org/ctc/discord/DiscordRestClient.java` lines 110-117 (method)
  + line 13 (import) — delete only those; keep everything else.
- `src/main/java/org/ctc/discord/dto/ThreadCreateRequest.java` — delete entire
  file (record sole-consumed by deleted `createThread()`).
- `src/test/java/org/ctc/discord/DiscordRestClientIT.java` lines 149-157
  (`given200_whenCreateThread_thenReturnsThread()`) + line 24 (orphan import) —
  delete only those; other ~10 IT methods in the file stay green.
- Keeper-grep (must still return non-empty after deletion):
  - `grep -rn "Thread\b" src/main/java/org/ctc/discord/` (record + list endpoints
    + private `ThreadList` stay).
  - `grep -rn "listActiveThreads\|listArchivedThreads\|listThreads" src/`
    (forum-thread listing is still production code consumed by
    `DiscordForumService`).

### ROADMAP.md refresh target (Plan 99-02)
- `.planning/ROADMAP.md` ~line 284 (top-level Progress table — v1.13 row)
- `.planning/ROADMAP.md` ~line 287 (v1.13 Phase Progress per-phase table)

### v1.12 DOCS-01 precedent (Plan 99-03 template + grep-acceptance)
- `.planning/phases/92-carry-forwards-cleanup/92-04-PLAN.md` — original DOCS-01
  plan; lines 193+ contain the exact frontmatter shape Phase 99 reuses.
- `.planning/phases/92-carry-forwards-cleanup/92-VALIDATION.md` rows 92-04-01..02
  — the grep-acceptance commands that proved DOCS-01 shipped.
- `.planning/milestones/v1.12-phases/89-perf-instrumentation-lever-1-per-fork-backup-staging-dir/89-VERIFICATION.md`
  — concrete example output (frontmatter + Goal Achievement table + Per-Dimension
  Verdict).
- `.planning/milestones/v1.12-phases/90-perf-consolidation-module-split-decision/90-VERIFICATION.md`
  — same template, different phase.
- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-VERIFICATION.md`
  — same template, milestone-closer flavor.

### Existing per-phase VALIDATION + SUMMARY (substance source for Plan 99-03)
- `.planning/phases/92-carry-forwards-cleanup/92-VALIDATION.md` + per-plan
  `92-01..04-SUMMARY.md`
- `.planning/phases/93-discord-foundation/93-VERIFICATION.md` (already exists —
  skip retrofill, only refresh VALIDATION frontmatter in Plan 99-04)
- `.planning/phases/93-discord-foundation/93-VALIDATION.md` + per-plan
  `93-01..03-SUMMARY.md`
- `.planning/phases/94-team-roles-and-match-channel-lifecycle/94-VALIDATION.md`
  + per-plan `94-01..04-SUMMARY.md`
- `.planning/phases/95-match-channel-posts/95-VALIDATION.md` +
  `95-04-VALIDATION.md` (per-plan authoritative close) + per-plan
  `95-01..04-SUMMARY.md`
- `.planning/phases/96-provisional-graphic-forum-threads/96-VALIDATION.md` +
  per-plan `96-01..03-SUMMARY.md`
- `.planning/phases/97-matchday-level-posts/97-VALIDATION.md` + per-plan
  `97-01..03-SUMMARY.md`
- `.planning/phases/98-polish-e2e-docs-close/98-VALIDATION.md` + per-plan
  `98-01..07-SUMMARY.md`

### Milestone planning artifacts (SC source for Plan 99-03)
- `.planning/milestones/v1.13-ROADMAP.md` — per-phase Success Criteria block;
  each phase's SC list feeds the `## Goal Achievement` table.

### Project-level invariants (must read before planning)
- `CLAUDE.md` — "Build & Test Discipline" (clean verify -Pe2e gate), "No
  Comment Pollution" (no Phase/Plan/Task refs in source files), "Git Workflow"
  (Milestone PR Already Exists), "Subagent Rules" (inline sequential, no
  worktrees), "GSD Workflow Discipline" (in-milestone polish — no deferral).
- `.planning/STATE.md` — current milestone state + Deferred Items (4
  carry-forward post-deploy UATs unaffected by Phase 99).
- `.planning/MILESTONES.md` line 10 + 35 — already references the
  `v1.13-MILESTONE-AUDIT.md` retroactive close; do NOT re-author.

### Post-merge boundary (do NOT touch in Phase 99)
- `docs/operations/discord-integration.md` — Phase 98 runbook; out of scope.
- `README.md` + `ctc-manager.wiki.git` — Phase 98 DOCS-03 already shipped; out
  of scope.
- `.planning/RETROSPECTIVE.md` — milestone-close artifact, not Phase 99.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **v1.12 DOCS-01 plan template:** `92-04-PLAN.md` lines 80-220 contain the
  exact retroactive-VERIFICATION.md authoring task pattern. Plan 99-03 reuses
  this verbatim (with v1.13 SC counts substituted).
- **`92-VALIDATION.md` grep-acceptance pattern:** rows 92-04-01..02 demonstrate
  the verification grep commands that prove the retrofilled VERIFICATION.md
  conforms (frontmatter `audit_method: retroactive`, required section
  headers). Plan 99-03 reuses the same grep-checks as task acceptance.

### Established Patterns
- **Retroactive VERIFICATION.md frontmatter** — fixed shape per v1.12 (phase,
  verified_on, status, verifier, score, overrides_applied, audit_method:
  retroactive). DOCS-01 grep-check in 92-VALIDATION.md row 92-04-02 enforces
  this. Plan 99-03 must produce identical shape.
- **In-milestone polish stays in-milestone** — CLAUDE.md "GSD Workflow
  Discipline → In-Milestone Polish — No Deferral". v1.13's debt closes IN
  v1.13 (this phase), not v1.14. Phase 99's existence is the precedent for
  this rule.
- **YAGNI deletion of unused production surfaces** — CLAUDE.md "Doing tasks":
  "Don't add features, refactor, or introduce abstractions beyond what the
  task requires." The deleted `createThread()` surface had no caller (audit
  verified 2026-05-25); the only fixture was its own IT test. Recoverable
  from git history if the FORUM-01 modal-build ever happens.
- **Grep-all-usages discipline** — CLAUDE.md "Architectural Principles → Grep
  All Usages Before Refactor". Plan 99-05 must re-run `grep -rn createThread src/`
  + `grep -rn ThreadCreateRequest src/` as a pre-edit safety check; current
  state shows only the 5 targeted occurrences (DiscordRestClient method+import,
  ThreadCreateRequest record, IT test+import). If grep returns more, planner
  STOPS and re-scopes.
- **Inline sequential execution, no subagents** — CLAUDE.md "Subagent Rules"
  + `feedback_chain_inline_milestones.md`. Phase 99 follows this regardless
  of plan count.
- **Atomic commit per plan** — each of 5 plans = one commit. Per CLAUDE.md
  "Atomic Tasks" + Phase 98 precedent.

### Integration Points
- **PR #130 (milestone PR)** — already open on `gsd/v1.13-discord-integration`;
  Phase 99 pushes accumulate; no new PR. Update body after Phase 99 closes.
- **`/gsd-complete-milestone v1.13`** — the immediate next step after Phase 99
  closes. Phase 99 prepares the ground; milestone-close executes the actual
  archive + tag-via-CI flow.
- **`.planning/phases/99-*/` directory** — already exists with `.gitkeep`; new
  Phase 99 artifacts (CONTEXT.md, PLAN.md, SUMMARY.md, VALIDATION.md) land
  here. NOT yet under `milestones/v1.13-phases/` — that path is reserved for
  post-archive.

</code_context>

<specifics>
## Specific Ideas

- **Single grep-check after Plan 99-01 commits:** `grep -nE "V1[12]" .planning/REQUIREMENTS.md`
  must show V12 next to `discord_post` and V13 next to `seasons.discord_*_thread_id`.
  Audit-doc tech_debt-bucket-1 calls this out specifically; planner must include
  it as an acceptance step.
- **Reuse the exact Phase-92 frontmatter wording** — `verifier: gsd-verifier (retroactive — v1.13 carry-forward Phase 99 audit-polish)`. The "carry-forward Phase 99" string is the audit-trail link between this retrofill batch and the v1.13 milestone audit; do not paraphrase.
- **FORUM-01 acceptance rewrite tone** — neutral and matter-of-fact ("Operator
  creates the thread directly in the Discord forum-channel, then links it via the
  modal in (b)"). Do not editorialize ("simpler than", "less than ideal").

</specifics>

<deferred>
## Deferred Ideas

### v1.14 Backlog (booked, only if/when relevant)
- **FORUM-01 "Create new Thread..." admin-UI modal** — if v1.14 (or later)
  decides to build the in-app create-modal in `season-form.html`, the deleted
  `DiscordRestClient.createThread()` method comes back via TDD (the original
  Phase 96 implementation is in git history at commit-range matching FORUM-01).
  Not a "deferral" — a genuine *if-needed-then-rebuild* note. No backlog entry
  is created automatically; only book it when product priority warrants.

### Out-of-scope for Phase 99 (covered elsewhere)
- **MILESTONES.md v1.13 entry** — already authored (Phase 98-07 commit
  `8989bd72`). Not re-touched.
- **PR #130 body refresh** — post-Phase-99 step performed manually after Plan
  99-04 commits; not part of any plan.
- **`/gsd-complete-milestone v1.13`** — separate command after Phase 99 ships;
  archives phases under `milestones/v1.13-phases/`, generates
  RETROSPECTIVE.md, triggers CI release.
- **`v1.13.0` Git tag** — release CI creates this post-merge; never manually
  per CLAUDE.md "No Local Git Tags".
- **The 4 carry-forward post-deploy operator UATs** (UAT-02, QUAL-02, UX-01,
  UAT-04) — these are pre-existing operator actions, not v1.13 regressions;
  audited as `blocks_close: false`; cross-milestone debt that survives Phase 99.

</deferred>

---

*Phase: 99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref*
*Context gathered: 2026-05-25*
