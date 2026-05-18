# Phase 87: Nyquist VALIDATION Closure - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Close the v1.10 Nyquist VALIDATION debt: bring all 8 v1.10 phases (71, 72, 73, 74, 75, 76, 78, 79) to `status: approved` with `nyquist_compliant: true` VALIDATION.md files. Drafts exist for phases 72-76 + 79 (`status: draft`, only in git history at ref `60f5f915^`); phases 71 + 78 have no VALIDATION.md at all. Phase 87 runs `/gsd:validate-phase` against each of the 8 phases, fills every auditor-identified gap with a generated test, fixes any auditor-flagged v1.10 implementation bug, and clears the "Nyquist `*-VALIDATION.md` for 6 phases (72-76, 79) + creation for 71 + 78" row from `.planning/STATE.md` "Deferred Items" at v1.11 close.

</domain>

<decisions>
## Implementation Decisions

### Directory Restoration (where v1.10 artefacts live for Phase 87)

- **D-01:** All v1.10 phase artefacts are restored to `.planning/milestones/v1.10-phases/<n>-<slug>/` — the established archive convention used by v1.0, v1.1, v1.2, v1.3, v1.5, v1.6, v1.8, v1.9. This becomes the **permanent home** for v1.10 phase artefacts (parallel to `.planning/milestones/v1.10-ROADMAP.md`, `v1.10-REQUIREMENTS.md`, `v1.10-MILESTONE-AUDIT.md` already at `.planning/milestones/`). The active `.planning/phases/` directory stays reserved for the in-flight v1.11 phases (80-87).
- **D-02:** **Minimal restore scope per phase** — only the files `/gsd:validate-phase` actually consumes: every `<n>-XX-PLAN.md`, every `<n>-XX-SUMMARY.md`, `<n>-CONTEXT.md`, `<n>-VERIFICATION.md`, optionally `<n>-RESEARCH.md` (only if it carries decisions not already in CONTEXT), plus the existing `<n>-VALIDATION.md` draft (for 72-76 + 79). PATTERNS / REVIEW / DISCUSSION-LOG / HUMAN-UAT files stay archived only in git history — they are not consumed by the auditor.
- **D-03:** **Restore source is `git show 60f5f915^:<path>`** (parent of the deletion commit). The exact slugs from git history are:
  - `71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui`
  - `72-backup-wire-contract-schema-manifest-objectmapper-audit-log-`
  - `73-backup-export-jackson-mixins-streaming-zip-endpoint`
  - `74-backup-import-preview-zip-hardening-multipart-config-schema-`
  - `75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat`
  - `76-operational-hardening-import-lock-read-only-banner-auto-back`
  - `78-docker-release-image-fix-pin-base-image-to-ubuntu-noble-for-` (slug to be confirmed from git history during plan execution)
  - `79-code-cleanup-test-performance-optimization-v1-10-milestone-c`
- **D-04:** Restored slugs are kept **verbatim** — no shortening, no re-slug. Cross-references in git messages (`docs(72-04): …`) must still resolve.

### Gap-Coverage Aggressiveness

- **D-05:** **Aggressive gap-filling.** For every gap the `gsd-nyquist-auditor` identifies (COVERED / PARTIAL / MISSING analysis per requirement), a test is generated and committed inside the same phase's atomic commit group. No selective filtering — even LOW / INFO severity gaps get filled. Manual-Only is reserved for behaviour that is genuinely not automatable (e.g., visual UAT, prod-only smoke).
- **D-06:** **No per-phase wallclock cap.** Tests are generated freely; final verification step (87-09 or equivalent closer task) measures `./mvnw verify -Pe2e` wallclock vs. the Phase-86-PR-branch CI median (recorded as 23:00 in 86-VERIFICATION). If post-Phase-87 wallclock regresses by **>5 %** vs that baseline, a tidy-up cycle reviews the most expensive added tests and either downgrades them to `@DataJpaTest` / unit shape or moves them to Manual-Only with explicit rationale.
- **D-07:** **JaCoCo: 82 % gate-only.** Movement between the v1.10-close 87.80 % and the pom.xml gate of 82 % is acceptable inside Phase 87. The hard gate is `./mvnw verify` (pom.xml threshold). No additional "must stay ≥ 87.80 %" guard. New tests are encouraged to add coverage but are not required to.
- **D-08:** **Implementation bugs found by auditor are fixed inside Phase 87.** Each fix lands in the same per-phase atomic commit group with a separate `fix(<phase-n>): …` commit alongside the `test(<phase-n>):` and `docs(<phase-n>): approve VALIDATION` commits. This is a deliberate scope expansion vs. the original Phase 87 framing: closing the validation debt also means closing any genuine v1.10 bugs the audit surfaces. If a fix is **non-trivial** (touches > ~50 LOC, spans multiple files, needs migration, etc.), the orchestrator pauses for a user-decision rather than auto-fixing.

