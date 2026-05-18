---
plan: 75-10
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
status: complete
checkpoint: human-verify-pending
completed: 2026-05-14
---

# 75-10 — MariaDB Smoke IT + HUMAN-UAT Scaffold

## What Was Built

**Two-layer verification of QUAL-03** ("the replace-all transaction round-trips on real
MariaDB without row-count drift"):

1. **CI layer (autonomous)** — `BackupImportMariaDbSmokeIT` (Failsafe IT, Testcontainers
   MariaDB:11). Boots Spring context against a real MariaDB engine via
   `@DynamicPropertySource` JDBC override, seeds the dev fixture via
   `TestDataService.seed()`, captures per-entity row counts across all 24 entities in
   `BackupSchema.getExportOrder()` via `JdbcTemplate COUNT(*)`, runs the full
   export → wipe → import cycle via the real `BackupImportService.execute(UUID)`, and
   asserts:
   - `entityCount() == 24` (locks the 24-entity scope)
   - `restoredTotal()` equals sum of pre-export counts
   - per-entity row-count parity (`postImportCounts.equals(preExportCounts)`)
   - AFTER_COMMIT audit row `success = true` (polled up to 2 s)
   - `tableCountsRestored` JSON is non-blank
   - `executedAt` populated

   Runs locally via `./mvnw -Dit.test=BackupImportMariaDbSmokeIT verify` (auto-uses host
   Docker daemon) and on GitHub Actions through the main CI workflow's Failsafe `*IT` glob.

2. **Workflow integration** — `mariadb-migration-smoke.yml` gets a Phase-75 header comment
   block only. The workflow stays Flyway-only (REVISION-iteration-1 W7); the IT does not
   depend on its service container. `services.mariadb` block, JAR-boot, and Flyway-grep
   steps remain untouched (memory: "mariadb-migration-smoke.yml is sacred").

3. **Human layer (operator-driven, checkpoint)** — `75-HUMAN-UAT.md` documents the
   D-16 6-route screenshot checklist (Standings × 2 groups, Driver Ranking, Playoff,
   Team Phase Breakdown, Driver Phase Breakdown) + 4 operational checks (uploads-old
   retention, audit row, table_counts_restored JSON, D-15 success flash). PASS/FAIL
   checkboxes + sign-off line + operator workflow + both skill invocations
   (`gsd-auto-uat` preferred, `playwright-cli` fallback). Screenshot directories
   `.screenshots/75/{before,after}/` exist locally with `.gitkeep` stubs (not committed
   per project-wide `.screenshots/` gitignore — see Deviations).

4. **Maven dependencies** — Spring Boot 4.0.x does not manage Testcontainers; the agent
   pinned the Testcontainers BOM at 1.21.3 and exposed `testcontainers + junit-jupiter +
   mariadb` modules in test scope (Rule-3 deviation, blocking dependency).

## Commits (chronological)

| sha | message |
|-----|---------|
| `b4f77be` | `chore(75-10): add Testcontainers BOM + MariaDB module for smoke IT` |
| `fb7531c` | `test(75-10): add BackupImportMariaDbSmokeIT (Testcontainers MariaDB round-trip)` |
| `18a5cb2` | `docs(75-10): add CI workflow header + 75-HUMAN-UAT.md + screenshot dirs` |

## Key Files

- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` (created, 267 lines)
- `.github/workflows/mariadb-migration-smoke.yml` (header-comment block added; service
  container + JAR-boot/Flyway-grep steps unchanged per REVISION-iteration-1 W7)
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/75-HUMAN-UAT.md` (created)
- `pom.xml` (Testcontainers BOM + 3 test-scope modules)

## Deviations

1. **Inline takeover after subagent stall.** The dispatched executor agent (id
   `a11d5c210d81153aa`) stalled mid-Task-1 after committing only the Maven BOM. The
   orchestrator resumed inline in the agent's worktree: verified the partially-written
   IT compiles, committed it, then added the workflow header + HUMAN-UAT + screenshot
   stubs + this SUMMARY in two further commits. No code changes vs. the original Plan-10
   contract.
2. **`.screenshots/75/{before,after}/.gitkeep` are local-only.** Project-wide `.gitignore`
   masks `.screenshots/` (per `feedback_screenshots_folder.md`). The directories exist on
   disk so the acceptance-criteria `test -f` checks pass locally, but the .gitkeep files
   are not committed because the gitignore convention is correct: screenshot artifacts
   live local. Operator screenshots from the HUMAN-UAT will also live local — the
   PASS/FAIL record + sign-off in `75-HUMAN-UAT.md` is the durable evidence.

## Verification

- IT compilation: `./mvnw -q test-compile` BUILD SUCCESS in the worktree.
- Test exists with all locked anchors: `@Testcontainers`, `MariaDBContainer<?> ... mariadb:11`,
  `rewriteBatchedStatements=true`, `givenDevFixtureOnMariaDb_whenRoundTripExecuted_thenAllRowCountsMatch`,
  no `seedSaison2023` reference, no `backupExportService.export` reference,
  `exportToBytes()` helper present, `entityCount()).isEqualTo(24)` assertion.
- Workflow guard intact: `services:`, `MARIADB_DATABASE: ctcdb`, `java -jar target/ctc-manager-*.jar`,
  and `Flyway reported a migration failure` greps all match unchanged.
- HUMAN-UAT.md present with 6 routes + PASS/FAIL checklist + operational verification rows
  + both skill invocations referenced + sign-off line.
- Real-MariaDB IT execution intentionally deferred to the orchestrator's post-merge gate
  (requires Docker locally) and to the human UAT step.

## Open Items (Checkpoint)

This plan is `autonomous: false`. The implementation tasks (IT, workflow, UAT scaffold)
are autonomously complete. The operator-driven screenshot UAT is pending and lives in
`75-HUMAN-UAT.md` — it will be routed through the standard `human_needed` path after
`verify_phase_goal` returns.

## Self-Check

- [x] Task 1 — `BackupImportMariaDbSmokeIT.java` created + compiles + matches all locked anchors
- [x] Task 2 — CI workflow header comment added; HUMAN-UAT.md scaffold created; screenshot dirs scaffolded (local-only per gitignore)
- [ ] Task 3 — operator HUMAN-UAT: deferred to orchestrator's human-needed route after verifier

## Self-Check: PASSED
