# Phase 11: Template Quality - Research

**Researched:** 2026-04-06
**Domain:** Thymeleaf admin templates, CSS refactoring, admin.css conventions
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Keep `th:style` with Thymeleaf expressions for data-driven values (team colors from DB, conditional winner/loser/draw coloring). These cannot be static CSS classes since the values come from the model at render time.

**D-02:** Replace all static `style="..."` attributes with CSS classes in admin.css. Static means any inline style whose values are known at design time (padding, font-size, display, gap, etc.).

**D-03:** Add semantic component classes to admin.css (e.g., `.score-card`, `.color-swatch`, `.modal-overlay`, `.action-bar`, `.score-value`, `.result-badge`) rather than Tailwind-style utilities. This matches the existing pattern in admin.css (`.badge`, `.empty-state`, `.chip`).

**D-04:** For common layout patterns repeated across templates, create reusable layout classes (e.g., `.flex-center`, `.flex-between`, `.nowrap`). Keep these minimal — only for patterns that appear 3+ times.

**D-05:** Priority 1: season-detail.html (45 inline styles) and race-detail.html (47 inline styles) — explicitly named in QUAL-01.

**D-06:** Priority 2: All remaining admin templates with inline styles (matchday-detail, team-form, race-results, standings, etc.), excluding graphic render templates (`*-render.html`) and template-editors.html.

**D-07:** template-editors.html (181 inline styles) is a special case — its styles are heavily structural for the editor UI. Refactor as a separate plan/task to keep changes reviewable.

**D-08:** Graphic render templates (team-card-render.html, matchday-*-render.html, playoff-round-*-render.html, overlay-render.html, power-rankings-render.html) are explicitly excluded. These are standalone HTML consumed by Playwright for screenshot generation and must not reference admin.css.

**D-09:** Before any template changes, capture Playwright screenshots of affected pages using dev+demo profile as baseline.

**D-10:** After CSS migration, compare screenshots to verify pixel-identical appearance. Any visual difference must be intentional and documented.

**D-11:** When replacing inline styles on elements that JavaScript manipulates via `element.style` or `element.className`, update the JS code to use the new CSS classes. Check all `<script>` blocks in modified templates.

### Claude's Discretion
- Exact naming of new CSS classes (semantic names that fit the existing admin.css naming style)
- Grouping and ordering of new classes within admin.css
- Whether to split large templates into multiple commits or handle per-template

### Deferred Ideas (OUT OF SCOPE)
None — analysis stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| QUAL-01 | Inline-Styles in Admin Templates durch CSS-Utility-Klassen ersetzt (Prioritaet: season-detail, race-detail; Ausnahme: Graphic-Render-Templates) | Full inline-style inventory compiled; CSS class naming strategy verified against existing admin.css patterns; JS-awareness analysis complete |
</phase_requirements>

---

## Summary

Phase 11 is a pure CSS/HTML refactoring phase: no Java code changes, no Spring logic, no new endpoints. The goal is to migrate static `style="..."` attributes in Thymeleaf admin templates to CSS classes in `admin.css`.

The codebase already has a consistent semantic class naming convention in admin.css (`.badge`, `.empty-state`, `.chip`, `.score-display`, `.detail-fields`). New classes must follow this convention — semantic and component-scoped, not utility-first. CSS custom properties (`--bg-card`, `--border`, `--radius-md`, etc.) are already in widespread use across inline styles; new classes must reference these variables, not hardcode colors.

The most important constraint is the static-vs-dynamic split: static `style="..."` attributes (layout, sizing, typography, spacing) must be replaced; Thymeleaf `th:style` expressions that compute colors from DB-driven values (team colors, winner/loser state) remain as-is. This distinction is locked in D-01/D-02.

The three templates with the highest inline style counts — template-editors.html (181, out of scope), race-detail.html (47), and season-detail.html (45) — drive the pattern library. By the time P1 is complete, most CSS components needed for P2 templates will already exist.

**Primary recommendation:** Work per-template, P1 first. Extract CSS classes for structural patterns as you encounter them, then reuse across P2 templates. Verify screenshots before and after each template change using the `playwright-cli` skill.

---

## Standard Stack

### Core (already in use — no new dependencies)

