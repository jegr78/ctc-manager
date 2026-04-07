---
phase: 10
slug: service-refactoring
status: draft
shadcn_initialized: false
preset: none
created: 2026-04-06
---

# Phase 10 — UI Design Contract

> Visual and interaction contract for Phase 10: Service Refactoring.
> This is a pure backend refactoring phase. The contract is a visual regression contract —
> all observable UI behaviour must remain bit-for-bit identical before and after the refactor.

---

## Nature of This Phase

**No new UI is introduced. No existing UI changes.**

Phase 10 refactors three backend classes:
- `TemplateEditorController` — generic dispatch replaces 20 copy-paste endpoints
- `PlayoffService` — split into `PlayoffBracketViewService` + `PlayoffSeedingService`
- `RaceService` — split into `RaceFormDataService` + `RaceCalendarService`

Success criterion #4 (ROADMAP.md) states: "All graphic editing, playoff, and race functionality
works identically from the UI." This contract defines what "identically" means across every
affected surface.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none — Thymeleaf SSR, no component library |
| Preset | not applicable |
| Component library | Custom CSS (`admin.css`) — no external library |
| Icon library | Unicode / emoji inline (no icon library in scope) |
| Font (headings) | Conthrax (semibold 600, woff2/ttf self-hosted) |
| Font (body/UI) | system-ui stack: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif |

Source: `src/main/resources/static/admin/css/admin.css` lines 1-44 (verified).

---

## Spacing Scale

Declared values (multiples of 4, from `admin.css`):

| Token | Value | CSS Variable / Usage |
|-------|-------|----------------------|
| xs | 4px | Icon gaps, badge padding, `.alias-row` margin |
| sm | 8px | Inline element spacing, `.actions` gap, `.attachment-item` gap |
| md | 16px | `.form-group` margin, `.toolbar` margin, `.card` margin |
| lg | 24px | `.container` top/bottom padding, `.card` padding, `.bracket` padding |
| xl | 32px | `.container` left/right padding, `.detail-fields` column gap |
| 2xl | 48px | `.empty-state` padding |
| 3xl | 64px | Bracket round gap (depth 2) |

Exceptions:
- Mobile `.container` padding collapses to 16px horizontal, 56px top (hamburger clearance)
- Template textarea min-height: 600px (unconstrained to spacing scale — functional requirement)
- `.bracket-round:nth-child(3)` gap: 160px (visual alignment, bracket-specific)

Source: `admin.css` verified. These values must remain unchanged after refactoring.

---

## Typography

Source: `admin.css` lines 34-44, 149-151, 176-196 (verified).

| Role | Size | Weight | Line Height | Font |
|------|------|--------|-------------|------|
| Body / table cell | 14px | 400 regular | 1.6 | system-ui stack |
| Label / metadata | 13px | 400 regular | 1.6 | system-ui stack |
| Table header | 12px | 600 semibold | 1 | system-ui stack |
| Heading h1 | 22px | 600 semibold | default | Conthrax |
| Heading h2 / h3 | 16px | 600 semibold | default | Conthrax (h1/h2), system-ui (h3) |

**No typography changes are permitted** in this phase. All Thymeleaf templates consume these
values through existing CSS classes — none are defined inline on the elements touched by the
refactor.

---

## Color

Source: `admin.css` `:root` block, lines 13-31 (verified).

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) — page background | `#0a0a0a` (`--bg`) | Page background, template textarea bg |
| Secondary (30%) — surface | `#141414` (`--bg-card`) | `.card`, quick-score form bg |
| Input surface | `#1a1a1a` (`--bg-input`) | Form inputs, bracket matchup, transfer items |
| Hover surface | `#1e1e1e` (`--bg-hover`) | Table row hover, sidebar hover, transfer hover |
| Sidebar | `#111` (hardcoded) | Sidebar background |
| Primary text | `#d0d0d0` (`--text`) | Body text, table cells |
| Dim text | `#999` (`--text-dim`) | Back links, sidebar inactive, empty states |
| Label text | `#aaa` (`--text-label`) | Form labels, table headers |
| White | `#ffffff` (`--white`) | Headings, button text, active sidebar |
| Accent (10%) | `#4fc3f7` (`--accent`) | Active sidebar border, focus ring, winner highlight, tab-active border, accent button bg |
| Border default | `#2a2a2a` (`--border`) | All card/table/input borders |
| Border focus | `#4fc3f7` (`--border-focus`) | Focus outline on all interactive elements |
| Success | `#2e7d32` (`--success`) | `.btn-success`, `.badge-active` border |
| Success bg | `#1b3a1b` (`--success-bg`) | `.alert-success` bg, `.badge-active` bg |
| Danger | `#d32f2f` (`--danger`) | `.btn-danger`, invalid field border |
| Danger bg | `#3a1b1b` (`--danger-bg`) | `.alert-error` bg |

