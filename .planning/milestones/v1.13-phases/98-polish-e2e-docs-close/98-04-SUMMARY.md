---
phase: 98-polish-e2e-docs-close
plan: 04
status: complete
gap_closure: true
last_updated: 2026-05-25
---

# Plan 98-04 SUMMARY — Schedule-Embed Layout Polish (inline:false)

## Origin

Surfaced during UAT-08 Stage 5 verification (2026-05-25): the Match
Schedule embed in the match-channel renders 4 fields all with
`inline: true`, so Discord packs 3 fields into row 1 and the 4th
into row 2. The Date field is the widest value
(`Tuesday, 26 May 2026 at 19:00 (in a day)`), which makes row 1
visually asymmetric next to the short Lobby Host + Race Director
values.

Operator decision 2026-05-25 (Option A): all 4 fields
`inline: false` — one field per row.

Tracked in `STATE.md § Deferred Items` as `ui_debt` with
in-milestone binding. Pre-existing PATCH-on-edit behaviour
(`postOrEdit` for the announcement-webhook embed) is preserved; the
layout-flip is a one-byte-per-field change.

## Files Modified

| File | Change |
|------|--------|
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | `buildSchedulePayload(...)` — 4 EmbedField constructions flipped `inline=true` → `inline=false` |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java` | Added `equalTo` import + 4 `withRequestBody(matchingJsonPath("$.embeds[0].fields[N].inline", equalTo("false")))` invariant-guard assertions on the existing happy-path test `givenRaceWithDateTime_whenPostSchedule_thenJsonPostWithEmbedContaining4Fields()` |
| `.planning/STATE.md` | `§ Deferred Items` — `ui_debt` row for the Schedule-Embed layout flipped from PENDING to RESOLVED with commit ref |

## Verification

- **Targeted IT** — `./mvnw clean verify -Pe2e -Dit.test=DiscordPostServiceScheduleIT -DfailIfNoTests=false` exit 0; the 4 new `inline = false` assertions on the happy-path test were enforced (failed first with `inline=true` in source, passed after the source flip — TDD-Red/Green-Refactor cycle).
- **Full verify deferred** — operator decision 2026-05-25 to bundle the full `./mvnw clean verify -Pe2e` with Plans 98-05 + 98-06 (matchday-level announcement-channel triggers) since all three live in the same `DiscordPostService` class.
- **Live Re-Post deferred** — dev server (PID 50909) still has the pre-flip classes loaded; live re-post test bundled with the post-98-05/06 full-verify cycle, where a single Re-Post will PATCH the existing schedule-embed (same `messageId=1508418568407224370`) and Discord will render with the new layout.

## Decisions Honored

- **In-milestone polish** (CLAUDE.md "GSD Workflow Discipline") — surfaced during UAT-08, closed in same milestone before `/gsd-complete-milestone v1.13`.
- **No comment pollution** — no `// Plan 98-04` markers in source.
- **No content-rewrite on shared files** — the IT-edit is pure APPEND of 4 assertion lines + 1 import line; no existing assertion was changed.
- **WireMock query-param + body-part discipline** (CLAUDE.md "Build & Test Discipline") — used `matchingJsonPath` for the JSON-embed body, consistent with the existing assertions.
- **Operator visual approval** is not part of this plan (the inline-flip is a 1-byte logic change; the Discord-side re-render after Re-Post will be the operator's pass/fail signal in the post-98-05/06 UAT round, tracked as Stage 5c).

## Out of Scope

- The `:ADR:` / `:VRX:` server-emoji upload by operator — already tracked separately in the UAT-08 closeout note.
- The matchday-level announcement-channel triggers (MATCHDAY_PAIRINGS + MATCHDAY_SCHEDULE) — covered by separate Plans 98-05 + 98-06 going through the full `/gsd-discuss-phase 98` → `/gsd-plan-phase` → `/gsd-execute-phase` workflow per operator decision 2026-05-25.

## Commit

`docs(98-04): schedule-embed layout polish — fields inline:false (one per row)`
