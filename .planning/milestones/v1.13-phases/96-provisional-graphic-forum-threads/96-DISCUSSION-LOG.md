# Phase 96: Provisional Graphic + Forum Threads - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-23
**Phase:** 96-provisional-graphic-forum-threads
**Areas discussed:** Provisional-Scores Layout-Quelle, Forum-Channel-Webhook-URL-Strategie, Provisional Scores Target-Channel, Auto-Unarchive + Re-Archive Verhalten, Plan-Decomposition

---

## Provisional-Scores Layout-Quelle

### Turn 1: Woher stammt die visuelle Vorlage für das Provisional-Scores PNG?

| Option | Description | Selected |
|--------|-------------|----------|
| Google-Sheets-URL teilen | User teilt URL/Screenshot; pixel-genaue Iteration via playwright-cli | |
| Anlehnung an MatchResultsGraphic | Kein externes Mockup; Layout aus match-results-render.html + ResultsGraphicService-Konventionen ableiten | ✓ |
| ASCII-Spec im Discuss schreiben | Layout textuell jetzt in CONTEXT.md definieren | |
| Aktuelles Screenshot pasten | User hängt PNG/JPG des heutigen Sheet-Screenshots an | |

**User's choice:** Anlehnung an MatchResultsGraphic
**Notes:** Captured as D-96-GRX-1. Iterative playwright-cli per [[feedback-graphic-pixel-positioning]] + [[feedback-graphic-design-iteration]].

### Turn 2: Wie unterscheidet sich das Provisional-Layout vom finalen MatchResults-PNG?

| Option | Description | Selected |
|--------|-------------|----------|
| Gleicher Aufbau, Provisional-Banner | Match-card + Race-Zeilen + Totals + offene Races als '-' / 'TBD' + Banner | |
| Nur abgeschlossene Races | Nur abgeschlossene Races als Zeilen + Running-Sum + Banner | |
| Slim-Variante mit Driver-Liste | Reduzierte Variante, kleinere Bildhöhe | |
| Identisch zu MatchResults | Exakt gleich, kein Banner | |
| **Other (User-Freitext)** | "ähnliches Layout wie die Race Results Grafik mit Team Cards und Blöcken je Team. Gesamtpunkte sollten weiterhin sichtbar sein. Statt nur die Punkte sollten auch die Positionen des Quali und Rennens + FL enthalten sein. Kleiner Umbau nötig." + 2 Reference-Screenshots im Chat | ✓ |

**User's choice:** Other — per-Team-Block-Layout mit per-Driver-Detail (Driver | Position | Quali | FL | Pts-Race | Pts-Quali | Pts-FL | Total) + Overall-Row pro Block
**Notes:** 2 Reference-Screenshots vom User attached in Chat 2026-05-23: (1) heutiges Google-Sheets-Workflow-Screenshot mit AHR 1 + TNR A Blöcken; (2) bestehende Race-Detail Results-Tabelle des CTC-Managers. Plan 96-01 legt zur Iteration eine Kopie unter `.screenshots/96-01/provisional-reference.png` ab.

### Turn 3: Wann/Wie wird die Provisional-Grafik gepostet?

| Option | Description | Selected |
|--------|-------------|----------|
| Nur die letzte Race | Detail-Aufschlüsselung der zuletzt abgeschlossenen Race | |
| Alle abgeschlossenen Races stacked | N Blöcke pro Team (eine pro abgeschlossener Race), stacked vertikal | |
| Aggregat pro Driver | Kumulativ über alle Races, Position/Quali/FL entfallen | |
| Latest Race + Match-Total-Badge | Letzte Race im Detail + kumulativer Match-Score im Header | |
| **Other (User-Freitext)** | "Je Rennen eine eigene Provisional Grafik. Eine Komplettgrafik wird nicht benötigt. Nur für jedes einzelne Rennen" | ✓ |

**User's choice:** Other — per-Race-PNG, keine Match-Aggregation
**Notes:** Captured as D-96-GRX-1a. Each completed race produces its own PNG.

