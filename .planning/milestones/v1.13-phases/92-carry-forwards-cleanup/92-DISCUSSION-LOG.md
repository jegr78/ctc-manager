# Phase 92: Carry-Forwards & Cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-20
**Phase:** 92-carry-forwards-cleanup
**Areas discussed:** DOCS-01 in/out, CsvImport DRY-Refactor, COV-01 Test-Strategie, CLEAN-01 grep-predicate location

---

## DOCS-01 — Retroaktive 89/90/91-VERIFICATION.md

| Option | Description | Selected |
|--------|-------------|----------|
| IN — alle 3 schreiben | Plan 92-04 schreibt 89/90/91-VERIFICATION.md im Standard-Template (Phase Goal Recap + Goal-Backward Walk-Through + Verification Outcome); ~30min total; ableiten aus existierender VALIDATION.md + SUMMARY.md; v1.11 precedent commit 2e84fd57 | ✓ |
| IN — nur Phase 91 | Nur 91-VERIFICATION.md; 89/90 skippen weil VALIDATION.md schon vollständig ist; reduziert Effort auf ~10min | |
| OUT — skippen, REQ-ID closen | DOCS-01 als REQ-ID-Akzeptanz dokumentieren «Shape-Gap mit Audit-Doc gedeckt, kein neuer File»; schnellster Pfad; öffnet evtl. zukünftige Audit-Diskussionen | |

**User's choice:** IN — alle 3 schreiben
**Notes:** User picked the Recommended option without modification. Captured as D-01 in CONTEXT.md.

---

## CsvImport DRY-Refactor

| Option | Description | Selected |
|--------|-------------|----------|
| 3x Duplikation akzeptieren | Plan 92-01 kopiert das Pattern aus DriverSheetImportController nach CsvImportController; keine neue Abstraktion zu pflegen; v1.14 Backlog-Item falls 4. Konsumer kommt | ✓ |
| Helper jetzt extrahieren | Plan 92-01 extrahiert `GoogleApiFlashTranslator.translate(GoogleApiException, RedirectAttributes\|Model)` neben dem Mapper; reduziert 3x ~20 Zeilen auf 3x 1 Zeile; erfordert Refactor in DriverSheetImportController + RaceController | |
| Helper extrahieren + Refactor begrenzen | Helper extrahieren UND auf alle 3 Controller anwenden; Scope-Erweiterung von UX-01; Risiko: 2 zusätzliche bestehende Controller anfassen schlägt auf Coverage + ITs durch | |

**User's choice:** 3x Duplikation akzeptieren
**Notes:** User picked Recommended. Aligns with «keep the change small, defer abstraction until justified» — captured as D-02 in CONTEXT.md. Deferred-idea `GoogleApiFlashTranslator extraction` noted under `<deferred>` for v1.14 backlog re-evaluation when Phase 93 `DiscordRestClient` lands its own typed-catch surface.

---

## COV-01 Test-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Mixed: Unit + IT | (a) `RaceControllerCalendarTest` als Mockito-mocked `@WebMvcTest` für controller branches; (b) `GoogleSheetsServiceIT` + `GoogleCalendarServiceIT` erweitern um `IOException`-defensive-catch-Pfade in `GoogleApiExceptionMapper`; schnellste Coverage-Recovery | ✓ |
| Alles als IT (@SpringBootTest) | RaceControllerCalendarIT + Service-ITs als full-context @SpringBootTest mit @AutoConfigureMockMvc; end-to-end real-life; längere Laufzeit (-> CI E2E budget) | |
| Alles als Unit-Test | Nur Mockito Unit-Tests; schnell; Mocks decken nicht die echten Exception-Mapping-Pfade vom GoogleApiExceptionMapper ab | |

**User's choice:** Mixed: Unit + IT
**Notes:** User picked Recommended. Captured as D-03 in CONTEXT.md. Rationale: balances JaCoCo recovery speed with semantic correctness — Mockito `@WebMvcTest` for controller branches stays cheap, ITs for service-layer exception mapping verify the actual `IOException` → typed-subtype mapping rather than mocked stubs.

