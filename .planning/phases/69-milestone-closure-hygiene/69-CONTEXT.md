# Phase 69: v1.9 Milestone Closure Hygiene — Context

**Gathered:** 2026-05-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Pure v1.9 closure bookkeeping. No new REQ-IDs, no new features, no behavior changes. Closes the `tech_debt` bucket from `v1.9-MILESTONE-AUDIT.md` (2026-05-08) so the milestone can ship with self-consistent process artifacts.

Seven success criteria from ROADMAP.md, all process/bookkeeping:

- SC1: Phase 64 `64-VERIFICATION.md` (sweep-derived, 7/7 SCs)
- SC2: Phase 65 `65-VERIFICATION.md` (UAT-derived, 3/3 tests + SC1=0 grep)
- SC3: Phase 61 status flip from `human_needed` → `passed` (UAT-01 + UAT-02)
- SC4: Phase 67 status flip from `human_needed` → `passed` (residue ACCEPT/RE-OPEN)
- SC5: SUMMARY-frontmatter `requirements-completed` filled for plans 58-01..06, 59-01..05, 60-01..07 (~20 REQ-IDs across 8 SUMMARYs)
- SC6: Phases 65/66/67/68 VALIDATION.md frontmatter flipped per audit; `./mvnw verify` exit 0 with JaCoCo line coverage ≥ 82%
- SC7: Branch invariant `gsd/v1.9-season-phases-groups` at every checkpoint and at commit time

Out of scope: any new test code beyond Nyquist auto-fill triggered by genuine gaps (mirrors Phase 64 methodology); any change to shipped REQ-ID semantics; modification of Flyway migrations.

</domain>

<decisions>
## Implementation Decisions