Accent reserved for:
- Active sidebar nav item (left border + bg tint)
- Focus outlines on all interactive elements (`--border-focus` alias)
- Bracket/Swiss winner team name + score highlight
- Template editor active tab underline
- Quick score submit button background tint
- Checkbox `accent-color`
- `.badge-sub` text color (sub-team badges)
- `.detail-link:hover` color

**No color values may change** as a result of Phase 10 refactoring.

---

## Copywriting Contract

Phase 10 introduces no new UI copy. The following flash-attribute messages are produced by
the refactored `TemplateEditorController` endpoints and must remain word-for-word identical.

| Element | Copy | Source |
|---------|------|--------|
| Save success flash | `Template saved` | `TemplateEditorController` save handler — unchanged |
| Save error flash | `Save failed: {exceptionMessage}` | `TemplateEditorController` save handler — unchanged |
| Invalid templateType error | `Unknown template type` | New generic dispatch handler (ARCH-03, 10-RESEARCH.md Pattern 3) |
| Reset success flash | Existing message — must not change | Verify in `TemplateEditorController` reset handler before refactor |
| Reset error flash | Existing message — must not change | Same |

The `Unknown template type` message for invalid `/{templateType}/save` or `/{templateType}/reset`
requests is the only net-new copy in this phase. It must appear as an `.alert-error` flash
attribute rendered via the standard `errorMessage` model attribute, consistent with all other
admin error states.

Empty state, destructive action confirmation: not applicable — Phase 10 introduces no new
screens or destructive flows.

---

## Affected UI Surfaces (Regression Contract)

The following pages must render identically before and after Phase 10. Playwright E2E tests
verify this at phase gate (`./mvnw verify -Pe2e`).

### 1. Template Editor — `/admin/tools/template-editors`

**Components used:** `.tab-btn`, `.tab-btn.tab-active`, `.template-textarea`, `.btn-primary`
(Save), `.btn-secondary` (Reset), `.alert-success`, `.alert-error`, `.card`

**Model attributes that must remain identical:**

| Attribute name | Value type | Source service (before) | Source service (after) |
|----------------|------------|--------------------------|------------------------|
| `teamCardTemplate` | String (HTML) | `TeamCardService` | `TeamCardService` (via `TemplateManageable` dispatch) |
| `lineupTemplate` | String (HTML) | `LineupGraphicService` | `lineupGraphicService` (via map) |
| `settingsTemplate` | String (HTML) | `SettingsGraphicService` | `settingsGraphicService` (via map) |
| `raceResultsTemplate` | String (HTML) | `ResultsGraphicService` | `resultsGraphicService` (via map) |
| `matchResultsTemplate` | String (HTML) | `MatchResultsGraphicService` | `matchResultsGraphicService` (via map) |
| `matchdayOverviewTemplate` | String (HTML) | `MatchdayOverviewGraphicService` | `matchdayOverviewGraphicService` (via map) |
| `matchdayScheduleTemplate` | String (HTML) | `MatchdayScheduleGraphicService` | `matchdayScheduleGraphicService` (via map) |
| `matchdayResultsTemplate` | String (HTML) | `MatchdayResultsGraphicService` | `matchdayResultsGraphicService` (via map) |
| `overlayTemplate` | String (HTML) | `OverlayGraphicService` | `overlayGraphicService` (via map) |
| `powerRankingsTemplate` | String (HTML) | `PowerRankingsGraphicService` | `powerRankingsGraphicService` (via map) |
| `*IsCustom` (10 boolean attrs) | boolean | each service | each service (via map) |
| `activeTab` | String | `@RequestParam` | `@RequestParam` — unchanged |

