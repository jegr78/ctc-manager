---
phase: quick-260531-suj
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/org/ctc/discord/dto/Webhook.java
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/resources/templates/admin/match-detail.html
  - src/test/java/org/ctc/discord/DiscordRestClientWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java
  - src/test/java/org/ctc/admin/controller/MatchControllerLinkChannelTest.java
autonomous: true
requirements: [LINK-CHANNEL-01]

must_haves:
  truths:
    - "An admin viewing a match with no Discord channel can enter an existing channel ID and link it"
    - "After linking, the match has BOTH discordChannelId AND discordChannelWebhookUrl set (posting-usable)"
    - "Linking reuses an existing 'CTC Manager' webhook on the channel if present, otherwise creates one"
    - "Linking a non-existent / inaccessible channel ID shows a clear flash error (not found / no permission) and writes nothing"
    - "The link form is only visible while discordChannelId is null (hidden once a channel exists)"
  artifacts:
    - path: "src/main/java/org/ctc/discord/DiscordRestClient.java"
      provides: "listWebhooks(channelId) -> GET /channels/{channelId}/webhooks"
      contains: "listWebhooks"
    - path: "src/main/java/org/ctc/discord/service/DiscordChannelService.java"
      provides: "linkExistingChannel(match, channelId) link flow"
      contains: "linkExistingChannel"
    - path: "src/main/java/org/ctc/admin/controller/MatchController.java"
      provides: "POST /admin/matches/{id}/link-discord-channel"
      contains: "link-discord-channel"
    - path: "src/main/resources/templates/admin/match-detail.html"
      provides: "inline link-channel form in Discord Actions card"
      contains: "link-discord-channel"
  key_links:
    - from: "match-detail.html link form"
      to: "MatchController.linkDiscordChannel"
      via: "POST /admin/matches/{id}/link-discord-channel with channelId param"
      pattern: "link-discord-channel"
    - from: "MatchController.linkDiscordChannel"
      to: "DiscordChannelService.linkExistingChannel"
      via: "delegation (thin controller)"
      pattern: "linkExistingChannel"
    - from: "DiscordChannelService.linkExistingChannel"
      to: "DiscordRestClient.listWebhooks / createWebhook / fetchChannel"
      via: "validate + reuse-or-create webhook"
      pattern: "listWebhooks"
---

<objective>
Let an admin retroactively link an EXISTING Discord channel to a Match (when `match.discordChannelId == null`) via the Admin UI, instead of creating a new one. The admin pastes the channel's snowflake ID; the server validates the channel is reachable, reuses or creates the "CTC Manager" webhook, and persists BOTH `discordChannelId` and `discordChannelWebhookUrl`.

Purpose: A channel may already exist (manually prepared) and should be associated with the match without creating a fresh one. A linked channel must be posting-usable, so a webhook is mandatory.

Output:
- `Webhook` DTO gains a `name` field (for webhook-reuse matching).
- `DiscordRestClient.listWebhooks(channelId)` (`GET /channels/{channelId}/webhooks`).
- `DiscordChannelService.linkExistingChannel(match, channelId)`.
- `MatchController` POST `/admin/matches/{id}/link-discord-channel`.
- Inline link form in the match-detail "Discord Actions" card.
- TDD coverage: RestClient WireMock IT (production URL/payload pinned), service link-flow WireMock IT, controller test.

Out of scope (per CONTEXT): re-linking an already-linked channel, creating channels (existing `createMatchChannel`), category membership check, permission-overwrite audit/setting. NO Flyway change — both columns already exist and are nullable.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/quick/260531-suj-bestehenden-discord-match-kanal-nachtr-g/260531-suj-CONTEXT.md
@CLAUDE.md

<interfaces>
<!-- Verified via grep on 2026-05-31. Executor uses these directly — no extra exploration. -->

Match (org.ctc.domain.model.Match):
  private String discordChannelId;          // nullable, length 32
  private String discordChannelWebhookUrl;  // nullable, length 500
  // setters: setDiscordChannelId(String), setDiscordChannelWebhookUrl(String)