| Asset | Location | Purpose |
|-------|----------|---------|
| admin.css | `src/main/resources/static/admin/css/admin.css` | All admin UI styles. New CSS classes are added here. |
| Thymeleaf templates | `src/main/resources/templates/admin/*.html` | Server-side rendered HTML — the source of inline styles |
| playwright-cli | `.claude/skills/playwright-cli/SKILL.md` | Visual screenshot comparison for before/after verification |

**No npm packages, no build tools, no new dependencies are required.** This is a file-edit-only phase. [VERIFIED: codebase inspection]

---

## Architecture Patterns

### Existing admin.css Class Categories

The existing admin.css follows this taxonomy (verified by direct file read):

| Category | Examples | Pattern |
|----------|----------|---------|
| Components | `.badge`, `.badge-active`, `.badge-inactive`, `.chip`, `.empty-state` | Named for what they represent |
| Layout | `.toolbar`, `.actions`, `.form-row`, `.detail-fields`, `.result-grid` | Named for the structure |
| Buttons | `.btn`, `.btn-xs`, `.btn-sm`, `.btn-lg`, `.btn-tab`, `.btn-primary`, `.btn-danger`, `.btn-success` | Modifier-style suffixes |
| Utilities | `.text-dim`, `.mb-sm`, `.mt-md`, `.seed-col`, `.seed-input` | Minimal; only for widely-repeated single properties |
| Domain | `.score-display`, `.score-win`, `.score-draw`, `.score-loss`, `.bracket-*`, `.swiss-*`, `.attachment-*` | Component-scoped domain names |

**Pattern rule:** New classes should fit into an existing category or introduce a clearly-scoped domain component class. [VERIFIED: admin.css read]

### CSS Custom Properties Available

All new classes must use these variables (do not hardcode colors or radii):

```css
/* From :root in admin.css */
--bg, --bg-card, --bg-input, --bg-hover
--border, --border-focus
--text, --text-dim, --text-label, --white
--accent, --success, --success-bg, --danger, --danger-bg
--radius-sm, --radius-md, --radius-lg
```
[VERIFIED: admin.css read]

### Anti-Patterns to Avoid

- **Hardcoded colors in new CSS:** Never `color: #66bb6a` — always `color: var(--success)` or the appropriate variable.
- **Tailwind-style micro-utilities:** `.p-16`, `.d-flex`, `.gap-8`. The project uses semantic names per D-03.
- **Modifying graphic render templates:** These are standalone HTML for Playwright screenshot generation. They intentionally do not reference admin.css (D-08).
- **Removing `th:style` for DB-driven values:** Team color swatches and winner/loser row coloring use `th:style` because values come from the model. These stay (D-01).
- **Forgetting JavaScript style.* assignments:** Several templates manipulate styles via JavaScript. When the HTML inline style is removed, the JS must be updated to use the new CSS class instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Color picker UI | Custom color picker component | Existing `input[type=color]` + text input pattern (already in both modal templates) |
| Modal overlay | JS dialog framework | CSS class + `display:flex/none` toggle via JS (already the pattern) |
| CSS preprocessing | Sass/Less/PostCSS | Plain CSS with custom properties — project has no build tool by design |
| Visual regression testing | Custom screenshot diff | `playwright-cli screenshot` before/after comparison |

---

## Inline Style Inventory

### Priority 1 Templates

#### season-detail.html (45 static inline styles)
[VERIFIED: direct grep]

Key patterns found:

