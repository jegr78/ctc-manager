# Phase 18: Merge UI - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Admin can initiate, preview, and confirm a driver merge from the driver detail page. A merge button opens the merge workflow, the admin selects a target driver, sees a preview of affected references, explicitly confirms, and is redirected to the target driver's detail page with a success message. This phase covers the controller, DTO, template, and a preview service method. The merge execution logic already exists in `DriverMergeService` (Phase 16+17).

</domain>

<decisions>
## Implementation Decisions

### Merge Workflow
- **D-01:** Separate merge page at `/admin/drivers/{id}/merge` — a "Merge" button on the driver detail page links to this page (consistent with the Edit button pattern)
- **D-02:** Two-step server-side flow: Step 1 shows source driver info + target driver dropdown, Step 2 (after form submit) shows preview with counts + confirm button
- **D-03:** No modals or AJAX — pure server-side rendering with form submits (consistent with app architecture)

### Target Driver Selection
- **D-04:** HTML `<select>` dropdown with all drivers sorted by PSN-ID, excluding the source driver
- **D-05:** Each option shows PSN-ID and nickname for identification (e.g., "PlayerOne (Nick)")
- **D-06:** Controller loads all drivers and passes them to the model, filtering out source driver

### Preview Mechanism
- **D-07:** New `DriverMergeService.previewMerge(sourceId, targetId)` method that counts affected FK entries without executing — returns a `MergePreview` record with counts per FK table
- **D-08:** Preview shows: SeasonDriver count (reassign + duplicates), RaceLineup count (reassign + duplicates), RaceResult count (reassign + duplicates), PsnAlias count
- **D-09:** `MergePreview` record is separate from `MergeResult` — preview counts what WILL happen, result counts what DID happen

### Confirmation & Success
- **D-10:** "Confirm Merge" button on the preview page submits POST to `/admin/drivers/{id}/merge` — with `targetId` as hidden form field
- **D-11:** JavaScript `confirm('Really merge [source] into [target]? This cannot be undone.')` as safety net on the confirm button (consistent with delete pattern)
- **D-12:** After successful merge, redirect to target driver detail page (`/admin/drivers/{targetId}`) with flash message "Driver merged: [sourcePsnId] into [targetPsnId] — [total] references reassigned, [dropped] duplicates resolved"
- **D-13:** On error (e.g., source already deleted), redirect back to driver list with error flash message

### Controller & DTO
- **D-14:** New endpoints in `DriverController`: `GET /admin/drivers/{id}/merge` (form), `POST /admin/drivers/{id}/merge/preview` (preview), `POST /admin/drivers/{id}/merge` (execute)
- **D-15:** No separate Form DTO needed — target driver ID comes as `@RequestParam UUID targetId` (consistent with the simple `assignToSeason` pattern)
- **D-16:** Controller injects `DriverMergeService` alongside existing `DriverService`

### Template
- **D-17:** New template `admin/driver-merge.html` — uses the existing `admin/layout` fragment
- **D-18:** Merge button added to `driver-detail.html` toolbar between Edit and Delete buttons, styled as `btn btn-secondary` (not primary — merge is less common than edit)

### Claude's Discretion
- Preview page layout and styling details
- Exact wording of confirmation dialog and flash messages
- Whether to show driver names in the preview table or just counts
- Internal implementation of `previewMerge()` (can reuse query patterns from `merge()`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Service Layer (merge logic)
- `src/main/java/org/ctc/domain/service/DriverMergeService.java` — Existing `merge()` method with `MergeResult` record; add `previewMerge()` here
- `src/main/java/org/ctc/domain/service/DriverService.java` — `findAll()`, `findById()` for loading driver lists

### Controller (endpoint patterns)
- `src/main/java/org/ctc/admin/controller/DriverController.java` — Existing endpoints, add merge endpoints here

### Templates (UI patterns)
- `src/main/resources/templates/admin/driver-detail.html` — Add merge button to toolbar
- `src/main/resources/templates/admin/driver-form.html` — Reference for form layout patterns
- `src/main/resources/templates/admin/season-detail.html` — Reference for confirm() pattern on delete

### Domain Model (FK tables for preview counts)
- `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` — `findByDriverId()` for counting
- `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` — `findByDriverId()` for counting
- `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` — `findByDriverId()` for counting
- `src/main/java/org/ctc/domain/repository/PsnAliasRepository.java` — `findByDriverId()` for counting

### CSS
- `src/main/resources/static/admin/css/admin.css` — Button classes (`btn-secondary`, `btn-primary`, `btn-danger`), modal styles (not used but available)

### Requirements
- `.planning/REQUIREMENTS.md` — MERGE-01, MERGE-02, MERGE-03, MERGE-04

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DriverMergeService.merge()` — Already handles all FK reassignment + duplicate handling, returns `MergeResult` with all counts
- `DriverService.findAll()` — Returns all drivers for dropdown population
- `DriverService.findById()` — Standard entity lookup for source driver
- `admin/layout` Thymeleaf fragment — Standard admin page layout
- `confirm()` JS pattern — Used on delete buttons throughout the app
- `.btn-secondary` CSS class — For non-primary actions like merge

### Established Patterns
- Controllers use `RedirectAttributes.addFlashAttribute()` for success/error messages
- Form DTOs with `@Valid` + `BindingResult` for complex forms; `@RequestParam` for simple inputs
- `@RequiredArgsConstructor` for constructor injection
- Templates use `th:replace="~{admin/layout :: layout(...)}"` layout pattern
- Toolbar div with `.actions` for page-level buttons (Edit, Delete)

### Integration Points
- `DriverController` — Add 3 new endpoints + inject `DriverMergeService`
- `driver-detail.html` — Add one merge button to toolbar
- `DriverMergeService` — Add `previewMerge()` method (query-only, no mutation)
- New template `driver-merge.html` — Two states: select target vs. preview+confirm

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 18-merge-ui*
*Context gathered: 2026-04-07*
