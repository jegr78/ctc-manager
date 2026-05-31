# Phase 105: Carbon HUD Graphics Redesign - Context

**Gathered:** 2026-05-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 105 re-scopes from the original "Team Card Visual Redesign" to the **full Carbon/Gold ("Carbon HUD") graphics redesign** of every Playwright-rendered admin graphic, against the external Claude-Design handoff delivered into this session on 2026-05-29.

**In scope — 16 templates under `src/main/resources/templates/admin/`:**

- **Team Card (1):** `team-card-render.html` — the original CARD-01 target, plus the recommended `TeamCardService` color-robustness patch.
- **Composites (5):** `settings-render.html`, `lineup-render.html`, `results-render.html`, `match-results-render.html`, `provisional-scores-render.html`.
- **Matchday/List (5):** `matchday-schedule-render.html`, `matchday-overview-render.html`, `standings-render.html`, `matchday-results-render.html`, `power-rankings-render.html`.
- **Stream Overlay (1):** `overlay-render.html` — geometry/skew/positions EXACTLY preserved, background stays transparent.
- **Analogy extension (4) — NOT in the handoff, designed by analogy to the Carbon system:** `matchday-pairings-render.html` (sibling of `matchday-overview`) and the three `playoff-round-*-render.html` (schedule/results/overview).

**Two backend changes (both decided in this discussion):**
- `TeamCardService`: add `accentVisColor` + `onPrimaryColor` (color-robustness patch).
- `ProvisionalScoresGraphicService`: set `raceLabel` only for matches with > 1 race (else `null`).

**Hard invariants (from the handoff + ROADMAP CARD-02):**
- Same dimensions/format/positions as the originals (Team Card 1080×1920; Overlay geometry exact).
- All `th:*` bindings and model variables stay UNCHANGED — no new mandatory variables. Card consumers (Discord auto-post POST-02, Re-Post/Refresh buttons, admin preview) keep working without caller changes.
- Pure drop-in template replacement for the 12 handoff templates; the 4 analogy templates are rebuilt to match the Carbon system using existing bindings only.

**Out of scope:**
- Any change to layout, format, data model, or `GraphicService` calling signatures (beyond the two named backend tweaks).
- New graphics or new model variables.

</domain>

<decisions>
## Implementation Decisions

### Scope & Structure
- **D-01:** Scope deliberately expanded from team-card-only to all admin graphics — the Claude-Design session produced a coherent, high-quality Carbon/Gold system the operator wants applied across the board. (User-confirmed 2026-05-29.)
- **D-02:** Stay as a single re-scoped Phase 105 ("Carbon HUD Graphics Redesign") rather than splitting into 105/106/107. `plan-phase` splits into ~4 plan groups (Team Card / Composites / Matchday-List / Overlay+Analogy), executed in waves per the GSD wave model.
- **D-03:** REQUIREMENTS broadened — CARD-01/CARD-02 extended to all graphics; new CARD-03 (composites) and CARD-04 (matchday-list + overlay + analogy templates) added. ROADMAP Phase 105 retitled accordingly.

### Team Card color robustness (Area 1)
- **D-04:** Apply the `TeamCardService` patch. After `gradientColor`, set `accentVisColor = computeAccentVisColor(accentColor, primaryColor)` and `onPrimaryColor = contrastColor(primaryColor)`. New helpers reuse the existing `relativeLuminance` (0–255 scale): accent < 28 → fall back to primary; primary luminance > 140 → dark text `#0b0b10`, else `#ffffff`. Style mirrors the existing `computeGradientColor`. Template reads both via Thymeleaf-Elvis with graceful fallback. `TeamCardService` is JaCoCo-excluded (Playwright runtime) — no coverage impact.

### Provisional Scores raceLabel (Area 2)
- **D-05:** Change `ProvisionalScoresGraphicService` (`:98`) so `raceLabel` is set ONLY for matches with > 1 race (else `null`), so the new `.race-chip` (rendered only when `raceLabel != null`) disappears on single-race matches. The existing ProvisionalScores IT that pins `"Race N"` MUST be updated to assert the new conditional behavior (both branches).