| Pattern | Occurrences | Proposed CSS Class |
|---------|-------------|-------------------|
| `style="margin-top:32px;"` on `.detail-section` | 5 | `.detail-section--spaced` or add `.mt-lg` utility |
| `style="padding:16px;"` on `.empty-state` | 5 | Fold into `.empty-state` definition |
| Modal overlay: `display:none;position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,0.6);align-items:center;justify-content:center;` | 2 | `.modal-overlay` |
| Modal body: `background:var(--bg-card);border:1px solid var(--border);border-radius:12px;padding:24px;width:100%;max-width:Xpx;margin:16px;` | 2 | `.modal-body` (with `--modal-max-width` or size modifier) |
| Color picker pair: `display:flex;gap:6px;align-items:center;` on `.color-pair` | 3 | `.color-pair` already exists as class on div; needs CSS rule |
| Color picker input: `width:36px;height:30px;padding:1px;...` | 3 | `.color-picker-input` |
| Color text input: `width:80px;font-size:13px;` | 3 | `.color-text-input` |
| `style="display:flex;gap:3px;"` color swatch row | 1 | `.color-swatch-row` |
| color swatch spans (th:style — DYNAMIC, keep) | 3 | N/A — remain as `th:style` |
| `style="margin-left:6px;font-size:11px;"` on badges | 2 | `.badge--inline` modifier |
| `style="min-width:auto;"` on table | 1 | `.table-compact` |
| Column width: `style="width:60px;"`, `style="width:1%;white-space:nowrap;"`, `style="text-align:center;"` etc. | 5 | `.col-xs`, `.col-nowrap`, `.text-center` (if 3+ uses) |
| `style="margin-left:4px;"` on Replace button | 1 | `.btn--ml` or `ms-xs` margin util |
| Modal section header: `style="margin:16px 0 8px;color:var(--text-dim);..."` | 1 | `.modal-section-label` |
| Modal hint text: `style="font-size:12px;color:var(--text-dim);..."` | 1 | `.form-hint` (reusable) |
| `style="margin-top:20px;justify-content:flex-end;"` on `.actions` | 2 | `.actions--end` modifier |
| `style="margin-top:12px;"` on form groups | 2 | Reuse `.mt-sm` (new) or `.mt-md` |
| `style="display:none;"` on logo preview | 1 | Initial hidden state — JS controls this |
| Logo preview img: `style="max-width:80px;border-radius:6px;background:#222;padding:4px;"` | 1 | `.logo-preview-img` |
| File input: `style="padding:4px;..."` | 1 | `.file-input` |
| `style="display:block;margin-top:2px;"` on `<small>` | 1 | `.form-hint` (reusable across templates) |

**JavaScript impact in season-detail.html:** [VERIFIED: grep]
- `document.getElementById('seasonTeamModal').style.display = 'none'` → when modal gets `.modal-overlay` class, JS must use `element.classList.remove('modal-overlay--visible')` or keep `style.display` manipulation. Safest approach: keep `style.display` toggling in JS since the hidden/visible states are JS-controlled. No class rename needed for JS-toggled display.
- `document.getElementById('modalLogoPreview').style.display = 'block/none'` → same pattern, keep JS style.display for JS-toggled visibility.
- The `style.display = 'flex'` when showing the modal is the "show" action — if the overlay is `.modal-overlay` with `display:none` by default, the JS must set `style.display = 'flex'` to show it (or use a class toggle). The safer option is using CSS class `.modal-overlay--visible { display: flex; }` and toggling the class, but keeping `style.display` toggling also works if the initial `style="display:none"` is replaced by a CSS default.

**Recommended approach:** Keep `style.display` JS manipulation for modals. The modal overlay's initial state moves to CSS (`.modal-overlay { display: none; }`). The JS `style.display = 'flex'` sets the open state. JS sets `style.display = 'none'` to close. This means the HTML inline `style="display:none"` on modals is removed (it becomes the CSS default), and `style.display = 'flex'` in JS remains.

#### race-detail.html (47 static inline styles)
[VERIFIED: direct grep]

Key patterns:

