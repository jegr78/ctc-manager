---
phase: 114
slug: scoring-personal-crediting
status: verified
threats_open: 0
asvs_level: 1
created: 2026-06-01
---

# Phase 114 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| (none new) | Phase 114 adds NO new attack surface: no external input, no network call, no auth change, no new endpoint, and no new schema/Flyway migration. The work is (a) `@Profile("dev")`-only seed data + a dev-only startup verification log, (b) an internal read-only read-model refactor over already-persisted data, (c) static-site generation over already-public data, (d) a test-only integration test. | None new — all data already persisted/public; dev seed never runs in `local`/`prod`/`docker`. |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-114-01 | Tampering | `TestDataService` + `DevDataSeeder` (dev seed) | accept | Seeder + runner are `@Profile("dev")` only — never run in `local`/`prod`/`docker` (CLAUDE.md "local darf KEINE Dev-Daten"). `DevDataSeeder.verifyGuestExample()` reads + logs only; constructs no entities and never throws. | closed |
| T-114-02 | Tampering | `DriverRankingService` attribution refactor | accept | Read-only `@Transactional(readOnly = true)` computation; no write path touched; `Match.homeScore`/`awayScore` persistence unchanged (D-10 lock). Regression unit + IT tests pin behavior. | closed |
| T-114-03 | Information disclosure | `DriverProfilePageGenerator` (public site) | accept | Pure-guest profile pages expose only the same driver/team/results already public for roster drivers — no new PII. Site generation is an internal batch with no untrusted input; "Test" seasons are excluded from the public site. | closed |
| T-114-04 | Tampering | `DriverRankingServiceGuestIT` (shared TCF context) | accept | Test uses `Test_`/`T-`-prefixed fixture rows only; the D-15 removal mutation runs in a `@Transactional` test that rolls back, so it cannot pollute the shared `@CtcDevSpringBootContext` cache for sibling tests. | closed |
| T-114-SC | Tampering (supply chain) | npm/pip/cargo installs | accept | No package installs in this phase — pure Java/Spring test + seed + read-model changes (RESEARCH.md "No New Packages Required"). | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-114-01 | T-114-01 | Dev-only seed/log surface; `@Profile("dev")` confines it to dev (port 9090), never to `local`/`prod`/`docker`. No production data path. | jegr78 (per plan threat model) | 2026-06-01 |
| AR-114-02 | T-114-02 | Read-only read-model refactor; no persisted-state write introduced (live read-model preserved, D-10). | jegr78 (per plan threat model) | 2026-06-01 |
| AR-114-03 | T-114-03 | Guest profile pages disclose only already-public roster-equivalent data; no new PII; Test seasons excluded from public site. | jegr78 (per plan threat model) | 2026-06-01 |
| AR-114-04 | T-114-04 | Test-only; rollback-isolated mutation; test-prefixed fixtures. | jegr78 (per plan threat model) | 2026-06-01 |
| AR-114-SC | T-114-SC | No dependency/package changes in this phase. | jegr78 (per plan threat model) | 2026-06-01 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-06-01 | 5 | 5 | 0 | /gsd-secure-phase (orchestrator, plan-time register; short-circuit — all dispositions `accept`, documented) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-06-01
