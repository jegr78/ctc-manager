---
phase: 92
plan: 03
slug: carry-forwards-cleanup
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 92-03 — Validation Slice

> Per-plan slice of `92-VALIDATION.md` per CONTEXT D-08.
> 3 rows 92-03-01..03 covering CLEAN-01 build-guard fence.

---

## Sampling Rate

- **Per-task command (after Task 1, predicate test):** `./mvnw test -Dtest='AssumptionsFencePredicateTest'` (~5 s, 2 methods)
- **Per-task command (after Task 2, fence integration):** `./mvnw validate` (~1 s — both fences run as part of the validate phase)
- **Per-plan command (Task 3, full gate):** `./mvnw verify` (~7:18 min, full Surefire + Failsafe + JaCoCo + SpotBugs)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 92-03-01 | 03 | 1 | CLEAN-01 | — | `assumptions-fence` triggers on `import static org.junit.jupiter.api.Assumptions.assumeFalse` (synthetic positive in `@TempDir`) | unit | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenJunitAssumptionsImport_whenPredicateRuns_thenViolationDetected'` | ✅ | ✅ green |
| 92-03-02 | 03 | 1 | CLEAN-01 | — | `assumptions-fence` does NOT trigger on `import static org.assertj.core.api.Assumptions.assumeThat` (synthetic negative in `@TempDir`) | unit | `./mvnw test -Dtest='AssumptionsFencePredicateTest#givenAssertjAssumptionsImport_whenPredicateRuns_thenNoViolation'` | ✅ | ✅ green |
| 92-03-03 | 03 | 1 | CLEAN-01 | — | `./mvnw validate` exit 0 on current codebase (BackupStagingDirPerForkIT.java:12 AssertJ import does NOT trigger fence; package discrimination works) | gate | `./mvnw validate` → fence emits "[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders." | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java` — NEW in greenfield package `org.ctc.build` (first use of this package in the codebase): 2 `@Test` methods × `@TempDir` + ProcessBuilder pattern. Predicate string declared as `FENCE_REGEX` constant for visual lock-step with the pom.xml regex.

---

## Sign-Off

| Field | Value |
|-------|-------|
| Shipper | gsd-executor (inline, sequential) |
| Ship date | 2026-05-21 |
| Commit SHA short | _(filled by commit step)_ |
| `./mvnw verify` exit code | 0 (BUILD SUCCESS, 7:18 min) |
| Fence OK echo | confirmed in build log: `[CLEAN-01 build-guard] OK - no JUnit-Jupiter Assumptions offenders.` |
| JaCoCo line coverage | 88.8838 % (preserved from Plan 92-02 — no test/coverage regression) |
| SpotBugs BugInstance count | 0 |
| `git diff --stat src/main/` | empty (pom-only + test-only plan per CONTEXT D-10) |
| `nyquist_compliant` | `true` |
