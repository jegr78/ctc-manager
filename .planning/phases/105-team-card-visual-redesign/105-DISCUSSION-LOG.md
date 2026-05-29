# Phase 105: Carbon HUD Graphics Redesign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-29
**Phase:** 105-Carbon-HUD-Graphics-Redesign (dir slug: team-card-visual-redesign)
**Areas discussed:** Handoff delivery, Scope, Process, TeamCardService color patch, raceLabel behavior, Extra-template coverage, Phase structure & verification

---

## Handoff Delivery (process gate)

The external Claude-Design handoff was the ROADMAP-locked precondition for starting this phase. First delivery attempt was a URL (`https://api.anthropic.com/v1/design/h/9lC7gQdDvjWHHDYX_61CGA`) — returned plain HTTP 404 via WebFetch and curl. User then provided a local zip (`~/Downloads/CTC Team Cards Redesign-handoff.zip`). Extracted, README + HANDOFF.md + DESIGN-NOTES.md read; canonical reference copied into `design-handoff/`.

---

## Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Team card only (Phase 105 as scoped) | Just `team-card-render.html` + optional service patch | |
| All 12 templates | Full Carbon redesign: card + composites + matchday/list + overlay | ✓ |
| Team card now, rest separate phase | Split across phases | |

**User's choice:** All 12 templates.
**Notes:** "Ja, der Scope hat sich auf alle Templates erweitert. Das war bewusst nachdem die Claude Design Sitzung so gute Ergebnisse und Vorschläge geliefert hat." Deliberate expansion.

---

## Process

| Option | Description | Selected |
|--------|-------------|----------|
| GSD cycle: CONTEXT.md first | discuss → CONTEXT.md → plan-phase → execute-phase | ✓ |
| Direct inline implementation | Swap templates now, patch service, verify via playwright-cli | |

**User's choice:** GSD cycle, CONTEXT.md first.

---

## TeamCardService color patch (Area 1)

| Option | Description | Selected |
|--------|-------------|----------|
| Apply patch | `accentVisColor` + `onPrimaryColor` via `relativeLuminance`; fixes AHR #000 & bright primaries | ✓ |
| CSS fallback only | No Java change; graceful CSS fallback, minor edge-case degradation | |

**User's choice:** Apply patch.

---

## raceLabel behavior (Area 2)

| Option | Description | Selected |
|--------|-------------|----------|
| Conditional | Set `raceLabel` only for > 1-race matches; `.race-chip` hides on single-race | ✓ |
| Keep current | Always `"Race N"`; chip always visible | |

**User's choice:** Conditional. Existing ProvisionalScores IT must be updated.

---

## Extra-template coverage (Area 3)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend all 4 by analogy | `matchday-pairings` + 3 `playoff-round-*` restyled to Carbon (no handoff ref) | ✓ |
| Only matchday-pairings | playoff-round templates left old | |
| Leave all 4 old | Only the 12 handoff templates restyled | |

**User's choice:** Extend all 4 by analogy. No handoff reference for these → explicit playwright-cli verification required.

---

## Phase structure & verification (Area 4)

| Option | Description | Selected |
|--------|-------------|----------|
| Re-scope Phase 105, plan-splits | One phase "Carbon HUD Graphics Redesign", CARD-03/04 added, ~4 plan groups, wave execution, grouped verification | ✓ |
| Split into multiple phases | 105/106/107 separate discuss/plan/execute cycles | |

**User's choice:** Re-scope Phase 105 + plan-splits.

## Claude's Discretion

- Exact mapping of handoff `matchday-overview-render.html` ("Pairings/Seeds") onto repo `matchday-overview` vs `matchday-pairings` — resolve in research via `AbstractMatchdayGraphicService` subclasses.
- Per-plan task granularity and wave grouping within the 4 groups.

## Deferred Ideas

None — discussion stayed within the (deliberately expanded) scope. The 4 analogy templates were folded into scope, not deferred.
