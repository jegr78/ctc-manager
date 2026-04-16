# Phase 41: UX Polish & Accessibility - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver polished visual feedback, keyboard/screen-reader accessibility, and code quality cleanup for the static site. This phase addresses eight requirements: skip-to-content link (UX-01), match winner highlight (UX-04), mobile scroll indicator (UX-05), footer with useful links (UX-06), nav toggle aria-label (UX-07), hover transitions (UX-08), cursor:pointer on clickables (UX-09), and inline style removal (QUAL-01). No new pages, no structural changes — purely UX polish and accessibility on existing pages.

</domain>

<decisions>
## Implementation Decisions

### Winner highlight (UX-04)
- **D-01:** Subtle accent background on the winning team name. The winner's `.match-team` span gets accent text color (`var(--accent)`) plus a semi-transparent accent background (`rgba(79,195,247,0.15)`) with small padding and border-radius. Consistent with `.bracket-team.winner` and `.nav-link-active` patterns already in the design system.
- **D-02:** CSS class `.match-team-winner` applied conditionally via `th:class` based on pre-computed winner data from the service. No score parsing in Thymeleaf.

### Mobile scroll indicator (UX-05)
- **D-03:** Right-edge gradient fade on `.table-wrap` when content overflows. A `::after` pseudo-element with `linear-gradient(to right, transparent, var(--bg-card))` signals scrollable content. Pure CSS, no JavaScript required.
- **D-04:** The gradient should only appear on mobile (behind `@media (max-width: 768px)`) and ideally disappears when fully scrolled. If pure CSS cannot detect scroll position, a static fade is acceptable.

### Footer content & layout (UX-06)
- **D-05:** Compact single-line layout. Top row: centered link bar with "Top" (smooth-scroll to top), "Archive" (links to archive.html), and active season name (links to active season standings). Separator: centered dot (·). Bottom row: existing CTC branding text.
- **D-06:** Footer links use `.footer-link` class styled like `.breadcrumb-link` (dim color, accent on hover, 0.2s transition).

### Hover transitions (UX-08)
- **D-07:** Targeted additions only — do not override existing transitions. Add `transition: background-color 0.2s` to `tr` for table row hover. Add transition to new footer links. Existing transitions on `.nav-links a`, `.entity-link`, `.subnav-link`, `.breadcrumb-link` remain untouched.

### cursor:pointer (UX-09)
- **D-08:** Add `cursor: pointer` to `a`, `label[for]`, `[role="button"]` globally. This covers nav toggle label, all links, and any future interactive elements.

### Skip-to-content link (UX-01)
- **D-09:** Standard skip-link as first child of `<body>` in `layout.html`. Visually hidden until focused (absolute positioning offscreen, on-focus slides into view). Target: `<main class="main" id="main-content">`.

### Nav toggle aria-label (UX-07)
- **D-10:** Move `aria-label` from the hidden `<input>` to the visible `<label>` element (which is the actual interactive control users see). Value: "Toggle navigation menu". Add `role="button"` to the label for screen readers.

### Inline style removal (QUAL-01)
- **D-11:** Replace the two inline styles in `driver-profile.html` (line 6: `style="margin-bottom: 24px;"` and line 47: `style="margin-top: 24px;"`) with CSS classes. Use existing utility patterns or create `.driver-header` and `.section-gap` classes. `archive.html` is already free of inline styles — no changes needed there.

### Claude's Discretion
- Exact gradient width and opacity for the scroll indicator fade
- Skip-link styling details (colors, font-size, z-index)
- Whether to add `aria-expanded` to the nav toggle for open/close state communication
- Footer link separator styling (dot vs pipe vs dash)
- CSS class names for the inline style replacements in driver-profile.html
- Whether `cursor: pointer` should also cover `.match-card` elements (clickable feel)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Static site generator
- `src/main/java/org/ctc/sitegen/SiteGeneratorService.java` — Core generation logic; `toRaceView()` must pre-compute winner info for UX-04; `writeTemplate()` for activeSeasonSlug (footer link target)
- `src/main/java/org/ctc/sitegen/model/RaceView.java` — View model; needs winner boolean or method for template conditional class

### Site templates
- `src/main/resources/templates/site/layout.html` — Skip-link (UX-01), nav toggle aria-label (UX-07), footer links (UX-06); shared across all pages
- `src/main/resources/templates/site/matchday.html` — Match winner highlight (UX-04); match-card structure with `.match-team` spans
- `src/main/resources/templates/site/index.html` — Also has match-cards (last matchday); same winner highlight needed
- `src/main/resources/templates/site/driver-profile.html` — Inline style removal (QUAL-01); lines 6 and 47
- `src/main/resources/templates/site/archive.html` — Already clean of inline styles; verify only

### Site CSS
- `src/main/resources/static/site/css/style.css` — All CSS additions: `.match-team-winner`, scroll indicator, `.footer-link`, `.skip-link`, table row transition, cursor:pointer rules; existing CSS variables and responsive breakpoints at 768px

### Existing patterns (for consistency)
- `.bracket-team.winner` (line 352) — Existing winner styling in bracket: accent color + bold
- `.nav-link-active` (line 414) — Accent color + accent background pattern
- `.entity-link` (line 191) — Link styling with accent color + hover transition

### Existing tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Integration tests with Jsoup HTML parsing; add assertions for skip-link, winner class, footer links, aria-labels

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- CSS variables `--accent`, `--bg-card`, `--border`, `--text-dim`, `--text-muted` — all UX additions use these
- `.bracket-team.winner` pattern — accent color + bold font for winner; `.match-team-winner` follows the same approach
- `.nav-link-active` pattern — accent + background for active state; winner highlight uses same background technique
- `.breadcrumb-link` styling — color transition pattern for footer links
- `@media (max-width: 768px)` breakpoint — established mobile breakpoint for scroll indicator

### Established Patterns
- Conditional CSS classes via `th:class` — subnav and nav active state both use this; winner highlight follows same pattern
- Template variables pre-computed in service — winner determination happens in `SiteGeneratorService`, not in templates
- Utility CSS classes (`.text-right`, `.text-center`, `.font-bold`, `.text-white`) — inline style replacements follow this pattern

### Integration Points
- `layout.html` `<body>` — skip-link inserts as first child
- `layout.html` `<label>` — aria-label addition on nav toggle
- `layout.html` `<footer>` — link bar inserts above existing text
- `layout.html` `<main>` — add `id="main-content"` for skip-link target
- `matchday.html` `.match-team` spans — conditional `.match-team-winner` class
- `RaceView` — needs method or field to determine home/away winner
- `style.css` — all new classes and rules added here

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

*Phase: 41-ux-polish-accessibility*
*Context gathered: 2026-04-16*
