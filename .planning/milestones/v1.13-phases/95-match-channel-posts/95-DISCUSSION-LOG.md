# Phase 95: Match Channel Posts - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-22
**Phase:** 95-match-channel-posts
**Areas discussed:** Q-95-01 Team-Cards-Trigger, TEAM_CARDS PNG-Quelle, Settings/Lineups Race→Match-Bridge, POST-05 Schedule Auto-Edit-Hook

---

## Q-95-01 — Team-Cards-Trigger (Auto-Post bei Channel-Create vs. Manual-Button vs. Hybrid)

**Source:** Pre-discussion seed `95-DISCUSSION-INPUT.md` (UAT-04 retro 2026-05-22): operator wünscht 1-Klick "Create Channel + Post Team Cards" Auto-Flow.

| Option | Description | Selected |
|--------|-------------|----------|
| C: Hybrid (Auto-on-Create + Re-Post-Button) | `DiscordChannelService.createMatchChannel()` ruft am Ende `discordPostService.postTeamCards()` auf. Match-Detail behält permanent einen Re-Post-Button. Failure beim Auto-Post → WARN-Log + gelbe Badge "Channel created, Team Cards post failed — click Re-Post", Channel bleibt. | ✓ |
| B: Manual-Only (ROADMAP-Wortlaut) | POST-02-Button bleibt einziger Trigger. Operator macht 2 Klicks (Create Channel → Post Team Cards). | |
| A: Auto-on-Create + kein Manual-Button | Channel-create postet sofort. Re-Post nur über /admin/discord/posts. | |
| Defer auf Phase 98 Polish | Phase 95 ships manual-only, Q-95-01 wird zu DISC-FUTURE-Ticket bzw. Phase-98-Polish-Item. | |

**User's choice:** C (Hybrid)
**Notes:** Symmetriebruch zu POST-03/04/05 ist begründet — Team Cards strukturell verknüpft mit Channel-Identität (`md{N}-{home}-vs-{away}` enthält dieselben Teams). 1-Klick-Happy-Path matters für UAT-04-Operator-UX.

### Follow-up: Auto-Post-Failure-Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Channel bleibt + gelbe Badge + WARN-Log | Channel-create-Transaktion committed. Team-Cards-Failure → WARN + Flash-Badge + errorCategory + Re-Post-Button bleibt sichtbar. Recoverable. | ✓ |
| Channel-Create rollt zurück (DELETE channel + DB-rollback) | Strenger Atomicity-Garant; teuer (Channel-Quote verschwendet, 50/category Discord-Limit). | |
| Channel bleibt, Pre-Flight cardExists()-Check VOR Discord-Roundtrip | Wenn beide PNGs vorhanden → Post; sonst Channel bleibt + Badge "Generate team cards first". | |

**User's choice:** Channel bleibt + gelbe Badge + WARN-Log
**Notes:** Channel ist die teure Ressource. Team-Cards-Recovery via Button ist preisgünstig.

---

## TEAM_CARDS PNG-Quelle (Pre-Gen / Auto-Gen / Refresh-Strategy)

**Context:** `TeamCardService.generateCard(SeasonTeam)` cached PNGs unter `data/{profile}/team-cards/{seasonId}/{teamId}.png`. Match-Post braucht 2 PNGs (home + away).

| Option | Description | Selected |
|--------|-------------|----------|
| B': Auto-Gen wenn fehlt + "Refresh Team Cards"-Link | `postTeamCards()` Pre-Flight prüft cardExists(); fehlt → ruft generateCard() synchron auf (~10-30s Playwright). Match-Detail bekommt zusätzlich kleinen "↻ Refresh"-Link der Cards regeneriert + automatisch re-postet. | ✓ |
| A: Pre-Gen-only — Badge wenn fehlt | Pre-Flight; cardExists==false → Flash-Badge "Generate team cards first" + Link zu /admin/tools/team-cards. Sauberer Layering-Cut. | |
| B: Auto-Gen-on-Demand (kein Refresh-Link) | Auto-Gen wenn fehlt, Cache-Hit sonst. Logo-/Lineup-Update-Edge-Case: 2-Klick (separate Regenerate + Re-Post). | |
| C: Always-Regenerate | Jeder Post rendert Cards neu. Garantierte Frische, aber Playwright-Kosten verdoppeln sich pro Klick. | |

**User's choice:** B' (Auto-Gen + Refresh-Link)
**Notes:** Operator-Happy-Path bleibt 1-Klick; Refresh-Link macht den Logo-/Lineup-Update-Edge-Case explizit ohne `/admin/tools/team-cards`-Detour.

---

## Settings/Lineups Race→Match-Bridge

**Context:** `SettingsGraphicService.generateSettings(Race)` + `LineupGraphicService.generateLineup(Race)` arbeiten pro Race. Match hat 3-4 Races mit unterschiedlichen Settings/Tracks.

