---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 06
status: complete
commits:
  - da76a999 docs(101): update PROJECT.md wire contract to 26-entity scope + SCHEMA_VERSION 2
  - 81b49215 docs(101): flip STATE.md baselines to SCHEMA_VERSION 2 / 26 entities + Roadmap event
  - 608b03fc docs(101): add Backup & Restore semantics § to discord-integration runbook (T-101-01 + T-101-02)
  - 1d62e1ab docs(101): README — 24 → 26 entity tables for Discord-state inclusion
requirements_addressed:
  - D-06
  - D-09
  - D-14 (documentation half — guard-test flips already shipped in Plans 01 + 02)
---

# Plan 101-06 — Documentation deliverable (PROJECT + STATE + runbook + README)

## Outcome

Four documentation surfaces updated to reflect the v1.13 backup wire-contract change.
No code changes; no automated tests.

| File | Change |
|------|--------|
| `.planning/PROJECT.md` | Core-stack line: 24 → 26 entities, SCHEMA_VERSION 1 → 2, package filter extended, lenient IN (1, 2) note. New "v1.13 Phase 101 update" sub-paragraph appended after paragraph 4 of § Backup Wire Contract documenting the 26-entity scope, discord_post topo-sort pin, v1-backup compatibility, and T-101-01/T-101-02 implications. Historical retrospective bullets (Phase 72/77/82) untouched. |
| `.planning/STATE.md` | `BackupSchema.SCHEMA_VERSION` baseline 1 → 2. `EXPORT_ORDER` size baseline 24 → 26. "revisited in Phase 101" marker removed. New 2026-05-26 Roadmap-Evolution bullet documenting Phase 101 close. |
| `docs/operations/discord-integration.md` | New § 8 "Backup & Restore semantics" with three sub-sections: 8.1 single-guild operation (cross-guild orphan-recovery flow), 8.2 webhook_token secrecy (operator-side filesystem access control as v1.13 mitigation), 8.3 v1-backup compatibility (pre-v1.13 backups restorable; Discord tables empty after; V8-V15 columns NULL). |
| `README.md` | Both user-facing "24 entity tables" mentions updated to "26 entity tables" with parenthetical note on the 2 Discord-state additions and lenient IN (1, 2) acceptance. |

## Verification

- `grep -c "26-entity\|SCHEMA_VERSION = 2\|v1.13 Phase 101 update" .planning/PROJECT.md` returns ≥ 3 hits.
- `grep "SCHEMA_VERSION.*\*\*2\*\*" .planning/STATE.md` returns 1 hit.
- `grep "26 entities" .planning/STATE.md` returns ≥ 1 hit.
- `grep "revisited in Phase 101" .planning/STATE.md` returns 0 hits (marker removed).
- `grep "2026-05-26.*Phase 101 closed" .planning/STATE.md` returns 1 hit.
- `grep "Backup & Restore semantics" docs/operations/discord-integration.md` returns 1 hit.
- `grep -c "24 entity tables" README.md` returns 0; `grep -c "26 entity tables" README.md` returns 2.
- `wc -l docs/operations/discord-integration.md` reports 746 lines (was 674, +72 — within plan budget of 50-80).

## CLAUDE.md "Documentation Maintenance" — third surface

The GitHub Wiki update is a manual operator step after milestone PR merge. Per
CLAUDE.md "Every feature release updates three places", the operator should copy the
new § 8 from `docs/operations/discord-integration.md` to the corresponding wiki page
(no automation in this repo can push to the wiki). Captured here as a post-merge
operator action.

## Threat-Model Notes

- **T-101-01 (webhook_token in backup ZIP):** the new § 8.2 documents the operator-side mitigation (filesystem access control) and explicitly states v1.13 ships NO at-rest encryption.
- **T-101-02 (cross-guild restore orphans):** the new § 8.1 documents the undefined behaviour and the four-step recovery flow.
- No code-side surface change in this plan — purely documentation.

## Scope Fence Preserved

- All Flyway migrations untouched.
- Historical retrospective bullets in PROJECT.md (lines 151, 159, 171) untouched — they correctly report state-at-that-time.
- Code files untouched (no src/ changes).
- CLAUDE.md untouched (backup discipline already lives there).
- Other PROJECT.md, STATE.md, README.md, runbook sections untouched.
- Wiki not pushed (no in-repo tooling for that).

## Phase Status

Phase 101 is now documentation-complete. All 6 plans landed atomic commits on
`gsd/v1.13-discord-integration`. Next steps: `/gsd-verify-work 101` to validate
phase goal achievement, then `/gsd-ship 101` or milestone-close depending on the
remaining v1.13 phases.
