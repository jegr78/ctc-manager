# Phase 94: Team Roles + Match Channel Lifecycle - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or
> execution agents. Decisions are captured in CONTEXT.md — this log
> preserves the alternatives considered.

**Date:** 2026-05-21
**Phase:** 94-team-roles-match-channel-lifecycle
**Areas discussed:** Match-Detail-Page Architektur, Match-Channel "aktive
Kategorie" Speicherort, Permission-Audit Fail-Verhalten, Live-Role-Dropdown
+ UI-Polish Bundling

---

## Match-Detail-Page Architektur

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: /admin/matches/{id} Detail + Edit-Page | Neue GET /admin/matches/{id} (read-only Detail mit Discord-Actions-Panel) + GET /admin/matches/{id}/edit (Form fuer 5 Discord-Felder). Inline auf matchday-detail.html bleibt minimal: Link 'Discord Actions' pro Match-Row. Skaliert sauber fuer Phase 95-96. Kostet 1 neuen Controller-Endpoint + 2 neue Templates + MatchForm DTO. | ✓ |
| Komplett inline auf matchday-detail.html | 5 Form-Felder als per-Match Inline-Edit (eigene POST-Endpoints pro Feld) + Buttons als Action-Strip pro Match-Row. matchday-detail.html waechst stark; Mobile-UX leidet; 5 inline-Forms erhoehen CSRF-Token-Footprint. Phase 95-96 wuerden das Pattern weiter aufblaehen. | |
| Nur neue Edit-Page, Buttons inline | GET /admin/matches/{id}/edit fuer die 5 Felder; alle Discord-Buttons inline auf matchday-detail.html. Kein eigenes Detail-Read-View. In Phase 95 12+ Buttons inline ist genauso eng wie Option B. | |

**User's choice:** Hybrid: /admin/matches/{id} Detail + Edit-Page
**Notes:** Sauberer Skalierungspfad fuer Phase 95-96 Post-Buttons. Folgt
dem existierenden per-entity-detail-page Muster (team-detail.html,
matchday-detail.html). matchday-detail.html bekommt nur "→ Detail" Link
pro Match-Row, keine inline-Edit-Bloat.

---

## Match-Channel "aktive Kategorie" Speicherort

### Frage 1: Welche Tabelle / welcher Slot?

| Option | Description | Selected |
|--------|-------------|----------|
| discord_global_config.current_match_category_id | Globaler Single-Slot in V8-Tabelle (analog announcement_webhook_url). Operator setzt EINE aktive Kategorie auf /admin/discord-config. Realistisch: 1 Liga = 1 aktive Saison = 1 aktive Kategorie. | ✓ |
| seasons.discord_current_category_id (per-season) | Per-Season Spalte. Vorteil: parallele Saisons sauber trennbar; Nachteil: zusaetzlicher Migration-Aufwand + zusaetzliches Edit-Surface + redundant solange nur 1 aktive Saison. | |
| season_phases.discord_category_id (per-Phase) | Fein-granular pro REGULAR/PLAYOFF-Phase. Overkill — kein klarer Mehrwert. | |

**User's choice:** discord_global_config.current_match_category_id
**Notes:** Einfachste Loesung; matches die Single-Operator-Single-League
Realitaet; DiscordConfigForm bekommt das 7. Feld.

### Frage 2: Migration-Strategie?

| Option | Description | Selected |
|--------|-------------|----------|
| V9 macht ALTER TABLE discord_global_config + teams in EINER Migration | V9 buendelt: ADD COLUMN current_match_category_id + ADD COLUMN discord_role_id. Beide gehoeren logisch zu CHAN-01/02. Keine separaten Migrationen. | ✓ |
| Separater V9.5 / dedicate Migration | V9 nur teams.discord_role_id, neue Migration fuer Category-Slot. Migration-File-Multiplikation ohne Mehrwert. | |
| V10 erweitern (zusammen mit matches.discord_*) | V10 = match-fields, semantisch falsch fuer global-config Spalte. | |

**User's choice:** V9 bundles both ALTER TABLEs
**Notes:** `V9__add_discord_team_role_and_current_match_category.sql`.
H2 + MariaDB kompatibel, keine CHECK, NOT NULL DEFAULT '' fuer
current_match_category_id.

---

## Permission-Audit Fail-Verhalten

| Option | Description | Selected |
|--------|-------------|----------|
| Bot loescht Channel + DB rollback | Bei Audit-fail: Bot DELETE /channels/{id} → DB-Tx rollback → DiscordAuthException mit Operator-Message. Sauberer Zustand: kein Zombie-Channel. Bei DELETE-fail: zweite Exception logged + Operator-Message ergaenzt. | ✓ |
| Channel stehen lassen + lauten Fehler | Bei Audit-fail: DB-Tx rollback + DiscordAuthException + Operator raeumt manuell auf. Operator-Forgetting-Risiko → Zombie-Channels stapeln sich → nehmen Kategorie-Slots weg. Schwaecher fuer T-93-03. | |
| Auto-revoke nicht-erlaubte Rollen + Warnung | Bot patcht Channel mit zusaetzlichen Overwrites. Maskiert Operator-Setup-Fehler; Discord-Overwrite-Count-Limits machen vollstaendige Revoke unmoeglich. | |

