# Phase 91: PERF Re-Harvest, Stretch UX Polish & Milestone Closer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-20
**Phase:** 91-PERF Re-Harvest, Stretch UX Polish & Milestone Closer
**Areas discussed:** UX-01 Scope, PERF-06 No-Improvement Outcome, PR Timing, Plan Cut, Exception Hierarchy, Error Rendering, Calendar Path, Docs Shape, Variance Spike, Baseline Swap, Nyquist Close, PR Body

---

## UX-01 Scope (Stretch IN or OUT for Phase 91 / v1.12)

| Option | Description | Selected |
|--------|-------------|----------|
| IN — Plan 91-02 nach PERF-06 | UX-01 ships as 2nd plan in Phase 91, after PERF-06 measurement validates budget. Typed-exception hierarchy + category badges + docs/operations/google-integration.md. | ✓ |
| DEFER zu v1.13 — Phase 91 = PERF-06 + Closer only | Phase 91 only has 2 plans (PERF-06 + Closer). UX-01 deferred-idea + STATE.md entry. | |
| Conditional — Entscheidung NACH PERF-06 Median | Decision gate after PERF-06: UX-01 IN only if substantial CI improvement; else defer. | |

**User's choice:** IN — Plan 91-02 nach PERF-06
**Notes:** UX-01 has been outstanding since v1.11 deferred-items; user picked "always IN" rather than "maybe IN" — orthogonal to PERF measurement, narrow surface, ship now.

---

## PERF-06 No-Improvement Outcome (CI-Median NICHT unter 23:00)

| Option | Description | Selected |
|--------|-------------|----------|
| Document + ship (Phase 90 OR-branch dokumentieren) | Per Success Criterion #2 wording: "no-improvement" outcome documented as Phase 90 OR-branch + next forward path; v1.12 still closes. | ✓ |
| Block close + re-investigate | Phase 91 stays open until material reduction; scope creep / milestone-stuck risk. | |
| Document + minimum threshold 'any measurable reduction' | Pragmatic: median < 23:00 (even 30s) counts; ≥ 30 % stretch is nice-to-have. | |

**User's choice:** Document + ship (Phase 90 OR-branch dokumentieren)
**Notes:** Pure D-10b decision; v1.12 ships the levers, not a gate-pass.

---

## PR Timing (When to open milestone PR on gsd/v1.12-...)

| Option | Description | Selected |
|--------|-------------|----------|
| PR vor PERF-06 öffnen, dann workflow_dispatch auf PR-Branch | Classic D-17: open PR early, CI via pull_request, 5 workflow_dispatch on PR-branch. Rolling body. | |
| PR erst beim Milestone-Closer öffnen (am Ende) | 5 workflow_dispatch on branch before PR exists. PR final body created at end. | |
| Hybrid: PR früh als Draft, Re-Harvest auf PR-Branch, finalize beim Closer | Draft PR opens after 91-01-PLAN.md commit, before 5 workflow_dispatch runs. Rolling body, flip to ready-for-review at Closer. | ✓ |

**User's choice:** Hybrid: PR früh als Draft, Re-Harvest auf PR-Branch, finalize beim Closer
**Notes:** Follows Phase 86 D-17 pattern + [[pr-description-update]]; Draft state signals not-yet-reviewable.

---

## Plan Cut (Phase 91 plan structure)

| Option | Description | Selected |
|--------|-------------|----------|
| 2 Plans: 91-01 PERF-06 + 91-02 Closer (UX-01 nur wenn IN als 3. Plan) | Conservative + roadmap-estimate-conform. UX-01 becomes 3rd plan when IN. | ✓ |
| 3 Plans (UX-01 separat): 91-01 PERF-06, 91-02 UX-01, 91-03 Closer | Clean separation: Measurement → Feature → Close. Three wave-pauses. | (implicit via UX-01=IN) |
| Single Atomic Closer Plan (91-01 only) | Maximum compact; bad forensics, atomic-revert hostile (Phase 90 D-06 REJECTED). | |

