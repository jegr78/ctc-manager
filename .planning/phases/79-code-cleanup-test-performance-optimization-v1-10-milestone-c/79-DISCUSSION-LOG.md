# Phase 79: Code Cleanup + Test Performance Optimization (v1.10 Milestone Closer) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-15
**Phase:** 79-code-cleanup-test-performance-optimization-v1-10-milestone-c
**Areas discussed:** Cleanup-Scope-Grenze, Test-Perf-Mechanismus, Comment-Thinning-Regel, Milestone-Closure-Flow

---

## Cleanup-Scope-Grenze

### Q1.1 — Wie weit soll der Cleanup-Sweep gehen?

| Option | Description | Selected |
|--------|-------------|----------|
| Chirurgisch: nur die 2 grossen Files (Recommended) | Nur BackupImportService.java (906 LOC) und BackupArchiveService.java (639 LOC) refactoren. Niedrigstes Regression-Risiko. | |
| Mittel: alle Service- + Controller-Klassen (~10 Files) | BackupImportService, BackupArchiveService, BackupExportService, BackupController, etc. | |
| Breit: gesamtes org.ctc.backup (84 Files) | Alle Restorer (22), MixIns (22), DTOs, Configs, Exceptions auch sweepen. | |

**User's choice:** Free-text override — "Über den gesamten Meilenstein hinweg haben sich viele unnötige Codepassagen eingeschlichen. Daher komplett auf src/main und src/test ausweiten. Das soll ein richtig großes und umfangreiches Cleanup sein."
**Notes:** Override goes BEYOND any presented option — full src/main + src/test sweep across the whole project, not scoped to org.ctc.backup. Codified as D-01 in CONTEXT.md.

### Q1.2 — Welche Refactoring-Klassen sind erlaubt bei diesem breiten Sweep?

| Option | Description | Selected |
|--------|-------------|----------|
| Comment-Thinning (Recommended) | Redundante Javadoc loeschen, // removed Marker, Phase-N-Referenzen, redundante Method-Headers. | ✓ |
| Dead-Code-Removal (Recommended) | Ungenutzte private Methoden, ungenutzte Parameter, ungenutzte Imports, unreachable Code, leere Catch-Bloecke. | ✓ |
| Extract-Method + Rename (Recommended) | Lange Methoden splitten (>50 LOC), unklare Variablen-/Parameter-Namen verbessern. | ✓ |
| Logik-Vereinfachung | Verschachtelte if/else flach klopfen, Loops durch Streams ersetzen wo klarer, Optional-Chains verkuerzen. | ✓ |

**User's choice:** All four cleanup classes.
**Notes:** Maximally aggressive cleanup. Codified as D-02 in CONTEXT.md.

### Q1.3 — Wie soll der Cleanup-Diff strukturiert werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Pro Package atomic commit (Recommended) | Ein Commit pro Java-Package. Nach jedem Commit: ./mvnw test. Granular reviewbar, einfaches git bisect. | ✓ |
| Pro Cleanup-Klasse atomic commit | Ein Commit fuer alle Comment-Thinnings, naechster fuer Dead-Code, naechster fuer Extract-Method, naechster fuer Logik-Vereinfachung. | |
| Per-File-Commit fuer grosse Files, Batch fuer kleine | Files >300 LOC bekommen eigene Commits, kleine werden per Package gebatcht. | |
| Ein grosser Cleanup-Commit | Alles in einem Commit, einmal verify am Ende. | |

**User's choice:** Pro Package atomic commit (Recommended).
**Notes:** Codified as D-03 in CONTEXT.md.

### Q1.4 — Wie umgehen mit verdächtig ungenutzten Klassen (Spring-Beans, JPA-Listenern, Reflection)?

| Option | Description | Selected |
|--------|-------------|----------|
| Nur loeschen wenn sichere Indikatoren (Recommended) | IDE + grep keine Referenzen UND keine @Component/@Service/@Bean/@Entity/@JpaListener/@EventListener-Annotation. Bei Unsicherheit: stehen lassen + im Plan als TBD-verify markieren. | ✓ |
| Aggressiv loeschen, im PR-Review fangen | Alles loeschen, was Tests + verify -Pe2e ueberlebt. Vertraut auf 1370 Tests. | |
| Erst Inventar, dann gezielt loeschen | Erster Plan-Step: Inventar aller verdaechtigen Klassen mit Klassifikation. | |

**User's choice:** Nur loeschen wenn sichere Indikatoren (Recommended).
**Notes:** Codified as D-04 in CONTEXT.md.