### Plan Structure & Sequencing

- **D-09:** **8 plans, 1 per v1.10 phase.** Plan numbering follows phase-87 convention: `87-01-PLAN.md` through `87-08-PLAN.md`. Each plan is self-contained: (a) restore the v1.10 phase files via `git show 60f5f915^:<path>`, (b) run `/gsd:validate-phase` against the restored directory, (c) apply all gap-coverage tests + any impl bug fixes, (d) update VALIDATION.md frontmatter to `status: approved` + `nyquist_compliant: true`, (e) commit the entire change set with atomic per-phase commits (per VAL-03).
- **D-10:** **Strict numeric sequence, single wave.** 87-01 = Phase 71, 87-02 = Phase 72, 87-03 = Phase 73, 87-04 = Phase 74, 87-05 = Phase 75, 87-06 = Phase 76, 87-07 = Phase 78 (Phase 77 is out of Phase-87 scope per ROADMAP — already compliant per v1.10 MILESTONE-AUDIT), 87-08 = Phase 79. No parallel execution, no wave splits. Maximum isolation of test resources (BackupRoundTripIT, Saison-2023 fixture, etc.) and clean per-phase git history.
- **D-11:** **Closer work is folded into 87-08.** After Phase 79's VALIDATION lands, the same plan (87-08) finishes by: clearing the Nyquist row from `.planning/STATE.md` "Deferred Items" table (satisfies VAL-04), flipping VAL-01..VAL-04 from `[ ]` to `[x]` in `.planning/REQUIREMENTS.md`, and updating `.planning/milestones/v1.10-MILESTONE-AUDIT.md` Nyquist scoreboard from `partial: 6 / missing: 2 / compliant: 1` to `compliant: 9 / partial: 0 / missing: 0`. No separate 87-09 plan — keeps the plan set tight.

### VALIDATION.md Document Shape

- **D-12:** **Hybrid shape — pre-execution template, retroactively filled.** All 8 VALIDATION.md files use the structure from `$HOME/.claude/get-shit-done/templates/VALIDATION.md`: Test Infrastructure table, Sampling Rate, Per-Task Verification Map, Wave 0 Requirements, Manual-Only Verifications, Validation Sign-Off. For the **existing 6 drafts (72-76 + 79)**, the existing layout is preserved and the Per-Task Verification Map rows are filled in with the real test class names and `✅ green` status. For the **2 new VALIDATION files (71 + 78)**, the same template is generated from scratch; the Wave 0 Requirements section is annotated `"satisfied retroactively"` since the implementation tasks already shipped.
- **D-13:** **`status: approved` definition.** A VALIDATION.md is approved when ALL of these hold: (1) every row in the Per-Task Verification Map names a real test file path (no `❌ W0` placeholders), (2) every row has status `✅ green` with CI evidence (run-id citation in commit message or VALIDATION.md), (3) every checkbox in the "Validation Sign-Off" section is checked, (4) frontmatter has `nyquist_compliant: true` AND `status: approved`. Gap tests and impl-bug fixes are part of the same per-phase commit group as the VALIDATION update — no in-between half-approved state.

### Branch & PR Strategy