| Pattern | Occurrences | Proposed CSS Class |
|---------|-------------|-------------------|
| Graphics action bar: `style="margin-bottom:16px;display:flex;justify-content:flex-end;gap:8px;align-items:center;flex-wrap:wrap;"` | 1 | `.action-bar` (reusable) |
| Disabled graphic button wrapper: `style="display:inline-flex;align-items:center;gap:8px;"` | 5 (repeated pattern) | `.btn-with-hint` |
| Score banner container: `style="background:var(--bg-card); border:1px solid var(--border); border-radius:var(--radius-md); padding:24px; margin-bottom:16px; display:flex; ..."` | 1 | `.score-banner` |
| Score banner team side: `style="text-align:center; flex:1;"` | 2 | `.score-banner__team` |
| Score label (Home/Away): `style="font-size:13px; text-transform:uppercase; ..."` | 2 (static) | `.score-banner__label` |
| Team name in banner: `style="font-size:16px; font-weight:600; margin-bottom:8px;"` + `th:style` (dynamic) | 2 — keep th:style, wrap static in class | `.score-banner__name` |
| Score value: `style="font-size:36px; font-weight:700; ..."` + `th:style` (dynamic) | 2 — keep th:style, wrap static in class | `.score-banner__value` |
| Result indicator container: `style="height:22px;"` | 2 | `.score-banner__result-area` |
| WIN badge: `style="background:rgba(102,187,106,0.15); color:#66bb6a; padding:2px 10px; border-radius:12px; font-size:11px; font-weight:700;"` | 2 | `.result-badge .result-badge--win` |
| LOSS indicator: `style="font-size:11px; color:var(--text-dim); ..."` | 2 | `.result-badge .result-badge--loss` |
| DRAW badge: `style="background:rgba(255,255,255,0.1); color:var(--text-label); ..."` | 1 | `.result-badge .result-badge--draw` |
| Colon separator: `style="font-size:24px; color:var(--text-dim); font-weight:300;"` | 1 | `.score-separator` |
| DRAW overlay: `style="position:absolute;"` | 1 | `.score-banner__draw-overlay` |
| Playoff badge: `style="background:rgba(79,195,247,0.1); color:var(--accent); padding:2px 8px; border-radius:12px; font-size:12px; font-weight:600;"` | 1 | `.context-badge` |
| Table text-align/font-variant on `td`: multiple | 7 | `.td-center`, `.td-right`, `.td-numeric`, `.td-numeric-bold`, `.td-label` |
| Attachment actions: `style="display:flex;gap:6px;align-items:center;"` | 1 | `.actions` (already exists) |
| `style="padding:16px;"` on `.empty-state` | 2 | Fold into `.empty-state` |
| Attachment forms area: `style="margin-top:16px;display:flex;gap:16px;flex-wrap:wrap;"` | 1 | `.attachment-forms` |
| Upload/link form: `style="display:flex;gap:8px;align-items:flex-end;"` | 2 | `.inline-form` |
| `style="margin-bottom:0;"` on nested `.form-group` | 2 | `.form-group--inline` modifier |
| File input styling | 1 | `.file-input` (same as season-detail) |
| `style="display:block;margin-top:2px;"` on `<small>` | 2 | `.form-hint` |
| Link inputs: `style="width:120px/200px;padding:6px 8px;..."` | 2 | `.input-sm`, `.input-lg` or `.attachment-input-name`, `.attachment-input-url` |
| `style="display:flex;gap:8px;"` on input row | 1 | `.input-row` |
| `style="font-size:12px;"` on `text-dim` span | 1 | `.text-xs` utility (if 3+ uses) |

**JavaScript impact in race-detail.html:** None — no JS style manipulation in this template. [VERIFIED: grep]

### Priority 2 Templates — Pattern Reuse Forecast

By completing P1, the following CSS classes will already cover large portions of P2 templates:

| New CSS Class | Reused in |
|---------------|-----------|
| `.empty-state` (padding folded in) | All P2 templates |
| `.form-hint` | team-form, race-results, import, gt7-sync-preview |
| `.file-input` | team-form, race-form, car-form, track-form |
| `.modal-overlay`, `.modal-body` | season-detail only, but pattern reusable |
| `.action-bar` / `.btn-with-hint` | matchday-detail (graphics section has same pattern) |
| `.inline-form` | matchday-detail, race-scoring-form, import |
| `.color-picker-input`, `.color-text-input` | team-form (has same color picker pattern) |
| `.td-center`, `.td-right`, `.td-numeric` | race-results, standings, matchday-detail |
| `.result-badge--win/loss/draw` | matchday-detail, standings |

### matchday-detail.html (42 styles) — Key Patterns
[VERIFIED: grep]

- `.match-row`: `background:var(--bg-input); border:1px solid var(--border); border-radius:var(--radius-sm); overflow:hidden;`
- `.match-header`: `display:flex; align-items:center; padding:12px 16px;` + winner/loser th:style
- `.match-team-name`: `flex:1; text-align:right; font-weight:600; color:var(--white);` (and mirrored left side)
- `.match-score-area`: `padding:0 16px; display:flex; align-items:center; gap:8px;`
- `.match-score-value`: `font-size:20px; font-weight:700;` + `th:style` for win/loss color — keep th:style
- `.match-bye`: `font-size:13px; color:var(--text-dim); font-style:italic;`
- `.leg-row`: `display:flex; align-items:center; padding:8px 0; font-size:13px;`
- `.leg-label`: `width:50px; color:var(--accent); font-weight:600;`
- `display:inline;` on download forms (repeated) → `.form-inline`
- `.graphics-section`, `.graphics-actions` for the exports block

---

## Common Pitfalls