---

## Test-Perf-Mechanismus

### Q2.1 — Welcher Test-Perf-Hebel hat Prioritaet?

| Option | Description | Selected |
|--------|-------------|----------|
| Surefire forkCount=2C + reuseForks=true (Recommended) | Unit-Tests ueber Prozesse parallelisieren. Erwarteter Gewinn: 30-50% bei ~1200 Unit-Tests. | ✓ |
| Failsafe forkCount=1C parallel fuer IT | Integration-Tests ueber Prozesse parallelisieren. Niedrigere Forks weil jeder IT-Prozess einen Spring-Context startet. | ✓ |
| @DirtiesContext-Audit + Context-Cache-Reuse | 13 @DirtiesContext-Annotations pruefen: jede unnoetige zerstoert den Spring-Cache. | ✓ |
| JUnit 5 @Execution(CONCURRENT) | Thread-Level-Parallelisierung innerhalb derselben JVM. Schaerfere Race-Risiken. | |

**User's choice:** Three options + free-text addition.
**Notes:** User explicitly added: "Zusätzlich noch prüfen, wie Testläufe im Workflow ausgelöst werden. Es gab nun häufig unnötige Re-Runs, wegen vermeintlichen Stale Code oder flaky Tests. Wichtig ist, dass alle Tests independent laufen und keinen dirty state zurücklassen - egal ob Einzellauf oder im Full Build." Codified as D-05 in CONTEXT.md (4 hebels: Surefire, Failsafe, @DirtiesContext, NEW Test-Independence-Audit). JUnit @Execution(CONCURRENT) explicitly NOT chosen.

### Q2.2 — Wie sollen die Perf-Hebel sequenziert werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Test-Independence zuerst, dann Parallelisierung (Recommended) | Phase 1: Independence-Audit (flaky-Test-Hunt, @DirtiesContext-Audit, dirty-state-Detection). Erst dann Phase 2 forkCount. | ✓ |
| Surefire zuerst, IT/Failsafe danach | Erst Unit-Tests parallelisieren, Stabilitaet pruefen, DANN Failsafe parallel aktivieren. | |
| Alles zusammen in einem Wave | Komplette Konfiguration in einem Plan-Step, ein finaler verify -Pe2e als Beweis. | |

**User's choice:** Test-Independence zuerst (Recommended).
**Notes:** Codified as D-05 sequence in CONTEXT.md.

### Q2.3 — Welches Erfolgskriterium fuer die Test-Perf-Optimierung?

| Option | Description | Selected |
|--------|-------------|----------|
| Baseline messen, dann Min-Reduktion (Recommended) | Plan-Step 1 misst aktuellen Wallclock. Erfolg = Reduktion um >= 30% (X*0.7). Dokumentiert in 79-AUTO-UAT.md. | ✓ |
| Absolute Zielzeit (z.B. < 5 Min) | Hartes Wallclock-Ziel ohne Baseline-Bezug. | |
| Nur 'no regression' — Hauptsache nicht langsamer | Phase 79 baut Infrastruktur, aber kein konkretes Speed-Up-Ziel. | |

**User's choice:** Baseline messen + Min-Reduktion (Recommended).
**Notes:** Codified as D-06 in CONTEXT.md.

### Q2.4 — Welche Workflow-Hygiene mit anpacken?

| Option | Description | Selected |
|--------|-------------|----------|
| ci.yml Concurrency-Group (Recommended) | concurrency: { group: ${github.ref}, cancel-in-progress: true } im ci.yml. | ✓ |
| Maven --no-transfer-progress + retry-flag | Saubere Logs (kein Download-Spam), dokumentierter Re-Run-Pfad. | ✓ |
| mariadb-migration-smoke.yml-Trigger ueberpruefen | Path-filter Sanity-Check. | ✓ |
| Flaky-Test-Quarantaene-Mechanismus | Liste der bekannten flaky Tests dokumentieren ODER mit @Tag("flaky") markieren. | ✓ |

**User's choice:** All four + important clarification.
**Notes:** User clarified the workflow concern: "Mit dem Workflow hatte ich den Execute Ablauf gemeint. Wir haben teilweise ja Gates für die einzelnen Phasen zur Absicherung - das ist auch gut so. Jedoch wurden zu häufig komplette Testläufe wieder und wieder ausgeführt, was zu einer sehr langen Fertigstellungszeit führt." Refers to the GSD-execute-flow re-running full test suites between waves, NOT the GitHub Actions workflow. Codified as D-07 (CI hygiene) AND D-08 (codify Test-Invocation-Discipline as a project convention in TESTING.md, links feedback_test_call_optimization memory).

