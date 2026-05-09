---
phase: 62
slug: public-site-phases-groups
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-02
mode: retroactive
---

# Phase 62 — Validation Strategy

> Retroactive Nyquist audit of an already-executed phase. Confirms that every locked requirement and every success criterion has automated verification — and that residual manual-only checks are explicitly deferred with `Why Manual` rationale, not coverage gaps.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.x; Jsoup 1.x for HTML parsing (already on classpath) |
| **Config file** | [pom.xml](../../pom.xml) — Surefire + Failsafe + JaCoCo, `<minimum>0.82</minimum>` line gate |
| **Quick run command** | `./mvnw test -Dtest='SiteGenerator*,StandingsPageGeneratorTest,MatchdaysPageGeneratorTest,DriverRankingPageGeneratorTest,TeamProfilePageGeneratorTest,DriverProfilePageGeneratorTest'` |
| **Full suite command (no E2E)** | `./mvnw verify` — 1215 Surefire + JaCoCo gate |
| **Full suite command (with E2E)** | `./mvnw verify -Pe2e` — 1215 Surefire + 31 Failsafe; JaCoCo 87.24% line coverage at Phase-62 close |
| **Coverage measured** | 87.24% line coverage (5909/6773) at Phase-62 HEAD (Plan-07 final verify) |
| **Estimated runtime** | ~120 s (quick command); ~80 s (verify, no E2E); ~280 s (verify -Pe2e) |

---

## Sampling Rate

Phase 62 was already executed. The retroactive sampling contract:

