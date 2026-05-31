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

## Milestone: v1.8 — Bulk Driver Import from Google Sheets

**Shipped:** 2026-04-25
**Phases:** 2 | **Plans:** 4 | **Timeline:** 2026-04-24 → 2026-04-25 (2 days)

### What Was Built

- Stateless preview service (`DriverSheetImportService.preview()`) mit D-12 Waterfall, 7 inneren Records + ErrorReason enum, `findByYear(int)` Auto-Match
- `@Transactional execute()` mit 6-Bucket-Walk, Cross-Tab-Driver-Dedup, per-row Skip/Accept, mutable `ExecuteResult` Accumulator
- Thin `DriverSheetImportController` (3 Handler) + 2 Thymeleaf-Templates (6 Bucket-Tabellen, Skip/Accept Checkboxen) + Entry-Button
- 21 Integration-Tests (17 happy + 4 exception), JaCoCo 82% gehalten

### What Worked

- **Reuse pattern strikt durchgehalten:** GoogleSheetsService + DriverMatchingService + CsvImportController-Preview-State unmodifiziert — keine Parallelinfrastruktur, niedriger Cognitive Load
- **D-13/D-15 Override-Mechanismus:** ROADMAP-Wording (`findByName/findByDisplayLabel`, `DriverSheetImportForm` DTO) wurde via CONTEXT-Decisions früh durch Implementierungs-Realität ersetzt; Override sauber in PROJECT.md + REQUIREMENTS.md dokumentiert
- **Code-Review → Auto-Fix-Loop:** 1 Critical (CR-01 cache key) + 3 Warnings → alle 4 atomisch gefixt + Regressionstest hinzugefügt vor Merge
- **Phase 55 Plan-Split:** Service / Controller+Templates / Tests sauber sequenziell — Plan 03 konnte Plan 02 Bug (SpEL-Lambda im Template) entdecken und dokumentieren

### What Was Inefficient

- **DevDataSeeder-Year-Collision:** Test-Fixture-Years 2024/2023 kollidierten mit `@Profile("dev") TestDataService.seed()` (auto-via CommandLineRunner) — 1 Test-Failure, ~20 Min Debug bis Root-Cause klar war. Fix war trivial (auf 2021/2022 wechseln), aber die Überraschung kostete Zeit. Lesson: integration tests under `@SpringBootTest(profiles=dev)` müssen DevDataSeeder-Annahmen kennen
- **Stuck-Agent (Plan 55-03):** Executor-Agent hit stream idle timeout nach 35 Min ohne Completion-Signal trotz fast fertiger Arbeit. Worktree-State war intakt, Orchestrator hat manuell weitergemacht (commit + ExceptionTest schreiben + verify). Lesson: Stream-Timeout ist nicht == Failure, immer Worktree inspizieren
- **Template-SpEL-Lambda-Pitfall:** `th:if="${preview.tabPreviews().stream().anyMatch(...)}"` war structurally invalid für Thymeleaf restricted SpEL. Plan 02 hat es geliefert, Plan 03 Tests haben es entdeckt — sollte schon in Plan 02 Code-Review gefangen werden, nicht erst durch Tests

### Patterns Established

- **Year-Fixture-Isolation:** Integration-Tests mit `@SpringBootTest(profiles=dev)` müssen Years vermeiden, die DevDataSeeder seedet (2023/2024/2026). 2021/2022/2025/2027 sind frei
- **D-15 Form-Binding-Override:** Bei dynamischen per-row Form-Keys ist `@RequestParam` + `Map<String, String>` der korrekte Pfad, nicht ein statisches DTO — `DriverSheetImportForm` wäre architektonisch falsch gewesen
- **Per-Tab Cache-Key für FUZZY-Accept:** `crossTabCreatedDrivers.computeIfAbsent(psnId + "_accept_" + year, …)` — der naive `psnId`-Key reicht nur für die NEW-Driver-Branch (Cross-Tab-Dedup OK dort, weil keine User-Choice splittet)

### Key Lessons

- **DevDataSeeder ist Test-Boundary:** Jeder neue `@SpringBootTest(profiles=dev)` muss DevDataSeeder-Years auf der Liste haben. Sollte in `.planning/codebase/TESTING.md` dokumentiert werden
- **Template-SpEL-Lambda-Allergie:** `th:if`-Lambdas auf Collections funktionieren NICHT mit Thymeleaf default; Berechnung gehört in den Controller (CLAUDE.md "Keep Thymeleaf Templates Lean" wurde durch Plan 02 verletzt, durch Plan 03 wieder hergestellt)
- **GSD Workflow-Resilience:** Stuck-Agent Recovery hat funktioniert — Plan 55-03 wurde inline finalisiert ohne Datenverlust dank Worktree-Isolation. Keine Anpassung nötig

### Cost Observations

- Model mix: ~70% sonnet (execution + verification), ~30% opus (orchestration + Code-Review-Fix)
- Sessions: 1 (alle Phasen + UAT + Audit + Ship + Complete in einer durchgehenden Session)
- Notable: PR-Erstellung + CI + Squash-Merge + lokales Cleanup + Milestone-Close in derselben Session lief sauber durch — `gh` CLI + Memory-Regel `feedback_squash_merge_message.md` haben Friktion vermieden

## Milestone: v1.9 — Season Phases & Groups

**Shipped:** 2026-05-09
**Phases:** 15 (56-70) | **Plans:** ~70 | **Timeline:** 2026-04-26 → 2026-05-09 (14 days)

### What Was Built

- Phase/Group domain model: `SeasonPhase` (REGULAR/PLAYOFF/PLACEMENT) + `SeasonPhaseGroup` + `PhaseTeam` roster — Saison-Container vom flachen Modell zur Phase-Klammer mit optionalen Sub-Gruppen
- Mechanische Daten-Migration via Flyway V3-V6: Bestandsseasons → 1 REGULAR + ggf. 1 PLAYOFF; `season_id`-Bridge-Spalten + `playoff_seasons`-Join-Table dropped; Bestand bleibt byte-identisch erreichbar
- Phase-aware Domain Services: `StandingsService.calculateStandings(phaseId, groupId)`, `DriverRankingService` cross-phase aggregation, `MatchdayGeneratorService`/`SwissPairingService` phase-/group-aware, `PlayoffService.createPlayoff` atomisch über PLAYOFF-Phase + Playoff
- Admin UI mit Saison-Detail Two-Row-Tabs, Phase- + Group-CRUD, Standings-Phase/Group-Auswahl + Combined-View, Playoff-UI auf PLAYOFF-Phase
- Public Site mit Phase-Tab-Reihe + Group-Sub-Tab-Reihe + per-phase URL-Varianten + PLAYOFF-Tab + Phase-Breakdown auf Team/Driver-Profilen; LEAGUE-only-Seasons rendern byte-identisch
- Driver-Import: `findByYearAndNumber`, Tab-Pattern `^\d{4}_S\d+$`, Parent-Team-Resolver (Phase 70 D-05 inverts Phase 66 D-04), `findByPsnId`-Guard gegen GAP-70-01 Cross-Tab-Duplicate-Insert
- 1227 Unit + 31 Playwright E2E Tests, JaCoCo Line 87.02% (Gate 82%), Lombok 1.18.46 + JEP 498 + Guava 33.4.8 zur Java-25-Warning-Bereinigung

### What Worked

- **Audit-getriebene Phasen-Insertion:** Audit nach Phase 62 deckte Gap "public site invisibly stuck on LEAGUE shape" auf — Phase 62 wurde als END-of-Milestone-Phase nachgezogen statt als Tech Debt verschoben. Gleicher Mechanismus bei Phasen 66/67/68/70 (UAT-discovered).
- **Multi-Wave Plans innerhalb großer Phasen:** Phase 58 (6 plans / 5 waves) und Phase 60 (7 plans / 4 waves) waren wave-explizit geplant — abhängige Plans liefen sequenziell, unabhängige parallel; keine Worktree-Clobbers wie in v1.1.
- **Live MariaDB UAT als Release-Gate:** UAT D-22 (Saison 2023 Driver-Import auf MariaDB) deckte sowohl GAP-70-01 (DB-State-Mismatch) als auch Phase-66-D-04-Domain-Violation auf — beide hätten unentdeckt produktions-deployt werden können. Lesson: Lokale H2-only-Verification ist nicht ausreichend für Schema-Migrations-Milestones.
- **Phase-66/Phase-70 Re-Open Addendum-Pattern:** Statt Phase 66 zurückzusetzen hat Phase 70 ein "Re-Open Addendum" zu `66-VERIFICATION.md` + Inline-Supersede-Notes auf `66-CONTEXT.md` D-04..D-09 + Frontmatter-Eintrag `re_verification` geschrieben. Audit-Trail bleibt intakt, Domain-Modell-Inversion ist nachvollziehbar dokumentiert.
- **Branch-Disziplin: 442 Commits, 1 Branch:** `gsd/v1.9-season-phases-groups` blieb über 14 Tage stabil; Subagent-Branch-Schutz (CLAUDE.md Subagent Rules) hat keine Branch-Switches zugelassen.
- **gsd-debug für GAP-70-01:** Der Debug-Workflow hat Hypothese 1 (cross-tab `computeIfAbsent` ohne DB-Konsultation) confirmed und Hypothese 2 (FUZZY-no-accept-dup) via `findByPsnId` Stage-1 short-circuit als unmöglich nachgewiesen — saubere wissenschaftliche Methode statt Trial-and-Error.

### What Was Inefficient

