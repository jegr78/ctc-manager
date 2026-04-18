# Phase 27: Restore Matchday/Result Seed Pipeline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-10
**Phase:** 27-restore-matchday-result-pipeline
**Mode:** Auto (all decisions auto-selected with recommended defaults)
**Areas discussed:** Data Restoration Strategy, Season Format Coverage, Scoring Integration, Test Coverage

---

## Data Restoration Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Restore from git history | Adapt code from commit 0396427 for fictive teams | ✓ |
| Rewrite from scratch | Build new pipeline without reference to old code | |

**User's choice:** [auto] Restore from git history (recommended default)
**Notes:** Gap closure phase — restoring known-working code is lower risk than rewriting

---

## Season Format Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| All three formats | League + Swiss + Round Robin (matches original) | ✓ |
| League only | Simpler, covers one format | |

**User's choice:** [auto] All three formats (recommended default)
**Notes:** DATA-04, DATA-05, DATA-06 each require a different format

---

## Scoring Integration

| Option | Description | Selected |
|--------|-------------|----------|
| ScoringService integration | Use calculatePoints() + aggregateMatchScores() | ✓ |
| Hard-coded points | Faster to implement, less realistic | |

**User's choice:** [auto] ScoringService integration (recommended default)
**Notes:** DATA-07 requires actual scoring system usage

---

## Test Coverage

| Option | Description | Selected |
|--------|-------------|----------|
| Per-format integration tests | Separate test per season format + scoring test | ✓ |
| Single aggregate test | One test checking all formats together | |

**User's choice:** [auto] Per-format integration tests (recommended default)
**Notes:** Matches DATA-04 through DATA-07 requirement granularity

---

## Claude's Discretion

- Matchday naming convention
- Race result count per race
- Position assignment strategy

## Deferred Ideas

None