### Extra-template coverage (Area 3)
- **D-06:** Extend the Carbon system to all 4 non-handoff templates by analogy — `matchday-pairings-render.html` (mirror of `matchday-overview`) and `playoff-round-schedule/results/overview-render.html` (analogous to the matchday-list templates). Rationale: avoid a visible style break when old + new graphics are posted together. These have NO handoff reference, so each requires explicit `playwright-cli` visual verification against the Carbon design tokens.

### Verification (Area 4)
- **D-07:** Group verification by the 4 handoff groups (Team Card / Composites / Matchday-List / Overlay+Analogy). Visual approval via `playwright-cli` Desktop + Mobile, screenshots into `.screenshots/105-*/`, compared against the reference screenshots in `design-handoff/screenshots/`. Use the admin template editors (`/admin/tools/template-editors`) and `/admin/tools/team-cards` → Generate as live-preview entry points. Dev server started via `./scripts/app.sh start dev` (loads `.env.dev`).

### Claude's Discretion
- Exact mapping of the handoff `matchday-overview-render.html` ("Pairings/Seeds") onto the repo's `matchday-overview` vs `matchday-pairings` templates — resolve during research by inspecting the `AbstractMatchdayGraphicService` subclasses' `getDefaultTemplatePath()` and which view each renders. The analogy template (`matchday-pairings`) must end up consistent regardless.
- Per-plan task granularity and wave grouping within the 4 groups.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design handoff (PRIMARY — read first, top to bottom)
- `.planning/phases/105-team-card-visual-redesign/design-handoff/DESIGN-NOTES.md` — the full design spec: file mapping (template → target path), design tokens (Gold `#f5c542`, Carbon vignette, bars, rows/panels, logo-chip, radii), used model variables (all unchanged), the two optional backend patches, render/browser hints (`color-mix`, `oklch(from …)` — Chromium-only, OK under Playwright), CTC-logo handling, test entry points.
- `.planning/phases/105-team-card-visual-redesign/design-handoff/HANDOFF.md` — team-card-specific handoff (V4 "Carbon HUD"), design principles, the `TeamCardService` patch code, composite/matchday/overlay redesign notes, the provisional-scores `raceLabel` recommendation.
- `.planning/phases/105-team-card-visual-redesign/design-handoff/README.md` — Claude-Design bundle instructions ("recreate pixel-faithfully; bindings unchanged; don't screenshot the prototypes").
- `.planning/phases/105-team-card-visual-redesign/design-handoff/handoff-templates/` — the **12 production-ready Thymeleaf templates** (1:1 drop-in replacements for `src/main/resources/templates/admin/`).
- `.planning/phases/105-team-card-visual-redesign/design-handoff/screenshots/` — 6 target-look reference renders (`01-team-card`, `02-composite-match-results`, `03-provisional-scores`, `04-matchday-pairings`, `05-standings`, `06-power-rankings`) — the visual acceptance bar.

### Implementation targets in the repo
- `src/main/resources/templates/admin/*-render.html` — the 16 templates being replaced/rebuilt (see Phase Boundary list).
- `src/main/java/org/ctc/admin/service/TeamCardService.java` — has `relativeLuminance` (0–255), `computeGradientColor`; add `computeAccentVisColor` + `contrastColor`, set 2 new ctx vars. Note the custom-template-override path (`team-card-template.html` in upload-dir).
- `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java:98` — `raceLabel` conditional change.
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — `encodeClasspathResource` helper; provides `ctcLogoBase64`/`vsBadgeBase64`/`commentatorBase64` to the 8 composite/matchday/overlay services (NOT to TeamCardService).
- `src/main/resources/static/admin/img/{ctc-logo-white.png,vs-badge.svg,commentator.png}` + `static/admin/fonts/ConthraxSb.woff2` — shared assets already present.

