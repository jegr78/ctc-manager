# Phase 11: Template Quality - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Admin templates use consistent CSS classes instead of scattered inline styles. Priority on season-detail and race-detail templates. Graphic render templates (*-render.html) are excluded — they use standalone HTML for Playwright screenshot generation.

</domain>

<decisions>
## Implementation Decisions

### Dynamic Styles Handling
- **D-01:** Keep `th:style` with Thymeleaf expressions for data-driven values (team colors from DB, conditional winner/loser/draw coloring). These cannot be static CSS classes since the values come from the model at render time.
- **D-02:** Replace all static `style="..."` attributes with CSS classes in admin.css. Static means any inline style whose values are known at design time (padding, font-size, display, gap, etc.).

### CSS Class Strategy
- **D-03:** Add semantic component classes to admin.css (e.g., `.score-card`, `.color-swatch`, `.modal-overlay`, `.action-bar`, `.score-value`, `.result-badge`) rather than Tailwind-style utilities. This matches the existing pattern in admin.css (`.badge`, `.empty-state`, `.chip`).
- **D-04:** For common layout patterns repeated across templates, create reusable layout classes (e.g., `.flex-center`, `.flex-between`, `.nowrap`). Keep these minimal — only for patterns that appear 3+ times.

### Template Priority and Scope
- **D-05:** Priority 1: season-detail.html (48 inline styles) and race-detail.html (51 inline styles) — explicitly named in QUAL-01.
- **D-06:** Priority 2: All remaining admin templates with inline styles (matchday-detail, team-form, race-results, standings, etc.), excluding graphic render templates (*-render.html) and template-editors.html.
- **D-07:** template-editors.html (181 inline styles) is a special case — its styles are heavily structural for the editor UI. Refactor as a separate plan/task to keep changes reviewable.
- **D-08:** Graphic render templates (team-card-render.html, matchday-*-render.html, playoff-round-*-render.html, overlay-render.html, power-rankings-render.html) are explicitly excluded. These are standalone HTML consumed by Playwright for screenshot generation and must not reference admin.css.

### Visual Verification
- **D-09:** Before any template changes, capture Playwright screenshots of affected pages using dev+demo profile as baseline.
- **D-10:** After CSS migration, compare screenshots to verify pixel-identical appearance. Any visual difference must be intentional and documented.

### JavaScript Awareness
- **D-11:** When replacing inline styles on elements that JavaScript manipulates via `element.style` or `element.className`, update the JS code to use the new CSS classes. Check all `<script>` blocks in modified templates.

### Claude's Discretion
- Exact naming of new CSS classes (semantic names that fit the existing admin.css naming style)
- Grouping and ordering of new classes within admin.css
- Whether to split large templates into multiple commits or handle per-template

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### CSS and Templates
- `src/main/resources/static/admin/css/admin.css` — Existing utility classes (.btn-xs, .btn-sm, .badge, .empty-state, .text-dim, .mb-sm, .mt-md) and component patterns
- `src/main/resources/templates/admin/season-detail.html` — Priority 1 template (48 inline styles, modal with color pickers, team table)
- `src/main/resources/templates/admin/race-detail.html` — Priority 1 template (51 inline styles, score card, results table, action bar)

### Specs and Requirements
- `docs/superpowers/specs/2026-03-26-ctc-manager-design.md` — Design spec for CTC Manager (dark theme, CSS variables, component patterns)
- `.planning/REQUIREMENTS.md` §QUAL-01 — "Inline-Styles in Admin Templates durch CSS-Utility-Klassen ersetzt (Prioritaet: season-detail, race-detail; Ausnahme: Graphic-Render-Templates)"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **admin.css** utility classes: `.btn-xs`, `.btn-sm`, `.btn-lg`, `.btn-tab`, `.badge`, `.badge-active`, `.badge-inactive`, `.text-dim`, `.empty-state`, `.mb-sm`, `.mt-md` — can be extended with new semantic classes
- **CSS custom properties**: `--bg-card`, `--border`, `--radius-md`, `--radius-sm`, `--bg-input`, `--white`, `--text-dim`, `--text-label`, `--accent`, `--success-bg` — already used in inline styles, migration to classes will reference these

### Established Patterns
- Existing CSS uses semantic class names (`.badge-active`, `.empty-state`) not utility-first approach
- Buttons already use CSS classes per CLAUDE.md convention: `.btn-xs`, `.btn-sm` instead of inline styles
- Dark theme with CSS variables throughout — all new classes must use CSS variables, not hardcoded colors

### Integration Points
- **JavaScript in templates**: season-detail.html has modal JS that sets `element.style.*` — must be updated alongside HTML
- **Thymeleaf `th:style`**: Used for dynamic color values (team colors, winner/loser states) — these remain as inline styles
- **admin.css load order**: Templates include admin.css via layout fragment — new classes are automatically available

### Inline Style Inventory (main templates, excluding *-render.html and worktrees)
| Template | Inline Styles | Priority |
|----------|--------------|----------|
| template-editors.html | 181 | Separate plan |
| race-detail.html | 51 | P1 |
| season-detail.html | 48 | P1 |
| matchday-detail.html | 42 | P2 |
| gt7-sync-preview.html | 29 | P2 |
| team-form.html | 25 | P2 |
| race-results.html | 18 | P2 |
| team-cards.html | 18 | P2 |
| import.html | 17 | P2 |
| season-form.html | 16 | P2 |
| race-scoring-form.html | 15 | P2 |
| races.html | 15 | P2 |
| standings.html | 14 | P2 |
| playoff-bracket.html | 12 | P2 |
| race-form.html | 12 | P2 |
| power-rankings.html | 11 | P2 |
| car-form.html | 10 | P2 |
| track-form.html | 10 | P2 |
| import-preview.html | 9 | P2 |
| match-scoring-form.html | 7 | P2 |
| team-detail.html | 6 | P2 |
| cars.html | 5 | P2 |
| gt7-sync.html | 5 | P2 |
| playoff-matchup.html | 5 | P2 |
| tracks.html | 5 | P2 |
| match-scoring-list.html | 4 | P2 |
| race-lineup.html | 4 | P2 |
| driver-form.html | 4 | P2 |
| matchday-generator.html | 3 | P2 |
| drivers.html | 3 | P2 |
| swiss-rounds.html | 3 | P2 |
| playoff-form.html | 2 | P2 |
| teams.html | 2 | P2 |
| matchday-form.html | 2 | P2 |
| matchdays.html | 1 | P2 |
| match-form.html | 1 | P2 |
| generate.html | 1 | P2 |
| driver-detail.html | 1 | P2 |

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — analysis stayed within phase scope.

</deferred>

---

*Phase: 11-template-quality*
*Context gathered: 2026-04-06*
