---
phase: 32
slug: layering-and-exception-fix
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-13
audited: 2026-04-14
---

# Phase 32 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito |
| **Config file** | `pom.xml` (surefire + failsafe) |
| **Quick run command** | `./mvnw test -Dtest=RaceGraphicServiceTest,RaceServiceTest,MatchdayServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 32-01-01 | 01 | 1 | ARCH-01 | — | RaceGraphicService relocated to admin.service | unit | `./mvnw test -Dtest=RaceGraphicServiceTest` | ✅ | ✅ green |
| 32-01-02 | 01 | 1 | ARCH-01 | — | TeamCardService decoupled from RaceService | unit | `./mvnw test -Dtest=RaceServiceTest` | ✅ | ✅ green |
| 32-01-03 | 01 | 1 | ARCH-01 | — | Zero admin imports in domain layer | structural | `grep -r "import org.ctc.admin" src/main/java/org/ctc/domain/` | ✅ | ✅ green |
| 32-02-01 | 02 | 1 | ARCH-02 | — | EntityNotFoundException for missing season | unit | `./mvnw test -Dtest=MatchdayServiceTest` | ✅ | ✅ green |
| 32-02-02 | 02 | 1 | ARCH-02 | — | BusinessRuleException for duplicate label | unit | `./mvnw test -Dtest=MatchdayServiceTest` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Audit 2026-04-14

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Manual-Only | 0 |
| Automated COVERED | 5 |

Reconstructed from phase artifacts (State B). All tests existed from TDD execution during phase implementation.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