---

## Comment-Thinning-Regel

### Q3.1 — Welche Comments duerfen geloescht werden?

| Option | Description | Selected |
|--------|-------------|----------|
| Loeschen: Phase-N-Referenzen (Recommended) | // Phase 75 D-07, // implements GAP-2, // see 72-CONTEXT.md — alle Phase-/Plan-/CONTEXT-Verweise raus. | ✓ |
| Loeschen: Was-Comments + // removed Marker (Recommended) | Comments, die nur wiederholen was der Code tut. | ✓ |
| Behalten: MariaDB-vs-H2-Quirks + Workarounds (Recommended) | Echte Pitfall-Warnungen fuer zukuenftige Devs. | ✓ |
| Behalten: Public-API-Javadoc | Service-public-Methoden + Controller-Endpoints behalten Javadoc. | ✓ |

**User's choice:** All four (delete-list and keep-list).
**Notes:** Codified as D-09 (delete-list) + D-10 (keep-list) in CONTEXT.md.

### Q3.2 — Test-Code-Comment-Regel?

| Option | Description | Selected |
|--------|-------------|----------|
| Test-Code behaelt // given / // when / // then (Recommended) | BDD-Strukturkommentare bleiben (per CLAUDE.md "Test Naming"). | ✓ |
| Identische Regel — auch BDD-Comments raus | Method-Name + AssertJ-Beschreibung soll selbsterklaerend sein. | |
| BDD-Comments NUR bei komplexen Tests | Pragmatisch, aber "komplex" ist subjektiv. | |

**User's choice:** Test-Code behaelt BDD-Comments (Recommended).
**Notes:** Codified as D-11 in CONTEXT.md.

### Q3.3 — Class-level Javadocs?

| Option | Description | Selected |
|--------|-------------|----------|
| Kondensieren auf 1-3 Zeilen Verantwortung (Recommended) | Header-Doc beschreibt nur die aktuelle Verantwortung der Klasse. Phase-Verlauf raus. | ✓ |
| Komplett loeschen | Class-Name + Package + Field-Liste sind Dokumentation genug. | |
| Behalten wie sie sind, nur Phase-Refs raus | Class-Docs bleiben weitgehend, nur konkrete Phase-N/Plan-N-Referenzen werden ersetzt. | |

**User's choice:** Kondensieren auf 1-3 Zeilen (Recommended).
**Notes:** Codified as D-12 in CONTEXT.md.

### Q3.4 — Wie schuetzen wir load-bearing Comments vor versehentlicher Loeschung?

| Option | Description | Selected |
|--------|-------------|----------|
| Grep-Schutzwortliste im Cleanup-Plan (Recommended) | Plan dokumentiert eine Schutzwortliste (MariaDB, H2, JEP, CVE, race, thread-safe, TODO, HACK, WORKAROUND, deadlock, OSIV, Lombok, Unsafe, transitiv). Cleanup-Subagent darf Comments mit diesen Woertern NICHT loeschen. | ✓ |
| Manueller PR-Review als alleiniger Schutz | Vertraut auf Reviewer-Aufmerksamkeit. | |
| Pre-Cleanup-Snapshot der wichtigen Comments | Plan-Step 1 grept alle //-Lines mit Schutzwoertern in eine IMPORTANT-COMMENTS.md. | |

**User's choice:** Grep-Schutzwortliste im Cleanup-Plan (Recommended).
**Notes:** Codified as D-13 in CONTEXT.md (with extended keyword list including FIXME, pitfall, auto-commit, auditing, AuditingEntityListener).

---

## Milestone-Closure-Flow

### Q4.1 — Wo gehoeren /gsd-audit-milestone und /gsd-complete-milestone hin?

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 79 enthaelt audit, complete kommt nach PR-Merge (Recommended) | audit-milestone als letzter Plan-Step. complete-milestone manuell vom User nachdem die Phase-79-PR gemergt ist. | |
| Beide INNERHALB Phase 79 als Plan-Steps | Maximale Automation, Archive-Commits landen im Feature-Branch. | ✓ |
| Beide AUSSERHALB Phase 79, vom User manuell | Phase 79 macht nur Cleanup + Test-Perf. Audit + Complete fuehrt der User danach selbst aus. | |

