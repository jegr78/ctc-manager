---
phase: 12
slug: security-hardening-recovery
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ |
| **Config file** | `pom.xml` (Surefire + Failsafe) |
| **Quick run command** | `./mvnw test -Dtest=FileStorageServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=FileStorageServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | SECU-01, SECU-02 | T-12-01 | Restore 11 security test methods (SSRF + path traversal) | unit | `./mvnw test -Dtest=FileStorageServiceTest` | ❌ W0 | ⬜ pending |
| 12-01-02 | 01 | 1 | SECU-01 | T-12-02 | validateHostname() blocks localhost, private IPs, link-local | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenLocalhostUrl*` | ❌ W0 | ⬜ pending |
| 12-01-03 | 01 | 1 | SECU-02 | T-12-03 | validateNoPathTraversal() + validatePathWithinUploadDir() reject traversal | unit | `./mvnw test -Dtest=FileStorageServiceTest#givenPathTraversalFilename*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Restore 11 test methods into `FileStorageServiceTest.java` (from commit `5b3a58b` diff)
- [ ] Restore 3 private methods + 6 call sites into `FileStorageService.java` (from commit `84e8896`)

*No new test infrastructure needed — `FileStorageServiceTest.java` already exists with correct imports.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
