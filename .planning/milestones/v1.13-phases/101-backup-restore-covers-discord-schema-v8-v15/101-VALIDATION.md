---
phase: 101
slug: backup-restore-covers-discord-schema-v8-v15
status: planned
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-26
updated: 2026-05-26
---

# Phase 101 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test (Surefire for unit, Failsafe for `*IT.java`) |
| **Config file** | `pom.xml` (surefire-maven-plugin 3.x, failsafe-maven-plugin 3.x, jacoco-maven-plugin 0.8.x) |
| **Quick run command** | `./mvnw test -Dtest=<ClassName>` (Surefire) / `./mvnw verify -Dit.test=<ClassName> -DfailIfNoTests=false` (Failsafe) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~7-10 min full (unit ~90s, IT ~3-5min adds 13 round-trip + 1 lenient-v1 + 1 byte-equality methods → ~+90s, E2E ~3min) |

---

## Sampling Rate

- **After every task commit:** `./mvnw verify -Dit.test=<ClassName> -DfailIfNoTests=false` — ≤30s per test class
- **After every plan wave:** `./mvnw verify` (Unit + Integration, no Playwright) — ~4 min
- **Before `/gsd-verify-work`:** `./mvnw clean verify -Pe2e` must be green
- **Max feedback latency:** 30s for targeted tests; 4 min for wave gate

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 101-01-T1 | 01 | 0 | D-09, D-14 | — | SCHEMA_VERSION constant bumped to 2 with paired guard-test assertion; pre-flip ordering guarantees green build before Plan 02 | integration | `./mvnw verify -Dit.test=BackupSchemaGuardTest -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-02-T1 | 02 | 1 | D-01, D-03, D-04, D-08, D-12, D-14 | T-101-01 (accept via DOCS-02), T-101-04 (accept, downstream guarded) | Package filter expanded; 2 MixIns + 2 Restorers created; @PostConstruct startup-validators see 26-table parity; guard-test entity-count assertion flipped — atomic 5-change commit | integration | `./mvnw clean verify -Dit.test='BackupSchemaGuardTest,BackupRoundTripIT,BackupImportMariaDbSmokeIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ (mix create + modify) | ⬜ pending |
| 101-03-T1 | 03 | 2 | D-02, D-12 | T-101-01 (accept) | MatchRestorer INSERT_SQL +8 V10/V11 columns | integration | `./mvnw verify -Dit.test='BackupRoundTripIT,BackupSchemaGuardTest,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-03-T2 | 03 | 2 | D-02, D-12 | — | TeamRestorer INSERT_SQL_PASS1 +1 V9 column | integration | `./mvnw verify -Dit.test='BackupRoundTripIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-03-T3 | 03 | 2 | D-02, D-12 | — | MatchdayRestorer INSERT_SQL +2 V15 columns | integration | `./mvnw verify -Dit.test='BackupRoundTripIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-03-T4 | 03 | 2 | D-02, D-12 | — | SeasonRestorer INSERT_SQL +2 V13 columns | integration | `./mvnw verify -Dit.test='BackupRoundTripIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-04-T1 | 04 | 2 | D-10, D-11 | T-101-03 (mitigate via manifest-driven import) | Importer accepts `schema_version IN (1, 2)` via SUPPORTED_SCHEMA_VERSIONS constant | integration | `./mvnw verify -Dit.test='BackupSchemaGuardTest,BackupRoundTripIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-04-T2 | 04 | 2 | D-17 | T-101-03 (mitigate — fence test asserts v3+ refusal) | Programmatic v1 ZIP imports successfully; Discord tables empty post-import; V8-V15 columns NULL on existing entities | integration | `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=false` | ❌ Wave 0 (new file) | ⬜ pending |
| 101-05-T1 | 05 | 3 | D-13, D-15 | — | 13 per-field regression-fence round-trips on V8-V15 columns | integration (TDD) | `./mvnw verify -Dit.test=BackupDiscordFieldRoundTripIT -DfailIfNoTests=false` | ❌ Wave 0 (new file) | ⬜ pending |
| 101-05-T2 | 05 | 3 | D-13, D-16 | — | DiscordGlobalConfig + DiscordPost byte-equality on H2 + MariaDB-opt-in | integration | `./mvnw verify -Dit.test=BackupRoundTripIT -DfailIfNoTests=false` | ✅ exists (modify) | ⬜ pending |
| 101-06-T1 | 06 | 4 | D-09, D-14 | T-101-01 (mitigate via DOCS-02) | PROJECT.md wire-contract paragraph reflects 26-entity scope + SCHEMA_VERSION 2 + lenient v1 acceptance | docs | `grep -c "26-entity\|SCHEMA_VERSION = 2\|26 operative entities\|v1.13 Phase 101 update" .planning/PROJECT.md` | ✅ exists (modify) | ⬜ pending |
| 101-06-T2 | 06 | 4 | D-09 | — | STATE.md baselines flipped + Roadmap-Evolution event added | docs | `grep -c "SCHEMA_VERSION.*2\|26 entities\|Phase 101 closed" .planning/STATE.md` | ✅ exists (modify) | ⬜ pending |
| 101-06-T3 | 06 | 4 | D-06 | T-101-01 (HIGH — mitigation), T-101-02 (MEDIUM — mitigation) | DOCS-02 runbook adds § Backup & Restore semantics covering single-guild + webhook_token secrecy + v1-compat | docs | `grep -c "Backup & Restore semantics\|single-guild\|webhook_token\|v1-backup compatibility" docs/operations/discord-integration.md` | ✅ exists (modify) | ⬜ pending |
| 101-06-T4 | 06 | 4 | D-09 | — | README user-facing entity-count flipped 24 → 26 | docs | `grep -c "26 entity tables" README.md` | ✅ exists (modify) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave Structure

