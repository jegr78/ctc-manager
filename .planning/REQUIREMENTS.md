# Requirements: CTC Manager — v1.15 CI Optimisation & Race/Match Defaults

**Defined:** 2026-05-30
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.15 Requirements

Requirements for the v1.15 milestone. Each maps to roadmap phases.

### CI — CI Optimisation

- [ ] **CI-01**: `ci.yml` expensive steps (Maven build, E2E, Docker build) run only when code/build files change; the required checks (`build-and-test`, `dockerfile-noble-pin-guard`, `docker-build`) always report a status so PRs never deadlock — implemented via a `dorny/paths-filter` `changes` job + conditional steps (approach A).
- [ ] **CI-02**: Non-required workflows (`codeql.yml`, `mariadb-migration-smoke.yml`) skip docs/planning-only changes via `paths-ignore` (approach C).
- [ ] **CI-03**: Ignore set defined and applied consistently: `.planning/**`, root `*.md`, `docs/**` (except `docs/site/**` which still triggers `deploy-site.yml`), `.gitmessage` + meta files. A commit touching both code and docs still runs full CI.
- [ ] **CI-04**: E2E test runtime reduced below the current 17:39 median (concrete target set at plan time).
- [ ] **CI-05**: Build caching improved (Maven / Playwright browser / Docker layer) for faster warm runs.
- [ ] **CI-06**: Flaky-test rerun reduction / quarantine extension; unstable tests stabilised at root cause (no symptom hotfixes — see CLAUDE.md "No Flaky Dismissal").

### RACE — Race/Match Prefill Defaults

- [ ] **RACE-01**: The Race/Match create form prefills the scoring scheme inherited from the season/matchday.
- [ ] **RACE-02**: The Race/Match create form prefills the default number of legs/races.
- [ ] **RACE-03**: The Race/Match create form prefills date/time inherited from the matchday.

### LINEUP — Missing-Driver Handling

- [ ] **LINEUP-01**: The Lineup graphic renders an "n/a" placeholder per missing slot when a team fields fewer than 6 drivers.
- [ ] **LINEUP-02**: The Scorecard/Results graphic renders "n/a" + 0 points for missing drivers.
- [ ] **LINEUP-03**: The Provisional-Scores graphic is padded to 6 rows with "n/a" placeholders for missing drivers (currently renders no row at all).
- [ ] **LINEUP-04**: Scoring treats missing drivers consistently as 0 points / no position — root cause fixed in the service, no template/controller fallback calculations (see CLAUDE.md "No Fallback Calculations").

### WO — Walkover

- [ ] **WO-01**: A team that does not compete at all is handled analogously to `Match.bye` — the opponent receives an auto-win with full match points.
- [ ] **WO-02**: The walkover state is persisted via a new Flyway migration (H2 + MariaDB compatible; existing migrations untouched).
- [ ] **WO-03**: A visible "w/o" label appears in standings and the relevant graphics.
- [ ] **WO-04**: An admin can mark a match as a walkover through the UI/form.

### LOBBY — Lobby Settings Graphic

- [ ] **LOBBY-01**: A new `LobbySettingsGraphicService` renders a Carbon-HUD Lobby Settings graphic from a Claude-Design drop-in template, driven purely by template variables (no new data model / no Flyway migration).
- [ ] **LOBBY-02**: An admin can preview and download the Lobby Settings graphic.
- [ ] **LOBBY-03**: The Lobby Settings graphic is available as a new Discord auto-post type, plugged into the existing Discord integration.
- [ ] **LOBBY-04**: The Lobby Settings template is editable via the template editor (custom override, `implements TemplateManageable`).
- [ ] **LOBBY-05**: The service is coverage-excluded (Playwright runtime), consistent with the other graphic services.

## Future Requirements

Deferred candidates from prior milestone audits — tracked in PROJECT.md "Deferred candidates", not in this roadmap:
- DISC-FUTURE-01..05 (inbound Discord, auto-trigger pipeline, settings-form migration, multi-guild, public-site webhook)
- ISEMPTY-AUDIT (`.isEmpty()` vs `.isBlank()` semantic audit)
- Discord channel bulk-rename
- Webhook-token encryption at rest
- Cross-milestone operator-action UATs (UAT-02, QUAL-02, UX-01)

## Out of Scope

Explicitly excluded for v1.15 to prevent scope creep.

| Feature | Reason |
|---------|--------|
| New data model for Lobby Settings | User decision: solved purely via template variables; values arrive with the Claude-Design handoff. |
| Branch-protection reconfiguration (CI approach B: single aggregation gate) | User chose approach A+C — required-check contract stays intact, no branch-protection change. |
| Walkover as a fully separate model (dedicated points config, forfeit reasons) | Reuse the existing `bye` semantics; a richer walkover model is out of scope. |
| E2E runtime: hard sub-target guarantee | CI-04 targets "below 17:39"; a specific number is set at plan time, not pre-committed here. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CI-01 | Phase 106 | Pending |
| CI-02 | Phase 106 | Pending |
| CI-03 | Phase 106 | Pending |
| CI-04 | Phase 106 | Pending |
| CI-05 | Phase 106 | Pending |
| CI-06 | Phase 106 | Pending |
| RACE-01 | Phase 107 | Pending |
| RACE-02 | Phase 107 | Pending |
| RACE-03 | Phase 107 | Pending |
| LINEUP-01 | Phase 108 | Pending |
| LINEUP-02 | Phase 108 | Pending |
| LINEUP-03 | Phase 108 | Pending |
| LINEUP-04 | Phase 108 | Pending |
| WO-01 | Phase 109 | Pending |
| WO-02 | Phase 109 | Pending |
| WO-03 | Phase 109 | Pending |
| WO-04 | Phase 109 | Pending |
| LOBBY-01 | Phase 110 | Pending |
| LOBBY-02 | Phase 110 | Pending |
| LOBBY-03 | Phase 110 | Pending |
| LOBBY-04 | Phase 110 | Pending |
| LOBBY-05 | Phase 110 | Pending |

**Coverage:**
- v1.15 requirements: 22 total
- Mapped to phases: 22 (100%)
- Unmapped: 0

---
*Requirements defined: 2026-05-30*
*Last updated: 2026-05-30 — roadmap created, traceability populated*
