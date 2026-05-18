---
phase: 72
slug: backup-wire-contract-schema-manifest-objectmapper-audit-log
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-11
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 72 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
>
> **Approved retroactively 2026-05-18 via Phase 87 / Plan 87-02** — all referenced test files exist on disk in `src/test/java/` and run green under targeted Surefire + Failsafe invocation (see "Validation Audit 2026-05-18" block at bottom for CI evidence).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + Spring Boot Test 4.0.6 |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) — no Wave 0 install needed |
| **Quick run command** | `./mvnw -Dtest='BackupSchema*,BackupManifest*,BackupObjectMapperConfig*,DataImportAudit*,V7DataImportAudit*' test` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~25–40 s (quick), ~6–8 min (full) |

---

## Sampling Rate

- **After every task commit:** Run quick command (scoped to phase 72 test classes).
- **After every plan wave:** Run `./mvnw test` (Surefire only — Failsafe ITs in full suite).
- **Before `/gsd-verify-work`:** Full suite (`./mvnw verify`) must be green AND JaCoCo coverage ≥ 82 %.
- **Max feedback latency:** ~40 s (quick), ~8 min (full).

---

## Per-Task Verification Map

> Filled by the planner — each PLAN.md task maps to one of these rows. Map is finalized when plan-checker passes.
>
> **Post-hoc evidence (2026-05-18):** Plan 87-02 audit confirmed all referenced test files exist on disk; targeted Surefire + Failsafe runs returned BUILD SUCCESS for the full Phase 72 test set (13 ITs / 4 Surefire classes green).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Test File | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-----------|--------|
| 72-01-XX | 01 (BackupSchema + topo-sort) | 1 | SCHEMA-01, IMPORT-08 | — | Topo-sort excludes `org.ctc.backup.*` package (structural exclusion) | IT | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupSchemaTopologyIT,BackupSchemaExclusionIT` | `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java`, `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java`, `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` | ✅ green |
| 72-02-XX | 02 (BackupManifest record) | 2 | SCHEMA-02 | — | N/A (pure data record) | Unit | `./mvnw test -Dtest=BackupManifestSerializationTest` | `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` | ✅ green |
| 72-03-XX | 03 (BackupObjectMapperConfig) | 2 | SCHEMA-04 | — | Default `ObjectMapper` keeps `FAIL_ON_UNKNOWN_PROPERTIES=false`; backup mapper is isolated `@Qualifier` bean | IT | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupObjectMapperConfigIT` | `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` | ✅ green |
| 72-04-XX | 04 (Flyway V7 + DataImportAudit entity/repo) | 3 | SCHEMA-03 | — | `data_import_audit` columns match D-09 DDL on H2 + MariaDB | IT | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=V7DataImportAuditMigrationIT` | `src/test/java/db/migration/V7DataImportAuditMigrationIT.java`, `src/test/java/org/ctc/backup/audit/DataImportAuditServiceTest.java`, `src/test/java/org/ctc/backup/audit/DataImportAuditSerializationTest.java` | ✅ green |
| 72-05-XX | 05 (PROJECT.md decisions + wire-contract docs) | 3 | SCHEMA-01..04 (anchor), IMPORT-08 (anchor) | — | N/A (doc-only) | Manual grep | `grep -q "data_import_audit" .planning/PROJECT.md && grep -q "Backup Wire Contract" .planning/PROJECT.md` | `.planning/PROJECT.md` (Key Decisions table + Wire Contract subsection) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** All five task groups have an automated check (4 IT/Unit + 1 grep). No three consecutive tasks rely on manual verification.

---

## Wave 0 Requirements

> Wave 0 = test scaffolding that must exist before the implementation tasks run. The planner places these stubs in plan 01 (Wave 1) as the FIRST task of each implementation wave.
>
> **Retroactive note (2026-05-18 / Plan 87-02):** Wave 0 stubs were created in-flight during v1.10 (commits in `60f5f915^` history). All stubs have since matured into real assertion-bearing tests on disk — satisfied retroactively per Phase 87 / Plan 87-02 audit. Checkboxes flipped to `[x]` based on file-existence confirmation:

- [x] `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` — full `@SpringBootTest` IT; asserts `BackupSchema` bean is present + 24-entity topo-sort order is acyclic (RED→GREEN via D-04 topo-sort)
- [x] `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` — asserts `DataImportAudit` is NOT in `getExportOrder()` (RED→GREEN via D-06 package filter); additionally `BackupSchemaGuardTest` covers SCHEMA-01 bean wiring
- [x] `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` — Surefire unit; asserts snake_case JSON shape via backup `ObjectMapper`
- [x] `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` — injects `@Primary` + `@Qualifier("backupObjectMapper")`, asserts they are distinct beans with different feature flags
- [x] `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` — JDBC metadata column assertions for `data_import_audit` table after Flyway V7 runs
- [x] No new framework install needed — JUnit 5 / Mockito / Spring Boot Test / JaCoCo already configured in `pom.xml`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions | Status |
|----------|-------------|------------|-------------------|--------|
| PROJECT.md "Key Decisions" row + "Backup Wire Contract (v1.10)" subsection (D-15) | SCHEMA-01..04 anchor | Documentation update — content review, not behavior | After commit, open `.planning/PROJECT.md`, verify (a) `data_import_audit` reference in Key Decisions context, (b) new subsection enumerates integer SCHEMA_VERSION + per-entity ZIP layout + JPA-Metamodel topo-sort + 23/24-entity scope decision | ✅ verified 2026-05-18 (grep returned 3 hits for `data_import_audit` and 1 hit for `Backup Wire Contract` in `.planning/PROJECT.md`) |
| REQUIREMENTS.md EXPORT-04 footnote referencing D-01/D-02/D-03 overrides | EXPORT-04 traceability | Doc-only override note (CONTEXT decision, not behavioral) | Open `.planning/milestones/v1.10-REQUIREMENTS.md` §EXPORT-04, verify footnote/inline note pointing to `72-CONTEXT.md` for the 23/24-entity scope adjustment | ✅ verified 2026-05-18 (archived in `.planning/milestones/v1.10-REQUIREMENTS.md`; v1.10-MILESTONE-AUDIT.md status: passed 2026-05-15) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or post-hoc evidence (5/5 tasks have a real test file or doc-grep evidence on disk)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (5 IT/unit test stubs all matured into assertion-bearing tests)
- [x] No watch-mode flags
- [x] Feedback latency < 40 s (quick), 8 min (full)
- [x] `nyquist_compliant: true` set in frontmatter (approved retroactively via Phase 87 / Plan 87-02)

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Audit method:** retroactive — Phase 72 shipped 2026-05-11 (commits aggregated in v1.10 closer 79-09). Plan 87-02 audited the existing test surface against the 5 requirements (SCHEMA-01, SCHEMA-02, SCHEMA-03, SCHEMA-04, IMPORT-08) and confirmed all 5 are COVERED by 8 existing test classes on disk. No new tests were generated.

**CI evidence:**

- **Targeted Phase 72 test run (Plan 87-02, 2026-05-18 09:13–09:15 local):**
  - `./mvnw test -Dtest='BackupSchemaGuardTest,BackupManifestSerializationTest,DataImportAuditServiceTest,DataImportAuditSerializationTest'` → BUILD SUCCESS, 42.9 s
  - `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupSchemaTopologyIT,BackupSchemaExclusionIT,BackupObjectMapperConfigIT,V7DataImportAuditMigrationIT'` → BUILD SUCCESS, 13/13 ITs, 0 failures, 0 errors, 30.4 s
- **Full-suite CI baseline:** Run-id `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, conclusion: success, 2026-05-18T01:30:27Z) — full `./mvnw verify -Pe2e` green, JaCoCo gate held at ≥ 82 %.
- **Originating v1.10 verification:** `72-VERIFICATION.md` `status: passed, must_haves_verified: 7/7` (2026-05-11) — captured during the in-flight Phase 72 execution.

**Requirements coverage matrix (audit result):**

| REQ-ID | Existing tests | Result |
|--------|----------------|--------|
| SCHEMA-01 | `BackupSchemaGuardTest`, `BackupSchemaTopologyIT`, `BackupSchemaExclusionIT` | ✅ COVERED |
| SCHEMA-02 | `BackupManifestSerializationTest` | ✅ COVERED |
| SCHEMA-03 | `V7DataImportAuditMigrationIT`, `DataImportAuditServiceTest`, `DataImportAuditSerializationTest` | ✅ COVERED |
| SCHEMA-04 | `BackupObjectMapperConfigIT` | ✅ COVERED |
| IMPORT-08 | `BackupSchemaExclusionIT` (package-filter assertion: `org.ctc.backup.*` excluded from `getExportOrder()`) | ✅ COVERED |

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-02
