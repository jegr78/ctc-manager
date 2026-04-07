# Retrospective

Living retrospective across milestones. Updated at each milestone completion.

## Milestone: v1.0 — Technical Debt Cleanup

**Shipped:** 2026-04-04
**Phases:** 5 | **Plans:** 12 | **Timeline:** 2026-03-27 → 2026-04-04 (8 days)

### What Was Built
- GlobalExceptionHandler mit 3 typed Exception classes, admin error page
- 135 orElseThrow()-Migrationen zu EntityNotFoundException/ValidationException
- 7 Controller von Repository-Injections befreit, 4 neue + 3 erweiterte Services
- RaceManagementService (673 LOC) in 3 fokussierte Services aufgeteilt
- 36 FK-Indexes + 28 @EntityGraph-Annotationen
- Spring Security Basic Auth (prod/docker) + SSRF-Schutz + 403-Seite

### What Worked
- **TDD-Workflow:** Tests first, dann Implementierung — hat Regressions frueh gefangen
- **Phase-Sequenzierung:** Exception-Infra zuerst, dann Services, dann Security — jede Phase baute auf der vorherigen auf
- **Parallele Execution:** Wave-basierte Parallelisierung mit Worktrees funktionierte gut fuer unabhaengige Plans
- **Research vor Planning:** RESEARCH.md mit Spring Security 7 Patterns hat D-07 (falsche Annahme ueber @WithMockUser) frueh korrigiert

### What Was Inefficient
- **REQUIREMENTS.md Tracking-Gap:** SRVC-01..07 wurden nicht automatisch als Complete markiert nach Phase 2 — musste manuell vor Milestone-Abschluss korrigiert werden
- **Plan-Checker Rate-Limit:** Planner Agent hat Rate-Limit erreicht bei Phase 5, musste manuell Plan 02 + 03 erstellen
- **D-07 Contradiction:** CONTEXT.md hatte locked decision die Research widerlegte — brauchte manuellen Override im Plan-Checker Loop

### Patterns Established
- Zwei-Profil SecurityFilterChain (@Profile statt Runtime-Check)
- GraphicGenerator-Pattern (DRY fuer Graphic Services)
- EntityNotFoundException als Standard fuer orElseThrow()
- Test-Naming: givenContext_whenAction_thenExpectedResult()

### Key Lessons
- Research-Phase lohnt sich besonders fuer Framework-Upgrades (Spring Security 7 API-Aenderungen)
- Locked Decisions in CONTEXT.md sollten nach Research validiert und ggf. revidiert werden
- Requirements-Traceability muss bei Phase-Completion automatisch aktualisiert werden

### Cost Observations
- Model mix: 90% opus, 10% sonnet (checker/verifier)
- Sessions: ~5 (Phase 1-2 combined, Phase 3, Phase 4, Phase 5 plan+execute, complete)
- Notable: Parallel worktree execution sparte ~50% Wartezeit bei Wave 1 Plans

## Milestone: v1.1 — Codebase Concerns Cleanup

**Shipped:** 2026-04-07
**Phases:** 10 | **Plans:** 20 | **Timeline:** 2026-04-04 → 2026-04-07 (4 days)

### What Was Built
- SSRF hostname blocklist + path traversal defense for FileStorageService (SECU-01, SECU-02)
- 10 domain services decoupled from admin DTOs, 5 controllers using services only (ARCH-01, ARCH-02)
- TemplateEditorController generic dispatch via TemplateManageable interface (ARCH-03)
- PlayoffService split into BracketView + Seeding, RaceService split into FormData + Calendar (ARCH-04, ARCH-05)
- 25+ catch(Exception e) narrowed to specific types across all controllers/services (ERRH-01)
- Cross-season alltime standings aggregation with sub-team resolution (FEAT-01)
- Inline styles replaced with CSS utility classes in all admin templates (QUAL-01)
- Unbounded findAll() scoped or documented (QUAL-02)

### What Worked
- **Recovery phases (12-15):** After worktree file clobber lost Phases 6-9 work, recovery was systematic — exact same logic re-applied with TDD, fidelity verified
- **Milestone audit before completion:** Caught the worktree clobber regression immediately and scoped precise gap-closure phases
- **Integration checker at audit:** Verified 42 cross-phase connections, caught REQUIREMENTS.md documentation drift
- **TDD in recovery phases:** Writing tests first during recovery ensured exact behavioral fidelity with originals

### What Was Inefficient
- **Worktree file clobber:** Parallel worktree agents on stale bases overwrote files from earlier phases — required 4 recovery phases (12-15) to re-implement lost work
- **REQUIREMENTS.md checkbox drift:** 7 of 12 requirements never got checked off despite being verified passed — documentation tracking gap persisted across multiple phases
- **Worktree merge conflicts:** Agent worktrees based on wrong codebase version required manual cherry-pick and conflict resolution (Phase 15)
- **Phase 10 Nyquist compliance:** Only 1 of 10 phases achieved full Nyquist validation — test-first discipline was present but formal VALIDATION.md lagged

