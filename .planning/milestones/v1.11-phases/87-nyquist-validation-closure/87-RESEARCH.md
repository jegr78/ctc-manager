# Phase 87: Nyquist VALIDATION Closure - Research

**Researched:** 2026-05-18
**Domain:** Meta-phase — orchestrated retroactive Nyquist validation across 8 archived v1.10 phases
**Confidence:** HIGH (mechanical workflow; all source artefacts inspected directly in `60f5f915^`)

## Summary

Phase 87 is a **meta-phase**. It does not implement a feature; it runs `/gsd:validate-phase` eight times against archived v1.10 phases (71, 72, 73, 74, 75, 76, 78, 79) to bring each phase's `VALIDATION.md` to `status: approved` + `nyquist_compliant: true`. The work per v1.10 phase is purely orchestration: (1) restore artefacts from git history (`60f5f915^`) into `.planning/milestones/v1.10-phases/<n>-<slug>/`, (2) invoke the `/gsd:validate-phase` workflow, (3) commit auditor-generated gap-coverage tests + any impl-bug fixes, (4) update VALIDATION frontmatter, (5) atomic per-phase commit group. Plan 87-08 also runs the milestone closer (REQUIREMENTS / STATE / v1.10-MILESTONE-AUDIT updates).

All 8 phases were inspected directly in `git show 60f5f915^:...`. File inventories are complete (no missing artefacts). The auditor will likely classify most v1.10 requirements as COVERED — existing IT/E2E coverage is dense across the backup/import surface (`BackupRoundTripIT`, `BackupImportRollbackIT`, `BackupImportMariaDbSmokeIT`, `ImportConcurrentLockIT`, etc.). The realistic gap profile across 8 phases is **~10-20 total gap tests** (estimated mean 1.5-2.5 per phase), heavily weighted toward phase 71 (TemplateRenderingSmokeIT covers PLAT-06 but PLAT-04/05/07 may need targeted asserts) and phase 78 (no test directory at all — Docker Dockerfile pin is verified by CI workflow, not Java tests). Phases 72-76 + 79 are the lightest because each already has a draft VALIDATION mapping requirements to tests.

**Primary recommendation:** Execute strictly serially (one plan per v1.10 phase, in the order 87-01..87-08) with the auditor invoked via `/gsd:validate-phase` per restored directory. **Do NOT batch the restore step across all 8 phases up front** — restore each phase as the first task of its own plan so each plan is self-contained and atomic per VAL-03. Trust the auditor's COVERED verdict; budget 1-3 gap tests per phase (rare outliers excepted); pause for any non-trivial impl-bug fix per CONTEXT D-08.

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 through D-14, verbatim)

- **D-01:** All v1.10 phase artefacts are restored to `.planning/milestones/v1.10-phases/<n>-<slug>/` — the established archive convention used by v1.0, v1.1, v1.2, v1.3, v1.5, v1.6, v1.8, v1.9. This becomes the permanent home for v1.10 phase artefacts (parallel to `.planning/milestones/v1.10-ROADMAP.md`, `v1.10-REQUIREMENTS.md`, `v1.10-MILESTONE-AUDIT.md` already at `.planning/milestones/`). The active `.planning/phases/` directory stays reserved for the in-flight v1.11 phases (80-87).
- **D-02:** Minimal restore scope per phase — only the files `/gsd:validate-phase` actually consumes: every `<n>-XX-PLAN.md`, every `<n>-XX-SUMMARY.md`, `<n>-CONTEXT.md`, `<n>-VERIFICATION.md`, optionally `<n>-RESEARCH.md` (only if it carries decisions not already in CONTEXT), plus the existing `<n>-VALIDATION.md` draft (for 72-76 + 79). PATTERNS / REVIEW / DISCUSSION-LOG / HUMAN-UAT files stay archived only in git history.
- **D-03:** Restore source is `git show 60f5f915^:<path>` (parent of the deletion commit). The exact slugs from git history are documented in CONTEXT.md and verified in §Restore Mechanics below.
- **D-04:** Restored slugs are kept verbatim — no shortening, no re-slug. (See §Risks for clarifying note on archive slug pattern vs. deletion-commit slug.)
- **D-05:** Aggressive gap-filling. Every auditor-identified gap (COVERED / PARTIAL / MISSING) gets a test, no severity filtering, no selective skipping. Manual-Only reserved for genuinely non-automatable behavior.
- **D-06:** No per-phase wallclock cap. Final closer step measures `./mvnw verify -Pe2e` wallclock vs Phase-86 CI median (23:00). >5% regression triggers tidy-up cycle.
- **D-07:** JaCoCo: 82% gate-only. Movement between v1.10-close 87.80% and pom.xml 82% gate is acceptable. No additional "must stay ≥ 87.80%" guard.
- **D-08:** Implementation bugs the auditor finds are fixed inside Phase 87. Each fix lands in the same per-phase atomic commit group via a separate `fix(<phase-n>): …` commit. Non-trivial fixes (>~50 LOC, multi-file, migration-bearing) pause for user-decision.
- **D-09:** 8 plans, 1 per v1.10 phase. Numbering: 87-01 = Phase 71, 87-02 = Phase 72, ..., 87-07 = Phase 78, 87-08 = Phase 79. (Phase 77 is out of Phase 87 scope — already compliant.)
- **D-10:** Strict numeric sequence, single wave. No parallel execution, no wave splits.
- **D-11:** Closer work folded into 87-08: clears Nyquist row from STATE.md Deferred Items, flips VAL-01..VAL-04 to `[x]` in REQUIREMENTS.md, updates v1.10-MILESTONE-AUDIT.md scoreboard.
- **D-12:** Hybrid shape — pre-execution template, retroactively filled. All 8 VALIDATION.md files use `$HOME/.claude/get-shit-done/templates/VALIDATION.md` structure.
- **D-13:** `status: approved` definition: every Per-Task Verification Map row names a real test file path, every row has status `✅ green` with CI evidence (run-id in commit message or VALIDATION.md), every Sign-Off checkbox checked, frontmatter has `nyquist_compliant: true` AND `status: approved`.
- **D-14:** Phase 87 work continues on the existing milestone branch `gsd/v1.11-tooling-and-cleanup`. No separate Phase 87 branch.

### Claude's Discretion

- Exact slugs for restored phase directories (verify against git history; if a slug is truncated due to filesystem limits, preserve the truncation form already used in the original deletion commit).
- Exact phrasing of `<n>-VALIDATION.md` Sign-Off checkboxes (template wording may be lightly adapted for retroactive language: "All tasks have <verify> or post-hoc evidence" instead of "All tasks have <verify> or Wave 0 dependencies").
- Test-class naming for generated gap-coverage tests — follow existing Phase 71-79 conventions seen in restored files; place them in the same package as the implementation they cover, with `@Tag("integration")` for `*IT.java` and `@Tag("e2e")` only when Playwright is involved.
- Whether the Plan 87-08 closing commit set is one fat squash or 3-4 small commits — planner picks based on diff size.

### Deferred Ideas (OUT OF SCOPE)

- Non-trivial impl-bug fixes (>~50 LOC, multi-file). Default action without user response: option (a) — capture and defer via `gsd-capture`.
- Per-phase `@DirtiesContext` audit (already covered by Phase 86; new `@DirtiesContext` becomes v1.12 PERF-FUTURE-02 candidate).
- MariaDB-only gap tests — go into existing Testcontainers infrastructure, not new MariaDB workflow.
- VALIDATION.md template evolution — out of scope; happens after Phase 87 ships, not during.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| VAL-01 | Approved `*-VALIDATION.md` files exist for phases 72, 73, 74, 75, 76, and 79 with status `approved` | §Restore Mechanics, §Q3 Gap Profile, §Sign-Off Mechanics — exact frontmatter shape locked, drafts inventoried |
| VAL-02 | New `*-VALIDATION.md` files are created for phases 71 (SB 4.0.6) and 78 (Docker noble pin), reaching `approved` status | §Template (Phase 71 + 78 sections), §Q3 — gap profile estimated, target file paths defined |
| VAL-03 | `/gsd:validate-phase` is executed against each of the 8 phases (71-76, 78, 79) and gap-coverage tests are committed atomically per phase | §Workflow Internals + §Commit Strategy — exact commit message convention captured per phase |
| VAL-04 | STATE.md "Deferred Items" no longer lists Nyquist VALIDATION items at v1.11 close | §Closer Mechanics — exact STATE/REQUIREMENTS/AUDIT diff blocks captured |

## Project Constraints (from CLAUDE.md)

These directives constrain Phase 87 even though it is a meta-phase:

