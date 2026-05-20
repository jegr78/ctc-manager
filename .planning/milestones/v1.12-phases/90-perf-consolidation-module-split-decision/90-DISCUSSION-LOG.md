# Phase 90: PERF Consolidation & Module-Split Decision - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 90-PERF Consolidation & Module-Split Decision
**Areas discussed:** PERF-03 cluster scope, PERF-03 mechanism, PERF-05 verdict, Plan structure & measurement

---

## PERF-03 Cluster Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Conservative: V5+V4 only | Nur `db.migration.**` (39 Klassen) auf eine geteilte Base — primary handoff aus 89-03. Risiko-arm, eine klare Cluster-Form, Phase-86 Lesson respektiert. PERF-03 acceptance verlangt nur 'at least one IT cluster' — V5+V4 erfüllt das. | ✓ |
| Moderate: + 1 secondary cluster | V5+V4 PLUS einer der beiden 12er-Cluster (backup-exception ODER admin-security). | |
| Aggressive: all top-4 | V5+V4 + backup-exception + admin-security (~63 Klassen). | |
| Data-driven: scope set in plan | Mindestens V5+V4, planner entscheidet ob secondary Cluster aufgenommen werden basierend auf Annotation-Audit. | |

**User's choice:** Conservative: V5+V4 only
**Notes:** Risiko-arm gewinnt; Phase-86 Lesson (blind wide consolidation kann fragmentation invertieren) bleibt aktiv. Sekundäre Cluster gehen in `<deferred>` für v1.13 Re-Evaluation gegen PERF-06 CI-Daten. Anmerkung im Kontext: D-01 fordert eine Hash-Bucket-Population-Audit vor dem Refactor (frische Aggregator-Run, alle Klassen in `9cefac4c` + `f524774b` enumerieren), weil 89-03 SUMMARY's "29 + 10 = 39 occurrences" Aggregator-Event-Count ist, nicht Unique-Klassen-Count.

---

## PERF-03 Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Meta-annotation (@CtcDevSpringBootContext) | Composed-Annotation packt `@SpringBootTest(classes=CtcManagerApplication.class) + @ActiveProfiles("dev")` zusammen. Jede Cluster-Klasse swappt 2 Annotations gegen 1. @Tag/@Transactional bleiben auf Subklasse. Niedrigste Churn, Spring-native @AliasFor-Pattern, Cache-Key reduziert durch identischen MergedContextConfiguration-Hash. Funktioniert für Surefire UND Failsafe. | ✓ |
| Abstract super-class (BaseDevSpringBootIT) | Java-Inheritance mit `extends`. Höhere Churn. V4Smoke `@Transactional`-Mix erzwingt Inheritance-Splitting oder Parameter. | |
| Shared @TestConfiguration via @Import | TECHNISCH NICHT PASSEND: @TestConfiguration fügt Beans hinzu, ändert keine Cache-Key-Komponenten. Falsches Werkzeug. | |

**User's choice:** Meta-annotation (@CtcDevSpringBootContext)
**Notes:** Composed-Annotation-Idiom: `MergedAnnotations` walkt nativ, kein custom Resolver. V4SmokeIT behält `@Transactional`, alle ITs behalten `@Tag("integration")` auf der Subklasse — die composed Annotation deckt nur die Cache-Key-relevanten Anteile (`@SpringBootTest + @ActiveProfiles`). D-02b: Cache-Key-Reduktion bewiesen via Aggregator-Diff vorher/nachher, nicht via Wallclock-Gate (mirror Phase 89 D-02 honest reporting).

---

## PERF-05 Verdict

| Option | Description | Selected |
|--------|-------------|----------|
| Defer with explicit blockers | Re-evaluate nach PERF-06 CI Re-Harvest (Phase 91). Blocker: (1) TestDataService cross-boundary (@Profile("dev") in src/main/java), (2) IDE-Friction-Risk per [[clean-maven-build-authority]], (3) keine Hard-Data ob Cumulative-Effect PERF-01..04 schon ausreicht. v1.13 entscheidet. Niedriges Risiko, höchste Optionalität. | ✓ |
| Reject with rationale | Definitiv NEIN. Projekt zu klein, Multi-Module Overhead > Gewinn, schließt Optionalität für immer. | |
| Proceed with extraction | Sub-Module ctc-manager-tests JETZT extrahieren. Substantielle pom.xml-Restruktur + IDE-Re-Indexing + CI-Update. Hohes Risiko. | |