### Patterns Established
- TemplateManageable interface pattern for generic controller dispatch
- Domain service decoupling via primitive parameters and nested domain records
- Recovery phase workflow: audit → identify gaps → plan gap-closure → execute → re-verify
- 3-source requirements cross-reference (VERIFICATION + SUMMARY frontmatter + REQUIREMENTS traceability)

### Key Lessons
- **Worktree isolation is fragile:** Parallel agents sharing the same repo can clobber each other's work if worktree base commits are stale. Always verify EXPECTED_BASE before spawning.
- **Audit early, audit often:** The milestone audit immediately after Phase 11 caught regressions that would have been much harder to trace later.
- **Recovery is cheaper than debugging:** Re-implementing with TDD (4 recovery phases) was faster than trying to cherry-pick and resolve conflicts from the clobbered commits.
- **Documentation tracking needs automation:** Manual checkbox updates in REQUIREMENTS.md consistently drift — need automated traceability updates at phase completion.

### Cost Observations
- Model mix: 70% opus (execution), 30% sonnet (verification, integration check)
- Sessions: ~8 (Phases 6-9, Phases 10-11, audit, Phases 12-13, Phase 14, Phase 15, re-audit, complete)
- Notable: Recovery phases (12-15) added ~30% overhead but preserved architectural integrity

## Milestone: v1.2 — Driver Merge

**Shipped:** 2026-04-07
**Phases:** 4 | **Plans:** 5 | **Timeline:** 2026-03-27 → 2026-04-07 (11 days)

### What Was Built
- DriverMergeService with transactional FK reassignment across SeasonDriver, RaceLineup, RaceResult, PsnAlias
- Proactive duplicate detection — source entries dropped when target already present in same season/race
- MergePreview (read-only) with per-table reference and duplicate counts
- Full merge UI: button on driver detail, target dropdown, preview table, JS confirm dialog
- Error handling on previewMerge() matching executeMerge() pattern (gap closure)

### What Worked
- **Focused milestone scope:** 4 phases, each building cleanly on the previous — no scope creep
- **TDD throughout:** Every phase started with failing tests, then implementation — 852 tests at end
- **Inline execution for small phases:** Phase 19 (1 plan, 2 tasks) ran inline without subagent overhead — faster and cheaper
- **Auto-UAT with playwright-cli:** Automated the human verification items for Phase 19, catching a stale-server issue in the process
- **Gap closure workflow:** Milestone audit identified GAP-01, Phase 19 closed it precisely

### What Was Inefficient
- **REQUIREMENTS.md checkbox drift (again):** All 14 requirements stayed `[ ]` despite being verified — same issue as v1.1
- **SUMMARY.md frontmatter incomplete:** `requirements_completed` field missing across all plans — summary-extract returns empty
- **Stale server during auto-UAT:** Server running old code gave false 409 errors — had to restart before tests passed
- **Phase 18 human verification:** 5 visual UI items still unverified via auto-UAT (only Phase 19 items automated)

### Patterns Established
- Separate MergeService from DriverService to avoid circular dependencies
- Proactive conflict detection (query before reassign) instead of catch-constraint-violation
- Two-state Thymeleaf template pattern (preview == null vs. preview != null)
- Auto-UAT with playwright-cli for flash message / redirect verification

### Key Lessons
- **Small phases execute cleanly:** 4 focused phases with clear dependencies had zero regressions
- **Auto-UAT needs fresh server:** Always restart dev server before auto-UAT when code changed since last start
- **Requirements tracking still needs automation:** Three milestones in a row with drifted checkboxes — this is a tooling gap, not a discipline gap
- **Inline execution for tiny phases:** Subagent overhead is not worth it for 1-2 task plans

### Cost Observations
- Model mix: 60% opus (execution), 40% sonnet (verification, integration check, research)
- Sessions: ~4 (research+plan, execute 16-18, execute 19 + auto-UAT + audit, complete)
- Notable: Phase 19 inline execution used ~5% of a typical subagent session's tokens

## Cross-Milestone Trends

| Metric | v1.0 | v1.1 | v1.2 |
|--------|------|------|------|
| Phases | 5 | 10 | 4 |
| Plans | 12 | 20 | 5 |
| Tests (start → end) | 628 → 753 | 753 → 820 | 832 → 852 |
| Coverage | 82%+ | 82%+ | 82%+ |
| Timeline (days) | 8 | 4 | 11 |
| Files changed | 68 | ~80 | ~15 |
| LOC delta | +3850 / -962 | +5100 / -1300 | +600 / -30 |
