# Phase 69: v1.9 Milestone Closure Hygiene — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-08
**Phase:** 69-milestone-closure-hygiene
**Areas discussed:** Phase 61 UAT-01/02 path, Phase 67 residue path, Nyquist 65-68 sweep depth, Final verify scope

---

## Phase 61 UAT-01 path (GROUPS-Saison Standings visual smoke after f5b10bc)

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-UAT via playwright-cli | Plan boots `dev,demo`, drives existing GROUPS fixture (Season 2023), captures screenshots, records outcome in `61-HUMAN-UAT.md`. SC3 closes via Run-Path. | ✓ |
| Manuell durch User | User clicks UAT-01 in browser, Claude transcribes outcome. Phase 69 waits for user confirmation. | |
| Formell deferred | Sign-off note: GroupsSeasonE2ETest covers programmatically; visual smoke not release-blocking; deferred. SC3 closes via Defer-Path. | |

**User's choice:** Auto-UAT via playwright-cli
**Notes:** Recommended path because dev,demo profile + existing Season 2023 GROUPS fixture make the click-through cheap; playwright-cli is project-standard for visual confirmation per `feedback_playwright_cli`; no real-data dependency.

---

## Phase 61 UAT-02 path (Legacy migrated season visual smoke)

| Option | Description | Selected |
|--------|-------------|----------|
| Formell deferred mit Sign-off | Defer-note in `61-HUMAN-UAT.md`: requires real pre-V4 production data, local fixtures cover only empty-state path; not release-blocking. Status flips to passed. | ✓ |
| Auto-UAT mit synthetischer Legacy-Saison | Build synthetic legacy season via fixture + populated race-results; playwright-cli verifies. More effort, no incremental value vs `LegacyMigratedSeasonE2ETest`. | |
| Aufschieben auf naechstes Milestone | Phase 69 closes SC3 only for UAT-01; UAT-02 stays open as separate backlog/follow-up. | |

**User's choice:** Formell deferred mit Sign-off
**Notes:** Already deferred during original 2026-05-02 UAT cycle by user; no new information available locally; release not blocked.

---

## Phase 67 residue path (~124 attribution markers across 40 files)

| Option | Description | Selected |
|--------|-------------|----------|
| ACCEPT + formal override | 5/5 D-19 gates GREEN; per-file judgement methodology produced this residue deliberately. Override addendum in 67-VERIFICATION.md, status flips to passed, residue noted as deferred for future Quality Gate Lock phase. | ✓ |
| RE-OPEN mit Plan 67-04 | Mechanical sweep across 40 files. CONTEXT.md D-13 conflict (forbade automated regex bulk delete). Risk of Javadoc false-positives. | |
| ACCEPT + zukuenftiger CI/pre-commit guard | Like ACCEPT, plus concrete backlog item committing to Maven Enforcer / pre-commit hook in next milestone. Structural close instead of process close. | |

**User's choice:** ACCEPT + formal override
**Notes:** Verifier already leaned this way ("Recommended Disposition / Verifier's lean: Option A"); formal override formalises that lean. The CI/pre-commit guard is captured in CONTEXT.md `<deferred>` for the next milestone's backlog (avoids re-opening v1.9 by adding a new phase).

---

## Nyquist 67/68 scope (comment-only / build-only phases)

| Option | Description | Selected |
|--------|-------------|----------|
| n/a by-design | Mirror Phase 63 docs-only: VALIDATION.md frontmatter `nyquist_compliant: n/a`, `wave_0_complete: n/a` with rationale. No new test code. | ✓ |
| Vollaudit per gsd-validate-phase | Run auditor for both phases like 56-62. Risk of false-positive missing-test reports for comment-only diffs. | |
| Hybrid: 67=n/a, 68=audit | Compromise: Phase 67 (pure comment-sweep) → n/a; Phase 68 (Lombok pin + JEP 498 flag) → audit because pom.xml affects build path. | |

