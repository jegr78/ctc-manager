# Requirements

## Milestone v1.14: Team Card Redesign & Data Safety

**Goal:** Visual-Redesign aller per Playwright generierten Admin-Grafik-PNGs (Team-Card + Komposite + Matchday-/Listen-Grafiken + Stream-Overlay) gemäß externem Claude-Design "Carbon HUD"-Handoff und Wiederherstellung der `local`-Profile-Daten-Isolation gegen den v1.11-Drift.

> **Scope-Erweiterung 2026-05-29:** CARD-01/CARD-02 ursprünglich nur Team-Card. Nach Lieferung des Claude-Design-Handoffs (kohärentes Carbon/Gold-System für alle 12 Render-Grafiken) hat der Operator den Scope bewusst auf alle Admin-Grafiken erweitert (CARD-03/CARD-04 ergänzt). Siehe `.planning/phases/105-team-card-visual-redesign/105-CONTEXT.md` D-01/D-03.

### Data Safety (SAFE)

- [x] **SAFE-01**: `DevDataSeeder` and `TestDataService` Spring beans are only loaded when the active profile contains `dev`. Active profiles `local`, `docker`, and `prod` MUST NOT instantiate either bean, so the test-data seeder cannot run against the real MariaDB or write demo logos into `data/local/uploads/`. (Reverts the v1.11 `@Profile({"dev","local"})` drift introduced by commit `598d1431`.)
- [x] **SAFE-02**: An integration test loads the Spring context with `@ActiveProfiles("local")` and asserts that both `DevDataSeeder` and `TestDataService` beans are absent from the context. The test must fail if either bean is registered, so any future re-drift toward including `local` in the seeder's `@Profile` value is caught by `./mvnw verify` instead of by a production data accident.

### Team Card Redesign (CARD)

- [ ] **CARD-01**: The team card PNG output of `TeamCardService` is regenerated to match the externally-supplied Claude-Design "Carbon HUD" handoff (HTML/CSS spec covering layout, typography, spacing, logo position, and visual hierarchy). Includes the recommended `TeamCardService` color-robustness patch (`accentVisColor` + `onPrimaryColor`). Handoff delivered 2026-05-29; canonical reference in `.planning/phases/105-team-card-visual-redesign/design-handoff/`.
- [ ] **CARD-02**: The existing card-consumer integration paths remain backward-compatible after the redesign: auto-post on Discord channel create (Phase 95 POST-02), manual Re-Post + Refresh buttons on `/admin/discord/posts` and the team detail page, and the team-card preview in the admin UI continue to work without changes to callers. Extends to all redesigned graphics — no `GraphicService` calling-signature or model-variable changes (except the two named backend tweaks in CARD-03).
- [ ] **CARD-03**: The five composite/matchup graphics (`settings-`, `lineup-`, `results-`, `match-results-`, `provisional-scores-render.html`) are restyled to the Carbon/Gold system as drop-in template replacements with unchanged `th:*` bindings. Includes the `ProvisionalScoresGraphicService` change to set `raceLabel` only for matches with > 1 race (else `null`), with the existing IT updated to assert the conditional `.race-chip`.
- [ ] **CARD-04**: The matchday/list graphics and stream overlay (`matchday-schedule-`, `matchday-overview-`, `standings-`, `matchday-results-`, `power-rankings-render.html`, `overlay-render.html`) are restyled to the Carbon/Gold system (overlay geometry/skew/positions and transparency preserved exactly). Additionally, the four templates NOT covered by the handoff — `matchday-pairings-render.html` and the three `playoff-round-*-render.html` — are rebuilt by analogy to the Carbon system using existing bindings only, so no visible old/new style break occurs when graphics are posted together.

## Future Requirements (deferred to a later milestone)

Carried forward from v1.13 close (per `.planning/PROJECT.md § Out of scope for v1.14`):

- **DISC-FUTURE-01** — Inbound Discord interaction (slash commands, polls, reaction reads); requires deployment model change (always-online endpoint instead of outbound webhooks)
- **DISC-FUTURE-02** — Auto-trigger pipeline (race-save → post); revisit once edit-confidence is established
- **DISC-FUTURE-03** — Discord settings-form migration into the admin app
- **DISC-FUTURE-04** — Multi-guild support
- **DISC-FUTURE-05** — Discord-notification webhook for the public site
- **ISEMPTY-AUDIT** — String `.isEmpty()` audit (~10 callsites with different semantics from `.isBlank()`; case-by-case decision per Phase 103 CONTEXT D-06)

Cross-milestone operator-action UATs (per CLAUDE.md "Pre-existing debt may cross milestone boundaries"):

- **UAT-02** — Legacy season visual smoke (carry from v1.9 / v1.10 / v1.11 / v1.12 / v1.13)
- **QUAL-02** — `local`-profile MariaDB manual smoke (carry from v1.11 / v1.12 / v1.13)
- **UX-01** — Driver-import badge screenshots in operator runbook (carry from v1.12 / v1.13)

## Out of Scope (v1.14)

Explicit exclusions per user scoping decision 2026-05-29:

- **Graphic content additions** — driver lists with PSN-IDs, season stats (points, rank), multiple card variants (Header / Standings / Match-Preview), and any new model variables are out of scope. v1.14 covers Layout + Visual Redesign of the existing graphics only (same dimensions, format, data model, bindings).
- **Card-generation performance/caching** — no caching layer, on-demand-regeneration scheme, or invalidation logic introduced in v1.14.
- **Real-upload restore into `data/local/uploads/`** — the user already mirrored the real logo / car / track images to `~/Library/CloudStorage/.../CTC-Admin/Backup/uploads`. Restoring them back is an operator action, not a v1.14 code requirement.
- **Orphan-file cleanup in `data/local/uploads/teams/`** — removing the 17 verwaisten Test-Logo folders (VRX / SGM / ADR / TBR / ICL / SVT / NFR / EGP / HMS / PWR) that the v1.11 seeder drift created is an operator action, not a v1.14 code requirement.
- **Backup-verify smoke covering UAT-02 / QUAL-02 / UX-01** — these are operator UATs that already cross multiple milestones; not pulled into v1.14 scope.

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SAFE-01 | Phase 104 — Data Safety Lockdown | Satisfied (104-VERIFICATION.md passed) |
| SAFE-02 | Phase 104 — Data Safety Lockdown | Satisfied (104-VERIFICATION.md passed) |
| CARD-01 | Phase 105 — Carbon HUD Graphics Redesign | Delivered & UAT-verified (105-UAT 12/12, integration PASS); formal 105-VERIFICATION.md pending |
| CARD-02 | Phase 105 — Carbon HUD Graphics Redesign | Delivered & UAT-verified; integration PASS (+1 preview-fidelity warning); 105-VERIFICATION.md pending |
| CARD-03 | Phase 105 — Carbon HUD Graphics Redesign | Delivered & UAT-verified (raceLabel both-branch unit tests); 105-VERIFICATION.md pending |
| CARD-04 | Phase 105 — Carbon HUD Graphics Redesign | Delivered & UAT-verified (AUTO-UAT 16 graphics); 105-VERIFICATION.md pending |

**Coverage:** 6/6 v1.14 requirements mapped to exactly one phase. No orphans, no duplicates.
