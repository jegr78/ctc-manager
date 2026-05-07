# Phase 64: Nyquist Validation Sweep — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-07
**Phase:** 64-nyquist-validation-sweep
**Areas discussed:** Plan-Struktur & Parallelisierung, Gap-Fill-Policy, Reconstruction-Tiefe für 56 + 59, Audit-Trail & Commit-Kadenz

---

## Plan-Struktur & Parallelisierung

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Plan pro Phase (6 Pläne) | 64-01..64-06, eine Phase je Plan. Maximale Atomarität, sauberer Commit-Trail, parallelisierbar in Wellen. Matches ROADMAP-Hinweis 'one task per phase; can be parallelized in waves'. | |
| 3 Pläne nach State-Typ | 64-01: State-B-Reconstruct (56+59); 64-02: State-A-Audit (57+60+62); 64-03: State-A-Light (58). Weniger Pläne, größere Tasks. | |
| 1 Bulk-Plan, 6 Tasks | Einzelner 64-01-PLAN.md mit 6 Tasks (Task = ein Phase-Audit). Minimaler Plan-Overhead, alle Phasen in einem Plan. | ✓ |

**User's choice:** 1 Bulk-Plan, 6 Tasks
**Notes:** Bewusste Abweichung vom Phase-63-Pattern (3 Pläne). Begründung implizit: Phase-Scope ist rein bookkeeping, alle Tasks nutzen dasselbe Toolset (`/gsd-validate-phase` retroactive), Plan-Overhead lohnt nicht. Trotz Bulk-Plan bekommt jede Task ihren eigenen VALIDATION.md Output.

---

## Gap-Fill-Policy bei MISSING-Findings

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-61-Präzedenz: Manual-Only mit Rationale | Keine neuen Tests. Bestehende Infrastruktur als Proof anerkennen. Echte Gaps werden in Manual-Only mit explizitem Why-Manual-Rationale eskaliert. Schnellster Pfad. | |
| Auto-fill mit Auditor-Subagent | Auditor generiert neue Tests für alle MISSING-Gaps. Maximale Coverage, aber Phase 64 mutiert zu echtem Code-Phase. | ✓ |
| Hybrid: Auto-fill nur für triviale Gaps | Echte Coverage-Lücken auto-fill, symbolische Gaps nur Map updaten. Schwellwert: 'fillable in single test method without impl change'. | |

**User's choice:** Auto-fill mit Auditor-Subagent
**Notes:** Bewusst gewählt gegen das schnellere Phase-61-Pattern. Begründung: User will v1.9 mit echter Coverage-Compliance schließen, nicht nur mit Bookkeeping-Compliance. Phase-Scope expandiert formal von "bookkeeping only" zu "bookkeeping + bedingte Test-Generierung". Subagent-Disziplin (CLAUDE.md) ist bindend: opus/sonnet, Branch-Schutz, Post-Dispatch-Validierung. Bei max 3 Auto-fill-Iterationen + Fail → Eskalation zu Manual-Only mit Why-Manual-Rationale (Phase-61-Fallback).

---

## Reconstruction-Tiefe für 56 + 59

| Option | Description | Selected |
|--------|-------------|----------|
| Voll Phase-61-Tiefe | Per-Task Verification Map mit allen REQ-IDs zu konkreten Test-Files+Line-Range gemappt, alle Test-Klassen aus SUMMARYs gelistet, Net-new Test-Infra inventarisiert, Validation Audit Tabelle. Hoher Aufwand, Gold-Standard. | ✓ |
| Mittel: nur SUMMARY-deklarierte Tests | Per-Task Map nur für Tests in PLAN.md/SUMMARY.md `requirements:` Frontmatter zitiert. Tests ohne Plan-Zitat werden nicht rückwirkend gesucht. | |
| Minimal: Frontmatter + 1-Liner-Audit | Nur Flags + 'Existing test infrastructure covered all phase requirements' + Validation Audit Tabelle. Schnellster Weg. | |

**User's choice:** Voll Phase-61-Tiefe
**Notes:** Auch für State-A-Updates (57, 60, 62) — bestehende Per-Task Maps werden aufgefüllt statt verkürzt. Phase 58 bleibt minimal (State-A light), da bereits `nyquist_compliant: true`. Phase 61 ist explizit als Template-Vorlage benannt.

---

## Audit-Trail

| Option | Description | Selected |
|--------|-------------|----------|
| Phase-61-Stil Validation Audit Block | Jede VALIDATION.md bekommt unten ein '## Validation Audit YYYY-MM-DD' Block mit Tabelle: Requirements audited, Plans audited, Gaps found, Resolved, Escalated, Net-new infra, Verdict. | ✓ |
| Nur Sign-Off-Checkliste flippen | Bestehende 'Validation Sign-Off' Checkliste mit `[x]` versehen + 'Approval: approved' setzen. Kein neuer Block. | |

**User's choice:** Phase-61-Stil Validation Audit Block
**Notes:** Ohne diesen Block gilt eine VALIDATION.md als unvollständig in Phase 64.

---

## Commit-Kadenz

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Commit pro Task (6 Commits) | Pro Phase-Audit ein Commit. Atomic commits matching Phase 61/63 Cadence, GSD-SDK commit-after-task default. Bei Auto-fill zusätzliche test()-Commits. | |
| 1 Bundle-Commit pro Plan | Alle 6 VALIDATION.md updates in einem Commit. Schneller, aber schlechter für selektives Revert. Bei Auto-fill: separater test()-commit. | ✓ |

**User's choice:** 1 Bundle-Commit pro Plan
**Notes:** `docs(64): nyquist validation sweep — 6 phases retroactive`. Falls Auto-fill Tests generiert: vorher separater `test(64): add Nyquist gap-fill tests` Commit (damit der docs-Commit auf grünen Tests basiert). Plan-Summary-Commit (`docs(64-01): plan summary`) am Ende.

---

## Claude's Discretion

- Exakte Reihenfolge der Tasks innerhalb des Plans (light → State-A → State-B als Vorschlag).
- Konkrete Wording der Phase-spezifischen Validation Audit Tabellen (Metric-Namen / Formulierung).
- Ob beim State-B-Reconstruct (56, 59) zuerst eine Skelett-VALIDATION.md aus dem Template gelesen wird oder die bestehende Phase-61-Datei direkt als Vorlage referenziert wird.
- Aggregations-Ebene der Cross-Reference-Links (file:line vs. file-only) bei sehr großen Test-Klassen-Mengen.

## Deferred Ideas

- **Phase 65 (Graphics-Bridge-Migration)**: eigene Phase im ROADMAP, nicht Teil von 64.
- **`/gsd-validate-phase` Workflow-Bulk-Modus**: Beobachteter Friction-Point bei der Plan-Struktur-Diskussion. Falls verfolgt: über Backlog.
- **Re-Audit von Phase 61**: bereits approved 2026-05-02, nicht in Phase 64.
- **JaCoCo-Threshold-Anhebung über 82%**: bei 85.17% Headroom theoretisch möglich, nicht in Phase 64.