### Pitfall 1: Removing Dynamic th:style by Mistake
**What goes wrong:** An editor removes a `th:style` that computes winner/loser color from the model, replacing it with a static class — breaking color logic for all races.
**Why it happens:** `th:style` and `style=` look similar in templates; the distinction requires reading the value.
**How to avoid:** Only touch `style="..."` (static, hardcoded). Leave `th:style="..."` (Thymeleaf expression) and `th:styleappend="..."` untouched.
**Warning signs:** Any `style` attribute whose value contains `${` is a Thymeleaf expression — never replace.

### Pitfall 2: Breaking JavaScript Modal Display
**What goes wrong:** The initial `style="display:none"` is removed from a modal div and replaced with a CSS class, but the JS still does `element.style.display = 'flex'` to show it. The CSS class `.modal-overlay { display: none; }` means the modal hides correctly, but then JS sets inline `style.display = 'flex'` which takes precedence. The close action sets `style.display = 'none'` inline, overriding the CSS again. This pattern actually works correctly.
**What actually breaks:** If JS sets `element.style.display = ''` (empty string) to "clear" the override — this would revert to the CSS default (`none`), not show the modal. Check: season-detail JS uses explicit `'none'`/`'flex'`, never `''`. Safe.
**How to avoid:** Document the JS-CSS interaction per modal. Verify JS still explicitly sets both show and hide states.

### Pitfall 3: empty-state Padding Inconsistency
**What goes wrong:** `.empty-state` has `padding: 48px` in admin.css, but many templates override it inline with `style="padding:16px;"`. If the override is removed without adjusting the CSS, visual appearance changes.
**Why it happens:** The default 48px padding is appropriate for full-page empty states but too large for small card sections.
**How to avoid:** Create `.empty-state--compact { padding: 16px; }` modifier class, replace all `style="padding:16px;"` on `.empty-state` elements with this class.

### Pitfall 4: Column Width Styles in Tables
**What goes wrong:** `<th style="width:1%;">` and `<td style="width:60px;">` are used to control table column widths. Removing these without a CSS replacement will cause table layout to shift.
**How to avoid:** These are legitimate static styles. Create targeted classes (`.col-action`, `.col-color`, `.col-xs`) or verify each case — they must be replaced, not just deleted.

### Pitfall 5: Graphic Render Template Confusion
**What goes wrong:** One of the `*-render.html` templates (team-card-render.html has 1 `style=`) gets edited because the grep shows a match.
**How to avoid:** Before any P2 grep-based bulk work, filter out all `*-render.html` files. Only edit non-render templates. The 1 style in team-card-render.html is intentional standalone styling.

### Pitfall 6: Missing Visual Regression After Each Template
**What goes wrong:** Changes to 3 templates are batched without screenshot verification, and a visual regression is only detected at PR review time, requiring tracing which template caused it.
**How to avoid:** Per D-09/D-10: screenshot before+after each template. Use `playwright-cli screenshot --filename=...` with descriptive names. Store in `.screenshots/` per feedback rule.

---

## Code Examples

### Semantic Component Class Pattern (from existing admin.css)
```css
/* Source: admin.css — existing .badge component as reference pattern */
.badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: var(--radius-lg);
    font-size: 12px;
    font-weight: 600;
}
.badge-active { background: var(--success-bg); color: #66bb6a; }
.badge-inactive { background: #222; color: #999; }
```

### Proposed New Classes — P1 Templates

