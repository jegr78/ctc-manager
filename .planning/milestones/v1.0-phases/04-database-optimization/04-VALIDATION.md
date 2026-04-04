---
phase: 4
slug: database-optimization
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (via spring-boot-starter-test) |
| **Config file** | `pom.xml` (Surefire/Failsafe plugins) |
| **Quick run command** | `./mvnw test` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw verify`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | DBIX-01 | integration | `./mvnw verify` | ✅ (implicit via 73 existing test files using H2 + Flyway) | ⬜ pending |
| 04-02-01 | 02 | 1 | DBIX-02 | integration | `./mvnw verify` | ✅ (existing tests validate EntityGraph compatibility) | ⬜ pending |
| 04-02-02 | 02 | 1 | DBIX-02 | manual | Run with `show-sql: true`, verify JOIN in SQL output | Manual (D-06) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. The V2 migration is automatically validated by all existing tests that use H2 + Flyway. EntityGraph annotations do not change test behavior, only query efficiency.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| V2 migration runs on MariaDB | DBIX-01 | MariaDB not available in CI test environment | `docker compose up --build -d`, verify app starts without migration errors |
| EntityGraph produces JOIN queries | DBIX-02 | User decision D-06: no dedicated query-count assertions | Run with `show-sql: true`, verify JOIN SQL in log output |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
