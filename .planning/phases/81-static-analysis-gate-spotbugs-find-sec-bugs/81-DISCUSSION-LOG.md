# Phase 81: Static Analysis Gate (SpotBugs + find-sec-bugs) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-16
**Phase:** 81-static-analysis-gate-spotbugs-find-sec-bugs
**Areas discussed:** Gate-Schwellwert, Fix-vs-Suppress Haltung, Klassen-Exclusion-Umfang, find-sec-bugs Pattern-Filter

---

## Gate-Schwellwert (HIGH-only vs Default)

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: Report=Default, Gate=High | spotbugs:spotbugs reportet Medium+High, aber spotbugs:check blockt nur bei HIGH. Erfüllt SC#4 wörtlich, gibt aber Medium-Findings nicht auf. Initial-Suppression bleibt überschaubar. | |
| Streng: Threshold=Default überall, Medium+HIGH blocken | Erfüllt STAT-02 wörtlich. Maximales Bug-Catching, aber jede find-sec-bugs Medium-Finding muss vor Gate-Aktivierung gefixt oder mit Rationale suppressed werden. | ✓ |
| Locker: Threshold=High überall, nur HIGH überhaupt reporten | Schnellster Weg zum grünen Gate, aber Medium-Findings sind unsichtbar. Verliert eigentlich den Sinn von effort=Max. | |

**User's choice:** Streng: Threshold=Default überall, Medium+HIGH blocken
**Notes:** Strengere Lesart bewusst gewählt — Phase 81 trägt die Initial-Triage-Last (Pre-Suppress für SSRF/ZIP-Slip/BCrypt, Real-Bug-Fixes für echte Medium-Findings). Effort=Max bleibt; Threshold=Default landet in BEIDEN executions (spotbugs:spotbugs Report-Goal + spotbugs:check Block-Goal). Trade-off: höherer Phase-81-Aufwand für strenger ausgerichteten Long-Term-Gate.

---

## Fix-vs-Suppress Haltung nach Lombok-Mitigation

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: HIGH fixen, Medium triagieren | HIGH-Findings werden in Phase 81 gefixt (sind selten und meist echte Bugs). Medium-Findings: echter Bug → fix; absichtliches Pattern (SSRF-Allowlist, ZIP-Slip-Defense, BCrypt-Bean) → spotbugs-exclude.xml mit Rationale + Code-Kommentar-Verweis; reines Stil-Issue → suppress mit kurzer Begründung. Phase-Scope bleibt vorhersehbar. | ✓ |
| Fix-First: praktisch keine Suppressions außer Lombok | Jedes Non-Lombok-Finding wird als Bug behandelt und gefixt. Suppressions nur für nachweisbar intentional (SSRF/BCrypt/ZIP-Slip) + Lombok EI_EXPOSE_REP. Risiko: Scope-Creep wenn der Baseline-Report 30+ Findings zeigt. | |
| Suppress-First mit Backlog-Tracking | Alle Findings werden zunächst suppressed mit Rationale, echte Bugs als Issues für spätere Phasen geparkt. Phase 81 wird schnell grün. Konflikt mit "Frustration ist kein Approval"-Memory — schiebt Schuld in die Zukunft. | |

**User's choice:** Hybrid: HIGH fixen, Medium triagieren
**Notes:** Klare Trennung — HIGH-Findings sind in Phase 81 selbst Pflicht-Fix (eigene atomic commits). Medium-Findings durchlaufen eine Triage: (a) echter Bug → fix; (b) absichtliches Pattern → suppress mit Rationale + Code-Kommentar-Verweis; (c) Stil → suppress mit kurzer Begründung. STAT-04 "no blanket SuppressWarnings" bleibt absolut. Jede `<Match>`-Suppression bekommt einen XML-Kommentar mit Begründung + ggf. Code-Verweis.

---

## Klassen-Exclusion-Umfang in spotbugs-exclude.xml

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid | Class-level excludes nur für CtcManagerApplication + TestDataService + DemoDataSeeder. Die 11 Grafik-Services bleiben unter dem Gate. Plus EI_EXPOSE_REP* Pattern-Exclude auf org.ctc.domain.model.*. Macht Code-Qualität und Coverage zu zwei unabhängigen Ebenen. | ✓ |
| Surgical: nur Pattern-Excludes | Keine class-level excludes. Alle 14 JaCoCo-exkludierten Klassen werden von SpotBugs analysiert. Nur EI_EXPOSE_REP* auf domain.model.*. Maximales Bug-Catching, aber bei den Grafik-Services können Medium-Findings auftauchen, die alle suppressed oder gefixt werden müssen. | |
| Mirror JaCoCo | Alle 14 JaCoCo-Excludes als class-level Excludes in spotbugs-exclude.xml übernehmen + EI_EXPOSE_REP* auf domain.model.*. Konsistent mit Coverage-Politik, aber blendet einen substantiellen Teil des produktiven Codes (Grafik-Services) von der Bug-Analyse aus. | |

