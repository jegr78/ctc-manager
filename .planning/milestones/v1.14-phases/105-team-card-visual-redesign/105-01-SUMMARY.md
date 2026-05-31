---
phase: 105-team-card-visual-redesign
plan: 01
status: complete
completed: 2026-05-29
requirements: [CARD-01, CARD-02]
---

# Plan 105-01 Summary: Team Card — Carbon HUD V4 + color-robustness patch

## What was built

The team card (the only graphic with a backend change) now renders in the
Carbon/Gold "Carbon HUD" system, with a color-robustness patch on
`TeamCardService` so dark accents and bright primaries stay legible.

## Tasks completed

1. **TeamCardService color-robustness helpers (D-04, CARD-01)** — added two
   package-private helpers mirroring the existing `computeGradientColor` style:
   - `computeAccentVisColor(accent, primary)`: `null` or `relativeLuminance < 28`
     → falls back to `primary`; otherwise returns `accent`.
   - `contrastColor(hex)`: `relativeLuminance > 140` → `#0b0b10` (dark text),
     else `#ffffff`.
   Both context variables (`accentVisColor`, `onPrimaryColor`) are set in
   `generateCard()` immediately after `gradientColor` (line 93), before
   `logoBase64`. 5 new unit tests cover both branches of each helper.
   `TeamCardServiceTest`: 12 tests green.

2. **Carbon HUD V4 team-card template (CARD-01/CARD-02)** — replaced
   `team-card-render.html` with the handoff template (drop-in). Reads only
   existing bindings + the 2 new vars via Thymeleaf-Elvis
   (`${accentVisColor ?: accentColor}`, `${onPrimaryColor ?: '#ffffff'}`).
   No new mandatory model variable. `TemplateRenderingSmokeIT`: 70 tests green.

## Deviation — genuine bug fixed (not in original plan)

The handoff `team-card-render.html` embedded the ~9 KB CTC-mark base64 inside a
`th:src` ternary (`${ctcLogoBase64 != null ? ctcLogoBase64 : 'data:image/png;base64,<9KB>'}`).
**Spring Boot 4 enforces a 10,000-character SpEL ceiling (`EL1079E`)**, checked
*before* evaluation, so `generateCard()` threw `TemplateProcessingException` and
crashed the dev seeder on startup. `TemplateRenderingSmokeIT` stayed green
because it exercises controller GETs, not `generateCard()`.

**Root-cause fix (no symptom hack):** `TeamCardService` never sets
`ctcLogoBase64` for the team card — it carries its own embedded mark via the
adjacent static `src` attribute — so the `th:src` ternary was dead code.
Removed the `th:src` attribute; the static `src` with the identical CTC mark
remains. Dev server now boots clean (Started in ~3 s, 46 cards generated,
0 `EL1079E`, 0 `TemplateProcessingException`).

## Visual verification

Operator-approved (`approved`) against `design-handoff/screenshots/01-team-card.png`:
- Carbon vignette background, gold keyline + accent pins, gold rating
  parallelogram (no "OVERALL" label), logo-chip coin with team-color ring +
  double-contour glow, HUD-bracketed POINTS/RECORD, centered lightning mark.
- Parent team (ADR, gold accent) and sub-team (SGM_S, cyan `--accent-vis`,
  "TEAM S" sub-label pill) both render correctly — the D-04 `accentVisColor`
  patch is visibly working (team-specific accent instead of fixed gold).
- Screenshots: `.screenshots/105-team-card/real-ADR.png`,
  `real-SGM_S-subteam.png`.

## Operator note

A saved custom team-card template in the upload dir (`team-card-template.html`)
overrides the new default — an operator with one must reset it via the template
editor to pick up the Carbon design.

## Commits

- `3e893c09` feat(team-card): add accentVisColor + onPrimaryColor helpers
- `6e8a13a3` feat(team-card): apply Carbon HUD V4 team-card render template
- `cfa08bbb` fix(team-card): drop oversized th:src logo ternary (SpEL 10k limit)

## Tests

- `TeamCardServiceTest`: 12 green (5 new helper tests, both branches each).
- `TemplateRenderingSmokeIT`: 70 green (no render exception on `/admin/teams/**`
  + template-editor GETs).
- Dev-server boot smoke: 46 team cards generated, 0 errors.

## Follow-up for later plans

The same oversized-inline-base64-in-`th:` pattern could exist in other handoff
templates — but the scan showed **only `team-card-render.html`** had inline
base64 blobs (the 8 composite/matchday/overlay services inject
`ctcLogoBase64`/`vsBadgeBase64`/`commentatorBase64` at runtime, so their
templates reference short variable names, not literals). No action needed for
105-02/03/04 on this front.