**User's choice:** 2 Plans + implicit "+1 wenn UX-01 IN" clause → resolves to **3 Plans** (91-01 PERF-06 + measurement docs, 91-02 UX-01 + google-integration.md, 91-03 Milestone Closer)
**Notes:** The 2-plan option's UX-01 clause + the UX-01=IN answer combine to the 3-plan structure. Sequential per [[wave-pause]] + [[inline-sequential-execution]].

---

## Exception Hierarchy (UX-01 typed exceptions)

| Option | Description | Selected |
|--------|-------------|----------|
| Flat: GoogleApiException + Category-Enum | Single class, getCategory() enum. Less typed catch. | |
| Hierarchisch: GoogleApiException → {Transient, Auth, NotFound, Permission}Exception | Sealed base + 4 subs. Compile-time catch typing. | ✓ |
| Hybrid: 2-Tier (Transient vs Permanent) + Category-Enum auf Permanent | Mid-ground. | |

**User's choice:** Hierarchisch: GoogleApiException → {Transient, Auth, NotFound, Permission}Exception
**Notes:** Idiomatic on Java 25 (sealed); mirrors v1.10 Backup BackupValidationException pattern.

---

## Error Rendering (Where to render category badge)

| Option | Description | Selected |
|--------|-------------|----------|
| Flash-Attribut errorMessage + errorCategory | Existing pattern (CLAUDE.md flash). New errorCategory flash key. CSS badge. | ✓ |
| Form-Field-Error per BindingResult.reject() | Spring forms way; switching pattern is churn. | |
| Dedicated Error-Card auf der Preview-Seite (HTML-Component) | Highest polish; over-engineered for 4 categories. | |

**User's choice:** Flash-Attribut errorMessage + errorCategory (preview/execute Endpoints)
**Notes:** Consistent with existing admin/controller pattern; minimal infrastructure change.

---

## Calendar Path (GoogleCalendarService UX scope)

| Option | Description | Selected |
|--------|-------------|----------|
| Same hierarchy, weitere consumer auf Service-Ebene | Typed exceptions for both services; consumers translate to UX where appropriate. | ✓ |
| Driver-Import ONLY in dieser Phase, Calendar deferred | Calendar stays IOException; UX inconsistency. | |
| Beide Services, aber Calendar nur logs+graceful-fallback (kein UI) | Same exceptions, calendar always log-and-fallback. | |

**User's choice:** Same hierarchy, weitere consumer auf Service-Ebene
**Notes:** Service layer is uniform; consumer-driven UX surface (D-08 in CONTEXT.md): user-trigger paths get flash UX, background-trigger paths get log + graceful-fallback.

---

## Docs Shape (docs/operations/google-integration.md)

| Option | Description | Selected |
|--------|-------------|----------|
| Operations Runbook (wie release-runbook.md): Setup + Errors + Troubleshooting | Operator-orientiert; consistent with existing operations/ docs. | ✓ |
| Pure Reference (Exception Hierarchy + Error Codes Table) | API-doc shape; loses operator action guidance. | |
| Full Guide: Architecture + Setup + Errors + Troubleshooting + Migration Notes | Scope creep; high write cost. | |

**User's choice:** Operations Runbook (wie release-runbook.md): Setup + Errors + Troubleshooting
**Notes:** Mirrors release-runbook.md + import-runbook.md shape; focused operator surface.

---

## Variance Spike (PERF-06 20 %-Toleranz überschritten)

| Option | Description | Selected |
|--------|-------------|----------|
| Auto: weitere 5 Runs harvesten, dann Median über alle 10 | Strict D-10 protocol from Phase 86 (hard-gate context). | |
| Manual: User entscheidet ob 2. Block oder n=5 mit notiertem Spike akzeptieren | Procedural pause point. | |
| Accept n=5 + dokumentiere Spike als 'observed variance' in test-performance.md | Honest-observational per Phase 89 D-02 / Phase 90 D-05; no hard gate. | ✓ |

**User's choice:** Accept n=5 + dokumentiere Spike als 'observed variance' in test-performance.md
**Notes:** No hard 7:50 gate active in v1.12; cost of 2nd 5-run block not justified for observational measurement.

