---
phase: 77
slug: final-uat-jacoco-hold-round-trip-test-documentation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-14
approved: 2026-05-14
---

# Phase 77 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test 4.x + Failsafe |
| **Config file** | `pom.xml` (`maven-failsafe-plugin` default-it execution, Surefire for unit tests) |
| **Quick run command** | `./mvnw -Dit.test=BackupRoundTripIT verify` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | Quick: ~90s (single IT class). Full: ~5–7 min including Playwright E2E + JaCoCo. |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -Dit.test=BackupRoundTripIT verify` (or the narrower test command in the task's `<automated>` block)
- **After every plan wave:** Run `./mvnw verify` (all non-E2E tests + JaCoCo gate)
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` BUILD SUCCESS (full suite with Playwright E2E)
- **Max feedback latency:** 120 seconds per task commit; 7 minutes per wave merge

---

## Per-Task Verification Map

> Concrete task IDs are filled by the planner. The mapping below covers the four QUAL requirements and the canonical task shapes derived from CONTEXT.md D-01..D-20 and RESEARCH.md.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 77-01-01 | 01 | 1 | QUAL-02 | — | Round-trip preserves byte-equal entities through `backupObjectMapper` | integration | `./mvnw -Dit.test=BackupRoundTripIT\$H2RoundTripTests verify` | ❌ W1 — to be created in `BackupRoundTripIT` | ⬜ pending |
| 77-01-02 | 01 | 1 | QUAL-02 | — | MariaDB engine matches H2 wire shape under live JDBC | integration | `./mvnw -Ddocker.available=true -Dit.test=BackupRoundTripIT\$MariaDbRoundTripTests verify` | ❌ W1 — to be created in `BackupRoundTripIT` | ⬜ pending |
| 77-01-03 | 01 | 1 | QUAL-04 | — | Phase 75 rollback IT stays green (no Phase 77 edits) | integration | `./mvnw -Dit.test=BackupImportRollbackIT verify` | ✅ — Phase 75 | ⬜ pending |
| 77-02-01 | 02 | 2 | QUAL-01 | — | JaCoCo line coverage ≥ 0.82 measured + recorded | maven gate | `./mvnw verify` (fails build if < 0.82) | ✅ — `pom.xml` jacoco-maven-plugin | ⬜ pending |
| 77-03-01 | 03 | 2 | QUAL-05 | — | README "Backup & Restore" section renders with working cross-links | visual | `playwright-cli open https://github.com/jegr78/ctc-manager` | ❌ W2 — README edit | ⬜ pending |
| 77-03-02 | 03 | 2 | QUAL-05 | — | Wiki page `Backup-and-Restore` published, 3 screenshots render | visual | `playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` | ❌ W2 — external wiki repo | ⬜ pending |
| 77-03-03 | 03 | 2 | QUAL-05 | — | 3 screenshots exist under `.screenshots/77/` | filesystem | `test -f .screenshots/77/01-backup-page.png && test -f .screenshots/77/02-preview-screen.png && test -f .screenshots/77/03-import-banner.png` | ❌ W2 — playwright-cli capture | ⬜ pending |
| 77-04-01 | 04 | 3 | QUAL-01..05 | — | Final phase gate green | full e2e | `./mvnw verify -Pe2e` | ✅ — full suite | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.* No Wave 0 stubs needed:

- `BackupRoundTripIT` already exists (Phase 73) — Phase 77 extends in-place per D-01.
- Testcontainers BOM, MariaDBContainer, `@DynamicPropertySource` pattern all wired in Phase 75.
- JaCoCo gate `0.82` already configured in `pom.xml` (D-11 keeps it locked).
- Failsafe `*IT.java` inclusion already configured — new `@Nested` tests auto-detected.
- `BackupObjectMapperConfig` qualifier + 22 MixIns + 22 Restorers shipped Phase 72/73/75.
- `playwright-cli` skill available — `.screenshots/77/` directory is new but follows project convention.

---

## Manual-Only Verifications

> Phase 77 D-13 explicitly removes HUMAN-UAT. All verifications are AUTO-UAT via `77-AUTO-UAT.md`.

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | All phase behaviors have automated verification (Failsafe ITs + JaCoCo gate + playwright-cli visual checks) | — |

*All phase behaviors have automated verification.*

---

## MariaDB Conditional Coverage (Research Open Question)

`BackupRoundTripIT$MariaDbRoundTripTests` mirrors Phase 75 `BackupImportMariaDbSmokeIT` and uses `@EnabledIfSystemProperty(named = "docker.available", matches = "true")`. CI (`.github/workflows/ci.yml`) does NOT pass `-Ddocker.available=true` today, so this nested class is SKIPPED on CI by default — consistent with the Phase 75 precedent.

- Local-developer verification: `./mvnw -Ddocker.available=true -Dit.test=BackupRoundTripIT\$MariaDbRoundTripTests verify` (Docker daemon required).
- CI gating decision (raise `-Ddocker.available=true` to mandatory in `ci.yml`) is **deferred to Phase 79** per D-15 milestone-closer boundary. AUTO-UAT step 5 manually confirms a recent local MariaDB run.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (checkpoint tasks 77-03-02/77-03-03 use `checkpoint:human-verify` per CLAUDE.md `playwright-cli` policy — acceptable per plan-checker Dimension 8c)
- [x] Sampling continuity: no 3 consecutive automated tasks without verify (sequence: 77-01-01 auto → 77-02-01 auto → 77-03-01 auto → [checkpoints 77-03-02/77-03-03] → 77-04-01 auto → 77-05-01 auto)
- [x] Wave 0 covers all MISSING references (N/A — none required; existing infrastructure covers all phase requirements)
- [x] No watch-mode flags (all commands use one-shot `./mvnw verify` / `playwright-cli` / `curl` invocations)
- [x] Feedback latency < 120s per commit, < 7min per wave (single-IT runs ~90s; full `./mvnw verify -Pe2e` ~5–7min)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-14
