# Phase 83: Quality and Polish Sweep - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 83-quality-and-polish-sweep
**Areas discussed:** QUAL-01+04 (Ordering/Service-Cut), QUAL-02 (Profile-Widening), QUAL-03 (Per-Group UI), QUAL-05 + Commit/Branch-Strategie

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| QUAL-01 + QUAL-04 Ordering/Service-Cut | Wie wird Year-Order erzwungen? @OrderBy vs. Service-Layer-ViewObject. Items hängen eng zusammen. | ✓ |
| QUAL-02 Profile-Widening Scope | DevDataSeeder allein, oder auch DemoDataSeeder? local-Profile bekommt Site-Generation/TeamCards. | ✓ |
| QUAL-03 Per-Group UI-Shape | Group-Dropdown im Form, per-Group-Cards, oder Single-Form auto-detect? | ✓ |
| QUAL-05 + Commit/Branch-Strategie | UAT-02 Pass/Fail-Tracking? Pro QUAL-ID ein Commit? Branch-Name? | ✓ |

**User's choice:** Alle vier Bereiche selektiert (multiSelect).

---

## QUAL-01: Year-ASC-Order auf Driver-Detail-Chips

| Option | Description | Selected |
|--------|-------------|----------|
| @OrderBy auf der Relation | `@OrderBy("season.year ASC, season.startDate ASC")` auf Driver.seasonDrivers. Minimal-invasiv, JPA-managed, matched ROADMAP-Wording. | ✓ |
| Service-Layer DTO mit explizitem Query | Neuer DriverDetailViewService gibt DriverDetailView-Record zurück. Mehr Code, aber konsistent mit QUAL-04 Pattern. | |
| @OrderBy + zusätzlicher Repository-IT | Wie Option 1, aber mit ausdrücklichem Repository-IT, der SQL-Order verifiziert. | |

**User's choice:** @OrderBy auf der Relation (Recommended).
**Notes:** ROADMAP-SC#1 verlangt sowohl Repository-IT als auch Playwright-Smoke unabhängig von der Implementierungsoption — die Tests werden also so oder so geplant. Festgehalten in D-04/D-06/D-07.

---

## QUAL-04: Service-Layer-Refactor-Scope für StandingsController

| Option | Description | Selected |
|--------|-------------|----------|
| Nur groups-Access kapseln | Neuer StandingsService.loadGroupsForPhase(phaseId) oder SeasonPhaseService.findGroups(phaseId). Tight scope. Andere Lazy-Stellen bleiben. | |
| Komplettes StandingsView-DTO | StandingsViewService.buildView(...) gibt StandingsView-Record zurück (groups, standings, ranking, flags). Controller ultra-thin. Größere Surface. | ✓ |
| Groups + andere Lazy-Stellen scan first | Erst grep nach Lazy-Pfaden, dann je nach Befund Option 1 oder 2. | |

**User's choice:** Komplettes StandingsView-DTO.
**Notes:** Festgehalten in D-21..D-25. Planner-Discretion (D-23) ob standings.html template umgeschrieben wird auf ${view.X} (Option b) oder ob Controller die view flach auf Model-Attribute unfurled (Option a). Option (b) ist sauberer aber größerer Diff.

---

## QUAL-02: Profile-Widening-Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Nur DevDataSeeder → {dev,local} | Eine Annotation, idempotenter seed(), Site-Generation läuft mit. Matched ROADMAP-Wording 1:1. | |
| DevDataSeeder + Site-Generation-Skip auf local | {dev,local}-widening, aber Site-Generation nur für dev. | |
| DevDataSeeder + Saison-2023-Fixture-Path eliminieren | Annotation widening + alle Saison-2023-spezifischen Fixture-Pfade entfernen. | ✓ |

**User's choice:** DevDataSeeder + Saison-2023-Fixture-Path eliminieren.
**Notes:** Scout in TestDataService.java zeigte: die 2023-Refs (Zeilen 170, 325-331, 473-485, 925-948) sind der KANONISCHE Seed, NICHT ein separater Saison-2023-Bootstrap-Path. Die ROADMAP-SC#2-Formulierung "ohne separaten Saison-2023-Fixture-Path" bezieht sich auf einen EXTERNEN Bootstrap (z.B. Operator-manuelles Anlegen via Admin-UI / SQL auf local), der durch das Annotation-Widening eliminiert wird. Festgehalten in D-09..D-15. Researcher soll trotzdem README + docs + data/local/ + import.sql nach externen Bootstrap-Artefakten greppen (D-11).

---

## QUAL-03: Per-Group-UI-Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Group-Dropdown im bestehenden Form | <select name="groupId"> conditional auf phase.layout==GROUPS. Eine Form, ein Submit, minimal-invasiv. | ✓ |
| Per-Group-Card auf Season-Detail mit eigenem Generate-Button | Bei GROUPS-Layout eine Card pro Group. Bessere Sichtbarkeit, mehr Template-Arbeit. | |
| Generate-All-Groups Button + per-Group Override | Single-Button generiert für alle Groups; optional Dropdown für Override. Komfortabelste UX, Service-Method-Erweiterung nötig. | |