**Critical constraint (from 10-RESEARCH.md Open Question 1):** The GET index method must
produce the same model attribute names as before refactoring. The safest approach is to leave
the index method's loop-free (10 separate try-catch blocks) unchanged and refactor only the
20 save/reset endpoints into the 2 generic endpoints. This eliminates any risk of attribute
name drift in the Thymeleaf template.

**URL contract:**

| Before | After | Behaviour |
|--------|-------|-----------|
| `POST /admin/tools/template-editors/team-cards/save` | `POST /admin/tools/template-editors/{templateType}/save` where `templateType=team-cards` | Identical — tab redirect to `?tab=team-cards` |
| `POST /admin/tools/template-editors/team-cards/reset` | `POST /admin/tools/template-editors/{templateType}/reset` where `templateType=team-cards` | Identical |
| (same for all 10 template types) | | |

No URL path changes are visible to the browser — the generic endpoint handles the same URL
patterns as the old specific endpoints. No bookmark breakage, no backward-compatibility concern.

### 2. Playoff Pages — `/admin/seasons/{id}/playoff`

**Components used:** `.bracket`, `.bracket-round`, `.bracket-matchup`, `.bracket-team`,
`.bracket-team.winner`, `.bracket-seed`, `.bracket-team-score`, `.card`, `.btn`

**Behaviour contract:**
- Bracket view renders all rounds, seeds, scores, and winner highlights identically
- Seeding form populates with correct seed numbers
- Auto-seed action distributes teams correctly
- Matchup creation, winner advance, and delete actions work as before

**Model attributes from `PlayoffController`:** The return types of `PlayoffBracketViewService`
methods must produce records with the same field names as the current `PlayoffService` inner
records. No Thymeleaf expression may break.

### 3. Race Pages — `/admin/seasons/{id}/races`

**Components used:** `.card`, `.form-group`, `.btn-primary`, `.result-grid`, `.score-display`,
`.quick-score-form`, `.quick-score-input`, `.quick-score-btn`

**Behaviour contract:**
- Race creation form pre-populates correctly (from `RaceFormDataService`)
- Race edit form pre-populates correctly (from `RaceFormDataService`)
- Results form populates drivers, cars, tracks correctly (from `RaceFormDataService`)
- Calendar event creation/update works (from `RaceCalendarService`)
- Quick score save works (stays in `RaceService`)
- All flash success/error messages appear correctly

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable — no shadcn |
| third-party | none | not applicable |

No frontend dependencies are added in Phase 10.

---

## Visual Regression Verification Protocol

The following commands constitute the full visual regression gate for Phase 10:

```bash
# Per-task: unit and integration tests
./mvnw test

# Per-wave: full build with coverage
./mvnw verify

# Phase gate: full suite including Playwright E2E (visual regression)
./mvnw verify -Pe2e
```

Playwright E2E tests cover the three affected surfaces above. Any visual or behavioural
deviation detected by Playwright is a Phase 10 regression and must be fixed before merge.

The `playwright-cli` skill may be used for interactive spot-checking of the template editor
tab switching, playoff bracket render, and race form pre-population after each implementation
task completes.

---

## What Implementors Must NOT Do

The following changes would violate this contract:

- Rename any model attribute passed to Thymeleaf from the three affected controllers
- Change any redirect URL path or query parameter (`?tab=...`) in `TemplateEditorController`
- Change any flash attribute key (`successMessage`, `errorMessage`)
- Add inline styles to any template element
- Change any `.btn`, `.alert`, `.card`, or `.badge` class assignment in templates
- Modify any Thymeleaf template file (`.html`) except to update service injection references
  if compile-time references to inner record types change (e.g., `PlayoffService.SeedingData`
  → `PlayoffSeedingService.SeedingData`)

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS — only one net-new string (`Unknown template type`)
- [ ] Dimension 2 Visuals: PASS — no new screens; regression contract defined
- [ ] Dimension 3 Color: PASS — no color changes; existing tokens documented
- [ ] Dimension 4 Typography: PASS — no typography changes; existing scale documented
- [ ] Dimension 5 Spacing: PASS — no spacing changes; existing scale documented
- [ ] Dimension 6 Registry Safety: PASS — no new dependencies

**Approval:** pending
