# Phase 54: Preview Service & Row Categorization - Context

**Gathered:** 2026-04-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Pure backend: `DriverSheetImportService.preview(sheetUrl)` fetches year-numbered
tabs from a Google Sheet, categorizes every data row into one of six buckets,
and returns a `DriverSheetImportPreview` — **no DB writes, no UI, no
controller**. All downstream pieces (controller, templates, execute path,
integration tests) belong to Phase 55.

</domain>

<decisions>
## Implementation Decisions

### Season Auto-Match (tab name → Season)

- **D-01:** New repository method `SeasonRepository.findByYear(int year)`
  replaces the ROADMAP SC#3 reference to `findByName`/`findByDisplayLabel`
  (neither fits the data: `Season.name` is free-text like `"CTC Season 1"`,
  `Season.displayLabel` is a computed getter `"<year> | #<n> | <name>"` — not a
  DB field). Tab-name parsing: `Integer.parseInt(tabName)` on a `^\d{4}$`
  match (guaranteed by the tab filter).
- **D-02:** Auto-select rule: `findByYear(y)` returns a singleton → that
  Season's id becomes `suggestedSeasonId`. Returns `0` or `≥2` → `suggestedSeasonId = null`.
- **D-03:** `TabPreview` carries an optional `ambiguousReason` string field.
  Populated when `suggestedSeasonId == null` and the year had multiple
  candidates (`"Multiple seasons for year 2024"`) or zero candidates
  (`"No season found for year 2024"`). Phase 55 renders it as a hint next
  to the empty dropdown.

### Preview Data Model

- **D-04:** `TabPreview` uses **typed per-bucket fields** instead of a generic
  `Map<Bucket, List<Row>>`. Six typed record lists — each row record carries
  only the fields that bucket needs (e.g. `ConflictRow` has `existingTeamShortName`
  + `sheetTeamShortName`; `FuzzySuggestionRow` has `suggestedDriverId` + `similarity`;
  `ErrorRow` has `rawPsnId` + `rawTeamCode` + `ErrorReason reason`). No nullable
  catch-all fields.
- **D-05:** `DriverSheetImportPreview` is `record DriverSheetImportPreview(List<TabPreview> tabPreviews)`
  — tabs pre-sorted ascending by year (4-digit numeric string comparison == ascending year).

### Preview-State Persistence (cross-phase, locked for Phase 55)

