---
phase: 54
slug: preview-service-row-categorization
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-24
audited: 2026-04-25
---

# Phase 54 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Backend-only phase: unit-test coverage of `DriverSheetImportService.preview(...)` is the sole validation surface. No integration/E2E tests in this phase (deferred to Phase 55).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito (`mockito-core`, `mockito-junit-jupiter`) + AssertJ 3.x |
| **Config file** | `pom.xml` (Surefire plugin lines 183-192; JaCoCo line-coverage gate at pom.xml:241 = 82%) |
| **Quick run command** | `./mvnw test -Dtest=DriverSheetImportServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | Quick: ~5–10s (pure unit, no Spring context) · Full: ~60–90s (entire suite + JaCoCo) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=DriverSheetImportServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** `./mvnw verify` must be green AND `target/site/jacoco/index.html` must show `DriverSheetImportService` at ≥82% line coverage
- **Max feedback latency:** 10 seconds for per-commit feedback

---

## Per-Task Verification Map

*Task IDs are provisional — planner may consolidate/split. Every task below lands in the plan with an `<automated>` block or depends on a Wave 0 test stub.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 54-01-01 | 01 | 0 | TEST-01 | — | N/A (test-only) | unit stub | `./mvnw test -Dtest=DriverSheetImportServiceTest` (expects compile) | ✅ | ✅ green |
| 54-01-02 | 01 | 1 | IMPORT-02 | — | N/A | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded` | ✅ | ✅ green |
| 54-01-03 | 01 | 1 | IMPORT-03 | — | Defensive read: row shorter than 3 columns → blank team code | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenRowShorterThanThreeColumns_whenPreview_thenTreatedAsBlankTeamCode` | ✅ | ✅ green |
| 54-01-04 | 01 | 1 | IMPORT-04 | — | Tabs sorted ascending by year | unit | `./mvnw test -Dtest=DriverSheetImportServiceTest#givenTabsInReverseOrder_whenPreview_thenTabsSortedAscendingByYear` | ✅ | ✅ green |
| 54-01-05 | 01 | 1 | IMPORT-05, DATA-01 | — | `suggestedSeasonId` null + `ambiguousReason` on 0 or ≥2 matches; no Season auto-create | unit | `...#givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason` + `...#givenNoSeasonForYear_whenPreview_thenSuggestedSeasonNullWithNoSeasonReason` | ✅ | ✅ green |
| 54-01-06 | 01 | 1 | UX-01 | — | NEW_DRIVER bucket | unit | `...#givenNewPsnId_whenPreview_thenCategorisedAsNewDriver` | ✅ | ✅ green |
| 54-01-07 | 01 | 1 | UX-02 | — | NEW_ASSIGNMENT bucket (existing driver, no SeasonDriver for season) | unit | `...#givenExistingDriverNoSeasonDriver_whenPreview_thenCategorisedAsNewAssignment` | ✅ | ✅ green |
| 54-01-08 | 01 | 1 | UX-03 | — | CONFLICT bucket (SeasonDriver exists, different team) | unit | `...#givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict` | ✅ | ✅ green |
| 54-01-09 | 01 | 1 | UX-04, MATCH-01 | — | FUZZY_SUGGESTION bucket carries candidate driver + similarity | unit | `...#givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn` + Mockito `verify(driverMatchingService).findDriver(...)` | ✅ | ✅ green |
| 54-01-10 | 01 | 1 | UX-05 | — | UNCHANGED bucket (SeasonDriver exists, same team) | unit | `...#givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged` | ✅ | ✅ green |
| 54-01-11 | 01 | 1 | UX-06, DATA-02 | — | ERROR bucket — blank PSN, blank team, unknown team, duplicate; no Team auto-create | unit | `...#givenBlankPsnId...` + `...#givenBlankTeamCode...` + `...#givenUnknownTeamCode...` + `...#givenDuplicatePsnInTab...` | ✅ | ✅ green |
| 54-01-12 | 01 | 1 | MATCH-01 | — | Delegates to existing `DriverMatchingService` unchanged (case-insensitive coverage via EXACT branch) | unit | `...#givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive` | ✅ | ✅ green |
| 54-01-13 | 01 | 1 | MATCH-02 | — | Same PSN in multiple tabs → independent `TabPreview` rows (naive bucketing, D-07) | unit | `...#givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently` | ✅ | ✅ green |
| 54-01-14 | 01 | 1 | DATA-04 | — | No Flyway migration added | manual grep | `ls src/main/resources/db/migration/V*.sql` unchanged (no new V-file) | ✅ | ✅ green |
| 54-01-15 | 01 | 1 | DATA-05 | — | `RaceLineup` untouched by preview service | manual grep | `grep -ni "raceLineup\|RaceLineup" src/main/java/org/ctc/dataimport/DriverSheetImportService.java` returns zero hits | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/org/ctc/dataimport/DriverSheetImportServiceTest.java` — JUnit 5 test class with `@ExtendWith(MockitoExtension.class)`, all `@Mock` deps, `@InjectMocks` for `DriverSheetImportService`. **17 @Test methods present** (16 from VALIDATION map + 1 bonus `givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment`).
- [x] Shared test fixtures inside `@BeforeEach` — pattern mirrors `CsvImportServiceTest`.
- [x] Mock-setup helpers used (per Claude's discretion).
- [x] No new framework install — Mockito + AssertJ already on the test classpath via Spring Boot 4.x BOM.

*Phase 54 does NOT require `@SpringBootTest` or `@DataJpaTest`. Integration coverage of the full import flow lives in Phase 55.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| No new Flyway migration added | DATA-04 | Flyway checksums are immutable; verifying file-system state is an existence check, not a behavioral test | `ls src/main/resources/db/migration/V*.sql` before and after phase — list must be identical (no new V-file) |
| `RaceLineup` untouched | DATA-05 | Negative-space assertion; not expressible as a JUnit test | `grep -rn "raceLineup\|RaceLineup" src/main/java/org/ctc/dataimport/DriverSheetImportService.java` — zero hits |
| Project JaCoCo line coverage ≥82% with new code included | TEST-01 / QUAL gate (project constraint, not a Phase-54 REQ) | JaCoCo gate is enforced by the Maven build, not by a single test | `./mvnw verify` must pass; open `target/site/jacoco/index.html` and confirm `org.ctc.dataimport.DriverSheetImportService` ≥82% line coverage |

---

## Mock Boundaries

**Replaces the "Mock Boundaries" section from RESEARCH.md — canonical for executors.**

| Dependency | Stubbed Method | Typical Stub |
|------------|----------------|--------------|
| `GoogleSheetsService` | `extractSpreadsheetId(url)` | returns a fixed spreadsheet id string |
| `GoogleSheetsService` | `getSheetNames(id)` | returns `List.of("2023", "2024", "Roster", "Overall")` |
| `GoogleSheetsService` | `readRangeFromSheet(id, tab, "A:C")` | returns `List<List<Object>>` — 1 header row + N data rows; mix `String` cells with occasional empty `""` and short rows |
| `DriverMatchingService` | `findDriver(psn)` | returns `MatchResult.exact(...)`, `MatchResult.fuzzy(...)`, or `MatchResult.noMatch(...)` per scenario |
| `SeasonRepository` | `findByYear(int)` | returns `List.of(season2024)` (singleton), `List.of()` (none), or `List.of(s1, s2)` (ambiguous) |
| `TeamRepository` | `findByShortName(code)` | returns `Optional.of(team)` or `Optional.empty()` |
| `SeasonDriverRepository` | `findBySeasonIdAndDriverId(seasonId, driverId)` | returns `Optional.empty()` (NEW_ASSIGNMENT), `Optional.of(sdSameTeam)` (UNCHANGED), or `Optional.of(sdDifferentTeam)` (CONFLICT) |

**Guarantee:** No repository is called with real DB I/O in Phase 54 unit tests. No `@SpringBootTest`, no `@DataJpaTest`. Phase 55 will add the integration test.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (`DriverSheetImportServiceTest.java`)
- [x] No watch-mode flags used (Surefire runs forked, one-shot)
- [x] Feedback latency <10s for quick runs (`./mvnw test -Dtest=DriverSheetImportServiceTest` ~5s)
- [x] `nyquist_compliant: true` set in frontmatter after verification passes
- [x] `wave_0_complete: true` set in frontmatter after test-class stub compiles

**Approval:** approved 2026-04-25 (Jens Gross via /gsd-validate-phase audit)

---

## Validation Audit 2026-04-25

| Metric | Count |
|--------|-------|
| Tasks in scope | 15 |
| Gaps found | 0 |
| Resolved (already covered, status flipped) | 13 (all unit-test rows 54-01-02..14, manual rows 54-01-14/15) |
| Escalated | 0 |
| New tests written | 0 |
| Bonus coverage | 1 (`givenExistingDriverAndAmbiguousSeason_whenPreview_thenCategorisedAsNewAssignment`) |

**Audit method:** Cross-referenced VALIDATION.md per-task method names with `grep "void (given|when)"` against `DriverSheetImportServiceTest.java`. All 16 named methods exist; bonus method also present. Manual-only rows DATA-04 (Flyway grep returns single V1 file unchanged) and DATA-05 (`raceLineup` grep on `DriverSheetImportService.java` returns 0 hits) confirmed.

**Outcome:** Phase 54 is Nyquist-compliant. No gap-filling required — VALIDATION.md status was simply never closed during execution. Statuses flipped from `⬜ pending` → `✅ green` retroactively against shipped tests.