Webhook DTO (org.ctc.discord.dto.Webhook) — CURRENT, needs `name` added:
  public record Webhook(String id, String token, String url,
                        @JsonProperty("channel_id") String channelId) {}
  // @JsonIgnoreProperties(ignoreUnknown = true). Discord's webhook JSON field is "name" (no underscore) → plain `String name` maps directly, no @JsonProperty needed.

DiscordRestClient (org.ctc.discord.DiscordRestClient):
  public Channel fetchChannel(String channelId) throws DiscordApiException;        // GET /channels/{channelId}
  public Webhook createWebhook(String channelId, String name) throws DiscordApiException; // POST /channels/{channelId}/webhooks
  public List<Channel> listChannels(String guildId) throws DiscordApiException;    // uses ParameterizedTypeReference<List<Channel>> CHANNEL_LIST
  // List<T> body pattern (mirror for listWebhooks):
  //   private static final ParameterizedTypeReference<List<Channel>> CHANNEL_LIST = new ParameterizedTypeReference<>() {};
  //   List<Channel> channels = execute(() -> bot.get().uri("/guilds/{guildId}/channels", guildId).retrieve().body(CHANNEL_LIST));
  //   return channels == null ? List.of() : channels;
  // import already present: org.springframework.core.ParameterizedTypeReference

DiscordChannelService (org.ctc.discord.service.DiscordChannelService):
  private static final String WEBHOOK_NAME = "CTC Manager";
  private final DiscordRestClient restClient;
  private final MatchRepository matchRepository;
  private final ApplicationEventPublisher eventPublisher;  // publishes ChannelCreatedEvent in createMatchChannel
  // createMatchChannel persistence tail:
  //   match.setDiscordChannelId(channel.id());
  //   match.setDiscordChannelWebhookUrl(webhook.url());
  //   matchRepository.save(match);
  //   eventPublisher.publishEvent(new ChannelCreatedEvent(match.getId()));

ChannelCreatedEvent listener (DiscordAutoPostListener.onChannelCreated):
  @TransactionalEventListener(AFTER_COMMIT) → calls discordPostService.postTeamCards(match).
  // CONSEQUENCE: publishing ChannelCreatedEvent auto-posts Team Cards. For a manually-prepared
  // linked channel this would spam team cards on link. DECISION (this plan): linkExistingChannel
  // does NOT publish ChannelCreatedEvent — admin uses the explicit "Post Team Cards" button.

MatchController (org.ctc.admin.controller.MatchController):
  // existing POST /{id}/create-discord-channel at ~line 137 — mirror its structure.
  // helpers to reuse (already in class):
  //   private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action)
  //       → maps category to message + sets errorMessage + errorCategory (lowercase-hyphen)
  //   private void applyErrorFlash(RedirectAttributes ra, BusinessRuleException e, String action)
  // injected: private final DiscordChannelService discordChannelService; private final MatchService matchService;
  // matchService.findById(UUID) returns Match.

DiscordApiException categories (DiscordApiExceptionMapper): TRANSIENT / AUTH / NOT_FOUND / MISSING_PERMISSIONS / CATEGORY_FULL.
  fetchChannel on a bad id → NOT_FOUND (404) or MISSING_PERMISSIONS (403) → applyErrorFlash maps to clear message.

WireMock IT base URL prefix: /api/v10  (see DiscordChannelServiceWireMockIT stubs: "/api/v10/channels/c1/webhooks").
Test helper: org.ctc...TestHelper via `helper` field; seedConfig(guildId, categoryId), seedMatch(suffix, homeRoleId, awayRoleId) patterns exist in DiscordChannelServiceWireMockIT.

