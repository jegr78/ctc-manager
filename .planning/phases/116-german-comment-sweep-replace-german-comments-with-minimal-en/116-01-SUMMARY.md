---
phase: 116-german-comment-sweep
plan: 01
type: execute
status: complete
requirements: [CLEAN-01, CLEAN-02]
---

# Plan 116-01 Summary — Main-source German comment sweep (CLEAN-01/02)

## Outcome

Replaced or removed every German comment in the main-source comment-bearing files. Comment-text only — no CSS rule, Thymeleaf expression, YAML key/value, or markup changed.

## Final target list (Task-1 re-scan, authoritative)

The CONTEXT inventory was a floor. The re-scan confirmed every floor target **and surfaced drift** (see below). Edited:

**Thymeleaf templates (CLEAN-01):**
- `matchday-detail.html:105` — `Single-Leg: direkt Link zum Race` → `Single leg: link directly to the race` (kept, concise WHY).
- `provisional-scores-render.html` — removed decorative section labels at `:30,:40,:55,:59`; translated WHY comments at `:86` (race-chip render condition) and `:209` (race-chip th:if render condition).
- `season-detail.html:6` — `Saison-Header` → `Season header` (kept); `:21` `Saison-Stamm-Display` → **removed** (redundant).
- `overlay-render.html` — `:19`/`:55` section labels reduced to minimal English (`Top: team matchup` / `Bottom: branding`); `:51` `CTC-Logo ... weiße Münze` → `CTC logo top-right (white coin — no invert)` (kept).
- `results-render.html:52` — `Fahrer-Zeilen + HUD-Eckbrackets` → **removed** (decorative).
- `settings-render.html` — `:19,:42,:54` decorative labels → **removed**.
- `team-card-render.html` — removed decorative labels (`Carbon-Textur`, `Teamfarben-Schienen`, `HUD-Rahmen`, `Rating-Parallelogramm`, `Mitte: Logo+Name`, `Stats-HUD-Zeile`, `CTC-Signatur`); kept+translated the genuine WHY notes: `:12-14` (data-driven team vars + fallback), `:30` (lift dark primary), `:63` (subtle outline keeps white number readable), `:69` (landscape logo zone), `:75-76` (double-outline/contain scaling rationale).

**Config / static (CLEAN-02):**
- `application.yml:28-30` OSIV rationale block → single concise English line (kept — deliberate decision); `:41` OSIV warning suppression → English; `:83` actuator health endpoint → English.
- `admin.css:395` — removed banned `Phase 91 / UX-01` marker → `Google API error categories`; `:2034` double violation (`Phase 60` marker + `Saison-Detail`) → `Two-row tabs (season detail + standings)`.

## Inventory drift found (NOT in original files_modified — user approved inclusion)

The scan surfaced two additional admin render templates with German CSS comments that plan 116-01's `files_modified` whitelist missed. CLEAN-01 covers **all** admin templates, so they were in-scope by requirement. User approved including them (AskUserQuestion, 2026-06-02):

- `lineup-render.html:52` — `Pairings + HUD-Eckbrackets + Gold-Mittellinie` → **removed** (decorative).
- `match-results-render.html:52` — `Race-Score + HUD-Eckbrackets` → **removed** (decorative).

This is a planner defect (files_modified undercounted vs. the CLEAN-01 "all admin templates" requirement) — recorded so the milestone PR body and 116-03's repo-wide scan reflect true scope.

## Verification (this plan)

- Residual umlaut scan over all 11 touched files: **0**.
- Residual German-word comment scan: **0** (remaining hits are English false positives: `render`, `header`, `border`, `placeholder`).
- `admin.css` banned-marker scan (`Phase N|Plan-N|UAT-|Wave N|WR-N`): **0**.
- Diff is comment-lines-only (HTML `<!-- -->`, CSS `/* */`, YAML `#`, incl. multi-line continuation lines) — no rule/key/expression/markup changed.
- Per locked verify-cadence, NO build run in this plan — the single `./mvnw clean verify -Pe2e` belongs to 116-03.

## Files changed (11 source files)

7 whitelisted templates + 2 drift templates (lineup-render, match-results-render) + application.yml + admin.css.
