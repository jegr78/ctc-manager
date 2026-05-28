# Requirements

## Milestone v1.14: Team Card Redesign & Data Safety

**Goal:** Visual-Redesign der per Playwright generierten Team-Card-PNGs gemäß externem Claude-Design-Handoff und Wiederherstellung der `local`-Profile-Daten-Isolation gegen den v1.11-Drift.

### Data Safety (SAFE)

- [ ] **SAFE-01**: `DevDataSeeder` and `TestDataService` Spring beans are only loaded when the active profile contains `dev`. Active profiles `local`, `docker`, and `prod` MUST NOT instantiate either bean, so the test-data seeder cannot run against the real MariaDB or write demo logos into `data/local/uploads/`. (Reverts the v1.11 `@Profile({"dev","local"})` drift introduced by commit `598d1431`.)
- [ ] **SAFE-02**: An integration test loads the Spring context with `@ActiveProfiles("local")` and asserts that both `DevDataSeeder` and `TestDataService` beans are absent from the context. The test must fail if either bean is registered, so any future re-drift toward including `local` in the seeder's `@Profile` value is caught by `./mvnw verify` instead of by a production data accident.

### Team Card Redesign (CARD)

- [ ] **CARD-01**: The team card PNG output of `TeamCardService` is regenerated to match the externally-supplied Claude-Design handoff (HTML/CSS spec covering layout, typography, spacing, logo position, and visual hierarchy). The discuss/plan/execute cycle for this requirement starts only after the user delivers the Claude-Design handoff into this session.
- [ ] **CARD-02**: The existing card-consumer integration paths remain backward-compatible after the redesign: auto-post on Discord channel create (Phase 95 POST-02), manual Re-Post + Refresh buttons on `/admin/discord/posts` and the team detail page, and the team-card preview in the admin UI continue to work without changes to callers.

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

- **Team-card content additions** — driver lists with PSN-IDs, season stats (points, rank), and multiple card variants (Header / Standings / Match-Preview) are out of scope. v1.14 covers Layout + Visual Redesign of the existing single team card only.
- **Card-generation performance/caching** — no caching layer, on-demand-regeneration scheme, or invalidation logic introduced in v1.14.
- **Real-upload restore into `data/local/uploads/`** — the user already mirrored the real logo / car / track images to `~/Library/CloudStorage/.../CTC-Admin/Backup/uploads`. Restoring them back is an operator action, not a v1.14 code requirement.
- **Orphan-file cleanup in `data/local/uploads/teams/`** — removing the 17 verwaisten Test-Logo folders (VRX / SGM / ADR / TBR / ICL / SVT / NFR / EGP / HMS / PWR) that the v1.11 seeder drift created is an operator action, not a v1.14 code requirement.
- **Backup-verify smoke covering UAT-02 / QUAL-02 / UX-01** — these are operator UATs that already cross multiple milestones; not pulled into v1.14 scope.

## Traceability

(Filled by `/gsd-roadmapper` when the v1.14 roadmap is created — requirements mapped to phases here.)
