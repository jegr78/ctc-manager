---
phase: 62
slug: public-site-phases-groups
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-02
---

# Phase 62 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `.planning/phases/62-public-site-phases-groups/62-RESEARCH.md` § "## Validation Architecture"

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test 4.x; Jsoup 1.x for HTML parsing (already on classpath) |
| **Config file** | `pom.xml` (no separate test config) |
| **Quick run command** | `./mvnw -Dtest='SiteGenerator*' test` (sitegen + helper + SC5 IT) |
| **Full suite command** | `./mvnw verify` (Surefire — Unit + IT, JaCoCo coverage; Playwright NOT needed) |
| **Estimated runtime** | ~1-2 min quick / ~5-10 min full |

---

## Sampling Rate

- **After every task commit:** `./mvnw -Dtest='SiteGenerator*' test`
- **After every plan wave:** `./mvnw verify` (full Surefire — unit + integration — including JaCoCo coverage report; verify ≥82% line coverage)
- **Before `/gsd-verify-work`:** `./mvnw verify` green; manual `playwright-cli` Desktop + Mobile sweep complete; release-note draft for D-19 prepared
- **Max feedback latency:** ~120 seconds for the quick command

---

## Per-Task Verification Map

| Source | Wave | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|------|----------|-----------|-------------------|-------------|--------|
| SC1 (GROUPS per-group + combined) | 1 | `/season/{slug}/standings-regular-group-{X}.html` exists; renders only that group's teams; legacy `standings.html` shows Group column for combined-view | Sitegen IT (Surefire, Jsoup) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist test` | ❌ Plan 6 | ⬜ pending |
| SC2 (multi-phase one tab per phase) | 1 | Phase tab row HTML present on `standings.html`; every phase has a tab anchor | Sitegen IT (Jsoup) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible test` | ❌ Plan 6 | ⬜ pending |
| SC3 (PLAYOFF tab reaches bracket) | 1 | PLAYOFF tab anchor href points to existing `/season/{slug}/playoff.html` | Sitegen IT (combined with SC2) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket test` | ❌ Plan 6 | ⬜ pending |
| SC4 (single-REGULAR-LEAGUE byte-identity) | 1 | `standings.html` for a LEAGUE-only single-phase season is byte-identical to a snapshot taken pre-Phase-62 | Sitegen IT (golden file) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline test` | ❌ Plan 1 (snapshot baseline taken there); finalised in Plan 6 | ⬜ pending |
| SC5 (regression test ≥1 GROUPS + ≥1 multi-phase) | 1 | Test class itself + green coverage of GROUPS-2023 fixture | Sitegen IT (entire `SiteGeneratorPhaseAwarenessIT` class) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT test` | ❌ Plan 6 | ⬜ pending |
| D-19 alltime cross-phase (standings) | 1 | Service-level: alltime points for a multi-phase season include PLAYOFF points | Unit test (StandingsService) | `./mvnw -Dtest=StandingsServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints test` | ❌ Plan 5 | ⬜ pending |
| D-19 alltime driver ranking | 1 | Same for driver ranking | Unit test (DriverRankingService) | `./mvnw -Dtest=DriverRankingServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults test` | ❌ Plan 5 | ⬜ pending |
| D-22 empty-phase roster + banner | 1 | PLAYOFF phase with 0 race results renders `standings-playoff.html` with all roster teams at 0 points + banner | Sitegen IT | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenPlayoffPhaseWithoutResults_whenGenerate_thenEmptyStateBannerAndRoster test` | ❌ Plan 1 (banner template) + Plan 6 (assertion) | ⬜ pending |
| D-26 a11y semantics | 1 | `role="tablist"` + `role="tab"` + `aria-selected` present on tab nav | Sitegen IT (Jsoup attribute assertion) | `./mvnw -Dtest=SiteGeneratorPhaseAwarenessIT#givenMultiPhaseSeason_whenGenerate_thenTabRowHasA11yAttributes test` | ❌ Plan 6 | ⬜ pending |
| D-26 visual + responsive | 1 | Desktop + Mobile screenshots match design contract | Manual via `playwright-cli` | `playwright-cli open http://localhost:9090/...` (manual, captured to `.screenshots/`) | manual — Plan 7 | ⬜ pending |
| Helper-class behavior parity (D-20) | 1 | Each `XxxPageGenerator` produces same output as today | Per-helper unit test (`@SpringBootTest`) | `./mvnw -Dtest='*PageGenerator*Test' test` | ❌ Plan 0 | ⬜ pending |
| `SiteGeneratorServiceIT` D-23 contract update | 1 | Existing Mockito IT updated for new constructor or replaced | Mockito unit test | `./mvnw -Dtest=SiteGeneratorServiceIT test` | ✅ exists; needs Plan 0 update | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/sitegen/SiteGeneratorPhaseAwarenessIT.java` — covers SC1, SC2, SC3, SC4 (golden file), SC5, D-22, D-26 a11y. **Single test class, multiple `@Test` methods.**
- [ ] `src/test/java/org/ctc/sitegen/StandingsPageGeneratorTest.java`, `DriverRankingPageGeneratorTest.java`, `MatchdaysPageGeneratorTest.java`, `TeamProfilePageGeneratorTest.java`, `DriverProfilePageGeneratorTest.java` — Plan 0 helper-class unit tests (each ≈ 5-10 test methods).
- [ ] `src/test/java/org/ctc/domain/service/StandingsServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeStandings_thenIncludesPlayoffPoints` — Plan 5 D-19 service test; class likely already exists, add new method.
- [ ] `src/test/java/org/ctc/domain/service/DriverRankingServiceTest#givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults` — Plan 5 D-19 service test; class likely already exists, add new method.
- [ ] Golden snapshot file: `src/test/resources/sitegen/baseline/single-league-standings.html` — captured pre-Plan-1, asserted post-rewrite for SC4.
- [ ] Update `src/test/java/org/ctc/sitegen/SiteGeneratorServiceIT.java:79-97` — constructor argument list reflects post-D-20 helper beans. (Existing file, not new.)