---

## CLEAN-01 grep-predicate location

| Option | Description | Selected |
|--------|-------------|----------|
| exec-maven-plugin in pom.xml | Plan 92-03 erweitert pom.xml `exec-maven-plugin` um eine neue `execution id=assumptions-fence` (Phase `validate`, bash-grep) parallel zu `template-fragment-call-guard`; 2 Unit-Tests in `org.ctc.build` package mit synthetic positive (JUnit) + negative (AssertJ); konsistent mit PLAT-07-Pattern; läuft lokal + in CI | ✓ |
| Standalone Bash-Script unter scripts/ | `scripts/check-assumptions-fence.sh` (gleiche grep, exit 1); Aufruf in pom.xml validate via exec; Script direkt aufrufbar für lokales Debugging; 2 Files statt 1, kein `scripts/*-fence.sh` precedent | |
| GitHub-Actions-Step in ci.yml | Neuer Step `assumptions-fence` in `.github/workflows/ci.yml` (ähnlich `dockerfile-noble-pin-guard`); nur CI; Verstoß erst nach push sichtbar | |

**User's choice:** exec-maven-plugin in pom.xml
**Notes:** User picked Recommended. Captured as D-04 in CONTEXT.md. Aligns with the existing Plan 71 PLAT-07 `template-fragment-call-guard` shape — Plan 92-03 reuses the build-guard pattern by adding a sibling `<execution>` rather than introducing a new mechanism.

---

## Claude's Discretion

Captured in `<decisions> § Claude's Discretion` of CONTEXT.md. Summary:

- Exact wording of the 3 retroactive VERIFICATION.md files (planner picks prose, mirrors v1.11 precedent 2e84fd57 shape).
- Exact CSS class name lookup for the 4 error-badge classes (planner verifies against `admin.css`; defaults to Phase 91 D-07 BEM shape).
- Exact wording of the placeholder PR body opened by Plan 92-01 (planner picks "work in progress" prose).
- Exact location of `RaceControllerCalendarTest` (`org.ctc.admin.controller` sibling vs nested package).
- Exact `pom.xml` line placement of the new `assumptions-fence` execution within the `exec-maven-plugin` block.
- Exact bash grep regex shape inside the `assumptions-fence` execution (semantic locked by D-04; planner picks BSD/GNU-grep-portable regex form).
- Whether `AssumptionsFencePredicateTest` invokes bash via `ProcessBuilder` or reuses the `exec-maven-plugin` invocation pattern.

## Deferred Ideas

- **`GoogleApiFlashTranslator` helper extraction** — defer to v1.14 backlog if a 4th Google-API-consuming controller emerges (or if Phase 93 `DiscordRestClient` ends up adopting the same 4-catch + flash pattern with its own `DiscordApiException` 4-permit hierarchy, the extract-translator decision becomes relevant at v1.14 scoping). Source: D-02 discussion.
- **`.planning/REQUIREMENTS.md` top-level traceability row flips for v1.13 REQ-IDs** — out of Phase 92 scope; each REQ-ID flips when the phase that owns it ships (per [[feedback-pr-description-update]] cadence). Source: BOOK-01 scope clarification during D-11.
- **Extending the `assumptions-fence` to cover other forbidden imports (JUnit-4, etc.)** — not in CLEAN-01 scope; v1.14 backlog if SpotBugs / OpenRewrite surfaces an analogous fence opportunity. Source: D-04 discussion.
- **Wider `@CtcDevSpringBootContext` adoption beyond Phase 90's 5-class `db.migration.**` cluster** — Phase 90 carry-forward; re-evaluate against the Phase 92 baseline if the new Mockito `@WebMvcTest` shape suggests context-cache opportunities. Source: D-03 discussion sidebar.
