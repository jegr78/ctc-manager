# Phase 102: Code-Review Fixes (v1.13 closeout) — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-28
**Phase:** 102-code-review-fixes
**Areas discussed:** Plan-Splitting Strategy, Info-Findings Scope, Refactoring-Style Warnings, Close-Loop Cadence

---

## Plan-Splitting Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| by-severity (Recommended) | Plan 102-01 = 9 critical/blocker mit Regression-Fence-Tests — kleinster Plan, höchstes Risiko, erstes Mergen. Plan 102-02 = 58 warning. Plan 102-03 = 52 info (Comment-Sweep + Dead-Code + Style). Plan 102-04 = /gsd-code-review 102 Close-Loop + Remediation. Sauberste Risiko-Stufung; jeder Plan hat klare Akzeptanz. | ✓ |
| by-phase | Plan 102-01 = Phase 92-95 Findings, Plan 102-02 = 96-98, Plan 102-03 = 99-101, Plan 102-04 = close-loop. Hält verwandten Code im selben Plan, vermischt aber Critical/Warning/Info — schlechtere Risiko-Stufung. | |
| by-domain | Plan 1 = Controller-Fixes (Bye-NPE, Permission-Audit, ErrorCategory), Plan 2 = Service-Fixes (AFTER_COMMIT-Lücken, Pre-Flight), Plan 3 = Test-Fixes (T-ALF, vacuous IT), Plan 4 = Comment-Sweep + Migration-Header, Plan 5 = close-loop. Mehr Plans, kleinste Cognitive Load pro Plan, aber Critical/Warning/Info bleibt vermischt. | |

**User's choice:** by-severity (Recommended)
**Notes:** Driver decision for D-01. 4 plans total (102-01 critical, 102-02 warning, 102-03 info, 102-04 close-loop). Risk-graded: highest-risk fixes land first in the smallest reviewable diff, with regression-fence tests clustered for pattern consistency.

---

## Info-Findings Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Alle 52 schließen (Recommended) | "Komplett sauberer Abschluss" verlangt es. Comment-Pollution + Dead Code + Flyway-Header-Blöcke sind durch CLAUDE.md ohnehin verpflichtend; Style-Items (Jackson-Annotations, markdown-link-escape) sind low-effort. | ✓ |
| Mechanical-only zwingend, Style judgment-by-judgment | Comment-Pollution + Dead Code + Flyway-Header zwingend; Style-Items pro Item entscheidbar mit STATE.md-Defer-Eintrag falls weglassen. Spart Aufwand, lässt aber Style-Drift offen. | |
| Touch-only | Info-Findings nur fixen wenn die Datei ohnehin in Plan 102-01 oder 102-02 geändert wird. Restliche Info-Findings deferred. Schneller, aber 'komplett sauber' wird verfehlt. | |

**User's choice:** Alle 52 schließen (Recommended)
**Notes:** Driver decision for D-02. Only acceptable closure-without-fix is "demonstrably inapplicable" judgment (e.g., legitimate non-obvious WHY-comment per CLAUDE.md) recorded in Plan 102-03 SUMMARY.md with one-line rationale. No finding is pre-emptively deferred to v1.14.

---

## Refactoring-Style Warnings (Controller-thin Verletzungen)

| Option | Description | Selected |
|--------|-------------|----------|
| Closen in 102 (Recommended) | MatchController.detail (40 Zeilen Model-Population) + SeasonController.populateDiscordIntegrationModel (45 Zeilen) + MatchdayController-Staleness-Helper (4 Helper, 60+ Zeilen) extrahieren in service methods. +30-60min pro Stelle, +tests bleiben grün (mechanisches Extract). Passt zu 'komplett sauber'. | ✓ |
| Surgical-Only | Nur nicht-strukturelle Warnings (Markdown-Link-Escape, @Size cap, doppelte resolveAnnouncementChannelId, postOrEdit SeasonRef phaseId drop). Controller-thin Verletzungen als deferred-with-STATE-Eintrag verschieben. Schneller, lässt aber bekannte Schuld stehen. | |
| Wrap into v1.14 tech-debt phase | Refactor-Warnings raus aus 102, dedizierte v1.14 mit Requirements-Liste. Sauberer Scope-Cut, aber widerspricht "Ende der geplanten Features" — v1.14 ist nicht geplant. | |

**User's choice:** Closen in 102 (Recommended)
**Notes:** Driver decision for D-03. Three refactor-extract targets: `MatchController.detail` → `MatchService.buildMatchDetailModel(matchId)`; `SeasonController.populateDiscordIntegrationModel` → `SeasonManagementService` or new `DiscordSeasonViewService` (planner decides); `MatchdayController` 4 staleness helpers + the controller-only `seasonTeamRepository` field → `StandingsService.snapshotMatchdayStaleness(...)` or fresh helper (planner decides). Existing tests stay green; each extracted method gets at least one new unit test asserting its boundary. Reuses v1.5 Phase 33 "Controller Cleanup" pattern (controller delegates to service returning view-model record).

---

## Close-Loop Cadence

| Option | Description | Selected |
|--------|-------------|----------|
| Per-Plan + End (Recommended) | /gsd-code-review 102 mit --files=<plan-diff> nach Plan 102-01 (critical) und nach 102-02 (warning) für frühes Feedback auf den jeweiligen Diff. Volle Phase-Review als Plan 102-04. Teurer in Agent-Tokens, höchste Sicherheit. Passt zu 'komplett sauber'. | ✓ |
| End-only | Ein /gsd-code-review 102 als Plan 102-04 (oder letzter Schritt). Falls Findings: Remediation-Sub-Plan. Wenig Token-Overhead, spätes Feedback wenn ein Critical-Fix kollateral Probleme erzeugt. | |
| Mid-phase Spot-Checks | Statt vollständigen Review-Passes: manuelle Spot-Checks mit grep/Read auf die T-91-02-IL Invariant, Bye-Pattern, AFTER_COMMIT-Annotation. Schnellster, aber kein Verlassen auf das Reviewer-Agent-Auge. | |

**User's choice:** Per-Plan + End (Recommended)
**Notes:** Driver decision for D-04. Per-plan review runs on the plan's touched-file subset (`--files=<comma-separated>`) immediately after `./mvnw verify` green, BEFORE plan SUMMARY.md commit. Plan 102-03 (info sweep) skips per-plan review (mechanical, near-zero regression risk; covered by Plan 102-04 full pass). Plan 102-04 reviews the full Phase-102 cumulative diff and handles cross-plan interactions; remediation happens inline in 102-04 (not a new sub-plan).

---

*Phase: 102-code-review-fixes*
*Discussion: 2026-05-28*
