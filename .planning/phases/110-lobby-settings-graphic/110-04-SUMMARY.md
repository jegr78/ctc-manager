---
phase: 110-lobby-settings-graphic
plan: 04
status: complete
requirements: [LOBBY-03]
requirements-completed: [LOBBY-03]
---

# Plan 110-04 Summary: Discord LOBBY_SETTINGS Post Type

## What was built

1. **`DiscordPostType.LOBBY_SETTINGS`** — new enum value (after `SETTINGS`).

2. **`DiscordPostService.postLobbySettings(Match)`** — `@Transactional`, mirrors `postSettings`:
   guards `matchHasCompleteSettings`, posts via `postRaceBundle(match, LOBBY_SETTINGS,
   "lobby-settings-race-", race -> readRaceGraphic(lobbySettingsGraphicService.generateLobbySettings(race)))`
   to the **MATCH channel** (D-01 — manual, not announcement/forum/auto-post). The new
   `LobbySettingsGraphicService` dependency was wired through the **explicit constructor**:
   field + constructor parameter + assignment + added to the `EI_EXPOSE_REP2`
   `@SuppressFBWarnings` justification bean list (no `@SuppressWarnings("all")`).

3. **`MatchController`** — `POST /{id}/post-lobby-settings` (success flash "Lobby settings posted.",
   `applyErrorFlash` for `BusinessRuleException` + `DiscordApiException`, redirect).

4. **`MatchService.buildDetailModel`** — `lobbySettingsPost` via `findMatchPost(LOBBY_SETTINGS)`.
   Lobby button reuses the existing `matchHasCompleteSettings` flag (same prerequisite).

5. **`match-detail.html`** — Post / Re-Post / disabled button states mirroring the Settings post
   (`data-testid` post-lobby-settings / -disabled / repost-lobby-settings). admin.css only.

## Deviation / cross-file impact

- **Five test callsites fixed (in scope):** `DiscordPostService` has a hand-written constructor;
  five unit tests construct it directly with positional mocks. `grep -rn "new DiscordPostService("`
  found all five — each got a `mock(LobbySettingsGraphicService.class)` argument after the
  settings mock (plus the import where needed). No exact-`DiscordPostType.values()`-count
  assertion exists (`DiscordPostFilterControllerIT` only does `attributeExists("postTypes")`),
  so the new enum value is safe.

## Decisions honored

- D-01: manual button, MATCH-channel target, not announcement/forum/auto-post. Re-post edits the
  existing message idempotently via `postRaceBundle`.

## Verification

- `./mvnw clean test-compile` — succeeds (explicit constructor in sync; the sealed
  `DiscordPostRef` switch is unaffected — it switches over `DiscordPostRef`, not `DiscordPostType`).
- Endpoint/symbol audit: `matchHasCompleteSettings`, `postRaceBundle`, `readRaceGraphic`,
  `applyErrorFlash`, `findMatchPost`, `findById` all pre-exist.
- SpotBugs `EI_EXPOSE_REP2` justification updated (full gate runs at phase end / `clean verify`).
- Live Discord post + button render verified at phase end via running app / plan 110-05.

## Commits

- `771bd905` feat(110): add LOBBY_SETTINGS Discord post type + postLobbySettings
- `3f8487ae` feat(110): wire post-lobby-settings endpoint + match model attr
- `6ba29f7b` feat(110): add Post Lobby Settings button to match-detail

## Self-Check: PASSED