- **Phase-66-D-04 als „Default war model-violating":** Das ursprüngliche Phase-66-Discuss hat „sub-team mit PhaseTeam wins" als naheliegendes Default gesetzt, ohne den Fahrer-am-Parent-Team-Domain-Constraint explizit zu validieren. Phase 70 (4 Plans, 4 Tage Re-Work) wäre vermeidbar gewesen, wenn das CONTEXT.md von Phase 66 das Domain-Modell strikt referenziert hätte. Lesson: Bei Domain-relevanten Defaults explizit "Welcher Pfad ist Domain-konform?" als Discuss-Frage stellen.
- **Phase-69 als Bookkeeping-Catch-Up:** 4 Plans nur um zurückgebliebene VERIFICATION.md (Phase 64/65), VALIDATION.md-Flips (Phase 65/66/67/68) und SUMMARY-Frontmatter-Sweeps (58/59/60) nachzuziehen. Pures Audit-Erfüllen, kein Code. Lesson: Frontmatter-Sweep + VERIFICATION.md sollten am Phasen-Ende automatisiert sein, nicht via Catch-Up-Phase.
- **Phase-61-„Cleanup & Quality Gate" wurde 14 Plans (5 + 9 gap-fixes):** ROADMAP versprach „Drop old columns + JaCoCo + 2 E2E tests" — am Ende kamen 9 weitere gap-Plans für Service-Cutover-Restwerk (mehrere Caller waren bei Phase-58-Service-Migration übersehen worden). Lesson: Phase 58 Plan-Checker hätte mit `grep` über alle Caller die Vollständigkeit der Service-Migration prüfen müssen.
- **Bookkeeping-Tech-Debt für 4 Phasen verbleibt:** Plan SUMMARY `requirements-completed` Frontmatter ist für Phasen 56/57/62/64 (15 SUMMARYs) immer noch unvollständig — explizit als „nächste Milestone Hygiene-Phase" deferred, aber das ist die zweite Milestone-Iteration in Folge mit ähnlichem Tech-Debt-Pattern.
- **Doppelter `sun.misc.Unsafe`-Warnings-Hunt (Phase 68 + Guava-Pin):** Phase 68 hat Lombok 1.18.46 + JEP 498 gepinnt, war aber nicht ausreichend — Guava 33.1.0 (transitiv via google-api-client) emittierte den gleichen Warning. Wäre via initialer Diagnose („welche Bibliothek triggered den Warning?") in einem Phasen-Schnitt machbar gewesen.

### Patterns Established

- **Re-Open Addendum auf VERIFICATION.md:** Statt eine bereits passte Phase zurückzusetzen, neue Phase legt `## Phase-XX Re-Open Addendum` + Frontmatter-`re_verification`-Eintrag + Inline-Supersede-Notes auf der Quell-Phase ab. Audit-Trail bleibt nachvollziehbar.
- **Live-DB UAT als Release-Gate für Schema-Migrations-Milestones:** ROADMAP-SC explizit „Saison X auf MariaDB ohne Errors importierbar" + Evidence-Snippet im AUDIT-File mit Anzahl betroffener Rows.
- **Phase-Insertion via Audit nach Mid-Milestone-Phasen:** Audit nach Phase N kann Phase N+1 ergänzen (Phase 62 nach Phase 61 UAT, Phase 70 nach Phase 69 Live-UAT). Strukturierter als „Tech Debt fürs nächste Milestone" wenn die Lücke milestone-blocking ist.
- **Phase-aware Service-API mit Combined-View-Default:** `calculateStandings(phaseId, groupId=null)` aggregiert über alle Sub-Groups der Phase — Combined-View ist nicht ein zweiter Service-Aufruf, sondern derselbe mit `null`-Group.
- **Domain-Model-Doc als Plan-Boundary:** Phase 70 D-05 inversion war notwendig, weil Phase 66 ohne explizit referenziertes Domain-Modell-Doc geplant wurde. Konsequenz: Domain-relevante Phasen brauchen ein Domain-Modell-Reference-Doc als Plan-Input.

### Key Lessons

- **Domain-Constraints müssen vor Defaults validiert werden:** „Naheliegender Default" reicht nicht — bei Domain-relevanten Defaults die Domain-Doku explizit konsultieren. Sonst kostet das wie Phase 70 eine zusätzliche 4-Plan-Phase.
- **Live-DB-UAT ist nicht optional bei Schema-Migrations-Milestones:** H2-Tests + lokaler Dev-Profil decken nicht alle Pfade ab. ROADMAP-SC für „cross-DB UAT" sollte default-on sein für jede Phase, die Flyway-Migrationen einführt.
- **Bookkeeping-Frontmatter ist durably wartungsbedürftig, nicht ein Once-Off:** Drei Milestones in Folge mit `requirements-completed` Drift. Tooling (Pre-Commit-Hook auf SUMMARY-Files) ist überfällig.
- **Plan-Checker muss Caller-Vollständigkeit prüfen:** Bei Service-API-Migrationen sollte der Plan-Checker `grep` über alle Caller-Sites laufen lassen und die Vollständigkeits-Behauptung in der PLAN.md prüfen — verhindert das Phase-61-Catch-Up-Phänomen.
- **`sun.misc.Unsafe`-Warnings: erst alle Quellen identifizieren, dann fixen:** `mvn dependency:tree` + Warning-Reproduktion auf jedem Modul-Level vor dem ersten Pin spart einen zweiten Cycle.

### Cost Observations

- Model mix: ~60% opus (Domain-Modell-Phase 56-58 + Audits), ~30% sonnet (Plans 60-62 UI/Templates), ~10% haiku (read-only Reviews + Research)
- Sessions: ~14 (eine pro Tag), GSD-Workflow durchgehend mit `--auto`-Chain für Phasen 64+65+67+68+69 (kleinere Phasen)
- Notable: Phase 70 wurde nach dem post-Phase-69-Audit eingefügt — der zweite Audit-Run (post-Phase-70) kostete weitere 30 Min, war aber nötig, um die 14-→-15-Phasen-Inversion sauber zu dokumentieren
- Notable: Phase 61 war mit 14 Plans (5 + 9 gap) der größte Gap-Tail des Milestones — pure Service-Cutover-Restwerk-Plans hätten Phase 58 Plan-Checker abfangen können

## Milestone: v1.11 — Tooling Infrastructure & Tech-Debt Sweep

**Shipped:** 2026-05-18
**Phases:** 8 (80-87) | **Plans:** 46 | **Timeline:** 2026-05-16 → 2026-05-18 (2 days)

> Note: v1.10 retrospective section was skipped during that milestone close — gap acknowledged, not retroactively filled here.

### What Was Built

- OpenRewrite developer-invoked refactoring (`-Prewrite` Maven profile), one-shot `CommonStaticAnalysis` cleanup across 380 files
- SpotBugs 4.9.8.3 + find-sec-bugs 1.14.0 blocking gate verify-bound, 220 baseline findings triaged to 0
- 12 Phase-75 backup REVIEW.md Info/Warning items resolved + `BackupSchemaGuardTest` + `BackupRestoreZipOpenCountIT` + 24-entity row-count parity
- Quality and Polish sweep: 4 v1.9/v1.10 carryover items (driver-detail chip order, DevDataSeeder widening, per-group matchday UI, OSIV cleanup) + UAT-02 procedure
- Mend Renovate GitHub App integration with comprehensive safety packageRules (Guava/Thymeleaf/Java LTS pins + noble Dockerfile regex + patch automerge)
- CodeQL SAST blocking gate (`security-extended`) with 3-layer FP suppression invariant (codeql-config + source markers + sast-acceptance.md)
- Test wallclock baseline established (CI median 23:00); PERF-04 OR-branch verdict with documented v1.12 forward path
- Phase 87 retroactive v1.10 Nyquist VALIDATION closure (8 phases approved, 6 gap-fill tests, 0 impl bugs)
- In-milestone v1.11 Nyquist closure (Option A inline, 6 retroactive approves + 1 retroactive 86-VERIFICATION.md)
- CI Playwright fork-channel corruption fix (`actions/cache@v4` + pre-install all 3 default browsers)
- T-2 master branch protection activated post-merge gate

### What Worked

- **In-milestone Nyquist closure (Option A pattern):** Discovering during milestone audit that the milestone itself accumulated Nyquist debt (same shape Phase 87 just closed for v1.10) and resolving it inline same-day via 6 retroactive `/gsd-validate-phase` runs + 1 retroactive VERIFICATION.md — avoided creating a v1.12 carry-forward phase. Sets precedent for future milestones.
- **gsd-nyquist-auditor agent dispatched per phase with CI baseline as authority:** Each agent had ~7 minutes wall-time to confirm coverage against `CI run 26033853591` SHA `3590b3a7` + shipped code; 0 impl bugs surfaced across 14 audits (8 v1.10 phases + 6 v1.11 phases) confirming both milestones shipped with strong test coverage.
- **Synthetic deliberate-violation testing (STAT-06 + SAST-06 + DEPS-08):** Throwaway-branch deliberate-failure PRs surfaced semantic bugs that structural testing missed — SAST-06's gate-step `ref=` vs `pr=` query bug only manifested on a real PR-context alert. Exactly the failure mode SAST-06 was designed to catch.
- **CI dumpstream artifact as debugging tool:** Surefire fork-channel corruption (Playwright Chromium auto-download mid-test) diagnosed via the `2026-05-18T05-24-53_082-jvmRun2.dumpstream` artifact from a broken CI run — file pointed straight at the failure mode.
- **D-17 PR-branch CI harvest = post-merge master harvest:** Recognizing that `ci.yml` runs identical steps for `pull_request`/`push`/`workflow_dispatch` triggers allowed Phase 86 PERF-05 baseline harvest within the same PR (5 `workflow_dispatch` runs) — no orphan post-merge `docs(86):` commit needed.

### What Was Inefficient

- **Auditor agent ignored "do not modify VALIDATION.md" instruction (Phase 84, 85):** Despite explicit "write to /tmp only" instructions, the gsd-nyquist-auditor agents directly modified `84-VALIDATION.md` / `85-VALIDATION.md` files. Outcome was actually fine (consistent shape, fewer steps), but breaks the orchestrator-driven atomic-commit pattern. Future audits: either accept the agent's direct-modification pattern (simpler) or enforce instruction more strictly.
- **REQUIREMENTS.md bookkeeping drift (recurring theme — see Cross-Milestone Trends):** STAT-01..07 + PERF-01..05 (12 checkboxes) remained `[ ]` for ~3 days post-shipping despite work being green in CI; only the milestone audit surfaced the drift. Tooling solution (auto-flip on phase-complete commit hook) still overdue.
- **Phase 86 missed VERIFICATION.md creation (during execution):** No goal-backward verification artifact was created during Plans 86-01..86-06 execution, only at retroactive audit time. Phase plans should include explicit "generate VERIFICATION.md" task or `/gsd-verify-work` invocation.
- **gsd-sdk `milestone.complete` accomplishment extraction noisy:** SUMMARYs without `one_liner:` frontmatter field produce literal "One-liner:" placeholders in MILESTONES.md. Future: enforce `one_liner:` in SUMMARY template or filter placeholders in CLI.

### Patterns Established

- **In-milestone Nyquist closure path (Option A):** When a milestone audit surfaces draft VALIDATION.md across the milestone's own phases, run retroactive `/gsd-validate-phase` + `gsd-nyquist-auditor` per phase + Phase-86-style retroactive VERIFICATION.md inline same-day. Avoids cross-milestone carry-forward.
- **CI Playwright cache invariant:** `actions/cache@v4` for `~/.cache/ms-playwright` + pre-install all 3 default browsers (`Playwright.create()` validates Chromium + Firefox + WebKit on first use — not just `chromium()`). Documented in `ci.yml` comment block + memory `feedback_gh_api_zsh_globs` (sibling memory `feedback_clean_build_only`).
- **3-layer FP suppression invariant (CodeQL):** codeql-config `query-filters` + source-marker comment + sast-acceptance.md table row — all three required for every suppression. Update-on-Triage discipline catches drift.
- **D-08 layer 2 architectural filter extension (SpotBugs):** When `EI_EXPOSE_REP*` false-positives appear across multiple service/DTO/record packages, extend the package-level `<Match>` filter rather than per-class `@SuppressFBWarnings` flood.
- **D-17 trigger-equivalence (CI workflows):** For step-timing baselines, `workflow_dispatch` on milestone branch is semantically equivalent to post-merge master `push` because `ci.yml` runs identical steps.

### Key Lessons

- **Auto-Nyquist-debt scanning IS the v1.11 lesson:** Both v1.10 and v1.11 accumulated identical draft-VALIDATION patterns during execution. The lesson isn't "phases should close Nyquist inline" — it's that milestone-close audit is the right moment to surface and resolve it, and Option A inline closure is faster than cross-milestone phases (~3 hours for 6 phases + 1 retroactive VERIFICATION).
- **`feedback_no_local_git_tags` invariant validated:** Even though `/gsd-complete-milestone` workflow's `git_tag` step suggests local tagging, the user's CLAUDE.md feedback memory was right — CI release workflow handles tagging post-merge, no local tag should ever be created.
- **`gh api -F field[]=value` breaks in zsh** (new `feedback_gh_api_zsh_globs` memory): Brackets interpreted as globs. JSON via `--input -` heredoc is the robust form.
- **PERF-04 OR-branch is honest closure, not failure:** The requirement explicitly allowed "≥30% reduction OR architectural blocker documented with v1.12 forward path". Documenting the blocker (Spring-context-per-fork structural cost) + 3 prioritized levers + `PERF-FUTURE-01` tracking IS satisfying the requirement.
- **Synthetic deliberate-violation PRs are the gold-standard gate test:** STAT-06 (NP_ALWAYS_NULL throwaway), SAST-06 (java/sql-injection throwaway PR #128), DEPS-08 (Dockerfile-bump throwaway PR #126), Phase 78 (DockerfilePinGuardTest in-process duplicate) — each surfaced gate semantics issues that structural testing missed.

### Cost Observations

- Model mix: ~60% opus (audits, planner, gsd-nyquist-auditor agents), ~30% sonnet (executor + integration-checker agents), ~10% haiku (read-only reviewers)
- Sessions: 2 calendar days, dense — ~14 hours active orchestration across audit/closure/CI-fix cycles
- Notable: gsd-nyquist-auditor agents averaged ~3-5 min wall-time per phase (~200-300s) — fast because all 6 v1.11 phases had complete VERIFICATION.md + plan SUMMARYs already in place
- Notable: 8 atomic closure commits + 3 milestone-archive commits + 2 CI-fix commits = 13 commits in the inline-closure tail (16:00–16:38 on 2026-05-18)

## Milestone: v1.12 — Driver-Import Gap-Closure & Test Performance Round 2

**Shipped:** 2026-05-20
**Phases:** 4 (88-91) | **Plans:** 15 | **Timeline:** 2026-05-18 → 2026-05-20 (2 days · 117 commits · 128 files · +19,549 / −462 LOC)

### What Was Built
- **Phase 88 — Build/Release Unblockers + YAGNI + Doc-Conventions + Driver-Import Gap-Closure (6 plans):** CLEAN-01 verify-baseline (Phase-80 JDT-cache diagnosis), CLEAN-02 `@Disabled`/`Assumptions.` sweep, CLEAN-03 `SiteGeneratorBaselineRefresh` CommandLineRunner, REL-01 `release.yml` hardening (SemVer-strict + idempotency guard + parser + dry-run), REL-02 `docs/operations/release-runbook.md` operator runbook, DOCS-01 CLAUDE.md "Skill Invocation Naming" subsection + colon-form regression-fence, DRIV-01 season-aware `resolveTeamByShortName`, DRIV-02 `TabPreview.usesGroups` defensive future-proofing
- **Phase 89 — PERF Instrumentation + Lever 1 Per-Fork Backup-Staging-Dir (3 plans):** PERF-01 per-fork `app.backup.staging-dir` + `app.backup.import-backups-dir` + `app.upload-dir` via `${surefire.forkNumber}`, Failsafe `default-it forkCount=2 reuseForks=true`, `BackupStagingDirPerForkIT` + `BackupStagingCleanupRaceIT`, `ImportLockedPostRejectorIT` deadline bump + Javadoc; PERF-02 `ContextCacheKeyFingerprintListener` (`TestExecutionListener`) + `scripts/test-perf/aggregate-fingerprints.sh` + `docs/test-performance.md § PERF-02 Forensics`; Wave-4 local median **09:19** (−10.4 % vs Phase 86 10:24)
- **Phase 90 — PERF Consolidation + Module-Split Decision (3 plans):** PERF-03 composed `@CtcDevSpringBootContext` annotation across 19 outer classes (13 Surefire + 6 Failsafe), Surefire cluster `9cefac4c → baafff8e` collapse (29 events / 13 classes preserved); PERF-04 `.withReuse(true)` on both MariaDB ITs + `~/.testcontainers.properties` opt-in protocol; PERF-05 `docs/test-performance.md § Test-Module-Split Decision` verdict `defer` with 3 explicit blockers + v1.13 trigger; Wave-5 local median **08:27** (−9.3 % vs Phase 89 09:19)
- **Phase 91 — PERF Re-Harvest + Stretch UX-01 + Closer (3 plans):** PERF-06 CI 5-run `workflow_dispatch` median **17:39** (Δ−23.3 % vs v1.11 23:00 baseline; variance 18.2 % within D-10 tolerance); UX-01 sealed `GoogleApiException` hierarchy + 4 typed permits + `GoogleApiExceptionMapper` (13 unit tests) + categorized flash UX (`errorCategory` + 4 BEM `.error-badge--*` modifiers) + `docs/operations/google-integration.md` operator runbook; Plan 91-03 milestone closer (MILESTONES.md v1.12 entry, README pointers, PR #129 composite body, D-11 retroactive Nyquist sweep, Draft → Ready flip)

### What Worked
- **Pre-merge audit-driven discovery of REL-02 timing inversion:** The 2026-05-20 audit session caught that the original "post-merge operator action" framing in 88-VERIFICATION.md was logically wrong — if the v1.12 PR squash-merges first, the hardened workflow reads `v1.9.0` as last tag and mis-tags `v1.10.0` against the v1.12 HEAD commit + locks out the legitimate `--target 45aabfd0` retroactive tag via idempotency guard. Pinning the runbook to "pre-merge" (commit `871d42ff`) + actual execution before merge (`v1.10.0` + `v1.11.0` published, legacy tags deleted) closed the contract substantively.
- **Live runbook execution surfaced 3 documentation bugs that would have hit the next operator:** (a) `gh release create --target <SHORT_SHA>` returns HTTP 422 — full SHA required; (b) bash interactive `read -p` loop blocks in zsh because `-p` reads from a coprocess; (c) fine-grained PATs don't support `write:packages` for Personal Accounts — classic PAT required for `docker login ghcr.io`. All three patched in commit `1180a627` in the same session.
- **D-17 trigger-equivalence pattern (Phase 86 → Phase 91 re-use):** The PR-branch `workflow_dispatch` 5-run harvest produced the authoritative CI median (17:39) without needing a post-merge `docs(91):` commit on master — same pattern that closed Phase 86 PERF-04 in v1.11.
- **Composed annotation pattern for cache-key consolidation (Phase 90 PERF-03):** Phase 89's `ContextCacheKeyFingerprintListener` data identified the 19-class refactor surface upfront — Phase 90 then applied `@CtcDevSpringBootContext` to those exact classes and the `aggregate-fingerprints.sh` re-run empirically confirmed the `9cefac4c → baafff8e` cluster collapse. Instrumentation-driven targeted consolidation, not blind refactoring.
- **JDT-cache diagnosis carried forward as decision (CLEAN-01):** The `BackupSchemaExclusionIT.java:40` "Unresolved compilation problem" turned out to be a VS Code Eclipse-JDT cache stale-state, not a real Java 25 / AssertJ generic-inference issue. `./mvnw clean test-compile` from the project root proved the source compiles cleanly. The `[[clean-maven-build-authority]]` memory rule was reinforced — IDE caches are never the source of truth.

### What Was Inefficient
- **Phase 89 SUMMARY frontmatter shape divergence:** Phases 89/90 use `requirements:` while Phases 88/91 use `requirements-completed:` in plan SUMMARY frontmatter — the `gsd-sdk query summary-extract --fields requirements_completed` query only knows about `requirements-completed`, so 89/90 returned `[]` and required manual cross-referencing during the milestone audit. Single naming convention would simplify the 3-source cross-reference.
- **Audit-trail divergence (89/90/91-VERIFICATION.md missing):** v1.11 had VERIFICATION.md per phase (some retroactively authored via commit `2e84fd57`). v1.12 phases 89/90/91 close on VALIDATION.md + per-plan SUMMARY.md only — substantive verification IS present, but the audit-trail document shape diverges. Flagged as warning in the v1.12 audit; optional v1.13 retrofill recommended.
- **CLEAN-02 grep-predicate drift:** REQUIREMENTS.md asserted `grep -rn "Assumptions\." src/test/java | wc -l = 0`. Phase 88 verified ✓ on 2026-05-19. Phase 89 (PERF-01) then introduced `BackupStagingDirPerForkIT.java:12,37` using AssertJ's `Assumptions.assumeThat` — different package + intent than the JUnit Windows-conditional that CLEAN-02 originally targeted, but the grep can't distinguish. Single regression-fence predicate broke between phases of the same milestone. Tighter predicate (`org\.junit\.jupiter\.api\.Assumptions`) or explicit whitelist would prevent recurrence.
- **UX-01 scope-creep risk surfaced post-implementation:** Plan 91-02 explicitly scoped UX-01 to 2 controllers (`DriverSheetImportController` + `RaceController`). The integration audit then surfaced that `CsvImportController` (race-results sheet-import) is the THIRD consumer of the now-typed `GoogleSheetsService` API — currently catches the typed subtypes as plain `IOException`, emits generic flash without `errorCategory`, and re-introduces the T-91-02-IL info-leak (`e.getMessage()` echo) that UX-01 explicitly closed for the other 2 controllers. Deferred to v1.13; could have been caught at plan-time by enumerating ALL consumers of the typed-throws API surface.

### Patterns Established
- **Pre-merge operator-runbook execution pattern:** Where verification accepts an override as "post-merge", verify the assumption — sometimes the hardened workflow's behavior makes pre-merge execution REQUIRED, not optional. Run the live remote-state check (`gh release list`, `gh api /repos/.../git/refs/tags`) before deferring to post-merge.
- **Live execution as runbook validation:** Even a review-ready runbook surfaces bugs only when run end-to-end. Build 1× operator-execution into the milestone close cycle — patch discovered bugs in the SAME session to prevent the next operator from hitting them.
- **Composed `@CtcDevSpringBootContext` annotation for context-cache consolidation:** Replaces ad-hoc `@SpringBootTest(classes = CtcManagerApplication.class) + @ActiveProfiles("dev")` pairs across the codebase. Documented as the cluster-consolidation idiom for v1.13+ work. Javadoc forbids `@DirtiesContext` / `@DynamicPropertySource` / `@TestPropertySource` overlay (would defeat consolidation).
- **`feedback-no-flaky-dismissal` reinforced via Phase 89 FLAKE-DIAGNOSTIC:** Tests that previously passed and now fail are regressions, not "flaky". Phase 89 spawned a FLAKE-DIAGNOSTIC.md replan when an IT became unreliable under elevated `forkCount=2` — root cause (`ImportLockedPostRejectorIT` lock-acquisition timeout under `reuseForks=true + forkCount=2`) was investigated + fixed via named-constant + Javadoc + deadline bump, NOT vertagged.

### Key Lessons
1. **Memory rule [[no-local-git-tags]] beats workflow defaults:** `gsd-sdk milestone.complete` workflow Step 7 wants `git tag -a v[X.Y]` locally, but the project memory says no — CI Release-Workflow taggs post-merge. Workflows are general; memory rules are project-specific. Memory wins.
2. **The "operator action" override accepts the runbook, not the executed state:** Phase 88 SC#4 accepted REL-02 as "post-merge operator action" — but the override only documents that the phase ships a runbook, NOT that the artifacts are published. Two separate checkpoints: (a) runbook ships (verified Phase 88), (b) runbook executes (verified Phase 91 audit + operator session 2026-05-20). Audit must reconcile both.
3. **Fine-grained PAT vs Classic PAT distinction is operator-blocking, not just a doc note:** GitHub fine-grained PATs cannot do `write:packages` on Personal Accounts. `docker login ghcr.io` requires a separate Classic PAT with `write:packages` scope. The Phase 88 runbook initial draft said `gh auth login --scopes repo,write:packages` (a Classic-PAT flow) but did not flag that fine-grained PATs are insufficient — the next operator would burn ~10 min debugging HTTP 403 before discovering this. Section 1 now explicitly distinguishes the two token paths.
4. **Squash-merge subject discipline is a single-human-action gate between green PR and working release:** `release.yml` requires `^feat(\(.+\))?:` subject for the v1.12.0 minor bump. Without it: silent skip (no Conventional prefix) or wrong version (PATCH for `fix:`). Memory `[[feedback-squash-merge-message]]` records this; v1.12 closure made the lesson durable by codifying it in `docs/operations/release-runbook.md § 6`.

### Cost Observations
- Model mix: ~100 % opus (Claude Opus 4.7 1M-context — orchestrator + executor for all 4 phases; gsd-integration-checker subagent ran on Sonnet 4.6 per `gsd-sdk resolve-model` "balanced" profile)
- Sessions: 2 calendar days, dense — ~8 hours active orchestration across audit/closure/runbook-execution cycles
- Notable: 5 PERF-06 `workflow_dispatch` runs (~85 min total CI wallclock for harvest) + 1 `pull_request` validation run + 1 final pre-merge CI run = 7 CI cycles in the Phase 91 closer tail
- Notable: 4 doc-only post-Phase-91 commits between Plan 91-03 close and milestone-archive — `docs/operations/release-runbook.md` pre-merge timing pin (`871d42ff`), 3-bugs patch (`1180a627`), squash-merge subject discipline (`f854cbba`) — all surfaced by the audit + operator-execution loop in the same session

## Milestone: v1.13 — Discord Integration & Carry-Forwards

**Shipped:** 2026-05-28
**Phases:** 12 (92-103) | **Plans:** 43 | **Timeline:** 2026-05-20 → 2026-05-28 (8 days · 399 commits · 559 files · +93.8k / −1.2k LOC)

### What Was Built
- **Phase 92 — Carry-Forwards & Cleanup (4 plans):** UX-01 typed-catch + `errorCategory` badge parity on `CsvImportController` (closes T-91-02-IL info-leak invariant for all 3 Google-Sheets consumers); COV-01 JaCoCo recovery ≥ 88.88 % via `RaceControllerCalendarTest` + `GoogleSheets/CalendarServiceIT`; CLEAN-01 `@Disabled`/`Assumptions.` grep predicate tightened to `org.junit.jupiter.api.Assumptions` (closes the v1.12 inefficiency where Phase 89 AssertJ `Assumptions.assumeThat` false-triggered the JUnit-only fence); DOCS-01 retroactive 89/90/91-VERIFICATION.md; BOOK-01 11-marker flip in v1.12-REQUIREMENTS.md.
- **Phase 93 — Discord Foundation (3 plans):** `DiscordRestClient` (Spring `RestClient`, Bot-token, `/api/v10`) + `DiscordWebhookClient` (multipart-PATCH) + sealed `DiscordApiException` (4 permits — same Phase-91-`GoogleApiException` shape, with `CategoryFull` as Discord-specific permit) + `DiscordRateLimitInterceptor` (per-bucket token-bucket + 429 retry-after) + `DiscordTimestamps` (`<t:UNIX:STYLE>` for 5 styles) + `DiscordEmojiCache` (60-min TTL); threat-model surfaces (token env-var, SSRF whitelist, log-mask, CSRF, DTO); `/admin/discord-config` page on Flyway V8.
- **Phase 94 — Team Roles + Match Channel Lifecycle (4 plans):** Flyway V9 `teams.discord_role_id` + role dropdown; V10 `matches.discord_*` + scheduling fields + Match-Detail Create-Channel button with full permission-overwrite model + webhook + permission-audit assertion; Archive Modal with `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$` category regex + 50-channel-per-category limit.
- **Phase 95 — Match Channel Posts (4 plans):** Flyway V11 `discord_post` + `DiscordPostService.postOrEdit` idempotency dispatcher + 5 per-match post types (Team Cards auto-on-create, Settings, Lineups, Schedule embed with auto-edit hook, Match Results with stale-detection); new `data-incomplete` error category for pre-flight-gated posts.
- **Phase 96 — Provisional Graphic + Forum Threads (3 plans):** `ProvisionalScoresGraphicService` replaces manual sheet-screenshot; Flyway V13 `seasons.discord_*_thread_id` + Link-existing-Thread modal; race-result forum-thread post with `?thread_id=` query param + `unarchiveIfArchived`.
- **Phase 97 — Matchday-Level Posts (3 plans):** POST-06 Match Preview Announcement + auto-edit on streamLink/teaser/RD; POST-07a Match Day Results + POST-07b Power Rankings (2 independent Matchday-Detail buttons); POST-08 phase-aware Standings with new `StandingsGraphicService` + Flyway V14 `discord_post.phase_id` FK; dynamic-sizing Playwright graphic for 14+ teams.
- **Phase 98 — Polish + E2E + Docs + Close (7 plans):** `DiscordFullMatchdayLifecycleE2ETest` 8-stage WireMock-backed walkthrough; `docs/operations/discord-integration.md` operator runbook with Bot setup screenshots + OAuth URL generator + token rotation + troubleshooting; README + Wiki; mobile polish; POST-09 Matchday Pairings (Markdown + PNG); POST-10 Matchday Schedule (pure-multipart PNG).
- **Phase 99 — Pre-merge audit-polish (5 plans):** REQUIREMENTS.md Flyway-prose fix (POST-01 V11 → V12, FORUM-01 V12 → V13 + acceptance rewrite scoping out the unbuilt modal); ROADMAP v1.13 Progress refresh; retroactive 92/94/95/96/97/98-VERIFICATION.md retrofill; YAGNI delete of `DiscordRestClient.createThread()` + DTO + orphan IT.
- **Phase 100 — Match Day Channel Naming Scheme (3 plans):** `DiscordChannelService.channelName(Match)` extended to `md{N}-{phase}-[{group}-]{home}-vs-{away}` with `phaseAbbrev` (rs/po/pm) + `groupSlug` + 100-char overflow guard; 5 IT files refreshed; two-scheme coexistence acceptance (no migration for existing channels).
- **Phase 101 — Backup/Restore covers Discord schema V8-V15 (6 plans):** `SCHEMA_VERSION` 1 → 2 + lenient `IN (1, 2)` accept; package-filter expansion to `org.ctc.discord.model.*` (24 → 26-entity scope); 2 new MixIns + 2 new Restorers for `DiscordGlobalConfig` + `DiscordPost` (pinned-last for `@Column UUID` FKs); 4 existing Restorers extended for 13 V8-V15 columns; 13 per-field regression-fence + byte-equality round-trip on H2 + MariaDB.
- **Phase 102 — Code-Review Fixes (4 plans):** 67 review findings closed across Phases 92-101 (9 critical/blocker + 58 warning/info); 45 commits; controller-thin extract template (`MatchService.buildMatchDetailModel`, `DiscordSeasonViewService.buildDiscordIntegrationModel`, `DiscordMatchdayViewService.buildMatchdayDiscordModel`, `StandingsService.snapshotMatchdayStaleness`); lock-IT async-latch dance removal; close-loop Pass-2 CLEAN after 5 inline remediations.
- **Phase 103 — StringUtils Blank-Check Sweep (1 plan):** 85 occurrences of `s != null && !s.isBlank()` / `s == null || s.isBlank()` replaced with `org.springframework.util.StringUtils.hasText(s)` across 42 production files; new `config/rewrite-validate-hasText.yml` OpenRewrite `FindMethods` detector recipe as closing-validation oracle.

### What Worked
- **Scope-grew-by-design via mid-milestone audit cycles:** The original milestone-setup planned 7 phases (92-98). Phase 99 (pre-merge audit-polish), Phase 100 (channel-naming refinement), Phase 101 (backup wire-contract extension for Discord schema), Phase 102 (code-review-fix closeout), and Phase 103 (StringUtils.hasText readability sweep) all emerged from in-milestone audits rather than upfront planning. Each landed inline on the milestone branch, ratcheting v1.13 from 7 phases / 27 plans / 27 REQs to 12 phases / 43 plans / 28 REQs without a v1.14 carry-forward. The "in-milestone polish — no deferral across milestones" CLAUDE.md rule held perfectly.
- **Hybrid Bot + Webhook architecture chosen on Day 1 paid off:** Spring `RestClient` Bot for stateful CRUD (channels, roles, categories) + Webhooks for all message posting (per-team avatar identity + outbound-only deployment) avoided the always-online endpoint that a slash-command bot would force. Zero new production dependencies — Spring 6.1+ core `RestClient` + `MultipartBodyBuilder` covered everything. DISC-FUTURE-01 captures the future deployment-model change required if inbound interaction is ever wanted.
- **Sealed `DiscordApiException` shape reused from Phase 91 `GoogleApiException`:** 4 permits + Mapper helper + typed catches at controller boundary + `errorCategory` flash key + BEM error-badge — Phase 91 pattern transferred wholesale, only `CategoryFull` is Discord-specific. Phase 92 UX-01 carry-forward (close T-91-02-IL on `CsvImportController`) and Phase 93 INFRA-01 landed inside the same week — shared mental model across both controllers' UX.
- **`DiscordPostService.postOrEdit` single-method idempotency dispatcher generalizes across 11 post types:** Lookup-on-`(match_id, post_type)` (or `(matchday_id, post_type)` for matchday-level), then POST or Webhook-PATCH. Same code path for Team Cards, Settings, Lineups, Schedule, Match Results, Match Preview, Match Day Results, Power Rankings, Standings, Matchday Pairings, Matchday Schedule. Operator retries are idempotent; auto-edit hooks reuse the same PATCH path.
- **Iterative visual-approval design loop for graphics:** `StandingsGraphicService` (Phase 97 POST-08), `ProvisionalScoresGraphicService` (Phase 96 GRAFX-01), and Matchday Pairings PNG (Phase 98 POST-09) all went through small-commit iterations with user visual verification per [[feedback_graphic_design_iteration]]. Pixel-positioning + dynamic-sizing-for-14-teams + emoji-resolution all caught early instead of after-the-fact rework.
- **Phase 101 backup-schema fold-back caught silent-data-loss before merge:** Mid-milestone audit surfaced that v1.13 added 9 new Flyway migrations (V8-V16) but the Phase 72 backup wire-contract still only included `org.ctc.domain.model.*` — Discord entities + V8-V15 columns on `Match`/`Team`/`Matchday`/`Season` would silently NULL on restore. Package-filter expansion + Restorer extensions + 13 per-field regression-fence closed the gap before v1.13.0 shipped, not after.
- **WireMock vs Real-API discipline pinned via Phase 95 5-bug audit:** WireMock-friendly regex/payload assertions hid 5 production regressions caught only on live-Discord drift inside the same PR. CLAUDE.md "Build & Test Discipline" now records: regex/payload/URL changes in external-API code require a separate test pinning production format; WireMock stubs use `withQueryParam(...)` not just `urlPathEqualTo`; transactional auto-post hooks need real `@Transactional` proxy (never `@MockitoBean` the post service in ITs).
- **Phase 102 close-loop reviewer Pass-2 CLEAN after 5 inline remediations:** 67 findings → 4 plans → 45 atomic commits → close-loop `gsd-code-reviewer` Pass 1 surfaced 5 new items (3 warning + 2 info) → all 5 remediated inline → Pass 2 CLEAN. Pattern: re-review after closeout catches drift from the fix-PRs themselves, not just from the original phases.

### What Was Inefficient
- **Original 92-98 plan undersized the milestone:** Discord integration was scoped as "Foundation → Channel Lifecycle → Match Posts → Provisional + Forum Threads → Matchday Posts → Polish + Docs + Close". Mid-milestone audit + code-review surfaced 5 additional phases (99-103). Net 12-phase total vs 7-phase plan = +71 % phase count. Not necessarily wrong — the additional phases all landed inline and were necessary closures — but the original sizing under-counted the audit-driven discovery rate of a multi-week milestone.
- **Phase 102 `tasks_completed` frontmatter shape divergence (mirrors Phase 89 v1.12 finding):** SUMMARY frontmatter in Phase 102 used `tasks_completed:` (int), while Phases 92-101 use `requirements:` / `requirements-completed:` array. `gsd-sdk query summary-extract --fields one_liner` returned `"Goal:"` for 102-02/03/04 because the body-extraction heuristic falls back to the first `## ` heading when no `one-liner:` frontmatter key exists. v1.12 surfaced the same divergence in v1.13 retro now — single naming convention plus a `one-liner:` frontmatter requirement would close this for v1.14.
- **`v1.13-MILESTONE-AUDIT.md` evolved through 3 passes (initial verdict → tech-debt closure → post-Phase-102/103 close):** Audit doc moved 3 times as Phase 99 closed audit gaps, Phase 102 closed code-review gaps, Phase 103 added a phase. Each pass left CLOSED markers + cross-reference annotations. The final audit doc reads cleanly but the diff history shows the inversion. Pattern: re-audit at every phase that materially changes the milestone scope, not just at the start + close.
- **`createThread()` YAGNI removal as Phase 99 follow-up indicates v1.13 added speculative surface:** Phase 99-05 deleted `DiscordRestClient.createThread()` + `ThreadCreateRequest` DTO + orphan IT method that was never wired into a feature. Captured in audit as a separate plan because it touched test surfaces. Caused by Phase 96 FORUM-01 originally scoping a "Create new Thread..." modal that was descoped during implementation but left the API surface in place. Pattern: when descoping a feature, grep-all-usages and delete the unused surface in the same plan.
- **`98-AUTO-UAT.md` status frontmatter false-positive at milestone close:** `gsd-sdk audit-open` flagged Phase 98 AUTO-UAT as `status: unknown` despite all 7 scenarios passing — the file lacks an explicit `status:` frontmatter field. Resolved as no-op false-positive, but a 1-line frontmatter standard for AUTO-UAT.md (`status: passed|failed|partial`) would silence the audit cleanly.

### Patterns Established
- **In-milestone audit-driven phase insertion as default closure pattern:** v1.10 cross-milestone Phase 87, v1.11 inline Option A, v1.12 pre-merge runbook execution, v1.13 = 4 audit-driven phases (99, 100, 101, 102) + 1 readability sweep (103). Pattern continues to favor inline closure over `/gsd-new-milestone` re-spawn when the work is bounded.
- **Sealed exception hierarchy + Mapper helper + categorized flash UX is the cross-Phase reusable pattern for external-service-typed-errors:** v1.12 `GoogleApiException`, v1.13 `DiscordApiException`. 4 permits + Mapper + `errorCategory` flash key + BEM `.error-badge--{transient|auth|not-found|permission|category-full}`. Next external service (Google Calendar enrichment, YouTube API, etc.) should adopt the same shape verbatim.
- **`DiscordPostService.postOrEdit` idempotency dispatcher across 11 post types:** Lookup-on-identity-key, then POST-or-PATCH. Future content-type families (notifications, alerts, league announcements) should reuse the same pattern. Stored `message_id` is the integration's source-of-truth identity.
- **Iterative visual-approval design loop is now the convention for graphic services:** Small atomic commits with user-visual checkpoint per [[feedback_graphic_design_iteration]]. `ProvisionalScoresGraphicService`, `StandingsGraphicService`, `MatchdayPairingsGraphicService` all shipped through this loop. Pixel-positioning per [[feedback_graphic_pixel_positioning]] reinforced.
- **Package-filter expansion is the structurally identical fix for backup-scope addition:** Phase 72 `org.ctc.domain.model.*` → Phase 101 `org.ctc.discord.model.*` extension. No marker, no opt-in, no developer memory required — automatic at next-boot topo-sort. Future modules (e.g. `org.ctc.notifications.model.*`) follow the same pattern.
- **Two-scheme coexistence acceptance for channel naming (Phase 100):** Existing channels stay on old format, new channels use new format, no migration script. Acceptance documented in STATE.md Deferred Items. Pattern transferable to any externally-visible-identifier scheme change where back-fill is operator-disruptive.
- **`SCHEMA_VERSION` monotonic int + lenient `IN (allowed-set)` accept:** Phase 72 `SCHEMA_VERSION = 1` → Phase 101 `SCHEMA_VERSION = 2` with `IN (1, 2)` lenient accept. Pre-version backups remain restorable; new-version columns land NULL on old backup restore (self-healed on first operator action). Pattern: every wire-incompatible schema change bumps the int by 1, importer expands the accepted set.

### Key Lessons
1. **Milestone scope is a moving target — accept it explicitly.** Original 7 phases → final 12 phases is not a planning failure; mid-milestone audits surface real work. Lock the milestone branch on Day 1, lock the milestone PR (#130) on Day 1, then allow phase-additive insertions inside the branch. The milestone PR's rolling description becomes the moving scope-of-truth, not the original ROADMAP entries.
2. **WireMock is not real-API coverage.** Phase 95 surfaced 5 production regressions inside the milestone PR that all green WireMock-backed ITs missed. Regex/payload/URL changes in external-API code require a separate test pinning the production format. WireMock stubs use `withQueryParam(...)`, not just `urlPathEqualTo`. Transactional auto-post hooks need real `@Transactional` proxy — never `@MockitoBean` the post service in ITs. The 5 Phase-95 bugs drove CLAUDE.md "WireMock is not Real-API Coverage" + [[feedback_wiremock_vs_real_api]].
3. **Grep-all-usages before refactoring.** Phase 99-05 deleted dead `createThread()` surface that lingered from a descoped feature. Phase 100 IT-file sweep (5 files / 14 occurrences) caught all literal occurrences of old channel-naming format. Pattern: when removing/renaming a method, repository call, or pattern, grep the entire `src/` for the symbol AND for structurally similar copies. Phase plans must include an explicit "audit all usages" task.
4. **Code-review closeout deserves its own phase when finding count crosses ~50.** Phase 102 (67 findings, 45 commits, 4 plans, close-loop Pass-2 CLEAN) showed that bundling fixes into the originating phases would have churned the milestone PR with too many overlapping commits. Dedicated closeout phase = single source-of-truth, single close-loop reviewer pass, single set of regression-fence tests.
5. **Pre-merge audit-polish IS its own phase.** Phase 99 closed 5 tech-debt items from the mid-milestone audit before milestone close, not after. Doc-debt (retroactive VERIFICATION.md retrofill), prose drift (Flyway versions in REQUIREMENTS.md), descoped-feature cleanup (YAGNI createThread delete) all in one phase = audit verdict closes cleanly to zero gaps. Pattern: every audit verdict with `tech_debt: [...]` triggers a pre-close phase that drains the list.
6. **Frustration ≠ approval.** Mid-milestone the user pushed back on speed; the orchestrator kept open `AskUserQuestion` gates open until explicit-option-selection per CLAUDE.md "GSD Workflow Discipline". Codified in CLAUDE.md.
7. **`feat(v1.13):` squash-merge subject is mandatory for `v1.13.0` MINOR bump.** Memory [[feedback-squash-merge-message]] + CLAUDE.md "Git Workflow" + `docs/operations/release-runbook.md § 6` all converge on this. PR title alone is not sufficient — Semantic Release reads the squash subject only.

### Cost Observations
- Model mix: ~95 % opus (Claude Opus 4.7 1M-context — orchestrator + executor across all 12 phases); subagents for code-review (`gsd-code-reviewer` Pass 1 + Pass 2), nyquist auditing, integration-checking ran on Sonnet 4.6 per `gsd-sdk resolve-model` "balanced" profile
- Sessions: 8 calendar days, dense — ~30+ hours active orchestration across discuss/plan/execute/review/audit cycles
- Notable: 399 commits in milestone range (avg ~50/day) — high-frequency atomic commit discipline preserved across all 12 phases despite scope-additive insertions
- Notable: 45 commits in Phase 102 alone (9 critical + 58 warning + 52 info findings collapsed into 45 atomic-fix commits across 4 plans + close-loop reviewer Pass-2)
- Notable: 5 inline remediations in Phase 102 close-loop = ~30 min total wall-time turnaround from Pass-1 findings to Pass-2 CLEAN
- Notable: phase 101 backup-fold-back required `./mvnw clean verify -Pe2e` on H2 + opt-in MariaDB (`docker.available=true`) — ~12 min wallclock for the round-trip byte-equality validation across both DBs

## Milestone: v1.14 — Team Card Redesign & Data Safety

**Shipped:** 2026-05-29
**Phases:** 2 (104-105) | **Plans:** 5 | **Timeline:** 2026-05-29 (1 day · 51 commits · 80 files · +6.6k / −2.5k LOC)

### What Was Built
- **Phase 104 — Data Safety Lockdown (1 plan):** Reverted the v1.11 `@Profile({"dev","local"})` drift (commit `598d1431`) on `DevDataSeeder` + `TestDataService` back to `@Profile("dev")`, so the `local` profile — which binds to the real MariaDB — can no longer instantiate either test-data seeder. Added `LocalProfileDataSafetyIT` (`@ActiveProfiles("local")` + H2 in-memory override mirroring `SecurityIntegrationTest.ProdProfileSecurityTest`) that asserts both beans absent and goes red on any future `@Profile`-widening / `@ConditionalOnProperty`-flip / `@Component` re-introduction. `OpenSecurityConfig` deliberately stays `@Profile({"dev","local"})` — auth, not data; only data seeders were narrowed (SAFE-01/02).
- **Phase 105 — Carbon HUD Graphics Redesign (4 plans, wave-ordered):** All 16 Playwright-rendered admin graphics restyled to the external Claude-Design "Carbon HUD" Carbon/Gold system. Wave 1 — `TeamCardService` color-robustness patch (`accentVisColor` + `onPrimaryColor`, reusing existing `relativeLuminance`) + Carbon team-card template (CARD-01/02). Wave 2 — `ProvisionalScoresGraphicService` `raceLabel`-only-for->1-race conditional (both-branch IT) + 5 Carbon composite templates (`settings`/`lineup`/`results`/`match-results`/`provisional-scores`) (CARD-02/03). Wave 3 — 5 Carbon matchday/list templates with standings dynamic-row-height preserved (CARD-02/04). Wave 4 — Carbon stream overlay (geometry/skew/transparency byte-locked) + 4 non-handoff analogy rebuilds (`matchday-pairings` + 3 `playoff-round-*`) (CARD-02/04). All as drop-in template replacements — unchanged `th:*` bindings, no `GraphicService` signature or model-variable changes except the two named backend tweaks. Every card-consumer path (Discord auto-post POST-02 2-PNG multipart, manual Re-Post + Refresh PATCH, admin + template-editor previews) stayed backward-compatible with zero caller changes.

### What Worked
- **Two-pillar split was honest, not bureaucratic.** Data Safety (104) and Graphics Redesign (105) share no entities, services, templates, or migrations. Separating them gave each its own goal + end-of-phase verify gate, and let Phase 104 establish a clean green baseline on the milestone branch while Phase 105 waited on the external design handoff. Phase 104's fix was pre-staged in `stash@{0}` and shipped in ~25 min including the regression IT.
- **Mid-milestone scope expansion handled cleanly via D-01/D-03.** Phase 105 was originally team-card-only (CARD-01/02). When the Claude-Design handoff arrived as a coherent Carbon/Gold system for all 12 render-graphics, the operator deliberately expanded scope to all 16 graphics, adding CARD-03 + CARD-04 and retitling the phase "Carbon HUD Graphics Redesign" — rather than deferring the rest to v1.15. The 4 non-handoff analogy templates were folded in (D-06), not deferred, specifically to avoid a visible old/new style break when graphics post together. This is CLAUDE.md "In-Milestone Polish — No Deferral Across Milestones" applied at requirement granularity.
- **JaCoCo-excluded graphic services made the redesign coverage-neutral.** Because `TeamCardService` + all `*GraphicService` classes are already JaCoCo-excluded (runtime-Playwright can't be instrumented), the template/CSS redesign carried zero coverage risk. The only coverage-relevant additions were Phase 104's IT and Phase 105's color-robustness + raceLabel unit tests — all net-positive.
- **Iterative visual-approval design loop held (per [[feedback_graphic_design_iteration]] + [[feedback_visual_checkpoint_self_review]]).** Verification grouped by the 4 handoff groups, `playwright-cli` Desktop + Mobile against `design-handoff/screenshots/`, screenshots into `.screenshots/105-*/`, dev server via `./scripts/app.sh start dev` per [[feedback_dev_server_via_app_sh]]. 12/12 UAT + 16/16 AUTO-UAT.
- **Backward-compat promoted to a first-class requirement (CARD-02) forced explicit consumer verification.** Rather than leaving "don't break the callers" implicit, CARD-02 made the planner enumerate and verify each consumer path (auto-post AFTER_COMMIT hook, Re-Post/Refresh PATCH, admin preview, template-editor preview). The WireMock-backed POST-02 IT stayed green; the 2-PNG multipart contract held.

### What Was Inefficient
- **The exact same AUTO-UAT `status:` frontmatter false-positive that v1.13 flagged recurred — but this time it was root-fixed.** v1.13's retro recorded "98-AUTO-UAT.md status frontmatter false-positive… a 1-line frontmatter standard would silence the audit cleanly." v1.14's close hit it again on `105-AUTO-UAT.md` (no top-level `status:` field → `audit-open` reports `unknown`). This session added `status: complete` to the frontmatter AND discovered a second false-positive: the quick-task SUMMARY was named `260529-dc2-SUMMARY.md` while `audit-open` + the gsd-quick skill expect an unprefixed `quick/<dir>/SUMMARY.md`. Both root-fixed (status marker added; files renamed to canonical names) so the audit reports clean. The lesson the v1.13 retro flagged was not codified into a guard — it should be: AUTO-UAT.md needs `status:` and quick-task files need canonical names at creation time.
- **Three audit passes (16:00 → 16:40 → 17:05) to reach `passed`.** The first milestone audit found Phase 105 entirely unverified (`gaps_found`); the 16:40 re-audit (after `/gsd-verify-work` + `/gsd-auto-uat`) narrowed it to artifact hygiene; the 17:05 pass closed the remainder (105-VERIFICATION produced, Nyquist VALIDATION reconciled PARTIAL → COMPLIANT, preview-fidelity warning fixed in `5d621e51`). The redesign shipped before its verification artifacts were complete — the verify/validate/audit chain ran retroactively rather than inline at phase close.
- **Loose test-count numbers in SUMMARY prose disagreed across sources at close.** 105-04-SUMMARY quoted "Surefire 529 / Failsafe 115", the v1.13 baseline was "1752 + 526 + 115 = 2393", and stale on-disk reports showed yet another figure. Only the authoritative end-of-close `./mvnw clean verify -Pe2e` settled the real count. Free-text test counts in SUMMARY files are unreliable bookkeeping — the build is the source of truth.

### Patterns Established
- **Requirement-granularity scope expansion is acceptable mid-phase when it prevents a worse outcome (visual inconsistency).** Phase 105 D-01/D-03/D-06: expanding from 1 graphic to 16 (and folding in 4 non-handoff analogy templates) was the right call because a partial redesign would ship a visible old/new style break. Recorded as a Key Decision in PROJECT.md.
- **A redesign of JaCoCo-excluded surfaces is coverage-neutral by construction** — plan accordingly: the only test work is the non-excluded helper tweaks, not the templates themselves.
- **Pre-close artifact-audit false-positives should be root-fixed, not acknowledged.** When `audit-open` flags an item that is genuinely complete, fix the marker/filename so the audit reports clean — don't defer it to STATE.md "acknowledged" (which masks the real signal next milestone). v1.14 chose Resolve-first over Acknowledge-all.

### Key Lessons
1. **Codify the v1.13 retro lesson that recurred.** AUTO-UAT.md MUST carry a `status: passed|failed|partial|complete` frontmatter field, and quick-task PLAN/SUMMARY files MUST use canonical unprefixed names (`quick/<dir>/PLAN.md`, `quick/<dir>/SUMMARY.md`) — the dir name already carries the id. Both are what `gsd-sdk audit-open` and the gsd-quick skill read. A retro lesson that is not promoted to a guard re-occurs.
2. **Verify inline at phase close, not retroactively at milestone audit.** Phase 105 shipped before its VERIFICATION.md / VALIDATION.md existed; three audit passes were needed to reconcile. Front-loading `/gsd-verify-work` + Nyquist VALIDATION at phase close (as v1.12's strict Nyquist gate did) avoids the audit-cycle churn.
3. **Promote backward-compat to a first-class requirement when rewriting shared output.** CARD-02 forced explicit per-consumer verification of `TeamCardService`'s output contract. Any future rewrite of a service whose output has multiple consumers should make "no caller churn" a named requirement, not an implicit assumption.
4. **The build is the only authoritative test count.** SUMMARY-prose test numbers drift and disagree; reconcile MILESTONES.md / STATE.md baselines from the end-of-close `./mvnw clean verify -Pe2e` output, never from free-text.
5. **`feat(v1.14):` squash-merge subject is mandatory for the `v1.14.0` MINOR bump.** PR #131; release CI tags after merge per CLAUDE.md "No Local Git Tags".

### Cost Observations
- Model mix: ~100 % opus (Claude Opus 4.8 1M-context — orchestrator + inline-sequential executor across both phases per CLAUDE.md "Inline Sequential is the Default"); read-only verifier / integration-checker / audit agents on the "balanced" profile
- Sessions: 1 calendar day, dense — discuss/plan/execute for both phases + 3 audit passes + verify/validate retrofill
- Notable: 51 commits in a single day; graphics-redesign churn concentrated in Thymeleaf render-templates + `admin.css` (JaCoCo-excluded surface), so the +6.6k / −2.5k LOC delta is overwhelmingly template/CSS, not test or production-Java code
- Notable: the redesign itself was coverage-neutral (JaCoCo-excluded services) — the only coverage-relevant additions were the Phase 104 IT and Phase 105 color-robustness / raceLabel unit tests

## Milestone: v1.15 — CI Optimisation & Race/Match Defaults

**Shipped:** 2026-05-31
**Phases:** 6 (106, 108-112; 107 removed) | **Plans:** 21 | **Timeline:** 2026-05-30 → 2026-05-31 (2 days · 149 commits · 263 files · +16.5k / −337 LOC)

### What Was Built
- **Phase 106 — CI Pipeline Optimisation (4 plans, CI-01..06):** Path-aware `ci.yml` — a `dorny/paths-filter` `changes` job gates the expensive Maven/E2E/Docker steps while the 3 required checks (`build-and-test`, `dockerfile-noble-pin-guard`, `docker-build`) always report a status, so a docs-only PR never deadlocks (approach A); hard `paths-ignore` only on the non-required `codeql.yml` + `mariadb-migration-smoke.yml` (approach C); buildx Docker layer cache; single `clean verify -Pe2e`; pom.xml no-rerun build guard + flaky-test policy doc. Live-PR checkpoint: `build-and-test` 14:55 < 17:39 baseline.
- **Phase 108 — Missing-Driver n/a Rendering (3 plans, LINEUP-01..04):** Central `TEAM_DRIVERS = 6` constant pads Lineup + Scorecard/Results + Provisional-Scores graphics to 6 rows with an "n/a" placeholder + shared `.empty-slot` de-emphasis (fixing the Provisional graphic's missing-row inconsistency); `ScoringService` records 0 points / no position for a missing slot at save time — no template/controller fallback.
- **Phase 109 — Walkover Handling (5 plans, WO-01..04):** Flyway **V17** `Match.walkoverTeam` (additive, V1-V16 untouched, H2 + MariaDB); bye-analogous auto-win crediting the opponent the full team race score; `TeamStanding.hasWalkover` + visible "(w/o)" label in matchday-detail, public standings, and 3 graphics; admin match-edit walkover dropdown via `MatchForm.walkoverTeamId` + validating `MatchService.updateWalkover` (2 review passes resolved).
- **Phase 110 — Lobby Settings Graphic (5 plans, LOBBY-01..05):** New `LobbySettingsGraphicService` (Carbon-HUD, template-variable-driven, `extends AbstractGraphicService implements TemplateManageable`, JaCoCo-excluded); admin preview + PNG download; `DiscordPostType.LOBBY_SETTINGS` + `postLobbySettings` manual match-channel button; template-editor override tab. No new data model, no Flyway. Blocked on + unblocked by the external Claude-Design handoff (Phase-105 pattern).
- **Phase 111 — Log-Injection Remediation, CodeQL CWE-117 (3 plans, SEC-LOG-01..04):** Central `org.ctc.util.LogSanitizer` (strips CR/LF + control chars) wraps all 29 flagged user-controlled log arguments across 17 files + removes the ad-hoc `MatchdayService.safeWeekend`; CodeQL re-scan reports 0 open `java/log-injection` with **no** new `query-filters` suppressions (5 review findings fixed).
- **Phase 112 — Unused Import Cleanup & Regression Guard (1 plan, IMP-01..02):** OpenRewrite `RemoveUnusedImports` strips every unused import across `src/main` + `src/test` (import-only diff), then a `maven-checkstyle-plugin` `UnusedImports` + `RedundantImports` gate bound to `validate` (+ `checkstyle-gate-guard` drift guard) makes regression impossible, documented in CLAUDE.md.

### What Worked
- **Dropping a requirement when the codebase scout proves there's no real problem.** Phase 107 (Race/Match prefill) was removed during discuss, not forced: scoring is already inherited via `Matchday → SeasonPhase → RaceScoring`, `legs` is a `SeasonPhase` setting (a Race *is* a single leg), and Matchday has no scheduled date to inherit. RACE-01..03 dropped permanently (not backlogged), the number left as a gap. Building all three would have meant a schema change the roadmap excluded — the discuss-phase scout caught it before any code was written.
- **Single-point-of-change patterns paid off twice.** The central `TEAM_DRIVERS = 6` constant (108) means a future per-season `driverSlots` feature swaps in one place; the central `LogSanitizer` (111) closed 29 alerts from one utility. Both fix the root cause at one site rather than scattering the change across graphics/log call sites.
- **CI-first strand independence cleaned the pipeline before any Java landed.** Phase 106 touches only `.github/workflows/*.yml` + `pom.xml` — zero shared surface with the application strands — so running it first meant every subsequent feature phase ran on the optimised, path-aware pipeline.
- **Log-injection fixed at source, no suppressions.** All 29 CWE-117 alerts were eliminated by sanitising the taint at the call site (`LogSanitizer.sanitize(...)`), not by adding `query-filters` dismissals — the security posture genuinely improved (0 open alerts, 0 new suppressions) per the integration-checker re-scan.
- **LINEUP-before-WO graphic sequencing avoided template clobber.** Phase 108 established the n/a placeholder pattern on the shared graphic templates; Phase 109 added the "(w/o)" label on top of that stable baseline. Reversing the order would have risked clobber on the same Thymeleaf files (D-Graphic-Sequencing).

### What Was Inefficient
- **The same VERIFICATION.md / Nyquist backfill recurrence as v1.13 and v1.14.** The first milestone audit graded `gaps_found` — but for verification-rigor only, not missing functionality: 5 of 6 phases shipped without a `VERIFICATION.md`, Phase 108 had no `VALIDATION.md`, and 106/109 were `nyquist_compliant: false`. All were closable retroactively (validate-phase runs + verification backfill) and were closed the same day, but the verify/validate chain again ran *after* the phases shipped rather than inline at each phase close. This is now a three-milestone pattern (v1.13 → v1.14 → v1.15).
- **REQUIREMENTS.md traceability drift, again.** At audit time 22 of 25 active rows still read "Pending" though the work was complete and REVIEW-resolved; SUMMARY `requirements_completed` frontmatter was populated for only 2 of 6 phases. The bookkeeping-drift theme is now overdue for a tooling solution (auto-flip on phase-complete commit, or a strict frontmatter gate).
- **CI-01/CI-02 could not be empirically proven on the milestone PR.** `pull_request` path filters evaluate the cumulative base…head diff, which on PR #132 contains code — so the docs-only skip only manifests on a wholly-docs-only PR. The behaviour was accepted config-sound by inspection (throwaway PR deliberately skipped), tracked in `docs/ci/v1.15-open-verify.md` as non-blocking debt.
- **Two CI-only failures surfaced at close that the milestone audit's local `clean verify -Pe2e` could not see.** (1) `docker-build` was red the whole milestone: Phase 112's `validate`-bound Checkstyle gate needs `config/checkstyle.xml`, but the Dockerfile build stage copied only `mvnw/.mvn/pom.xml/src` — so `./mvnw package` inside the multi-stage build failed (`/build/config/checkstyle.xml` not found). The full-checkout `build-and-test` job passed, which is exactly why local `verify` and the audit missed it. Lesson: a new `validate`-phase plugin that reads a repo file must be matched by a Dockerfile `COPY` — the container build is a distinct verification surface from `mvnw verify`. (2) An intermittent `ConcurrentModificationException` in `BackupControllerTest` (pre-existing async-streaming/MockHttpServletResponse race) failed `build-and-test` once — not reproducible locally even at `@RepeatedTest(300)` because the window only opens under CI thread-scheduling. Per the user's directive, both were root-cause-fixed inside the milestone before merge (no retry/timeout hotfix); the flaky test was aligned to the `asyncDispatch` pattern its sibling tests already use.

### Patterns Established
- **Discuss-phase can DELETE scope, not only add it.** When the codebase scout shows a planned requirement maps to no real problem in the current data model, the right move is to drop it permanently (Phase 107 / RACE-01..03), leaving the phase number as a gap per the integer-phase policy — not to build a speculative feature or carry it as backlog.
- **Path-aware CI without touching branch protection (approach A+C).** Gate expensive steps *inside* the required jobs via a `paths-filter` `changes` job (so the required-check contract stays intact) and apply hard `paths-ignore` only to non-required workflows. A single aggregation-gate (approach B) would have forced a branch-protection reconfiguration — explicitly out of scope.
- **Central-constant / central-util as the default for cross-cutting changes.** `TEAM_DRIVERS` and `LogSanitizer` both made a many-site change a one-site change, and both leave a clean seam for the next evolution (per-season slots; additional sanitised call sites).

### Key Lessons
1. **Promote "verify + validate inline at phase close" to an enforced gate — the retroactive-backfill pattern has now recurred three milestones running (v1.13/v1.14/v1.15).** v1.12's strict Nyquist gate (D-11) shipped all 4 phases compliant; v1.15 reverted to backfill-at-audit. The orchestrator hand-carries the review → verify-work → validate chain (CLAUDE.md "Validate-Phase Before New Phase"), but it was not run at each phase close. Front-load it.
2. **The bookkeeping-drift tooling solution is now badly overdue.** REQUIREMENTS traceability + SUMMARY `requirements_completed` frontmatter drifted Pending-at-close in v1.0/v1.1/v1.2/v1.9/v1.11/v1.12/v1.15. A pre-commit auto-flip or strict frontmatter validation would end a theme that has cost cleanup time in seven milestones.
3. **A `gaps_found` audit verdict is not necessarily a functional gap.** v1.15's initial `gaps_found` was verification-rigor only — every requirement was WIRED with file:line, integration was 6/6 CONNECTED, 3/3 flows complete. Distinguish "definition-of-done met, artifacts missing" from "functionality missing" when triaging an audit.
4. **Security findings below the CI gate threshold still get fixed at source.** The 29 `java/log-injection` alerts were medium (below the 7.0 HIGH/CRITICAL gate, so they never blocked the build) — but were driven to 0 by source-fix rather than left as accepted noise. Below-threshold ≠ ignorable.
5. **`feat(v1.15):` squash-merge subject is mandatory for the `v1.15.0` MINOR bump.** PR #132; release CI tags after merge per CLAUDE.md "No Local Git Tags".

### Cost Observations
- Model mix: ~100 % opus (Claude Opus 4.8 1M-context — orchestrator + inline-sequential executor across all 6 phases per CLAUDE.md "Inline Sequential is the Default"); read-only verifier / integration-checker / validate / audit agents on the "balanced" profile
- Sessions: 2 calendar days; 6 phases through discuss/plan/execute + a same-day audit-gap-closure pass (VERIFICATION backfill + 3 validate-phase runs + traceability/roadmap sync)
- Notable: 149 commits over 2 days; the +16.5k / −337 LOC delta is feature-heavy (new graphic service + walkover scoring/standings/graphics + 29-call-site log sanitisation + whole-tree import cleanup), not a template/CSS churn like v1.14
- Notable: test count 2416 → 2472 (+56); coverage held ~89 % (gate 82 %) — the new `LobbySettingsGraphicService` is JaCoCo-excluded, so coverage came from walkover/missing-driver/LogSanitizer unit + IT tests

## Cross-Milestone Trends

| Metric | v1.0 | v1.1 | v1.2 | v1.8 | v1.9 | v1.10 | v1.11 | v1.12 | v1.13 | v1.14 | v1.15 |
| ------ | ---- | ---- | ---- | ---- | ---- | ----- | ----- | ----- | ----- | ----- | ----- |
| Phases | 5 | 10 | 4 | 2 | 15 | 9 | 8 | 4 | 12 | 2 | 6 |
| Plans | 12 | 20 | 5 | 4 | ~70 | 50 | 46 | 15 | 43 | 5 | 21 |
| Tests (start → end) | 628 → 753 | 753 → 820 | 832 → 852 | 1011 → 1064 | 1064 → 1258 | 1258 → 1652 | 1652 → 1675 | 1675 → 1696 | 1696 → 2393 | 2393 → 2416 | 2416 → 2472 |
| Coverage | 82%+ | 82%+ | 82%+ | 82%+ | 87.02% | 87.80% | 88.88% | 88.44% | **89.43%** | ~89.42% | ~89% |
| CI E2E baseline | n/a | n/a | n/a | n/a | n/a | n/a | 23:00 | **17:39** | 17:39 (held) | 17:39 (held) | path-aware; B&T 14:55 |
| Timeline (days) | 8 | 4 | 11 | 2 | 14 | 7 | 2 | 2 | 8 | 1 | 2 |
| Files changed | 68 | ~80 | ~15 | 39 | 567 | 521 | 718 | 128 | 559 | 80 | 263 |
| LOC delta | +3850 / -962 | +5100 / -1300 | +600 / -30 | +11.2k / -539 | +88.4k / -2.5k | +77.4k / -1.2k | +81.3k / -3.2k | +19.5k / -462 | +93.8k / -1.2k | +6.6k / -2.5k | +16.5k / -337 |
| Nyquist verdict at close | n/a | n/a | n/a | n/a | n/a | partial → compliant (via v1.11 Phase 87) | compliant 8/0/0 | compliant 4/0/0 | compliant 12/0/0 | compliant 2/0/0 | 5 compliant + 1 partial-by-design (backfilled at audit) |

### Recurring Themes

- **Bookkeeping-Drift in `requirements-completed` Frontmatter:** Milestones v1.0 / v1.1 / v1.2 / v1.9 / v1.11 / v1.12 alle mit Drift (v1.12: 7 of 15 REQ-IDs Pending in Traceability + 4 inconsistent checkbox/traceability states at milestone close — Plan 91-03 deliberately deferred per stale-state avoidance pattern, audit-flagged for post-merge cleanup). Überfällig für Tooling-Lösung (Pre-Commit-Hook auto-flip auf phase-complete commit OR Nyquist-Gate-style strict frontmatter validation).
- **Audit-getriebene Phase-Insertion:** v1.1 (Recovery 12-15), v1.2 (Phase 19), v1.9 (Phasen 62, 66, 67, 68, 70), v1.10 (Phase 78). v1.11 evolution: in-milestone Option A inline closure (post-audit), kein neuer Phase-Insert. v1.12 evolution: pre-merge operator-runbook execution + 3-bugs patch closes REL-02 substantively without a follow-up phase — pattern continues to favor inline closure over phase-insert when the work is doc-only.
- **Live-DB / Real-World UAT entdeckt Domain-Gaps:** v1.8 (DevDataSeeder Year-Collision), v1.9 (Phase-66-D-04 Domain-Violation + GAP-70-01), v1.10 (75-HUMAN-UAT Driver chip-order observation → QUAL-01 in v1.11). v1.12 adds: live runbook execution surfaces 3 documentation bugs (Short-SHA HTTP 422, bash `read -p` in zsh, fine-grained PAT limitation) that pure read-only audit could not catch.
- **Nyquist-Debt akkumuliert während Execution, wird beim Milestone-Audit sichtbar:** v1.10 (6/9 phases draft) → Phase 87 → v1.10 compliant; v1.11 (6/8 phases draft) → in-milestone Option A → v1.11 compliant. v1.12 was different — D-11 strict gate enforced `nyquist_compliant: true` at phase close from Phase 88 onwards; all 4 phases shipped compliant. Pattern shift: front-loading the Nyquist gate prevents the audit-cycle entirely.
- **Synthetic deliberate-violation PRs als Gold-Standard für Gate-Tests:** STAT-06 (Phase 81), DEPS-08 (Phase 84), SAST-06 (Phase 85), DockerfilePinGuardTest (Phase 87). v1.12 adds: REL-01 `workflow_dispatch -F dry-run=true` on PR branch (run 26080324918 success) — exercises version-determination + idempotency-guard logic in isolation BEFORE the post-merge real event. Same pattern: prove gate semantics, not just gate structure.
- **Single-human-action gates between green-PR-state and working-release-state:** v1.12 surfaced this explicitly via the squash-merge subject discipline. v1.11 master branch protection enable was similar. Pattern: even with full CI green, there's a final operator action that determines whether the release actually fires correctly. Codify in runbook + PR description checklist BEFORE the next milestone hits the same gate.
- **Sealed-exception hierarchy reuse across external-service boundaries:** v1.12 introduced `GoogleApiException` (4 permits + Mapper + categorized flash UX) for Google Sheets/Calendar. v1.13 replicated the shape verbatim with `DiscordApiException` (4 permits, +`CategoryFull` as Discord-specific). Pattern now durable: every new external service adopts the same 4-permit + Mapper + `errorCategory` flash key + BEM `.error-badge--*` shape, no improvisation. Reduces the per-service ramp-up cost.
- **Phase-additive scope-growth as default for non-trivial milestones:** v1.13 grew from planned 7 phases to shipped 12 phases via mid-milestone audit phases (99 doc-debt + 100 channel-naming refinement + 101 backup wire-contract + 102 code-review-fix closeout + 103 readability sweep). Each addition closed within the milestone branch, not deferred to v1.14. Pattern reinforces CLAUDE.md "In-Milestone Polish — No Deferral Across Milestones" with the strongest data point yet (5 in-milestone phase insertions).
- **WireMock-vs-Real-API discipline now permanent:** Phase 95's 5 production regressions caught only on live-Discord drift drove CLAUDE.md "WireMock is not Real-API Coverage" (Build & Test Discipline section). Regex/payload/URL changes in external-API code require separate production-format-pinning tests. WireMock stubs use `withQueryParam(...)`, never just `urlPathEqualTo`. Transactional auto-post hooks need real `@Transactional` proxy. Pattern will apply to any future external-service integration (DISC-FUTURE-01..05, Calendar enrichment, YouTube API).
