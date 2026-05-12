---
id: "09"
title: "Templates — backup landing extension + preview + confirm pages"
phase: 74
plan: 09
type: execute
wave: 3
depends_on: ["08"]
requirements: [IMPORT-03, IMPORT-04]
files_modified:
  - src/main/resources/templates/admin/backup.html
  - src/main/resources/templates/admin/backup-preview.html
  - src/main/resources/templates/admin/backup-confirm.html
autonomous: true
must_haves:
  truths:
    - "Admin sees the existing Phase 73 Export Backup card AND a new Import Backup card stacked below on /admin/backup."
    - "Admin can pick a .zip file in the Import Backup card and submit it via multipart POST to /admin/backup/import-preview."
    - "After a successful preview stage, admin lands on /admin/backup-preview view rendering header block, schema-match banner, 24-card grid with delta pills, and Proceed/Cancel CTAs."
    - "After clicking Proceed to Confirm, admin lands on /admin/backup-confirm view rendering yellow warning callout, recap line, acknowledgement checkbox bound to BackupImportConfirmForm, red Execute Import button (last tab stop)."
    - "If the @AssertTrue acknowledged validation fails, the confirm page re-renders with .field-error message inline next to the checkbox (no Flash, no redirect)."
    - "Flash messages from Plans 05/06/08 (multipart-too-large, schema-mismatch, hardening-reject, stub-success) render via existing layout.html alert region in their locked D-02 verbatim form."
    - "Zero new CSS classes are added; every class referenced is grep-verifiable in admin.css."
  artifacts:
    - path: "src/main/resources/templates/admin/backup.html"
      provides: "Backup landing page extended with a second .card containing the Import Backup form (D-23)"
      contains: "Import Backup"
    - path: "src/main/resources/templates/admin/backup-preview.html"
      provides: "Preview page — header block + schema-match banner + 24-card grid + Proceed/Cancel CTAs (D-03/D-04/D-05/D-06/D-07)"
      contains: "Backup Import — Preview"
    - path: "src/main/resources/templates/admin/backup-confirm.html"
      provides: "Confirm page — warning callout + recap + @AssertTrue checkbox + Execute Import .btn-danger (D-10)"
      contains: "Execute Import"
  key_links:
    - from: "templates/admin/backup.html (import form)"
      to: "POST /admin/backup/import-preview (Plan 05 endpoint)"
      via: "Thymeleaf @{/admin/backup/import-preview} with method=post enctype=multipart/form-data"
      pattern: "import-preview"
    - from: "templates/admin/backup-preview.html (Proceed form)"
      to: "POST /admin/backup/import-confirm (Plan 06 endpoint)"
      via: "Hidden input stagingId + @{/admin/backup/import-confirm}"
      pattern: "import-confirm"
    - from: "templates/admin/backup-confirm.html (Execute form)"
      to: "POST /admin/backup/import-execute (Plan 08 stub endpoint)"
      via: "th:object='${confirmForm}' BackupImportConfirmForm + @{/admin/backup/import-execute}"
      pattern: "import-execute"
    - from: "templates/admin/backup-preview.html (Cancel form)"
      to: "POST /admin/backup/import-cancel (Plan 08 endpoint)"
      via: "Hidden input stagingId in <form method='post'> (CSRF-protected per discipline trade-off)"
      pattern: "import-cancel"
    - from: "preview header + cards"
      to: "BackupImportPreview record (Plan 03 DTO)"
      via: "${preview.originalFilename}, ${preview.fileSizeBytes}, ${preview.uploadFileCount}, ${preview.totalImportedRows}, ${preview.schemaVersion}, ${preview.entityCounts} loop"
      pattern: "preview\\.(originalFilename|fileSizeBytes|uploadFileCount|totalImportedRows|schemaVersion|entityCounts|stagingId)"
---

<objective>
Author the three Thymeleaf templates that complete the Phase 74 Backup Import UI surface: extend the existing `admin/backup.html` Phase 73 landing with a second `.card` Import form (D-23), and create the two new pages `admin/backup-preview.html` (D-03/D-04/D-05/D-06/D-07) and `admin/backup-confirm.html` (D-10) per the UI-SPEC.

Purpose: Deliver the user-facing surface for the preview-confirm-execute flow. The DTOs (Plan 03), services (Plan 04), and endpoints (Plans 05/06/08) already exist by Wave 3 — this plan is purely the SSR view layer. Locked D-02 English copy is rendered verbatim; D-01 (English-only UI) is enforced; CLAUDE.md `feedback_no_inline_styles.md` is enforced (zero `style="..."` on `.btn`); CLAUDE.md `feedback_ui_language.md` is enforced; CLAUDE.md `feedback_playwright_cli.md` is enforced (final task is visual verification with playwright-cli, Desktop + Mobile, screenshots into `.screenshots/`).

Output: Three Thymeleaf templates that bind to the `BackupImportPreview` record (Plan 03) and `BackupImportConfirmForm` (Plan 03) via `${preview}` and `${confirmForm}` model attributes set by `BackupController` endpoints (Plans 05/06/08). Zero new CSS classes (every class referenced is grep-verified in `admin.css`). Zero modifications to `admin/layout.html` (the existing `errorMessage` / `successMessage` Flash region at lines 82-83 already handles the four reject paths and the stub-success path).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md

@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md

@src/main/resources/templates/admin/backup.html
@src/main/resources/templates/admin/layout.html
@src/main/resources/templates/admin/season-form.html
@src/main/resources/static/admin/css/admin.css

<interfaces>
<!-- Plan 03 DTOs (verified in CONTEXT D-21). Executor binds Thymeleaf expressions to these record components — no codebase exploration needed. -->

From `src/main/java/org/ctc/backup/dto/BackupImportPreview.java` (Plan 03 — record):
```
public record BackupImportPreview(
    UUID stagingId,
    String originalFilename,
    long fileSizeBytes,
    int schemaVersion,
    int currentSchemaVersion,
    boolean schemaMatches,
    List<EntityRowCount> entityCounts,
    int uploadFileCount,
    long totalImportedRows
) {}
```

