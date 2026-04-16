---
phase: 39
slug: entity-cross-linking
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-16
---

# Phase 39 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Jsoup (HTML parsing) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 39-01-01 | 01 | 1 | CONT-02 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*teamStandings*Link*` | ✅ | ✅ green |
| 39-01-02 | 01 | 1 | CONT-03 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*driverRanking*Link*` | ✅ | ✅ green |
| 39-01-03 | 01 | 1 | CONT-04 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*matchday*DriverLink*` | ✅ | ✅ green |
| 39-01-04 | 01 | 1 | CONT-08 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*teamProfile*Drivers*` | ✅ | ✅ green |
| 39-01-05 | 01 | 1 | CONT-02,CONT-04 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*index*Link*` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Integration test stubs in `SiteGeneratorServiceTest.java` for link verification using Jsoup `select("a")` assertions
- [ ] Existing test infrastructure covers framework requirements — no new dependencies needed

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual hover effect on entity links | D-05, D-06 | CSS hover states not testable in Jsoup | Inspect link hover in browser: color changes to `#b3e5fc`, underline appears |
| Link color matches `--accent` (#4fc3f7) | D-05 | CSS variable resolution not testable in integration tests | Visual check in browser |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** verified 2026-04-16

---

## Validation Audit 2026-04-16

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

All 5 task verification entries have automated tests that pass green. 34 total tests in SiteGeneratorServiceTest, 959 in full suite.