| Option | Description | Selected |
|--------|-------------|----------|
| D: Multipart-Bundle (1 Post pro Type, N Attachments) | Pro Match: 1 SETTINGS-Post mit files[0..N-1]=settings-race-{i}.png; 1 LINEUPS-Post mit files[0..N-1]=lineups-race-{i}.png. ROADMAP-konform (1 row pro Type). Re-Post via neuem `editMessageWithAttachments()` Multipart-PATCH (~30 LOC Scope-Add in Plan 95-01). | ✓ |
| A: N Buttons / N Posts (sequenziell) | Match-Detail bekommt "Post Settings Race 1..N" Buttons. N rows in discord_post pro Match pro Type. PK müsste (channel_id, post_type, race_id) statt match_id sein. Bricht ROADMAP-Success-Crit 1. | |
| C: Composite Match-PNG | Neuer MatchSettingsGraphicService + Thymeleaf-Template das alle N Races in einem Grid-PNG kombiniert. Template-Aufwand + Mobile-Display-Risk + Pixel-Positionierung. | |
| B: Buttons auf Race-Detail-Page statt Match-Detail | Settings/Lineups-Buttons wandern auf Race-Detail. Bricht POST-03 ROADMAP-Wortlaut ("on Match-Detail") + Navigation-Friction. | |

**User's choice:** D (Multipart-Bundle)
**Notes:** Discord-Multipart-Limit 10 Attachments + 25 MB > CTC-Footprint (4 Races à ~200KB). Saubere ROADMAP-Konformität.

### Follow-up: Partial-Data-Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-Flight Pflicht: alle Races müssen Daten haben | Button-Visibility: `visible AND all races have settings/raceLineup`. Sonst Tooltip "Configure all races first". Saubere Semantik. | ✓ |
| Pragmatisch: K Attachments posten (1≤K≤N) | Post enthält nur fertige Races. Re-Post fügt restliche nach. Iterativer Workflow. | |
| Hybrid: K Attachments + "{K}/{N}"-Badge | Wie pragmatisch, aber Match-Detail zeigt nach Post "{K}/{N} races posted". | |

**User's choice:** Pre-Flight Pflicht
**Notes:** "Post Settings" garantiert vollständige Daten. Vermeidet Re-Post-Confusion (Operator weiß ohne Channel-Check, dass alle Races drin sind).

---

## POST-05 Schedule Auto-Edit-Hook

**Context:** ROADMAP-Krit 4: "Schedule embed auto-edits on Match-Form save when any of lobbyHost / raceDirector / streamer changes AND a SCHEDULE post exists; no edit fires when fields are unchanged."

| Option | Description | Selected |
|--------|-------------|----------|
| B: MatchService.save() Pre/Post-Diff + DiscordPostService.autoEdit() | MatchService lädt Entity vor Save, vergleicht 3 Felder, ruft nach Save `autoEditScheduleIfNeeded()` wenn changes && SCHEDULE-post exists. Konform CLAUDE.md "Controllers thin". | ✓ |
| C: Spring ApplicationEvent (MatchScheduleChangedEvent) | Loose Coupling via Event-Bus. Sauberste Architektur, aber neuer Pattern ohne 2. Konsument — YAGNI in v1.13. | |
| A: MatchController inline-Diff | Controller macht Diff vor save() und ruft DiscordPostService direkt. Bricht "Keep Controllers Thin" [[feedback-orchestrator-discipline]]. | |
| D: @PostUpdate Entity-Listener auf Match | JPA-Lifecycle-Listener. Hidden control-flow, schlechte Test-Visibility. | |

**User's choice:** B (MatchService-Hook)
**Notes:** Domain-Layer-Hook ist die richtige Stelle. ApplicationEvent-Refactor kann später passieren wenn Phase 97 MATCH_PREVIEW-Auto-Edit den 3. Konsumenten bringt.

### Follow-up: Edit-Trigger-Felder

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Match-Schedule-Felder (lobbyHost / raceDirector / streamer) | ROADMAP-Wortlaut. Race-Date-Verschiebung → manueller Re-Post nötig. | ✓ |
| + Race.dateTime-Min-Änderung triggert ebenfalls | RaceService.save() ruft denselben autoEditScheduleIfNeeded(). Genauer, aber 2 Trigger-Stellen + RaceService bekommt Discord-Coupling. | |
| Hash-basiert über alle Schedule-Eingaben | Universal, aber Race-Save muss auch geprüft werden. | |

**User's choice:** Nur Match-Schedule-Felder
**Notes:** Minimaler Coupling-Surface — RaceService bleibt Discord-frei in v1.13. Race-Reschedule-After-MD-Start ist seltener Edge-Case.

---

## Claude's Discretion

Listed in 95-CONTEXT.md `<decisions>` § Claude's Discretion. Summary:

- `DiscordPostType` enum-location (`org.ctc.discord.model` vs. `org.ctc.discord.dto`).
- `DiscordPostRef` sealed-hierarchy vs. plain-record.
- `/admin/discord/posts` Navigation-Eintrag (Sidebar / Tab-Bar / Direct-URL).
- `DiscordPostFilterForm` Field-Types (Dropdown vs. Snowflake-String).
- Race-Sort-Order in Multipart-Bundle (raceNumber vs. dateTime).
- SCHEDULE-Embed-Color (Discord-Default vs. CTC-Branding-Hex).
- Re-Edit vs. Re-Post Semantik auf /admin/discord/posts-Page.

---

## Deferred Ideas

- **Race.dateTime-Änderung triggert Schedule-Embed-Auto-Edit** — DISC-FUTURE Ticket für v1.14.
- **Spring ApplicationEvent für Discord-Auto-Edit-Hooks** — YAGNI in v1.13; Refactor wenn 3. Konsument auftaucht (Phase 96/97).
- **K-von-N Settings/Lineups-Posting (iterativer Workflow)** — falls Operator-Feedback dies fordert.
- **Bulk-Re-Post-Button auf `/admin/discord/posts`** — v1.14-Wunsch.
