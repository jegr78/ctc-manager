# Phase 103: StringUtils Blank-Check Sweep - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-28
**Phase:** 103-stringutils-blank-check-sweep-replace-manual-null-and-isblan
**Areas discussed:** Plan granularity, Refactor mechanism, Import style, Verify cadence

---

## Plan Granularity

| Option | Description | Selected |
|--------|-------------|----------|
| 1 Plan pro Top-Package (7 Plans) | domain (11) / discord (9) / admin (9) / sitegen (5) / dataimport (4) / backup (4) / gt7sync (1). ROADMAP-Default-Vorschlag. Atomare Commits pro Package, klare File-Whitelist je Subagent, einfache Rollback-Punkte. | |
| 1 Plan total (43 Files in einem Sweep) | Maximal kohärenter Diff, ein einziger Commit, eine `mvn clean verify -Pe2e` am Ende. Risiko: bei einem maskierten Verhaltensunterschied (z. B. Whitespace-Edge) müsste in der gesamten Phase gesucht werden. | ✓ |
| Hybrid: Discord-Cluster separat + Rest gebündelt | Plan 1: discord-Package (9 Files, frisch aus v1.13 — höchstes Bug-Risiko bei Regression). Plan 2: alle übrigen 34 Files. Schneidet den heißesten Code in einen eigenen Verify-Loop. | |

**User's choice:** 1 Plan total (43 Files in einem Sweep)
**Notes:** Treats the phase as a single mechanical unit. Maximally coherent diff. Planner may internally batch by package for executor turn-taking but the unit of work is one plan / one final commit / one phase-end verify.

---

## Refactor Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Hand-Edit mit gezielter grep-getriebener Substitution | Subagent ersetzt Pattern für Pattern, prüft jeden Treffer visuell, fügt Import hinzu. Volle Kontrolle über Edge-Cases (Methodenketten, Nested-Calls, der DriverService::hasText Bonus). Aufwand pro Datei ~1-2 min. | |
| OpenRewrite-Recipe (Researcher prüft Existenz / Custom) | Researcher prüft, ob `rewrite-spring` oder `rewrite-migrate-java` eine fertige `UseStringUtilsHasText`-Recipe hat. Falls ja: `./mvnw -Prewrite rewrite:run` macht alle 86 Stellen mechanisch. Falls nein: Hand-Edit. Vorteil: vollständig deterministisch. | |
| Hand-Edit primär, Recipe als Validierung am Ende | Plans führen Hand-Edits aus, am Phase-Ende läuft `rewrite:dryRun` zur Validierung dass keine Stellen vergessen wurden. Researcher schreibt ggf. eine Minimal-Validation-Recipe. | ✓ |

**User's choice:** Hand-Edit primär, Recipe als Validierung am Ende
**Notes:** Recipe = oracle, not editor. Researcher must investigate whether an existing rewrite-spring/rewrite-migrate-java recipe covers the pattern; if not, a minimal in-repo validation recipe is acceptable. The `rewrite:dryRun` runs before the phase-end `clean verify -Pe2e`.

---

## Import Style

| Option | Description | Selected |
|--------|-------------|----------|
| Klassen-qualifiziert: StringUtils.hasText(s) | `import org.springframework.util.StringUtils;` + `StringUtils.hasText(s)`. Explizit, gleicher Stil wie bestehender Spring-Code in der Codebase (z. B. `Objects.requireNonNull`, `Assert.notNull`). Standard-Empfehlung. | |
| Static Import: hasText(s) | `import static org.springframework.util.StringUtils.hasText;` + `hasText(s)`. Kürzer, dichter — aber riskiert Collision mit `java.lang.String.isBlank` Erwartungen beim Lesen. Inkonsistent zu sonstigem Codebase-Stil. | ✓ |
| DriverService-Filter zusätzlich auf Method-Reference | Method-Reference geht NUR mit Static Import: `filter(StringUtils::hasText)` funktioniert klassen-qualifiziert. Diese Option = klassen-qualifiziert PLUS expliziter Auftrag, `DriverService.java:160` als `filter(StringUtils::hasText)` zu schreiben. | |

**User's choice:** Static Import: hasText(s)
**Notes:** The dense static-import form is the explicit reader-friendliness call. Consequence: DriverService.java:160 stays a lambda (`filter(s -> hasText(s))`), NOT a method reference — the roadmap's "method-reference bonus" is voided. Planner must surface this so the executor doesn't "improve" the lambda back to a method-reference. No co-existing static + class-qualified imports in the same file.

---

## Verify Cadence

| Option | Description | Selected |
|--------|-------------|----------|
| Targeted Tests pro Plan + 1x clean verify -Pe2e am Phase-Ende | Während Plans: `./mvnw test -Dtest=<betroffene Tests>` für schnelle Loops. Phase-End-Gate: ein einziger `./mvnw clean verify -Pe2e`. CLAUDE.md-konform (Build & Test Discipline). Schnellster Weg. | ✓ |
| Per-Plan clean verify -Pe2e | Nach jedem der 7 Plans ein voller `clean verify -Pe2e`. ~17 min × 7 = ~2 h reine Verify-Zeit. Granularere Bug-Lokalisation, aber teuer für einen rein mechanischen Refactor mit null Behavior-Change. | |
| Per-Plan `mvn verify` (Surefire+Failsafe, ohne -Pe2e), Phase-End mit -Pe2e | Mittelweg: Unit+IT nach jedem Plan (~5 min), Playwright E2E nur am Phase-Ende. Catch-Net für Spring-Context-Verletzungen pro Plan, ohne 7× Playwright-Cost. | |

**User's choice:** Targeted Tests pro Plan + 1x clean verify -Pe2e am Phase-Ende
**Notes:** Surefire-targeted `-Dtest=…` per executor batch (~30 s) as cheap regression catch-net during the sweep; one `./mvnw clean verify -Pe2e` (~10 min + Playwright) at phase end as the merge oracle. No per-plan full verify (only one plan exists anyway under D-01).

---

## Claude's Discretion

- OpenRewrite validation lever placement (`pom.xml` `<configuration>` vs. one-off `rewrite.yml`) — researcher decides
- Whether the validation recipe is an existing `rewrite-spring` recipe or a minimal custom one — researcher decides based on registry inspection
- In-plan task ordering within the single-plan scope (e.g., domain-first vs. discord-first) — planner decides, must be deterministic

## Deferred Ideas

- String `.isEmpty()` audit (~10 hits) — different semantics, case-by-case decision, belongs in a separate post-v1.13 phase
- Method-reference bonus form (`filter(StringUtils::hasText)`) — voided by D-03 static-import choice; could be revisited if a future readability pass reverts to class-qualified imports