From `src/main/java/org/ctc/backup/dto/EntityRowCount.java` (Plan 03 — record):
```
public record EntityRowCount(
    String tableName,
    String humanLabel,
    long currentRows,
    long importedRows
) {}
```

From `src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java` (Plan 03 — Lombok form DTO):
```
@Getter @Setter @NoArgsConstructor
public class BackupImportConfirmForm {
    @NotNull
    private UUID stagingId;
    @NotNull
    @AssertTrue(message = "You must acknowledge the deletion warning to continue.")
    private Boolean acknowledged;
}
```

Controller model-attribute names (per Plans 05/06/08):
- `${preview}` — `BackupImportPreview` instance, set in Plans 05 and 06.
- `${confirmForm}` — `BackupImportConfirmForm` instance, set in Plans 06 and 08 (when re-rendering on validation error).
- `${title}` — string, set to "Backup" so `layout.html` line 76 sidebar-active rule `${title.contains('Backup') ? 'active' : ''}` matches.
- `${entityCount}` — int, set in Plan 06 (size of `preview.entityCounts()`) — used only on the confirm-page recap.
- `${sizeInMb}` — pre-formatted string (e.g. `"12.4"`) set in Plans 05/06 to avoid SpEL division in the template (CLAUDE.md "Keep Thymeleaf Templates Lean").
</interfaces>

<verified_css>
<!-- Every class referenced below has been grep-verified to exist in src/main/resources/static/admin/css/admin.css. -->
<!-- Executor MUST NOT add new CSS. If a need surfaces, STOP and escalate. -->

- `.container` — admin.css:143
- `.alert` — admin.css:153
- `.alert-success` — admin.css:159
- `.alert-error` — admin.css:160
- `.alert-warning` — admin.css:161
- `.card` — admin.css:165
- `.actions` — admin.css:209
- `.btn` — admin.css:211
- `.btn-primary` — admin.css:234
- `.btn-secondary` — admin.css:236
- `.btn-danger` — admin.css:238
- `.btn-lg` — admin.css:245
- `.form-group` — admin.css:258
- `.form-group .field-error` — admin.css:318 (NB: field-error MUST sit inside `.form-group` for the styling to apply)
- `.form-check` — admin.css:335 (flex row, gap: 8px, align-items: center — input-first, label-second per established pattern in `season-form.html:17-20`)
- `.badge` — admin.css:346
- `.badge-inactive` — admin.css:356
- `.text-dim` — admin.css:388
- `.mb-md` — admin.css:1347
- `.mt-md` — admin.css:1078 (note: re-declared at 1931 — harmless)
- `.mt-xs` — admin.css:1737
- `.card-grid` — admin.css:1692 (auto-fill, minmax(220px, 1fr), gap 16px)
- `.card--compact` — admin.css:1699

NOT used by this plan (do NOT add): `.badge-active`, `.badge-warning`, `.btn-xs`, `.btn-sm`, `.btn-success`. The delta pill uses `.badge.alert-success` / `.badge.alert-error` / `.badge.badge-inactive` (UI-SPEC §"Delta-pill colour contract"); `.badge-active`/`.badge-warning` are explicitly NOT the right semantic.
</verified_css>

<locked_copy>
<!-- D-02 strings — verbatim, character-for-character. NEVER paraphrase, translate, or reword. -->
<!-- Flash strings (1, 2, 3, 5) are SET by other plans (05, 06, 08, GlobalExceptionHandler), and rendered by `admin/layout.html` lines 82-83. -->
<!-- String 4 (checkbox label) is rendered in `admin/backup-confirm.html` by THIS plan. -->

1. Flash: `Upload too large — maximum is 100 MB.`
2. Flash: `Schema version mismatch: backup={actual}, expected={current}. Cannot import.`
3. Flash: `Backup archive failed safety checks (size or path) and was rejected.`
4. Checkbox label (THIS plan renders): `I am an admin and I understand all operational data will be deleted.`
5. Flash: `Validation succeeded. Import execution will be enabled in Phase 75.`

UI-SPEC additional locked strings (D-01 English-only authority):
- Import card heading: `Import Backup`
- Import card body: `Restores the league database from a previously exported ZIP archive. The import is a two-step process: first you will see a preview of what will change, then you confirm. The upload is limited to 100 MB.`
- File input label: `Backup ZIP file`
- Import CTA: `Import Backup`
- Preview h1: `Backup Import — Preview`
- Preview header labels: `File:`, `Size:`, `Uploads:`, `Total imported rows:`
- Preview pill: `Schema version {N} matches.`
- Preview count separator: ` → ` (Unicode RIGHTWARDS ARROW `U+2192`, NOT ASCII `->`)
- Preview CTAs: `Cancel`, `Proceed to Confirm`
- Confirm h1: `Backup Import — Confirm`
- Confirm warning: `This will delete ALL operational data and replace it with the contents of the uploaded backup. This action cannot be undone.`
- Confirm recap: `You are about to replace {totalImportedRows} rows across {entityCount} tables and restore {uploadFileCount} uploaded files from {originalFilename} ({sizeInMB} MB).`
- Confirm CTAs: `Cancel`, `Execute Import`
- Confirm JS dialog: `Replace all operational data? This cannot be undone.`
- Confirm field-error: `You must acknowledge the deletion warning to continue.` (matches `@AssertTrue.message` in `BackupImportConfirmForm`, so `th:errors="*{acknowledged}"` renders this string)
</locked_copy>

</context>

<tasks>

