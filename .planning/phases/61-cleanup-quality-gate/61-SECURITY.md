---
phase: 61
slug: cleanup-quality-gate
status: verified
threats_open: 0
asvs_level: standard
created: 2026-05-02
---

# Phase 61 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| HTTP request → Spring controller | Existing CSRF/auth chain (Spring Security) unchanged; Phase 61 only deletes routes / removes legacy paths | Admin form data, IDs |
| Flyway → DB schema | V6 migration runs as DB owner; IRREVERSIBLE schema changes (drops `seasons.race_scoring_id`, `match_scoring_id`, bridge `season_phases.season_id` etc.) | Schema metadata |
| Test → embedded H2 / MariaDB CI | RANDOM_PORT @SpringBootTest; ephemeral test DB; no prod data; MariaDB pre-merge smoke gate added | Synthetic test fixtures only |
| Stubbed GoogleSheetsService → DriverSheetImportService | `@TestConfiguration @Bean @Primary` test-scope override; replaces OAuth-protected service in 61-04 E2E | None — no network IO |
| Playwright headless Chromium → admin UI | Ephemeral browser context per `@BeforeEach`; no persistent cookies | E2E click streams |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-61-01-01 | I (Information disclosure) | ROADMAP.md / PROJECT.md rationale entries | accept | `.planning/` is checked into the repo by design; no secrets/PII | closed |
| T-61-01-02 | T (Tampering) | Audit-trail integrity | accept | Standard git commit signing + branch protection apply; doc-only update weakens nothing | closed |
| T-61-02-01 | E (Elevation of privilege) | PlayoffController removed routes | accept | Deleted routes returned 404; Spring Security still applies before route resolution. Negative-route test (T06) prevents accidental re-introduction | closed |
| T-61-02-02 | T (Tampering) | Matchday/Playoff `getSeason()` convenience getter | mitigate | Read-only `return phase != null ? phase.getSeason() : null` (Matchday.java:53-58, Playoff.java:61-66). Legacy `season` field removed by V6, so no setter could ever be generated | closed |
| T-61-02-03 | I (Information disclosure) | calculateStandingsLegacy removal | accept | Same standings data as phase-aware path; removal reduces audit surface | closed |
| T-61-02-04 | D (Denial of service) | Hibernate `ddl-auto=validate` startup gate | mitigate | All four profiles set `spring.jpa.hibernate.ddl-auto: validate`; `./mvnw verify` runs in CI gate before deploy (`.github/workflows/ci.yml`) | closed |
| T-61-02-05 | R (Repudiation) | Tracked Behavior Change (D-03 endpoint 404) | mitigate | 61-02-SUMMARY.md lines 62/99/121/131 explicitly call out 5xx response change for `/admin/playoffs/{id}/add-season` and `/admin/playoffs/{id}/remove-season` | closed |
| T-61-03-01 | D (data loss) | V6 destructive schema change in prod | mitigate | `V6__CleanupLegacySeasonColumns.java:33` carries IRREVERSIBLE Javadoc; V6MigrationTest (4 `@Test` methods, INFORMATION_SCHEMA assertions) runs in `./mvnw verify`; ops-backup recommendation captured in 61-03-SUMMARY.md `## Tracked Behavior Changes #1` | closed |
| T-61-03-02 | T (Tampering) | Flyway checksum integrity for V1-V5 | mitigate (with documented exception) | V1-V4 untouched in Phase 61. **V5 rewritten in commit `6db56d4` (UAT-03 escape fix)**: original V5 SQL form had never successfully applied to any persistent MariaDB instance (caused error 1064 on first prod-shape deploy = Phase-61 UAT itself); only H2-in-memory ran V5 successfully (no persistent `flyway_schema_history` survives JVM exit). Therefore no live checksum can collide. Rationale documented in 61-UAT.md:76-86 and 61-VERIFICATION.md UAT-03 closure. Defense-in-depth: `.github/workflows/mariadb-migration-smoke.yml` (commit `bed0ffd`) adds pre-merge MariaDB Flyway gate to prevent recurrence | closed |
| T-61-03-03 | I (Information disclosure) | INFORMATION_SCHEMA queries in V6MigrationTest | accept | Standard JPA-test pattern; no PII; INFORMATION_SCHEMA is dev/test-DB-internal | closed |
| T-61-03-04 | E (Elevation of privilege) | V6 SQL execution authority | accept | Flyway runs at app startup with profile DB credentials; same trust level as V1-V5 | closed |
| T-61-03-05 | T (Tampering) | Hibernate `ddl-auto=validate` post-V6 | mitigate | `./mvnw verify` BUILD SUCCESS post-V6 (1173 tests, 0 failures, 85.18% line coverage); `./mvnw verify -Pe2e` 1172 Surefire + 31 Failsafe pass; `docker compose up` smoke V1→V6 clean on MariaDB 11.8, `/actuator/health = UP` | closed |
| T-61-04-01 | T (Tampering) | Stubbed GoogleSheetsService | accept | `@TestConfiguration @Bean @Primary` is test-scope only; not loaded in prod runtime | closed |
| T-61-04-02 | I (Information disclosure) | Test data isolation | mitigate | `GroupsSeasonE2ETest.java` lines 70-71/105/134/184/265: season `Test-GROUPS Season 2099`, `year=2099`, teams `T-GA-1..T-GB-2`, drivers `T_groups_drv01..T_groups_drv12`. Idempotent `@BeforeEach` cleanup; aligns with CLAUDE.md "Isolate Test Data Completely" + project memory `feedback_test_data_isolation` | closed |
| T-61-04-03 | E (Elevation of privilege) | Test runs admin operations without auth | accept | `@ActiveProfiles("dev")` disables auth per CLAUDE.md "Auth only for prod/docker"; standard E2E convention | closed |
| T-61-04-04 | D (Denial of service) | Test runtime length | accept | D-15 trades ~30-60s test runtime for higher confidence (UI clicks vs service shortcuts); CI cost bounded | closed |
| T-61-05-01 | T (Tampering) | @Sql fixture data | accept | T-prefix + year=2098/2097 + UUID range 0000-0061-* prevents collision with prod or DevDataSeeder fixtures | closed |
| T-61-05-02 | I (Information disclosure) | INFORMATION_SCHEMA / standings query exposure | accept | Test only reads; no data leaves test process; no PII | closed |
| T-61-05-03 | E (Elevation of privilege) | E2E test bypasses auth | accept | `@ActiveProfiles("dev")` disables auth; standard E2E pattern | closed |
| T-61-05-04 | D (Denial of service) | Coverage-repair tests CI runtime | accept | New Surefire tests are unit-level (~50-200ms each); negligible CI cost | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-61-01 | T-61-01-01, T-61-01-02 | Doc-only changes to ROADMAP.md / PROJECT.md / STATE.md / planning artifacts. `.planning/` is repo-public by design; no secrets, no PII; git signing + branch protection cover audit-trail integrity | jegr (Phase 61 planner) | 2026-04-29 |
| AR-61-02 | T-61-02-01, T-61-02-03 | Removed PlayoffController routes returned 404 (no auth bypass possible — Spring Security applies before route resolution). `calculateStandingsLegacy` exposed identical data to phase-aware path; removal is pure attack-surface reduction | jegr (Phase 61 planner) | 2026-04-29 |
| AR-61-03 | T-61-03-03, T-61-03-04 | INFORMATION_SCHEMA queries in V6MigrationTest are dev/test-DB-internal; V6 Flyway runs with the same DB credentials as V1-V5 — no privilege escalation introduced | jegr (Phase 61 planner) | 2026-04-29 |
| AR-61-04 | T-61-04-01, T-61-04-03, T-61-04-04 | E2E test infrastructure: `@TestConfiguration` stubs are test-scope only; `@ActiveProfiles("dev")` disables auth per CLAUDE.md profile policy; D-15 runtime cost-of-confidence accepted | jegr (Phase 61 planner) | 2026-04-29 |
| AR-61-05 | T-61-05-01, T-61-05-02, T-61-05-03, T-61-05-04 | Coverage-repair tests use ephemeral fixtures (T-prefix + year 2098/2097 + UUID range); read-only paths only; auth-disabled-via-profile per standard E2E pattern; unit-level Surefire runtime | jegr (Phase 61 planner) | 2026-04-29 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-02 | 20 | 20 | 0 | gsd-security-auditor (Phase 61 secure run) |

### Audit 2026-05-02 — Initial Verification (State B from artifacts)

- **Mitigations verified:** 7/7 declared `mitigate` threats present in code (V6 `mitigate (with exception)` documented separately)
- **Accept-disposition risks:** 13 documented in Accepted Risks Log
- **V5 exception (T-61-03-02):** UAT-03 escape required dialect-aware rewrite of V5 (Phase 60 escape). Assessed safe: V5 SQL form had never successfully run against persistent MariaDB; only H2-in-memory ran V5 (no surviving checksum). Defense-in-depth via `.github/workflows/mariadb-migration-smoke.yml`
- **Unregistered findings:** None — SUMMARY 61-01 + 61-04 explicitly declare zero new threat surface
- **Notes:** Test 2 (legacy migrated season visual smoke) deferred to user's local pass — not a security item; covered automatically by `LegacyMigratedSeasonE2ETest` for the schema-vs-template contract

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-02
