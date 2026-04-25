# Phase 55: Admin Import UI & Transactional Execute - Context

**Gathered:** 2026-04-24
**Status:** Ready for planning

<domain>
## Phase Boundary

Admin-facing slice: an admin navigates `/admin/drivers`, clicks an "Import from
Google Sheet" button, lands on a new page at `/admin/drivers/import`, submits
a Sheet URL, and is shown a per-tab preview produced by Phase 54's
`DriverSheetImportService.preview(sheetUrl)`. The admin picks a Season per
year-tab, toggles Skip checkboxes on conflicts and Accept checkboxes on fuzzy
matches, clicks Execute, and the server re-fetches the sheet, re-runs
preview categorization, applies the admin's decisions, and persists `Driver`
rows + `SeasonDriver` assignments in a single `@Transactional` boundary.
On success, redirect to `/admin/drivers/import` (or `/admin/drivers`) with a
flash headline-count summary.

**In scope:**
- 1 new controller `DriverSheetImportController` in `org.ctc.admin.controller`
- 2 new Thymeleaf templates (`admin/driver-import.html`, `admin/driver-import-preview.html`)
- 1 new button on `admin/drivers.html` toolbar
- Transactional execute path that calls Phase 54's service twice (preview
  for validation, then imperatively applies decisions) and writes to
  `DriverRepository` + `SeasonDriverRepository`
- Full integration test `DriverSheetImportControllerIT` with mocked
  `GoogleSheetsService`

**Out of scope:**
- Any modification of Phase 54's `DriverSheetImportService` (contract is locked)
- Any modification of `RaceLineup` (DATA-05 held across both phases)
- Any new Flyway migration (DATA-04 held across both phases)
- Playwright E2E tests (deferred at milestone level — Google Sheets × Playwright
  mocking is fragile)
- i18n — all UI text English per CLAUDE.md

</domain>

<decisions>
## Implementation Decisions

### Preview Page Layout (D-14)

- **D-14:** One `.card` section per year-tab, vertically stacked, matching
  the existing `admin/import-preview.html` pattern. Each section shows:
  - H2 = the 4-digit year
  - Season dropdown (pre-selected to `suggestedSeasonId` if non-null; empty
    with `ambiguousReason` hint rendered below if null)
  - Six inner tables (or table sections), one per bucket, each with a
    count heading:
    - `New Drivers (N)` — psnId + teamShortName columns
    - `New Assignments (N)` — psnId + existing driver link + teamShortName
    - `Conflicts (N)` — psnId + existing team + sheet team + **Skip checkbox**
    - `Fuzzy Match Suggestions (N)` — psnId + suggested driver (psnId/nickname
      + similarity %) + teamShortName + **Accept checkbox**
    - `Unchanged (N)` — psnId + teamShortName (display-only, greyed)
    - `Errors (N)` — psnId + teamCode + error reason text (display-only,
      these rows are never imported)
  - Empty buckets collapse gracefully (hide the sub-table when count = 0;
    keep the heading only if the whole tab has non-zero buckets, else skip
    the count header entirely to reduce visual noise)
- Execute button at the bottom of the form (outside the per-tab loop), with
  the Ambiguous-Season warning banner directly above it (see D-16).

### Execute Form Binding (D-15)

- **D-15:** Mirror `CsvImportController.execute(...)` exactly. Controller
  signature:
  ```java
  @PostMapping("/execute")
  public String execute(@RequestParam String sheetUrl,
                        @RequestParam(required = false) Map<String, String> allParams,
                        RedirectAttributes redirectAttributes) { ... }
  ```
  Per-row / per-tab decisions flow through `allParams` using these
  conventions (all lowercase keys):
  - `seasonId_<year>=<uuid>` — admin-picked or pre-selected Season per tab
  - `skip_<psnId>_<year>=on` — admin ticked Skip on a CONFLICT row (absence
    means overwrite per DATA-03 default)
  - `accept_<psnId>_<year>=<driverUuid>` — admin ticked Accept on a FUZZY
    row (absence means treat as NEW_DRIVER — creates a brand-new Driver,
    does NOT link to the suggested existing Driver)
- **QUAL-03 compliance:** no JPA entity is bound via `@ModelAttribute`; the
  flat `@RequestParam String sheetUrl` handles the sole scalar, and
  `Map<String, String>` handles the dynamic per-row set — matches existing
  project convention, no new parallel mechanism needed.