**User's choice:** Group-Dropdown im bestehenden Form (Recommended).
**Notes:** Festgehalten in D-16..D-20. Form-Feld bleibt hidden/null für SINGLE-Layout, call-site unverändert. Playwright-E2E auf Season 2023 (Group A + B).

---

## QUAL-05: UAT-02-Tracking

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-83 dokumentiert Test-Script + leerer Slot im milestone-audit | docs/uat/UAT-02-legacy-season-smoke.md + leerer Result-Slot. Phase-83 closes ohne Live-UAT, Operator füllt Slot nach Production-Deploy. | ✓ |
| Phase-83 inkl. Production-UAT-Execution (blockiert Phase-Close) | Phase wartet auf v1.11 Production-Deploy, dann manuelle UAT-02. Blockiert Milestone-Pipeline. | |
| UAT-02 in eigene Mini-Phase 88 auslagern | Phase-83 closed mit QUAL-01..04, UAT-02 wird Phase 88. ROADMAP-Anpassung. | |

**User's choice:** Phase-83 dokumentiert Test-Script + leerer Slot im milestone-audit (Recommended).
**Notes:** Festgehalten in D-26..D-29. Phase-83-Close-Criterion = procedure-doc + result-slot existieren. Live-Execution post-deploy. Location-Choice (STATE.md vs. dedizierte v1.11-UAT-02.md) ist Planner-Discretion.

---

## Commit-Choreographie und Branch

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Commit pro QUAL-ID + Wave-Gruppierung | 5 Atomic Commits + 1 Verification-Commit. Branch: feature/quality-and-polish-sweep off origin/master. | ✓ (Commit-Strategie) ✗ (Branch) |
| 1 Plan = 1 PR-Branch | Jede QUAL-ID eigener Feature-Branch + Sub-PR. | |
| Wave-Gruppierung nach Risiko | Wave 1: QUAL-02/05 (Annotation/Docs). Wave 2: QUAL-01/03 (Driver/UI). Wave 3: QUAL-04 (Service). | |

**User's choice:** Option 1 für die Commit-Choreographie (1 Commit pro QUAL-ID), ABER explizite Korrektur zum Branch: "auf aktuellem Branch bleiben, keine neuen Branches/PRs für diesen Meilenstein anlegen". User-Frage: "woher dieser Vorschlag schon wieder kommt".
**Notes:**
- Quelle des fehlerhaften Branch-Vorschlags rekonstruiert: Phase 80-CONTEXT.md + Phase 82-CONTEXT.md (D-03) enthielten beide "Branch: feature/<slug>"-Vorschläge — historische Fehler, kein Vorbild.
- Memory `feedback_milestone_branch.md` war eigentlich eindeutig ("Keine per-Phase feature/...-Branches anlegen"), wurde aber ignoriert weil Phase-CONTEXT-Dokumente als "Precedent" missgelesen wurden.
- Memory aktualisiert (2026-05-17): Phase-CONTEXT.md-Dateien sind KEINE autoritative Quelle für Branch-Strategie; discuss-phase darf KEINEN feature/-Branch mehr als Option anbieten.
- D-01/D-02 in CONTEXT.md halten die Korrektur fest: alle Commits direkt auf `gsd/v1.11-tooling-and-cleanup`, kein Sub-Branch, kein Sub-PR.

---

## Claude's Discretion

Areas vom User explizit oder implizit dem Planner überlassen (vollständige Liste siehe CONTEXT.md `### Claude's Discretion`):

- QUAL-01: code + test commit zusammen oder split
- QUAL-02: optionaler `DevDataSeederLocalProfileIT` oder Drop
- QUAL-03: GET-handler `findRegularPhase` vs. neue `findRegularPhaseWithGroups` (OSIV-abhängig)
- QUAL-04: Option (a) Controller-unfurl vs. Option (b) Template-rewrite auf ${view.X}
- QUAL-04: `StandingsControllerIT`-Updates als separater Commit oder gemeinsam mit refactor-Commit
- QUAL-05: Result-Slot in STATE.md oder dedizierte `.planning/milestone-audits/v1.11-UAT-02.md`

## Deferred Ideas

Vollständige Liste siehe CONTEXT.md `<deferred>` Block. Kurzfassung:

- Broader OSIV-cleanup in anderen Controllers (v1.12+)
- QUAL-03 Generate-All-Groups Shortcut (v1.12+ feature)
- Staging-Environment-Automation für UAT-02 (zukünftiges Infrastruktur-Topic)
- Generisches `@OrderBy`-Coverage-Pattern (single-use heute)
- DemoDataSeeder Profile-Widening (explizit out-of-scope für Phase 83)
- Template-rewrite auf `${view.X}` falls QUAL-04 Option (a) gewählt wird (v1.12 cleanup)
