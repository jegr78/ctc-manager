# Phase 100: Match Day Channel Naming Scheme - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-26
**Phase:** 100-match-day-channel-naming-scheme-phase-prefix-rs-po-pm-option
**Areas discussed:** Token-Order & Format, Phase-Type Abbreviations, Group-Slug Derivation, Migration & Backwards-Compat

---

## Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Token-Order & Format | Where the phase and group prefixes sit relative to `mdN` and the team tokens | ✓ |
| Phase-Type Abbreviations | REGULAR→rs / PLAYOFF→po / PLACEMENT→pm, mapping source | ✓ |
| Group-Slug Derivation | How `SeasonPhaseGroup.name` becomes a Discord-legal slug | ✓ |
| Migration & Backwards-Compat | What happens to channels created under the Phase-94 scheme | ✓ |

**User's choice:** all four areas selected.

---

## Token-Order & Format

| Option | Description | Selected |
|--------|-------------|----------|
| `{phase}-md{N}-[{group}-]{home}-vs-{away}` | Phase first, mdN second. Liest sich linear, folgt dem Phase-Titel-Wortlaut. Example: `rs-md3-group-a-alf-vs-bra` | |
| `md{N}-{phase}-[{group}-]{home}-vs-{away}` | mdN first, phase second. Sortiert in Discord nach Matchday-Nr zuerst. Example: `md3-rs-group-a-alf-vs-bra` | ✓ |
| `{phase}-md{N}-...` with underscore separator between phase and mdN | Visuelle Trennung der zwei Achsen via `_`. Example: `rs_md3-group-a-...` | |

**User's choice:** `md{N}-{phase}-[{group}-]{home}-vs-{away}` — Discord-Sortierung nach Matchday-Nummer steht vorne; Phase-Type ist zweiter Token.
**Notes:** Selection deviates from the phase-title-wording (which suggested phase first) but is consistent with how operators visually scan a Discord channel list: matchday number is the primary axis, phase type secondary.

---

## Phase-Type Abbreviations

| Option | Description | Selected |
|--------|-------------|----------|
| Hardcoded switch — REGULAR→rs, PLAYOFF→po, PLACEMENT→pm | Static mapping in `DiscordChannelService`. Exhaustive sealed-switch. Future PhaseType additions trigger compile-fail (intentional). | ✓ |
| Configurable per DiscordGlobalConfig (3 new fields) | Operator sets abbreviations via `/admin/discord-config`. Requires Flyway migration + form fields + validation. | |
| Derive from `SeasonPhase.label` | Take first 2 chars of free-text label. Risk: collisions ("Regular" / "Round-of-16"). Not recommended. | |

**User's choice:** hardcoded `switch(phaseType)`.
**Notes:** No-fallback / no-default branch (D-05 in CONTEXT.md) — exhaustive sealed switch only.

---

## Group-Slug Derivation

| Option | Description | Selected |
|--------|-------------|----------|
| Last-Token Slug (`"Group A"` → `a`) | Split on whitespace, take last token, slugify. Optimal für "Group X"-Muster, Fallback bei 1-Token-Namen. | |
| Full slugified name (`"Group A"` → `group-a`) | Komplettes Slug. Lowercase + non-alphanum → `-`, collapse, trim. Konsistente Regel für alle Fälle. | ✓ |
| 3-Char-Prefix (`"Group A"` → `gro`) | Erste 3 Buchstaben nach Slugify. Kollidiert bei "Group A"/"Group B" → beide `gro`. Nicht praktikabel. | |
| New `SeasonPhaseGroup.slug` field | Operator gibt expliziten Slug ein. Erfordert Flyway V14 + Form-Feld. Maximale Kontrolle, höchster Aufwand. | |

**User's choice:** full slugified name.
**Notes:** Selection drives D-06 in CONTEXT.md: 5-step slugify (NFD-decompose → strip combining marks → lowercase → replace non-`[a-z0-9]` → collapse `-` → trim).

---

## Migration & Backwards-Compat

| Option | Description | Selected |
|--------|-------------|----------|
| Leave-as-is, neues Schema gilt nur für neue Channels | Existing channels behalten alten Namen. Null Migration-Code, null Rate-Limit-Risiko. | ✓ |
| Admin-Button "Rename all match channels to new scheme" | One-shot bulk rename via Discord PATCH. Rate-limit-aware. +1 Plan, +Tests. | |
| Lazy auto-rename beim nächsten Post | PATCH zum neuen Namen vor dem Post wenn altes Schema erkannt. Versteckte Seiteneffekte. | |
| Hybrid: Leave-as-is + Read-only Diagnostic-Liste | Wie Option 1 + Liste auf `/admin/discord-config`. Operator triagiert manuell. | |

**User's choice:** Leave-as-is.
**Notes:** Existing v1.13-era match-channels are predominantly UAT artifacts. Operator wipes them manually in Discord if desired. Phase 100 deliberately does NOT build a rename action (D-08); v1.14 may add one if production reality demands it (deferred idea in CONTEXT.md).

---

## Claude's Discretion

- Exact wording of the `BusinessRuleException` message when channel-name exceeds 100 chars (D-10 in CONTEXT.md) — must include both the produced name and its length, otherwise planner's call.
- Whether to add an optional pure-unit `DiscordChannelServiceNamingTest` (parameterized, no Spring context) in addition to refreshing the existing WireMock-IT fixtures (D-13). Defer to planner.
- Whether the slugify-helper stays as a `private static` inside `DiscordChannelService` or becomes a package-private helper class in `org.ctc.discord.service` (D-12) — either fine; YAGNI says inline.

## Deferred Ideas

- Operator-configurable phase-abbreviations (v1.14 backlog if a future league wants different chars).
- `SeasonPhaseGroup.slug` explicit-slug field (v1.14 if the derive-from-name rule proves insufficient).
- Bulk-rename action for pre-Phase-100 channels (v1.14 entry point if operator changes their mind post-merge).
- Diagnostic list of "channels with old naming scheme" on `/admin/discord-config` (same v1.14 trigger).
- Discord channel-renaming when `Team.shortName` changes after creation (already a Phase-94 invariant, not a Phase-100 regression — out of scope).