- **QUAL-04 compliance:** same form-param design as `CsvImportController`;
  no DTO aggregation, no `@SessionAttributes`.
- Form DTO for the GET form page (`/admin/drivers/import`): none needed —
  the page shows a simple `<input name="sheetUrl">` and POSTs to `/preview`
  with just that one field. No validation annotations beyond HTML
  `required` + server-side null/blank guard in the controller.

### Ambiguous-Season Handling (D-16)

- **D-16:** **Proceed-with-warning**, not hard-block. When the preview
  comes back with one or more `TabPreview.suggestedSeasonId == null`:
  - Each affected tab renders its Season dropdown empty with the
    `ambiguousReason` text shown as a small warning hint (e.g.
    "No season found for year 2024" / "Multiple seasons for year 2024 —
    pick one or skip this tab").
  - A warning banner sits immediately above the Execute button summarizing:
    "⚠ 2 tabs have no season selected: 2024, 2025. These tabs will be
    skipped on execute."
  - On execute: tabs whose resolved `seasonId_<year>` is blank/missing are
    silently skipped (their rows are not imported). The flash summary
    includes the skipped-tab list alongside the headline counts so the
    admin sees what was skipped post-hoc.
- Rationale: the admin may legitimately want to import 2023 + 2024 but
  leave 2025 for later (the Season doesn't exist yet). Hard-blocking
  forces a navigation detour to `/admin/seasons/new`. Silent skipping
  would be invisible. Warning banner + post-hoc flash gives the admin
  transparent control without friction.

### Flash Summary Detail (D-17)

- **D-17:** Headline aggregated counts across all tabs, one-line
  flash. Matches `CsvImportController.execute()` style.
  ```
  Import successful: 5 new drivers, 12 new assignments,
  2 conflicts overwritten, 1 conflict skipped, 3 unchanged, 1 error.
  Skipped tabs: [2025] (no season selected).
  ```
  - Counts are totals across all tabs combined.
  - "conflicts overwritten" counts CONFLICT rows where Skip was NOT ticked.
  - "conflicts skipped" counts CONFLICT rows where Skip WAS ticked.
  - "error" counts rows already in the ERROR bucket (never imported).
  - "Skipped tabs" line only appears when D-16 proceed-with-warning fires.
- On exception (DataAccessException / BusinessRuleException):
  single-line error flash ("Import error: {message}") → redirect to
  `/admin/drivers/import`, `@Transactional` ensures zero partial writes.

### Entry Button (D-18)

- **D-18:** `admin/drivers.html` toolbar gets an "Import from Google Sheet"
  `.btn-secondary` sized matching the existing `+ New Driver` button,
  placed immediately to its LEFT. Order in the toolbar `.actions` div:
  ```html
  <input class="search-input" ...>
  <a href="/admin/drivers/import" class="btn btn-secondary">Import from Google Sheet</a>
  <a href="/admin/drivers/new" class="btn btn-primary">+ New Driver</a>
  ```
- Secondary styling communicates "supplementary workflow"; primary stays
  on the single-driver create path. No icon, plain text — matches the
  rest of the admin toolbar convention.

### Driver Create Defaults (D-19)

- **D-19:** When the execute path creates a brand-new `Driver` from the
  sheet (NEW_DRIVER bucket, or FUZZY without Accept ticked):
  - `psnId` = sheet column A value (already trimmed via Phase 54's
    `cellToString`)
  - `nickname` = same as `psnId` (sensible default; admin can edit later
    via `/admin/drivers/{id}/edit`)
  - `active` = `true` (imported drivers are assumed racing-active)
  - `aliases` = empty set / list
- Rationale: admins can fine-tune per-driver metadata in the existing
  driver edit UI. No schema change; no Flyway migration (DATA-04).

### Transactional Boundary (IMPORT-06, locked)

- **Locked by requirement:** the entire execute pass (all tabs, all rows,
  all Driver creates + SeasonDriver writes) runs inside a single
  `@Transactional` boundary on a service method. If any write fails
  (DB constraint violation, optimistic lock, etc.), the whole import
  rolls back. The flash reports the exception message.
- Transaction scope: service-level, not controller-level. Controller
  delegates to `DriverSheetImportService.execute(...)` (new method added
  in Phase 55 — Phase 54 kept the service read-only). `@Transactional`
  sits on the new `execute(...)` method.

### Cross-Tab Driver Dedup on Execute (Phase 54 D-07, reinforced)

- Phase 54 D-07 locked "naive bucketing + execute-side dedup". Phase 55
  implements the dedup:
  - Execute walks tabs in ascending-year order (Phase 54 already sorts).
  - Internal `Map<String, UUID>` maps `psnId → driverId` across the whole
    execute run.
  - First occurrence of a new PSN that needs creating → `Driver.create()`,
    store id in the map.
  - Subsequent occurrences of the same PSN across later tabs → look up
    the map, reuse the same `driverId`, only create a new `SeasonDriver`.
- This means a Fuzzy decision in 2023 doesn't retroactively affect 2024:
  if 2024's row is `NEW_DRIVER` (no Accept stubbed), it stays a new driver
  even if 2023 linked the same PSN to an existing driver — Phase 54 D-08
  (per-row independence) confirmed by user choice here.

### Claude's Discretion

- Exact method signature on `DriverSheetImportService.execute(...)` —
  planner/researcher to design (likely: takes sheetUrl, parsed per-row
  decisions, returns a result record with the headline counts).
- Internal result record shape (likely a `DriverSheetImportResult` record
  with fields for each count). Record definition lives in
  `DriverSheetImportService` alongside the existing 7 preview records —
  if naming consistency matters, prefix the new record with `Execute`
  (e.g. `ExecuteResult`) so it's clearly distinct from `DriverSheetImportPreview`.
- Preview-page JavaScript: whether to add a small "Accept all fuzzy" /
  "Skip all conflicts" bulk-action button above each bucket table. If
  simple (pure DOM, no framework), include; if it grows complex, defer
  to a follow-up iteration (captured in Deferred Ideas).
- Exact error messages for validation failures (missing sheetUrl, bad
  URL format, etc.) — sensible English strings; no i18n keys.
- `DriverSheetImportControllerIT` test method naming and scope depth —
  researcher to decide based on CsvImportControllerIT precedent.

</decisions>

<specifics>
## Specific Ideas

- The user explicitly picked the **CsvImport-mirror** pattern for form
  binding (D-15) — this is a strong signal that the overall controller
  structure, exception handling, and template layout should stay as close
  to `CsvImportController` + `admin/import-preview.html` as practical.
  Researcher should read `CsvImportController.java` (full file) and
  `admin/import-preview.html` (full file) before planning.
- The user rejected both "tab-UI" and "unified table" in favor of
  vertical card-per-tab sections (D-14). Do NOT introduce tab JavaScript
  or a consolidated filterable table, even if the sheet is large.
- The user rejected the hard-block approach for ambiguous seasons in
  favor of proceed-with-warning + post-hoc flash (D-16). This is a
  deliberate UX tradeoff favoring admin flexibility over strict
  data-quality gates.
- The user chose secondary button styling for the entry point (D-18) —
  Phase 55 is a power-user workflow, not the primary driver-management
  entry point. Don't escalate visual weight.
- The user chose `nickname = psnId` default (D-19) — do NOT introduce
  a "nickname required, force manual entry" gate.

</specifics>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design & Requirements

- `docs/superpowers/specs/2026-04-24-bulk-driver-import-design.md` — full
  milestone design; note §Architecture's `@SessionAttributes` assumption
  is **superseded** by D-06 (carried forward from Phase 54) and D-15.
- `.planning/REQUIREMENTS.md` §v1.8 — 11 of the 28 milestone requirements
  are Phase 55 scope (IMPORT-01, IMPORT-06, UX-07, UX-08, DATA-03,
  TEST-02, TEST-03, QUAL-01, QUAL-02, QUAL-03, QUAL-04).
- `.planning/ROADMAP.md` §"Phase 55: Admin Import UI & Transactional
  Execute" — Goal + 5 Success Criteria.
- `.planning/phases/54-preview-service-row-categorization/54-CONTEXT.md`
  — decisions D-06 (no @SessionAttributes), D-07 (cross-tab dedup on
  execute), D-08 (per-row fuzzy independence), D-12 (bucketing precedence).
  All locked; do not re-litigate.
- `.planning/phases/54-preview-service-row-categorization/54-01-SUMMARY.md`
  — shipped service contract: 7 records + `ErrorReason` enum. Note the
  post-phase-54 refinements (iter-2 review fixes): `ErrorRow` uses
  `psnId` / `teamCode` (not `rawPsnId` / `rawTeamCode`);
  `UnchangedRow.existingSeasonDriverId` is now available; redundant
  `.trim()` removed.

### Reuse (read before implementing)

- `src/main/java/org/ctc/dataimport/DriverSheetImportService.java` (shipped
  in Phase 54) — invoke `preview(sheetUrl)` from the controller; ADD a
  new `execute(...)` method in Phase 55 with `@Transactional` — the
  preview method itself stays unchanged.
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — **full
  file** is the reference blueprint. In particular `execute(...)` at
  line 124 shows the `Map<String, String> allParams` iteration pattern
  (lines 178-189), the exception types to catch (line 205), and the
  redirect flash idiom.
- `src/main/java/org/ctc/dataimport/CsvImportService.java` — reference
  for the result-record pattern (`ImportPreview` inner class ≈ line 555)
  and `executeMultiRaceImport(...)` as an example of imperative
  execute-side logic.
- `src/main/resources/templates/admin/import-preview.html` — reference
  for the card-per-section Thymeleaf pattern (lines 15-80 show the
  multi-race per-race-card loop).
- `src/main/resources/templates/admin/import.html` — reference for the
  simple form page structure (Sheet URL input → POST /preview).
- `src/main/resources/templates/admin/drivers.html` — the entry point
  where D-18's button is added (toolbar at lines 6-11).
- `src/main/resources/templates/admin/layout.html` — `admin/layout :: layout(title, ::section)`
  fragment used by every admin page; new templates use the same.

### Domain

- `src/main/java/org/ctc/domain/model/Driver.java` — constructor
  `Driver(psnId, nickname)`, plus `setActive(true)` after construction;
  `aliases` default-initialized to empty collection.
- `src/main/java/org/ctc/domain/model/SeasonDriver.java` — constructor
  `SeasonDriver(season, driver, team)`; Phase 55 creates these imperatively
  inside the execute transaction.
- `src/main/java/org/ctc/domain/repository/DriverRepository.java` —
  `save(Driver)` for new drivers.
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` —
  `save(SeasonDriver)` for new assignments; `findById(...)` for the
  Skip branch (load existing SeasonDriver by `existingSeasonDriverId`
  from `ConflictRow` to confirm it still exists); `delete(...)` or
  in-place `setTeam(...) + save(...)` for the overwrite-on-conflict branch.

### Project Conventions

- `CLAUDE.md` — architectural principles. Key items relevant to Phase 55:
  "Keep Controllers Thin", "DTOs instead of Entities in Controllers",
  "Keep Thymeleaf Templates Lean", "No Inline Styles on Buttons",
  "Do Not Modify Flyway Migrations", Given-When-Then test naming,
  82% coverage gate.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`DriverSheetImportService.preview(sheetUrl)`** — Phase 54's shipped
  API, called by both `POST /preview` (to render the preview page) and
  `POST /execute` (to re-fetch + re-derive before applying decisions).
  Idempotent by design (Phase 54 D-06).
- **`CsvImportController` exception handling** — catches `IOException`,
  `BusinessRuleException`, `ValidationException`, `IllegalArgumentException`,
  `IllegalStateException`, `DataAccessException` in one block → flash
  + redirect. Phase 55 mirrors this.
- **`admin/layout.html` Thymeleaf fragment** — `~{admin/layout :: layout(title, ::section)}`.
  New templates reuse without modification.
- **Existing admin CSS classes** — `.card`, `.toolbar`, `.actions`, `.btn`,
  `.btn-primary`, `.btn-secondary`, `.btn-sm`, `.badge`, `.badge-active`,
  `.badge-inactive`, `.alert`, `.alert-error`, `.empty-state`, `.table-scroll`,
  `.search-input`, `.mb-md`. No new CSS classes needed; `admin.css` stays
  untouched (QUAL-01).

### Established Patterns

- **Controller method pattern:**
  ```java
  @GetMapping → render form page with addCommonAttributes(model)
  @PostMapping("/preview") → delegate to service, catch exceptions,
                             return form with errorMessage on failure,
                             return preview page on success
  @PostMapping("/execute") → delegate to service @Transactional,
                             catch exceptions, flash + redirect
  ```
- **Exception policy:** controller methods never rethrow — they always
  translate to a user-visible flash or model errorMessage. Business logic
  exceptions (BusinessRuleException, ValidationException) get their
  `getMessage()` surfaced verbatim; infrastructure exceptions get a
  prefixed generic message.
- **Model attribute conventions:** `successMessage` / `errorMessage`
  flash attributes; `preview` / `previews` for the preview DTO.
- **Integration test pattern (TEST-02):** `*ControllerIT.java` uses
  `@SpringBootTest` + `MockMvc` + `@MockBean GoogleSheetsService` to
  short-circuit the real Sheets API. Assertion style: `.andExpect(...)` +
  repository state checks post-execute.

### Integration Points

- **Entry:** `admin/drivers.html` toolbar gains ONE new `<a>` element
  (D-18). No JS, no new controller method on `DriverController`.
- **New controller:** `DriverSheetImportController` in `org.ctc.admin.controller`
  — same package as `DriverController`. `@RequestMapping("/admin/drivers/import")`.
  Injects `DriverSheetImportService` (Phase 54) + `GoogleSheetsService`
  (availability check) + `SeasonManagementService` (populates Season
  dropdowns per year via `findAll()` or `findByActiveTrue()`).
- **New service method:** `DriverSheetImportService.execute(...)` — added
  in Phase 55. Phase 54's `preview(...)` remains unchanged.
- **Redirect target after execute:** `redirect:/admin/drivers/import`
  (mirror `CsvImportController`'s self-redirect) — NOT back to
  `/admin/drivers` so the admin sees the flash in context. Alternative
  (`redirect:/admin/drivers`) is reasonable too; planner to confirm
  during implementation.

### Constraints from Scout

- `CsvImportController` uses `@RequestMapping("/admin/import")` (NOT
  under `/admin/drivers/...`). Phase 55's route is nested under drivers:
  `/admin/drivers/import` — small structural divergence but matches
  REQUIREMENTS.md IMPORT-01 verbatim.
- `admin/import-preview.html` has inline `style="..."` attributes on
  `<tr>` and `<span>` elements (lines 50, 72). **Phase 55 MUST NOT
  copy those** — use CSS classes per QUAL-01 + CLAUDE.md "No Inline
  Styles on Buttons" (the spirit extends to all admin templates).
- Existing admin.css has `.badge-active`, `.badge-inactive` but NOT
  specific bucket-color badges. Planner may add minimal semantic
  classes (e.g. `.badge-conflict`, `.badge-fuzzy`, `.badge-error`,
  `.badge-new`, `.badge-unchanged`) to `admin.css`. If research confirms
  color-coding is useful, add these in the same phase; otherwise rely
  on table-section headers alone for bucket affordance.

</code_context>

<deferred>
## Deferred Ideas

- **Bulk-action buttons on preview page** — "Accept all fuzzy" / "Skip
  all conflicts" buttons per tab or global. Captured under Claude's
  Discretion (D-19 discretion list) — simple DOM version can be
  in-scope; a more elaborate Select-N-rows batch UI belongs in a later
  iteration.
- **Import history / audit log** — showing past imports with timestamps,
  admin, counts. Would need a new `ImportRun` entity + Flyway migration;
  out of scope (DATA-04 milestone-wide constraint). Could be its own
  phase in v1.9.
- **Email / push notification on completion** — not part of the spec;
  admins are already on the page to see the flash summary.
- **Dry-run / preview-only export (CSV download of categorized rows)** —
  nice-to-have for audit. Out of scope; could be added post-v1.8.
- **Undo / rollback a completed import** — `@Transactional` gives
  all-or-nothing at execute time, but once committed there's no in-app
  undo. Deferred; would require a reverse-apply path + ImportRun entity.
- **Cross-tab fuzzy-decision propagation UX** — already deferred from
  Phase 54; reaffirmed here.
- **i18n / German translation of admin strings** — already deferred at
  milestone level (CLAUDE.md: English UI).
- **Playwright E2E tests for the import flow** — already deferred at
  milestone level (fragile Sheets × Playwright mocking). Unit +
  integration tests must meet the 82% coverage gate (TEST-03).

</deferred>

---

*Phase: 55-admin-import-ui-transactional-execute*
*Context gathered: 2026-04-24*
