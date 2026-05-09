# Phase 64: Nyquist Validation Sweep — Context

**Gathered:** 2026-05-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Sechs v1.9-Phasen (56, 57, 58, 59, 60, 62) bekommen retroaktive `*-VALIDATION.md` Sweeps, sodass die v1.9-Milestone mit voller Nyquist-Coverage schließt (statt 1 compliant / 4 partial / 2 missing). Erfolg = `nyquist_compliant=true` + `wave_0_complete=true` in jeder Frontmatter; alle sechs nutzen `mode: retroactive`, da die zugrundeliegenden Phasen bereits abgeschlossen sind.

**Out of scope** (gehören in andere Phasen / sind bereits erledigt):
- Phase 61 (bereits approved + compliant)
- Phase 63 (docs-only, bereits passed)
- Graphics-Bridge-Migration (Phase 65)
- Code-Refactoring oder neue Feature-Tests, die nicht direkt fehlende REQ-ID-Coverage schließen

</domain>

<decisions>
## Implementation Decisions

### Plan-Struktur (D-01)
- **D-01:** Ein Bulk-Plan `64-01-PLAN.md` mit 6 Tasks, einer pro Ziel-Phase. Trotz Bulk-Struktur erhält jede Task ihren eigenen Audit-Output (eine VALIDATION.md). Bewusste Abweichung vom Phase-63-Pattern (3 Pläne) — der User hat explizit Bulk gewählt, weil der Phase-Scope rein bookkeeping ist und alle Tasks dasselbe Toolset nutzen (`gsd-validate-phase` retroactive).
- **D-02:** Task-Reihenfolge folgt State-Komplexität (leicht → schwer): Task 1 = Phase 58 (light, nur `wave_0_complete` flippen), Task 2-4 = Phase 57/60/62 (State A draft → approved), Task 5-6 = Phase 56/59 (State B reconstruct from scratch).

### Gap-Fill-Policy (D-03)
- **D-03:** Wenn der `gsd-nyquist-auditor` MISSING REQ-IDs findet (kein lateraler automatisierter Test), dann **Auto-fill via Auditor-Subagent** — neue Tests werden generiert, gegen `./mvnw test -Dtest=<NewTest>` grün gefahren, dann als COVERED in der Per-Task Map verzeichnet. Damit weicht Phase 64 vom Phase-61-Pattern ab (Phase 61 hatte keine fillable Gaps gefunden); falls Phase 64 echte Gaps findet, expandiert der Phase-Scope auf Test-Generierung.
- **D-04:** Subagent-Disziplin per CLAUDE.md ist **bindend**: Auditor-Subagent muss `model: opus` oder `model: sonnet` sein (nie haiku). Subagent-Prompt MUSS den aktiven Branch (`gsd/v1.9-season-phases-groups`) namentlich nennen und explizit verbieten: kein `git stash`, kein `git checkout`, kein `git reset`, kein Branch-Switch. Nach jedem Subagent-Return: Post-Dispatch-Validierung via `git branch --show-current` + `git log --oneline -3` + `git diff --stat`.
- **D-05:** Falls ein Auto-fill-Versuch nach max. 3 Iterationen scheitert (Auditor escalates), wird die REQ-ID in die Manual-Only-Sektion mit explizitem `Why-Manual`-Rationale verschoben (Phase-61-Fallback-Pattern). `nyquist_compliant=true` darf nur gesetzt werden, wenn alle Gaps entweder COVERED (lateral oder via auto-fill) oder explizit dokumentiert deferred sind.

### Reconstruction-Tiefe für 56 + 59 (D-06)
- **D-06:** Volle Phase-61-Tiefe für die zwei MISSING-Phasen. Per-Task Verification Map mit allen REQ-IDs (jeweils 10 für Phase 56, 6 für Phase 59) zu konkreten Test-Files mit Line-Range gemappt; alle Test-Klassen aus PLAN.md / SUMMARY.md SUMMARYs gelistet; Net-new Test-Infrastructure inventarisiert; Validation Audit Block am Ende.
- **D-07:** Das gilt auch für die State-A-Updates (57, 60, 62) — bestehende Per-Task Maps werden aufgefüllt (statt verkürzt) und bestehende Manual-Only-Sektionen reviewed + verfeinert. Phase 58 (State-A light) bleibt minimal: nur `wave_0_complete=true` flippen, Sign-Off-Checkliste closen, Validation Audit Block anhängen.

### Audit-Trail & Commit-Kadenz (D-08)
- **D-08:** Jede der 6 VALIDATION.md bekommt ein `## Validation Audit YYYY-MM-DD` Block am Ende mit Phase-61-Tabelle: Requirements audited, Plans audited, Gaps found, Resolved (already automated), Escalated to manual-only, Net-new test infrastructure, Verdict. Ohne diesen Block gilt eine VALIDATION.md als unvollständig.
- **D-09:** Commit-Kadenz: **1 Bundle-Commit pro Plan** für die VALIDATION.md-Updates, also `docs(64): nyquist validation sweep — 6 phases retroactive`. Falls Auto-fill der MISSING-Gaps Tests generiert, gehen diese in einen separaten `test(64): add Nyquist gap-fill tests` Commit (vor dem docs-Commit, damit der docs-Commit auf grünen Tests basiert). Plan-Summary-Commit (`docs(64-01): plan summary — nyquist validation sweep complete`) folgt am Ende.

