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

- **In-milestone Nyquist closure (Option A pattern):** Discovering during milestone audit that the milestone itself accumulated Nyquist debt (same shape Phase 87 just closed for v1.10) and resolving it inline same-day via 6 retroactive `/gsd:validate-phase` runs + 1 retroactive VERIFICATION.md — avoided creating a v1.12 carry-forward phase. Sets precedent for future milestones.
- **gsd-nyquist-auditor agent dispatched per phase with CI baseline as authority:** Each agent had ~7 minutes wall-time to confirm coverage against `CI run 26033853591` SHA `3590b3a7` + shipped code; 0 impl bugs surfaced across 14 audits (8 v1.10 phases + 6 v1.11 phases) confirming both milestones shipped with strong test coverage.
- **Synthetic deliberate-violation testing (STAT-06 + SAST-06 + DEPS-08):** Throwaway-branch deliberate-failure PRs surfaced semantic bugs that structural testing missed — SAST-06's gate-step `ref=` vs `pr=` query bug only manifested on a real PR-context alert. Exactly the failure mode SAST-06 was designed to catch.
- **CI dumpstream artifact as debugging tool:** Surefire fork-channel corruption (Playwright Chromium auto-download mid-test) diagnosed via the `2026-05-18T05-24-53_082-jvmRun2.dumpstream` artifact from a broken CI run — file pointed straight at the failure mode.
- **D-17 PR-branch CI harvest = post-merge master harvest:** Recognizing that `ci.yml` runs identical steps for `pull_request`/`push`/`workflow_dispatch` triggers allowed Phase 86 PERF-05 baseline harvest within the same PR (5 `workflow_dispatch` runs) — no orphan post-merge `docs(86):` commit needed.

### What Was Inefficient

- **Auditor agent ignored "do not modify VALIDATION.md" instruction (Phase 84, 85):** Despite explicit "write to /tmp only" instructions, the gsd-nyquist-auditor agents directly modified `84-VALIDATION.md` / `85-VALIDATION.md` files. Outcome was actually fine (consistent shape, fewer steps), but breaks the orchestrator-driven atomic-commit pattern. Future audits: either accept the agent's direct-modification pattern (simpler) or enforce instruction more strictly.
- **REQUIREMENTS.md bookkeeping drift (recurring theme — see Cross-Milestone Trends):** STAT-01..07 + PERF-01..05 (12 checkboxes) remained `[ ]` for ~3 days post-shipping despite work being green in CI; only the milestone audit surfaced the drift. Tooling solution (auto-flip on phase-complete commit hook) still overdue.
- **Phase 86 missed VERIFICATION.md creation (during execution):** No goal-backward verification artifact was created during Plans 86-01..86-06 execution, only at retroactive audit time. Phase plans should include explicit "generate VERIFICATION.md" task or `/gsd:verify-work` invocation.
- **gsd-sdk `milestone.complete` accomplishment extraction noisy:** SUMMARYs without `one_liner:` frontmatter field produce literal "One-liner:" placeholders in MILESTONES.md. Future: enforce `one_liner:` in SUMMARY template or filter placeholders in CLI.

### Patterns Established

- **In-milestone Nyquist closure path (Option A):** When a milestone audit surfaces draft VALIDATION.md across the milestone's own phases, run retroactive `/gsd:validate-phase` + `gsd-nyquist-auditor` per phase + Phase-86-style retroactive VERIFICATION.md inline same-day. Avoids cross-milestone carry-forward.
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

## Cross-Milestone Trends

| Metric | v1.0 | v1.1 | v1.2 | v1.8 | v1.9 | v1.10 | v1.11 |
| ------ | ---- | ---- | ---- | ---- | ---- | ----- | ----- |
| Phases | 5 | 10 | 4 | 2 | 15 | 9 | 8 |
| Plans | 12 | 20 | 5 | 4 | ~70 | 50 | 46 |
| Tests (start → end) | 628 → 753 | 753 → 820 | 832 → 852 | 1011 → 1064 | 1064 → 1258 | 1258 → 1652 | 1652 → 1675 |
| Coverage | 82%+ | 82%+ | 82%+ | 82%+ | 87.02% | 87.80% | 88.88% |
| Timeline (days) | 8 | 4 | 11 | 2 | 14 | 7 | 2 |
| Files changed | 68 | ~80 | ~15 | 39 | 567 | 521 | 718 |
| LOC delta | +3850 / -962 | +5100 / -1300 | +600 / -30 | +11.2k / -539 | +88.4k / -2.5k | +77.4k / -1.2k | +81.3k / -3.2k |
| Nyquist verdict at close | n/a | n/a | n/a | n/a | n/a | partial → compliant (via v1.11 Phase 87) | compliant 8/0/0 |

### Recurring Themes

- **Bookkeeping-Drift in `requirements-completed` Frontmatter:** Milestones v1.0 / v1.1 / v1.2 / v1.9 / v1.11 alle mit Drift (v1.11: STAT-01..07 + PERF-01..05 stale for 3 days) — überfällig für Tooling-Lösung (Pre-Commit-Hook auto-flip auf phase-complete commit).
- **Audit-getriebene Phase-Insertion:** v1.1 (Recovery 12-15), v1.2 (Phase 19), v1.9 (Phasen 62, 66, 67, 68, 70), v1.10 (Phase 78) — Pattern hat sich als wertvoller Mechanismus etabliert. v1.11 evolution: in-milestone Nyquist closure als Option A inline pattern (post-audit), kein neuer Phase-Insert nötig.
- **Live-DB / Real-World UAT entdeckt Domain-Gaps:** v1.8 (DevDataSeeder Year-Collision), v1.9 (Phase-66-D-04 Domain-Violation + GAP-70-01), v1.10 (75-HUMAN-UAT Driver chip-order observation → QUAL-01 in v1.11) — Lokale Dev-Profile decken nicht alle Pfade ab.
- **Nyquist-Debt akkumuliert während Execution, wird beim Milestone-Audit sichtbar:** v1.10 (6/9 phases draft) → Phase 87 → v1.10 compliant; v1.11 (6/8 phases draft) → in-milestone Option A → v1.11 compliant. Pattern reproduzierbar; Tooling-Verbesserung wäre nyquist-Status als Quality-Gate auf phase-complete commit (analog zu SpotBugs/CodeQL/JaCoCo gates).
- **Synthetic deliberate-violation PRs als Gold-Standard für Gate-Tests:** STAT-06 (Phase 81), DEPS-08 (Phase 84), SAST-06 (Phase 85), DockerfilePinGuardTest (Phase 87) — alle nutzen Throwaway-Branch oder In-Process-Duplikat um Gate-Semantik zu beweisen, nicht nur Gate-Struktur. Strukturelle Tests (`yq`/`actionlint`/`grep`) reichen nicht aus.