- **D-14:** Phase 87 work continues on the existing milestone branch `gsd/v1.11-tooling-and-cleanup` (per `feedback_milestone_branch`). The existing v1.11 PR (#122 per STATE.md) gets the per-phase commits appended; PR description is updated at phase close per `feedback_pr_description_update`. No separate Phase-87 branch.

### Claude's Discretion

- The exact slugs for restored phase directories (verify against git history; if a slug is truncated due to filesystem limits, preserve the truncation form already used in the original deletion commit).
- The exact phrasing of `<n>-VALIDATION.md` Sign-Off checkboxes (template wording may be lightly adapted for retroactive language: "All tasks have <verify> or post-hoc evidence" instead of "All tasks have <verify> or Wave 0 dependencies").
- Test-class naming for generated gap-coverage tests — follow existing Phase 71-79 conventions seen in restored files; place them in the same package as the implementation they cover, with `@Tag("integration")` for `*IT.java` and `@Tag("e2e")` only when Playwright is involved.
- Whether the Plan 87-08 closing commit set is one fat squash or 3-4 small commits (REQUIREMENTS / STATE / AUDIT updates) — planner picks based on diff size.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope, Requirements & Workflow

- `.planning/ROADMAP.md` §"Phase 87: Nyquist VALIDATION Closure" — Goal, Depends-on (Phase 86), Requirements (VAL-01..VAL-04), Success Criteria
- `.planning/REQUIREMENTS.md` §VAL-01..VAL-04 — full requirement text
- `.planning/PROJECT.md` §"Current Milestone: v1.11 ... Validation closure" — milestone framing
- `.planning/STATE.md` §"Deferred Items" — the Nyquist row to be cleared by VAL-04 in plan 87-08
- `$HOME/.claude/get-shit-done/workflows/validate-phase.md` — `/gsd:validate-phase` workflow used per phase (State A: audit existing; State B: reconstruct from artifacts)
- `$HOME/.claude/get-shit-done/templates/VALIDATION.md` — shape for new VALIDATION.md files (Phases 71, 78)

### v1.10 Archive Context (must read to understand each phase under audit)

- `.planning/milestones/v1.10-ROADMAP.md` — full Phase 71-79 details (goal / depends-on / requirements / plans per phase)
- `.planning/milestones/v1.10-REQUIREMENTS.md` — 39/39 REQ-IDs traced to v1.10 phases (PLAT, SCHEMA, EXPORT, IMPORT, SECU, QUAL, PLAT-CI, D-01..D-20)
- `.planning/milestones/v1.10-MILESTONE-AUDIT.md` — audit verdict (passed); Nyquist scoreboard (`compliant_phases: 1 / partial_phases: 6 / missing_phases: 2`)

### Restore Source (git history)

- git ref `60f5f915` — "chore(v1.11): clear v1.10 phase directories before new milestone" — the deletion commit; **restore source is `60f5f915^`** (its parent). Use `git show 60f5f915^:<path>` for every restored file.
- git ref `80b64f2b` — "chore(v1.10): archive milestone files and collapse roadmap" — moved ROADMAP/REQUIREMENTS/AUDIT into `.planning/milestones/` (already current head)

### Testing Conventions (must respect)

- `.planning/codebase/TESTING.md` §"Test Categorization (`@Tag`)" — every new test class needs `@Tag("integration")` or `@Tag("e2e")`; Surefire-only stays untagged
- `.planning/codebase/TESTING.md` §"Test Invocation Discipline" — one final `./mvnw verify -Pe2e` per phase; targeted `-Dtest=` / `-Dit.test=` between waves
- `CLAUDE.md` §"Constraints" — JaCoCo gate 82 % minimum
- `CLAUDE.md` §"Architectural Principles" — Tag Tests by Category, Isolate Test Data Completely
- `CLAUDE.md` §"Subagent Rules" — gsd-nyquist-auditor `Read`-only against impl files; impl bug fixes are an orchestrator-level decision

### Phase-86 Baseline (for wallclock guard at phase close)

- `.planning/phases/86-test-wallclock-reduction/86-VERIFICATION.md` — PERF-05 CI median = **23:00** on the PR branch (per D-17). This is the wallclock floor that the Phase-87 closer compares against; >5 % regression triggers the D-06 tidy-up cycle.
- `docs/test-performance.md` — Phase-86 baseline + verdict; new gap tests appended to existing infrastructure must fit the documented pattern (no new Maven profile, no new fork-config change).

### Deliverables To Be Created in Phase 87

- `.planning/milestones/v1.10-phases/<n>-<slug>/<n>-VALIDATION.md` × 8 — final approved files
- Generated gap-coverage test files (locations decided per phase by the auditor)
- Any `fix(<phase-n>): …` commits for impl bugs the auditor surfaces

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`@Tag("integration")` / `@Tag("e2e")` routing** — Phase 79 / Phase 86 established the convention; every generated gap test inherits it.
- **`@DataJpaTest` slice pattern** — Phase 86 (D-05..D-08) added `JpaAuditingConfig` and converted 3 repository ITs; same slice infrastructure is available for any auditor-generated repository-only gap test.
- **`@SpringBootTest + @Transactional` IT pattern** — still the default for cross-tier behavior; auditor uses this when the gap spans Controller→Service→Repository.
- **`BackupRoundTripIT` + `BackupImportMariaDbSmokeIT`** — already cover the export→wipe→import golden path on H2 + MariaDB; gaps in Phase 75 cluster are likely edge-cases (rollback semantics, post-commit listener idempotency) not the happy path.
- **`docs/test-performance.md`** — Phase 86 deliverable; Phase 87's wallclock-guard step appends a "Post-Phase-87" row showing the wallclock delta from new gap tests.

### Established Patterns

- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** for post-commit file-system mutations (Phase 75 D-04) — auditor must respect when generating tests for the post-commit upload-tree path.
- **`AuditingEntityListener` bypass via `JdbcTemplate.batchUpdate`** (Phase 75 D-07) — backup-restore tests must not assume `AuditingEntityListener` fired; auditor needs to know this if generating restore-path tests.
- **`@EntityGraph` eager-fetch on every collection field reachable in export aggregate** (Phase 73 D-03) — generated tests asserting "no LazyInitializationException" must use the same `findAllForBackup()` repository methods.
- **`ReentrantLock` in `ImportLockService`** (Phase 76 D-01) — singleton state; tests generated for the 503/409 paths must reset the lock between tests (use the reset-helper-bean pattern from Phase 86 D-03).
- **CodeQL FP markers** (`// CodeQL FP: <rule-id> — …`) and **SpotBugs suppressions** in `config/spotbugs-exclude.xml` — Phase 87 gap tests must not trigger new SAST/SpotBugs alerts; if a generated test legitimately needs a suppression, the suppression goes in `sast-acceptance.md` per the established workflow.

### Integration Points

- **`.planning/milestones/v1.10-phases/<n>-<slug>/`** — new archive directories created by Phase 87's restore step. After Phase 87 closes, this matches the v1.0-v1.9 archive convention.
- **`/gsd:validate-phase` agent invocation** — workflow expects `${PHASE_DIR}/*-PLAN.md` and `${PHASE_DIR}/*-SUMMARY.md`; the restore step ensures these exist before each per-phase plan invokes the workflow.
- **`gsd-nyquist-auditor` subagent** — per `feedback_subagent_stability`: never invoked with `model: "haiku"`; branch-protection language ("no `git stash`, `git checkout`, `git reset`") must be in every prompt; post-dispatch validation runs `git branch --show-current` + `git log --oneline -3` + `git diff --stat`.
- **CI workflow `.github/workflows/ci.yml`** — already has `workflow_dispatch` trigger (Phase 86 PERF-05); Phase 87 closer uses the same trigger for wallclock-guard CI re-baseline if needed.

</code_context>

<specifics>
## Specific Ideas

- **Commit message shape per phase:** `docs(87-XX): restore v1.10 phase <n> for validation closure` (restore) → `test(87-XX): fill <n> validation gaps (<gap-count> tests)` → optional `fix(<phase-n>): <one-liner>` per impl bug → `docs(87-XX): approve <n>-VALIDATION.md (status: approved, nyquist_compliant: true)`. Each commit lands inside the same per-phase atomic group; commit subjects use the `87-XX` plan reference so they're greppable per plan and per v1.10 phase.
- **Wallclock baseline reference for D-06 guard:** Phase-86 PR-branch CI median = 23:00 (from 86-VERIFICATION.md). 5 % regression threshold = ~01:09 added; any single phase pushing past ~01:09 added on top of its own additions triggers the tidy-up cycle.
- **VALIDATION.md frontmatter target shape** (replaces draft frontmatter):
  ```yaml
  phase: <n>
  slug: <restored-slug>
  status: approved
  nyquist_compliant: true
  approved_on: 2026-05-18  # ISO date of approval commit
  audit_method: retroactive  # vs. "during-execution" for in-flight phases
  ```
- **Closer scoreboard target** in `.planning/milestones/v1.10-MILESTONE-AUDIT.md`:
  ```yaml
  nyquist:
    compliant_phases: 9
    partial_phases: 0
    missing_phases: 0
    overall: compliant
  ```

</specifics>

<deferred>
## Deferred Ideas

- **Non-trivial impl-bug fixes** — if the auditor finds a bug whose fix exceeds ~50 LOC or spans multiple files/modules, the orchestrator pauses and offers the user a choice: (a) carve out into a v1.12 backlog item with `gsd-capture`, (b) accept as risk and document in `<n>-VALIDATION.md` Manual-Only section, (c) expand Phase 87 scope and execute the fix. Default action without user response: option (a) — capture and defer.
- **Per-phase `@DirtiesContext` audit** — Phase 86 covered the 16 current usages, but if any gap test would naturally need a `@DirtiesContext`, the audit notes it as a v1.12 follow-up (`PERF-FUTURE-02` candidate) rather than adding the annotation back.
- **MariaDB-only gap tests** — if a gap explicitly requires MariaDB semantics that H2 cannot reproduce (rare; the `mariadb-migration-smoke.yml` workflow is the only place these live today), they go into the existing Testcontainers infrastructure rather than a new MariaDB workflow.
- **VALIDATION.md template evolution** — if Phase 87 surfaces a strictly better post-hoc shape, the template at `$HOME/.claude/get-shit-done/templates/VALIDATION.md` should be updated **after** Phase 87 ships, not during — out of scope.

</deferred>

---

*Phase: 87-Nyquist VALIDATION Closure*
*Context gathered: 2026-05-18*
