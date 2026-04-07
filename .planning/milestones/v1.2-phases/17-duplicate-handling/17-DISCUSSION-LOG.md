# Phase 17: Duplicate-Handling - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 17-Duplicate-Handling
**Mode:** auto
**Areas discussed:** Conflict Detection Strategy, Conflict Resolution, MergeResult Reporting, RaceLineup Handling

---

## Conflict Detection Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Proactive check before reassignment | Query for existing target entry before each reassignment — clean, testable, explicit | [auto] |
| Try-catch DataIntegrityViolationException | Attempt reassignment, catch constraint violation on failure — reactive, harder to test | |
| Batch duplicate detection | Query all duplicates upfront before processing any FK table — efficient but more complex | |

**User's choice:** [auto] Proactive check before reassignment (recommended default)
**Notes:** Matches existing Phase 16 pattern of proactive validation (self-merge check). Existing repository methods already support the lookups needed.

---

## Conflict Resolution

| Option | Description | Selected |
|--------|-------------|----------|
| Delete source entry, keep target | When duplicate detected, remove source's entry — target's data preserved unchanged | [auto] |
| Keep source entry, delete target | Replace target's data with source's — loses target's existing data | |
| Merge fields from both entries | Combine data from both entries into one — complex, unclear which fields take precedence | |

**User's choice:** [auto] Delete source entry, keep target (recommended default)
**Notes:** Simplest approach. The target driver's existing entries are the "canonical" ones since the target is the surviving driver. Source entries are duplicates to be discarded.

---

## MergeResult Reporting

| Option | Description | Selected |
|--------|-------------|----------|
| Extend MergeResult with dropped counts | Add seasonDriversDropped, raceLineupsDropped, raceResultsDropped fields | [auto] |
| Single total dropped count | One aggregated dropped count — less granular | |
| No change to MergeResult | Only log dropped entries, don't report in return value | |

**User's choice:** [auto] Extend MergeResult with dropped counts (recommended default)
**Notes:** Phase 18 UI will need granular counts to display a meaningful success message. Keeping reporting per-table matches the existing MergeResult structure.

---

## RaceLineup Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Handle logical duplicates defensively | Check for same race+driver even without DB constraint | [auto] |
| Skip — no DB constraint means no conflict | Only handle tables with actual UniqueConstraints | |
| Add DB UniqueConstraint via Flyway migration | Add constraint + handle duplicates — more thorough but scope creep | |

**User's choice:** [auto] Handle logical duplicates defensively (recommended default)
**Notes:** RaceLineup has no UniqueConstraint but having two entries for the same driver in the same race is logically invalid. Defensive handling prevents data inconsistency. No Flyway migration — that would be new capability.

---

## Claude's Discretion

- Internal ordering of duplicate detection within the reassignment loop
- Whether to use a single loop with inline check or extract a helper method
- Exact field names for the new MergeResult counts

## Deferred Ideas

None — discussion stayed within phase scope
