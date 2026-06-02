---
phase: 110
slug: lobby-settings-graphic
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-31
---

# Phase 110 — Validation Strategy

> Per-phase validation contract. Reconstructed from artifacts (State B) on 2026-05-31, gaps filled by gsd-nyquist-auditor.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + AssertJ; Spring MockMvc (controllers); Playwright (E2E + graphic render); WireMock (Discord ITs) |
| **Config file** | `pom.xml` (Surefire/Failsafe routing is `@Tag`-based) |
| **Quick run command** | `./mvnw -Dtest=<ClassName> test` (unit) / `-Dit.test=<ClassName> -DfailIfNoTests=false` (IT) |
| **Full suite command** | `./mvnw clean verify -Pe2e` |
| **Estimated runtime** | ~8 min full (unit + IT + E2E + JaCoCo + SpotBugs) |

---

## Sampling Rate

- **After every task commit:** targeted `-Dtest=<ClassName>` (tight TDD loop)
- **After every wave / phase end:** `./mvnw clean verify -Pe2e`
- **Before sign-off:** full suite green + JaCoCo 82% gate + SpotBugs 0
- **Max feedback latency:** ~8 s (targeted) / ~8 min (full)

---

## Per-Task Verification Map

| Requirement | Plan | Secure / Expected Behavior | Test Type | Automated Command | Status |
|-------------|------|----------------------------|-----------|-------------------|--------|
| LOBBY-01 | 110-01 | Weather prefix parsing (D-06) + room-name (D-08) correct & null-safe | unit | `-Dtest=LobbySettingsWeatherParsingTest` | ✅ green |
| LOBBY-01 | 110-01/03 | Template renders from `${v.*}` vars via Thymeleaf (3-row weather block) | unit | `-Dtest=TemplatePreviewServiceTest` | ✅ green |
| LOBBY-02 | 110-02 | `canGenerateLobbySettings` gate (settings+track+teams, no car/cards) | unit | `-Dtest=RaceServiceTest` | ✅ green |
| LOBBY-02 | 110-02 | `RaceGraphicService.generateLobbySettings` delegates + wraps IOException | unit | `-Dtest=RaceGraphicServiceTest` | ✅ green (G1) |
| LOBBY-02 | 110-02 | `POST /generate-lobby-settings` redirect + flash | integration | `-Dtest=RaceControllerTest` | ✅ green (G2) |
| LOBBY-02 | 110-02 | `RaceDetailData` record arity (constructor callsites) | integration | `-Dtest=RaceControllerCalendarTest` | ✅ green |
| LOBBY-03 | 110-04 | `postLobbySettings` track-null guard → BusinessRuleException (no 500) | unit | `-Dtest=DiscordPostServicePreFlightTest` | ✅ green |
| LOBBY-03 | 110-04 | `DiscordPostService` explicit-ctor wiring (field+param+assignment) | unit | `-Dtest=DiscordPostServicePreFlightTest,DiscordPostServiceRefBranchesTest` | ✅ green |
| LOBBY-03 | 110-04 | `lobbySettingsPost` present in match-detail view model | unit | `-Dtest=MatchControllerDetailViewModelTest` | ✅ green (G3) |
| LOBBY-03 | 110-04 | `POST /post-lobby-settings` happy/preflight/transient branches | integration | `-Dit.test=MatchControllerPostLobbySettingsPreFlightIT` | ✅ green (G4) |
| LOBBY-04 | 110-03 | Editor tab + `lobbySettingsTemplate`/`lobbySettingsIsCustom` attrs | integration | `-Dtest=TemplateEditorControllerTest` | ✅ green |
| LOBBY-04 | 110-03 | Live-preview renders lobby-settings (no "Unknown template type") | unit | `-Dtest=TemplatePreviewServiceTest` | ✅ green |
| LOBBY-05 | 110-01 | `LobbySettingsGraphicService` JaCoCo-excluded; 82% line gate met | build gate | `./mvnw clean verify -Pe2e` (jacoco:check) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No Wave 0 setup needed — JUnit 5 / Mockito / MockMvc / Playwright / WireMock were already present; the four gap tests (G1–G4) reuse existing fixtures and analogs.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pixel-fidelity of the rendered 1920×1080 PNG vs the design-handoff reference | LOBBY-01, LOBBY-02 | `LobbySettingsGraphicService` is a Playwright-runtime service — JaCoCo-excluded; Playwright cannot run under coverage instrumentation. Layout/visual quality is a human judgement. | Compare `.screenshots/auto-uat/lobby-settings-editor-preview.png` (or a generated `lobby-settings.png`) side-by-side with `design-handoff/preview/lobby-settings-1920x1080.png`. **Verified: visual checkpoint approved 2026-05-31.** |
| Live Discord post lands in the match channel | LOBBY-03 | Real webhook send to a live Discord channel requires Discord credentials + a linked channel; not available in CI/demo. WireMock covers the integration/payload logic. | On a match with a linked Discord channel + complete settings + tracks, click "Post Lobby Settings"; confirm the bundle appears in the match channel and re-post edits the existing message. (Operator UAT — pending real-data setup.) |

---

## Validation Sign-Off

- [x] All requirements have `<automated>` verify or a documented manual-only entry
- [x] Sampling continuity: no 3 consecutive requirements without automated verify
- [x] Wave 0 covers all MISSING references (none — existing infra)
- [x] No watch-mode flags
- [x] Feedback latency acceptable (~8 min full gate)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-31 (gaps G1–G4 filled green; full `clean verify -Pe2e` green)