### SC3 — Phase 61 UAT closure path
- **D-01:** UAT-01 (GROUPS-Saison standings visual smoke after `f5b10bc`) closes via **Auto-UAT** using `playwright-cli`. Plan boots `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, drives the existing GROUPS fixture (Season 2023), captures screenshots of phase-tab + group-sub-tab + per-group standings + combined view to `.screenshots/`, records outcome in a new `.planning/phases/61-cleanup-quality-gate/61-HUMAN-UAT.md` artifact (status `passed` with screenshots + dev-server log excerpt).
- **D-02:** UAT-02 (Legacy migrated season visual smoke) closes via **formal defer with sign-off**. Defer-note in `61-HUMAN-UAT.md`: requires real pre-V4 production data, local fixtures only exercise the empty-state path (D-18 read-only); not release-blocking; user verifies opportunistically after next production deploy. Phase 61 status flips to `passed` regardless of UAT-02 sign-off because UAT-01 is the BLOCKED-then-FIXED follow-up; UAT-02 was already deferred during the original 2026-05-02 UAT cycle.
- **D-03:** After UAT-01 + UAT-02 are recorded, Plan flips `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` frontmatter `status: human_needed` → `status: passed`, adds an UAT-Closure addendum section pointing to `61-HUMAN-UAT.md`. The existing `human_verification:` block is preserved verbatim (audit trail) — only `status` and a new `uat_closed: 2026-05-08` field flip.

### SC4 — Phase 67 residue closure path
- **D-04:** Phase 67 residue (~124 attribution markers — D-NN, Pitfall N, embedded Phase NN, SC4, UX-NN, IMPORT-NN — across ~40 files in `src/main` + `src/test`) is **ACCEPTED + formal override**. The 5 D-19 grep gates were the contract; gates are 5/5 GREEN; per-file judgement methodology (CONTEXT.md D-13 explicitly forbade automated regex bulk delete because of Javadoc false-positive risk) deliberately produced this residue.
- **D-05:** Override is recorded as an **addendum section in the existing `67-VERIFICATION.md`** (not a separate `67-OVERRIDE.md`). Single-source preferred — verifier already wrote a "Recommended Disposition / Verifier's lean: Option A" section that the override formalises. Frontmatter flips `status: human_needed` → `status: passed`, `overrides_applied: 0` → `overrides_applied: 1`, and the `deferred:` block stays verbatim with a new `human_verification:` resolution note recording the user's ACCEPT decision and date.
- **D-06:** Residue is **explicitly captured for a future "Quality Gate Lock" / CI-pre-commit-guard phase** (no new phase number assigned in v1.9; the next milestone will inherit this backlog item). Recorded in `<deferred>` below — Phase 69 does NOT add a new phase to v1.9 ROADMAP for this work; that would re-open the milestone.

### SC1 + SC2 — Phase 64 + 65 VERIFICATION.md authoring
- **D-07:** Phase 64 `64-VERIFICATION.md` is **synthesised from `64-01-SUMMARY.md` content** (sweep-derived; 7/7 SCs verified per SUMMARY's "Outcome" table). The artifact is retroactive — Phase 64 IS the sweep, so the SUMMARY is the primary truth source. VERIFICATION.md mirrors the SUMMARY's REQ-ID coverage table + Auto-fill table + Manual-Only escalations + Scope-expansion section, with frontmatter `status: passed`.
- **D-08:** Phase 65 `65-VERIFICATION.md` is **synthesised from `65-UAT.md` (3/3 tests `pass` with screenshots) + the SC1 grep proof (`grep -nR "calculateStandings(seasonId" src/main/java | wc -l` = 0)**. The artifact is retroactive — Phase 65 shipped on UAT.md `status: complete` per project precedent. VERIFICATION.md mirrors the UAT entries' expected/result/evidence/screenshots blocks + adds the SC1 grep gate + JaCoCo 87.8% coverage claim, with frontmatter `status: passed`.

### SC5 — SUMMARY-frontmatter `requirements-completed` sweep
- **D-09:** Sweep covers exactly **8 plan SUMMARYs**: 58-01..06 (SVC-01..05 distribution), 59-01..05 (IMPORT-01/02/04, DATA-01/02), 60-01..07 (UI-01..07). The audit's "missing in SUMMARY frontmatter" note documents which REQ-IDs each plan satisfies; the sweep edits the YAML frontmatter only — SUMMARY body text stays verbatim.
- **D-10:** Verification is **`gsd-sdk query summary-extract` returning non-empty arrays** for each plan ID (per ROADMAP SC5 exact wording). Plan in Phase 69 runs the query post-sweep and includes the JSON output in the plan SUMMARY's evidence section.
- **D-11:** Mapping each plan → REQ-IDs uses the audit table as primary truth (`v1.9-MILESTONE-AUDIT.md` lines 90-110 map each REQ-ID to source plan); cross-checked against each plan's `<truth>` / `<requirement>` references in body text. No invented mappings — only what the existing VERIFICATION tables already document as SATISFIED.

### SC6 — Nyquist sweep depth for 65/66/67/68
- **D-12:** **Phase 67 + Phase 68 are marked `n/a` by-design** (mirrors Phase 63 docs-only treatment). VALIDATION.md frontmatter becomes `nyquist_compliant: n/a` + `wave_0_complete: n/a` with rationale: Phase 67 = comments-only diff (no bytecode change, comment-cleanup cannot regress test coverage by construction); Phase 68 = `pom.xml` Lombok pin + JEP 498 flag in 3 fork sites (build-only; no logic-code path under test). Audit note added to each VALIDATION.md but no new test code generated.
- **D-13:** **Phase 65 + Phase 66 audited via `gsd-validate-phase` (mirrors Phase 64 methodology)**. Auditor reads each phase's REQ-ID set from PLAN frontmatters, audits Wave 0 test coverage against existing test infrastructure. If audit reveals genuine gaps, **auto-fill with new tests inline in Phase 69 plan** (single atomic commit per phase, like 64-01 SUMMARY's `V3MigrationTest auto-fill`). If no gaps, frontmatter flip is mechanical (`nyquist_compliant: false` → `true`, `wave_0_complete: false` → `true`).
- **D-14:** Auto-fill commits use Conventional Commit prefix `test(69)` (not `test(65)` / `test(66)`) — Phase 69 owns the bookkeeping, the auto-fill is a Phase 69 deliverable applied retroactively to a closed phase. Avoids re-opening the closed phase's commit history narrative.
- **D-15:** Manual-Only escalations (Visual-Quality-Bar, MariaDB-CI tier, etc.) are documented per row with concrete `Why Manual` rationale (Phase 64 standard) — frontmatter flips to `nyquist_compliant: true` once all entries are either COVERED or Manual-Only-with-rationale.

### SC6 — Final verify gate
- **D-16:** Phase 69 closes with **`./mvnw verify -Pe2e`** (one final command), honouring `feedback_e2e_verification`. Surefire (1246 tests) + Failsafe Playwright E2E (31 tests) + JaCoCo 0.82 line gate. Expected runtime ~5-7 min. SC6 evidence is the BUILD SUCCESS log + JaCoCo line-coverage figure recorded in 69-VERIFICATION.md.
- **D-17:** No intermediate full `verify` runs during Phase 69 plan execution (per `feedback_test_call_optimization`). Targeted `./mvnw test -Dtest=…` per Nyquist auto-fill task, then ONE `./mvnw verify -Pe2e` at phase gate. UAT-01 Auto-UAT runs `playwright-cli` separately (not via Failsafe profile); its dev-server is started + stopped within the plan's task scope.

### SC7 — Branch hygiene
- **D-18:** Active branch `gsd/v1.9-season-phases-groups` at every checkpoint and at commit time (mirrors Phase 67's verified Gate 19). Subagent prompts MUST forbid `git stash`, `git checkout`, `git reset`, branch switching (per CLAUDE.md Subagent Rules + `feedback_subagent_stability`). Post-dispatch validation block (current branch + last 3 commits + diff stat) immediately after EVERY subagent.
- **D-19:** No worktree for Phase 69 — work is bookkeeping (markdown edits + frontmatter flips + at most 1-2 auto-fill test files). Inline on `gsd/v1.9-season-phases-groups`, commits stay atomic per SC.

### Plan organisation (Claude's discretion)
- **D-20:** Planner is free to choose 1 mega-plan vs. 4-7 focused plans (one per SC or grouped). Constraint: each plan must be atomically executable per CLAUDE.md "Atomic Tasks" rule. Recommended grouping (planner refines): Plan 69-01 = SC1+SC2 (VERIFICATION.md authoring); Plan 69-02 = SC3+SC4 (status flips, including Auto-UAT for UAT-01); Plan 69-03 = SC5 (SUMMARY-frontmatter sweep); Plan 69-04 = SC6 (Nyquist sweep + final verify gate); SC7 is invariant maintained by every plan, not its own plan.

### Claude's Discretion
- Exact wording of override / defer / closure addendums (must include date, rationale, sign-off citation)
- Order of SC1+SC2 vs SC3+SC4 vs SC5 vs SC6 (independent; can run as parallel waves where worktree-isolation isn't required for inline markdown edits)
- Whether `61-HUMAN-UAT.md` is a new file (D-01 implies yes) or a new section in `61-VERIFICATION.md` (planner may choose; new file recommended for audit-trail clarity)
- Exact Nyquist gap-fill test naming + location (must follow existing test conventions: `*Test.java` for Surefire, `*IT.java` for Failsafe)
- Whether to update `.planning/STATE.md` `last_activity` per plan or only at phase close

</decisions>

<specifics>
## Specific Ideas

- "Phase 64 swept 56-62 with auto-fill (V3MigrationTest)" — same methodology for 65/66 (D-13). 67/68 mirror Phase 63 docs-only treatment (n/a by-design).
- "verifier's lean: Option A" (Phase 67 residue ACCEPT) — already documented in `67-VERIFICATION.md` "Recommended Disposition" section. Phase 69 formalises by flipping status + adding override note.
- "EIN finaler verify am Ende" + "immer mit -Pe2e" — both feedback memories honoured by exactly one `./mvnw verify -Pe2e` at phase close (D-16/D-17).
- "Branch invariant" parallels Phase 67's verified Gate 19 — same enforcement pattern.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Source of truth (audit + roadmap)
- `.planning/v1.9-MILESTONE-AUDIT.md` — The audit (2026-05-08) that surfaced all 14 tech-debt items closed by Phase 69. Frontmatter `tech_debt:` block enumerates every gap; tables map each REQ-ID to source plan and current SUMMARY-frontmatter status.
- `.planning/ROADMAP.md` §"Phase 69: v1.9 Milestone Closure Hygiene" (lines 488-516) — Phase Goal, Gap Closure list, 7 Success Criteria (SC1-SC7).

### Phase artifacts to author or update
- `.planning/phases/64-nyquist-validation-sweep/64-01-SUMMARY.md` — primary truth for new `64-VERIFICATION.md` (D-07). Authoritative on what passed for SC1.
- `.planning/phases/65-graphics-bridge-migration/65-UAT.md` — primary truth for new `65-VERIFICATION.md` (D-08). 3/3 UAT tests with screenshots.
- `.planning/phases/65-graphics-bridge-migration/65-02-SUMMARY.md` (and 65-01, 65-03 SUMMARYs) — additional evidence for `65-VERIFICATION.md`.
- `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` — frontmatter to flip (D-03); existing `human_verification:` block stays verbatim.
- `.planning/phases/67-comment-cleanup-resweep/67-VERIFICATION.md` — frontmatter to flip + override addendum (D-05); "Recommended Disposition" section already lays the groundwork.
- `.planning/phases/65-graphics-bridge-migration/65-VALIDATION.md` — draft to flip post-audit (D-13).
- `.planning/phases/66-team-shortname-collision-fix/66-VALIDATION.md` — draft to flip post-audit (D-13).
- `.planning/phases/67-comment-cleanup-resweep/67-VALIDATION.md` — draft to flip to `n/a` (D-12).
- `.planning/phases/68-lombok-unsafe-deprecation-warning-fix/68-VALIDATION.md` — draft to flip to `n/a` (D-12).

### Plan SUMMARYs requiring frontmatter sweep (SC5)
- `.planning/phases/58-service-layer/58-{01..06}-SUMMARY.md` — fill `requirements-completed` for SVC-01..05.
- `.planning/phases/59-import-test-data/59-{01..05}-SUMMARY.md` — fill `requirements-completed` for IMPORT-01/02/04, DATA-01/02.
- `.planning/phases/60-admin-ui/60-{01..07}-SUMMARY.md` — fill `requirements-completed` for UI-01..07.

### Project conventions (constraints — non-negotiable)
- `CLAUDE.md` — branch workflow (origin/master, never local master), Subagent Rules (model selection, branch protection, post-dispatch validation, plan adherence, atomic tasks, fallback), TDD/BDD (Given-When-Then naming), test coverage 82% minimum, Flyway immutability.
- `.planning/PROJECT.md` — v1.9 milestone state (1246 tests, 87.24% line coverage), out-of-scope list, key decisions table.
- `.planning/STATE.md` — current position (Phase 66 COMPLETE, branch `gsd/v1.9-season-phases-groups`, milestone v1.9 100% complete on phase count).
- `pom.xml` — JaCoCo `<minimum>0.82</minimum>` line gate (must remain unchanged; SC6 evidence).

### Methodology mirrors
- `.planning/phases/64-nyquist-validation-sweep/64-PLAN.md` — Phase 64 sweep methodology (mirror for D-13).
- `.planning/phases/64-nyquist-validation-sweep/64-CONTEXT.md` — same.

### Tooling references
- `gsd-sdk query summary-extract` — SC5 verification mechanism (D-10).
- `playwright-cli` skill — UAT-01 Auto-UAT execution (D-01).
- `gsd-validate-phase` skill — Nyquist audit per phase (D-13).
- `gsd-auto-uat` skill — alternative wrapper for UAT-01 (D-01; user feedback `feedback_auto_uat_reminder` recommends this for UI-heavy phases).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 64 methodology** (`64-01-SUMMARY.md`): retroactive REQ-ID audit → auto-fill (where genuine gap) → frontmatter flip → SUMMARY pattern. Phase 69 mirrors this for SC1, SC2, SC6 (D-07/D-08/D-13).
- **Phase 61 + 67 frontmatter shape** (status, score, gaps, deferred, human_verification): both phases already use the structured frontmatter that Phase 69 mutates. No new schema needed.
- **`gsd-sdk query summary-extract`**: programmatic mechanism for SC5 evidence collection (per ROADMAP SC5 wording).
- **`mariadb-migration-smoke.yml` CI workflow** (commit `bed0ffd`): unrelated to Phase 69 directly, but its existence eliminates one Phase 61 UAT concern (UAT-03 V6 MariaDB) — that test is already de-facto closed by CI.
- **`LegacyMigratedSeasonE2ETest`** (Phase 61 SC4): proves Legacy-Saison rendering works on the post-V6 schema for empty standings; UAT-02 only adds the populated-data path which test fixtures cannot synthesise without real data — supports D-02 defer rationale.
- **`GroupsSeasonE2ETest`** (Phase 61 SC3): proves GROUPS workflow programmatically; UAT-01 adds visual confirmation post-`f5b10bc` dropdown fix — supports D-01 Auto-UAT scope (small click-through, not full E2E re-run).
- **Project-local fixtures** (Season 2023 GROUPS + Season 2024-3 Empty-Phase): seeded by `dev,demo` profile DevDataSeeder; UAT-01 leans on Season 2023 directly without test-data fabrication.

### Established Patterns
- **Atomic commits per SC** (Phase 67 used `style(67-NN)` + `docs(67-NN)`): Phase 69 plans follow same shape with `docs(69-NN)` for VERIFICATION/SUMMARY edits, `test(69)` for Nyquist auto-fill (D-14), `style(69)` not expected (no comment cleanup in scope).
- **Frontmatter-first edits** (Phase 67 SUMMARYs, Phase 64 VALIDATION updates): edit YAML frontmatter atomically, body text stays verbatim where evidence is unchanged. SC5 follows this pattern strictly.
- **Branch invariant verification** (Phase 67 Gate 19): `git branch --show-current` check after every subagent; same pattern for Phase 69 SC7.
- **Override addendum sections** (Phase 61 UAT Closure Update 2026-05-02T00:35:00Z): existing precedent for adding closure narrative as a new section at the bottom of an existing VERIFICATION.md without rewriting prior content. Phase 69 D-03/D-05 reuse this shape.

### Integration Points
- Phase 69 commits flow into the v1.9 milestone close. After SC1-SC7 are met, the milestone is ready for `/gsd-audit-milestone` re-run (which should now report `verdict: passed` instead of `tech_debt`) and then `/gsd-complete-milestone`.
- The audit doc (`v1.9-MILESTONE-AUDIT.md`) is NOT updated by Phase 69 — it is a point-in-time snapshot. Phase 69's SUMMARY artifacts become the new evidence chain that a re-audit would consult.
- No interaction with shipped feature code: no entity edits, no service edits, no template edits, no controller edits. Only `.planning/phases/**/*.md` + `.planning/STATE.md` + `.planning/ROADMAP.md` + (conditionally) new `src/test/java/**/*Test.java` files for Nyquist auto-fill.

</code_context>

<deferred>
## Deferred Ideas

- **Quality Gate Lock / CI comment-noise guard** (Phase 67 D-06 forward-looking commitment): an automated gate (Maven Enforcer plugin / pre-commit hook / CI grep gate) that blocks commits introducing `// D-NN`, `// Pitfall N`, embedded `// Phase NN` mid-sentence, etc. Out of v1.9 scope (would re-open the milestone); captured for the next milestone's backlog. Add to `.planning/MILESTONES.md` "next-milestone candidates" or open a backlog item via `/gsd-add-backlog` post-Phase-69.
- **Phase 67 RE-OPEN with Plan 67-04** (alternative to D-04 ACCEPT): mechanical sweep across ~40 files to strip the 124 attribution markers. Rejected for Phase 69 because (a) verifier explicitly leaned ACCEPT, (b) D-13 of Phase 67 forbade automated regex bulk delete due to Javadoc false-positive risk. Re-evaluate if a future "Quality Gate Lock" phase wants byte-zero residue.
- **WARN-1: per-group matchday generation UI affordance** (carried from v1.6 / v1.9 first audit; `SeasonController.generateMatchdays:251` hardcodes `groupId=null`). Documented Rule-3 deviation in Phase 61 QUAL-02. Out of v1.9 scope; candidate for future UI phase.
- **OBS-3: `StandingsController.java:139` lazy collection** (`resolvedPhase.getGroups()` — same anti-pattern Phase 64 fixed elsewhere). Read-only OSIV-safe context, not a defect, style-only. Defer to next milestone's Code Review pass.
- **OBS-4: REQUIREMENTS.md traceability table missing rows for phases 64-68**. By design — gap-closure phases reinforce existing IDs, never claim new REQ-IDs. No action needed.
- **UAT-02 (Legacy season visual smoke with real data)** (D-02): user verifies opportunistically after next production deploy with actual pre-V4 legacy-season data. Not release-blocking.
- **Audit re-run + milestone close**: After Phase 69 SC1-SC7 met, run `/gsd-audit-milestone` to confirm `tech_debt` → `passed`, then `/gsd-complete-milestone` to archive v1.9. Out of Phase 69 scope (Phase 69 ends at `./mvnw verify -Pe2e` BUILD SUCCESS + 69-VERIFICATION.md `passed`).

</deferred>

---

*Phase: 69-milestone-closure-hygiene*
*Context gathered: 2026-05-08*