**User's choice:** n/a by-design
**Notes:** Phase 67 is comments-only (cannot regress bytecode coverage by construction); Phase 68 is `pom.xml` pin + JEP 498 flag in 3 fork sites (build-only, no logic-code path under test). Phase 63 docs-only is the established precedent.

---

## Nyquist auto-fill behaviour (when 65/66 audit finds genuine gap)

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-fill mit neuen Tests (mirrors Phase 64) | Plan commits missing tests inline (V3MigrationTest auto-fill pattern from 64-01-SUMMARY). ONE final `./mvnw verify -Pe2e` per `feedback_test_call_optimization`. Frontmatter flips to `nyquist_compliant: true`. | ✓ |
| Manual-Only escalation | Document gap as Manual-Only with "Why Manual" rationale (Visual-Quality-Bar / MariaDB-CI). Frontmatter flips when all entries are COVERED or Manual-Only-with-rationale. | |
| Block + report NEEDS_CONTEXT | Subagent escalates per gap; user decides individually. Mid-phase user interaction required. | |

**User's choice:** Auto-fill mit neuen Tests (mirrors Phase 64)
**Notes:** Phase 64 already established this methodology and shipped successfully (V3MigrationTest auto-fill, 8/8 green on first iteration). Manual-Only escalations remain available for genuinely-manual concerns (Visual-Quality-Bar etc.) per Phase 64 standard.

---

## Final verify scope

| Option | Description | Selected |
|--------|-------------|----------|
| `./mvnw verify -Pe2e` | Honors `feedback_e2e_verification`. Surefire+IT (~1246 tests) + Failsafe Playwright E2E (31 tests) + JaCoCo 82% gate. ~5-7 min. Full coverage snapshot for v1.9 closure. | ✓ |
| `./mvnw verify` (ohne -Pe2e) | `feedback_test_call_optimization` notes -Pe2e only when UI affected. Phase 69 has no UI changes; Auto-UAT runs separately via playwright-cli. ~3 min. | |
| `./mvnw verify` + separater playwright-cli UAT-01 run | Surefire+IT (~3 min) as SC6 gate, plus separate playwright-cli for UAT-01 (SC3). Cleaner SC3-vs-SC6 separation, skips Failsafe profile if no new E2E tests. | |

**User's choice:** `./mvnw verify -Pe2e`
**Notes:** Project default per `feedback_e2e_verification` (36 days old but unchanged in practice). Single final verify, no intermediate full runs (`feedback_test_call_optimization` honoured by avoiding mid-phase verify). UAT-01 playwright-cli still runs separately as part of the Auto-UAT task; the `-Pe2e` Failsafe run gives the full coverage snapshot before milestone close.

---

## Claude's Discretion

- Exact wording of override / defer / closure addendum sections in 61-VERIFICATION.md and 67-VERIFICATION.md (must include date, rationale, sign-off citation).
- Plan organisation: 1 mega-plan vs. 4-7 focused plans (CONTEXT.md D-20 recommends 4 plans grouped by SC clusters, but planner refines).
- Order of SC1+SC2 vs. SC3+SC4 vs. SC5 vs. SC6 plan execution (independent; can run parallel waves where worktree-isolation isn't required).
- Whether `61-HUMAN-UAT.md` is a new file or a section in `61-VERIFICATION.md` (D-01 implies new file; planner may choose).
- Exact Nyquist gap-fill test naming + location.
- Whether to update `.planning/STATE.md` `last_activity` per plan or only at phase close.

## Deferred Ideas

- Future "Quality Gate Lock" / CI comment-noise guard phase (next milestone backlog).
- Phase 67 RE-OPEN with Plan 67-04 (alternative not chosen).
- WARN-1: per-group matchday generation UI affordance.
- OBS-3: StandingsController.java:139 lazy collection.
- OBS-4: REQUIREMENTS.md traceability rows for phases 64-68 (by design).
- UAT-02 with real legacy-season production data (post-deploy verification).
- Audit re-run + `/gsd-complete-milestone` after Phase 69 closes.