<task type="auto">
  <name>Task 1: Extend admin/backup.html — append Import Backup .card below the existing Export Backup .card (D-23)</name>
  <files>src/main/resources/templates/admin/backup.html</files>
  <read_first>
    @src/main/resources/templates/admin/backup.html
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md (§"admin/backup.html (existing — Phase 74 extends)")
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-23)
  </read_first>
  <acceptance_criteria>
    - Lines 1-19 of `admin/backup.html` are byte-identical to Phase 73 (the existing Export Backup `.card` is UNTOUCHED — no copy edits, no class changes, no whitespace tweaks).
    - A new second `<div class="card">` block is inserted AFTER the existing closing `</div>` of the export card and BEFORE `</section>`.
    - The new `.card` contains EXACTLY: `<h2>Import Backup</h2>` heading; `<p class="text-dim mb-md">` body containing the locked copy `Restores the league database from a previously exported ZIP archive. The import is a two-step process: first you will see a preview of what will change, then you confirm. The upload is limited to 100 MB.`; one `<form th:action="@{/admin/backup/import-preview}" method="post" enctype="multipart/form-data">`; inside it a `<div class="form-group">` with `<label for="backupZip">Backup ZIP file</label>` paired with `<input type="file" id="backupZip" name="file" accept=".zip" required aria-describedby="backupZipHelp">`; submit `<button type="submit" class="btn btn-primary btn-lg">Import Backup</button>`.
    - The `<p class="text-dim mb-md">` carries `id="backupZipHelp"` so the file input's `aria-describedby` resolves (UI-SPEC §Accessibility Contract).
    - The form `name` attribute is exactly `file` (matches `BackupController.importPreview` `@RequestParam("file") MultipartFile file` in Plan 05).
    - ZERO inline `style="..."` attributes anywhere in the file (CLAUDE.md `feedback_no_inline_styles.md`).
    - NO new CSS classes referenced — every class is in the `<verified_css>` allow-list above.
    - The file remains a valid Thymeleaf template (well-formed XML/HTML5; `xmlns:th` declared on `<html>` like the existing file).
  </acceptance_criteria>
  <action>
    Edit `src/main/resources/templates/admin/backup.html` and add the second `.card` block per the UI-SPEC skeleton (§"admin/backup.html (existing — Phase 74 extends)"). The existing Export Backup `.card` (lines 7-18 in the current file) MUST remain byte-identical — touch ONLY the region between its closing `</div>` and the `</section>` close tag. Use the locked English copy listed in `<locked_copy>` verbatim; do NOT paraphrase. The file input MUST carry `accept=".zip"`, `required`, `name="file"`, and `aria-describedby="backupZipHelp"`; the body-copy `<p>` MUST carry `id="backupZipHelp"`. No `style="..."` on any element; all visual decisions go through CSS classes from `<verified_css>`. Do NOT modify `admin/layout.html` (Flash region already exists at lines 82-83). The submit button is `.btn .btn-primary .btn-lg` (mirrors the Export button — both landing CTAs are visually peers).
  </action>
  <verify>
    <automated>./mvnw test -Dtest=BackupControllerIT -DfailIfNoTests=false -q 2>&amp;1 | tail -20 &amp;&amp; grep -F 'Import Backup' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'Restores the league database' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'th:action="@{/admin/backup/import-preview}"' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'enctype="multipart/form-data"' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'accept=".zip"' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'id="backupZipHelp"' src/main/resources/templates/admin/backup.html &amp;&amp; ! grep -E 'style="[^"]+"' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'Export Backup' src/main/resources/templates/admin/backup.html &amp;&amp; grep -F 'all 24 entities' src/main/resources/templates/admin/backup.html</automated>
  </verify>
  <done>
    `admin/backup.html` renders two stacked `.card` blocks (Export above, Import below); the Phase 73 Export card content is byte-identical (verified by grep for `Export Backup` and `all 24 entities`); the new Import card has the locked copy, the multipart form pointing at `/admin/backup/import-preview`, the file input with `accept=".zip"`, `required`, `name="file"`, and `aria-describedby` pairing; zero inline styles; only allow-list CSS classes referenced.
  </done>
</task>

