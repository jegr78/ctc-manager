---
phase: 57
slug: data-migration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-27
---

# Phase 57 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test 4.x |
| **Config file** | `pom.xml` (Surefire/Failsafe), `src/test/resources/application-dev.yml` (H2 in-memory) |
| **Quick run command** | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~10-15 seconds (single IT class), ~3-4 minutes (full verify with JaCoCo) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT,V4MigrationSmokeIT`
- **After every plan wave:** Run `./mvnw verify` (Surefire + Failsafe + JaCoCo)
- **Before `/gsd-verify-work`:** Full `./mvnw verify` must be green AND JaCoCo line coverage ≥ 82%
- **Max feedback latency:** ~15 seconds (per-task), ~4 minutes (per-wave)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD     | TBD  | TBD  | MIGR-02..05 | —          | N/A             | integration | `./mvnw test -Dtest=V4MigrateSeasonsToPhasesIT` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky · Map will be filled by gsd-planner; this row is a placeholder.*

---

## Wave 0 Requirements

- [ ] `src/test/java/db/migration/V4MigrateSeasonsToPhasesIT.java` — 6 test methods covering SC1-SC5 + NOT-NULL flip (D-16)
- [ ] `src/test/java/db/migration/V4MigrationSmokeIT.java` — `@SpringBootTest` smoke for end-to-end Spring + JPA wiring (D-18)
- [ ] Test fixtures: `seedLegacyData()` helper inserting 3 seasons (one with playoff, one without, one empty), 2 teams/season, 2 matchdays/season, 1 playoff (D-17)

*Existing test infrastructure (JUnit 5, Spring Boot Test, H2) covers all needs — no new framework install.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| MariaDB `MODIFY COLUMN ... UUID NOT NULL` produces working schema on real MariaDB instance | MIGR-04 | H2 ≠ MariaDB DDL semantics — automated tests run on H2 only; MariaDB syntax is statically verified but not executed in CI | Boot app with `local` profile against fresh local MariaDB (no V4 yet) seeded with V1+V2+V3 data; observe Flyway log line `V4__MigrateSeasonsToPhases` runs green; verify `DESCRIBE matchdays;` shows `phase_id` as `NOT NULL` |
| Empty production-like DB boots cleanly | MIGR-02..05 | Empty-DB safety is asserted in IT but final dev/local profile launch is the truest signal | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → app must start without Flyway errors |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (V4MigrateSeasonsToPhasesIT + V4MigrationSmokeIT)
- [ ] No watch-mode flags (Surefire/Failsafe run-once)
- [ ] Feedback latency < 15s (per-task IT)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
