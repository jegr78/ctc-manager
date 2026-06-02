# Phase 109: Walkover Handling - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 109-walkover-handling
**Areas discussed:** Data model & bye distinction, Auto-win scoring, Admin marking flow, "w/o" label semantics & placement, Walkover match races, Discord behaviour

---

## Data Model & bye Distinction

### Persistence
| Option | Description | Selected |
|--------|-------------|----------|
| FK walkover_team_id | Nullable FK column on matches; stores which team is affected | ✓ |
| boolean walkover + convention | Single flag; cannot express which of two real teams forfeited | |
| @ManyToOne walkoverTeam entity field | Same DB shape, mapped as JPA association | |

**User's choice:** FK `walkover_team_id`
**Notes:** Needed because a walkover has both teams real — home OR away can forfeit. Mapping detail left to Claude (follow Match home/away `@ManyToOne` convention).

### Semantics of stored reference
| Option | Description | Selected |
|--------|-------------|----------|
| Non-competing team (forfeiter) | walkover_team_id = team that did NOT compete; opponent auto-wins | ✓ |
| Winning team | walkover_team_id = winner; loser derived | |

**User's choice:** The non-competing team (forfeiter)

### Relationship to bye
| Option | Description | Selected |
|--------|-------------|----------|
| Keep separate | bye and walkover independent; match is normal OR bye OR walkover | ✓ |
| Walkover as bye variant | Enum byeType etc.; invariants collide (bye needs awayTeam=null) | |

**User's choice:** Keep separate

---

## Auto-Win Scoring

### Award timing
| Option | Description | Selected |
|--------|-------------|----------|
| Standings read-time, scores null | Exactly like bye; processMatch() awards; no DB scoreline | ✓ |
| Explicit forfeit scoreline on save | Set home/away score to a fixed forfeit value | |

**User's choice:** At standings read-time, scores null

### Forfeiter standings entry
| Option | Description | Selected |
|--------|-------------|----------|
| Counted loss + 0 points | addLoss() + 0 match points; both real teams appear normally | ✓ |
| No entry (like bye) | No win/loss recorded for forfeiter | |

**User's choice:** Counted loss + 0 points

### Point difference / Buchholz
| Option | Description | Selected |
|--------|-------------|----------|
| No — only Win/Loss + match points | No synthetic point difference, no Buchholz contribution | ✓ |
| Synthetic point difference | Credit winner a fictional score margin | |

**User's choice:** No — only Win/Loss + match points

---

## Admin Marking Flow

### Location
| Option | Description | Selected |
|--------|-------------|----------|
| In the match edit form | match-form-edit.html + MatchForm + MatchController edit | ✓ |
| Dedicated action on matchday-detail | Separate button/inline control per match row | |

**User's choice:** In the match edit form
**Notes:** Walkover is known after scheduling → edit is the natural place; bind via Form DTO.

### Team selection
| Option | Description | Selected |
|--------|-------------|----------|
| Dropdown: None / Home / Away | Single select; None = null = clear | ✓ |
| Checkbox + separate team select | Activate then pick team (JS toggle like bye) | |

**User's choice:** Dropdown — None / Home team / Away team

### Consistency rules
| Option | Description | Selected |
|--------|-------------|----------|
| walkover_team_id must be home/away; not combinable with bye | Service validation → BindingResult error + errorMessage flash | ✓ |
| DB FK only, minimal app validation | Rely on FK constraint alone | |

**User's choice:** Service-level validation (team ∈ {home, away}, not with bye)

---

## "w/o" Label — Semantics & Placement

### Label target
| Option | Description | Selected |
|--------|-------------|----------|
| Next to the non-competing team (forfeiter) | Label and data field point at the same team | ✓ |
| Next to the winning team | Sports-common but diverges from stored semantics | |

**User's choice:** Next to the forfeiter

### Standings views (multi-select)
| Option | Description | Selected |
|--------|-------------|----------|
| matchday-detail.html (admin) | Where bye already shows via .match-bye | ✓ |
| site/standings.html (public) | Public standings page | ✓ |

**User's choice:** Both admin matchday-detail and public site/standings

### Graphics (multi-select)
| Option | Description | Selected |
|--------|-------------|----------|
| match-results-render (scorecard) | Forfeiter slot shows "w/o"; adjust MatchResultsGraphicService | ✓ |
| lineup-render | "w/o" on forfeiter team | ✓ |
| provisional-scores-render | Usually not relevant, but requested | ✓ |

**User's choice:** All three graphics

---

## Walkover Match Races

| Option | Description | Selected |
|--------|-------------|----------|
| Remain, just unscored | Races/legs untouched; scoring skips them (no results) | ✓ |
| Delete races on walkover | orphanRemoval; destructive, harder to undo | |

**User's choice:** Remain, just unscored (non-destructive, reversible)

---

## Partial Results Precedence

| Option | Description | Selected |
|--------|-------------|----------|
| Walkover takes precedence, results untouched | processMatch() branches on walkover first, ignores scores/results | ✓ |
| Delete results on marking | Clean state but destructive | |
| Block marking when results exist | Validation prevents walkover until results removed | |

**User's choice:** Walkover takes precedence; results untouched (reversible)

---

## Discord Behaviour

| Option | Description | Selected |
|--------|-------------|----------|
| No special behaviour this phase | Walkover only changes persistence/scoring/label/graphics | ✓ |
| Walkover-specific Discord handling | Own logic (suppress/teaser); out of scope | |

**User's choice:** No special behaviour this phase (deferred)

---

## Claude's Discretion

- JPA mapping of `walkover_team_id` (raw UUID vs. `@ManyToOne`) — follow Match home/away convention.
- CSS class naming for the "w/o" badge — mirror `.match-bye` / Phase-108 empty-state styling.
- `.sql` vs. Java dialect-aware Flyway V17 — confirm during research (simple nullable FK column is `.sql`-friendly).

## Deferred Ideas

- Richer walkover model (dedicated points config, forfeit reasons) — out of scope (D-WO-Bye-Analogy).
- Walkover-specific Discord handling — own phase / future milestone.
- Destructive walkover variants (delete results/races) — considered and rejected.