- **After every task commit (during execution):** Targeted Surefire (`./mvnw test -Dtest=…`) — feedback < 30 s
- **After every plan wave:** `./mvnw verify` (Surefire + JaCoCo) — feedback < 90 s
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` — feedback < 5 min
- **Before PR merge:** Same as above
- **Max feedback latency observed:** ~120 s (per-plan verify cycles); final gate 280 s

---

## Per-Task Verification Map

Phase 62 = 8 plans (62-00 through 62-07). The map below covers every locked requirement ID and every success criterion declared in the plan frontmatters, mapped to the test evidence proving coverage.

| Task | Plan | Requirement | Behavior under test | Test Type | Test File / Evidence | Automated Command | Status |
|------|------|-------------|---------------------|-----------|----------------------|-------------------|--------|
| 62-00 T0 | 00 | QUAL-01 (SC4 baseline capture) | Pre-Phase-62 golden snapshot of single-LEAGUE standings.html captured before any refactor; `@Disabled` harness committed | test fixture | [SiteGeneratorBaselineCaptureTest.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java) (`@Disabled`); [single-league-standings.html](../../src/test/resources/sitegen/baseline/single-league-standings.html) (14054 bytes, no `phase-tab-row`/`group-tab-row` markers) | `test -f src/test/resources/sitegen/baseline/single-league-standings.html && [ $(wc -c < src/test/resources/sitegen/baseline/single-league-standings.html) -gt 1000 ]` | ✅ |
| 62-00 T1/T2 | 00 | QUAL-01 (refactor byte-identity) | 5 helper Spring beans extracted; SiteGeneratorService slimmed to orchestrator (568 LOC from 868); all existing sitegen tests green after refactor | unit + compile | [SiteGeneratorServiceTest.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java) (84 tests); [SiteGeneratorE2ETest.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorE2ETest.java) (8 tests); [SiteGeneratorServiceIT.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java) (1 test) | `./mvnw test -Dtest='SiteGeneratorServiceTest,SiteGeneratorE2ETest,SiteGeneratorServiceIT'` | ✅ |
| 62-01 | 01 | UI-02 / UI-05 / UI-07 — standings phase-/group-awareness | Phase-tab row emitted when ≥2 phases; group sub-tab row for GROUPS-layout; per-group + combined standings files generated; PLAYOFF tab → playoff.html (D-08); SC4 byte-identity for LEAGUE-only; empty-state banner (D-22); a11y attributes (D-26) | unit/integration (Surefire `@SpringBootTest`) | [StandingsPageGeneratorTest.java:94-250](../../src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java#L94-L250) — 9 `@Test` methods (1 `@Disabled` pending Swiss+GROUPS fixture); Flyway clean+migrate in `@BeforeAll`; Jsoup assertions | `./mvnw test -Dtest=StandingsPageGeneratorTest` | ✅ |
| 62-02 | 02 | UI-02 / UI-05 — matchdays phase-/group-awareness | Legacy matchdays.html = REGULAR-only; per-phase variants; per-group variants for GROUPS-layout; phase-tab row with a11y on multi-phase; single-LEAGUE stays without tab rows | unit/integration (Surefire) | [MatchdaysPageGeneratorTest.java:97-230](../../src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java#L97-L230) — 8 `@Test` methods; Jsoup assertions; Flyway clean+migrate in `@BeforeAll` | `./mvnw test -Dtest=MatchdaysPageGeneratorTest` | ✅ |
| 62-03 | 03 | UI-05 — driver-ranking phase-awareness | Legacy driver-ranking.html aggregates across all phases; per-phase variants; first tab "All Phases" (active default); a11y attributes; SC4 byte-identity for LEAGUE-only | unit/integration (Surefire) | [DriverRankingPageGeneratorTest.java:121-260](../../src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java#L121-L260) — 8 `@Test` methods; Jsoup assertions | `./mvnw test -Dtest=DriverRankingPageGeneratorTest` | ✅ |
| 62-04 | 04 | UI-02 / UI-05 — team/driver profile phase breakdown | team-profile.html shows Phase Breakdown section only when ≥2 phases (D-13); single-REGULAR stays byte-identical (SC4); standings panel uses combined-view (D-14); single-URL per entity (D-16); driver-profile.html sections results by phase; RaceLineup-as-Source-of-Truth preserved | unit/integration (Surefire) | [TeamProfilePageGeneratorTest.java:90-190](../../src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java#L90-L190) — 5 `@Test` methods; [DriverProfilePageGeneratorTest.java:90-190](../../src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java#L90-L190) — 6 `@Test` methods | `./mvnw test -Dtest='TeamProfilePageGeneratorTest,DriverProfilePageGeneratorTest'` | ✅ |
| 62-05 | 05 | QUAL-01 (D-19 tracked behavior change) | `StandingsService.calculateAlltimeStandings` includes PLAYOFF + PLACEMENT points (cross-phase loop via `seasonPhaseService.findAllPhases`); `DriverRankingService.calculateAlltimeRanking` uses `findByRaceMatchdaySeasonIdIn` (no IsNull filter) — covers PLAYOFF-linked race results | unit (Mockito, Surefire) | [StandingsServiceTest.java:894](../../src/test/java/org/ctc/domain/service/StandingsServiceTest.java#L894) — `givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints`; [DriverRankingServiceTest.java:541](../../src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java#L541) — `givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults` | `./mvnw test -Dtest='StandingsServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints,DriverRankingServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults'` | ✅ |
| 62-06 | 06 | QUAL-02 (SC5 regression gate) | Cross-cutting regression IT: SC1 per-group+combined files; SC2 phase-tab-row visible; SC3 PLAYOFF tab → playoff.html; SC4 byte-identity (LEAGUE-only baseline); D-26 a11y (role=tablist/tab + aria-selected); D-22 empty-state banner + roster; D-04 archive cross-link integrity; D-19 alltime pages existence | integration (Surefire `@SpringBootTest`) | [SiteGeneratorPhaseAwarenessIT.java:101-305](../../src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java#L101-L305) — 9 `@Test` methods; Jsoup parsing; Flyway clean+migrate in `@BeforeAll` | `./mvnw test -Dtest=SiteGeneratorPhaseAwarenessIT` | ✅ (9/9 green) |
| 62-06 D-22 fixture | 06 | QUAL-02 (D-22 empty-phase fixture) | `Season 2024 — Empty Phase` fixture added to `TestDataService.seed()` — REGULAR phase, 4 PhaseTeam rows, 0 race results; survives productionSeasons filter (no "Test" substring in name) | fixture | [TestDataService.java](../../src/main/java/org/ctc/admin/TestDataService.java) (class Javadoc Z. 67-68; season constructor Z. 326); slug `2024-3-season-2024-empty-phase` | `./mvnw test -Dtest='SiteGeneratorPhaseAwarenessIT#givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible'` | ✅ |
| post-execution W-1 | post-exec (commit `f850cc4`) | QUAL-02 (W-1 regression) | `DriverRankingService.resolveTeamFromLineup` no longer returns `null` unconditionally; resolves via `raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driverId).map(RaceLineup::getTeam).orElse(null)` — Source-of-Truth contract (`feedback_racelineup_source_of_truth`) | unit (Mockito) | [DriverRankingServiceTest.java:354-383](../../src/test/java/org/ctc/domain/service/DriverRankingServiceTest.java#L354-L383) — `givenPerPhaseRanking_whenRaceLineupExists_thenTeamPopulatedFromLineup` | `./mvnw test -Dtest='DriverRankingServiceTest#givenPerPhaseRanking_whenRaceLineupExists_thenTeamPopulatedFromLineup'` | ✅ |
| 62-07 final gate | 07 | QUAL-01 (coverage) | JaCoCo line coverage ≥ 82% — full E2E gate including Playwright | meta-gate | [pom.xml](../../pom.xml) JaCoCo plugin; 87.24% (5909/6773) at Phase-62 close; `./mvnw verify -Pe2e` BUILD SUCCESS — 1215 Surefire + 31 Failsafe, 0 failures, 0 errors | `./mvnw verify` (gate enforced) | ✅ (87.24%) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** No 3 consecutive tasks lack automated verification. Every code-touching plan and every gap-fix commit has either (a) targeted Surefire tests, (b) compile + JPA-validate at `@SpringBootTest` startup, or (c) explicit grep audit gates. Plan 07 (visual sweep) is correctly classified Manual-Only with full `Why Manual` rationale.

---

## Wave 0 Requirements

Phase 62 did not require a new test framework install — JUnit 5 + Mockito + Spring Boot Test + Jsoup were already wired from prior milestones. The **net-new test infrastructure** introduced by Phase 62:

- [StandingsPageGeneratorTest.java](../../src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java) — 250 LOC, 9 `@Test` methods (1 `@Disabled`); Surefire `@SpringBootTest @ActiveProfiles("dev")` + Flyway clean+migrate + Jsoup assertions; covers SC1, SC4, D-22, D-26 for standings
- [MatchdaysPageGeneratorTest.java](../../src/test/java/org/ctc/sitegen/MatchdaysPageGeneratorTest.java) — 230 LOC, 8 `@Test` methods; same pattern; covers UI-02/UI-05 matchdays behavior
- [DriverRankingPageGeneratorTest.java](../../src/test/java/org/ctc/sitegen/DriverRankingPageGeneratorTest.java) — 256 LOC, 8 `@Test` methods; covers UI-05 driver-ranking phase-awareness
- [TeamProfilePageGeneratorTest.java](../../src/test/java/org/ctc/sitegen/TeamProfilePageGeneratorTest.java) — 192 LOC, 5 `@Test` methods; covers D-13/D-14/D-16 team profile
- [DriverProfilePageGeneratorTest.java](../../src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorTest.java) — 190 LOC, 6 `@Test` methods; covers D-15/D-16 driver profile
- [SiteGeneratorPhaseAwarenessIT.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java) — 305 LOC, 9 `@Test` methods; canonical SC5 regression gate covering SC1–SC5 + D-04 + D-22 + D-26 + D-19 in one class
- [SiteGeneratorBaselineCaptureTest.java](../../src/test/java/org/ctc/sitegen/SiteGeneratorBaselineCaptureTest.java) — manual-only capture harness (`@Disabled`); aligns SC4 baseline with `TestDataService.seed()` fixture path
- [src/test/resources/sitegen/baseline/single-league-standings.html](../../src/test/resources/sitegen/baseline/single-league-standings.html) — 14054-byte golden snapshot for SC4 byte-identity assertion (re-captured post-deterministic-sort fix in Plan 01 commit `1ffd559`)
- Two additional golden snapshots: `src/test/resources/sitegen/baseline/single-league-team-profile.html` (6553 bytes) and `src/test/resources/sitegen/baseline/single-league-driver-profile.html` (8080 bytes) — for SC4 team/driver profile variants

All Wave-0-equivalent assets are committed and green.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| **Visual parity of phase-tab row + group-sub-tab row with UI-SPEC** | D-26 / QUAL-02 visual sub-aspect | **Visual-Quality-Bar**: pixel/CSS quality bar — `playwright-cli` screenshot comparison against UI-SPEC design contract (44px primary row, 36px secondary, accent active state). Automated CSS regression tooling not in project. The automated `SiteGeneratorPhaseAwarenessIT` asserts structural correctness (role=tablist, aria-selected); colour/spacing/font-weight require visual judgment. | Start dev server (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`). Open `playwright-cli open http://localhost:9090/site/season/season-2023/standings.html` (Desktop 1280×800 + Mobile 390×844). Capture `.screenshots/phase62-standings-{desktop,mobile}.png`. Compare against UI-SPEC §Tab Row Design. |
| **Mobile horizontal-scroll behavior of nested tab rows** | D-26 / QUAL-02 | **Real-Device-Touch-Behavior**: touch-scroll overflow on nested tab rows cannot be reliably asserted from a headless Jsoup parse. The `playwright-cli` mobile viewport confirms both rows scroll horizontally without breaking grid. Closed during Plan-07 sweep (accepted PASS — screenshot `phase62-07-standings-groups-mobile.png` in `.screenshots/`). | `playwright-cli` mobile viewport (~390×844 px). Confirm phase-tab-row + group-sub-tab-row + standings table all render correctly; second row stays under first. |
| **Active-tab visual treatment (underline / accent / bold)** | D-26, D-05 | **Visual-Quality-Bar**: active-state visual design is a CSS judgment call (underline border, accent colour, bold weight) — not checkable by Jsoup attribute assertions alone. The automated tests assert `aria-selected="true"` on the active tab but cannot verify the CSS rendering fidelity. | `playwright-cli`: load each tab variant, assert active state is unambiguous (solid bottom border vs. no border on inactive tabs). |
| **Empty-state banner copy + spacing** | D-22 / QUAL-02 | **Visual-Quality-Bar**: the heading and paragraph copy (`No results recorded yet.` / `Standings will appear once race results are recorded.`) are asserted structurally by `SiteGeneratorPhaseAwarenessIT#givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible`. Spacing / padding / font-size versus UI-SPEC §Copywriting Contract require a visual check. | Generate site against the `Season 2024 — Empty Phase` fixture; inspect `/season/2024-3-season-2024-empty-phase/standings.html` via `playwright-cli`. |
| **D-19 alltime numerical delta documented in PR description** | D-19 / D-29 | **Release-Note-Boundary**: the tracked behaviour change (REGULAR-only → cross-phase alltime aggregation) shifts publicly visible alltime totals. The structural correctness is proven by `StandingsServiceTest` + `DriverRankingServiceTest`. The exact before/after numerical delta against real production data must be documented in the PR description per D-29 — this is a user-visible, release-note-level change that cannot be tested against real data in fixtures. | Generate `alltime-standings.html` + `alltime-driver-ranking.html` against a production snapshot (local/docker profile). Compare cross-phase totals against Phase-61 baseline. Document delta in PR body §Tracked Behavior Changes. |
| **PLAYOFF tab click-through to bracket** | D-08, SC3 / QUAL-02 | **End-to-End-Navigation**: the `SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket` asserts the `href="playoff.html"` attribute structurally. The click-through (confirms the file physically loads + renders a bracket) requires a live file-server context. Closed during Plan-07 sweep (HTTP 200 via `curl` + `phase62-07-anchor-playoff-groups-desktop.png`). | `playwright-cli`: click PLAYOFF tab on `standings.html`; confirm bracket renders at `/season/{slug}/playoff.html`. |
| **Cross-link integrity from teams/archive/footer/sub-nav post-Phase-62** | D-04 / D-09 backward-compat | **End-to-End-Navigation**: `SiteGeneratorPhaseAwarenessIT#givenGeneratedSite_whenParseArchiveLinks_thenAllSeasonLinksResolveToExistingFiles` checks archive.html cross-link file existence. Full click-through from teams.html / footer / sub-nav requires a live browser context. | `playwright-cli` Desktop: navigate from each entry point; confirm no 404s. |

