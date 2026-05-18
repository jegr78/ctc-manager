# Phase 87: Nyquist VALIDATION Closure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 87-Nyquist VALIDATION Closure
**Areas discussed:** Directory restoration approach, Gap-coverage Aggressivität, Plan-Struktur & Commit-Granularität, VALIDATION.md Dokument-Shape

---

## Directory restoration approach

### Where the v1.10 phase artefacts live

| Option | Description | Selected |
|--------|-------------|----------|
| Archiv unter milestones/v1.10-phases/ | Erstelle `.planning/milestones/v1.10-phases/<n>-<slug>/` pro Phase, restore PLAN+SUMMARY+VALIDATION-Draft aus `60f5f915^`. Matched v1.0-v1.9 Archiv-Pattern. | ✓ |
| Temporär unter `.planning/phases/` | Restore unter aktuellem phases/, am Ende per milestone-complete archivieren. Kollidiert mit aktivem v1.11-Phase-Set. | |
| Lightweight: nur VALIDATION.md | Kein PLAN/SUMMARY-Restore. Aus git-log/git-show rekonstruieren, neue Location milestones/v1.10-validation/. /gsd:validate-phase müsste angepasst werden. | |

**User's choice:** Archiv unter `.planning/milestones/v1.10-phases/<n>-<slug>/` (matches established convention).

### How much to restore per phase

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal: nur was /gsd:validate-phase braucht | PLANs + SUMMARYs + CONTEXT + VERIFICATION + optional RESEARCH + draft VALIDATION. Lean Branch. | ✓ |
| Vollständig | Alles aus dem 60f5f915-Stand (PLAN/SUMMARY/CONTEXT/VERIFICATION/RESEARCH/PATTERNS/REVIEW/DISCUSSION-LOG/VALIDATION). Großer Diff. | |
| Nur PLAN + SUMMARY + alte VALIDATION | Reduzierte Pflichtmenge. CONTEXT/VERIFICATION/RESEARCH bleiben weg. | |

**User's choice:** Minimal restore scope.
**Notes:** Restore source = `git show 60f5f915^:<path>`. Slugs preserved verbatim from git history; cross-references in existing commit messages must continue to resolve.

---

## Gap-coverage Aggressivität

### Wie aggressiv Tests generieren

| Option | Description | Selected |
|--------|-------------|----------|
| Selektiv: nur HIGH-Risk Gaps | Auditor identifiziert alle, aber nur Security/Transaction/FK/Auth gefüllt. LOW/INFO → Manual-Only. | |
| Aggressiv: jeder Gap wird gefüllt | Jeder identifizierte Gap bekommt einen Test. Max Nyquist, Wallclock-Risiko. | ✓ |
| Dokumentation-only | Keine neuen Tests; nur retroaktives Mapping + accepted residual risk. | |

**User's choice:** Aggressive — fill every gap.

### Wallclock-Schutz

| Option | Description | Selected |
|--------|-------------|----------|
| Soft-Cap +60s pro Phase | Hard limit pro Phase, sonst Reduktion auf HIGH gaps. | |
| Hard-Cap +30s pro Phase | Strikter Stop-Mechanismus. | |
| Kein Cap, aber finale Verifikation | Aggressive Generierung läuft frei; am Phase-87-Ende vergleicht Verifier wallclock vs. Phase-86-Baseline. Bei >5% Regression: Tidy-up. | ✓ |
| You decide | Claude wählt nach Auditor-Output. | |

**User's choice:** Kein Cap, finale Verifikation.

### JaCoCo-Schutz

| Option | Description | Selected |
|--------|-------------|----------|
| Coverage darf nur steigen | Nie unter 87.80% drücken. | |
| 82% gate-only | Nur das harte pom.xml-Gate gilt; Bewegung zwischen 82% und 87.80% akzeptiert. | ✓ |
| Pro Phase JaCoCo-Snapshot | Per-package Messung, global ≥1% Verbesserung erforderlich. | |

**User's choice:** 82% gate-only.

### Impl-Bug Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Eskalieren, nicht fixen | v1.10-Code bleibt unangetastet; Bugs als 'finding' in VALIDATION + Backlog-Eintrag. | |
| Trivial-Fix erlaubt | ≤5 LOC isoliert fixen, sonst eskalieren. | |
| Alles fixen | Jeder gefundene Bug in Phase 87 fixen. Hoher Aufwand-Faktor, aber wirklich-abgeschlossen. | ✓ |