| Wave | Plans | Atomic | Sequential / Parallel |
|------|-------|--------|------------------------|
| 0 | 101-01 (SCHEMA_VERSION + guard-test pre-flip) | yes | sequential (must precede everything) |
| 1 | 101-02 (package filter + MixIns + Restorers + entity-count flip — atomic 5-change commit) | yes | sequential (single atomic commit) |
| 2 | 101-03 (existing Restorers extend INSERT_SQL — 4 atomic tasks) + 101-04 (lenient version check + v1-acceptance IT — 2 atomic tasks) | yes | inline-sequential per CLAUDE.md v1.13 D-05; do NOT spawn parallel-wave subagents |
| 3 | 101-05 (regression fence + byte-equality — 2 atomic tasks) | yes | inline-sequential |
| 4 | 101-06 (docs sweep — 4 atomic tasks) | yes | inline-sequential |

All execution is **inline-sequential on `gsd/v1.13-discord-integration`** per CLAUDE.md "Inline Sequential is the Default" + CONTEXT branch invariants. Wave numbers are sequencing aids only — no parallel subagent dispatch.

---

## Wave 0 Requirements

Phase 101 extends existing test infrastructure — no new framework installs. Pre-existing assets the new tests depend on:

- ✅ `BackupSchemaGuardTest.java` — assertions are flipped IN-PHASE (Plan 01 + Plan 02), not before; the Plan-01-first ordering rule is the load-bearing constraint per RESEARCH §Pitfall 6.
- ✅ `BackupRoundTripIT.java` — H2 + MariaDB round-trip pattern, extended by Plan 05 Task 2.
- ✅ `BackupImportMariaDbSmokeIT.java` — MariaDB Testcontainer opt-in via `@EnabledIfSystemProperty(named="docker.available", matches="true")` (RESEARCH §RQ-10).
- ✅ `TestDataService.seed()` — does NOT seed `DiscordPost` or non-default `DiscordGlobalConfig` rows (RESEARCH §OQ-3); Plan 05 owns the Discord fixture in `@BeforeEach`.
- ❌ `BackupDiscordFieldRoundTripIT.java` — NEW in Plan 05 Task 1 (13 per-field regression-fence tests).
- ❌ `BackupLenientV1AcceptanceIT.java` — NEW in Plan 04 Task 2 (lenient v1 acceptance + v3-refusal fence).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Cross-guild restore produces orphan IDs surfaced via existing `DiscordApiException.NotFound` typed-catch | D-06 (undefined behaviour) | No second Discord guild in CI; behaviour deliberately undefined per CONTEXT.md | Operator: restore a backup onto a non-source guild, observe that the next `Re-Post` click flashes a guild-mismatch badge instead of silently 404-looping. |
| `webhook_token` PII implication on backup ZIP file-level access control | T-101-01 | Filesystem-level concern; not testable in-process | Operator: read DOCS-02 "§ Backup & Restore semantics" (Plan 06 Task 3), confirm understanding before exporting. |
| Wiki sync of `docs/operations/discord-integration.md` § N additions | CLAUDE.md "Documentation Maintenance" | GitHub Wiki cannot be pushed from inside the repo via Maven — operator copies the section manually after PR merge | Operator: after merge, open the wiki page corresponding to the discord-integration runbook and add the new § Backup & Restore semantics. |

---

## Phase Gate Summary

- Each task commit gated by `./mvnw verify -Dit.test=<ClassName>` (≤30s).
- Each wave merge gated by `./mvnw verify` (~4 min).
- Phase end gated by `./mvnw clean verify -Pe2e` green AND coverage ≥ 88.88% baseline (CLAUDE.md "Code Coverage" + Baselines-to-Preserve).
- SpotBugs gate maintained: 0 new BugInstances (CLAUDE.md "Static Analysis").
- CodeQL gate maintained: no new HIGH/CRITICAL findings (CLAUDE.md "CodeQL SAST").
- ASVS L1 + STRIDE: T-101-01 mitigated via DOCS-02 (Plan 06), T-101-02 mitigated via DOCS-02 (Plan 06), T-101-03 mitigated via manifest-driven import + v3-fence test (Plan 04), T-101-04 accepted (downstream guards in Phase 93 INFRA-02 SSRF whitelist).

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (BackupDiscordFieldRoundTripIT + BackupLenientV1AcceptanceIT created in Plans 04 + 05; pre-existing IT modifications scheduled in Plans 02/03/05)
- [x] No watch-mode flags
- [x] Feedback latency < 30s for targeted, < 4min for wave gate
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planner-approved 2026-05-26
