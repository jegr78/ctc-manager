# Phase 61 — UI Review

**Audited:** 2026-05-02
**Baseline:** Abstract 6-pillar standards (no UI-SPEC.md authored — Phase 61 is a backend cleanup / quality-gate phase; UI surface is intentionally tiny). Project conventions from `CLAUDE.md` enforced (English UI text, no inline styles on `.btn`, lean Thymeleaf templates, OSIV-friendly server-side rendering).
**Screenshots:** Not captured — no dev server detected on ports 9090 / 9091 / 8080. Code-only audit.
**Phase 61 UI footprint (verified via git log):**
- `src/main/resources/templates/admin/season-phase-form.html` — UAT-01 fix at lines 26 / 35 / 43 (commit `f5b10bc`).
- `src/main/resources/templates/admin/seasons.html` — gap-09 stale-comment strip (commit `461bc16`, no rendered-UI delta).
- All other templates audited for **non-regression**: cleanup grep audit (61-02) verified that the V6 schema-drop did not leave Thymeleaf SpEL crashes against dropped Season fields.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Phase Edit Form (Phase Type / Layout / Format) labels now correctly render `Regular Season` / `Playoff` / `Placement` / `League` / `Groups` / `Bracket` / `Swiss` / `Round Robin` (UAT-01 closed). BUT `/admin/playoffs/{id}/add-season` (D-03) now returns a generic `500 — Error / Something went wrong` (`error.html:15`) — no copy explaining the endpoint was retired. |
| 2. Visuals | 2/4 | UAT-01 was a P0 visual regression that shipped: empty `<option>` labels rendered through plan + execute + verify on commit `238d469` (Phase 60) and only a manual UAT smoke-check on 2026-05-02 caught it. The fix landed (commit `f5b10bc`) and a regression test was added, but the codebase has zero visual / golden snapshot coverage that would catch the same class of bug elsewhere. |
| 3. Color | 3/4 | No regression introduced. `season-detail.html:82-84` still hardcodes `#333333` / `#555555` / `#4fc3f7` as default-color fallbacks — pre-existing from Phase 60, but should be moved to admin.css custom properties for theme parity. |
| 4. Typography | 3/4 | No regression introduced; baseline preserved. Layout, h1/h2/h3 hierarchy on the touched templates is clean and inherited from `admin/layout` partial. No hand-rolled font sizes in `season-phase-form.html` or `seasons.html` after the gap-09 sweep. |
| 5. Spacing | 3/4 | `season-phase-form.html` uses canonical `form-row` / `form-group` / `actions mt-md` spacing — admin.css scale honored. 3 inline `th:style="display:inline-block;width:14px;height:14px;..."` survivals on `season-detail.html:56-58` for color swatches; pre-existing from Phase 60, not Phase 61's responsibility but worth raising as a `.swatch-chip` extraction. CLAUDE.md §"No Inline Styles on Buttons" is technically respected (these are `<span>`s, not `.btn`s). |
| 6. Experience Design | 2/4 | Two issues: (a) UAT-01 (template SpEL bug) was a hard task-completion blocker that escaped automated tests — the user could not switch to GROUPS layout at all; classifies as a 1/4 in isolation, lifted to 2/4 because it was caught and fixed in-branch with a regression test. (b) D-03 retired endpoints land on the global error template with no explanation copy that the URL was intentionally retired (just a generic 500). |

**Overall: 15/24**

---

## Top 3 Priority Fixes

