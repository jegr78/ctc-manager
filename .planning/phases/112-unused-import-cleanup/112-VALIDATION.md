---
phase: 112
slug: unused-import-cleanup
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-31
---

# Phase 112 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (Surefire/Failsafe); Checkstyle `maven-checkstyle-plugin` 3.6.0 (core 13.5.0) build gate |
| **Config file** | `config/checkstyle.xml`; `maven-checkstyle-plugin` + `checkstyle-gate-guard` exec in `pom.xml` |
| **Quick run command** | `./mvnw -Dtest=CheckstyleGateGuardPredicateTest test` / `./mvnw validate` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~1-2 s (Checkstyle validate) · full suite ~17-18 min |

---

## Sampling Rate

- **After every task commit:** `./mvnw validate` (exercises the Checkstyle gate + guards)
- **After every plan wave:** `./mvnw clean verify -Pe2e`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~2 s for the gate, full suite otherwise

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 112-01-01 | 01 | 1 | IMP-01 | T-112-01 | Zero unused imports across `src/main/java` + `src/test/java`; isolated `RemoveUnusedImports` dryRun reports no pending changes | gate | `./mvnw validate` (Checkstyle `UnusedImports`) · `./mvnw -Prewrite rewrite:dryRun -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports` | ✅ | ✅ green |
| 112-01-02 | 01 | 1 | IMP-02 | T-112-04 | Gate bound to `validate` fails the build on a new unused import (both roots); gate config is protected from silent drift | unit + gate | `./mvnw -Dtest=CheckstyleGateGuardPredicateTest test` · `./mvnw validate` | ✅ | ✅ green |
| 112-01-03 | 01 | 1 | IMP-02 | — | CLAUDE.md documents the gate so future phases/subagents inherit the rule | doc-grep | `grep -q "Checkstyle" CLAUDE.md && grep -q "RemoveUnusedImports" CLAUDE.md` | ✅ | ✅ green |
| 112-01-04 | 01 | 1 | IMP-01, IMP-02 | T-112-02/03 | Whole tree builds green under the authoritative gate with the guard active | suite | `./mvnw clean verify -Pe2e` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure (JUnit 5 + Surefire, `org.ctc.build` guard-test convention, Checkstyle build gate) covers all phase requirements. The validation gap (no committed predicate test for the `checkstyle-gate-guard`) was filled by `src/test/java/org/ctc/build/CheckstyleGateGuardPredicateTest.java`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Checkstyle `UnusedImports` module reds `validate` on a *synthetic* injected unused import | IMP-02 | A committed synthetic behavioral test would require adding Checkstyle core as a test-scope dependency — disproportionate. The behavior is continuously enforced by the live gate on all real committed code, and was proven once by a manual probe during execution. | Add `import java.util.concurrent.atomic.AtomicLong;` to one file under `src/main/java` and one under `src/test/java`, run `./mvnw validate` → exits 1 naming both files; remove the imports → exits 0. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (guard-predicate test added)
- [x] No watch-mode flags
- [x] Feedback latency < 2s (gate)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-31

---

## Validation Audit 2026-05-31

| Metric | Count |
|--------|-------|
| Gaps found | 1 |
| Resolved | 1 |
| Escalated (manual-only) | 1 |

Gap: `checkstyle-gate-guard` (added for review finding WR-02) had no committed predicate test, unlike its sibling `assumptions-fence` guard. Resolved by `CheckstyleGateGuardPredicateTest` (7 cases: intact pass, four weakening modes fail, self-reference-safety of the `[[:space:]]*` patterns, and a pom-sync assertion). One behavioral nuance (synthetic unused-import → red) recorded as manual-only.