### Claude's Discretion
- Exakte Reihenfolge der Tasks innerhalb des Plans (light → State-A → State-B ist Vorschlag, kann anders sortiert werden, falls Wave-Parallelisierung im Executor sinnvoll ist).
- Konkrete Wording der Phase-spezifischen Validation Audit Tabellen (Metric-Namen / Formulierung).
- Ob beim State-B-Reconstruct (56, 59) zuerst eine Skelett-VALIDATION.md aus dem Template gelesen wird oder die bestehende Phase-61-Datei als Vorlage referenziert wird.
- Welche Ebene von Cross-Reference-Links (file path : line range) sinnvoll ist — der Planner soll Phase-61-Stil anstreben, aber bei Aufwand-Explosion (>50 Test-Klassen pro Phase) auf Klassen-Ebene aggregieren dürfen.

</decisions>

<specifics>
## Specific Ideas

- "1 compliant / 4 partial / 2 missing" → Ziel: "7 compliant / 0 partial / 0 missing" (Phase 61 zählt mit, Phase 63 ist docs-only und braucht kein VALIDATION.md per Convention).
- Phase 61 (`61-VALIDATION.md`) ist der **Gold-Standard-Reference** für die State-B-Reconstruction. Speziell die "Per-Task Verification Map", die "Manual-Only Verifications" mit `Why Manual` Spalte, und der "Validation Audit YYYY-MM-DD" Appendix-Block sind das Vorbild.
- Auto-fill ist absichtlich gewählt (nicht das schnellere Phase-61-Pattern), weil der User v1.9 mit echter Coverage-Compliance schließen will — nicht nur mit Bookkeeping-Compliance. Wenn echte Gaps existieren, sollen sie geschlossen werden.
- Phase 58 ist der Sonderfall: VALIDATION.md hat bereits `nyquist_compliant: true`, nur `wave_0_complete: false`. Wave 0 (PhaseTestFixtures + 3 Repository ITs) ist laut SUMMARY-Trail längst grün gelaufen — der Flag-Flip ist quasi-mechanisch.
- Für die Manual-Only-Sektion gilt der Phase-61-Standard: jeder Eintrag braucht `Why Manual` Rationale (Visual-Quality-Bar, Production-Data-Boundary, Real-Device-Touch-Behavior, etc.). Keine Eskalation ohne Begründung.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 64 Scope & Goal
- `.planning/ROADMAP.md` § "Phase 64: Nyquist Validation Sweep" — Goal, Success Criteria SC1..SC6 (eine pro Ziel-Phase), Process-only-Markierung
- `.planning/v1.9-MILESTONE-AUDIT.md` § "Nyquist Compliance" — Audit-Tabelle mit per-Phase Action-Recipes; Tech-Debt-Inventory unter `tech_debt: nyquist`

### Reference-Implementation (Gold-Standard für Reconstruction)
- `.planning/phases/61-cleanup-quality-gate/61-VALIDATION.md` — Vollständige retroactive VALIDATION.md mit Per-Task Verification Map, Manual-Only-Sektion mit Why-Manual-Rationale, und "Validation Audit 2026-05-02" Appendix-Block. Dies ist das **Template** für 56 + 59 State-B-Reconstruction und die Tiefen-Vorlage für 57 / 60 / 62 State-A-Updates.

### Workflow-Mechanics
- `$HOME/.claude/get-shit-done/workflows/validate-phase.md` — Definiert State-A/B/C Detection, Gap-Klassifikation (COVERED / PARTIAL / MISSING), Auditor-Subagent-Spawn-Pattern, VALIDATION.md-Output-Format
- `$HOME/.claude/skills/gsd-validate-phase/SKILL.md` — Slash-Command-Wrapper

### Per-Phase Quellen für die State-B-Reconstruction (56, 59)
- `.planning/phases/56-model-schema-foundation/56-01-PLAN.md` … `56-05-PLAN.md` + Zugehörige `56-0X-SUMMARY.md` Dateien — Task-Listen, Requirements-Frontmatter, Test-Klassen-Referenzen
- `.planning/phases/59-import-test-data/59-01-PLAN.md` … `59-05-PLAN.md` + zugehörige Summaries — analog
- `.planning/phases/56-model-schema-foundation/56-VERIFICATION.md` und `.planning/phases/59-import-test-data/59-VERIFICATION.md` (sofern vorhanden) — Verified Test-Files für die Per-Task Map

### Per-Phase Quellen für die State-A-Updates (57, 58, 60, 62)
- Bestehende `*-VALIDATION.md` plus zugehörige PLAN/SUMMARY-Trails für den Cross-Reference-Step

