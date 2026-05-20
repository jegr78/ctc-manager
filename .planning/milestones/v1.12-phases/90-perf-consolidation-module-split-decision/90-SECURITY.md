---
phase: 90
slug: perf-consolidation-module-split-decision
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-20
---

# Phase 90 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Plan-time register short-circuit applied (all 3 threats verified CLOSED via mitigation evidence; `register_authored_at_plan_time: true`).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| test-classpath ↔ production-classpath | `src/test/java/` is Maven `test`-scope and never on the production runtime classpath; production code cannot import from `src/test/java/org/ctc/testsupport/`. | n/a (test-only types) |
| developer-machine ↔ Docker daemon | Local Docker daemon runs Testcontainers MariaDB. With reuse enabled, the container survives JVM exit. CI runners host their own Docker daemon but never have `~/.testcontainers.properties` and never set `-Ddocker.available=true`. | test-only DB state (`ctc_test` schema, `ctc/test` credentials) |
| developer-machine `$HOME` | `~/.testcontainers.properties` is per-developer, never committed; controls whether `.withReuse(true)` is honored. | local dev opt-in flag (`testcontainers.reuse.enable=true`) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-90-SEC-01 | Tampering | `src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` accidentally referenced from `src/main/java/**` | mitigate | Composed annotation lives strictly under `src/test/java/`; Maven `test`-scope isolation enforces classpath separation; D-09 invariant asserts `git diff <phase-baseline>..HEAD -- 'src/main/java/**' \| wc -l = 0`; CodeQL `security-extended` would surface any production-side import on push. Residual: Negligible. | closed |
| T-90-TC-01 | Information Disclosure (local-dev only) | Testcontainers reuse persists DB state between consecutive `./mvnw verify` runs; future MariaDB IT could observe stale rows if not defensively seeded | mitigate | `docs/test-performance.md § PERF-04 Testcontainers Reuse § Future MariaDB IT authors — seed defensively` (line 442) requires `testDataService.seed(...)` in `@BeforeEach`. Both existing ITs already comply: `BackupImportMariaDbSmokeIT` replaces fixture in `@BeforeEach`; `BackupRoundTripIT.MariaDbRoundTripTests` wipes-and-restores inside test body. Production data never exposed (test-only `ctc_test` schema). Residual: Low (dev-only). | closed |
| T-90-TC-02 | Denial of Service (local-dev only) | Orphan reuse-mode MariaDB containers accumulate on developer machine across project rotations (Ryuk disabled in reuse mode) | mitigate | `docs/test-performance.md § PERF-04 Testcontainers Reuse § Cleanup hint` (line 454) documents `docker container prune --filter "label=org.testcontainers.reuse.enable=true"` + diagnostic `docker ps --filter label=org.testcontainers.reuse.enable=true`. Recovery is one shell command; disk pressure is the only impact. Residual: Low (dev-only). | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

Plan 90-03 had no threat rows (pure-docs verdict; zero code surface — `(none)` register row per `90-03-PLAN.md`).

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|

*No accepted risks.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-20 | 3 | 3 | 0 | /gsd-secure-phase 90 (plan-time short-circuit; auditor not spawned) |

### Mitigation Evidence

- **T-90-SEC-01:** `ls src/test/java/org/ctc/testsupport/CtcDevSpringBootContext.java` → file exists; `ls src/main/java/org/ctc/testsupport/` → directory does not exist; `git diff e0fa53ee..36e6a70a -- 'src/main/java/**' | wc -l` → 0 (D-09 invariant verified across the Phase 90 commit range).
- **T-90-TC-01:** `grep -n "seed defensively" docs/test-performance.md` → line 442 (`### Future MariaDB IT authors — seed defensively`); `BackupImportMariaDbSmokeIT` and `BackupRoundTripIT.MariaDbRoundTripTests` `@BeforeEach` compliance verified per 90-02-SUMMARY.md § Threat-Model Mitigations.
- **T-90-TC-02:** `grep -n "docker container prune" docs/test-performance.md` → line 460 inside `### Cleanup hint` (line 454).

### D-04b Production-File Git-Clean

`git diff e0fa53ee..36e6a70a -- 'src/main/resources/application*.yml' 'src/main/java/org/ctc/backup/service/BackupStagingCleanup.java' | wc -l` → 0. CI gate `@EnabledIfSystemProperty(named = "docker.available", matches = "true")` preserved on both MariaDB ITs (CI never sets the flag, so reuse is dev-only by Testcontainers default).

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log (none required)
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-20