```css
/* Source: based on inline styles extracted from season-detail.html and race-detail.html */

/* Modal overlay (season-detail.html — 2 occurrences) */
.modal-overlay {
    display: none;
    position: fixed;
    inset: 0;
    z-index: 1000;
    background: rgba(0, 0, 0, 0.6);
    align-items: center;
    justify-content: center;
}
/* Note: JS uses style.display = 'flex'/'none' to show/hide — does not toggle a class */

/* Modal body wrapper */
.modal-body {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 24px;
    width: 100%;
    margin: 16px;
}

/* Color picker row (season-detail and team-form) */
.color-pair {
    display: flex;
    gap: 6px;
    align-items: center;
}

/* Color picker input (season-detail: 36px, team-form: 48px — use width as variant or inline override for size) */
.color-picker-input {
    height: 30px;
    padding: 1px;
    border: 1px solid var(--border);
    border-radius: 4px;
    background: var(--bg-input);
    cursor: pointer;
}

/* Color hex text input */
.color-text-input {
    width: 80px;
    font-size: 13px;
}

/* Color swatch row in table */
.color-swatch-row {
    display: flex;
    gap: 3px;
}

/* Score banner (race-detail.html) */
.score-banner {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    padding: 24px;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 32px;
    position: relative;
}

.score-banner__team {
    text-align: center;
    flex: 1;
}

.score-banner__label {
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 1px;
    color: var(--text-dim);
    margin-bottom: 4px;
}

.score-banner__name {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 8px;
    /* color set by th:style at render time */
}

.score-banner__value {
    font-size: 36px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    margin-bottom: 6px;
    /* color set by th:style at render time */
}

.score-banner__result-area {
    height: 22px;
}

.score-banner__draw-overlay {
    position: absolute;
}

.score-separator {
    font-size: 24px;
    color: var(--text-dim);
    font-weight: 300;
}

/* Result badges (WIN / DRAW / LOSS) */
.result-badge--win {
    background: rgba(102, 187, 106, 0.15);
    color: #66bb6a;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.5px;
}

.result-badge--loss {
    font-size: 11px;
    color: var(--text-dim);
    letter-spacing: 0.5px;
}

.result-badge--draw {
    background: rgba(255, 255, 255, 0.1);
    color: var(--text-label);
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.5px;
}

/* Context badge (Playoff indicator) */
.context-badge {
    background: rgba(79, 195, 247, 0.1);
    color: var(--accent);
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 600;
}

/* Action bar (generate graphics section) */
.action-bar {
    margin-bottom: 16px;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
}

/* Disabled button with hint text */
.btn-with-hint {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}

/* Attachment add forms area */
.attachment-forms {
    margin-top: 16px;
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
}

/* Inline form (display:flex for horizontal layout) */
.inline-form {
    display: flex;
    gap: 8px;
    align-items: flex-end;
}

/* Form group without bottom margin (for inline contexts) */
.form-group--inline {
    margin-bottom: 0;
}

/* Form hint text below inputs */
.form-hint {
    display: block;
    margin-top: 2px;
    font-size: 12px;
    color: var(--text-dim);
}

/* File input styling */
.file-input {
    padding: 4px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg-input);
    color: var(--white);
    font-size: 12px;
}

/* Table: empty state compact (folded padding override) */
.empty-state--compact {
    padding: 16px;
}

/* Table alignment utility classes */
.col-action { width: 1%; white-space: nowrap; }
.col-color  { width: 60px; }
.td-center  { text-align: center; }
.td-right   { text-align: right; }
.td-numeric { font-variant-numeric: tabular-nums; }
.td-numeric-bold { font-variant-numeric: tabular-nums; font-weight: 700; }
.td-label   { font-size: 12px; font-weight: 600; }

/* Modal section label */
.modal-section-label {
    margin: 16px 0 8px;
    color: var(--text-dim);
    font-size: 13px;
    text-transform: uppercase;
    letter-spacing: 1px;
}

/* Badge inline modifier */
.badge--inline {
    margin-left: 6px;
    font-size: 11px;
}

/* Logo preview image */
.logo-preview-img {
    max-width: 80px;
    border-radius: 6px;
    background: #222;
    padding: 4px;
}

/* Actions row justified to end */
.actions--end {
    justify-content: flex-end;
}

/* Detail section with top spacing (replaces style="margin-top:32px;") */
.detail-section + .detail-section {
    margin-top: 32px;
}
/* OR: add utility */
.mt-lg { margin-top: 32px; }
```

### Template Migration Example — Modal

**Before (season-detail.html):**
```html
<div id="seasonTeamModal" style="display:none;position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,0.6);align-items:center;justify-content:center;">
    <div style="background:var(--bg-card);border:1px solid var(--border);border-radius:12px;padding:24px;width:100%;max-width:480px;margin:16px;">
```

**After:**
```html
<div id="seasonTeamModal" class="modal-overlay">
    <div class="modal-body" style="max-width:480px;">
```

Note: `max-width` varies per modal (480px vs 420px) — keep as inline or use a modifier class. Keeping `style="max-width:480px"` is acceptable for a single-value variation.