| Directive | Impact on Phase 87 |
|-----------|-------------------|
| **Test Coverage:** Minimum 82% line coverage. | New gap tests should not lower coverage (D-07 says 82% gate-only; movement between 87.80% and 82% acceptable). |
| **Flyway:** No V1 migration changes; only V2+. | N/A for Phase 87 — no DB schema work. |
| **Profiles:** Auth only for prod/docker; dev/local without auth. | Gap tests targeting `BackupController` should use `@ActiveProfiles("dev")` unless the gap is specifically a security/prod test. |
| **OSIV:** Remains enabled — only `@EntityGraph`. | Gap tests for export aggregates must use `findAllForBackup()` repository methods (Phase 73 D-03). |
| **Backward Compatibility:** No breaking changes to URLs/endpoints. | Phase 87 introduces no endpoints. |
| **Playwright:** compile-scope. | Phase 87 may add `@Tag("e2e")` Playwright tests for UI-visible gaps; same compile-scope dependency, no profile change. |
| **Tag Tests by Category (`@Tag`):** Every new test class MUST be tagged. | Every auditor-generated test class gets `@Tag("integration")` for `*IT.java` or `@Tag("e2e")` for `org.ctc.e2e.*`. Plain unit tests stay untagged. |
| **Isolate Test Data Completely:** E2E test data uses test prefix. | Auditor-generated E2E tests, if any, use `T-`/`Test_` prefixes. |
| **RaceLineup is Source of Truth:** | N/A for Phase 87. |
| **Don't Modify Flyway Migrations.** | N/A — Phase 87 adds no migrations. |
| **Subagent Rules: never haiku, name active branch, branch-protection in every prompt.** | Every `/gsd:validate-phase` invocation must include "Branch: `gsd/v1.11-tooling-and-cleanup`; no `git stash`/`git checkout`/`git reset`/branch switching." Auditor uses `gsd-nyquist-auditor` model resolution (NOT haiku); orchestrator runs post-dispatch `git branch --show-current` + `git log --oneline -3` + `git diff --stat` per `feedback_subagent_stability`. |
| **Atomic Tasks.** | Per CONTEXT D-09: each plan is fully self-contained (restore → audit → fill gaps → fix bugs → approve). |
| **Flaky-Test-Vertagung verboten.** Pre-merge green tests that fail post-Phase-87 are regressions, not "flaky" — investigate ursache, never out-of-scope. | Auditor's `@Tag("flaky")` quarantine reserved for genuinely flaky tests with monthly review (CD-05); not used to dismiss gap-test failures. |
| **PR-Beschreibung pflegen.** | After each plan 87-XX closes, the milestone PR body is updated via `gh pr edit` per `feedback_pr_description_update`. |

## Architectural Responsibility Map

Phase 87 is meta — no production-tier capability is added. The "tiers" map to **planning artefact tiers** instead:

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Restoration of v1.10 phase artefacts from git history | Filesystem (planning archive) | — | `git show 60f5f915^:<path>` is read-only against history; write target is `.planning/milestones/v1.10-phases/` |
| Gap analysis per v1.10 requirement | `gsd-nyquist-auditor` subagent | `/gsd:validate-phase` workflow | Auditor classifies COVERED / PARTIAL / MISSING; workflow orchestrates the gap-fill loop |
| Gap-coverage test generation | Java test suite (`src/test/java/org/ctc/{backup,admin,e2e,domain}/`) | — | Auditor writes tests adhering to project Tag conventions; tests join the existing IT/E2E suite |
| Impl-bug fixes (per D-08) | Java production code (`src/main/java/`) | — | Orchestrator-decision (NOT auditor — auditor is Read-only against impl); separate `fix(<phase-n>):` commits |
| VALIDATION.md frontmatter sign-off | `.planning/milestones/v1.10-phases/<n>-<slug>/<n>-VALIDATION.md` | — | Single-file edit per phase plan; frontmatter shape locked by D-13 |
| Closer (STATE / REQUIREMENTS / AUDIT updates) | `.planning/STATE.md`, `.planning/REQUIREMENTS.md`, `.planning/milestones/v1.10-MILESTONE-AUDIT.md` | — | Plan 87-08 tail; 3 files, 3 deltas |

## Restore Mechanics (Q1)

### Exact Restore Source per Phase

Source ref: `60f5f915^` (parent of deletion commit `60f5f915` = "chore(v1.11): clear v1.10 phase directories before new milestone"). Verified: `git ls-tree -r --name-only "60f5f915^" -- ".planning/phases/<slug>/"` returns the full file list intact at that ref.

### Per-Phase File Inventory (verified directly in `60f5f915^`)

**Note on slugs:** The slugs in `60f5f915^` are filesystem-truncated to fit OS path limits. The CONTEXT D-04 "verbatim" rule keeps them as-is. There is a separate question of whether the **destination archive directory under `.planning/milestones/v1.10-phases/`** should use the same truncated slugs or clean re-slugs matching the v1.9 archive pattern. See §Risks Q8.

#### Phase 71 — Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 + Build Guard

