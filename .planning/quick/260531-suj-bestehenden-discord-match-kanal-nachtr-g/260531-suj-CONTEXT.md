# Quick Task 260531-suj: Bestehenden Discord Match Kanal nachträglich verknüpfen - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

<domain>
## Task Boundary

An admin must be able to link an EXISTING Discord channel to a Match retroactively, when the match has no `discordChannelId` yet. Use case: a Discord channel already exists (manually prepared) and should be associated with the match without creating a new channel. Implemented via the Admin UI.

Out of scope: changing/re-linking an already-linked channel, creating new channels (that's the existing `createMatchChannel` flow), Discord-side channel management.
</domain>

<decisions>
## Implementation Decisions

### Input method
- **Manual channel-ID entry** (text field for the Discord snowflake). Admin copies the ID from Discord (right-click → Copy ID). No channel picker/dropdown.
- Server-side validation of the entered ID.

### Webhook handling
- On link: **list the channel's webhooks, reuse an existing "CTC Manager" webhook if present, otherwise create a new one** via `createWebhook(...)`.
- A "list webhooks for a channel" method does NOT yet exist on `DiscordRestClient` → must be added (`GET /channels/{channelId}/webhooks`). The reused webhook constant name is `WEBHOOK_NAME` ("CTC Manager") already defined in `DiscordChannelService`.
- Both `discordChannelId` AND `discordChannelWebhookUrl` MUST be set — a linked channel without a webhook is posting-unusable (all DiscordPostService posts depend on the webhook URL).

### Validation depth
- **Existence + reachability only**: `restClient.fetchChannel(id)` must succeed (channel exists, bot has access).
- Map errors to clear user feedback: 404 (NOT_FOUND) → "channel not found / bot has no access"; 403 (MISSING_PERMISSIONS) → "bot lacks permission". Reuse existing `DiscordApiException` category mapping (see `MatchControllerCreateChannelErrorCategoryTest`).
- Do NOT enforce category membership or set/audit permission overwrites (unlike `createMatchChannel`). The admin deliberately links a prepared channel.

### UI placement
- **Match-detail "Discord Actions" card** (`match-detail.html`), alongside the existing "Create Discord Channel" button.
- Visible only when `match.discordChannelId == null` (same condition as the create button).
- Own POST action: `/admin/matches/{id}/link-discord-channel` with the channel-ID as a request param. Thin controller → delegate to a new service method (e.g. `DiscordChannelService.linkExistingChannel(match, channelId)`).
- Flash attributes `successMessage` / `errorMessage` per project convention.

### Claude's Discretion
- Exact wording of UI labels (German UI? — NO: UI texts are English per CLAUDE.md), flash messages, snowflake format validation (length/numeric) on the form DTO.
- Whether the channel-ID input is a small inline form on the card vs. a tiny modal — keep it minimal/inline.
- Test structure following existing patterns (`DiscordChannelServiceWireMockIT` for the link flow with WireMock, unit test for validation/webhook-reuse logic, controller test for the new endpoint).

</decisions>

<specifics>
## Specific Ideas

Grounding from codebase exploration (verify before relying):
- `Match.discordChannelId` (nullable, len 32), `Match.discordChannelWebhookUrl` (nullable, len 500) — `org.ctc.domain.model.Match`.
- `DiscordChannelService.createMatchChannel(Match)` — existing create flow; `WEBHOOK_NAME = "CTC Manager"`. New method `linkExistingChannel` should mirror its persistence tail (set both fields, `matchRepository.save`, publish `ChannelCreatedEvent` if appropriate — confirm whether the event should fire on link too).
- `DiscordRestClient`: has `fetchChannel(channelId)`, `listChannels(guildId)`, `createWebhook(channelId, name)`. Needs new `listWebhooks(channelId)` returning the channel's webhooks (reuse `Webhook` DTO; confirm/extend its fields for `name` + `url`).
- `MatchController` (`org.ctc.admin.controller`): existing POST `/admin/matches/{id}/create-discord-channel` at ~line 137 — mirror its structure for `/link-discord-channel`.
- `match-detail.html` "Discord Actions" card lines ~23–53 — add link UI next to create button under the `discordChannelId == null` block.
- Error category mapping pattern: `DiscordApiExceptionMapper`, categories `TRANSIENT/AUTH/NOT_FOUND/MISSING_PERMISSIONS/CATEGORY_FULL`.

</specifics>

<canonical_refs>
## Canonical References

- `./CLAUDE.md` — Architectural Principles (thin controllers, DTOs/form binding, Spring-native `RestClient`, no inline styles, WireMock-vs-real-API discipline, score-aggregation N/A here), Conventions (naming, Lombok, logging, no comment pollution), Test discipline (`@Tag`, clean build, coverage ≥82%, given-when-then).
- WireMock test discipline (CLAUDE.md "Build & Test Discipline"): the new `listWebhooks` regex/URL pattern needs a test pinning the production Discord format with `withQueryParam`/path assertions, not just `urlPathEqualTo`.

</canonical_refs>