### Turn 4: Wo lebt der 'Post Provisional Scores'-Button?

| Option | Description | Selected |
|--------|-------------|----------|
| Auf Race-Detail (per Race) | Race-Detail bekommt 2 Discord-Buttons | |
| Match-Detail mit per-Race-Zeile | Match-Detail bekommt einen neuen Sub-Bereich 'Per-Race Posts' | |
| Beide Orte gespiegelt | Race-Detail UND Match-Detail | |
| Multipart: 1 Post mit allen abgeschlossenen Races | Eine Button-Klick auf Match-Detail postet N PNGs als Multipart-Bundle | ✓ |

**User's choice:** Multipart-Bundle (Option 4) + Zusatz "Provisional Results werden nur im Match Day Kanal gepostet. Diese landen nie im Results Forum Thread"
**Notes:** Captured as D-96-GRX-1b + D-96-GRX-1c. Match-channel only, never forum-thread. FORUM-02 Wording "applies to both" wird als Auto-Unarchive-Logik interpretiert.

---

## Forum-Channel-Webhook-URL-Strategie

### Turn 1: Wie soll der Bot/Operator an die Webhook-URLs der race-results + standings Forum-Channels kommen?

| Option | Description | Selected |
|--------|-------------|----------|
| Bot auto-creates on-link | Bot erstellt Webhook beim Linken eines Threads, persistiert URL | |
| Operator paste in discord-config | Operator erstellt manuell + pastet URLs in discord-config | |
| Just-in-time bei Post | Lazy-Init beim ersten Post-Klick | |
| Reuse Match-Channel-Webhook-URL | (Sanity-Check zur Ablehnung — API-technisch unmöglich) | |
| **Other (User-Confusion)** | "Wozu haben wir dann die beiden Discord Configs 'Race-Results Forum Channel ID' und 'Standings Forum Channel ID' eingeführt. Was ist der Sinn und Zweck dieser?" | clarification asked |

**User's choice:** N/A — clarification requested. Explained channel-ID vs webhook-URL distinction (channel-ID for Thread-Enumeration + Create-Thread, webhook-URL for Posting).
**Notes:** Led to follow-up turn.

### Turn 2: Bot-Direkt-POST (channel-IDs reichen) vs Bot-auto-create-Webhook (Spec-konform) vs Operator-paste

| Option | Description | Selected |
|--------|-------------|----------|
| Bot-Direkt-POST | Bot-Token POST/PATCH /channels/{threadId}/messages direkt; keine Webhooks; bricht "all-via-webhook" Invariante | |
| Bot auto-creates Webhook | Bot erstellt Webhook auf Parent-Forum-Channel beim FORUM-01 Save | |
| Operator pastet Webhook-URLs | 2 neue Felder in discord-config; Operator setzt manuell | |
| **Other (User-Klärung)** | "Der Bot muss keine neuen Threads hier erstellen. Das macht der Operator direkt im Discord für jede Saison. Dann brauchen wir also vermutlich nur die Webhook URLs für Results und Standings, oder?" | clarification |

**User's choice:** Implicit endorsement of Operator-paste — Discord-side setup is manual; app only needs to know webhook-URLs + thread-IDs.
**Notes:** Two scope reductions discovered: (a) Drop Create-Thread; (b) Webhook-URLs are operator-pasted.

### Turn 3: FORUM-01 Setup-Form & Season-Detail Thread-Picker — wie schlank?

| Option | Description | Selected |
|--------|-------------|----------|
| Schlank: nur Webhook-URLs + Thread-ID-Paste | DROP channel-IDs, simple text-inputs for thread-IDs | |
| Mittel: Webhook-URLs + Picker mit Auto-Pre-Select | channel-IDs bleiben + 2 neue Webhook-URL-Felder; Modal-Picker mit pinned-Auto-Select | ✓ |
| Mittel-minus: nur Webhook-URLs + Picker mit Pre-Select | channel-IDs werden intern aus webhook-URL resolved | |

