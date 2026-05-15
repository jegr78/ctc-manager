---
phase: 77-final-uat-jacoco-hold-round-trip-test-documentation
executed: 2026-05-15T10:38:00Z
server_profile: dev,demo
total: 6
passed: 2
failed: 0
skipped: 4
---

# Auto-UAT Report: Phase 77

Phase 77 closes v1.10's QUAL deliverables (QUAL-01 + QUAL-02 + QUAL-04 + QUAL-05). This AUTO-UAT records the 6 D-13-locked verification items; item 2 (JaCoCo) is measured at plan-time, the remaining 5 items are completed by the post-PR-merge AUTO-UAT run via `playwright-cli` and `./mvnw verify -Pe2e`.

## Results

### 1. `./mvnw verify -Pe2e` BUILD SUCCESS (H2 + Rollback IT)

- **Status:** pending
- **Evidence:**
  - [ ] `BackupRoundTripIT$H2RoundTripTests`: BUILD SUCCESS
  - [ ] `BackupImportRollbackIT`: BUILD SUCCESS (Phase 75, D-14 untouched)
  - [ ] Full suite `./mvnw verify -Pe2e`: BUILD SUCCESS

### 2. JaCoCo Line Coverage Measured

- **Status:** passed
- **Evidence:**
  - Measured: 88.9% — 6.9% buffer over the 82% gate (D-11 / D-12).
  - Command: `awk -F',' 'NR>1{miss+=$8;cov+=$9}END{printf "%.1f%%\n", cov/(miss+cov)*100}' target/site/jacoco/jacoco.csv`
  - Captured at: `2026-05-15T07:39:00Z`
  - Raw: 7428 lines covered, 927 lines missed, 8355 total lines.
  - `pom.xml` JaCoCo minimum unchanged at `0.82`; excludes list unchanged (`git diff --stat pom.xml` shows 0 lines).

### 3. README "Backup & Restore" Section Renders on GitHub

- **Status:** pending
- **Evidence:**
  - [ ] `playwright-cli open https://github.com/jegr78/ctc-manager` → "Backup & Restore" section visible after "Features", before "Quick Start" (CD-05).
  - [ ] Cross-link to `docs/operations/import-runbook.md` resolves.
  - [ ] Cross-link to wiki page `../../wiki/Backup-and-Restore` resolves.

### 4. GitHub Wiki Page Exists with 3 Screenshots

- **Status:** pending
- **Evidence:**
  - [ ] `playwright-cli open https://github.com/jegr78/ctc-manager/wiki/Backup-and-Restore` → page renders.
  - [ ] All 3 screenshots load from `raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/*.png`.
  - [ ] Internal cross-links (to runbook on `blob/master/docs/operations/import-runbook.md`) work.

### 5. `BackupRoundTripIT$MariaDbRoundTripTests` GREEN (local run)

- **Status:** passed
- **Evidence:**
  - [x] Local-machine run: `./mvnw -Ddocker.available=true -Dit.test=BackupRoundTripIT\$MariaDbRoundTripTests verify` → BUILD SUCCESS.
  - Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 40.53 s — in org.ctc.backup.service.BackupRoundTripIT$MariaDbRoundTripTests`
  - MariaDB Testcontainers image: `mariadb:11` (pulled and started v11.8 in container)
  - Captured at: 2026-05-15T10:38:00Z
  - **Phase 77 CI activation (post-AUTO-UAT):** D-05 actually said the IT *should* run in CI on every PR + master push (GitHub Actions ubuntu-latest ships Docker). The `@EnabledIfSystemProperty(docker.available=true)` gate combined with the absence of that flag in `ci.yml` had silently skipped both MariaDB ITs in CI — contradicting D-05. Resolved by adding `-Ddocker.available=true` to both `ci.yml` mvnw steps and fixing `BackupImportMariaDbSmokeIT` (`@ActiveProfiles({"local","dev"})` for `TestDataService` access + dynamic `BackupSchema.getExportOrder().size()` source of truth). Both ITs are now ACTIVE and GREEN locally and will be ACTIVE in CI.
  - **Environment hotfix:** Required Testcontainers BOM upgrade `1.21.3 → 2.0.5` (Docker Engine 29+ minimum API ≥ 1.40; Testcontainers 1.x shaded docker-java sends API 1.32; gh:testcontainers/testcontainers-java#11235). Without the bump the run fails at `RyukResourceReaper.maybeStart` with `BadRequestException`. This is the only `pom.xml` diff in Phase 77 — exception to D-11/D-12/D-16 because (a) D-18 forbids NEW deps but allows upgrades on existing deps and (b) it is the only path to make QUAL-02's MariaDB half verifiable.

### 6. Three Screenshots Committed to `.screenshots/77/`

- **Status:** pending
- **Evidence:**
  - [ ] `test -f .screenshots/77/01-backup-page.png` returns 0.
  - [ ] `test -f .screenshots/77/02-preview-screen.png` returns 0.
  - [ ] `test -f .screenshots/77/03-import-banner.png` returns 0.

## Summary

| # | Test | Status | Evidence |
|---|------|--------|----------|
| 1 | `./mvnw verify -Pe2e` BUILD SUCCESS | pending | post-merge CI run |
| 2 | JaCoCo 88.9% ≥ 82% | passed | jacoco.csv (7428/8355 lines) |
| 3 | README "Backup & Restore" section renders | pending | playwright-cli post-merge |
| 4 | Wiki page + 3 screenshots | pending | playwright-cli post-merge |
| 5 | MariaDB round-trip green (local run) | passed | `mvn -Ddocker.available=true ...` → 1/1 PASS in 40.53 s |
| 6 | Screenshots committed to .screenshots/77/ | pending | filesystem post-merge |

2/6 passed (JaCoCo measured pre-merge; MariaDB round-trip confirmed locally on Testcontainers 2.0.5 + MariaDB 11.8); 4/6 pending post-merge AUTO-UAT execution. Final status updated by post-merge AUTO-UAT run.
