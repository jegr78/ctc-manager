---
phase: 99
plan: 01
status: shipped
shipped_on: 2026-05-25
commit: pending
---

# Plan 99-01 — REQUIREMENTS.md prose-fix + audit cross-update

## Self-Check

| Gate | Expected | Actual | Status |
|------|----------|--------|--------|
| POST-01 prose V11 removed | `grep -c "Flyway V11 \`discord_post\` table"` = 0 | 0 | PASS |
| POST-01 prose V12 present | `grep -c "Flyway V12 \`discord_post\` table"` = 1 | 1 | PASS |
| FORUM-01 prose V12 removed | `grep -c "Flyway V12 adds \`seasons.discord"` = 0 | 0 | PASS |
| FORUM-01 prose V13 present | `grep -c "Flyway V13 adds \`seasons.discord_race_results_thread_id\`..."` = 1 | 1 | PASS |
| "Create new Thread" removed | `grep -c "Create new Thread" REQUIREMENTS.md` = 0 | 0 | PASS |
| operator-workflow note added | `grep -c "operator-workflow note"` = 1 | 1 | PASS |
| "list-existing + create-new + unlink" removed | `grep -c ...` = 0 | 0 | PASS |
| Audit doc — partial removed | `grep -c "satisfied (partial)"` = 0 | 0 | PASS |
| Audit doc — YAGNI verdict | `grep -c "YAGNI verdict 2026-05-25"` = 1 | 1 | PASS |
| Audit doc — Plan 99-05 refs | `grep -c "Plan 99-05"` ≥ 2 | 3 | PASS |
| Audit doc — Plan 99-01 refs | `grep -c "Plan 99-01"` ≥ 2 | 2 | PASS |
| src/ unchanged | `git diff --stat src/` empty | empty | PASS |

## Deliverables

- `.planning/REQUIREMENTS.md` — POST-01 row line 50: `Flyway V11` → `Flyway V12` (single substring fix).
- `.planning/REQUIREMENTS.md` — FORUM-01 row line 66: full acceptance rewrite — `Flyway V12 adds` → `Flyway V13 adds`; dropped `(c) "Create new Thread..." modal` sub-clause; renumbered to (a)/(b)/(c); appended `operator-workflow note: ...`; verification-tail trimmed from `list-existing + create-new + unlink + ...` to `list-existing + unlink + ...`.
- `.planning/v1.13-MILESTONE-AUDIT.md` — Requirements-Coverage row FORUM-01 (line 115): `satisfied (partial)` → `satisfied` + evidence-cell rewrite.
- `.planning/v1.13-MILESTONE-AUDIT.md` — Footnote line 127: `**FORUM-01 noted as "partial"** ...` → `**FORUM-01 noted as "satisfied"** ...` reflecting YAGNI cleanup outcome (Plan 99-01 + Plan 99-05).
- `.planning/v1.13-MILESTONE-AUDIT.md` — Tech-debt bucket 5 (line 202): rewritten to record the explicit YAGNI deletion + the operator-workflow.

## Key Changes

- 2 files changed (`.planning/REQUIREMENTS.md` + `.planning/v1.13-MILESTONE-AUDIT.md`); 5 insertions + 5 deletions.
- Zero src/ changes — pure Markdown-edits.
- D-06 spirit verified: `grep -nE "V1[123]" .planning/REQUIREMENTS.md` lists V12 next to `discord_post` (line 50) + V13 next to `seasons.discord_*_thread_id` (line 66); REQUIREMENTS.md never references V11 directly (the `discord_channel_archived_at` follow-up landed in Phase 94 without a dedicated REQ-prose line, so there is no off-by-one V11 reference to fix).

## Commit

`docs(99-01): correct Flyway version prose + FORUM-01 acceptance text` on `gsd/v1.13-discord-integration`.

## Addressed Decisions

D-01, D-03, D-04, D-05, D-06, D-19, D-24, D-26, D-27.
