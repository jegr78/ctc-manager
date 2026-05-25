---
phase: 98-polish-e2e-docs-close
plan: 05
executed: 2026-05-25T18:21:00+02:00
server_profile: dev,demo (started via scripts/app.sh — .env.dev loaded)
total: 7
passed: 7
failed: 0
skipped: 0
scope: Plan 98-05 MATCHDAY_PAIRINGS UI surfaces (discord-config textarea + matchday-detail Discord Announcements card + edit-pairings page + form-submit state-flip)
---

# Auto-UAT Report: Phase 98 / Plan 98-05 (MATCHDAY_PAIRINGS)

Browser-driven walkthrough of the new Pairings UI on `dev,demo` profile.
DiscordDevSeeder loaded the test guild + announcement webhook from
`.env.dev` via `scripts/app.sh` env-export.

## Results

### 1. discord-config — Matchday Pairings Template textarea visible
- **Status:** passed
- **URL:** `/admin/discord-config`
- **Screenshots:**
  - [01-discord-config.png](../../.screenshots/auto-uat-98/01-discord-config.png)
  - [01-discord-config.yaml](../../.screenshots/auto-uat-98/01-discord-config.yaml)
- **Evidence:** Snapshot contains `textbox "Matchday Pairings Template"` with placeholder "Leave empty to use the built-in default." Field rendered under the existing VS Emoji Name field per Plan 98-05 visual_reference Section C.

### 2. discord-config — Operator template fill + Save
- **Status:** passed
- **Action:** `fill('matchday-pairings-template', 'Custom Template Test for {{matchdayNumber}} — Deadline {{deadline}}, Weekend {{weekend}}')` then click `Save`.
- **Screenshots:**
  - [02-discord-config-filled.yaml](../../.screenshots/auto-uat-98/02-discord-config-filled.yaml)
  - [03-discord-config-saved.yaml](../../.screenshots/auto-uat-98/03-discord-config-saved.yaml)
- **Evidence:** Post-save snapshot contains both `Configuration saved.` success flash AND the textarea pre-populated with the verbatim operator template (`Custom Template Test for {{matchdayNumber}} — Deadline {{deadline}}, Weekend {{weekend}}`). Round-trip via `DiscordGlobalConfigService.save` → `toForm` confirmed.

### 3. matchday-detail — Discord Announcements section gated on announcement webhook
- **Status:** passed
- **URL:** `/admin/matchdays/04eefd1e-…/` (Matchday 1, dev seeded)
- **Screenshots:**
  - [08-matchday-detail-with-webhook.png](../../.screenshots/auto-uat-98/08-matchday-detail-with-webhook.png)
  - [08-matchday-detail-with-webhook.yaml](../../.screenshots/auto-uat-98/08-matchday-detail-with-webhook.yaml)
- **Evidence:** Section heading `Discord Announcements` rendered, meta block shows `Pick Deadline: — not set —` / `Scheduled Weekend: — not set —`. The new `matchdayAnnouncementActive` model attribute correctly gates visibility (compare to first run pre-app.sh-restart — section absent when `announcementWebhookUrl` was empty in DB).

### 4. matchday-detail — Disabled state with D-98-PRE-1 tooltip
- **Status:** passed
- **Evidence:** Snapshot contains `generic "Set pick deadline first" [...]: Post Matchday Pairings` — disabled span with the exact tooltip text from `canPostMatchdayPairings` first reject branch.

### 5. matchday-detail — Edit Pairings link present
- **Status:** passed
- **Evidence:** `link "Edit Pairings" [...] /url: /admin/matchdays/04eefd1e-…/edit-pairings` rendered in the meta block — matches Plan 98-05 visual_reference Section B.

### 6. edit-pairings form — Pick Deadline + Scheduled Weekend fields + Save
- **Status:** passed
- **URL:** `/admin/matchdays/04eefd1e-…/edit-pairings`
- **Action:** `fill('pickDeadline', '2099-05-29T19:00')` + `fill('scheduledWeekend', '29-31 May')` + click `Save Pairings`.
- **Screenshots:**
  - [09-edit-pairings-form.png](../../.screenshots/auto-uat-98/09-edit-pairings-form.png)
- **Evidence:** Form rendered with `textbox "Pick Deadline"` (datetime-local) and `textbox "Scheduled Weekend"` (free-text, placeholder "e.g. 22-24 May or Tue 26 May"). Submit redirected to matchday-detail with no validation errors.

### 7. matchday-detail — Button state transition (disabled → enabled Post Matchday Pairings)
- **Status:** passed
- **Screenshots:**
  - [10-matchday-detail-after-save.png](../../.screenshots/auto-uat-98/10-matchday-detail-after-save.png)
  - [10-matchday-detail-after-save.yaml](../../.screenshots/auto-uat-98/10-matchday-detail-after-save.yaml)
- **Evidence:**
  - Success flash: `Pairings saved.`
  - Meta block now shows `Pick Deadline: Fri, 29 May 2099 19:00` (Thymeleaf `#temporals.format` output) and `Scheduled Weekend: 29-31 May` — values persisted via `MatchdayService.savePairings`.
  - Button transitioned from disabled `<span class="btn btn-secondary btn-sm disabled">` to active `<button>` rendering `Post Matchday Pairings` (snapshot ref e399 — a real button, no longer a disabled span).
  - `canPostMatchdayPairings` re-evaluated true after the save → pre-flight gate cleared as expected.

## Summary

```
Executed: 7 | Passed: 7 | Failed: 0 | Skipped: 0
```

All Plan 98-05 UI deliverables verified end-to-end on the live dev,demo
profile. Server gracefully started via `scripts/app.sh start dev,demo`
(picked up `.env.dev` test-guild + announcement webhook URL).

The actual `Post Matchday Pairings` click → live Discord post is the
operator-driven UAT-09 step (out of scope for this static UI Auto-UAT
since it would post a real message to the operator's test guild).

Coverage gap acknowledged: the stale-state button label flip (`Update
Matchday Pairings` testid) is verified by the Playwright E2E test in
`MatchdayDetailDiscordAnnouncementE2ETest.givenStalePost_…` (Plan 98-05
Task 11) but not exercised here because it requires a seeded
`DiscordPost` row + intervening matchday edit, which would need direct
DB seeding outside the operator-facing UI flow.

## Server cleanup

Server was started by this Auto-UAT (via `scripts/app.sh start dev,demo`).
Per CLAUDE.md "scripts/app.sh profile-aware" pattern, stop with:

```bash
./scripts/app.sh stop dev,demo
```

(The Auto-UAT report does not auto-stop because the user might continue
manual inspection — explicit operator action.)