**User's choice:** Hybrid
**Notes:** Coverage- und Static-Analysis-Excludes sind bewusst entkoppelt — die Frage "testen wir das" ist orthogonal zur Frage "kann es Bugs enthalten". Grafik-Services sind produktiver Code; etwaige Medium-Findings dort (DM_DEFAULT_ENCODING / OS_OPEN_STREAM) werden per-Pattern oder per-Methode suppressed mit Rationale, nicht blanket-class-level. EI_EXPOSE_REP*-Pattern-Exclude auf `org.ctc.domain.model.*` ist belt-and-braces zur Lombok-Annotations-Mitigation.

---

## find-sec-bugs Pattern-Filter

| Option | Description | Selected |
|--------|-------------|----------|
| Alle 144 aktiv, Triage über Hybrid-Postur | STAT-03 wörtlich erfüllt. Was tatsächlich feuert, wird in der Baseline-Phase sichtbar und dann triagiert. Robust gegen überraschende Findings, die wir jetzt nicht antizipieren. | ✓ |
| SPRING_ENDPOINT pre-suppressen | SPRING_ENDPOINT ist von find-sec-bugs als 'informational' dokumentiert. Pre-Suppress als single bug-pattern exclude mit Rationale-Kommentar. Alles andere bleibt aktiv. Reduziert Initial-Noise um die Anzahl der Endpoints (~80+). | |
| Aggressiv pre-filtern | Mehrere bekannt-noisy Patterns pre-excluden. Risiko: wir suppressen Patterns, die in diesem Codebase gar nicht feuern würden — unnötige Konfig-Last. | |

**User's choice:** Alle 144 aktiv, Triage über Hybrid-Postur
**Notes:** Keine Pre-Filterung von find-sec-bugs Patterns. Wenn SPRING_ENDPOINT in der Baseline tatsächlich auf jedem Controller-Endpoint feuert und nicht als HIGH klassifiziert wird, kann es als single bug-pattern Pre-Suppression mit Rationale aufgenommen werden — aber datengetrieben, nicht spekulativ. Strenge Auslegung von STAT-03 "144 Spring Security-aware patterns participate in the gate".

---

## Claude's Discretion

- Final plugin version pins für `spotbugs-maven-plugin` (Research: 4.9.8.3) und `findsecbugs-plugin` (Research: 1.14.0) — Planner picks latest stable auf Maven Central at planning time, dokumentiert die gewählte Version.
- Build-Guard für `<Match>` Rationale-Kommentare in `config/spotbugs-exclude.xml` (Analog zum `template-fragment-call-guard`-Pattern) — Planner entscheidet basierend auf CI-Kosten-Nutzen.
- Throwaway-Branch-Location für STAT-06 deliberate-violation Test (src/main vs src/test) — abhängig vom finalen `<includeTests>`-Setting.
- README-Update für SpotBugs (CLAUDE.md ist Pflicht per STAT-07; README optional).
- Datei-Layout/Sortierung in `config/spotbugs-exclude.xml`.
- Plugin-Reihenfolge in pom.xml `<build><plugins>` (Funktion vs Alphabet).

## Deferred Ideas

- Checkstyle / PMD Integration (Research Conflict 2 → permanent rejection unless new milestone surfaces specific gap).
- CodeQL SAST → Phase 85; Phase-81-Suppressions sind Vorlage.
- Renovate-Auto-Bumps für spotbugs-/findsecbugs-Plugins → Phase 84.
- `<includeTests>true</includeTests>` für Test-Code-Scanning → mögliches Follow-up.
- Custom SpotBugs Detectors (z.B. Controller-darf-keine-Repository-injizieren) → ArchUnit ist besser, separate Phase.
- Build-Guard für `<Match>` Rationale (D-09 tail) → Upgrade von "discretion" zu "mandatory" wenn künftige Phase eine rationale-lose Suppression durch den Review schmuggelt.
- `spotbugs:gui` Developer-Target → discretion.