**Why the manual residue is acceptable:** Every manual item in the table is either (a) a visual/CSS quality check that a Jsoup structural assertion cannot perform (`Visual-Quality-Bar`), (b) a real-device touch-scroll behavior, (c) a live-file-server click-through, or (d) a release-note delta against production data. The automated test suite (`SiteGeneratorPhaseAwarenessIT` + per-helper `*PageGeneratorTest` + `*ServiceTest` D-19 methods) proves all structural, data-flow, and schema-level contracts. Items (b) and (c) were closed during Plan-07 visual sweep and are recorded as `ACCEPTED (visual sweep)` in `62-HUMAN-UAT.md`.

---

## Validation Sign-Off

- [x] All tasks have automated verify or are documented as manual-only with rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (net-new test classes inventoried above)
- [x] No watch-mode flags (Surefire / Failsafe in CI mode)
- [x] Feedback latency < 300 s for the full E2E gate (Plan-07 final verify measured ~280 s)
- [x] `nyquist_compliant: true` set in frontmatter
- [x] `wave_0_complete: true` set in frontmatter — all 9 net-new test classes/fixtures committed and green

**Approval:** approved 2026-05-07

---

## Validation Audit 2026-05-07

| Metric | Count |
|--------|-------|
| Requirements audited | 5 LOCKED (UI-02, UI-05, UI-07, QUAL-01, QUAL-02) + 1 D-requirement (D-19 tracked behavior change) |
| Plans audited | 8 (62-00 through 62-07) + 1 post-execution W-1 fix |
| Gaps found | 0 fillable — all REQ-IDs already had automated lateral tests from Phase-62 execution |
| Resolved (already automated) | 10/10 verification dimensions (all REQ-IDs + W-1 regression) |
| Auto-filled in this audit | 0 (no new tests required — coverage already existed) |
| Escalated to manual-only | 6 (visual/CSS quality bar, real-device touch-scroll, PLAYOFF click-through, archive click-through, D-19 alltime numerical delta in PR, empty-state spacing) — all with `Why Manual` rationale; items 2, 3, 6 closed during Plan-07 sweep |
| Net-new test infrastructure | StandingsPageGeneratorTest, MatchdaysPageGeneratorTest, DriverRankingPageGeneratorTest, TeamProfilePageGeneratorTest, DriverProfilePageGeneratorTest, SiteGeneratorPhaseAwarenessIT, SiteGeneratorBaselineCaptureTest (manual-only `@Disabled`), 3 golden snapshot files (single-league-standings.html + team-profile + driver-profile), 2 D-19 service test methods in StandingsServiceTest + DriverRankingServiceTest, W-1 regression test in DriverRankingServiceTest |

**Verdict:** **NYQUIST-COMPLIANT.** Every locked requirement (UI-02, UI-05, UI-07, QUAL-01, QUAL-02) and every success criterion (SC1–SC5) has automated verification in the Surefire suite. The D-19 tracked behavior change has dedicated unit tests in `StandingsServiceTest` and `DriverRankingServiceTest`. The W-1 `resolveTeamFromLineup` regression has a dedicated test at `DriverRankingServiceTest:354-383`. The six manual-only items correspond to visual quality bars, real-device behaviors, and production-data boundaries that fixture-based tests cannot cover economically — all explicitly justified. Phase-62 close: 87.24% JaCoCo line coverage (3.24 pp headroom over the 82% gate), 1215 Surefire + 31 Failsafe, 0 failures.