**User's choice:** Beide INNERHALB Phase 79 als Plan-Steps.
**Notes:** Override of recommended option. User accepts that complete-milestone archive commits will land on the feature branch (and ride into master via squash-merge). Codified as D-14 in CONTEXT.md.

### Q4.2 — Was bei Audit-Findings?

| Option | Description | Selected |
|--------|-------------|----------|
| Hard-Stop: Findings muessen vor /gsd-complete-milestone gefixt werden (Recommended) | Phase 79 Plan wird bei Findings unterbrochen. Findings als zusaetzliche Plan-Steps oder Hotfix-Sub-Phase 79.X. | ✓ |
| Soft: Findings dokumentieren, Phase 79 ship-en, dann Folge-Phase | Audit-Findings werden dokumentiert, complete-milestone laeuft trotzdem mit Carry-Over zu v1.11. | |
| Audit-Findings je nach Schwere triagieren | Plan definiert Schweregrad-Schwellen. | |

**User's choice:** Hard-Stop (Recommended).
**Notes:** Codified as D-15 in CONTEXT.md.

### Q4.3 — Carry-Overs von v1.9?

| Option | Description | Selected |
|--------|-------------|----------|
| Strikt v1.10-Scope, Carry-Overs bleiben fuer v1.11 (Recommended) | Phase 79 beruehrt nur Code/Tests aus dem v1.10-Cleanup-Scope. Die v1.9-Carry-Overs warten auf v1.11. | |
| Plan-SUMMARY-Frontmatter-Sweep mit aufnehmen | Niedriges Risiko, reines Bookkeeping in .planning/phases/56,57,62,64. | ✓ |
| Quality-Gate-Lock mit aufnehmen | Coverage-Gate-Hardening: pom.xml jacoco minimum raisen + Comment-Noise-Guard. | |

**User's choice:** Plan-SUMMARY-Frontmatter-Sweep mit aufnehmen.
**Notes:** SINGLE concession to v1.9 carry-overs — all others stay v1.11+. Codified as D-16 in CONTEXT.md.

### Q4.4 — PR-Strategie?

| Option | Description | Selected |
|--------|-------------|----------|
| Eine grosse Squash-PR (Recommended) | Per-Package atomic commits bleiben fuer Bisect erhalten, alles geht in eine PR. | ✓ |
| Mehrere PRs nach Cleanup-Klasse | PR 1: Comment-Thinning, PR 2: Dead-Code-Removal, etc. Massiver Koordinations-Aufwand und Rebase-Hell. | |
| Zwei PRs: Cleanup-First, dann Test-Perf+Audit | Trennt Code-Aenderungen von Build-Konfig. | |

**User's choice:** Eine grosse Squash-PR (Recommended).
**Notes:** Codified as D-17 in CONTEXT.md.

---

## Claude's Discretion

7 areas captured in CONTEXT.md as CD-01..CD-07:
- CD-01 Per-package commit ordering
- CD-02 Extract-method threshold (default >50 LOC, can extract earlier when readability clearly improves)
- CD-03 Logic-Vereinfachung pattern catalog (case-by-case)
- CD-04 @DirtiesContext audit verdicts (per annotation)
- CD-05 Flaky-test quarantine list (max 5 hard cap)
- CD-06 ci.yml concurrency-group placement (workflow-level vs job-level)
- CD-07 Plan-SUMMARY-Frontmatter sweep mechanics (script vs manual)

## Deferred Ideas

- pom.xml version bump 1.8.0-SNAPSHOT -> 1.10.0 (Phase 77 D-16 carry; separate release workflow after Phase 79)
- Raising JaCoCo minimum above 0.82 (Phase 77 D-11 hold; v1.11+ decision)
- JUnit 5 @Execution(CONCURRENT) (race-risk rejected; revisit only if process-level parallelism hits ceiling)
- v1.9 carry-overs except Plan-SUMMARY: Quality-Gate-Lock, per-group matchday UI, StandingsController:139 lazy-collection, UAT-02 (all v1.11+)
- Backup-feature extensions: per-Saison selectivity, verify-only mode, manifest.sha256, /admin/backup/history UI, @Scheduled cleanup (all v1.11+)
- GSD-orchestrator changes (multi-wave verify reduction) — codified only as project convention; orchestrator change is GSD-SDK-repo work
- Templates / CSS / HTML cleanup — out of Phase 79 scope (Java + tests + config + .planning/ frontmatter only)
- HUMAN-UAT for Phase 79 — none planned
