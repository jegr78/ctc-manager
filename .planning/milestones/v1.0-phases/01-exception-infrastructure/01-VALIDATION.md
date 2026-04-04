---
phase: 1
slug: exception-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Mockito |
| **Config file** | `pom.xml` (Surefire + JaCoCo) |
| **Quick run command** | `./mvnw test -pl .` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | EXCP-01 | unit | `./mvnw test -Dtest=GlobalExceptionHandlerTest` | ❌ W0 | ⬜ pending |
| 01-02-01 | 02 | 1 | EXCP-02 | unit | `./mvnw test -Dtest=*ServiceTest` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/admin/controller/GlobalExceptionHandlerTest.java` — tests for EXCP-01

*Existing test infrastructure covers orElseThrow migration (EXCP-02) — existing service tests will verify exception messages.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Error page renders in Admin-Layout | EXCP-01 | Visual verification of layout rendering | Navigate to invalid entity URL, verify Sidebar/Header visible |
| Flash messages still work after handler | EXCP-01 | Verifies no interference with existing patterns | Submit form with validation error, verify flash message appears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