Framework install: none required. JUnit 5 + Mockito + Spring Boot Test + Jsoup are already on the project's compile/test classpath. `playwright-cli` is the manual verification tool — already installed per CLAUDE.md "Visual Verification".

---

## Manual-Only Verifications

| Behavior | Source | Why Manual | Test Instructions |
|----------|--------|------------|-------------------|
| Visual parity of phase-tab row + group-sub-tab row with UI-SPEC | D-26 / UI-SPEC.md | Pixel/CSS quality bar — automated CSS regression tooling not in project | Start `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`. Open `playwright-cli open http://localhost:9090/site/season/season-2023/standings.html` (Desktop + Mobile viewport). Capture screenshots in `.screenshots/phase62-standings-{desktop,mobile}.png`. Compare against UI-SPEC. |
| Mobile horizontal-scroll behavior of nested tab rows (Risk #6) | D-26 | Real-device touch-scroll behavior cannot be unit-tested | `playwright-cli` mobile viewport (~390×844 px, iPhone 14). Confirm both rows scroll horizontally without breaking grid; second row stays under first. |
| Active-tab visual treatment (underline / accent / bold) | D-26, D-05 | Visual-design judgment | `playwright-cli`: load each tab variant, assert active state is unambiguous. |
| Empty-state banner copy + spacing | D-22 | Copy-register check | Generate site against a season fixture with PLAYOFF phase but zero race results (extend `TestDataService` in dev,demo profile if needed); inspect `/season/{slug}/standings-playoff.html`. |
| `playoff.html` cross-link integrity from PLAYOFF tab | D-08, SC3 | End-to-end click-through | `playwright-cli`: click PLAYOFF tab on `standings.html`; confirm bracket renders. |
| Cross-link integrity from `teams.html` / `archive.html` / footer / sub-nav post-Phase-62 | D-04 / D-09 backward-compat | Manual click-through across overview pages | `playwright-cli` Desktop: navigate from each entry point; confirm no 404s. |
| Alltime number recomputation (D-19 TRACKED BEHAVIOR CHANGE) | D-19 / D-29 | User-visible visible-to-public-users behavior shift; release-note must call out | Generate `alltime-standings.html` + `alltime-driver-ranking.html` against a fixture with PLAYOFF phase. Compare totals against Phase-61-baseline. Document delta in PR body. |

---

## CSS Regression Sampling

- Plan 1 + Plan 7 visual sweep on `playwright-cli` for: `.subnav` (existing — must remain pixel-identical), new `.phase-tab-row` / `.group-tab-row` classes, mobile overflow-scroll, active-tab accent visual, empty-state banner.
- No automated CSS regression tooling; rely on manual screenshots + `git diff` on `style.css`.

---

## Coverage Discipline

- Helper-class extraction (Plan 0) MUST keep coverage ≥ 82% — measured via JaCoCo at `target/site/jacoco/index.html` after `./mvnw verify`.
- D-19 service-method change (Plan 5) is high-risk for coverage drop if old REGULAR-only paths are simply deleted; ensure new path is fully covered before merging.
- New helper unit tests in Plan 0 typically RAISE coverage; SC5 IT in Plan 6 raises it further.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s for quick command
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