- **Slug in 60f5f915^:** `71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui` (truncated; final word would have been "build-guard")
- **PLANs:** 5/5 (`71-01-PLAN.md` … `71-05-PLAN.md`) [VERIFIED]
- **SUMMARYs:** 5/5 [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present (status: passed, must_haves_verified: 7/7, score: 100%) [VERIFIED]
- **RESEARCH.md:** **ABSENT** — Phase 71 had no formal RESEARCH.md in 60f5f915^ [VERIFIED]
- **VALIDATION.md:** **ABSENT** — confirms VAL-02 (Phase 71 is one of two missing) [VERIFIED]
- **Other artefacts NOT restored per D-02:** `71-DISCUSSION-LOG.md`, `71-PATTERNS.md`, `71-REVIEW.md`

#### Phase 72 — Backup Wire Contract

- **Slug in 60f5f915^:** `72-backup-wire-contract-schema-manifest-objectmapper-audit-log-` (truncated trailing dash)
- **PLANs:** 5/5 (`72-01-PLAN.md` … `72-05-PLAN.md`) [VERIFIED]
- **SUMMARYs:** 5/5 [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present [VERIFIED]
- **RESEARCH.md:** present (1064 lines) — restore per D-02 if it carries decisions beyond CONTEXT
- **VALIDATION.md:** present (status: draft, nyquist_compliant: false, 86 lines) — confirmed draft per CONTEXT [VERIFIED]
- **Other NOT restored:** `72-DISCUSSION-LOG.md`, `72-PATTERNS.md`

#### Phase 73 — Backup Export Streaming ZIP

- **Slug in 60f5f915^:** `73-backup-export-jackson-mixins-streaming-zip-endpoint`
- **PLANs:** 4/4 (`73-01-PLAN.md` … `73-04-PLAN.md`) [VERIFIED]
- **SUMMARYs:** 4/4 [VERIFIED]
- **CONTEXT.md:** **ABSENT** — Phase 73 had no `73-CONTEXT.md` in 60f5f915^ [VERIFIED — phase relied on `73-RESEARCH.md` + `73-UI-SPEC.md` for context]
- **VERIFICATION.md:** present (195 lines) [VERIFIED]
- **RESEARCH.md:** present (1071 lines) — restore mandatory for Phase 73 since no CONTEXT.md exists
- **VALIDATION.md:** present (status: draft) [VERIFIED]
- **Other NOT restored:** `73-AUTO-UAT.md`, `73-HUMAN-UAT.md`, `73-PATTERNS.md`, `73-REVIEW.md`, `73-REVIEW-FIX.md`, `73-UI-SPEC.md`

#### Phase 74 — Backup Import Preview + ZIP Hardening + Multipart Config

- **Slug in 60f5f915^:** `74-backup-import-preview-zip-hardening-multipart-config-schema-` (truncated trailing dash)
- **PLANs:** 10/10 — **but filenames are `01-PLAN.md` through `10-PLAN.md` WITHOUT the `74-` prefix** [VERIFIED — divergence from other phases]
- **SUMMARYs:** 10/10 — same `01-SUMMARY.md` pattern [VERIFIED]
- **CONTEXT.md:** present (`74-CONTEXT.md`) [VERIFIED]
- **VERIFICATION.md:** present (192 lines) [VERIFIED]
- **RESEARCH.md:** present (1306 lines) — restore per D-02
- **VALIDATION.md:** present (status: draft, 102 lines) [VERIFIED]
- **Other NOT restored:** `74-DISCUSSION-LOG.md`, `74-PATTERNS.md`, `74-PLAN-OUTLINE.md`, `74-UI-SPEC.md`

**Planner note:** Phase 74's plan filename pattern is `01-PLAN.md` not `74-01-PLAN.md`. Per D-04 "verbatim", restore them as-is. The `/gsd:validate-phase` workflow glob is `${PHASE_DIR}/*-PLAN.md` (Step 2a) — it does not require the `<n>-` prefix, so this works without modification.

#### Phase 75 — Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT

- **Slug in 60f5f915^:** `75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat`
- **PLANs:** 10/10 (`75-01-PLAN.md` … `75-10-PLAN.md`) [VERIFIED]
- **SUMMARYs:** 10/10 [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present [VERIFIED]
- **RESEARCH.md:** present — restore per D-02
- **VALIDATION.md:** present (status: draft) [VERIFIED]
- **Other NOT restored:** `75-DISCUSSION-LOG.md`, `75-HUMAN-UAT.md`, `75-PATTERNS.md`, `75-REVIEW.md`, `75-REVIEW-FIX.md`

#### Phase 76 — Operational Hardening — Lock + Banner + Auto-Backup

- **Slug in 60f5f915^:** `76-operational-hardening-import-lock-read-only-banner-auto-back` (truncated; final word would have been "backup")
- **PLANs:** 4/4 [VERIFIED]
- **SUMMARYs:** 4/4 [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present [VERIFIED]
- **RESEARCH.md:** present — restore per D-02
- **VALIDATION.md:** present (status: draft) [VERIFIED]
- **Other NOT restored:** `76-AUTO-UAT.md`, `76-DISCUSSION-LOG.md`, `76-PATTERNS.md`, `76-REVIEW.md`

#### Phase 78 — Docker Release Image Fix — Pin to Noble

- **Slug in 60f5f915^:** `78-docker-release-image-fix` (**short slug — NOT truncated**) — CONTEXT.md D-03 hedged "slug to be confirmed from git history during plan execution"; **confirmed: `78-docker-release-image-fix`** [VERIFIED]
- **PLANs:** 3/3 (`78-01-PLAN.md` … `78-03-PLAN.md`) [VERIFIED]
- **SUMMARYs:** 3/3 [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present [VERIFIED]
- **RESEARCH.md:** **ABSENT** — Phase 78 had no formal RESEARCH.md (reactive surgical phase) [VERIFIED]
- **VALIDATION.md:** **ABSENT** — confirms VAL-02 (Phase 78 is one of two missing) [VERIFIED]
- **Other NOT restored:** `.gitkeep`, `78-DISCUSSION-LOG.md`
- **Note:** No PATTERNS.md, REVIEW.md, UAT files at all — phase was 3-plan reactive surgical fix

#### Phase 79 — Code Cleanup + Test Perf — v1.10 Milestone Closer

- **Slug in 60f5f915^:** `79-code-cleanup-test-performance-optimization-v1-10-milestone-c` (truncated; final word would have been "closer")
- **PLANs:** 14 PLAN.md files with **descriptive filenames** [VERIFIED]:
  - `79-01-baseline-and-independence-audit-PLAN.md`
  - `79-02a-cleanup-leaf-admin-controller-backup-leaves-PLAN.md` … `79-02h-cleanup-domain-service-exception-repository-model-PLAN.md` (a-h = 8 files)
  - `79-03-test-perf-parallelization-PLAN.md`
  - `79-04-build-config-cleanup-PLAN.md`
  - `79-05-test-invocation-discipline-doc-PLAN.md`
  - `79-06-plan-summary-frontmatter-sweep-PLAN.md`
  - `79-07-final-wallclock-and-jacoco-verify-PLAN.md`
  - `79-08-milestone-audit-PLAN.md`
  - `79-09-milestone-complete-and-pr-PLAN.md`
- **SUMMARYs:** 14/14 with matching `79-01-SUMMARY.md` ... `79-09-SUMMARY.md` (NO descriptive suffix in SUMMARY names) [VERIFIED]
- **CONTEXT.md:** present [VERIFIED]
- **VERIFICATION.md:** present [VERIFIED]
- **RESEARCH.md:** present — restore per D-02
- **VALIDATION.md:** present (status: draft) [VERIFIED]
- **Other NOT restored:** `79-AUTO-UAT.md`, `79-DISCUSSION-LOG.md`, `79-INDEPENDENCE-AUDIT.md`

**Planner note:** Phase 79's PLAN filenames carry descriptive suffixes. The `/gsd:validate-phase` workflow glob `${PHASE_DIR}/*-PLAN.md` still matches these correctly. Keep filenames verbatim per D-04.

### Restore Commit Convention

Per CONTEXT.md specifics + verified naming consistency:

```
docs(87-XX): restore v1.10 phase <n> for validation closure
```

Example: `docs(87-03): restore v1.10 phase 73 for validation closure`

The restore is a single git mv-equivalent: 5-20 file additions under `.planning/milestones/v1.10-phases/<n>-<slug>/`. The `gsd-sdk query commit "..."` form works (see Phase 86 sample). Per CLAUDE.md "Conventional Commits": `docs:` is the right verb-class.

### Restore Command Snippet (planner reference)

```bash
PHASE_NUM=72  # adjust per plan
SLUG="72-backup-wire-contract-schema-manifest-objectmapper-audit-log-"
DEST=".planning/milestones/v1.10-phases/${SLUG}"
mkdir -p "${DEST}"

# Mandatory files (PLANs + SUMMARYs + CONTEXT + VERIFICATION + draft VALIDATION + optional RESEARCH)
for FILE in $(git ls-tree -r --name-only "60f5f915^" -- ".planning/phases/${SLUG}/" \
    | grep -E '(PLAN\.md|SUMMARY\.md|CONTEXT\.md|VERIFICATION\.md|VALIDATION\.md|RESEARCH\.md)$'); do
  TARGET="${DEST}/$(basename "${FILE}")"
  git show "60f5f915^:${FILE}" > "${TARGET}"
done

git add "${DEST}"
git commit -m "docs(87-02): restore v1.10 phase ${PHASE_NUM} for validation closure"
```

Per phase, this restore commit is the FIRST commit of the per-phase atomic group.

## `/gsd:validate-phase` Workflow Internals (Q2)

### Workflow Walkthrough (`$HOME/.claude/get-shit-done/workflows/validate-phase.md`)

**Step 0 — Initialize:**
- `gsd-sdk query init.phase-op "${PHASE_ARG}"` loads init JSON (phase_dir, phase_number, phase_slug, padded_phase)
- `gsd-sdk query resolve-model gsd-nyquist-auditor --raw` → auditor model (per CLAUDE.md Subagent Rules: NEVER haiku)
- `gsd-sdk query config-get workflow.nyquist_validation --raw` → must be `true` (`.planning/config.json` confirms `"nyquist_validation": true`)

**Step 1 — Detect Input State:**
- `VALIDATION_FILE=$(ls "${PHASE_DIR}"/*-VALIDATION.md 2>/dev/null | head -1)`
- `SUMMARY_FILES=$(ls "${PHASE_DIR}"/*-SUMMARY.md 2>/dev/null)`
- **State A** (`VALIDATION_FILE` non-empty): Audit existing — applies to **phases 72-76 + 79** (6 of 8)
- **State B** (`VALIDATION_FILE` empty, `SUMMARY_FILES` non-empty): Reconstruct from artifacts — applies to **phases 71 + 78** (2 of 8)
- **State C:** No SUMMARY files → exits with "Phase {N} not executed". Restore guarantees we never hit State C.

**Step 2 — Discovery:**
- 2a: Reads all PLAN.md + SUMMARY.md files. Extracts task lists, requirement IDs, key-files changed, verify blocks.
- 2b: Builds Requirement-to-Task Map: `{ task_id, plan_id, wave, requirement_ids, has_automated_command }`.
- 2c: Test infrastructure detection:
  - State A: parse from existing VALIDATION.md Test Infrastructure table.
  - State B: filesystem scan (`find . -name "pytest.ini" -o ...`) — for our Java/Maven project, the scan picks up `pom.xml` + `src/test/java/**/*Test.java` and `*IT.java`.
- 2d: Cross-references each requirement to existing tests by filename, imports, descriptions.

**Step 3 — Gap Analysis:** Classifies each requirement as COVERED / PARTIAL / MISSING. If no gaps → skip to Step 6, set `nyquist_compliant: true`.

**Step 4 — Present Gap Plan:** Calls AskUserQuestion with gap table. **Critical interaction for Phase 87:** `workflow.text_mode` in `.planning/config.json` is **`false`**, so AskUserQuestion is used. **However**, `mode: "yolo"` is set in config. The orchestrator should pre-select "Fix all gaps" automatically per CONTEXT D-05 (aggressive gap-filling). If AskUserQuestion blocks the run, the planner should add a `--auto` or equivalent flag or pre-set `text_mode: true` for the duration of Phase 87. **Defer to orchestrator runtime to confirm — could be a landmine. See §Risks Q8.**

**Step 5 — Spawn `gsd-nyquist-auditor`:** Subagent invoked via `Agent()`. Includes `<files_to_read>` (PLAN, SUMMARY, impl files, VALIDATION.md), `<gaps>`, `<test_infrastructure>`, `<constraints>` (impl files read-only, max 3 debug iterations, escalate impl bugs). **Important:** the workflow contains an `ORCHESTRATOR RULE — CODEX RUNTIME` directive: after spawning the auditor, the orchestrator must STOP working on this task until the auditor returns. The auditor returns one of `## GAPS FILLED` / `## PARTIAL` / `## ESCALATE`.

**Step 6 — Generate/Update VALIDATION.md:**
- State B (Phases 71, 78): create from template at `$HOME/.claude/get-shit-done/templates/VALIDATION.md`. Fill frontmatter, Test Infrastructure, Per-Task Map, Manual-Only, Sign-Off. Write to `${PHASE_DIR}/${PADDED_PHASE}-VALIDATION.md`. **`${PADDED_PHASE}` for phase 71 = `71`, for phase 78 = `78`** (zero-padding for phases ≥10 is identity).
- State A (Phases 72-76, 79): update Per-Task Map statuses, add escalated to Manual-Only, update frontmatter, append "Validation Audit {date}" block with gap counts (Gaps found / Resolved / Escalated).

**Step 7 — Commit:**
- `git add {test_files}` + `git commit -m "test(phase-${PHASE}): add Nyquist validation tests"`
- `gsd-sdk query commit "docs(phase-${PHASE}): add/update validation strategy"`
- **Question for VAL-03 atomic-per-phase:** the workflow commits 2 commits per invocation (one for tests, one for docs). Phase 87's per-plan atomic group expects these PLUS the restore commit + any `fix(<phase-n>):` commit. **Total per plan: 3-4 commits**, all on the milestone branch. The "atomic" framing in VAL-03 means "no in-between half-approved state" (D-13), not literally one commit — see §Commit Strategy below.

**Step 8 — Results + Routing:** Prints `GSD > PHASE {N} IS NYQUIST-COMPLIANT` (or PARTIAL) and the next-step suggestion. Phase 87's orchestrator captures the status, then proceeds to the next plan.

### `gsd-nyquist-auditor` Contract

**Inputs (via `<files_to_read>`, `<gaps>`, `<test_infrastructure>`, `<constraints>`):**
- PLANs + SUMMARYs from the phase under audit (restored from `60f5f915^`)
- Implementation files (read-only — auditor MUST NOT modify)
- Existing draft VALIDATION.md (State A) or empty/none (State B)
- Test infrastructure metadata (framework = JUnit 5; runner = `./mvnw verify -Pe2e`; project root config = pom.xml)
- Project skills via `.claude/skills/` discovery (loads `.planning/codebase/TESTING.md` patterns)

**Adversarial stance (codified in the auditor.md):** FORCE — assume every gap is uncovered until a passing test proves the requirement is satisfied. Write tests that can fail. Common failure modes (trivially-passing tests, easy-edge-only, "test created = gap filled", SKIPping rather than escalating, weakening assertions to make tests pass) are explicitly forbidden.

**Outputs (one of):**

1. **`## GAPS FILLED`** — all gaps resolved with passing tests
   - `### Tests Created` table: file path, type (unit/integration/smoke), command
   - `### Verification Map Updates` table: task_id, requirement, command, status (always `green` here)
   - `### Files for Commit` — list of test file paths

2. **`## PARTIAL`** — some gaps resolved, others escalated
   - `### Resolved` table (same shape as GAPS FILLED)
   - `### Escalated` table: task_id, requirement, reason, iterations (X/3)
   - `### Files for Commit` — resolved-gap test paths only

3. **`## ESCALATE`** — zero gaps resolved (auditor cannot fill any)
   - `### Details` table: task_id, requirement, reason, iterations
   - `### Recommendations` — manual test instructions or impl-fix needed

**Critical for VAL-03 atomic-per-phase:** the workflow Step 7 commits per workflow invocation, not per phase. The Phase-87 orchestrator runs the workflow ONCE per v1.10 phase plan. If the workflow returns ESCALATE, the orchestrator decides per CONTEXT D-08:
- Non-trivial impl bug (>~50 LOC) → pause for user-decision (per D-08 + Deferred Idea defaulting to `gsd-capture`)
- Trivial impl bug → orchestrator manually applies fix in a separate `fix(<phase-n>):` commit, then re-runs `/gsd:validate-phase` for that phase to re-verify

### Workflow Gates Honored

| Gate | Source | Phase 87 effect |
|------|--------|----------------|
| `workflow.nyquist_validation` | `.planning/config.json` | `true` — workflow runs |
| `workflow.text_mode` | `.planning/config.json` | `false` — AskUserQuestion is used at Step 4 (see Q8 landmine) |
| `mode: "yolo"` | `.planning/config.json` | Orchestrator can pre-select "Fix all gaps" per D-05 |
| `response_language` | not set in config; CLAUDE.md says German for user communication, English for docs/code | VALIDATION.md content stays English (it's documentation per CLAUDE.md "Language") |
| Subagent model | `gsd-sdk query resolve-model gsd-nyquist-auditor --raw` | Must NOT be haiku (CLAUDE.md Subagent Rules + `feedback_subagent_stability`) |

## Predicted Gap Profile per v1.10 Phase (Q3) — The Most Valuable Section

For each v1.10 phase, lists existing test classes that satisfy each REQ-ID and predicts auditor classification (COVERED / PARTIAL / MISSING). Estimates are **conservative** — the auditor's actual gap count may be smaller because existing tests often cover multiple requirements.

### Phase 71 (PLAT-01..PLAT-07) — Plan 87-01

| REQ-ID | Requirement summary | Existing tests | Predicted status | Likely gap |
|--------|--------------------|----------------|------------------|------------|
| PLAT-01 | pom.xml SB 4.0.5 → 4.0.6 | None — version is a pom.xml constant | PARTIAL | Tiny guard test: parse `pom.xml`, assert `spring-boot-starter-parent` version starts with `4.0.6`. **OR** rely on `TemplateRenderingSmokeIT` as transitive evidence (workflow boots SB 4.0.6 context). Auditor likely accepts PLAT-01 as covered by `TemplateRenderingSmokeIT` smoke. |
| PLAT-02 | `<dependencyManagement>` Thymeleaf 3.1.5 pin | None directly | PARTIAL | Tiny guard test asserting `mvn dependency:tree` shows Thymeleaf at 3.1.5. **OR** rely on smoke test boot. Auditor likely picks one and approves. |
| PLAT-03 | 3 known templates use controller `pageTitle` | `TemplateRenderingSmokeIT` (64 dynamic routes, GET all admin) | COVERED | None — IT verifies HTTP 200 + no TemplateProcessingException |
| PLAT-04 | Audit of all ~80 templates, fixed Fragment-ternary pattern | `TemplateRenderingSmokeIT` covers admin; site routes need separate coverage | PARTIAL | Likely auditor wants either (a) extend smoke IT to cover site templates dynamically, OR (b) a doc-only grep-test that asserts zero `th:(replace\|insert\|include)=".*\(.*\$\{.*\}.*\)"` patterns remain. The PLAT-07 build-guard already does (b) at the validate-phase, so auditor accepts PLAT-04 transitively. |
| PLAT-05 | `./mvnw verify -Pe2e` BUILD SUCCESS + JaCoCo ≥ 82% | CI workflow `ci.yml` + JaCoCo plugin in pom.xml | COVERED | None — this is by definition green at phase close |
| PLAT-06 | `TemplateRenderingSmokeIT` exists, dynamic over RequestMappingHandlerMapping | `src/test/java/org/ctc/admin/controller/integration/TemplateRenderingSmokeIT.java` [VERIFIED present] | COVERED | None |
| PLAT-07 | `exec-maven-plugin` grep-gate bound to validate phase | pom.xml `template-fragment-call-guard` execution + `[PLAT-07 build-guard]` output | PARTIAL | Auditor may want an IT that invokes `./mvnw -q validate` and asserts exit 0 + no offender pattern in source. **Rare** — more likely auditor accepts the existing structural setup as covered. |
| **Estimated gap count** | | | | **0-2 tests** (most likely a pom.xml-version guard + a multi-template grep guard if auditor is strict) |

### Phase 72 (SCHEMA-01..04, IMPORT-08) — Plan 87-02

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| SCHEMA-01 | `BackupSchemaGuardTest` (Phase 82) + `BackupSchemaTopologyIT` + `BackupSchemaExclusionIT` | COVERED | None |
| SCHEMA-02 | `BackupManifestSerializationTest` | COVERED | None |
| SCHEMA-03 | `V7DataImportAuditMigrationIT` (`src/test/java/db/migration/`) + `DataImportAuditServiceTest` + `DataImportAuditSerializationTest` | COVERED | None |
| SCHEMA-04 | `BackupObjectMapperConfigIT` | COVERED | None |
| IMPORT-08 | `BackupSchemaExclusionIT` (asserts `data_import_audit` excluded by package filter) | COVERED | None |
| **Estimated gap count** | | | **0-1 tests** (this phase is exceptionally well-covered) |

### Phase 73 (EXPORT-01..06) — Plan 87-03

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| EXPORT-01 | `BackupControllerIT` + `BackupExportE2ETest` | COVERED | None |
| EXPORT-02 | `BackupExportE2ETest` (streaming ZIP download + filename) | COVERED | None — but auditor may want stricter ISO-Instant filename regex assertion |
| EXPORT-03 | `BackupRoundTripIT` (24-entity row-count parity) + `BackupUploadsMirrorIT` + manifest-first assertion | COVERED | None |
| EXPORT-04 | 24 MixIns + `BackupEntityAnnotationCleanlinessIT` (no annotation drift in org.ctc.domain.model) + per-MixIn tests (`DriverMixInTest`, `RaceMixInTest`, `RaceAttachmentMixInTest`, `SeasonMixInTest`, `TeamMixInTest`) | COVERED | None |
| EXPORT-05 | `BackupExportNoLazyInitIT` + `BackupRepositoryEntityGraphIT` | COVERED | None |
| EXPORT-06 | `BackupControllerSecurityIT` (CSRF nested @Nested prod test) | COVERED | None |
| **Estimated gap count** | | | **0-1 tests** (ISO-Instant filename regex if auditor is strict on EXPORT-02) |

### Phase 74 (IMPORT-01..04, SECU-01..04) — Plan 87-04

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| IMPORT-01 | `BackupImportServiceIT` + `BackupArchiveServiceReadIT` + `BackupArchiveServiceIT` + `BackupStagingCleanupIT` | COVERED | None |
| IMPORT-02 | `BackupImportSchemaMismatchIT` (HTTP 400 + Flash, DB unchanged) | COVERED | None |
| IMPORT-03 | `BackupImportPreviewTest` + `EntityRowCountTest` + `BackupImportConfirmFormValidationIT` | COVERED | None |
| IMPORT-04 | `BackupImportConfirmFormValidationTest` + `BackupImportConfirmFormValidationIT` (Pflicht-Checkbox) | COVERED | None |
| SECU-01 | `BackupImportZipSlipIT` + `PathTraversalGuardTest` | COVERED | None |
| SECU-02 | `BackupImportZipBombIT` + `BackupImportLimitsTest` + `LimitedInputStreamTest` | COVERED | None |
| SECU-03 | `BackupImportMultipartLimitIT` + `BackupArchiveExceptionTest` | COVERED | None |
| SECU-04 | `BackupImportControllerSecurityIT` covers the `BackupUploadExceptionHandler` `@ControllerAdvice` path | PARTIAL | Likely targeted: assert dedicated `@ControllerAdvice` is used (not `GlobalExceptionHandler`) — usually a structural test reflecting class on the advice. Auditor may generate a small Mockito-style test confirming `MaxUploadSizeExceededException` hits the right advice. |
| **Estimated gap count** | | | **0-2 tests** |

### Phase 75 (IMPORT-05..07, QUAL-03) — Plan 87-05

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| IMPORT-05 | `BackupImportExecuteIT` + `BackupImportRollbackIT` + 24 `EntityRestorer` tests + `BackupRoundTripIT` | COVERED | None |
| IMPORT-06 | `BackupArchiveExtractUploadsIT` + `BackupImportPostCommitIT` (post-commit upload-tree restore via stage-and-rename, 24h `uploads-old/` retention) | COVERED | None — but auditor may want explicit timestamp-directory assertion on `data/.import-backups/<ts>/uploads-old/` |
| IMPORT-07 | `DataImportAuditServiceTest` + `DataImportAuditSerializationTest` + audit-row assertions in `BackupImportRollbackIT` (success=false case) | COVERED | None |
| QUAL-03 | `BackupImportMariaDbSmokeIT` (Testcontainers Saison-2023 round-trip) + 75-HUMAN-UAT.md auto-executed 2026-05-14 10/10 PASS | COVERED | None — auditor MAY mark `Manual-Only` for the HUMAN-UAT visual diff items |
| **Estimated gap count** | | | **1-3 tests** (most likely a post-commit-listener idempotency test + timestamped-directory assertion + `Team.parentTeam` pre-step assertion) |

### Phase 76 (SECU-05..07) — Plan 87-06

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| SECU-05 | `ImportConcurrentLockIT` (2-thread IT with `BlockingRestoreFailureInjector` + 409 redirect) + `ImportLockServiceTest` (ReentrantLock semantics) | COVERED | None |
| SECU-06 | `ImportLockedPostRejectorIT` (POST rejection HTTP 503) + `ImportLockBannerAdviceIT` (yellow banner advice) | PARTIAL | **Likely highest-yield gap of the milestone:** Phase 76 has POST-rejection + banner ITs but may lack a "non-import POST under /admin/** is rejected with whitelist-on-equals, not startsWith" structural assertion. Auditor may want an IT that submits a path like `/admin/backups-fake` to verify it is NOT smuggled past the whitelist. Auditor may also want a `GET` non-rejection test (GET should pass during lock, only POST is rejected). |
| SECU-07 | `AutoBackupBeforeImportFailureIT` (auto-export fails → import aborted before DB write) + `AutoBackupBeforeImportPathIT` (Step 0.5 path correctness) | COVERED | None — but auditor may want catch-order assertion: `AutoBackupBeforeImportException` BEFORE `BackupImportException` (first-match-wins) |
| **Estimated gap count** | | | **2-4 tests** (this is the most likely "PARTIAL" phase) |

### Phase 78 (PLAT-CI-01, PLAT-CI-02) — Plan 87-07 (State B — no VALIDATION.md exists)

| REQ-ID | Existing tests | Predicted status | Likely gap |
|--------|----------------|------------------|------------|
| PLAT-CI-01 | `dockerfile-noble-pin-guard` job in `.github/workflows/ci.yml` (grep guard) — **not a Java test**, a CI workflow check | COVERED via CI | Auditor may classify as Manual-Only (CI workflow) OR want a `JUnit + Files.readString(Path.of("Dockerfile"))` guard test that parses Dockerfile FROM lines and asserts `-noble` suffix. **Likely outcome: auditor generates a small `DockerfilePinGuardTest` in `src/test/java/org/ctc/` to make it programmatically verifiable.** |
| PLAT-CI-02 | `docker-build` job in `ci.yml` (full `docker build .` on every PR + push) + `dockerfile-noble-pin-guard` job | COVERED via CI | Same shape — could remain Manual-Only or add a `DockerfileStructuralGuardTest` reading the workflow YAML. |
| **Estimated gap count** | | | **1-2 tests** (likely a `DockerfilePinGuardTest` reading Dockerfile + ci.yml) **AND** Phase 78 needs a brand-new VALIDATION.md created from template (State B). |

**Phase 78 special framing:** Phase 78 is **reactive surgical** (Dockerfile-pin only, no domain code). The Wave 0 Requirements section in the new VALIDATION.md will be annotated `"satisfied retroactively"` per CONTEXT D-12. The Test Infrastructure table will be light (no quick command — only `./mvnw verify -Pe2e` final gate); Manual-Only Verifications will dominate (post-merge release-workflow observation per Phase 78 SC#3).

### Phase 79 (D-01..D-20 decisions; no formal REQ-IDs) — Plan 87-08

Phase 79 is unusual because it has no REQ-IDs — its 20 decisions (D-01..D-20) act as acceptance criteria. The draft 79-VALIDATION.md seeds 14 decision-rows in its Per-Task Map (D-05 Wave 1, D-05 Wave 2, D-06, D-07, D-08, D-09..D-13 Schutzwortliste, D-14, D-16, D-18, D-19) and several entries are marked `Manual / scripted`.

| Decision | Existing tests | Predicted status | Likely gap |
|----------|----------------|------------------|------------|
| D-05 Wave 1 (test independence) | `./mvnw test -Dsurefire.runOrder=reversealphabetical` + 3 random seeds — **manual invocation, no test file** | Manual-Only | None — properly classified |
| D-05 Wave 2 (Surefire/Failsafe forkCount) | `./mvnw verify` (existing CI green) | COVERED via CI | None |
| D-06 (wallclock reduction) | `docs/test-performance.md` baseline tables | Manual-Only | None (wallclock not assertable in JUnit) |
| D-07 (ci.yml concurrency + flaky tag) | `actionlint .github/workflows/ci.yml` + `@Tag("flaky")` filter | PARTIAL → Manual-Only | Auditor likely accepts as Manual-Only |
| D-08 (TESTING.md doc) | grep TESTING.md for "Test Invocation Discipline" section | Manual-Only (doc grep) | None |
| D-09..D-13, D-20 (Schutzwortliste comment preservation) | `grep -rE "MariaDB\|H2\|JEP\|CVE..." src/` | Manual-Only (cleanup grep) | None |
| D-14 (audit-milestone clean) | `/gsd-audit-milestone v1.10` already ran 2026-05-15 → status passed | COVERED | None |
| D-16 (frontmatter sweep on phases 56/57/62/64) | grep phase SUMMARY frontmatter | Manual-Only | None |
| D-18 (JaCoCo ≥ 0.82) | pom.xml `jacoco-maven-plugin` check goal | COVERED via CI | None |
| D-19 (final phase gate green) | `./mvnw verify -Pe2e` exit 0 | COVERED via CI | None |
| **Estimated gap count** | | | **0-1 tests** (Phase 79 is overwhelmingly doc + CI-config — most gaps will resolve to Manual-Only) |

**Plus the closer work (D-11 of Phase-87 CONTEXT):** Plan 87-08 also performs the milestone-level updates (STATE.md, REQUIREMENTS.md, v1.10-MILESTONE-AUDIT.md) — see §Closer Mechanics. These updates are doc-only commits, not tests.

### Aggregate Gap Count Estimate

| Plan | v1.10 Phase | Estimated gaps |
|------|-------------|----------------|
| 87-01 | 71 | 0-2 |
| 87-02 | 72 | 0-1 |
| 87-03 | 73 | 0-1 |
| 87-04 | 74 | 0-2 |
| 87-05 | 75 | 1-3 |
| 87-06 | 76 | 2-4 |
| 87-07 | 78 | 1-2 |
| 87-08 | 79 | 0-1 |
| **Total** | | **4-16 gap tests across all 8 phases; realistic midpoint ~10** |

## Wallclock Baseline & Guard Mechanism (Q4)

**Current `./mvnw verify -Pe2e` CI baseline (post-Phase-86, on PR branch via D-17 equivalence):**
- **Median CI wallclock: 23:00** (1380 seconds) — recorded in `docs/test-performance.md` and `86-06-SUMMARY.md`
- **5-run sample (PR branch, workflow_dispatch):** 1380 / 1391 / 1363 / 1318 (dropped min) / 1422 (dropped max); median of middle 3 = 1380s
- **Variance:** 7.5% — within D-10 20% tolerance

**5% regression threshold per CONTEXT D-06:**
- 23:00 × 1.05 = 1380s × 1.05 = **1449s = 24:09**
- Maximum acceptable wallclock at Phase 87 close: **24:09**
- Headroom: ~69 seconds total across ~10 added gap tests = ~7 seconds per gap test on average

**Realistic upper-bound estimate:** 10 added gap tests at ~5 seconds each (typical Spring `@SpringBootTest` IT cold-start cost is amortized after the first; an additional IT in an already-loaded context adds ~1-3s) = ~50 seconds. **Well within the 69-second budget.** A bigger risk is a single new test that opens a new context (forces context reload) — that adds ~30s. Auditor-generated tests should reuse existing test classes / `@Tag("integration")` groups to avoid context proliferation.

**Guard mechanism instrumentation:** the planner should add a final task in plan 87-08 that:

1. Triggers a `workflow_dispatch` run on the milestone PR branch (Phase 86 PERF-05 path)
2. Captures Maven Total time from `gh run view <id> --log | grep "Total time:"`
3. Compares to baseline 23:00 (1380s)
4. If > 24:09 (5% regression): triggers tidy-up cycle per D-06 — review the most expensive added tests, downgrade them to `@DataJpaTest` (Phase 86 D-05 conversion pattern available) or move to Manual-Only

This is a **PR-branch CI gate** task, NOT a CI workflow file change. The existing `workflow_dispatch` trigger added by Phase 86 makes this single-task feasible. Total wallclock for the guard step: ~24 minutes (one CI run).

## Test Class Placement & Tag Conventions (Q5)

Per CLAUDE.md Architectural Principles (`Tag Tests by Category (@Tag)`) and `.planning/codebase/TESTING.md` §Test Categorization:

| v1.10 Phase | Conventional test-class location | Tag |
|------|----------------------------------|-----|
| 71 (templates, build guard) | `src/test/java/org/ctc/admin/controller/integration/` | `@Tag("integration")` for new ITs; untagged for pom.xml-version unit guard |
| 72 (schema, mixins, V7 migration) | `src/test/java/org/ctc/backup/schema/` + `src/test/java/db/migration/` | `@Tag("integration")` for `*IT.java`, untagged for `*Test.java` |
| 73 (export, repository) | `src/test/java/org/ctc/backup/service/` + `src/test/java/org/ctc/backup/serialization/` + `src/test/java/org/ctc/e2e/` | `@Tag("integration")` for ITs, `@Tag("e2e")` for E2E only |
| 74 (import preview, ZIP hardening) | `src/test/java/org/ctc/backup/service/` + `src/test/java/org/ctc/backup/dto/` + `src/test/java/org/ctc/backup/exception/` | `@Tag("integration")` for ITs |
| 75 (replace-all transaction, restorers) | `src/test/java/org/ctc/backup/service/` + `src/test/java/org/ctc/backup/restore/entity/` | `@Tag("integration")` for ITs |
| 76 (lock, banner, auto-backup) | `src/test/java/org/ctc/backup/it/` + `src/test/java/org/ctc/backup/lock/` | `@Tag("integration")` for ITs |
| 78 (Dockerfile pin) | `src/test/java/org/ctc/` (project-level) | untagged unit (`DockerfilePinGuardTest`) |
| 79 (cleanup) | various per package | untagged or `@Tag("integration")` per behavior |

**Naming conventions discovered in restored files:**
- `*IT.java` → Spring-context integration tests (Failsafe-routed via `@Tag("integration")`)
- `*Test.java` → unit tests (Surefire, untagged)
- `*E2ETest.java` → Playwright E2E (only in `org.ctc.e2e.*`, `@Tag("e2e")`)
- Helper classes (non-test injectors, fixtures): `*Injector.java`, `*Helper.java`

**Pitfall reminder per TESTING.md §"Why we moved away from filename routing (Phase 79 D-05)":** Surefire `<exclude>**/*IT.java</exclude>` does NOT filter `@Nested` inner classes — the tag is the source of truth. Auditor MUST tag every generated class.

## Sign-Off Mechanics for `status: approved` (Q6)

### Frontmatter Target Shape (per CONTEXT specifics)

```yaml
---
phase: <n>                    # e.g., 72
slug: <restored-slug>          # e.g., backup-wire-contract-schema-manifest-objectmapper-audit-log
status: approved              # was: draft (State A) or absent (State B)
nyquist_compliant: true       # was: false (State A) or absent (State B)
approved_on: 2026-05-18       # ISO date of approval commit
audit_method: retroactive     # vs. "during-execution" for in-flight phases
wave_0_complete: true         # was: false — flipped for retroactive: tests now exist
created: <original-date>      # preserve from draft if State A; else date of Phase-87 plan run
---
```

**Note on `wave_0_complete`:** the template uses this field. For retroactive runs (State A + B), the field flips from `false` to `true` because all referenced test files now exist on disk (`✅` not `❌ W0`).

**Note on `slug`:** the draft frontmatter (Phase 72 draft seen above) has `slug: backup-wire-contract-schema-manifest-objectmapper-audit-log` — note the **trailing dash removed** vs the filesystem slug. The planner should preserve the existing draft frontmatter's slug (it pre-existed at v1.10 time and reflects the human form). For State B (Phases 71, 78), use the truncated filesystem slug minus trailing punctuation per D-04 spirit.

### CI Evidence Convention

Per CONTEXT D-13: "every row has status `✅ green` with CI evidence (run-id citation in commit message or VALIDATION.md)". Two paths:

1. **Recommended:** Cite the latest passing `ci.yml` run-id in the VALIDATION.md "Validation Audit YYYY-MM-DD" appended block (per `validate-phase.md` Step 6 State A):

```markdown
## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | {N} |
| Resolved | {M} |
| Escalated | {K} |

**CI evidence:** Run-id `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup`, conclusion: success, e2e step wallclock: 23:00). Full suite: `./mvnw verify -Pe2e` → BUILD SUCCESS. JaCoCo: ≥ 82% (gate held).
```

2. **Fallback:** Include the run-id in the `docs(87-XX): approve <n>-VALIDATION.md ...` commit message body.

Recommend BOTH — Audit block in the file is more discoverable; commit message body is for git-archaeology grepping.

### Sign-Off Checklist Adapted for Retroactive Language

The template ships these 6 checkboxes:

> - [ ] All tasks have `<automated>` verify or Wave 0 dependencies
> - [ ] Sampling continuity: no 3 consecutive tasks without automated verify
> - [ ] Wave 0 covers all MISSING references
> - [ ] No watch-mode flags
> - [ ] Feedback latency < {N}s
> - [ ] `nyquist_compliant: true` set in frontmatter

**Retroactive adaptation (per Claude's Discretion #2 in CONTEXT):**

> - [x] All tasks have `<automated>` verify or **post-hoc evidence** (Wave 0 satisfied retroactively — all referenced test files now exist)
> - [x] Sampling continuity: no 3 consecutive tasks without automated verify (verified post-execution)
> - [x] Wave 0 covers all MISSING references **(retroactively — gap tests landed in Phase 87)**
> - [x] No watch-mode flags
> - [x] Feedback latency < {N}s (existing baseline; no new test class adds > 30s)
> - [x] `nyquist_compliant: true` set in frontmatter

**Approval line:**

```
**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-XX
```

## Closer Mechanics (Plan 87-08 Tail) (Q7)

### STATE.md "Deferred Items" Diff

Current STATE.md row to remove (line 58):

```
| tech_debt | Nyquist *-VALIDATION.md for 6 phases (72-76, 79) + creation for 71 + 78 | Phase 87 |
```

**Exact edit:** delete this single row. The surrounding table structure is preserved. Use the `Edit` tool with `old_string` = the full row (including leading and trailing pipe characters and any trailing newline) and `new_string` = empty.

Alternative: use `gsd-sdk query state.deferred-clear "<id>"` if such a verb exists — **not verified**; presume direct file edit per the existing CTC pattern (no special SDK verb in evidence).

### REQUIREMENTS.md Diff (VAL-01..VAL-04 + Traceability Table)

Current REQUIREMENTS.md `### Nyquist VALIDATION Closure (VAL)` block (lines 73-78):

```markdown
- [ ] **VAL-01**: Approved `*-VALIDATION.md` files exist for phases 72, 73, 74, 75, 76, and 79 with status `approved` in the frontmatter
- [ ] **VAL-02**: New `*-VALIDATION.md` files are created for phases 71 (Spring Boot 4.0.6 upgrade) and 78 (Docker noble pin), reaching `approved` status
- [ ] **VAL-03**: `/gsd:validate-phase` is executed against each of the 8 phases (71-76, 78, 79) and the produced gap-coverage tests are committed atomically per phase
- [ ] **VAL-04**: STATE.md "Deferred Items" no longer lists the Nyquist VALIDATION items at v1.11 close
```

**Target:** flip 4 leading `[ ]` to `[x]` (per CLAUDE.md `feedback_grep_all_usages` — use `grep -n "^- \[ \] \*\*VAL-"` to verify the exact line numbers before editing).

**Traceability table updates** (lines 160-163):

```
| VAL-01 | Phase 87 | Pending |   →   | VAL-01 | Phase 87 | Satisfied |
| VAL-02 | Phase 87 | Pending |   →   | VAL-02 | Phase 87 | Satisfied |
| VAL-03 | Phase 87 | Pending |   →   | VAL-03 | Phase 87 | Satisfied |
| VAL-04 | Phase 87 | Pending |   →   | VAL-04 | Phase 87 | Satisfied |
```

### v1.10-MILESTONE-AUDIT.md Diff (YAML Scoreboard)

Current YAML block (lines 12-16 of `.planning/milestones/v1.10-MILESTONE-AUDIT.md`):

```yaml
  nyquist:
    compliant_phases: 1
    partial_phases: 6
    missing_phases: 2
    overall: partial
```

**Target:**

```yaml
  nyquist:
    compliant_phases: 9
    partial_phases: 0
    missing_phases: 0
    overall: compliant
```

**Also update lines 52-63** (detailed `nyquist:` block):

```yaml
nyquist:
  compliant_phases: ["71", "72", "73", "74", "75", "76", "77", "78", "79"]
  partial_phases: []
  missing_phases: []
  overall: compliant
  detail: |
    All 9 v1.10 phases reached status: approved + nyquist_compliant: true
    via Phase 87 (Nyquist VALIDATION Closure) retroactive audit. Plans 87-01..87-08
    ran /gsd:validate-phase against the restored phase directories at
    .planning/milestones/v1.10-phases/<n>-<slug>/; gap-coverage tests landed
    in src/test/java/ as atomic per-phase commit groups (VAL-01..VAL-03).
    STATE.md Deferred Items Nyquist row cleared at v1.11 close (VAL-04).
```

**Add line in `## 4. Nyquist Compliance Discovery` table (rows for phases 71, 72, 73, 74, 75, 76, 78, 79 — all 8):** flip `n/a` / `false` → `true` per phase. Set `Status` column to `COMPLIANT` for all.

### Closer Commit Sequence

Per Claude's Discretion #4: planner chooses 1 fat commit vs 3-4 small commits. **Recommendation: 3 small commits** for git-archaeology clarity:

```
docs(87-08): clear Nyquist row from STATE.md Deferred Items (VAL-04)
docs(87-08): flip VAL-01..VAL-04 to satisfied in REQUIREMENTS.md
docs(87-08): update v1.10-MILESTONE-AUDIT.md nyquist scoreboard to compliant
```

Each commit is single-file or near-single-file; diff is small; revert is targeted if needed.

## Risks & Landmines (Q8)

### Restore Step Risks

| Risk | Mitigation |
|------|------------|
| **R-01: Slug mismatch in `60f5f915^`** — CONTEXT D-03 anticipated truncation; 78 slug turned out shorter than predicted. | Verified per-phase: 78's slug is `78-docker-release-image-fix` (no truncation). Other 7 slugs verified verbatim. |
| **R-02: 73-CONTEXT.md absent** — Phase 73 had no formal CONTEXT.md in `60f5f915^`; planner must restore `73-RESEARCH.md` to preserve decisions. | Restore 73-RESEARCH.md mandatory for Phase 73 per D-02 ("optionally `<n>-RESEARCH.md`, only if it carries decisions"). For Phase 73, decisions live ONLY in RESEARCH — restore is mandatory. |
| **R-03: 74 plan filenames lack `74-` prefix** (`01-PLAN.md` not `74-01-PLAN.md`) | Workflow glob `${PHASE_DIR}/*-PLAN.md` matches both forms. Restore verbatim per D-04. |
| **R-04: 79 plan filenames have descriptive suffixes** (`79-01-baseline-and-independence-audit-PLAN.md`) | Same — glob matches. Restore verbatim. |
| **R-05: Archive slug convention mismatch** — `60f5f915^` uses filesystem-truncated slugs; v1.9-phases archive uses clean human slugs (e.g., `56-model-schema-foundation`). | **Decision needed by planner:** keep truncated slugs verbatim per D-04, OR rename to clean slugs matching v1.9 archive convention per D-01. Recommend **keep truncated verbatim** (D-04 wins explicit; cross-references in git history `docs(72-04): ...` don't depend on directory name). Flag for user confirmation if planner wants to deviate. |
| **R-06: Phase 71 absent VALIDATION.md AND RESEARCH.md** — Phase 71 had only CONTEXT + PLANs + SUMMARYs + VERIFICATION in `60f5f915^`. | State B path applies. Plan 87-01 generates VALIDATION.md from template; CONTEXT.md is enough context for the auditor. RESEARCH.md is not needed by the auditor. |
| **R-07: Phase 78 absent VALIDATION.md AND RESEARCH.md, AND only 3 plans (3 PLAN + 3 SUMMARY)** | State B path applies; phase is small enough that the auditor can reconstruct from CONTEXT + 3 plans. |
| **R-08: STATE.md mentions a stale "Phase 72 IT compile error" item** (line 120) | This is `feedback_clean_maven_build_authority` — the error was a VS Code/JDT cache false positive resolved in commit `17f314c4`. The line is stale text that should be removed during the 87-08 closer **or** left alone (out of scope; not on the Nyquist Deferred row). **Recommend leave alone** — VAL-04 is specifically about Nyquist rows, not all of STATE.md. |

### Auditor Spawn Risks

| Risk | Mitigation |
|------|------------|
| **R-09: AskUserQuestion blocks the auto-run** at workflow Step 4 | `mode: yolo` in `.planning/config.json` should cause the auto-pre-select. If it doesn't, the orchestrator picks "Fix all gaps" per D-05. Monitor first plan run for behavior; fall back to manual confirm if needed. |
| **R-10: Auditor classifies more than 2-4 PARTIAL findings per phase** | The CONTEXT D-05 aggressive policy keeps the work bounded; D-06 wallclock guard catches excess. Worst case: planner pauses, downgrades classifications via Manual-Only escalation per D-08 path. |
| **R-11: Impl-bug avalanche (auditor finds non-trivial bugs)** | Default per Deferred Idea #1: capture via `gsd-capture` and defer to v1.12 backlog. Orchestrator must NOT silently auto-fix > ~50 LOC. |
| **R-12: Auditor refuses to escalate** (the auditor.md adversarial stance flag) | The auditor IS allowed to ESCALATE; the failure mode is opposite — auditor weakening assertions to make tests pass. Orchestrator reviews each `## GAPS FILLED` table critically; if a generated test looks trivially-passing, regenerate or escalate manually. |
| **R-13: Auditor model resolution returns haiku** (CLAUDE.md violation) | The orchestrator MUST verify `gsd-sdk query resolve-model gsd-nyquist-auditor --raw` returns opus/sonnet before spawning. Per `feedback_subagent_stability`. |
| **R-14: Subagent branch violation** | Every spawn prompt names branch `gsd/v1.11-tooling-and-cleanup` and forbids `git stash`/`git checkout`/`git reset`. Post-dispatch `git branch --show-current && git log --oneline -3 && git diff --stat` validates. |
| **R-15: Worktree clobber on shared VALIDATION.md** (per `feedback_worktree_file_clobber`) | D-10 enforces strict sequence (single wave, 1 plan at a time). No parallel agents touch shared files. |

### VALIDATION.md Update Risks

| Risk | Mitigation |
|------|------------|
| **R-16: Frontmatter parsing breaks** if YAML has tabs or unbalanced quotes | Use the `Write` or `Edit` tool with verified template; never hand-type frontmatter. Validate with `yq` or similar after write. |
| **R-17: `status` enum mismatch** (e.g., `status: Approved` vs `status: approved`) | Lowercase per template + draft examples. Specifically: `approved` not `APPROVED` or `Approved`. |
| **R-18: `nyquist_compliant` boolean type mismatch** (`true` vs `"true"`) | YAML boolean literal: `true` (no quotes). Verified by draft inspection: drafts use `nyquist_compliant: false` (no quotes). |
| **R-19: Per-Task Map row count mismatch** between draft and final | For State A: preserve all draft rows; flip `❌ W0` → `✅` + status `⬜ pending` → `✅ green`. For State B (Phases 71, 78): generate rows from PLAN task list. |

### Closer Risks

| Risk | Mitigation |
|------|------------|
| **R-20: `[ ]` → `[x]` flip on the wrong line** (multiple `[ ]` lines exist in REQUIREMENTS.md) | Use `Edit` tool with full row content as `old_string` (not just `- [ ]`) — anchors on the VAL-XX identifier. |
| **R-21: STATE.md row deletion collides with concurrent edits** | Branch is single-author (jegr78 + Claude orchestrator). No collision expected. `git pull --rebase` before commit per CLAUDE.md `feedback_branch_from_origin`. |
| **R-22: v1.10-MILESTONE-AUDIT.md two-block YAML update** (top frontmatter scoreboard at lines 12-16 + detailed `nyquist:` block at 52-63) — only one updated → inconsistent | Update BOTH in the same commit; verify with `grep -nE "compliant_phases|partial_phases|missing_phases" .planning/milestones/v1.10-MILESTONE-AUDIT.md` shows new values 2x each. |
| **R-23: PR description update lapsed** | After plan 87-08 lands, run `gh pr edit <#122> --body "$(...)"` per `feedback_pr_description_update`. Plan 87-08 must include this as an explicit final task. |

## Validation Architecture (Phase 87 itself — meta-validation)

> Phase 87 is a meta-phase; its own VALIDATION.md describes how Phase 87 verifies its 4 requirements (VAL-01..VAL-04). The framework is Java JUnit 5 + grep/file-existence checks on `.planning/milestones/v1.10-phases/` artefacts. Most verifications are file-existence + frontmatter assertions, not behavioral tests.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + Spring Boot Test 4.0.6 — same as v1.11 |
| Config file | `pom.xml` (Surefire + Failsafe + JaCoCo; Phase 79 D-05 fork config) |
| Quick run command | `./mvnw test -Dtest='<NewClass>'` (per-task) — for the gap-fill tests landed in v1.10 packages |
| Full suite command | `./mvnw verify -Pe2e` |
| Manual-Only verification commands | `ls .planning/milestones/v1.10-phases/*/` + `grep -E '^status: approved' .planning/milestones/v1.10-phases/*/*-VALIDATION.md \| wc -l` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VAL-01 | 6 v1.10 phases (72-76, 79) have VALIDATION.md with `status: approved` | Manual-Only (grep) | `for n in 72 73 74 75 76 79; do grep -E '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md; done \| wc -l` ≥ 6 | ✅ existing |
| VAL-02 | 2 new VALIDATION.md files for phases 71 + 78 reach `approved` | Manual-Only (grep) | `for n in 71 78; do test -f .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md && grep -q '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md; done` exit 0 | ✅ existing |
| VAL-03 | `/gsd:validate-phase` ran for each of 8 phases; gap tests committed atomically | Manual-Only (git log grep) | `git log --oneline gsd/v1.11-tooling-and-cleanup -- src/test/java/ \| grep -E 'test\\(87-[0-9]{2}\\):' \| wc -l` ≥ 1 (lower bound — exact count depends on gap fill) | ✅ existing |
| VAL-04 | STATE.md Deferred Items has no Nyquist row at v1.11 close | Manual-Only (grep) | `grep -c "Nyquist \\*-VALIDATION.md" .planning/STATE.md` returns 0 | ✅ existing |

### Global Phase 87 Acceptance

A single composite assertion suffices:

```bash
# All 8 VALIDATION.md files exist with status: approved
COUNT=$(for n in 71 72 73 74 75 76 78 79; do
  grep -E '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md 2>/dev/null
done | wc -l)
test "$COUNT" -eq 8 || echo "FAIL: only $COUNT/8 approved"

# Nyquist row absent from STATE.md
grep -c "Nyquist \*-VALIDATION.md" .planning/STATE.md
# expected: 0

# v1.10 audit scoreboard updated
grep -E "compliant_phases: 9" .planning/milestones/v1.10-MILESTONE-AUDIT.md | wc -l
# expected: ≥ 1
```

### Sampling Rate

- **Per task commit (gap-fill commit):** `./mvnw test -Dtest=<NewClass>` (targeted; ~30s)
- **Per plan (87-XX) merge:** the 4-check composite above (file-existence + frontmatter grep + git log grep)
- **Phase 87 gate:** `./mvnw verify -Pe2e` BUILD SUCCESS + CI wallclock ≤ 24:09 + composite assertion green

### Wave 0 Gaps

- **None** — existing test infrastructure (JUnit 5 + pom.xml Surefire/Failsafe + `@Tag` routing from Phase 79) covers all Phase-87 needs. Phase 87 adds zero new test framework or new Maven profile.
- **No new IT base classes** — auditor reuses `BackupRoundTripIT`-style `@SpringBootTest @ActiveProfiles("dev") @Transactional @Tag("integration")` shells where applicable.

### Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 8 VALIDATION.md files reach `status: approved` | VAL-01, VAL-02 | Frontmatter assertions are file-existence + grep, not unit tests | `for n in 71 72 73 74 75 76 78 79; do grep -E '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md; done` → 8 matching lines |
| Gap-coverage tests committed atomically per phase | VAL-03 | Git log inspection, not a runtime test | `git log --oneline -- src/test/java/ \| grep "test(87-"` |
| STATE.md Deferred Items has no Nyquist row | VAL-04 | File grep, not unit test | `grep -c "Nyquist" .planning/STATE.md` → 0 |
| Phase-86 wallclock baseline preserved (D-06 5% guard) | (D-06 internal) | Manual CI workflow_dispatch + log parse, not a JUnit assertion | Trigger CI run, capture `Total time:`, compare to 23:00 × 1.05 = 24:09 |

## Code Examples

### Restored-Phase Directory Layout Target

```
.planning/milestones/v1.10-phases/
├── 71-spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-bui/
│   ├── 71-01-PLAN.md
│   ├── 71-01-SUMMARY.md
│   ├── 71-02-PLAN.md
│   ├── 71-02-SUMMARY.md
│   ├── 71-03-PLAN.md
│   ├── 71-03-SUMMARY.md
│   ├── 71-04-PLAN.md
│   ├── 71-04-SUMMARY.md
│   ├── 71-05-PLAN.md
│   ├── 71-05-SUMMARY.md
│   ├── 71-CONTEXT.md
│   ├── 71-VALIDATION.md           # NEW (State B, generated from template)
│   └── 71-VERIFICATION.md
├── 72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/
│   ├── 72-01-PLAN.md ... 72-05-PLAN.md (10 files)
│   ├── 72-CONTEXT.md
│   ├── 72-RESEARCH.md
│   ├── 72-VALIDATION.md           # UPDATED (State A: status: draft → approved)
│   └── 72-VERIFICATION.md
... etc.
```

### Sample Per-Plan Commit Sequence (Plan 87-04 — Phase 74 example)

```
docs(87-04): restore v1.10 phase 74 for validation closure
            # Restores 10 PLANs (01-PLAN.md..10-PLAN.md), 10 SUMMARYs,
            # 74-CONTEXT.md, 74-RESEARCH.md, 74-VALIDATION.md, 74-VERIFICATION.md
            # into .planning/milestones/v1.10-phases/74-backup-import-preview.../
test(87-04): fill 2 validation gaps for phase 74 (SECU-04 advice scope + IMPORT-01 staging path)
            # New test files under src/test/java/org/ctc/backup/exception/
fix(74): align BackupUploadExceptionHandler advice ordering (single-class scope)
            # OPTIONAL — only if auditor surfaces a trivial bug
docs(87-04): approve 74-VALIDATION.md (status: approved, nyquist_compliant: true)
            # Single-file update: 74-VALIDATION.md frontmatter + Per-Task Map +
            # appended "## Validation Audit 2026-05-18" block with CI run-id
```

Per CONTEXT D-08 + D-13: even if no impl bug surfaces, the 3-commit (restore + test + approve) shape lands. If no test gaps either, the 2-commit (restore + approve) shape lands.

### Template Frontmatter for State B (Phase 71, 78)

```yaml
---
phase: 71
slug: spring-boot-4-0-6-upgrade-thymeleaf-3-1-5-template-audit-build-guard
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-18
approved_on: 2026-05-18
audit_method: retroactive
---
```

For Phase 78, swap to `phase: 78` + `slug: docker-release-image-fix`.

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `*-VALIDATION.md` drafts written pre-execution; never updated post-execution | `/gsd:validate-phase` workflow runs post-execution and updates frontmatter + Per-Task Map | Retroactive approval path exists for shipped phases — no orphan drafts |
| Manual gap-test writing per missing requirement | `gsd-nyquist-auditor` subagent generates + runs gap tests | Adversarial stance enforced; FORCE classification prevents trivially-passing tests |
| Filename-based test routing (`**/*IT.java`) | `@Tag("integration")` / `@Tag("e2e")` routing (Phase 79 D-05) | `@Nested` inner class leakage to wrong phase is structurally impossible |
| Local-only wallclock measurement | CI median over 5 `workflow_dispatch` runs on PR branch (Phase 86 D-17) | PR-branch CI ≡ post-merge master CI; no orphan post-merge commit needed |

**Deprecated/outdated:**
- Surefire `<exclude>**/*IT.java</exclude>` — replaced by `<excludedGroups>integration</excludedGroups>` in Phase 79.
- `@DirtiesContext` on Backup ITs — audited and reduced to genuinely-needed cases in Phase 86 (D-03).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `mode: yolo` config will auto-select "Fix all gaps" at workflow Step 4 AskUserQuestion | Q2 Workflow Internals | Plan execution blocks at first plan run — falls back to manual confirm; recoverable |
| A2 | Gap-count midpoint estimate ~10 across 8 phases (4-16 range) | Q3 Aggregate Gap Count | If auditor is much stricter, wallclock budget may be tight — D-06 tidy-up cycle catches |
| A3 | Most v1.10 phases have COVERED status because IT coverage is dense | Q3 per-phase tables | If auditor disputes, Phase 87 may run longer (more gap tests) |
| A4 | `gsd-sdk query commit` SDK verb works without arguments beyond message | Q1 Restore Commit | Falls back to direct `git commit -m "..."` — recoverable |
| A5 | Phase 86 PR-branch CI median 23:00 holds; baseline doesn't drift due to other v1.11 phases | Q4 Wallclock Baseline | If baseline drifts up before Phase 87 runs, the 5% guard threshold drifts too — neutral |
| A6 | `audit_method: retroactive` is a non-standard frontmatter key not in template | Sign-Off mechanics | Template-strict validators may complain — additive key, low risk; can be omitted |
| A7 | The archive destination `.planning/milestones/v1.10-phases/<n>-<slug>/` does NOT exist yet | Restore Mechanics | Verified: `ls .planning/milestones/v1.10-phases/` returns empty/no-dir — first restore creates the parent |
| A8 | `gsd-sdk query state.deferred-clear` does NOT exist as an SDK verb | Closer Mechanics | Direct `Edit` tool on STATE.md is the fallback (and the recommendation) |
| A9 | The `slug` in restored VALIDATION.md frontmatter should keep the **draft's** human-readable slug, NOT the filesystem-truncated path slug | Sign-Off mechanics | If wrong, the archive uses different slug from frontmatter; cosmetic risk only |

## Open Questions

1. **Should the archive directory use truncated `60f5f915^` slug or clean v1.9-style slug?**
   - What we know: D-04 says verbatim; v1.9-phases archive uses clean slugs; D-01 cites v1.9 as the convention.
   - What's unclear: which decision wins.
   - Recommendation: **Keep verbatim** (D-04 explicit). If the planner prefers clean slugs, surface as a CONTEXT amendment.

2. **Should existing Phase 78 VERIFICATION.md `human_verification` block (post-merge release-workflow observation) become Manual-Only entries in the new 78-VALIDATION.md?**
   - What we know: SC#3 was a by-design post-merge deferral; the audit doc records it as `remaining_post_merge_actions: 2`.
   - What's unclear: should the new 78-VALIDATION.md re-state this, or just point to VERIFICATION.md?
   - Recommendation: **Re-state in Manual-Only with backlink to 78-VERIFICATION.md** — keeps VALIDATION.md self-contained.

3. **Should plan 87-08 close PR #122 or leave it open?**
   - What we know: D-14 says Phase 87 work continues on the milestone branch; PR description is updated.
   - What's unclear: whether `gh pr merge --squash` happens at plan 87-08 close or at `/gsd:complete-milestone v1.11`.
   - Recommendation: **Leave PR open**; `/gsd:complete-milestone v1.11` is the proper closer per CTC v1.x convention. Plan 87-08 ends with PR description update, not merge.

4. **Does the auditor have permission to write to `src/test/java/db/migration/`?**
   - What we know: auditor `tools` list includes `Write`. CLAUDE.md "Constraints" says "Do not change the existing V1 migration; only new V2+ migrations."
   - What's unclear: whether the auditor might add an `IT.java` next to an existing migration test (e.g., V7DataImportAuditMigrationIT).
   - Recommendation: yes — adding a new test class is allowed (only the migration `.sql` files are protected). Auditor must NEVER modify `V*__*.sql`.

## Sources

### Primary (HIGH confidence)
- `.planning/phases/87-nyquist-validation-closure/87-CONTEXT.md` — all 14 D-XX decisions, canonical refs, specifics
- `git show 60f5f915^:...` — direct inspection of all 8 v1.10 phase directories, full file inventory, frontmatter samples
- `$HOME/.claude/get-shit-done/workflows/validate-phase.md` — workflow steps 0-8 + structured returns
- `$HOME/.claude/agents/gsd-nyquist-auditor.md` — auditor contract, adversarial stance, debug loop, return formats
- `$HOME/.claude/get-shit-done/templates/VALIDATION.md` — template shape, section ordering, sign-off list
- `.planning/REQUIREMENTS.md` (head of milestone branch) — VAL-01..VAL-04 exact text, traceability rows
- `.planning/STATE.md` (head) — Deferred Items table with the Nyquist row at line 58
- `.planning/milestones/v1.10-REQUIREMENTS.md` — 39 v1.10 REQ-IDs with phase mapping
- `.planning/milestones/v1.10-MILESTONE-AUDIT.md` — nyquist scoreboard before-state at lines 12-16 + 52-63
- `.planning/codebase/TESTING.md` — Tag conventions, Test Invocation Discipline, JUnit 5 patterns
- `CLAUDE.md` — Subagent Rules, Architectural Principles, Constraints
- `.planning/config.json` — `mode: yolo`, `nyquist_validation: true`, `text_mode: false`

### Secondary (MEDIUM confidence)
- `.planning/phases/86-test-wallclock-reduction/86-06-SUMMARY.md` — CI median 23:00 baseline
- `docs/test-performance.md` — PERF-05 baseline harvest details
- v1.9-phases archive — reference for "established archive convention" wording in D-01

### Tertiary (LOW confidence)
- (none — every claim verified against actual file content or git history)

## Metadata

**Confidence breakdown:**
- Restore mechanics: HIGH — every file existence verified directly in `60f5f915^`
- Workflow internals: HIGH — workflow file read end-to-end; auditor contract loaded
- Gap profile prediction: MEDIUM — based on existing test inventory; auditor's actual verdict may differ ±50%
- Wallclock guard math: HIGH — exact numbers from `86-06-SUMMARY.md`
- Sign-off / frontmatter shape: HIGH — confirmed against template AND existing drafts
- Closer mechanics (line numbers): MEDIUM — line numbers correct at research time; may shift if intermediate edits land before Plan 87-08

**Research date:** 2026-05-18
**Valid until:** 7 days (fast-moving — STATE.md / REQUIREMENTS.md edits may land before Plan 87-08)