**JavaScript remains unchanged:** `element.style.display = 'flex'/'none'` still works because it overrides the CSS `display: none` default.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Playwright E2E |
| Config file | `pom.xml` (Surefire + Failsafe) |
| Quick run command | `./mvnw test` |
| Full suite command | `./mvnw verify` |
| E2E command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QUAL-01 | No static inline styles in season-detail, race-detail, P2 templates | Visual (Playwright screenshot) + manual grep verification | `./mvnw verify -Pe2e` | Yes (existing E2E suite) |
| QUAL-01 | Admin pages render correctly after CSS migration | Playwright E2E smoke | `./mvnw verify -Pe2e` | Yes |

**Note:** There is no automated test that counts inline styles. The verification approach is:
1. Before: `grep -c 'style=' template.html` to confirm current count
2. After: `grep -c 'style=' template.html` to confirm reduction (only `th:style` remain)
3. Visual: Playwright screenshots before/after per template

### Sampling Rate
- **Per template migration:** `playwright-cli screenshot` before + after on dev+demo profile
- **Per wave merge:** `./mvnw verify` (unit + integration)
- **Phase gate:** `./mvnw verify -Pe2e` full Playwright suite before `/gsd-verify-work`

### Wave 0 Gaps
None — existing test infrastructure covers the phase. No new test files required; this is a CSS/HTML refactoring with visual verification only.

---

## Environment Availability

| Dependency | Required By | Available | Notes |
|------------|------------|-----------|-------|
| Java 25 / Maven | Build and test | Yes (existing project) | `./mvnw` wrapper present |
| Playwright Chromium | Visual screenshot verification | Check at task time | `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` if missing |
| playwright-cli (skill) | Visual comparison | Yes | `.claude/skills/playwright-cli/SKILL.md` present |
| dev+demo profile | Demo data for screenshot baseline | Yes | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` |

---

## Security Domain

This phase is a CSS/HTML refactoring with no new endpoints, no user input handling, and no authentication changes. No ASVS categories apply. Security enforcement: no applicable threat patterns for pure CSS class migration.

---

## Open Questions

1. **`.detail-section` margin-top pattern**
   - What we know: 5 occurrences of `style="margin-top:32px;"` on `.detail-section` in season-detail.html.
   - What's unclear: Whether a CSS adjacent sibling selector (`.detail-section + .detail-section { margin-top: 32px; }`) or a utility class (`.mt-lg`) is more appropriate. The adjacent selector is elegant but only works when sections are siblings.
   - Recommendation: Use `.mt-lg { margin-top: 32px; }` utility — explicit and matches the existing `.mt-md` and `.mb-sm` pattern.

2. **Modal max-width variation**
   - What we know: Two modals in season-detail differ: 480px (edit) and 420px (replace).
   - What's unclear: Whether to create `.modal-body--sm / --md` modifiers or leave a single `style="max-width:Xpx;"` per modal.
   - Recommendation: Leave `style="max-width:480px;"` inline — a single-property override is acceptable. The primary goal is removing compound structural inline styles. Per D-04, minimal layout utilities only for 3+ occurrences.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `th:styleappend` attributes in season-detail.html line 68 are dynamic (team replacement strikethrough) and should be kept | Inventory | If wrong, a static-looking `th:styleappend` is missed; low risk, visually obvious |
| A2 | race-detail.html contains no JS `style.*` manipulation | JS Impact | If wrong, a JS-driven visual regression occurs; easily caught by screenshot comparison |

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection: `admin.css` — full read, CSS variable and class inventory
- Direct codebase inspection: `season-detail.html` — full read, all 45 `style=` occurrences catalogued
- Direct codebase inspection: `race-detail.html` — full read, all 47 `style=` occurrences catalogued
- Direct codebase inspection: `matchday-detail.html` — grep of all style= occurrences
- Direct codebase inspection: `team-form.html` — grep of all style= occurrences
- Grep scan: all admin templates for `element.style.*` and `className` JS manipulation

### Secondary (MEDIUM confidence)
- CONTEXT.md decisions D-01 through D-11 — extracted user and auto decisions from discuss phase
- CLAUDE.md conventions — button CSS class rules, template quality requirements

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all tools verified in codebase
- Architecture: HIGH — CSS patterns extracted directly from existing files
- Pitfalls: HIGH — identified from actual code patterns in templates
- CSS class proposals: MEDIUM — exact names are Claude's discretion per CONTEXT.md; naming is a recommendation

**Research date:** 2026-04-06
**Valid until:** 2026-06-01 (stable CSS patterns, admin.css rarely changes)
