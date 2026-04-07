---
phase: 18
slug: merge-ui
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring MockMvc |
| **Config file** | `pom.xml` (Surefire + Failsafe, JaCoCo) |
| **Quick run command** | `./mvnw test -Dtest=DriverMergeServiceTest,DriverControllerTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=DriverMergeServiceTest,DriverControllerTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-01-01 | 01 | 1 | MERGE-03 | — | N/A | unit | `./mvnw test -Dtest=DriverMergeServiceTest` | ✅ (needs new nested class) | ⬜ pending |
| 18-01-02 | 01 | 1 | MERGE-01 | — | N/A | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (needs new methods) | ⬜ pending |
| 18-01-03 | 01 | 1 | MERGE-02 | — | N/A | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (needs new methods) | ⬜ pending |
| 18-01-04 | 01 | 1 | MERGE-03 | — | N/A | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (needs new methods) | ⬜ pending |
| 18-01-05 | 01 | 1 | MERGE-04 | — | N/A | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (needs new methods) | ⬜ pending |
| 18-01-06 | 01 | 1 | MERGE-04 | T-18-01 | Self-merge prevented | integration | `./mvnw test -Dtest=DriverControllerTest` | ✅ (needs new methods) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `DriverControllerTest` — add new test methods for merge endpoints (file exists, needs new methods)
- [ ] `DriverMergeServiceTest` — add `PreviewMergeTests` nested class (file exists, needs new nested class)

*Existing infrastructure covers all phase requirements. No new test framework or fixture installation needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Merge button visible between Edit and Delete | MERGE-01 | Visual position verification | Open `/admin/drivers/{id}`, verify Merge button appears in toolbar between Edit and Delete |
| JS confirm dialog shows correct driver names | MERGE-04 | Browser JS execution | Click "Confirm Merge", verify dialog shows source and target PSN IDs |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