### Project Constraints (CLAUDE.md)
- `CLAUDE.md` § "Subagent Rules" — Modell-Auswahl (opus/sonnet, nie haiku für Code), Branch-Schutz, Post-Dispatch-Validierung, Plan-Adherence (`NEEDS_CONTEXT` statt eigenmächtiger Erweiterung)
- `CLAUDE.md` § "Git Workflow" — Conventional Commits Prefixes (`docs:`, `test:`, `chore:`), Commit-Format, gh CLI für PR

### Test-Infrastructure-Konstanten
- `pom.xml` (JaCoCo 82% line gate, Surefire/Failsafe-Aufteilung, Playwright `-Pe2e`)
- `.planning/codebase/TESTING.md` — Test-Konventionen, Given-When-Then Naming
- `.planning/codebase/CONVENTIONS.md` — Java-Konventionen

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase 61 VALIDATION.md template**: 124 Zeilen retroactive Audit als Vorlage. Spezifisch: Frontmatter-Block (`mode: retroactive`), Per-Task Verification Map mit `Test File / Evidence`-Spalte (Markdown-Links zu `../../src/test/...#LXX-LYY`), Manual-Only-Tabelle mit `Why Manual`-Spalte, Validation Audit Appendix.
- **gsd-nyquist-auditor Subagent** (`$HOME/.claude/agents/gsd-nyquist-auditor.md`): bestehender Subagent-Type für State-A/B Audit + Gap-Fill. Wird bereits vom `validate-phase.md` Workflow gespawnt — kein neuer Subagent-Type nötig.
- **gsd-validate-phase Skill / Workflow**: Komplette Mechanik bereits installiert. Phase 64 hat per Task einfach `/gsd-validate-phase {N}` aufzurufen (oder den Workflow im Bulk-Modus zu wrappen).

### Established Patterns
- **`mode: retroactive`** in Frontmatter signalisiert: Phase ist abgeschlossen, Erfolgsmaß ist Artefakt-Vollständigkeit, nicht Re-Implementation. Phase 61 hat diesen Modus etabliert.
- **Markdown-Links auf Test-Files mit Line-Ranges** (`[Test.java:42-58](../../src/test/.../Test.java#L42-L58)`) für Cross-Reference. Phase 61 verwendet das durchgängig in der Per-Task Map.
- **`@Sql BEFORE_TEST_METHOD` mit per-Test SQL-Fixtures** (`legacy-season-without-playoff.sql`) ist das Pattern für Migration-Regression-Tests in v1.9. Falls Auto-fill Tests für MIGR-Requirements generiert, sollte dieses Pattern wiederverwendet werden.
- **Conventional Commits + Co-Author Footer**: alle v1.9-Commits folgen `<type>(<scope>): <description>` mit Claude-Co-Author-Block. Phase 64 muss das beibehalten.

### Integration Points
- **`gsd-sdk query commit`**: Standard-CLI für Commit-Erstellung in GSD-Phasen. Nimmt Conventional-Commit-Message + Datei-Liste, fügt Co-Author-Footer automatisch hinzu.
- **`./mvnw test -Dtest=<ClassName>`**: schneller Targeted-Test-Aufruf für Auto-fill-Verification. Per `feedback_test_call_optimization` Memory: kein wiederholter `./mvnw verify` während Auto-fill-Loop, sondern Targeted-Tests bis zum Final-Gate.
- **GitHub Actions CI** (`.github/workflows/`): bestehende Workflows (inkl. `mariadb-migration-smoke.yml`) prüfen jede PR; Phase 64 muss CI grün halten — nicht durchbrechen via fehlgeschlagene Auto-fill-Tests.

</code_context>

<deferred>
## Deferred Ideas

- **Phase-65-Vorgriff (Graphics-Bridge-Migration)**: kam beim Audit-Lesen kurz auf — nicht in Phase 64. Phase 65 ist eigenständig im ROADMAP terminiert.
- **`/gsd-validate-phase` Workflow-Verbesserung** (z.B. Bulk-Modus, automatische Multi-Phase-Sweeps): hier nur als beobachteter Friction-Point notiert. Nicht in v1.9. Falls relevant: per `/gsd-add-todo` oder `/gsd-add-backlog` als Backlog-Item.
- **Re-Audit der Phase-61-VALIDATION.md selbst**: Phase 61 ist `approved 2026-05-02`, kein Re-Audit nötig in Phase 64. Falls neue Auto-fill-Tests in 64 Phase-61-REQs lateral berühren würden, das nur dokumentieren — nicht 61 wiederöffnen.
- **Coverage-Threshold-Erhöhung über 82%**: bei 85.17% line coverage post-UAT (siehe Phase 61 audit) wäre eine Threshold-Anhebung möglich. Nicht in Phase 64. Falls relevant: eigener Backlog-Item.

</deferred>

---

*Phase: 64-nyquist-validation-sweep*
*Context gathered: 2026-05-07*
