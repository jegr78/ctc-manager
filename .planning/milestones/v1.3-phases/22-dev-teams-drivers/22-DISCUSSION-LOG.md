# Phase 22: Dev Teams & Drivers - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-09
**Phase:** 22-dev-teams-drivers
**Areas discussed:** Team naming, Sub-team structure, Driver naming, Team card generation, Data replacement strategy
**Mode:** --auto (all decisions auto-selected)

---

## Team Naming Convention

| Option | Description | Selected |
|--------|-------------|----------|
| Racing-themed fictive names | Realistic racing team names like "Velocity Racing" | ✓ |
| Generic test names | Simple names like "Alpha Team", "Team B" | |
| Mixed approach | Some themed, some generic | |

**User's choice:** Racing-themed fictive names (auto-selected as recommended default)
**Notes:** More realistic test data for visual verification

---

## Sub-Team Structure

| Option | Description | Selected |
|--------|-------------|----------|
| 2-3 parents with 2-3 sub-teams each | Matches ROADMAP requirement exactly | ✓ |
| More parents with fewer sub-teams | Wider but shallower | |
| Fewer parents with more sub-teams | Deeper hierarchy testing | |

**User's choice:** 2-3 parents with 2-3 sub-teams each (auto-selected as recommended default)
**Notes:** Directly matches ROADMAP SC-2 success criteria

---

## Driver Naming Style

| Option | Description | Selected |
|--------|-------------|----------|
| Realistic-sounding fictive names | Full first + last names that look real | ✓ |
| Clearly fake names | Obviously test names like "Driver_01" | |
| Gaming-style handles | PSN-style names | |

**User's choice:** Realistic-sounding fictive names (auto-selected as recommended default)
**Notes:** Tests display formatting realistically

---

## Team Card Generation Timing

| Option | Description | Selected |
|--------|-------------|----------|
| At seed time during DevDataSeeder | Cards generated on startup | ✓ |
| On-demand when pages are viewed | Lazy generation | |
| Pre-generated static files | Checked into resources | |

**User's choice:** At seed time during DevDataSeeder (auto-selected as recommended default)
**Notes:** Ensures cards visible immediately on teams list page

---

## Data Replacement Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Replace real data with fictive | Full replacement in TestDataService | ✓ |
| Add fictive alongside real | Keep both datasets | |
| Separate seeder class | New class for fictive data | |

**User's choice:** Replace existing real team data with fictive data (auto-selected as recommended default)
**Notes:** Avoids confusion, aligns with requirement "entirely fictive names"

---

## Claude's Discretion

- Specific team names, colors, and driver names
- Logo generation approach
- Seeding order details
