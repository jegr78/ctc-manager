---
phase: 99-pre-merge-audit-polish-requirements-flyway-prose-roadmap-ref
reviewed: 2026-05-28T00:00:00Z
depth: deep
files_reviewed: 2
files_reviewed_list:
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/test/java/org/ctc/discord/DiscordRestClientIT.java
findings:
  critical: 0
  warning: 2
  info: 4
  total: 6
status: issues_found
---

# Phase 99: Code Review Report

**Reviewed:** 2026-05-28T00:00:00Z
**Depth:** deep
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Scope of Phase 99 code changes on these two files is small and focused: removal of the unused `createThread` /
`ThreadCreateRequest` pair (YAGNI cleanup) and an IT fixture refresh that decouples the `fetchChannel` happy-path
assertion from a now-obsolete channel-name format. The cross-file audit confirms:

- `DiscordRestClient` public API surface is consistent — all 11 remaining methods have live callers
  (`DiscordChannelService`, `DiscordPostService`, `DiscordForumService`, `DiscordDevSeeder`,
  `DiscordBotIdentityCache`, `DiscordCategoryResolver`, `DiscordConfigController`, `MatchController`).
- No orphan references to `createThread` or `ThreadCreateRequest` remain in `src/` after the deletion.
- The `Spring-Native` convention is respected — `RestClient` (not JDK `HttpClient`), exception mapping
  delegated to a single mapper, and the rate-limit / retry logic lives behind the bean's
  `ClientHttpRequestInterceptor`.
- No `// Phase 99`, `// Plan 99-XX`, `// D-NN` comment pollution in either file.
- No hardcoded secrets, no `eval`, no `innerHTML`, no unsafe reflection.
- WireMock discipline (User Convention): `DiscordRestClient` has no endpoints that carry query parameters
  (`thread_id`, archive cursors, etc.), so the `withQueryParam(...)` rule does not apply here. Endpoint
  paths are all path-only and the IT correctly uses `urlPathEqualTo`.

The findings below are quality / robustness defects only — no security blockers, no correctness bugs.

## Warnings

### WR-01: `fetchGuildEmojis` NPE when `body(...)` returns null

**File:** `src/main/java/org/ctc/discord/DiscordRestClient.java:56-66`
**Issue:** `RestClient`'s `.body(EMOJI_LIST)` returns `null` for an empty/204 body or a JSON `null` payload.
The subsequent `emojis.size()` and the enhanced-for over `emojis` will NPE. The sibling thread-list paths
(`listActiveThreads`, `listArchivedThreads`) already defend against this (`result == null ? List.of() :
result.threads()`), so the missing guard here is an inconsistency, not a deliberate choice. Production
Discord won't return `null` for `/emojis`, but the IT WireMock stubs frequently do via misconfigured
`okJson` payloads — and any 204 response from a misbehaving proxy would crash the cache refresh in
`DiscordDevSeeder.java:84`.
**Fix:**
```java
public Map<String, String> fetchGuildEmojis(String guildId) throws DiscordApiException {
    List<Emoji> emojis = execute(() -> bot.get()
            .uri("/guilds/{guildId}/emojis", guildId)
            .retrieve()
            .body(EMOJI_LIST));
    if (emojis == null) {
        return Map.of();
    }
    Map<String, String> out = new HashMap<>(emojis.size());
    for (Emoji e : emojis) {
        out.put(e.name(), "<:" + e.name() + ":" + e.id() + ">");
    }
    return out;
}
```
Apply the same null-guard idiom to `fetchGuildRoles` (line 49-54) and `listChannels` (line 86-91) for
consistency — both currently return whatever `body(...)` returns including `null`, which their callers
(`DiscordCategoryResolver.java:31`, `DiscordConfigController.java:111`) treat as a non-null `List`.

### WR-02: `deleteChannel` IT exercises only the 5xx-exhausted path, not the happy-path 204 cleanly

**File:** `src/test/java/org/ctc/discord/DiscordRestClientIT.java:222-239`
**Issue:** `given500_whenDeleteChannel_thenDiscordTransientExceptionThrown` is fine, but with the
`fivexx-backoff-ms` test override set to `10,10,10` (line 55) and 3 retries baked into the interceptor,
a deterministic 500 here will trigger four total `DELETE` calls against WireMock plus three sleeps. That
adds ~30 ms of latency per IT run and a four-call WireMock verify burden if a future contributor adds
`wm.verify(...)` to the test. More importantly, the test as written never asserts the retry count, so
a regression that drops 5xx retries to zero would silently still produce the same
`DiscordTransientException` and the test would still pass. The interceptor's retry behaviour is the
contract under test for this path — it should be asserted explicitly.
**Fix:** Add an explicit call-count assertion (or split into two tests — one for retry-then-fail count,
one for happy-path 204) so a regression in `DiscordRateLimitInterceptor.MAX_5XX_RETRIES` is observable:
```java
wm.verify(4, deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c500")));
```

