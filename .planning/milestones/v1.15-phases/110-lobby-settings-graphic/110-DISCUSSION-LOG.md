# Phase 110: Lobby Settings Graphic - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 110-lobby-settings-graphic
**Areas discussed:** Discord Posting, Generate Gating & Scope, fest↔match-spezifisch Split, Weather Preset/Custom, Room Name

---

## Discord Posting (LOBBY-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Match channel, manual button (like Settings) | New `DiscordPostType.LOBBY_SETTINGS`, manual button, posts to match channel (mirror `postSettings`) | ✓ |
| Match channel, auto-post on event | `@TransactionalEventListener` AFTER_COMMIT auto-post (like TEAM_CARDS) | |
| Other target (announcement/forum) | Announcement webhook or forum thread instead of match channel | |

**User's choice:** Match channel, manual button (mirror Settings exactly).
**Notes:** Lobby settings are match-specific config → same destination and trigger as the Settings graphic.

---

## Generate Gating & Scope (LOBBY-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Disabled-with-hint like Settings, gate = Settings+Track only (no cards) | Mirror Settings UX, drop the team-card requirement; `canGenerateLobbySettings = hasAllSettings && track != null && !exists` | ✓ |
| Always active + error flash (handoff default) | Button always clickable, error flash on incomplete settings | |

**User's choice:** Disabled-with-hint like Settings; gate only on complete settings + track; NO team cards required.
**Notes:** UI consistency with the adjacent Settings button.

---

## fest ↔ match-spezifisch Split (LOBBY-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Adopt handoff proposal | ~14 marked `${v.*}` fields match-specific, rest fixed defaults; editor-adjustable | ✓ (base) |
| Minimal split | Only Room Name, Track, Laps, Weather, Time of Day match-specific | |

**User's choice:** Handoff proposal as base, with one refinement — Weather is Preset OR Custom and must be distinguished per setting; Category stays fixed `"—"`.
**Notes:** Drove the follow-up Weather question below.

---

## Weather Preset/Custom (LOBBY-01, no schema change)

| Option | Description | Selected |
|--------|-------------|----------|
| Prefix convention on existing `weather` field | Service parses prefix: "Preset…" → preset code into Preset Weather row, Custom="—"; "Custom…" → slot sequence into Custom Weather, Preset="—" | ✓ |
| Always Preset (drop Custom) | weather fills Preset row only; Custom fixed "—" | |
| New field via Flyway V18 | Dedicated customWeather + mode field (breaks LOBBY-01) | |

**User's choice:** Prefix convention. Preset → value (e.g. `S01`/`C04`) into Preset Weather, Custom Weather = "—". Custom → Preset Weather = "—", individual slots (e.g. `R01, ?, R04, C05, S11, ?, C01, R08`) into Custom Weather. Selection Method derived. No schema change.
**Notes:** Requires a small template tweak (D-07) — the handoff template hardcodes the Selection Method row and defaults Preset Weather to 'S01'.

---

## Room Name Format

| Option | Description | Selected |
|--------|-------------|----------|
| CTC – {year} – {matchday} – {home} vs. {away} (handoff) | e.g. "CTC – 2026 – MD4 – P1R vs. VEZ", short names | ✓ |
| Without teams: CTC – {year} – {matchday} | Shorter, no pairing | |

**User's choice:** Handoff format with team short names; falls back to "CTC – {year} – {matchday}" when teams absent.
**Notes:** —

---

## Claude's Discretion

- Exact `TemplateManageable` method bodies (1:1 mirror of `SettingsGraphicService`).
- `TemplatePreviewService.buildLobbySettingsContext()` shape (required so editor live-preview does not throw).

## Deferred Ideas

- Carbon-polish restyle of the other 12 render templates ("CTC Team Cards Redesign" handoff) — separate future phase.
- Dedicated `customWeather`/`weatherMode` schema field — rejected for this phase (LOBBY-01); prefix convention used.