<task type="auto">
  <name>Task 2: Create admin/backup-preview.html — header block + schema banner + 24-card grid + Proceed/Cancel CTAs (D-03/D-04/D-05/D-06/D-07)</name>
  <files>src/main/resources/templates/admin/backup-preview.html</files>
  <read_first>
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md (§"admin/backup-preview.html (NEW — Phase 74)" + §"Delta-pill colour contract" + §"Accessibility Contract")
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-03, D-04, D-05, D-06, D-07, D-21)
    @src/main/resources/templates/admin/backup.html (the layout-fragment idiom)
    @src/main/resources/templates/admin/season-form.html (form-check pattern — input-first, label-second)
  </read_first>
  <acceptance_criteria>
    - File is a new Thymeleaf template using `th:replace="~{admin/layout :: layout('Backup', ~{::section})}"` (so sidebar Backup entry is active per `layout.html:76` `${title.contains('Backup')}` rule).
    - Single `<section>` child contains, top-to-bottom:
      1. `<h1>Backup Import — Preview</h1>`
      2. `<div class="card mb-md">` with four `<p>` rows: `File:`, `Size:`, `Uploads:`, `Total imported rows:` bound to `${preview.originalFilename}`, `${sizeInMb}` (model attribute pre-formatted by Plan 05), `${preview.uploadFileCount}`, `${preview.totalImportedRows}`. Each label is in `<strong>...</strong>`.
      3. `<div class="alert alert-success mb-md" role="status">` with copy `Schema version <span th:text="${preview.schemaVersion}">1</span> matches.` (D-04 full-width banner). `role="status"` because mismatches never render this page (CONTEXT D-09) so the success state is always informational.
      4. `<div class="card-grid mb-md">` wrapping a `th:each="card : ${preview.entityCounts}"` loop emitting one `<div class="card card--compact">` per entity: `<h3 th:text="${card.humanLabel}">...</h3>`, a `<p>` line `<strong th:text="${card.currentRows}">N</strong> → <strong th:text="${card.importedRows}">M</strong>` (NB: Unicode `→` U+2192, NOT ASCII `->`), and a delta pill: `<span class="badge mt-xs" th:classappend="${card.importedRows lt card.currentRows ? ' alert-error' : (card.importedRows gt card.currentRows ? ' alert-success' : ' badge-inactive')}" th:text="${card.importedRows - card.currentRows}" th:aria-label="${(card.importedRows - card.currentRows) + ' rows ' + (card.importedRows lt card.currentRows ? '(data loss)' : (card.importedRows gt card.currentRows ? '(gain)' : '(unchanged)'))}">Δ</span>`. (UI-SPEC §"Required ARIA attributes" — screen readers cannot infer state from colour.)
      5. CTA row — TWO sibling `<form>` blocks (NOT nested forms — invalid HTML):
         - Primary form: `<form th:action="@{/admin/backup/import-confirm}" method="post"><input type="hidden" name="stagingId" th:value="${preview.stagingId}"><button type="submit" class="btn btn-primary">Proceed to Confirm</button></form>`
         - Secondary form: `<form th:action="@{/admin/backup/import-cancel}" method="post"><input type="hidden" name="stagingId" th:value="${preview.stagingId}"><button type="submit" class="btn btn-secondary">Cancel</button></form>`
         Wrapped inside a `<div class="actions">` so the 8px gap (admin.css:209) renders correctly.
    - The two CTA `<form>` blocks are siblings inside `.actions` (no `<form>` nesting; HTML5 forbids it).
    - Per `## Notes` trade-off: Cancel is a POST form (NOT a `<a th:href="...">` GET link as the UI-SPEC skeleton originally drafted) because Plan 08 defines `POST /admin/backup/import-cancel` and CSRF discipline forbids a `@GetMapping` that mutates filesystem state. UI-SPEC accessibility concern is preserved: a `<button class="btn btn-secondary">` inside a form is fully keyboard-accessible.
    - ZERO inline `style="..."` attributes.
    - NO new CSS classes referenced.
    - The Thymeleaf `gt` / `lt` SpEL operators are used (not `>` or `<` which are reserved in XML/HTML) per project convention.
  </acceptance_criteria>
  <action>
    Create `src/main/resources/templates/admin/backup-preview.html` following the structure in `acceptance_criteria` and the UI-SPEC §"admin/backup-preview.html" skeleton. Use the same layout-fragment idiom as `admin/backup.html` (`th:replace="~{admin/layout :: layout('Backup', ~{::section})}"`). The `${preview}` model attribute is a `BackupImportPreview` record (Plan 03), so use record-component accessor names in SpEL (`preview.originalFilename`, `preview.fileSizeBytes`, etc.). The pre-formatted `${sizeInMb}` is set by Plan 05's controller (do NOT do `${preview.fileSizeBytes / 1048576.0}` in the template — CLAUDE.md "Keep Thymeleaf Templates Lean" forbids template arithmetic of this kind). Use `th:classappend` to chain `badge` with one of `alert-error` / `alert-success` / `badge-inactive` based on the delta direction (UI-SPEC §"Delta-pill colour contract"). Add `th:aria-label` to each delta pill so screen readers announce state ("rows (data loss)" / "rows (gain)" / "rows (unchanged)"). Two sibling forms inside one `.actions` flex container (NEVER nest forms). Cancel is a POST form (see `## Notes`). No `style="..."` anywhere. Use `→` (U+2192) — type the literal Unicode character, not `&rarr;` (the UI-SPEC §Copywriting Contract is explicit on this). Use Thymeleaf `gt`/`lt` operators (not `&gt;`/`&lt;` or raw `>`/`<`) per project convention.
  </action>
  <verify>
    <automated>test -f src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'th:replace="~{admin/layout :: layout(' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'Backup Import — Preview' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'Schema version' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'matches.' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'class="card-grid mb-md"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'class="card card--compact"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'th:each="card : ${preview.entityCounts}"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'th:action="@{/admin/backup/import-confirm}"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'th:action="@{/admin/backup/import-cancel}"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'Proceed to Confirm' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F 'th:aria-label' src/main/resources/templates/admin/backup-preview.html &amp;&amp; grep -F '→' src/main/resources/templates/admin/backup-preview.html &amp;&amp; ! grep -E 'style="[^"]+"' src/main/resources/templates/admin/backup-preview.html &amp;&amp; ./mvnw test -Dtest=BackupControllerIT -DfailIfNoTests=false -q 2>&amp;1 | tail -20</automated>
  </verify>
  <done>
    `admin/backup-preview.html` exists; renders within the admin layout fragment with title `Backup` (sidebar active state); shows the four-row header card, the full-width green schema-match alert, the 24-card grid (loop over `${preview.entityCounts}`), and two sibling POST forms inside `.actions` for Proceed and Cancel; delta pills carry `th:aria-label` and chain `badge` with one of `alert-error`/`alert-success`/`badge-inactive`; Unicode `→` is the count separator; zero inline styles; only allow-list CSS classes referenced.
  </done>
</task>