## Info

### IN-01: `BotUser` record exposes Discord ID without `@JsonIgnoreProperties`

**File:** `src/main/java/org/ctc/discord/DiscordRestClient.java:158-159`
**Issue:** `BotUser(String id, String username, String discriminator)` is the only public record in this
file that does **not** carry `@JsonIgnoreProperties(ignoreUnknown = true)`, unlike its private siblings
`Emoji` (line 161-163) and `ThreadList` (line 165-167). Discord's `/users/@me` response in v10 returns
~15 fields (`avatar`, `banner`, `accent_color`, `flags`, `premium_type`, `mfa_enabled`, `verified`,
`email`, `locale`, ...). Jackson on Spring Boot 4.x defaults to `FAIL_ON_UNKNOWN_PROPERTIES=false`, so
this currently works — but the inconsistency means a future Jackson config flip (or a project-wide
`ObjectMapper` customizer that toggles strict mode) silently breaks bot-identity caching.
**Fix:** Add `@JsonIgnoreProperties(ignoreUnknown = true)` to `BotUser` for consistency with the rest of
the DTO surface (every Discord DTO in `org.ctc.discord.dto` already carries the annotation).

### IN-02: `WebhookCreateRequest` lacks `@JsonInclude(NON_NULL)` parity with sibling DTOs

**File:** `src/main/java/org/ctc/discord/DiscordRestClient.java:169-170`
**Issue:** `WebhookCreateRequest` is a private single-field record (`String name`), so there's no
realistic null-emission risk today. But every other request DTO in `org.ctc.discord.dto`
(`ChannelCreateRequest`, `ChannelModifyRequest`) is annotated `@JsonInclude(JsonInclude.Include.NON_NULL)`.
Either keep the request DTO co-located here for cohesion (acceptable) and add the same annotation, or
move it next to its siblings in `org.ctc.discord.dto` — the current "private record but inconsistent
annotation profile" is the worst of both worlds.
**Fix:** Add `@JsonInclude(JsonInclude.Include.NON_NULL)` to the record declaration to match the
project's request-DTO convention.

### IN-03: `execute(...)` swallows the `null` return for void operations via sentinel

**File:** `src/main/java/org/ctc/discord/DiscordRestClient.java:125-133`
**Issue:** `deleteChannel` adapts a `void` operation into the generic `RestCall<T>` interface by
returning a literal `null` from the lambda and ignoring the return at the call site. This is a small
code smell — the type `RestCall<Void>` would document the void-ness explicitly, and a future maintainer
who adds a second void-returning method (`deleteWebhook`, `removeReaction`, ...) is likely to follow the
pattern rather than question it. Consider a sibling `executeVoid(Runnable)` overload or a `RestCall<Void>`
return type with `return null;` made explicit by parametrization. Low priority — current code is
functionally correct.
**Fix:**
```java
private static void executeVoid(VoidRestCall call) throws DiscordApiException {
    execute(() -> { call.run(); return null; });
}
@FunctionalInterface private interface VoidRestCall { void run(); }
```
Then in `deleteChannel`: `executeVoid(() -> bot.delete().uri(...).retrieve().toBodilessEntity());`

### IN-04: IT test for `fetchChannel` uses placeholder name "any-name" without explaining why

**File:** `src/test/java/org/ctc/discord/DiscordRestClientIT.java:204-220`
**Issue:** Commit `4dc42318` changed the WireMock channel-name fixture from `md1-h-vs-a` to `any-name` to
neutralize coincidental coupling with the production channel-name format. The intent — "this test
asserts permission-overwrite parsing, not naming" — is lost without a one-line comment, and
"`any-name`" reads like a placeholder that someone forgot to fill in. The IT also never asserts
`ch.name()`, which makes the choice of placeholder load-bearing only in a negative sense (a future
contributor who adds `assertThat(ch.name()).isEqualTo(...)` would be confused).
**Fix:** Either drop `"name"` from the JSON body entirely (it isn't asserted), or add a single trailing
assertion such as `assertThat(ch.name()).isNotBlank();` to make the placeholder's role explicit. The
hard-banned phase-reference comment style (`// Phase 100 fix: ...`) is the wrong tool — name the
fixture better instead (e.g., `"name":"unused-fixture"`).

---

_Reviewed: 2026-05-28T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
