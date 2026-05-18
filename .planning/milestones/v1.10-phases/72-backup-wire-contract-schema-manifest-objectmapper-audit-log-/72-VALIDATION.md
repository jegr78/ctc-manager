---
phase: 72
slug: backup-wire-contract-schema-manifest-objectmapper-audit-log
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-11
---

# Phase 72 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

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

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 72-01-XX | 01 (BackupSchema + topo-sort) | 1 | SCHEMA-01, IMPORT-08 | — | Topo-sort excludes `org.ctc.backup.*` package (structural exclusion) | IT | `./mvnw -Dit.test=BackupSchemaTopologyIT,BackupSchemaExclusionIT verify -Pe2e` | ❌ W0 | ⬜ pending |
| 72-02-XX | 02 (BackupManifest record) | 2 | SCHEMA-02 | — | N/A (pure data record) | Unit | `./mvnw -Dtest=BackupManifestSerializationTest test` | ❌ W0 | ⬜ pending |
| 72-03-XX | 03 (BackupObjectMapperConfig) | 2 | SCHEMA-04 | — | Default `ObjectMapper` keeps `FAIL_ON_UNKNOWN_PROPERTIES=false`; backup mapper is isolated `@Qualifier` bean | IT | `./mvnw -Dit.test=BackupObjectMapperConfigIT verify -Pe2e` | ❌ W0 | ⬜ pending |
| 72-04-XX | 04 (Flyway V7 + DataImportAudit entity/repo) | 3 | SCHEMA-03 | — | `data_import_audit` columns match D-09 DDL on H2 + MariaDB | IT | `./mvnw -Dit.test=V7DataImportAuditMigrationIT verify -Pe2e` | ❌ W0 | ⬜ pending |
| 72-05-XX | 05 (PROJECT.md decisions + wire-contract docs) | 3 | SCHEMA-01..04 (anchor), IMPORT-08 (anchor) | — | N/A (doc-only) | Manual grep | `grep -q "data_import_audit out of export scope" PROJECT.md && grep -q "Backup Wire Contract" PROJECT.md` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** All five task groups have an automated check (4 IT/Unit + 1 grep). No three consecutive tasks rely on manual verification.

---

## Wave 0 Requirements

> Wave 0 = test scaffolding that must exist before the implementation tasks run. The planner places these stubs in plan 01 (Wave 1) as the FIRST task of each implementation wave.

- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java` — stub `@SpringBootTest` shell, asserts `BackupSchema` bean is present (RED until D-04 topo-sort lands)
- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaExclusionIT.java` — stub: asserts `DataImportAudit` is NOT in `getExportOrder()` (RED until D-06 package filter lands)
- [ ] `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` — stub: Surefire unit; asserts JSON shape via backup `ObjectMapper`
- [ ] `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` — stub: injects both qualifiers, asserts they are distinct
- [ ] `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` — stub: JDBC metadata column assertions
- [ ] No new framework install needed — JUnit 5 / Mockito / Spring Boot Test / JaCoCo already configured in pom.xml.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| PROJECT.md "Key Decisions" row + "Backup Wire Contract (v1.10)" subsection (D-15) | SCHEMA-01..04 anchor | Documentation update — content review, not behavior | After commit, open `PROJECT.md`, verify (a) `data_import_audit` row in Key Decisions table with `✓ v1.10`, (b) new subsection enumerates integer SCHEMA_VERSION + per-entity ZIP layout + JPA-Metamodel topo-sort + 23/24-entity scope decision |
| REQUIREMENTS.md EXPORT-04 footnote referencing D-01/D-02/D-03 overrides | EXPORT-04 traceability | Doc-only override note (CONTEXT decision, not behavioral) | Open `REQUIREMENTS.md` §EXPORT-04, verify footnote/inline note pointing to `72-CONTEXT.md` for the 23/24-entity scope adjustment |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (5 IT/unit test stubs)
- [ ] No watch-mode flags
- [ ] Feedback latency < 40 s (quick), 8 min (full)
- [ ] `nyquist_compliant: true` set in frontmatter when planner finalizes the per-task map

**Approval:** pending
