---
quick_id: 260531-suj
executed: 2026-05-31
mode: standalone
server_profile: dev (via ./scripts/app.sh start dev)
total: 4
passed: 3
verified_by_code: 1
failed: 0
---

# Auto-UAT Report — Quick Task 260531-suj (Link existing Discord channel)

Test match: `SVT vs SGM S` — `/admin/matches/10178464-9f88-4e28-94f9-5fec1542e1ad` (seeded, `discordChannelId == null`).

## Results

### 1. Link form present when no channel linked — PASSED
Discord Actions card shows, next to "Create Discord Channel":
- `textbox "Existing Discord channel ID"` (accessible name from the new `aria-label`; placeholder "Existing channel ID")
- `button "Link Existing Channel"`
Screenshot: [match-detail-desktop.png](../../../.screenshots/auto-uat/match-detail-desktop.png)

### 2. Layout desktop + mobile — PASSED
- Desktop: input + both buttons inline (`.form-inline`), no overflow, secondary button styling consistent.
- Mobile (390×844): `.discord-actions` stacks vertically, full-width button/input/button, clean.
Screenshots: [desktop](../../../.screenshots/auto-uat/match-detail-desktop.png) · [mobile](../../../.screenshots/auto-uat/match-detail-mobile.png)

### 3. Blank/whitespace submit → error flash, nothing linked — PASSED
Submitted a whitespace channel ID (empty is blocked client-side by `required`; whitespace reaches the server). Result: red flash "Discord channel ID is required.", match still unlinked (Create button still present, no Channel badge). Server-side blank-check confirmed; no Discord call made.
Screenshot: [blank-validation-flash.png](../../../.screenshots/auto-uat/blank-validation-flash.png)

### 4. Form hidden once a channel is linked — VERIFIED BY CODE
No seeded match in the dev profile has a linked channel, so this could not be exercised live. Verified by the template conditional `th:if="${match.discordChannelId == null}"` on the link form (and the `!= null` badge span) in `match-detail.html`, plus the controller/service re-link guard added in the review round.

## Notes
- Dev server started via `./scripts/app.sh start dev` (loads `.env.dev`), not `mvnw spring-boot:run` directly.
- First server start crashed on a stale `target` (`NoClassDefFoundError: TestDataService$ScoringDefaults`) — resolved by `./mvnw clean compile`; unrelated to this feature's code.