match-detail.html "Discord Actions" card: the create form is inside
  <form th:if="${match.discordChannelId == null}" ...create-discord-channel...> at lines ~26-37.
  CSS classes in use: card, discord-actions, form-inline, btn btn-primary btn-sm, btn-secondary btn-sm, badge.
  admin.css path: src/main/resources/static/admin/css/admin.css (verify .form-inline exists; reuse only existing classes — no inline styles).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Webhook DTO `name` field + DiscordRestClient.listWebhooks (RED→GREEN, production-format WireMock IT)</name>
  <files>src/main/java/org/ctc/discord/dto/Webhook.java, src/main/java/org/ctc/discord/DiscordRestClient.java, src/test/java/org/ctc/discord/DiscordRestClientWireMockIT.java</files>
  <behavior>
    - Test (integration, @Tag("integration"), @SpringBootTest, WireMock): given a stub for GET /api/v10/channels/{id}/webhooks returning a JSON array of two webhooks (one named "CTC Manager" with a url, one named "Other"), when listWebhooks(channelId) is called, then it returns a List<Webhook> of size 2 whose elements expose name + url + id.
    - Pin the PRODUCTION Discord format: assert via getRequestedFor on the exact path (urlPathEqualTo("/api/v10/channels/{id}/webhooks")) — array body, fields id/name/token/url/channel_id; webhook "name" field has NO underscore (maps to plain `name`), "channel_id" maps via @JsonProperty.
    - Test: given GET webhooks returns an empty array, when listWebhooks called, then returns empty list (not null).
    - If an existing DiscordRestClient WireMock IT class already covers GET endpoints, ADD the new tests there instead of a new file (check first with grep); only create DiscordRestClientWireMockIT.java if no suitable RestClient-level IT exists.
  </behavior>
  <action>
    Add a `String name` component to the `Webhook` record (between `url` and `channelId`); Discord's webhook payload uses field `"name"` so no @JsonProperty annotation is needed — keep `@JsonIgnoreProperties(ignoreUnknown = true)`. Update the existing `createWebhook` JSON stubs in DiscordChannelServiceWireMockIT only if they break on the new field (they ignore unknowns, so they should not — do not touch them otherwise).
    Add `public List<Webhook> listWebhooks(String channelId) throws DiscordApiException` to DiscordRestClient: declare a `private static final ParameterizedTypeReference<List<Webhook>> WEBHOOK_LIST = new ParameterizedTypeReference<>() {};` next to the existing CHANNEL_LIST/ROLE_LIST/EMOJI_LIST refs, then `execute(() -> bot.get().uri("/channels/{channelId}/webhooks", channelId).retrieve().body(WEBHOOK_LIST))` returning `result == null ? List.of() : result`. Mirror listChannels exactly. Add the `org.ctc.discord.dto.Webhook` import if not present; no unused imports (Checkstyle gate).
    No comment pollution. Follow given-when-then naming with // given / // when / // then body comments.
  </action>
  <verify>
    <automated>./mvnw -Dit.test=DiscordRestClientWireMockIT -DfailIfNoTests=false verify</automated>
  </verify>
  <done>Webhook record has a `name` accessor; DiscordRestClient.listWebhooks returns the channel's webhooks via GET /channels/{id}/webhooks; the WireMock IT pins the production array format with a path assertion and passes; empty-array case returns an empty list.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: DiscordChannelService.linkExistingChannel (RED→GREEN, full link-flow WireMock IT + reuse-vs-create)</name>
  <files>src/main/java/org/ctc/discord/service/DiscordChannelService.java, src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java</files>
  <behavior>
    - Test (add to DiscordChannelServiceWireMockIT): given a reachable channel "c9" and GET /api/v10/channels/c9/webhooks returns a "CTC Manager" webhook with url U, when linkExistingChannel(match, "c9"), then match reloads with discordChannelId="c9" AND discordChannelWebhookUrl=U, and NO POST to /channels/c9/webhooks happened (wm.verify(exactly(0), postRequestedFor(...webhooks))) — webhook reused.
    - Test: given channel "c9" reachable and GET webhooks returns an array WITHOUT a "CTC Manager" entry, when linkExistingChannel, then a webhook is created (wm.verify postRequestedFor /channels/c9/webhooks) and discordChannelWebhookUrl is the created webhook's url; both fields persisted.
    - Test: given fetchChannel("bad") returns 404, when linkExistingChannel(match, "bad"), then a DiscordApiException with category NOT_FOUND is thrown and match is unchanged (discordChannelId still null, no webhook GET/POST). Use assertThatThrownBy (// when / then combined).
    - Test: given fetchChannel returns 403, then DiscordApiException category MISSING_PERMISSIONS, nothing persisted.
    - Verify NO ChannelCreatedEvent side effect: assert team-cards auto-post path is not triggered (no outbound team-card webhook execute). Simplest: do not publish the event; the IT already mocks no DiscordPostService call — assert via absence of any post to the channel-message endpoint, or omit if the IT has no post stub (event simply isn't published).
  </behavior>
  <action>
    Add `@Transactional public void linkExistingChannel(Match match, String channelId) throws DiscordApiException`. Steps in order:
    1. Validate reachability: `restClient.fetchChannel(channelId)` — let any DiscordApiException propagate unchanged (404→NOT_FOUND, 403→MISSING_PERMISSIONS) so the controller maps it. Do NOT check category membership or permission overwrites (per CONTEXT D-validation-depth).
    2. Resolve webhook: `restClient.listWebhooks(channelId)`, find the first whose `name()` equals `WEBHOOK_NAME` ("CTC Manager") with a non-blank url; if present reuse its url, else `restClient.createWebhook(channelId, WEBHOOK_NAME)` and use the created url.
    3. Persist tail: `match.setDiscordChannelId(channelId); match.setDiscordChannelWebhookUrl(webhookUrl); matchRepository.save(match);` then `log.info("Discord channel linked to match {} → channelId={}", match.getId(), channelId);`.
    4. Do NOT publish ChannelCreatedEvent — linking a prepared channel must not auto-post Team Cards (the onChannelCreated listener does); the admin uses the explicit Post Team Cards button. (Record this WHY as a single-line comment only if non-obvious; otherwise rely on this plan.)
    Use `org.springframework.util.StringUtils.hasText` (already statically imported) for the non-blank url check. No new unused imports.
    Do not call assertPreconditions (those are create-only: team roles + category). Linking deliberately skips them.
  </action>
  <verify>
    <automated>./mvnw -Dit.test=DiscordChannelServiceWireMockIT -DfailIfNoTests=false verify</automated>
  </verify>
  <done>linkExistingChannel validates via fetchChannel, reuses an existing "CTC Manager" webhook or creates one, persists both discord fields, and does NOT publish ChannelCreatedEvent. All four IT scenarios (reuse, create, 404, 403) pass; reuse case asserts zero webhook POSTs.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: MatchController POST link endpoint + match-detail link form (controller test + template wiring)</name>
  <files>src/main/java/org/ctc/admin/controller/MatchController.java, src/main/resources/templates/admin/match-detail.html, src/test/java/org/ctc/admin/controller/MatchControllerLinkChannelTest.java</files>
  <behavior>
    - Controller test (plain unit, untagged, MockMvc @WebMvcTest or direct controller test mirroring how the create endpoint is tested — check existing MatchController test style first):
      - given a blank/missing channelId param, when POST /admin/matches/{id}/link-discord-channel, then an errorMessage flash is set and linkExistingChannel is NOT called (reject empty input before hitting Discord).
      - given a valid channelId and discordChannelService.linkExistingChannel succeeds, when POST, then redirect to /admin/matches/{id} with successMessage; linkExistingChannel invoked once with (match, channelId).
      - given linkExistingChannel throws DiscordApiException(NOT_FOUND), when POST, then errorMessage + errorCategory "not-found" flash set (via applyErrorFlash), redirect to /admin/matches/{id}.
      Use Mockito for matchService.findById + discordChannelService. Given-when-then naming.
  </behavior>
  <action>
    Controller: add `@PostMapping("/{id}/link-discord-channel") public String linkDiscordChannel(@PathVariable UUID id, @RequestParam String channelId, RedirectAttributes redirectAttributes)`. Thin: trim channelId; if blank → `redirectAttributes.addFlashAttribute("errorMessage", "Discord channel ID is required.")` and return redirect (no service call). Else `try { discordChannelService.linkExistingChannel(matchService.findById(id), channelId.trim()); redirectAttributes.addFlashAttribute("successMessage", "Discord channel linked."); } catch (DiscordApiException e) { applyErrorFlash(redirectAttributes, e, "Link Discord Channel"); }`. Return `"redirect:/admin/matches/" + id`. Bind via @RequestParam (NOT an entity) per thin-controller / no-mass-assignment rule. No new fields needed; reuse existing applyErrorFlash(DiscordApiException) overload. Snowflake length/numeric validation is discretionary — a blank check is sufficient; do NOT add a category/permission check.
    Template (match-detail.html, inside the existing `discord-actions` div, in the `match.discordChannelId == null` block, after the create-channel form ~line 37): add an inline POST form to `@{/admin/matches/{id}/link-discord-channel(id=${match.id})}` with a `<input type="text" name="channelId" ... required>` (placeholder "Existing channel ID", English) and a submit button. Reuse existing CSS classes only (`form-inline`, `btn btn-secondary btn-sm`); NO inline styles — if a width/spacing class is genuinely missing, add it to admin.css AND keep buttons class-based. Add a `data-testid="link-discord-channel"` on the button and `data-testid="link-discord-channel-input"` on the input for UAT. UI text English. Keep it minimal/inline (no modal).
  </action>
  <verify>
    <automated>./mvnw -Dtest=MatchControllerLinkChannelTest test-compile surefire:test@default-test -DfailIfNoTests=false || ./mvnw -Dtest=MatchControllerLinkChannelTest verify -DfailIfNoTests=false</automated>
  </verify>
  <done>POST /admin/matches/{id}/link-discord-channel exists, rejects blank channelId without calling the service, delegates valid IDs to linkExistingChannel, maps DiscordApiException via applyErrorFlash, and redirects with flash messages. The match-detail card shows an inline link form only when discordChannelId is null, using existing CSS classes and English text. Controller test passes.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin browser → MatchController | Untrusted channelId string crosses here (admin-authenticated in prod/docker; dev/local unauthenticated by design) |
| MatchController/Service → Discord REST API | Outbound; channelId is interpolated into the URL path `/channels/{channelId}/...` |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-link-01 | Tampering / Injection | channelId @RequestParam → DiscordRestClient URI | mitigate | RestClient `uri("/channels/{channelId}", channelId)` URL-encodes the path variable (Spring UriTemplate) — no string concat. Blank-check in controller; fetchChannel rejects non-existent/garbage IDs (NOT_FOUND) before any write. |
| T-link-02 | Information Disclosure | linking an arbitrary reachable channel | accept | Admin-only action (auth on prod/docker); admin deliberately links a prepared channel. Low blast radius — only sets two fields on one Match. |
| T-link-03 | Denial of Service / spam | auto-post Team Cards on link | mitigate | linkExistingChannel does NOT publish ChannelCreatedEvent, so no auto-post to a prepared channel. |
| T-link-04 | Tampering | npm/pip/cargo installs | n/a | No new dependencies added (Maven, existing libs only). No package-legitimacy gate needed. |
</threat_model>

<verification>
- No Flyway migration added (columns `discordChannelId` / `discordChannelWebhookUrl` already exist, nullable) — confirm `git status` shows no new `V*.sql`.
- No unused imports introduced (Checkstyle gate runs in `validate`).
- New IT classes/methods tagged `@Tag("integration")`; controller test untagged (plain unit).
- WireMock stubs for the new GET webhooks endpoint assert the production path (urlPathEqualTo / getRequestedFor), not just a loose match.
- End-of-phase full run (run ONCE, not per task): `./mvnw clean verify`. JaCoCo line coverage must remain ≥ 82% (DiscordChannelService / DiscordRestClient are NOT coverage-excluded — the new link logic is unit/IT-covered by Tasks 1–2).
</verification>

<success_criteria>
- An admin on a match-detail page with `discordChannelId == null` sees an inline "link existing channel" form; submitting a reachable channel ID links it, setting both `discordChannelId` and `discordChannelWebhookUrl`, and the form disappears (channel now set).
- Reuse of an existing "CTC Manager" webhook works (no duplicate webhook created); when absent, one is created.
- 404 / 403 channel IDs produce clear flash errors and persist nothing.
- No Team Cards are auto-posted on link.
- `./mvnw clean verify` is green; coverage ≥ 82%; no Checkstyle/SpotBugs regressions.
</success_criteria>

<output>
Create `.planning/quick/260531-suj-bestehenden-discord-match-kanal-nachtr-g/260531-suj-SUMMARY.md` when done.
</output>
