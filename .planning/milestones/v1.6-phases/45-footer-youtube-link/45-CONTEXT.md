# Phase 45: Footer YouTube Link - Context

**Gathered:** 2026-04-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Add a YouTube channel link (`https://www.youtube.com/@CommunityTeamCup`) to the shared footer in `layout.html` so it appears on every generated static site page.

</domain>

<decisions>
## Implementation Decisions

### Link Presentation
- **D-01:** Use plain text "YouTube" as the link label — consistent with existing footer links (Top, Archive, Season Name) which are all text-based, no icons.
- **D-02:** Use the `footer-link` CSS class (already exists in `style.css` line 442) — no new CSS needed.
- **D-03:** Add `target="_blank" rel="noopener"` since it's an external link.

### Link Position
- **D-04:** Place after the last existing footer link (the active season link). Add a `footer-sep` dot separator before it, following the existing pattern.
- **D-05:** The YouTube link is always visible (no `th:if` guard) — unconditional, unlike the season link which depends on `activeSeasonSlug`.

### URL
- **D-06:** Hardcoded URL `https://www.youtube.com/@CommunityTeamCup` — not configurable. The footer is a layout concern, not a data-driven feature.

### Claude's Discretion
- Whether to add a small CSS modifier for external links (e.g., subtle icon) — not required but acceptable

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Site Layout
- `src/main/resources/templates/site/layout.html` — Shared layout template, footer at lines 64-77. YouTube link goes here.
- `src/main/resources/static/site/css/style.css` — `.footer-link` class at line 442, `.footer-sep` at line 450.

### Tests
- `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — Existing footer tests (search for "footer"). New tests for YouTube link follow same JSoup parsing pattern.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `footer-link` CSS class — already styled with color, hover transition, cursor:pointer
- `footer-sep` pattern — `<span class="footer-sep" aria-hidden="true">&middot;</span>` used between links
- Existing footer link pattern in layout.html serves as exact template

### Established Patterns
- Footer links are plain `<a>` elements with `class="footer-link"` and optional `th:if` guards
- External links (none exist yet in footer) should use `target="_blank" rel="noopener"`
- Tests parse generated HTML with JSoup and assert link presence via CSS selectors

### Integration Points
- Single file change: `layout.html` footer section (lines 64-77)
- Tests: `SiteGeneratorServiceTest.java` — add assertions for YouTube link in footer

</code_context>

<specifics>
## Specific Ideas

- URL: `https://www.youtube.com/@CommunityTeamCup` (exact, from user requirement)
- Follow the exact existing pattern: `footer-sep` dot + `footer-link` anchor

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 45-footer-youtube-link*
*Context gathered: 2026-04-16*
