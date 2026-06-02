---
phase: 115
slug: guest-marking-visibility
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-01
validated: 2026-06-02
---

# Phase 115 — Validation

> Post-execution Nyquist audit: every requirement has automated verification or a documented manual-only justification. All automated tests are green (`./mvnw clean verify -Pe2e` BUILD SUCCESS, 2026-06-02).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + AssertJ + Thymeleaf TemplateEngine |
| **Config file** | `pom.xml` (Surefire + Failsafe, JaCoCo) |
| **Quick run command** | `./mvnw clean test -Dtest=<affected-test-class>` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | full suite ~7+ min; targeted unit ~30–60s |

---

## Per-Requirement Verification Map

| Requirement | Surface | Test Type | Test (method) | Automated Command | Status |
|-------------|---------|-----------|---------------|-------------------|--------|
| MARK-01 | Scorecard / results graphic | unit + integration | `ResultsGraphicServiceTest.givenGuestResult_whenBuildResultRows_thenGuestFlagSetFromRaceLineup` (flag) · `GraphicTemplateGuestRenderIT.givenGuestResultRow_whenRenderingResults…` (render) | `./mvnw clean test -Dtest=ResultsGraphicServiceTest` · `./mvnw clean verify -Dit.test=GraphicTemplateGuestRenderIT -DfailIfNoTests=false` | ✅ green |
| MARK-02 | Provisional scores graphic | unit + integration | `ProvisionalScoresGraphicServiceTest.givenGuestResult_whenBuildContext_thenProvisionalRowGuestFlagSet` (flag) · `GraphicTemplateGuestRenderIT.givenGuestRow_whenRenderingProvisionalScores…` (render) | `./mvnw clean test -Dtest=ProvisionalScoresGraphicServiceTest` · `./mvnw clean verify -Dit.test=GraphicTemplateGuestRenderIT -DfailIfNoTests=false` | ✅ green |
| MARK-03 | Lineup graphic | unit + integration | `LineupGraphicServiceTest.givenGuestLineupEntry_whenBuildPairings_thenGuestFlagsSet` (flag) · `GraphicTemplateGuestRenderIT.givenGuestPairing_whenRenderingLineup…` (render) | `./mvnw clean test -Dtest=LineupGraphicServiceTest` · `./mvnw clean verify -Dit.test=GraphicTemplateGuestRenderIT -DfailIfNoTests=false` | ✅ green |
| MARK-04 | Admin race-detail | unit | `RaceServiceTest.givenGuestResult_whenGetRaceDetailData_thenGuestDriverMapFlagsGuest` | `./mvnw clean test -Dtest=RaceServiceTest` | ✅ green |
| MARK-04 | Admin matchday-detail | manual | template-only `th:if="${lu.guest}"` (direct entity getter via OSIV; no service derivation) | playwright-cli screenshot `.screenshots/115-matchday-detail.png` | 📋 manual-only |
| MARK-05 | Driver-ranking (admin standings + public site) | integration | `DriverRankingServiceGuestIT.givenGuestAppearances_whenCalculateRankings_thenRowsAreMarkedHasGuestAppearance` (the `hasGuestAppearance` flag both surfaces bind) | `./mvnw clean verify -Dit.test=DriverRankingServiceGuestIT -DfailIfNoTests=false` | ✅ green |
| MARK-06 | Public driver-profile + "as guest for" sub-label | integration | `DriverProfilePageGeneratorIT.givenPureGuestDriver_whenGenerate_thenGuestRaceMarkedWithStarAndSubLabel` (asserts `guest-marker`, `&#x2605;`, `as guest for`) | `./mvnw clean verify -Dit.test=DriverProfilePageGeneratorIT -DfailIfNoTests=false` | ✅ green |

Supporting guest-data coverage: `DriverRankingServiceGuestIT` also pins fielding-team score attribution, idempotent re-aggregation, and pure-guest (no SeasonDriver) ranking/alltime inclusion.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Verification (done) |
|----------|-------------|------------|---------------------|
| `matchday-detail.html` renders `★` on guest lineup rows | MARK-04 | Pure template conditional on the `RaceLineup.isGuest()` entity getter (covered elsewhere); no service-layer derivation to unit-test | playwright-cli screenshot `.screenshots/115-matchday-detail.png` |
| Guest `★` in amber (`#f59e0b`), placed after the driver name, distinguishable at 1× on dark bg, no legend | SC-1 / all MARK | Visual treatment — confirmed against rendered references | `.screenshots/115-*.png` (8 surfaces); user-approved at the visual gate 2026-06-02 |
| Cross-surface uniformity (Scorecard / Provisional / Lineup all mark via the same mechanism; placement identical everywhere) | SC-2 | Cross-render visual consistency | Side-by-side of all `.screenshots/115-*.png`; user-approved |

---

## Validation Audit 2026-06-02

| Metric | Count |
|--------|-------|
| Requirements audited | 6 (MARK-01…06) + SC-1/SC-2 |
| Automated (COVERED) | 6 surfaces (MARK-01/02/03 flag + render, MARK-04 race-detail, MARK-05, MARK-06) |
| Manual-only (justified) | 3 (matchday-detail render, SC-1, SC-2) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

The render-level binding for the three admin graphic templates (MARK-01/02/03) — a blind spot in the original strategy because every per-service unit test mocks the `TemplateEngine` — was closed by `GraphicTemplateGuestRenderIT` during the Phase 115 code-review remediation (`fix(115): resolve code-review findings`). Coverage now exceeds the original validation strategy.

---

## Validation Sign-Off

- [x] All requirements have automated verify or are listed under Manual-Only with explicit instructions
- [x] Sampling continuity: no 3 consecutive requirements without automated verify
- [x] No new test infrastructure needed (existing JUnit/Failsafe + real TemplateEngine render IT)
- [x] No watch-mode flags
- [x] Feedback latency < 60s (targeted)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** Nyquist-compliant — 2026-06-02