**User's choice:** Mittel
**Notes:** Captured as D-96-FOR-1 + D-96-FOR-1b + D-96-FOR-1c + D-96-FOR-2. V13 = 2 ADD discord_global_config + 2 ADD seasons. Drop create-thread.

---

## Provisional Scores Target-Channel

### Status

**Implicitly resolved during Provisional-Scores Layout discussion (Turn 4):** User-Direktive "Provisional Results werden nur im Match Day Kanal gepostet. Diese landen nie im Results Forum Thread." Captured as D-96-GRX-1c.

No separate AskUserQuestion turn needed for this area.

---

## Auto-Unarchive + Re-Archive Verhalten

### Turn 1: Soll der Bot nach dem Post den Thread wieder schließen?

| Option | Description | Selected |
|--------|-------------|----------|
| Nie re-archive (Spec-Default) | Hardcoded: Thread bleibt offen; Discord's natürliche Inactivity-Auto-Archive | ✓ |
| Configurable via app-yml-Flag | Spring-Property mit default false | |
| Configurable via discord-config UI | UI-Checkbox auf Discord-Config-Page | |
| Pre-Status restaurieren (auto-decide) | Bot merkt sich pre-archived-state, restauriert | |

**User's choice:** Nie re-archive (Spec-Default)
**Notes:** Captured as D-96-FOR-4 + D-96-FOR-4a. Auto-Unarchive applies to ALL forum-thread posts (RACE_RESULTS, Phase 97 POST-07/08); not to PROVISIONAL_SCORES (match-channel only).

---

## Plan-Decomposition

### Turn 1: Vorgeschlagene Plan-Reihenfolge?

| Option | Description | Selected |
|--------|-------------|----------|
| 01 GRAFX, 02 FORUM-01, 03 FORUM-02 | Klare 1-Topic-pro-Plan-Aufteilung | ✓ |
| 01 FORUM-01-Foundation, 02 FORUM-02, 03 GRAFX | Schema first, GRAFX last | |
| Mehr aufteilen (4 Plans) | Kleinere Plans, mehr Wave-Pauses | |

**User's choice:** 01 GRAFX, 02 FORUM-01, 03 FORUM-02 (Recommended)
**Notes:** Captured as D-96-05. Sequential inline on `gsd/v1.13-discord-integration`; wave-pause after each plan.

---

## Claude's Discretion

The following areas were captured in CONTEXT.md `### Claude's Discretion` for planner-decision:

- `Thread` DTO `pinned`-Detection-Mechanik (D-96-FOR-2a) — `thread.flags` Bitfield vs `thread.thread_metadata.archived` vs Discord-API-actual-Response-Shape.
- Season-Edit-Section-Location (D-96-FOR-2b) — Form-inline vs dedicated sub-page.
- `SeasonMixIn` + `DiscordGlobalConfigMixIn` Backup-Wire-Contract (D-96-07) — exportieren oder ignorieren der neuen Discord-Felder; empfohlen: Thread-IDs exportieren, Webhook-URLs NICHT.
- `DiscordWebhookClient` Overload-Strategy (D-96-FOR-3a) — Method-Overloads vs WebhookTarget-Wrapper-Record.
- `ResultsGraphicService.generateResultsBytes` Implementation-Variante (D-96-FOR-3d).
- Visual-Regression-Snapshot für ProvisionalScoresGraphicService (Plan 96-01 optional).

---

## Deferred Ideas

- Bot-side Create-Thread Workflow — DISC-FUTURE.
- Re-Archive-After-Post Configurability — v1.14 falls jemals nötig.
- Visual-Regression-Snapshot-Test (Pixel-Hash gegen Reference) — Plan 96-01 oder Phase 98.
- `DiscordForumService.createThread()` Service-Wrapper + UI — deferred.
- `DiscordPostRef.MatchdayRef` Permit-Implementation — Phase 97 POST-07.
- Webhook-URL-Export-vs-Skip im Backup-Wire-Contract — D-96-07 Planner-Discretion.
- Mobile-Viewport `.card`-Overflow auf Season-Edit — Phase 98 Polish-Sweep.
