---
phase: 105
slug: team-card-visual-redesign
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-29
---

# Phase 105 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Carbon HUD graphics redesign: 16 Playwright-rendered admin templates + 2 backend tweaks.
> Graphic services are JaCoCo-excluded (Playwright runtime) — automated coverage comes from
> the `TeamCardServiceTest` / `ProvisionalScoresGraphicServiceTest` unit tests and the
> `TemplateRenderingSmokeIT` + Discord WireMock ITs; visual fidelity is operator-gated.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test (Surefire/Failsafe), Playwright (E2E + render) |
| **Config file** | `pom.xml` (Surefire = unit/integration, Failsafe = `-Pe2e`) |
| **Quick run command** | `./mvnw clean verify` |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~7–9 min full (`-Pe2e`); targeted unit/IT ~30–90 s |

---

## Sampling Rate

- **After every task commit:** Run the targeted command for the touched class
  (`-Dtest=TeamCardServiceTest`, `-Dtest=ProvisionalScoresGraphicServiceTest`,
  `-Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false`).
- **After every plan wave:** Run `./mvnw clean verify` (template render-smoke + Discord ITs green).
- **Before `/gsd-verify-work`:** `./mvnw clean verify -Pe2e` must be green (Wave 4 gate).
- **Max feedback latency:** ~90 s for targeted unit/IT loops.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 105-01-01 | 01 | 1 | CARD-01 | — | N/A (server-controlled color math, no untrusted input) | unit | `./mvnw clean verify -Dtest=TeamCardServiceTest` | ⚠️ new helper tests (W0) | ⬜ pending |
| 105-01-02 | 01 | 1 | CARD-02 | — | N/A (template renders server model) | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | ✅ | ⬜ pending |
| 105-01-03 | 01 | 1 | CARD-01,02 | — | N/A | manual | `playwright-cli` Desktop+Mobile vs `screenshots/01-team-card.png` | N/A (visual) | ⬜ pending |
| 105-02-01 | 02 | 2 | CARD-03 | — | N/A | unit | `./mvnw clean verify -Dtest=ProvisionalScoresGraphicServiceTest` | ⚠️ both-branch update + new single-race test (W0) | ⬜ pending |
| 105-02-02 | 02 | 2 | CARD-02,03 | — | N/A | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | ✅ | ⬜ pending |
| 105-02-03 | 02 | 2 | CARD-02,03 | — | N/A | manual | `playwright-cli` vs `screenshots/02-composite-match-results.png`, `03-provisional-scores.png` | N/A (visual) | ⬜ pending |
| 105-03-01 | 03 | 3 | CARD-02,04 | — | N/A | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | ✅ | ⬜ pending |
| 105-03-02 | 03 | 3 | CARD-04 | — | N/A | integration | Same | ✅ | ⬜ pending |
| 105-03-03 | 03 | 3 | CARD-02,04 | — | N/A | manual | `playwright-cli` vs `screenshots/05-standings.png`, `06-power-rankings.png` | N/A (visual) | ⬜ pending |
| 105-04-01 | 04 | 4 | CARD-04 | — | N/A (overlay geometry pinned by grep assertion) | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | ✅ | ⬜ pending |
| 105-04-02 | 04 | 4 | CARD-04 | — | N/A | integration | Same (4 analogy templates render-smoke) | ✅ | ⬜ pending |
| 105-04-03 | 04 | 4 | CARD-02,04 | — | N/A | e2e | `./mvnw clean verify -Pe2e` (JaCoCo ≥82%, SpotBugs 0, Discord WireMock ITs green) | ✅ | ⬜ pending |
| 105-04-04 | 04 | 4 | CARD-04 | — | N/A | manual | `playwright-cli` vs `screenshots/04-matchday-pairings.png` + Carbon-token check on analogy templates | N/A (visual) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] New unit tests for `computeAccentVisColor` (accent < 28 → primary fallback) and `contrastColor` (luminance > 140 → `#0b0b10`, else `#ffffff`) in `TeamCardServiceTest`.
- [ ] Update both existing `ProvisionalScoresGraphicServiceTest` methods (`whenValidRace_thenTemplateContextIncludesRaceLabelAndExpectedVariables`, `givenSameRaceTwice_whenGenerateProvisionalWithSameIndex_thenSameRaceLabel`) to a ≥2-race fixture asserting `"Race N"`.
- [ ] New `ProvisionalScoresGraphicServiceTest` single-race branch test: `givenSingleRaceMatch_whenBuildContext_thenRaceLabelIsNull()`.

*All other phase behaviors reuse existing infrastructure (`TemplateRenderingSmokeIT`, Discord WireMock ITs).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| All 16 graphics match the Carbon HUD reference renders | CARD-01,02,03,04 | Pixel/visual fidelity cannot be asserted by JUnit; graphic services are JaCoCo-excluded (Playwright runtime) | Start dev server via `./scripts/app.sh start dev`; render via `/admin/tools/template-editors` + `/admin/tools/team-cards` → Generate; `playwright-cli` Desktop + Mobile screenshots into `.screenshots/105-<group>/`; compare against `design-handoff/screenshots/`. |
| 4 analogy templates have no visible old/new style break | CARD-04 | No handoff reference render exists | Visual comparison of `matchday-pairings` + 3 `playoff-round-*` against their Carbon siblings' tokens. |
| Re-Post / Refresh buttons PATCH the existing Discord message and render new designs | CARD-02 | Requires live Discord-dev wiring (`scripts/app.sh` env) | Match-Detail Re-Post Team Cards + `/admin/discord/posts` against dev server. |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies (visual checkpoints are operator-gated per `feedback_graphic_design_iteration`)
- [x] Sampling continuity: no 3 consecutive automated tasks without a verify command (each wave's auto tasks carry targeted commands)
- [ ] Wave 0 covers all MISSING references (`TeamCardServiceTest` helpers, `ProvisionalScoresGraphicServiceTest` both branches)
- [x] No watch-mode flags
- [x] Feedback latency < 90 s (targeted) / < 9 min (full `-Pe2e`)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
