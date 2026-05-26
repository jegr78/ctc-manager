---
phase: 101
slug: backup-restore-covers-discord-schema-v8-v15
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-26
---

# Phase 101 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Playwright (Surefire for unit, Failsafe for `*IT.java`) |
| **Config file** | `pom.xml` (surefire-maven-plugin 3.x, failsafe-maven-plugin 3.x, jacoco-maven-plugin 0.8.x) |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` (Surefire) / `./mvnw verify -Dit.test=<ClassName> -DfailIfNoTests=false` (Failsafe) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~7-10 min full (unit ~90s, IT ~3-5min, E2E ~3min) |

---

## Sampling Rate

- **After every task commit:** Run targeted `./mvnw test -Dtest=<ClassName>` (or `-Dit.test=<ClassName>` for ITs) — ≤30s per test class
- **After every plan wave:** Run `./mvnw verify` (Unit + Integration, no Playwright) — ~4 min
- **Before `/gsd-verify-work`:** `./mvnw clean verify -Pe2e` must be green
- **Max feedback latency:** 30s for targeted tests; 4 min for wave gate

---

## Per-Task Verification Map

> Populated by the planner after PLAN.md is written.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 101-XX-YY | XX | N | REQ-XX | T-101-XX / — | … | unit/it/e2e | `./mvnw test -Dtest=…` | ✅ / ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Phase 101 extends existing test infrastructure — no new framework installs. Pre-existing assets the new tests depend on:

- `BackupSchemaGuardTest.java` — assertions must flip from `SCHEMA_VERSION=1` / `EXPORT_ORDER.size()=24` to `=2` / `=26` BEFORE any restorer or schema change (else build is permanently red until end-of-phase).
- `BackupRoundTripIT.java` — H2 round-trip pattern, extends to new entities.
- `BackupImportMariaDbSmokeIT.java` — MariaDB Testcontainer opt-in via `@EnabledIfSystemProperty(named="docker.available", matches="true")` (per RESEARCH §11).
- `TestDataService.seed()` — does NOT seed `DiscordPost` or non-default `DiscordGlobalConfig` rows; new round-trip tests must add explicit `@BeforeEach` Discord fixture seeding (per RESEARCH OQ-3).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Cross-guild restore produces orphan IDs surfaced via existing `DiscordApiException.NotFound` typed-catch | D-06 (undefined behaviour) | No second Discord guild in CI; behaviour deliberately undefined per CONTEXT.md | Operator: restore a backup onto a non-source guild, observe that the next `Re-Post` click flashes a guild-mismatch badge instead of silently 404-looping. |
| `webhook_token` PII implication on backup ZIP file-level access control | D-06 | Filesystem-level concern; not testable in-process | Operator: read DOCS-02 "Backup & Restore semantics" section, confirm understanding before exporting. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s for targeted, < 4min for wave gate
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