1. **BLOCKER (already fixed, but raise the bar) — Add a Thymeleaf rendering smoke layer.** UAT-01 (`season-phase-form.html:26,35,43`) shipped in Phase 60 commit `238d469` and survived plan + execute + verify because no test asserted the rendered `<option>` text content. Phase 61 added one targeted IT (`SeasonPhaseControllerIT#givenExistingPhase_whenGetEditForm_thenDropdownOptionsHaveNonEmptyLabels`) covering exactly the 8 expected labels, which is the right direction but only patches one form. **Concrete fix:** add a contract test that walks every `select th:each` in `templates/admin/*.html` and asserts the rendered options contain non-empty text. ~30 lines of MockMvc-based assertion logic, prevents the entire bug class from recurring on any other dropdown (e.g. SeasonForm's car/track transfer pickers, race-result driver dropdowns).
2. **WARNING — Replace the generic "Something went wrong" copy on D-03 retired endpoints.** Phase 61 D-03 turned `/admin/playoffs/{id}/add-season` and `/remove-season` into 5xx — the global `templates/error.html:15` and `templates/admin/error.html:10` render `"Something went wrong"` as the default `${message}`. Old bookmarks land here with zero context. **Concrete fix:** add a `ResourceNotFoundException`-equivalent or an `@ExceptionHandler` in `PlayoffController` that maps the missing endpoint to a 410 Gone with body `"This endpoint was retired in v1.9. Playoff seasons are now managed via the Phase tabs on the Season detail page."` — UX clarity for the documented Tracked Behavior Change in Phase 61 D-03.
3. **WARNING — Extract three inline `th:style` color-swatch lines to a CSS class.** `season-detail.html:56-58` build a 14×14 px color preview via `display:inline-block;width:14px;height:14px;border-radius:3px;background:` + a dynamic `${st.effectivePrimaryColor}`. The static portion belongs in `admin.css` as a `.color-swatch-chip` class; only the `background` should remain dynamic via `th:style="'background:' + ${color}"`. Pre-existing from Phase 60, but visible in the Phase 61 audit because it sits on `season-detail.html` which the cleanup phase explicitly listed as an audit target.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

**Method:** Grepped for generic labels across the 5 audited templates + 2 error templates.

**Positive findings:**
- All UI text is English (CLAUDE.md §"UI text in English" honored): `Phase Type`, `Layout`, `Format`, `Save Phase`, `Cancel`, `Edit Season`, `+ Add Phase`, `+ Add Group`, `Combined`, `View bracket →`. No German leakage.
- Empty-state copy is task-specific, not generic: `seasons.html:39` `No seasons created yet.`, `season-detail.html:41` `No teams assigned.`, `standings.html:51` `Please select a season.`, `standings.html:56` `This season has no Regular Phase yet. Create one first to see standings.`, `standings.html:62` `This phase has no race results yet — standings will appear once results are recorded.`. These are all helpful action-oriented messages.
- Phase Type / Layout / Format dropdown labels post-UAT-01 fix: `Regular Season` / `Playoff` / `Placement` / `League` / `Groups` / `Bracket` / `Swiss` / `Round Robin` — clear, business-domain language (verified at `SeasonPhaseController.java:325-336` and asserted in `SeasonPhaseControllerIT.java:85-94`).
- Destructive-action confirmations are specific: `season-detail.html:15` `Really delete this season? This will also delete all phases, matchdays and results.` and `season-detail.html:301` `Really delete this phase? This will fail if it has matchdays or roster entries.` — both honor CLAUDE.md §"Confirmation for destructive actions".

**Negative findings (justifies score 2):**
- **D-03 endpoint copy is generic.** `/admin/playoffs/{id}/add-season` and `/remove-season` were removed in Phase 61 (Tracked Behavior Change). Old bookmarks land on `templates/admin/error.html:10` which renders `"Something went wrong"` as the default `${message}` text. The user has no signal that the endpoint was intentionally retired — they assume the system is broken. UAT Test 4 in `61-UAT.md` confirms this lands on the global error page (`result: pass` but only because the test asserted "5xx not 404", not "user-meaningful copy").
- **`season-form.html:78`** uses inline copy: `t.shortName + ' — ' + t.name + (t.subTeam ? ' (Sub of ' + t.parentOrSelf.shortName + ')' : '')`. Mostly business-correct, but the parens-style label string is built in the template — borderline-violates CLAUDE.md §"Keep Thymeleaf Templates Lean" because three string concatenations live in SpEL. Pre-existing, not Phase 61's regression.

**Generic-label grep result:** 2 hits, both in default error-template fallbacks (`error.html:15`, `admin/error.html:10` `"Something went wrong"`). 0 hits in the 5 phase-touched templates.

### Pillar 2: Visuals (2/4)

**Method:** Code-only audit (no dev server). Assessed visual hierarchy via template structure, focal-point markers, icon-only-button audit.

**Positive findings:**
- `season-phase-form.html` post-fix has clear hierarchy: H1 with phase + season name (`(Edit Phase / New Phase) — ${season.name}`), card-wrapped form, three logical `form-row` groupings, and a primary-secondary action pair (`Save Phase` / `Cancel`). Focal point is unambiguous.
- `season-detail.html:266-275` uses a two-row tab-nav pattern (Phase 60 D-29) with an explicit `tab-active` modifier — phase awareness is visually communicated. Group sub-tabs inherit from the same pattern (lines 277-290) with `tabs-secondary` to differentiate.
- `standings.html` has 8 `aria-label` attributes on sortable columns (`Sort by Team`, `Sort by Played`, etc.) — accessibility is non-trivial.
- `seasons.html:24-25` retains its existing `● Active` / `○ Inactive` badge unicode glyphs after the gap-09 comment strip; gap-09 was a pure-comment removal with no visible delta (verified via `61-gap-09-SUMMARY.md` line 38).

**Negative findings (justifies score 2):**
- **UAT-01 was a P0 visual regression that shipped.** `season-phase-form.html:26,35,43` rendered `<option value="REGULAR"></option>` (correct value, empty text content) for all three primary dropdowns. The user could not see what they were selecting and could not switch to GROUPS layout. This is a Pillar-2 (Visuals) red-flag because:
  - Phase 60 commit `238d469` shipped the bug,
  - Phase 60 verification didn't catch it (no integration test asserted rendered option text),
  - Phase 61 verification (`/gsd-verify-work 61` initial pass) reported all 4 ROADMAP SCs verified,
  - Only the manual UAT smoke (2026-05-02) caught it.
- **No golden-snapshot or rendered-HTML test scaffolding** exists across the codebase. The Phase 61 fix added one targeted MockMvc assertion (`SeasonPhaseControllerIT.java:85-94`) checking exactly 8 hardcoded label strings — patches one form, doesn't generalize.
- **`season-phase-form.html:23-28` `th:disabled` + hidden mirror-input** — when `phaseTypeReadonly` is true (edit mode), the `<select>` is disabled and a hidden `<input name="phaseType">` carries the value. Functional, but in the disabled state the user sees a dropdown they can't open; no "Phase type cannot be changed after creation" hint copy. Pre-existing from Phase 60, surfaces during the Phase 61 audit because it lives on the same hot-spot template.

### Pillar 3: Color (3/4)

**Method:** Grepped `#[0-9a-fA-F]{3,8}` and `rgb(` across the 5 audited templates.

**Findings:**
- Phase 61 introduced **zero** new color references.
- `season-detail.html:82-84` hardcodes three fallback hex values: `#333333` / `#555555` / `#4fc3f7` — these are dataset attributes for the SeasonTeam edit modal's color pickers, used as placeholders when no team color is set. Pre-existing (Phase 60), and they live in `data-*` attributes rather than CSS, so they're not breaking the 60/30/10 split — they're seed values for the JS-driven `<input type="color">` widgets.
- `admin.css` is 2001 lines and is the canonical color source. No template overrides accent / primary / secondary directly via `style="color:..."`.

**Verdict:** No regression introduced; baseline preserved. Score 3/4 (not 4) because the three hardcoded hex defaults at `season-detail.html:82-84` should be CSS custom properties (`--swatch-default-primary`, etc.) for theme parity if a dark-mode is ever added.

### Pillar 4: Typography (3/4)

**Method:** Inspected the 5 audited templates for hand-rolled font-size / font-weight inline overrides.

**Findings:**
- 0 hand-rolled `style="font-size:..."` or `style="font-weight:..."` in the 5 audited templates.
- All headings use semantic `<h1>` / `<h2>` / `<h3>` / `<h4>` consistently — `season-detail.html` has H1 (season name), H2 (phase name + section labels Roster / Bracket / Matchdays / Standings), H3 (subsections), H4 (Modal section labels). Hierarchy is clean.
- `text-dim` utility class (defined in admin.css) is the canonical helper for de-emphasized copy — used 6+ times in the audited templates without inline overrides.
- `.label` class on `season-detail.html:24-32` provides typography for definition-list-style fields.

**Verdict:** No regression introduced; baseline preserved. Score 3/4 (not 4) because Phase 61's UI footprint is too small to verify the >4 fonts / >2 weights guideline against the entire codebase.

### Pillar 5: Spacing (3/4)

**Method:** Inspected the 5 audited templates for `style="margin:..."` / `style="padding:..."` / arbitrary spacing values, and counted CSS-class spacing usage.

**Findings:**
- **Spacing scale is honored** in the 5 audited templates: `mt-md` / `mt-lg` / `mt-xs` / `mt-sm` / `mt-xl` / `mb-sm` / `mb-xs` / `ml-xs` are used 17+ times across `season-detail.html` alone.
- `form-row` / `form-group` / `form-check` / `actions` / `actions--end` are the canonical layout primitives — `season-phase-form.html` uses them exclusively (`form-row` × 4, `form-group` × 12).
- Button-size class audit (`btn-xs|sm|lg|tab`): `season-phase-form.html` 0, `seasons.html` 2, `season-form.html` 6, `season-detail.html` 7, `standings.html` 2 — Phase 60's button hierarchy is preserved.

**Negative findings (justifies score 3, not 4):**
- **3 inline `th:style` survivals on `season-detail.html:56-58`** for color-swatch chips: `display:inline-block;width:14px;height:14px;border-radius:3px;background:`. These are NOT on `.btn` elements (CLAUDE.md §"No Inline Styles on Buttons" technically respected) — they're decorative `<span>` chips. Still recommend extracting to `.color-swatch-chip` admin.css class. Pre-existing from Phase 60.

### Pillar 6: Experience Design (2/4)

**Method:** Audited state coverage (loading / error / empty / disabled / confirmation), the form-completion journey on `/admin/seasons/{sid}/phases/{pid}/edit`, and the legacy-URL behavior change.

**Positive findings:**
- **Empty states** are well-covered: `seasons.html:38-40`, `season-detail.html:41`, `season-detail.html:259-264` (no REGULAR phase), `standings.html:50-58` (4 separate empty-state branches: no season, no REGULAR phase, phase-but-no-results, no driver ranking).
- **Destructive confirmations** are present: `season-detail.html:15`, `season-detail.html:301`, `seasons.html:30`, `season-form.html:62-65` (remove team form).
- **Validation feedback** is rendered: `season-phase-form.html:29,37` shows field-level errors via `th:errors`; `season-form.html:14` shows name-field error.
- **D-02 convenience-getter pattern** is honored: `matchday-detail.html:8,10,28`, `playoff-matchup.html:16`, `playoff-seed.html:56` — none of these recompute the season manually after V6 dropped `Matchday.season_id`. The CLAUDE.md §"No Fallback Calculations" rule is preserved.
- **Regression coverage added in-branch:** `SeasonPhaseControllerIT.java:75-95` asserts all 8 dropdown labels are rendered post-fix. Worth credit even though the test only patches one form.

**Negative findings (justifies score 2):**
- **UAT-01 was a hard user-task-blocker.** Per `61-UAT.md` Test 1 narrative: `Auf /admin/seasons/{id}/phases/{pid}/edit sind die Auswahlboxen Phase Type, Layout und Format leer (keine Option-Texte sichtbar). Ohne Layout-Umschaltung auf GROUPS lässt sich Test 1 nicht durchführen — Group-Sub-Tabs erscheinen nur bei isGroupsLayout=true.` The user couldn't complete the GROUPS-layout journey at all until commit `f5b10bc`. In isolation, this is a 1/4. Lifted to 2/4 because the fix landed in-branch with a regression test. The bug shipped through Phase 60 plan + execute + verify, indicating the verification gate has a blind spot for rendered-text content.
- **D-03 endpoint behavior is unsignaled.** `/admin/playoffs/{id}/add-season` 5xx redirects to a generic `500 — Error / Something went wrong` page. No retired-endpoint copy. Tracked Behavior Change is documented in `61-CONTEXT.md` and the PR description, but the user-facing surface gives no hint.
- **No loading state for the form-save round-trip.** `season-phase-form.html:100` `<button type="submit">Save Phase</button>` does not disable on submit. If the user double-clicks, two POSTs go through. Pre-existing, not Phase 61's responsibility, but flagged because the Phase 61 cleanup audit re-touched this template.
- **The disabled `phaseType` select in edit mode shows no rationale.** Users in edit mode see a disabled dropdown they can't open and no copy saying "Phase type cannot be changed after creation". CLAUDE.md §"Keep Thymeleaf Templates Lean" — an inline `<small class="form-hint" th:if="${phaseTypeReadonly}">Phase type is fixed after creation.</small>` would close the loop.

---

## Files Audited

**Templates (Phase 61 directly touched):**
- `src/main/resources/templates/admin/season-phase-form.html` (UAT-01 hot-spot, lines 26 / 35 / 43 fix verified via grep — `.get(pt|l|f)` pattern present, no `[pt]` bracket-indexer survivals)
- `src/main/resources/templates/admin/seasons.html` (gap-09 stale-comment strip — verified no rendered-UI delta)

**Templates (Phase 61 audit-target, no direct edit):**
- `src/main/resources/templates/admin/season-form.html`
- `src/main/resources/templates/admin/season-detail.html`
- `src/main/resources/templates/admin/standings.html`
- `src/main/resources/templates/error.html` (D-03 landing page for retired endpoints)
- `src/main/resources/templates/admin/error.html` (admin-themed error template)

**Java sources (UAT-01 root cause):**
- `src/main/java/org/ctc/admin/controller/SeasonPhaseController.java` (lines 316-339, `addFormModelAttributes` method — `Map<Enum, String>` model population pattern)
- `src/test/java/org/ctc/admin/controller/integration/SeasonPhaseControllerIT.java` (lines 75-95, regression test for UAT-01)

**CSS:**
- `src/main/resources/static/admin/css/admin.css` (2001 lines — sanity-checked, no Phase 61 changes; spacing / button-size / color tokens still canonical)

**Phase artifacts read for context:**
- `.planning/phases/61-cleanup-quality-gate/61-CONTEXT.md`
- `.planning/phases/61-cleanup-quality-gate/61-VERIFICATION.md` (UAT closure section + Original verification snapshot)
- `.planning/phases/61-cleanup-quality-gate/61-UAT.md` (UAT-01 + UAT-03 narrative)
- `.planning/phases/61-cleanup-quality-gate/61-04-SUMMARY.md` (GroupsSeasonE2ETest)
- `.planning/phases/61-cleanup-quality-gate/61-05-SUMMARY.md` (LegacyMigratedSeasonE2ETest)
- `.planning/phases/61-cleanup-quality-gate/61-gap-09-SUMMARY.md` (final stale-comment strip)

**Audit grep evidence:**
- `grep -nE 'style="' on 5 templates`: 3 hits, all on `season-detail.html:56-58` (color-swatch chips, pre-existing).
- `grep -nE '#[0-9a-fA-F]+' on 5 templates`: 3 hits on `season-detail.html:82-84` (data-attribute defaults, pre-existing).
- `grep -nE '\$\{season\.(format|legs|...)\}' across all templates`: **0 hits**. V6 schema-drop is clean — no Thymeleaf SpEL crashes against dropped Season fields.
- `grep -nE '\$\{(matchday|playoff)\.season\.' across all templates`: 6 hits across `playoff-matchup.html`, `matchday-detail.html`, `races.html`, `playoff-seed.html` — all use the D-02 convenience getter, none recompute the season manually.
