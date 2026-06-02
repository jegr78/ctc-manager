---
phase: 116
slug: german-comment-sweep
status: validated
nyquist_compliant: false
validation_mode: manual-only-signed-off
wave_0_complete: true
created: 2026-06-02
---

# Phase 116 — Validation Strategy

> Reconstructed from artifacts (State B). This is a **comments-only** phase: it changes no
> code path, so there is no new behavior to unit-test. Per CLEAN-04, the acceptance contract is
> (1) a repository-wide German-comment **detection scan** returning zero comment findings, and
> (2) `./mvnw clean verify -Pe2e` staying green with coverage held. The no-regression half is
> fully automated; the "zero German comments" half is verified by a documented detection-scan
> recipe and was **deliberately accepted as manual-only** (user decision 2026-06-02 — a permanent
> german-comment build-guard was offered and declined as over-engineering for a one-time cleanup).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Playwright (Surefire/Failsafe + JaCoCo) |
| **Config file** | `pom.xml` (Surefire/Failsafe/JaCoCo/SpotBugs/Checkstyle) |
| **Quick run command** | `grep -rnE '[äöüßÄÖÜ]' --include='*.html' --include='*.java' --include='*.xml' --include='*.yml' --include='*.css' src/` (detection scan) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | scan ~1s · full suite ~7–10 min |

---

## Sampling Rate

- **After every task commit:** targeted detection scan over touched files (German comment grep).
- **After every plan wave:** N/A — single full run deferred to phase end per locked verify-cadence.
- **Before `/gsd:verify-work`:** the one full `./mvnw clean verify -Pe2e` must be green.
- **Max feedback latency:** ~1s (scan) for content; ~10 min (full build) for the no-regression proof.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|-------------|--------|
| 116-01-02 | 01 | 1 | CLEAN-01 | No German comment in admin Thymeleaf templates; no markup/CSS change | scan | German-comment grep over `templates/admin/*.html` | ✅ | ✅ green (manual scan) |
| 116-01-03 | 01 | 1 | CLEAN-02 | No German comment / banned marker in `application.yml`, `admin.css`, `logback-spring.xml` | scan | German-comment + `Phase [0-9]\|Plan-[0-9]\|UAT-\|Wave [0-9]\|WR-[0-9]` grep | ✅ | ✅ green (manual scan) |
| 116-02-02/03 | 02 | 1 | CLEAN-03 | No German comment in test sources; `Saison`→`Season` prose; literals/identifiers untouched | scan + build | German-comment grep + `./mvnw clean verify -Pe2e` (test files compile & run green) | ✅ | ✅ green |
| 116-03-01 | 03 | 2 | CLEAN-04 (scan) | Repo-wide zero German comments across html/java/xml/yml/yaml/properties/css/sql | scan | repo-wide German-comment grep with documented exclusions | ✅ | ✅ green (manual scan) |
| 116-03-02 | 03 | 2 | CLEAN-04 (build) | No behavioral change; coverage held; gates green | build | `./mvnw clean verify -Pe2e` | ✅ | ✅ green (automated) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Evidence (2026-06-02):** repo-wide scan = 0 German comments (exclusions documented in 116-03-SUMMARY). `clean verify -Pe2e` = BUILD SUCCESS · 2530 tests (Surefire 1851 + Failsafe IT 563 + E2E 116) · line coverage 89.88% (≥82% gate, ≥~89% v1.15 baseline) · SpotBugs 0 · Checkstyle 0.

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. A comments-only sweep adds/removes no tests — the no-regression proof reuses the full Surefire/Failsafe/E2E suite (test count unchanged at 2530, ≥2472 baseline). No Wave 0 stubs required.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Zero German comments in admin templates | CLEAN-01 | No automated build-guard (declined as over-engineering for a one-time content cleanup); detection is a documented grep recipe | Run the CONTEXT `<specifics>` recipe over `src/main/resources/templates/admin/*.html`; classify each umlaut/German-word hit as comment (fail) vs. `th:`/string-literal (ok); expect 0 comment hits. |
| Zero German comments / banned markers in config + static resources | CLEAN-02 | Same — documented scan, not wired as a fence | `grep -rnE '[äöüßÄÖÜ]'` + marker grep over `application.yml`, `admin.css`, `logback-spring.xml`; expect 0 in comments. |
| Zero German comments in test sources (incl. `Saison`→`Season`) | CLEAN-03 | Same — documented scan | `grep -rn 'Saison' src/test/java/` (only `seedSaison2023()` identifier + `PlayoffRestorerTest` string literal allowed) + umlaut grep; expect 0 comment hits. |
| Repo-wide zero German comments | CLEAN-04 (scan half) | Same — documented scan | Run the full CONTEXT `<specifics>` recipe across html/java/xml/yml/yaml/properties/css/sql + pom.xml; every residual hit must map to a documented "Out" exclusion (Nürburgring track name, string literals, `SiteSlugger` transliteration regex, `seedSaison2023()` identifier). |

> **Future automation option (declined 2026-06-02):** a `german-comment-guard` `exec-maven-plugin` guard in `pom.xml` (mirroring `checkstyle-gate-guard` + a predicate test) would convert all four manual-only rows to COVERED and act as a regression fence against the handoff-recurrence that introduced this debt. Available as a dedicated future cleanup if German comments recur.

---

## Validation Sign-Off

- [x] Every requirement has a defined verification (scan and/or full build)
- [x] No-regression behavior is automated (`clean verify -Pe2e`, runs every CI/verify)
- [x] Manual-only items documented with exact, repeatable scan instructions
- [x] Full suite green; coverage held (89.88%); test count held (2530 ≥ 2472)
- [x] No watch-mode flags; single authoritative full run at phase end
- [ ] `nyquist_compliant: true` — **NOT set**: CLEAN-01/02/03 + the scan half of CLEAN-04 are manual-only by deliberate, signed-off decision (no automated regression fence). This is a complete validation with manual-only sign-off, not an unfilled automatable gap.

**Approval:** validated (manual-only) 2026-06-02 — user-accepted manual scan over a permanent build-guard.

---

## Validation Audit 2026-06-02
| Metric | Count |
|--------|-------|
| Requirements | 4 (CLEAN-01..04) |
| Automated (build) | 1 (CLEAN-04 no-regression) |
| Manual-only (signed off) | 4 (CLEAN-01/02/03 + CLEAN-04 scan half) |
| Gaps escalated | 0 |
| Tests generated | 0 (comments-only — no behavior to test) |
