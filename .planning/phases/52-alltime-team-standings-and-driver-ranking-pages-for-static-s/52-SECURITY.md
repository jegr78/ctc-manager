---
phase: 52
slug: alltime-team-standings-and-driver-ranking-pages-for-static-s
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-18
---

# Phase 52 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Service -> Template | Backend service data flows into Thymeleaf context variables | Aggregated racing statistics (public, no PII) |
| Static Output -> GitHub Pages | Generated HTML files served publicly | Static HTML, CSS — no secrets, no user input |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-52-01 | I (Information Disclosure) | alltime-standings.html, alltime-driver-ranking.html | accept | Pages display aggregated public racing statistics — no PII, no secrets. Static site is public by design. | closed |
| T-52-02 | T (Tampering) | layout.html nav links | accept | Static HTML output — no runtime request handling. Links are relative paths to generated files, not user-controllable. | closed |
| T-52-03 | S (Spoofing) | SiteGeneratorService methods | accept | No authentication boundary — site generator runs as admin-only batch process. Output is static files served by GitHub Pages. | closed |

*Status: open / closed*
*Disposition: mitigate (implementation required) / accept (documented risk) / transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-52-01 | T-52-01 | Static site displays publicly aggregated racing data. No PII or secrets in output. Information disclosure is by design. | Claude (gsd-secure-phase) | 2026-04-18 |
| AR-52-02 | T-52-02 | HTML files are generated offline and served statically. No runtime request handling means no tampering vector. | Claude (gsd-secure-phase) | 2026-04-18 |
| AR-52-03 | T-52-03 | Site generation runs inside the admin application (Spring Boot) which is already auth-protected in prod/docker profiles. No new auth boundary introduced. | Claude (gsd-secure-phase) | 2026-04-18 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-18 | 3 | 3 | 0 | Claude (gsd-secure-phase) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-18