**User's choice:** Bot loescht Channel + DB rollback
**Notes:** Security-First Default fuer T-93-03. Operator bekommt klare
Message + retry-Aufforderung. Cleanup-DELETE Fail wird gesondert logged
+ Message ergaenzt. Server-Admin-Rollen-Bypass ist NICHT erkennbar via
permission_overwrites Audit (in 93-THREAT-MODEL.md als forward-reference
dokumentiert, Phase 98 DOCS-02 flag operativ).

---

## Live-Role-Dropdown + UI-Polish Bundling

### Frage 1: Dropdown-Loading-Strategie?

| Option | Description | Selected |
|--------|-------------|----------|
| Cache-only + Plain-Text-Fallback | DiscordRoleCache analog DiscordEmojiCache (D-93-03 Pattern). Team-Form GET liest aus Cache; falls leer → Plain-Text-Input + Hinweis auf Refresh-Server-Roles Cache Button. KEIN AJAX, KEIN eager Discord-Call. Existierender Refresh-Button fuellt sie. | ✓ |
| Eager-fetch beim GET /admin/teams/{id}/edit | Discord-Call inline kann 200-1000ms; Team-Edit haengt von Discord-Verfuegbarkeit ab. | |
| AJAX-load-on-focus | Page rendert mit Text-Input; bei Focus JS-fetch nach. Zusaetzlicher CSRF-Endpoint, JS-Race-Conditions. | |

**User's choice:** Cache-only + Plain-Text-Fallback
**Notes:** Neue DiscordRoleCache mirror-Klasse zu DiscordEmojiCache.
Operator-Workflow-Kontinuitaet aus UAT-03 (Refresh-Button-Discipline ist
schon etabliert).

### Frage 2: UAT-03 Mobile-Overflow Scope?

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 94 fold-in (in CHAN-Plan-Templates) | CSS-Fix landet in Plan 94-01 (.discord-actions / .inline-form / .btn-group responsive). Single CSS-Pass profitiert alle Discord-Pages. Wave-Pause + Mobile-Screenshot-Sweep via playwright-cli. | ✓ |
| Defer auf Phase 98 Polish-Phase | Phase 98 = 'Polish + E2E + Docs + Close'. Buendel-Pass; Operator-UAT bis dahin Desktop-only. | |
| Eigenes Plan 94-04 (UI-Polish-Only) | 4. Plan dediziert. Bricht "3 Plans 1:1 zu CHAN-01/02/03" Symmetrie. | |

**User's choice:** Phase 94 fold-in (in CHAN-Plan-Templates)
**Notes:** Vermeidet Wiederholung des deferred-debt Patterns das wir
gerade fuer UAT-03 abgeschlossen haben. Plan 94-01 shipt .discord-actions
Cluster + responsive-wrap CSS; Plans 94-02/03 nutzen diese Klassen direkt.

---

## Claude's Discretion

Areas wo der Planner-Agent eigene Entscheidungen trifft (alle in
CONTEXT.md § "Claude's Discretion" gelistet):

- MatchForm Package-Location (`org.ctc.admin.dto`)
- DiscordPermissions Package-Location (`org.ctc.discord` vs `.dto`)
- Exakte CSS-Klassennamen fuer .discord-actions Cluster
- Ob `POST /save-edit` neuer Endpoint oder unified `/save` mit id-Mode-dispatch
- Channel-record Erweiterung fuer permission_overwrites (Extend vs
  separate ChannelWithPermissions record)
- Archive-Modal HTML-Element (`<dialog>` vs CSS-Overlay-div)
- Phase-95-Placeholder-Section Shape in match-detail.html
- Permission-Overwrite type-field encoding (Discord: 0=role, 1=member)

---

## Deferred Ideas

(siehe CONTEXT.md § "Deferred Ideas" fuer vollstaendige Liste)

- Per-team-role-color Sync
- Match-channel Auto-Rotation bei fast-voller Kategorie
- Live-Discord UAT-04 Dashboard / one-click Reset
- Permission-Audit fuer Server-Admin-Rollen-Visibility (Discord-Runtime-Bypass)
- Match-Detail-Page Graphics-Preview Thumbnails
- Bulk Team-Role-Assign UI
- Confirmation-Modal vor DELETE bei Audit-Fail (rejected: Security-Race)
- Webhook-URL-Rotation-History bei Audit-Fail-Recreate

---

## Carry-Forwards from Phase 93 (re-affirmed, not re-discussed)

- D-93-05: Sequential inline execution on `gsd/v1.13-discord-integration`
  (no worktrees, no subagents)
- D-93-06: Rolling milestone PR via `gh pr edit --body-file`
- D-93-07: Standard quality gates (JaCoCo ≥ 88.88 %, SpotBugs 0,
  CodeQL 0, EXPORT_ORDER 24, SCHEMA_VERSION 1, Flyway V1-V8 immutable)
- D-93-08: Per-plan Nyquist VALIDATION.md
- D-93-09: `@Tag` discipline
- D-93-10: 3 plans 1:1 to CHAN-01/02/03 per Design Spec § 5

UAT-03 PASSED 2026-05-21 — Discord platform live-validated, Phase 94
unblocked.
