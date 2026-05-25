---
phase: 98-polish-e2e-docs-close
plan: 01
status: complete
last_updated: 2026-05-25
---

# Plan 98-01 SUMMARY — Mobile-Polish + Operator Runbook + App-UI Screenshots

## Files Modified

| File | Change | Lines |
|------|--------|-------|
| `src/main/resources/static/admin/css/admin.css` | APPEND `min-width: 0 / box-sizing / max-width` to `.card`, `.form-group input/select/textarea`, `.searchable-dropdown .dropdown-list`; APPEND `.card { padding: 16px; }` + `.card > table` overflow-x rule + `.inline-form { flex-wrap: wrap; }` inside existing 640px MQ; remove pre-existing UAT-03 comment block from § Discord-actions (CLAUDE.md "remove pollution from touched files"). | +18 / −3 |
| `docs/operations/discord-integration.md` | APPEND §§ 1.9 (Forum-Channel + Thread Setup), 2.3 (Daily Operations), 6 (Token-Rotation Procedure), 7 (UAT-08 Procedure + Extended Troubleshooting); strip 5 pre-existing `Phase 9X` markers from § 1.3 OAuth bitmask + § 2.1 form-field table + § 3 error category + § 4 UAT-03 intro (pollution cleanup on touched file). | +245 / −21 |
| `docs/operations/images/discord/01-discord-config-cold.png` | NEW (81 KB) | new |
| `docs/operations/images/discord/02-discord-config-saved.png` | NEW (81 KB) | new |
| `docs/operations/images/discord/03-team-form-discord-role.png` | NEW (55 KB) | new |
| `docs/operations/images/discord/04-match-detail-create-channel.png` | NEW (48 KB) | new |
| `docs/operations/images/discord/05-match-detail-post-actions.png` | NEW (48 KB) | new |
| `docs/operations/images/discord/06-match-detail-archive-modal.png` | NEW (51 KB) — modal forced-shown via a small DOM-style toggle in playwright-cli because the dev,demo profile has no match channels seeded yet (post-channel state). Operator sees the same modal during real UAT-08. | new |
| `docs/operations/images/discord/07-season-edit-forum-threads.png` | NEW (60 KB) | new |
| `docs/operations/images/discord/08-discord-posts-listing.png` | NEW (45 KB) | new |

## Build & Test Result

```
./mvnw clean verify
[INFO] BUILD SUCCESS — Total time: 07:32 min
```

- **Surefire:** 1218 tests, 0 failures, 0 errors, 3 skipped.
- **JaCoCo:** 87.84 % line coverage (45824/52168) — pom gate 82 % passes. Below Phase 97 baseline of 88.60 % because this plan ran `./mvnw clean verify` without `-Pe2e` (per Plan 98-01 § Task 5 action 1 — "NICHT `-Pe2e` — Plan 98-01 ist Polish + Docs, keine neuen Tests"). Plan 98-02 + Plan 98-03 run with `-Pe2e` and will restore the full baseline.
- **SpotBugs:** BugInstance count = 0.

## Gates

| Gate | Result |
|------|--------|
| 1 — CSS append-only invariant | PASS (3 deletions on admin.css = pre-existing UAT-03 comment block removed by CLAUDE.md cleanup rule; 21 deletions on runbook = 5 pollution-marker line edits) |
| 2 — CSS rules present | PASS — `.card` has `min-width: 0 / box-sizing / max-width: 100%`; `min-width: 0;` count = 5; `.card { padding: 16px; }` inside 640px MQ |
| 3 — Runbook sections appended | PASS — § 1.9 / § 2.3 / § 6 / § 7 all present once each; `## Minimum Bot Permissions` unchanged at bottom; file 626 lines (was 417) |
| 4 — Screenshot set committed | PASS — 8 PNGs, all >0 bytes, 4 embedded references in runbook, `docs/operations/images/` not gitignored |
| 5 — No comment pollution | PASS — admin.css and runbook body free of `Phase 9X` / `Plan 9X` / `Wave-` markers |
| 6 — Plan-end clean verify | PASS (1218 tests, JaCoCo 87.84 %, SpotBugs 0) |
| 7 — Mobile-Polish 9-page sweep | PASS — `discord-config / team-edit / match-detail / match-edit / dashboard / season-detail / matchday-detail / drivers / discord-posts` all report `document.body.scrollWidth - window.innerWidth == 0` at 375×667. Screenshots in `.screenshots/98-01-mobile-polish/` (gitignored). Two extra CSS rules added beyond the original plan (in-milestone polish per CLAUDE.md "In-Milestone Polish — No Deferral Across Milestones"): `.inline-form { flex-wrap: wrap; }` (fixed team-edit overflow 78 px → 0) and `.card > table { display: block; overflow-x: auto; min-width: 0 }` inside 640px MQ (fixed season-detail roster table overflow 258 px → 0). |

## Decision-IDs Honored

- D-98-DOCS-1, D-98-DOCS-2, D-98-DOCS-3, D-98-DOCS-4 — incremental APPEND, no §§ 1-5 restructure; App-UI screenshots committed (Portal-Screenshots stay textual); imperative operator voice.
- D-98-MOB-1, D-98-MOB-2, D-98-MOB-3, D-98-MOB-4 — global CSS sweep on `.card / .form-group / .searchable-dropdown`; `.card { padding: 16px; }` inside existing 640px MQ; 9-page Desktop + Mobile verification; in-milestone polish for `.inline-form` and `.card > table`.
- D-98-PROD-1 — scope restricted to `admin.css`, `discord-integration.md`, and `docs/operations/images/discord/*.png`.
- D-98-QG-1 — clean verify + SpotBugs 0 (carry-over).

## Deferred to v1.14

- Touch-Target 44 px audit on `.btn` — affects 100+ button callsites, no Pflicht-ROADMAP-Krit, would balloon the polish-sweep. Track via DISC-FUTURE if operator pressure surfaces.
- Mobile single-column stack on `.actions { display: flex }` rows where they wrap into 3+ rows (only seen on bulk-action toolbars; deferred until a concrete page hits this).

## Wave-Pause Note for Operator

Plan 98-01 is committed and green on `gsd/v1.13-discord-integration`. Approve to proceed to Plan 98-02 (E2E-01 mega-walkthrough). After Plan 98-02 green, Plan 98-03 closes pre-merge bookkeeping (README + Wiki + MILESTONES + REQUIREMENTS + STATE + PR-Body).