**User's choice:** Defer with explicit blockers
**Notes:** Optionalität für v1.13 bleibt erhalten. D-05 dokumentiert die 3 Blocker, den Re-Evaluation-Trigger (post-PERF-06 CI median), und eine "Why not reject?"-Begründung. Acceptance ist die Defer-Variante der REQUIREMENTS.md PERF-05 (3 Outcomes erlaubt: proceed/defer/reject — Defer ist regulär gültig).

---

## Plan Structure & Measurement

### Plan Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Three plans, sequential (mirror Phase 89) | 90-01 PERF-03 (Meta-Annotation + Refactor + Cache-Key-Diff + Wave-5), 90-02 PERF-04 (Testcontainers + docs), 90-03 PERF-05 (Defer-Doc). Sequential per [[wave-pause]] + [[inline-sequential-execution]]. | ✓ |
| Two plans (bundle PERF-04 + PERF-05) | 90-01 PERF-03, 90-02 PERF-04 + PERF-05. Eine Wave-Pause weniger; Code-Change + Doc-Change in einem Commit erschwert Forensik. | |
| Single plan for all three REQs | Alles in 90-01. Maximal kompakt, widerspricht [[wave-pause]], atomare Reverts unmöglich. | |

**User's choice:** Three plans, sequential (mirror Phase 89)

### Measurement

| Option | Description | Selected |
|--------|-------------|----------|
| Aggregator + local 3-run idle (bundle into PERF-03 SUMMARY) | Mirror Phase 89 D-02: 3 lokale Runs nach PERF-03 Merge, Cache-Key-Cluster-Diff + Wallclock-Median + JaCoCo + Context-Load-Count in 90-01 SUMMARY. PERF-06 (Phase 91) bleibt authoritative. | ✓ |
| Aggregator only (skip local wallclock) | Nur Cache-Key-Diff + JaCoCo. Spart 25 Min, verliert Sanity-Check (durch 3-Seed Failsafe ohnehin gedeckt). | |
| Aggregator + single local run (no median) | Cache-Key-Diff + 1 Run als Sanity-Check + 3-Seed Failsafe. Mittelweg. | |

**User's choice:** Aggregator + local 3-run idle (bundle into PERF-03 SUMMARY)
**Notes:** Phase 89 D-02 honest-reporting pattern wird identisch übernommen. PERF-06 (Phase 91 CI 5-run Re-Harvest) bleibt die authoritative Cumulative-Effect-Messung für v1.12.

---

## Claude's Discretion

Folgende Detail-Entscheidungen sind dem planner überlassen (siehe `90-CONTEXT.md § Claude's Discretion` für die volle Liste):

- Exakte Prosa für `docs/test-performance.md § PERF-03 Cluster` + Top-5 diff table format
- Ob Cache-Key-Aggregator zweimal von scratch läuft ODER 89-Wave-4 Fingerprint-Output für "before" snapshot wiederverwendet
- Genaue Package-Position für `@CtcDevSpringBootContext` (`org.ctc.testsupport.CtcDevSpringBootContext` canonical vs. Sub-Package)
- Defensive Javadoc-Warnung gegen `@DirtiesContext`-Subklassen-Additionen
- Exakte Wording `docs/test-performance.md § PERF-04 Testcontainers Reuse` + README.md Pointer-Paragraph
- `.test-perf-logs/90-01-wave5-run-{1,2,3}/` Evidenz-Retention vs. target-wipe

## Deferred Ideas

Während der Diskussion adressiert (in `90-CONTEXT.md § Deferred Ideas` verlinkt):

- Sekundäre Cluster-Konsolidierung (backup-exception 12, admin-security 12, AdminWorkflowE2E 7) → v1.13 Re-Evaluation
- Maven Sub-Module Extraktion → v1.13 (PERF-05 D-05 defer-verdict)
- Wider `@CtcDevSpringBootContext` Adoption über die ~100 IT-Klassen → v1.13 Sweep-Phase
- `@CtcLocalSpringBootContext` Sister-Annotation für `@ActiveProfiles("local")` → nur wenn future Phase ein local-profile Cluster identifiziert
- PERF-04 CI-side reuse enabling → außerhalb Scope (D-04 Invariant: CI behavior unchanged)
- PERF-06 CI authoritative Re-Harvest → Phase 91
- UX-01 Google-API typed-exception hierarchy → Phase 91 stretch
