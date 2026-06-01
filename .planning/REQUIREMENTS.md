# Requirements: CTC Manager — v1.17 Guest Drivers

**Defined:** 2026-06-01
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

## v1.17 Requirements

Requirements for the v1.17 milestone. Each maps to roadmap phases.

### GUEST — Guest Assignment (Lineup & Results)

- [ ] **GUEST-01**: An admin can add a guest driver to a race lineup, selectable from **any existing driver** in the system (not restricted to the season roster), specifying which team the guest is fielded for.
- [x] **GUEST-02**: An admin can record a guest driver's finishing position/result in the race results for the fielding team.
- [x] **GUEST-03**: An admin can edit or remove a guest-driver assignment from a lineup/result.
- [x] **GUEST-04**: A lineup/result entry is persistently identifiable as a guest assignment in the data model, independent of season-roster membership (new additive Flyway migration if needed; existing migrations untouched, H2 + MariaDB compatible).

### SCORE — Scoring & Personal Crediting

- [ ] **SCORE-01**: A guest driver's race points count normally toward the **fielding team's** match score and standings, via the existing `ScoringService.aggregateMatchScores(race)` aggregation — no separate points model for guests.
- [ ] **SCORE-02**: A guest driver's earned points are **additionally credited to that driver personally in the season's driver-ranking**, additive to their own team races — including drivers who have no roster entry (`SeasonDriver`) in that season.
- [ ] **SCORE-03**: Guest crediting is recomputed consistently on every result save (no double-counting; root cause handled in the service, no template/controller fallback calculations per CLAUDE.md "No Fallback Calculations").

### MARK — Marking & Visibility

- [ ] **MARK-01**: Guest drivers are marked on the **Scorecard** (race scoring) graphic.
- [ ] **MARK-02**: Guest drivers are marked on the **Provisional Scores** graphic.
- [ ] **MARK-03**: Guest drivers are marked on the further **matchday graphics** (e.g. match-results / matchday-result), so guests are not unmarked when those graphics post alongside the Scorecard.
- [ ] **MARK-04**: Guest assignments are marked in the **admin matchday/race detail** view (lineup + results).
- [ ] **MARK-05**: The season **driver-ranking** (admin + public site) marks the guest appearance.
- [ ] **MARK-06**: The public **driver-profile** page shows the guest race as a marked entry (which team fielded the driver).

> The concrete visual treatment of the guest marking (`*` after the name, a "Gast"/origin-team badge, …) is decided in the UI phase against a rendered reference (see CLAUDE.md "Visual Verification with `playwright-cli`") — it is intentionally not pre-committed here.

## Future Requirements

Deferred candidates from prior milestone audits — tracked in PROJECT.md "Deferred candidates", not in this roadmap:
- DISC-FUTURE-01..05 (inbound Discord, auto-trigger pipeline, settings-form migration, multi-guild, public-site webhook)
- Per-season configurable team size (`driverSlots` on `SeasonPhase`/`Season`, default 6)
- ISEMPTY-AUDIT (`.isEmpty()` vs `.isBlank()` semantic audit)
- Discord channel bulk-rename
- Webhook-token encryption at rest
- Cross-milestone operator-action UATs (UAT-02, QUAL-02, UX-01)

## Out of Scope

Explicitly excluded for v1.17 to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Ad-hoc / free-text guest drivers not yet in the system | User decision: a guest is always an **existing** driver, selected from the driver pool — no on-the-fly driver creation in the lineup form. |
| Dedicated guest points config / different scoring rules for guests | Guests score exactly per the existing race scoring; no forfeit/override model. |
| Automatic guest detection | Guest assignment is always a deliberate manual admin action. |
| Bulk guest import (CSV/Sheets) | Out of scope; the driver-sheet import path is unchanged. |
| Reassigning a guest's points away from the fielding team | The fielding team always receives the points (SCORE-01); guest crediting is additive on the personal ranking only. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| GUEST-01 | Phase 113 | Pending |
| GUEST-02 | Phase 113 | Complete |
| GUEST-03 | Phase 113 | Complete |
| GUEST-04 | Phase 113 | Complete |
| SCORE-01 | Phase 114 | Pending |
| SCORE-02 | Phase 114 | Pending |
| SCORE-03 | Phase 114 | Pending |
| MARK-01 | Phase 115 | Pending |
| MARK-02 | Phase 115 | Pending |
| MARK-03 | Phase 115 | Pending |
| MARK-04 | Phase 115 | Pending |
| MARK-05 | Phase 115 | Pending |
| MARK-06 | Phase 115 | Pending |

**Coverage:**
- v1.17 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0

---
*Requirements defined: 2026-06-01*