---

## Baseline Swap (STATE.md update strategy)

| Option | Description | Selected |
|--------|-------------|----------|
| Replace 23:00 by new median; alte Zahl historisch im Changelog/PROJECT.md | Single CI median line in STATE.md; history in PROJECT.md + test-performance.md. | ✓ |
| Append new baseline (v1.12 + v1.11 carried forward) | STATE.md shows both; grows with every PERF milestone. | |
| Replace + add 'v1.11 baseline (23:00) reference' line | Hybrid mid-ground; mid-gain. | |

**User's choice:** Replace 23:00 by new median; alte Zahl historisch im Changelog/PROJECT.md
**Notes:** Signal-density-friendly; history preserved in two other locations.

---

## Nyquist Close (VALIDATION carry-forward)

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 91 selbst nyquist-validieren + alle 4 v1.12-Phasen via /gsd-validate-phase prüfen vor Closure | Strict; each plan ships VALIDATION.md; pre-close gate validates 88+89+90+91. | ✓ |
| Option-A in-milestone closure (wie v1.11): explizit notieren dass Phase 91 carries to v1.13 | Faster; introduces ambiguity. | |
| Nyquist nur für UX-01 (Plan 91-02), nicht für PERF-06 oder Closer | Targeted; PERF-06 + Closer skip VALIDATION. | |

**User's choice:** Phase 91 selbst nyquist-validieren + alle 4 v1.12-Phasen via /gsd-validate-phase prüfen vor Closure
**Notes:** v1.12 has been disciplined throughout; closing with Option-A would break that discipline.

---

## PR Body (Milestone PR composition)

| Option | Description | Selected |
|--------|-------------|----------|
| Per-REQ-ID Tabelle (REQ → Phase → Status → Plan → Acceptance) + CI-Run-Links + JaCoCo/SpotBugs/CodeQL | Structured; reviewer scans by REQ-ID. | |
| Narrative + Bullet-List per Phase | Lesbar; v1.11 PR #122 pattern. | |
| Beides: Summary-Tabelle oben + Narrative-Bullets unten + CI-Links + Coverage-Numbers | Full composite; longer body, all stakeholders. | ✓ |

**User's choice:** Beides: Summary-Tabelle oben + Narrative-Bullets unten + CI-Links + Coverage-Numbers
**Notes:** Follows [[pr-description-update]] rolling-summary pattern; finalized at Plan 91-03.

---

## Claude's Discretion

- Exact prose wording of `docs/test-performance.md § PERF-06 Re-Harvest`
- Exact prose wording of `docs/operations/google-integration.md` (shape locked by D-09; prose open)
- Exception package location (`org.ctc.dataimport.exception` default; alternative acceptable)
- Mapper utility class name + shape (static helper vs. builder vs. method-on-base)
- Exact CSS class naming for category badges (BEM-ish default; admin.css convention precedence)
- Sealed `permits ...` syntax vs. classic abstract+final subclasses
- `errorCategory` flash value as String vs. enum
- Post-PERF-06 CI run-log retention shape
- `MILESTONES.md` v1.12 entry exact wording (anchored to v1.11 entry shape)

## Deferred Ideas

- Wider `@CtcDevSpringBootContext` adoption beyond `db.migration.**` (v1.13 re-eval)
- Test-module-split extraction (v1.13 owns next decision)
- CI-side Testcontainers reuse on GitHub-hosted runners (out of scope; cold-start cost small)
- `@CtcLocalSpringBootContext` sister composed annotation (Phase 90 deferred)
- Background-trigger calendar-sync UX surface (no user trigger, log + fallback)
- `@ControllerAdvice` typed-exception handlers (extract if 3rd consumer surfaces; v1.13 cleanup)
- OAuth re-link UI wizard (v1.13+ if AuthGoogleApiException rates trend up)
- Retry-with-backoff for `TransientGoogleApiException` (manual-retry friction signal)
- Sheet ID lookup helper (admin-tool ping endpoint; v1.13)