- **D-06:** **No `@SessionAttributes`.** Phase 55 execute re-fetches the sheet
  and re-runs preview categorization server-side, then applies the user's
  per-row form decisions. Mirrors the existing `CsvImportController.execute()`
  pattern exactly — which uses re-fetch + `Map<String,String> allParams`
  (`confirm_<psnId>` convention). Satisfies QUAL-04 ("no new parallel
  mechanism"). Phase 54 therefore writes the preview service so it is
  **idempotent and cheap to call twice**.

### Cross-Tab Driver Identity (SC#4)

- **D-07:** **Naive bucketing + execute-side dedup.** Same new PSN in three
  tabs produces three independent `NewDriverRow` entries across three
  `TabPreview`s. Phase 55 `execute(...)` deduplicates by `psnId` on commit:
  first occurrence → `Driver.create()`, subsequent occurrences → reuse the
  same `Driver` and only create the `SeasonDriver` assignment.
- **D-08:** User decisions on `FuzzyMatchRow` are applied **per row, independent
  across tabs.** If the admin accepts fuzzy in 2023 but rejects in 2024 for
  the same PSN, two Drivers may end up being created — the system respects
  the explicit per-row form state rather than trying to cross-tab-enforce
  consistency. Documented as an intentional UX tradeoff, not a validation error.

### Error Encoding

- **D-09:** `ErrorRow.reason` is a typed enum `ErrorReason` with a
  `message()` helper returning a **hard-coded English string** (no i18n
  keys; UI is English-only per CLAUDE.md).
- **D-10:** `ErrorReason` values for Phase 54:
  - `BLANK_PSN_ID` — column A empty / whitespace only
  - `BLANK_TEAM_CODE` — column C empty / whitespace only
  - `UNKNOWN_TEAM_CODE` — non-blank team short code not found via `TeamRepository.findByShortName(...)`
  - `DUPLICATE_IN_TAB` — same PSN appears more than once in the same tab
- **D-11:** Duplicate handling: **first occurrence wins.** First row with a
  given PSN in a tab is categorized normally (NEW_DRIVER / NEW_ASSIGNMENT /
  CONFLICT / UNCHANGED / FUZZY_SUGGESTION). Every subsequent row with the
  same PSN in the same tab lands in the ERROR bucket with `DUPLICATE_IN_TAB`
  and carries its own `rawTeamCode` so the admin can see which duplicate was
  dropped.

### Row Bucketing Precedence (consolidated rule)

- **D-12:** Order of checks when categorizing a single row (first match wins):
  1. Blank PSN → `ERROR/BLANK_PSN_ID`
  2. Blank team code → `ERROR/BLANK_TEAM_CODE`
  3. Unknown team short code → `ERROR/UNKNOWN_TEAM_CODE`
  4. PSN already seen in this tab → `ERROR/DUPLICATE_IN_TAB`
  5. `DriverMatchingService.findDriver(psn)` returns `MatchType.FUZZY` →
     `FUZZY_SUGGESTION` (carries candidate `Driver` id + similarity)
  6. Match type is `EXACT` (includes the 3 exact-tier stages internally):
     look up existing `SeasonDriver(suggestedSeasonId, matchedDriver)`:
     - Not found or `suggestedSeasonId == null` → `NEW_ASSIGNMENT`
     - Found with same team → `UNCHANGED`
     - Found with different team → `CONFLICT`
  7. Match type is `NONE` → `NEW_DRIVER`

### Claude's Discretion

- Choice of internal data structure for "seen PSNs within a tab" during the
  duplicate check (Set vs Map — trivial).
- Exact class naming for the row records (e.g. `NewDriverRow` vs `NewDriverEntry`)
  as long as `TabPreview`'s field names read clearly in Thymeleaf.
- Whether to front-load all team / season lookups into a per-tab cache or
  query repeatedly — researcher/planner may decide based on typical sheet
  size (<100 rows/tab expected in practice).
- Test utility helpers / builders to keep the 9+ given-when-then scenarios
  readable.

### Roadmap Deviation Noted

- **D-13:** ROADMAP Success Criterion #3 ("`findByName`/`findByDisplayLabel`
  fallback") is structurally wrong — both methods either don't exist or
  don't match the actual data shape. Implementation deliberately uses
  `findByYear(int)` + uniqueness instead (see D-01..D-03). CONTEXT.md is
  the authoritative decision; ROADMAP SC#3 will be tightened after the
  phase completes. Verifier should accept the `findByYear` pattern as
  satisfying the spirit of SC#3.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design & Requirements

- `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md` — full
  feature design (architecture, data flow, test scenarios). Note: the
  "preview-state via @SessionAttributes" assumption in §Architecture is
  **superseded** by D-06 (re-fetch pattern matches existing CsvImport).
- `.planning/REQUIREMENTS.md` §v1.8 — 28 requirements, of which 17 are
  mapped to Phase 54 (IMPORT-02..05, UX-01..06, MATCH-01..02, DATA-01..02,
  DATA-04..05, TEST-01).
- `.planning/ROADMAP.md` §"Phase 54: Preview Service & Row Categorization"
  — Goal + 6 Success Criteria. SC#3 superseded by D-01..D-03 (see D-13).

### Reuse (read before implementing anything)

- `src/main/java/org/ctc/dataimport/GoogleSheetsService.java` — use
  `extractSpreadsheetId(url)`, `getSheetNames(spreadsheetId)`,
  `readRangeFromSheet(spreadsheetId, tabName, "A:C")`. No modifications.
- `src/main/java/org/ctc/dataimport/DriverMatchingService.java` — use
  `findDriver(String)` returning `MatchResult(searchTerm, driver,
  MatchType{EXACT|FUZZY|NONE}, similarity)`. The 4 spec stages (exact,
  CI, alias, Levenshtein≥0.8) are already all inside; EXACT covers stages
  1-3, FUZZY covers stage 4. No modifications.
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — **read
  `execute(...)`** (≈ line 124) for the re-fetch pattern + `confirm_<psnId>`
  form-params convention that Phase 55 must mirror. No modifications in
  Phase 54.
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — `ImportPreview`
  inner class (≈ line 555) as a reference for preview-data-class style
  (records, Lombok, immutable fields). Do **not** reuse the class itself
  — Phase 54 introduces parallel but typed classes.

### Domain

- `src/main/java/org/ctc/domain/model/Season.java` — fields `name` (String),
  `year` (int), `number` (int); `getDisplayLabel()` is computed. Confirms
  why the ROADMAP's `findByName`/`findByDisplayLabel` references don't
  fit (see D-01, D-13).
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — extend
  with new `findByYear(int)`. Existing methods (`findByActiveTrue`,
  `findBySeasonTeamsTeamId`, `findByYearAndNumber`) untouched.
- `src/main/java/org/ctc/domain/repository/TeamRepository.java` — use
  existing `findByShortName(String)` for team resolution.
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` —
  lookup existing `SeasonDriver(season, driver)` for CONFLICT/UNCHANGED
  detection. Verify the exact finder method name during research.
- `src/main/java/org/ctc/domain/repository/DriverRepository.java` — used
  transitively via `DriverMatchingService`; no direct calls from the new
  service.

### Project Conventions

- `CLAUDE.md` — architectural principles (thin controllers, DTO not
  entity, no fallback calculations, BDD test naming, 82% coverage gate,
  English UI).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`GoogleSheetsService`** — full Google Sheets v4 API wrapper already
  exists. Phase 54 needs no new HTTP/auth plumbing.
- **`DriverMatchingService.findDriver(psn)`** — covers all 4 matching
  stages in a single call; returns a `MatchResult` with `MatchType`
  enum. Phase 54 uses it as a black box per MATCH-01.
- **`ImportPreview` data-class pattern (CsvImportService)** — records + Lombok,
  inner classes under the service, Thymeleaf-friendly getters.
- **`CsvImportController.execute()` form-param pattern** — `confirm_<psnId>`
  convention for per-row decisions; worth mirroring exactly in Phase 55
  (`skip_<psnId>_<tabYear>`, `accept_<psnId>_<tabYear>`).

### Established Patterns

- **`@Service` with `@RequiredArgsConstructor` + `@Slf4j`** — all services
  use constructor injection of final fields. Apply to `DriverSheetImportService`.
- **Unit tests live next to the service under `src/test/java/org/ctc/dataimport/`**
  with names like `CsvImportServiceTest.java`. Given-when-then method
  naming (CLAUDE.md BDD convention).
- **No OSIV dependency in services** — the preview service must be
  callable from tests without an HTTP request; it does **not** rely on
  OSIV for lazy loading.

### Integration Points

- `DriverSheetImportService` wires into the same `org.ctc.dataimport`
  package (alongside `CsvImportService`). No package restructuring.
- Phase 55 controller will live in `org.ctc.admin.controller`
  (`DriverSheetImportController`) — out of scope for Phase 54, but the
  service contract must be stable enough for that controller to consume
  without modification.

### Constraints from Scout

- `SeasonRepository` currently has no `findByYear(int)` — **new method
  must be added** in Phase 54 (see D-01).
- `Season.displayLabel` is a **computed getter**, not a persisted column
  — it cannot be used in a JPA finder. Do not introduce a getter-backed
  derived query.
- `CsvImportController` uses a stateless re-fetch, **not** `@SessionAttributes`
  — contradicts the design-spec assumption. Locked as D-06.

</code_context>

<specifics>
## Specific Ideas

- **Bucket precedence rule** (D-12) was derived by walking through a
  worked example: a row with blank PSN + unknown team would otherwise
  hit two ERROR reasons; the admin should see the earliest fault
  first (blank PSN) so they fix the sheet incrementally.
- **Per-row independence for fuzzy decisions** (D-08) matches how the
  HTML form posts decisions — each `accept_<psnId>_<year>` checkbox is
  independent. Cross-tab consistency would require JavaScript or a
  second submission round; intentionally avoided.
- **Test-scenarios target ≥9** per SC#6. D-07..D-12 each map to at least
  one scenario:
  - `givenMixedTabNames_whenPreview_thenOnlyFourDigitTabsIncluded` (filter)
  - `givenNewPsnId_whenPreview_thenCategorisedAsNewDriver` (NEW_DRIVER)
  - `givenExistingPsnIdDifferentCase_whenPreview_thenResolvedViaCaseInsensitive` (EXACT via CI)
  - `givenFuzzyCandidate_whenPreview_thenSuggestedMatchAwaitsUserOptIn` (FUZZY_SUGGESTION)
  - `givenExistingSeasonDriverSameTeam_whenPreview_thenCategorisedAsUnchanged` (UNCHANGED)
  - `givenExistingSeasonDriverDifferentTeam_whenPreview_thenCategorisedAsConflict` (CONFLICT)
  - `givenUnknownTeamCode_whenPreview_thenRowErroredWithUnknownTeam` (ERROR/UNKNOWN_TEAM_CODE)
  - `givenBlankPsnId_whenPreview_thenRowErroredWithBlankPsn` (ERROR/BLANK_PSN_ID)
  - `givenBlankTeamCode_whenPreview_thenRowErroredWithBlankTeam` (ERROR/BLANK_TEAM_CODE)
  - `givenDuplicatePsnInTab_whenPreview_thenSecondRowErroredWithDuplicate` (ERROR/DUPLICATE_IN_TAB)
  - `givenSamePsnInMultipleTabs_whenPreview_thenEachTabCategorisedIndependently` (MATCH-02 cross-tab)
  - `givenMultipleSeasonsForYear_whenPreview_thenSuggestedSeasonNullWithAmbiguousReason` (D-03)

</specifics>

<deferred>
## Deferred Ideas

- **Cross-tab fuzzy-decision propagation UX** — a "apply this decision to
  all tabs where this PSN appears" master checkbox. Nice polish, belongs
  in a future iteration once admins have used v1.8 and feedback surfaces.
- **i18n of error messages** — D-09 commits to English-only. If the
  admin UI ever gets German localization, `ErrorReason.message()` can
  be converted to a `messages.properties` key lookup.
- **Configurable fuzzy threshold** (currently 0.8 constant in
  `DriverMatchingService`). Out of scope; would be its own phase.
- **SeasonDriverRepository finder verification** — research must confirm
  the exact method name/signature for `findBySeasonAndDriver(...)` or
  similar. Default assumption: method exists; if not, Phase 54 adds it.
- **ROADMAP SC#3 text correction** — post-phase cleanup commit to tighten
  the success criterion wording from `findByName/findByDisplayLabel` to
  `findByYear with uniqueness check` (D-13).

</deferred>

---

*Phase: 54-preview-service-row-categorization*
*Context gathered: 2026-04-24*