<task type="auto">
  <name>Task 3: Create admin/backup-confirm.html — yellow warning + recap + @AssertTrue checkbox + Execute Import .btn-danger (D-10)</name>
  <files>src/main/resources/templates/admin/backup-confirm.html</files>
  <read_first>
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-UI-SPEC.md (§"admin/backup-confirm.html (NEW — Phase 74)" + §"Destructive actions" + §"Accessibility Contract")
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-10)
    @src/main/resources/templates/admin/season-form.html (form-check + field-error pattern)
    @src/main/resources/templates/admin/backup.html
  </read_first>
  <acceptance_criteria>
    - File is a new Thymeleaf template using `th:replace="~{admin/layout :: layout('Backup', ~{::section})}"`.
    - Single `<section>` child contains, top-to-bottom:
      1. `<h1>Backup Import — Confirm</h1>`
      2. `<div class="alert alert-warning mb-md" role="alert">` with the locked warning copy `This will delete ALL operational data and replace it with the contents of the uploaded backup. This action cannot be undone.` (full text, no truncation).
      3. `<div class="card mb-md" id="confirmRecap">` with one `<p>` containing the locked recap: `You are about to replace <strong th:text="${preview.totalImportedRows}">N</strong> rows across <strong th:text="${entityCount}">X</strong> tables and restore <strong th:text="${preview.uploadFileCount}">U</strong> uploaded files from <strong th:text="${preview.originalFilename}">file.zip</strong> (<span th:text="${sizeInMb}">12.4</span> MB).` (the `id="confirmRecap"` pairs with `aria-describedby` on the Execute button — UI-SPEC §"Required ARIA attributes").
      4. `<form th:action="@{/admin/backup/import-execute}" method="post" th:object="${confirmForm}" onsubmit="return confirm('Replace all operational data? This cannot be undone.');">` — single form bound to `${confirmForm}` (`BackupImportConfirmForm`).
         - First child: `<input type="hidden" th:field="*{stagingId}">` — Thymeleaf renders both `name=stagingId` and `value=...` from the form object.
         - Second child: `<div class="form-group">` containing a `<div class="form-check">` with `<input type="checkbox" id="acknowledged" th:field="*{acknowledged}" value="true">` and `<label for="acknowledged">I am an admin and I understand all operational data will be deleted.</label>` (input-first, label-second per the `season-form.html` analog). Below the `.form-check`, an inline error: `<span class="field-error" role="alert" th:if="${#fields.hasErrors('acknowledged')}" th:errors="*{acknowledged}">error</span>`. The `<span>` MUST be inside the same `.form-group` so the `.form-group .field-error` selector at `admin.css:318` applies.
         - Third child: CTA row `<div class="actions">` containing TWO sibling forms or one button + one form? **CONSTRAINT:** HTML5 forbids nested forms. Resolution: the Execute button is the submit of THIS outer form; the Cancel must be a SECOND sibling form OUTSIDE the outer form. So restructure as: the outer `<form>` is for execute; place a separate `<form th:action="@{/admin/backup/import-cancel}" method="post"><input type="hidden" name="stagingId" th:value="${confirmForm.stagingId}"><button type="submit" class="btn btn-secondary">Cancel</button></form>` AFTER the execute form, both wrapped in a single `<div class="actions">` only if both are at the same DOM level. Final structure: place both forms as siblings inside `<div class="actions">` — outer is the execute form (with checkbox), inner Cancel is a separate form. **Re-read carefully: a `<form>` cannot contain another `<form>`.** Therefore: the `.actions` row contains the Execute button (submit of the outer execute form, so the button is INSIDE that outer form's DOM) + the standalone Cancel form as a sibling. To make this work cleanly, put the `<div class="actions">` AFTER the execute form's closing `</form>` and put the Execute button as a `<button type="submit" form="executeForm">` referencing the parent form by `id="executeForm"` — OR more simply: keep Execute button INSIDE its parent form (no `.actions` wrap there), and render the Cancel form below it inside its own minimal wrapper. **Adopted resolution:** the Execute button lives inside `<form id="executeForm">` followed immediately by a sibling `<form th:action="@{/admin/backup/import-cancel}">` containing the Cancel button. Both forms are wrapped by a single `<div class="actions">` parent (since `<div>` can contain multiple `<form>` siblings — this is valid HTML). The 8px gap from `.actions` (flex container, admin.css:209) sits both buttons inline.
      - The Execute button is: `<button type="submit" class="btn btn-danger" aria-describedby="confirmRecap">Execute Import</button>` — last tab stop in the page (UI-SPEC §Keyboard nav order). Inside the outer execute `<form>`.
      - The Cancel button is: `<button type="submit" class="btn btn-secondary">Cancel</button>` — inside the separate cancel `<form>`.
    - ZERO inline `style="..."` attributes.
    - NO new CSS classes referenced.
    - The Execute Import button MUST NOT have `.btn-xs` or any size-modifier that reduces font below 14px (UI-SPEC §Color contrast — destructive button is at AA threshold).
    - The JS `confirm()` dialog string is exactly `Replace all operational data? This cannot be undone.` — UX-extra per D-10; server-side `@AssertTrue` is the authoritative gate.
  </acceptance_criteria>
  <action>
    Create `src/main/resources/templates/admin/backup-confirm.html` following the structure in `acceptance_criteria` and the UI-SPEC §"admin/backup-confirm.html" skeleton. Use the layout-fragment idiom. Bind to `${confirmForm}` (a `BackupImportConfirmForm`). The checkbox uses `th:field="*{acknowledged}"` which auto-emits `id`, `name`, and `value=true` — combined with explicit `value="true"` to mirror the `season-form.html:18` idiom for unambiguous boolean POST. The error span uses `th:errors="*{acknowledged}"` so the JSR-303 `@AssertTrue(message=...)` message in `BackupImportConfirmForm` becomes the rendered text (matches UI-SPEC string `You must acknowledge the deletion warning to continue.`). The `<span class="field-error">` MUST be inside the parent `.form-group` for the `admin.css:318` `.form-group .field-error` selector to apply (`field-error` is NOT a top-level style). Use the `id="confirmRecap"` + `aria-describedby="confirmRecap"` pairing so screen readers read the consequence before announcing "Execute Import". Resolve the nested-form constraint per `acceptance_criteria` (one execute form, one cancel form, both as siblings inside one `<div class="actions">` parent). NO `style="..."` anywhere. Add `role="alert"` on the warning callout AND on the `.field-error` span (UI-SPEC §"Required ARIA attributes" — recommends `role="alert"` on field-error so screen readers announce the validation error on re-render). Use the `onsubmit="return confirm(...)"` form-level handler (NOT `onclick` on the button — the form-level handler runs on Enter-key submission too).
  </action>
  <verify>
    <automated>test -f src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:replace="~{admin/layout :: layout(' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'Backup Import — Confirm' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'This will delete ALL operational data' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'I am an admin and I understand all operational data will be deleted.' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:object="${confirmForm}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:field="*{stagingId}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:field="*{acknowledged}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:errors="*{acknowledged}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:action="@{/admin/backup/import-execute}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'th:action="@{/admin/backup/import-cancel}"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'class="btn btn-danger"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'aria-describedby="confirmRecap"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'onsubmit="return confirm' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; grep -F 'Replace all operational data? This cannot be undone.' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; ! grep -E 'style="[^"]+"' src/main/resources/templates/admin/backup-confirm.html &amp;&amp; ./mvnw test -Dtest=BackupControllerIT -DfailIfNoTests=false -q 2>&amp;1 | tail -20</automated>
  </verify>
  <done>
    `admin/backup-confirm.html` exists; renders within the admin layout fragment with title `Backup`; shows the yellow warning callout, the recap card with `id="confirmRecap"`, an execute form bound to `${confirmForm}` with hidden `stagingId`, the `.form-check` checkbox with locked label and `.field-error` span, the `.btn-danger` Execute Import button with `aria-describedby="confirmRecap"` and form-level `onsubmit` JS confirm; a sibling cancel form with `.btn-secondary` Cancel button; both buttons inside a single `<div class="actions">` parent; zero inline styles; only allow-list CSS classes referenced.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 4: Visual verification with playwright-cli — Desktop + Mobile, all three pages, screenshots into .screenshots/</name>
  <what-built>
    Three Thymeleaf templates: `admin/backup.html` (extended with Import card), `admin/backup-preview.html` (new), `admin/backup-confirm.html` (new). All bind to DTOs from Plan 03 via model attributes set by controllers in Plans 05/06/08. Zero new CSS — all classes are pre-existing in `admin.css`.
  </what-built>
  <how-to-verify>
    1. **Start the dev server** (separate terminal — keep running for the duration of verification):
       `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`
       Wait for `Started CtcManagerApplication`. Server listens on `http://localhost:9090`.

    2. **Inspect the backup landing page (Desktop 1280×800):**
       `playwright-cli open http://localhost:9090/admin/backup --viewport 1280x800 --screenshot .screenshots/74-09-backup-landing-desktop.png`
       Expected: two stacked `.card` blocks. Top card (Export) shows the Phase 73 body copy and the blue `Export Backup` button — must look identical to before Phase 74. Below it, a second card with `<h2>Import Backup</h2>`, the locked body copy `Restores the league database…`, a file input labelled `Backup ZIP file`, and a blue `Import Backup` button.

    3. **Inspect the backup landing page (Mobile 375×667):**
       `playwright-cli open http://localhost:9090/admin/backup --viewport 375x667 --screenshot .screenshots/74-09-backup-landing-mobile.png`
       Expected: both cards stack vertically full-width; no horizontal scroll; sidebar collapses (toggle button visible).

    4. **Upload a test ZIP to reach the preview page** (manual via the running browser opened by playwright-cli, since playwright-cli `open` does not script file inputs):
       a. In the playwright-cli browser, click `Choose File` and pick `target/backup-fixtures/demo-export.zip` if it exists, OR generate one via `curl -X POST http://localhost:9090/admin/backup/export -o /tmp/demo-export.zip` first.
       b. Click `Import Backup`.
       c. URL now `/admin/backup/import-preview` (POST landed on the preview view).

    5. **Inspect the preview page (Desktop):**
       `playwright-cli open http://localhost:9090/admin/backup/import-preview --viewport 1280x800 --screenshot .screenshots/74-09-backup-preview-desktop.png`
       (Note: cannot navigate directly via GET — the preview page is rendered only after POST. Capture screenshot during the manual flow from step 4.)
       Expected: `<h1>Backup Import — Preview</h1>`; header card showing File / Size / Uploads / Total imported rows in four rows; full-width green banner `Schema version 1 matches.`; 24-card grid below in ~5 columns at desktop width; each card has a kebab-case-converted human label, `current → imported` count line with the Unicode `→`, and a delta pill (color depends on data). Buttons row at bottom: secondary `Cancel`, primary `Proceed to Confirm`.

    6. **Inspect the preview page (Mobile):**
       `playwright-cli open http://localhost:9090/admin/backup/import-preview --viewport 375x667 --screenshot .screenshots/74-09-backup-preview-mobile.png`
       Expected: 24 cards in a single column; banner full-width; buttons stack OR sit inline depending on width (the `.actions` flex container allows wrapping).

    7. **Inspect the confirm page (Desktop):**
       Click `Proceed to Confirm` to land on `/admin/backup/import-confirm`.
       `playwright-cli screenshot .screenshots/74-09-backup-confirm-desktop.png --viewport 1280x800`
       Expected: yellow `.alert-warning` callout at top with locked copy `This will delete ALL operational data…`; recap card below with the rendered numbers and filename; `.form-check` row with checkbox + label `I am an admin and I understand all operational data will be deleted.`; buttons row with `Cancel` (secondary) and red `.btn-danger` `Execute Import`.

    8. **Inspect the confirm page (Mobile):**
       `playwright-cli screenshot .screenshots/74-09-backup-confirm-mobile.png --viewport 375x667`
       Expected: all elements stack vertically; the destructive red button remains clearly distinguishable from the secondary Cancel button.

    9. **Validation error flow (Desktop):**
       Without checking the acknowledgement checkbox, click `Execute Import` → JS `confirm()` dialog appears with text `Replace all operational data? This cannot be undone.` → click OK → server-side `@AssertTrue` fails → page re-renders with `.field-error` text `You must acknowledge the deletion warning to continue.` visible inline below the checkbox in red.
       `playwright-cli screenshot .screenshots/74-09-backup-confirm-validation-error.png --viewport 1280x800`

    10. **Tab-order check (keyboard accessibility):** On the confirm page, press `Tab` repeatedly. Confirm the order is: sidebar links → acknowledgement checkbox → Cancel button → Execute Import button (last). Confirm focus rings (sky-blue 2px outline) are visible on each focusable element.
  </how-to-verify>
  <resume-signal>Type "approved" once all 6+2 screenshots match the UI-SPEC visual intent, the validation-error flow works, the tab order is correct, and the dev server log shows no template-resolution errors. Describe any deviation.</resume-signal>
</task>

</tasks>

<verification>
**Template-coverage gates (executor MUST pass before signalling done):**

1. **D-02 locked strings present, verbatim:**
   - `grep -F 'I am an admin and I understand all operational data will be deleted.' src/main/resources/templates/admin/backup-confirm.html` → exit 0
   - The four Flash strings (D-02 #1, #2, #3, #5) are NOT rendered by this plan — they are SET by Plans 05/06/08 and the `BackupUploadExceptionHandler`. This plan's responsibility is only that `admin/layout.html` (lines 82-83 — `<div th:if="${errorMessage}" class="alert alert-error" th:text="${errorMessage}">`) is the rendering surface — VERIFIED unchanged.

2. **Zero new CSS classes:**
   - `git diff src/main/resources/static/admin/css/admin.css` → empty diff (this plan does NOT touch admin.css)
   - Every class in the three new/extended templates appears in the `<verified_css>` allow-list above. Grep each unique class name from the three template files against `admin.css` to confirm:
     ```bash
     for cls in card card-grid 'card--compact' alert alert-success alert-error alert-warning badge badge-inactive btn btn-primary btn-secondary btn-danger btn-lg form-group 'form-group .field-error' form-check text-dim mb-md mt-xs mt-md actions; do
       grep -q "\\.${cls}[[:space:]{,]" src/main/resources/static/admin/css/admin.css || echo "MISSING: $cls"
     done
     ```
     Output must be empty.

3. **Zero inline styles on .btn or anywhere:**
   - `! grep -E 'style="[^"]+"' src/main/resources/templates/admin/backup.html src/main/resources/templates/admin/backup-preview.html src/main/resources/templates/admin/backup-confirm.html` → exit 0

4. **English-only UI (CLAUDE.md feedback_ui_language.md):**
   - `! grep -E '(Sicherung|Hochladen|Bestätigen|Verstehe|Schema-Version|Backup-Archiv)' src/main/resources/templates/admin/backup.html src/main/resources/templates/admin/backup-preview.html src/main/resources/templates/admin/backup-confirm.html` → exit 0 (no German leaks)

5. **HTML well-formedness:**
   - The Thymeleaf engine itself will fail to render at runtime if XML is malformed. Task 4's playwright-cli verification (dev server flow) is the proof-of-render.
   - Additionally: `./mvnw test -Dtest=BackupControllerIT -DfailIfNoTests=false -q` passes (existing Phase 73 controller tests must not break).

6. **Sidebar Backup link stays active on all three pages:**
   - `${title.contains('Backup')}` rule in `layout.html:76` triggers on any title containing "Backup" → all three pages pass `title='Backup'` so the rule fires.
   - Playwright check in Task 4: the Backup sidebar entry must show the sky-blue left-border (accent indicator).

7. **No nested `<form>` tags (HTML5 invalid):**
   - `python3 -c "import re,sys; [sys.exit(1) if len(re.findall(r'<form[^>]*>', re.split(r'</form>', open(f).read(), 1)[0])) > 1 else None for f in ['src/main/resources/templates/admin/backup-preview.html', 'src/main/resources/templates/admin/backup-confirm.html']]"` — sentinel check; manual review during Task 4 is authoritative.

8. **Final regression check:**
   - `./mvnw verify` — full test suite passes (no template-resolution errors during context startup; no existing test broken).
</verification>

<success_criteria>
- [ ] `admin/backup.html` rendered at `/admin/backup` shows two stacked `.card` blocks; the Phase 73 Export Backup card is byte-identical to its prior state; the new Import Backup card has the locked English copy, file input with `accept=".zip"`, `required`, and `aria-describedby`, and a `.btn-primary.btn-lg` submit.
- [ ] `admin/backup-preview.html` rendered after a successful upload shows: `<h1>Backup Import — Preview</h1>`, four-row header card, full-width green schema-match banner, 24-card grid with delta pills, Proceed/Cancel CTA forms inside `<div class="actions">`. Delta pills carry `th:aria-label` for screen readers.
- [ ] `admin/backup-confirm.html` rendered after clicking Proceed shows: `<h1>Backup Import — Confirm</h1>`, yellow warning callout, recap card with `id="confirmRecap"`, `.form-check` row with checkbox bound to `*{acknowledged}` and the locked label string, `.field-error` span inside its `.form-group` (so the CSS selector applies), Execute Import `.btn-danger` button with `aria-describedby="confirmRecap"` as the last tab stop, Cancel `.btn-secondary` button in a sibling form.
- [ ] Validation error flow: clicking Execute Import without checking the checkbox triggers the JS `confirm()` dialog (UX-extra), and after dialog-confirm the server-side `@AssertTrue` re-renders the page with the locked `.field-error` text inline. (Endpoint exists in Plan 08; rendering is owned by this plan.)
- [ ] Zero new CSS classes introduced; zero inline `style="..."` attributes; English-only UI (no German strings); `./mvnw verify` passes.
- [ ] playwright-cli screenshots captured for Desktop + Mobile for all three pages (6 baseline + 1 validation-error + tab-order verbal sign-off) and stored under `.screenshots/74-09-*.png`.
</success_criteria>

<output>
After completion, create `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-09-SUMMARY.md` describing:

- Which Plan 03 DTOs are bound by which templates (`${preview}` → `BackupImportPreview`, `${confirmForm}` → `BackupImportConfirmForm`).
- The Cancel POST-form trade-off (per `## Notes` below).
- The nested-form HTML5 constraint resolution (sibling forms inside `<div class="actions">`).
- The `<small>` → `<span>` field-error tag choice (so it sits cleanly inside `.form-group` next to the `.form-check`).
- Any deviation from the UI-SPEC skeleton, with the rationale.
- Test impact: which Phase-74 ITs (Plans 11/12) and the E2E test (Plan 12) depend on the exact CSS-class names and form actions used here.
- Screenshot paths under `.screenshots/74-09-*.png`.
</output>

## Notes

**Cancel as POST trade-off (resolved against UI-SPEC skeleton):**
- UI-SPEC §"Layout decisions" for `backup-preview.html` initially favours `<a class="btn btn-secondary">Cancel</a>` (GET link) to avoid HTML5 nested-form invalidity.
- Plan 08 defines `POST /admin/backup/import-cancel` — making Cancel a GET would require either (a) a sibling `@GetMapping` in Plan 08 (CSRF concern: a mutation triggered by GET is a project-wide anti-pattern and conflicts with `BackupControllerSecurityIT`'s CSRF matrix), or (b) accepting state mutation on GET (rejected — CLAUDE.md "Architectural Principles" and the existing CTC CSRF discipline forbid it).
- Resolution: keep POST as Plan 08 defines, and use a `<form method="post">` with a `<button class="btn btn-secondary">Cancel</button>` as a SIBLING form inside `<div class="actions">`. The two sibling forms are valid HTML5 (only NESTED forms are forbidden). The `.actions` flex container at `admin.css:209` renders both submit buttons inline with 8px gap — visually identical to the link-based skeleton.
- Accessibility preserved: `<button>` inside a `<form>` is fully keyboard-accessible (Tab focuses, Enter submits). No screen-reader regression.

**HTML5 nested-form constraint (applies to both preview and confirm pages):**
- A `<form>` element cannot contain another `<form>` element (HTML5 §4.10.3).
- The Proceed/Cancel pair on `backup-preview.html` is rendered as TWO sibling forms inside one `<div class="actions">` parent.
- The Execute/Cancel pair on `backup-confirm.html` follows the same pattern: an outer `<form>` for the Execute submission (with hidden stagingId + checkbox + `.field-error`), followed by a SIBLING `<form>` for the Cancel submission, both inside one `<div class="actions">` parent. Crucially, the checkbox + `.field-error` live inside the Execute form so they participate in its `@Valid` binding; the Cancel form is purely a single-button POST and does NOT bind to `${confirmForm}`.

**`<small>` vs `<span>` for the field-error:**
- UI-SPEC §"Component Inventory" shows `<small class="field-error">`; CSS rule `.form-group .field-error` (admin.css:318) styles by class, not by tag.
- Either tag works visually; this plan adopts `<span>` (semantic-neutral, matches `season-form.html:14` pattern) over `<small>` (which carries a typographic side-effect — browsers default `<small>` to 80% font-size, which combined with the CSS rule may compound). Choosing `<span>` is the safer default.

**Accessibility: `aria-live` on the Flash region:**
- UI-SPEC §"Screen-reader behaviour on Flash redirects" recommends adding `role="status"` to `.alert-success` and `role="alert"` to `.alert-error` on the layout.
- This plan does NOT modify `admin/layout.html` (its `files_modified` frontmatter lists only the three template files). Adding `role` attributes to the layout's Flash region is deferred to a follow-up cleanup phase (or the executor may surface this as a Discovery item; if shipping inside Phase 74, requires re-running the planner to update `files_modified`).
- Workaround within Phase 74: each NEW template adds `role="status"` to its in-template success banner (the schema-match pill on preview) and `role="alert"` to its in-template warning callout (the confirm-page yellow box) and field-error span. The Flash redirects (Plans 05/06/08) still rely on the existing layout — no regression vs Phase 73.

**Visual verification skill alignment:**
- CLAUDE.md `feedback_playwright_cli.md`: "Bei UI-Aenderungen immer playwright-cli zur visuellen Pruefung nutzen."
- Task 4 enforces this. The dev profile (`dev,demo`) is used so the `BackupExportService` has data to round-trip-import and produce non-zero preview counts.

**Test impact (read by the cross-AI checker):**
- The Playwright E2E test in Plan 12 (`BackupImportE2ETest`) will `page.getByRole(AriaRole.LINK, name="Backup").click()` and then `page.getByRole(AriaRole.BUTTON, name="Import Backup").click()` — the button name is locked at `Import Backup` per `<locked_copy>`. Changing this string breaks Plan 12.
- The integration test in Plan 11 (`BackupControllerIT`-style) will `mockMvc.perform(get("/admin/backup")).andExpect(content().string(Matchers.containsString("Import Backup")))` — the substring assertion locks the heading. Match `<locked_copy>` verbatim.
- The validation IT in Plan 11 (`BackupImportConfirmFormValidationIT`) will assert that submitting `acknowledged=null` re-renders `backup-confirm.html` (not redirect) with the `.field-error` text containing `You must acknowledge` — match the message string in `@AssertTrue` (set in Plan 03 DTO) verbatim.

## PLAN COMPLETE 09

Zusammenfassung (Deutsch):

Der Plan `09-PLAN.md` wurde unter `/Users/jegr/Documents/github/ctc-manager/.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/09-PLAN.md` erstellt.

Wichtige Entscheidungen im Plan:
- **Drei Tasks plus ein Visual-Verification-Checkpoint** (Task 4 — playwright-cli Desktop + Mobile, Screenshots in `.screenshots/`).
- **Cancel als POST-Form** statt `<a>`-Link (CSRF-Disziplin gewinnt gegen UI-SPEC-Empfehlung — dokumentiert im `## Notes`-Block).
- **HTML5 nested-form constraint**: Zwei Geschwister-Forms innerhalb `<div class="actions">` statt verschachtelter Forms (gilt für Preview UND Confirm).
- **Alle CSS-Klassen sind grep-verifiziert** im `<verified_css>`-Block (Zeilen-Referenzen auf `admin.css`); null neue CSS.
- **D-02 Strings verbatim** rendern; D-01 English-only ist durch Verification-Gate 4 (`! grep -E '(Sicherung|Hochladen|...)'`) abgesichert.
- **Field-error im `.form-group`** platziert, damit der `admin.css:318` Selektor `.form-group .field-error` greift.
- **`role="alert"` und `aria-describedby="confirmRecap"`** für Screenreader-A11y; `aria-live` auf Flash-Region ist als Deferral dokumentiert (Layout nicht in files_modified).
- **Test-Impact** für Plan 11/12 ist explizit im `## Notes`-Block dokumentiert, damit der Checker die String-Locks erkennt.