### Project specs / conventions
- `docs/superpowers/specs/2026-03-30-team-cards-design.md` — the SUPERSEDED original team-card design (historical; the new Carbon design replaces it).
- `.planning/REQUIREMENTS.md` — CARD-01/02 (broadened) + new CARD-03/04.
- `CLAUDE.md` — "No Inline Styles on Buttons" (N/A here — render templates), "Keep Thymeleaf Templates Lean", coverage-excluded graphic services list, visual-verification-with-playwright-cli, dev-server via `./scripts/app.sh start dev`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AbstractGraphicService.encodeClasspathResource(path, mime)` — already wired into all 8 composite/matchday/overlay services; `ctcLogoBase64`/`vsBadgeBase64`/`commentatorBase64` already bound. New composite/matchday/overlay templates need NO service change for assets.
- `TeamCardService.relativeLuminance` (0–255) + `computeGradientColor` — direct basis for the D-04 patch helpers.
- Shared assets (`ctc-logo-white.png`, `vs-badge.svg`, `commentator.png`, `ConthraxSb.woff2`) all present under `static/admin/`.
- Custom-template-override mechanism in `TeamCardService` (`team-card-template.html` in upload-dir) — an operator with a saved custom team-card template will NOT pick up the new default until they reset via the editor. Flag in plan as an operator note.

### Established Patterns
- Render pipeline = Playwright/Chromium screenshot of a Thymeleaf-rendered HTML at fixed viewport (Team Card 1080×1920). Templates may use modern CSS (`color-mix`, `oklch(from …)`) since the bundled Chromium supports them.
- All graphic services are JaCoCo-excluded (Playwright runtime cannot run under instrumentation) — template/service changes here do not move coverage; the 82% gate is held by the rest of the suite.
- Each `*GraphicService` renders a fixed template name via `templateEngine.process(...)`; matchday/playoff services resolve via `getDefaultTemplatePath()` on `AbstractMatchdayGraphicService` / `AbstractPlayoffRoundGraphicService` subclasses.

### Integration Points
- Discord auto-post POST-02 (`AFTER_COMMIT` hook, Phase 95) uploads 2 multipart PNGs per match — must stay green (WireMock IT). Template-only changes keep the upload path intact.
- Re-Post / Refresh buttons on Match-Detail + `/admin/discord/posts` PATCH existing messages — backward-compat per CARD-02.
- `TemplateRenderingSmokeIT` for `/admin/teams/**` + admin template-editor preview must stay green (no render exceptions on the new templates).
- `ProvisionalScoresGraphicService` D-05 change has a test-impact: the existing IT pinning `"Race N"` must be updated (Test-Impact audit for the planner).

</code_context>

<specifics>
## Specific Ideas

- Design tokens are fixed by the handoff (DESIGN-NOTES.md § Design-Tokens): Gold accent `#f5c542`; Carbon vignette `radial-gradient(120% 80% at 50% 30%, #15151b, #0d0d11 56%, #08080b)`; bars `linear-gradient(180deg,#202028,#121217)` + gold keyline; rows/panels carbon gradients radius 13/14px; logo-chip coin `#14141a` + team-color ring + double-contour drop-shadow; team-card `--primary-vis: oklch(from var(--primary) max(l,.62) c h)`.
- Overlay: geometry MUST NOT change (top-bar 921×120 @ 500/0, bottom-bar 1275×148 @ 218/924, skew, CTC corner), background transparent — it intentionally overlays video.
- CTC logo on dark Carbon bars uses `ctcLogoBase64` (white coin) WITHOUT invert filter. Team Card embeds its own base64 CTC mark (service sets no `ctcLogoBase64` there).
- The handoff bundle's `index.html` / `redesign.html` / `graphics-proposals.html` / `overlay-proposals.html` are reference previews only — NOT copied into the repo, NOT deployed.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within the (deliberately expanded) phase scope. The 4 analogy templates were folded into scope (D-06) rather than deferred, to avoid a mixed old/new visual style.

</deferred>

---

*Phase: 105-Carbon-HUD-Graphics-Redesign*
*Context gathered: 2026-05-29*