**User's choice:** Alles fixen.
**Notes:** Captured guardrail: if a fix is non-trivial (>~50 LOC or multi-file), orchestrator pauses for user decision rather than auto-fixing (D-08).

---

## Plan-Struktur & Commit-Granularität

### Plan-Struktur

| Option | Description | Selected |
|--------|-------------|----------|
| 8 Plans, 1/Phase | 87-01 Phase71, 87-02 Phase72, …, 87-08 Phase79. Klare atomic-per-phase Commits. | ✓ |
| 4 gruppierte Plans | Spring-Boot-71 / Backup-Cluster 72-76 / Docker-78 / Closer-79. | |
| 1 Plan, 8 Task-Batches | Einzelner großer Plan. | |
| 9 Plans: 8 + Closer | 8 Plans pro Phase plus 87-09 Closer. | |

**User's choice:** 8 Plans, 1 per Phase.
**Notes:** Closer work folded into 87-08 (Phase 79 plan) per D-11.

### Sequenzierung

| Option | Description | Selected |
|--------|-------------|----------|
| Wellen nach Risiko | Wave1: 71+78 parallel; Wave2: 72; Wave3: 73-76; Wave4: 79. | |
| Streng sequenziell 71→79→78 | Eine Phase nach der anderen in Numerik-Reihenfolge. Maximale Isolierung. | ✓ |
| Parallel alle 8 | Eine Wave; schnellste Wallclock, höchstes Konflikt-Risiko. | |
| 2er-Paare | (71,78) → (72,73) → (74,75) → (76,79). | |

**User's choice:** Strictly sequential. Interpreted as numeric order: 71 → 72 → 73 → 74 → 75 → 76 → 78 → 79 (77 out of scope per ROADMAP).

---

## VALIDATION.md Dokument-Shape

### Dokument-Struktur

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: pre-execution Shape, retroaktiv ausgefüllt | Drafts behalten Wave-0/Sampling-Tables, werden mit echten Test-Klassen + ✅ green gefüllt. Neue (71, 78) folgen demselben Template, Wave 0 markiert "satisfied retroactively". | ✓ |
| Post-hoc-Audit Shape | Komplett neuer Aufbau: Coverage Summary + Gaps Found + Remediation + Manual-Only Residuals. | |
| Existing Drafts behalten, neue minimal | Drafts bleiben unverändert, neue VALIDATION minimal. Inkonsistent. | |

**User's choice:** Hybrid, retroactively filled.

### Sign-off Definition

| Option | Description | Selected |
|--------|-------------|----------|
| Per-Task-Map vollständig ✅ | Jede Task-Row hat reales Test-File, ✅ green mit CI-Beweis, Sign-Off-Checklist abgehakt, nyquist_compliant: true. | ✓ |
| Frontmatter-Update reicht | Schwächste Variante: nur Frontmatter. | |
| Approved + Operator-Signature | Wie Per-Task-Map + manuelle Operator-Signatur per Datum/Initialen. | |

**User's choice:** Per-Task-Map vollständig.

---

## Claude's Discretion

- Exact slugs for restored phase directories (verify against git history; preserve truncation form from deletion commit).
- Phrasing adjustments to Sign-Off checkboxes for retroactive language ("All tasks have <verify> or post-hoc evidence").
- Test-class naming + placement for generated gap-coverage tests — follow existing Phase 71-79 conventions in restored files.
- Whether plan 87-08's closing commit set is one squash or 3-4 small commits (REQUIREMENTS / STATE / AUDIT).

## Deferred Ideas

- **Non-trivial impl-bug fixes** (>~50 LOC or multi-file) — orchestrator pause with user-decision (capture+defer / accept-as-risk / expand-scope); default = capture+defer.
- **Per-phase `@DirtiesContext` audit** — if a generated gap test would need it, note as v1.12 `PERF-FUTURE-02` candidate; do not re-introduce in Phase 87.
- **MariaDB-only gap tests** — fold into existing Testcontainers infrastructure; do not create a new MariaDB workflow.
- **VALIDATION.md template evolution** — improve `$HOME/.claude/get-shit-done/templates/VALIDATION.md` post-Phase-87 only.
