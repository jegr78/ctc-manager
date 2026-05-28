---
phase: 93-discord-foundation
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 47
files_reviewed_list:
  - config/spotbugs-exclude.xml
  - pom.xml
  - src/main/java/org/ctc/discord/DiscordConfig.java
  - src/main/java/org/ctc/discord/DiscordEmojiCache.java
  - src/main/java/org/ctc/discord/DiscordHostValidator.java
  - src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/main/java/org/ctc/discord/DiscordTimestamps.java
  - src/main/java/org/ctc/discord/DiscordWebhookClient.java
  - src/main/java/org/ctc/discord/dto/Channel.java
  - src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java
  - src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java
  - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/discord/dto/Embed.java
  - src/main/java/org/ctc/discord/dto/EmbedField.java
  - src/main/java/org/ctc/discord/dto/NamedAttachment.java
  - src/main/java/org/ctc/discord/dto/Role.java
  - src/main/java/org/ctc/discord/dto/Thread.java
  - src/main/java/org/ctc/discord/dto/WebhookMessage.java
  - src/main/java/org/ctc/discord/dto/WebhookPayload.java
  - src/main/java/org/ctc/discord/exception/DiscordApiException.java
  - src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java
  - src/main/java/org/ctc/discord/exception/DiscordAuthException.java
  - src/main/java/org/ctc/discord/exception/DiscordCategoryFullException.java
  - src/main/java/org/ctc/discord/exception/DiscordNotFoundException.java
  - src/main/java/org/ctc/discord/exception/DiscordTransientException.java
  - src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java
  - src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java
  - src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java
  - src/main/java/org/ctc/discord/util/BucketState.java
  - src/main/java/org/ctc/discord/util/CachedEntry.java
  - src/main/java/org/ctc/discord/web/DiscordConfigController.java
  - src/main/resources/application-local.yml
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V8__discord_global_config.sql
  - src/main/resources/logback-spring.xml
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/templates/admin/discord-config.html
  - src/main/resources/templates/admin/layout.html
  - src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java
  - src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java
  - src/test/java/org/ctc/discord/DiscordLogMaskingIT.java
  - src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java
  - src/test/java/org/ctc/discord/DiscordRestClientIT.java
  - src/test/java/org/ctc/discord/DiscordTimestampsTest.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientIT.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java
  - src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java
  - src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java
  - src/test/java/org/ctc/discord/model/DiscordGlobalConfigToStringTest.java
  - src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerTest.java
  - src/test/java/org/ctc/e2e/PlaywrightConfig.java
  - src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java
  - src/test/resources/logback-test.xml
findings:
  critical: 0
  warning: 6
  info: 8
  total: 14
status: issues_found
---

# Phase 93: Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 47
**Status:** issues_found

## Summary

Phase 93 (Discord Foundation) builds the SSRF-guarded REST/Webhook clients,
the sealed exception hierarchy, the rate-limit interceptor, emoji/role/bot
caches, the V8 `discord_global_config` migration, the admin UI for config,
log masking, and an E2E surface check. The submitted code is in good
overall shape: structured exception mapping is sound, Spring-native
abstractions are used everywhere (no JDK `HttpClient`), WireMock-vs-real-
API discipline is largely followed (`withQueryParam("wait", "true")`
asserted in both `DiscordWebhookClientIT` and `DiscordWebhookClientMultipartIT`),
and the host-whitelist defense is exercised from both the bot client and
the webhook client.

The findings below cluster around three themes:

1. **Rate-limit interceptor robustness** — unchecked `Integer.parseInt` /
   `Double.parseDouble` of attacker-controllable HTTP headers can blow
   the whole retry loop (WR-01); `new Random()` is shared across threads
   with no need (IN-04).
2. **Code duplication & drift risk** — `USER_AGENT_VALUE` and the giant
   multipart-build block are duplicated between `DiscordConfig` and
   `DiscordWebhookClient` and between `executeMultipart` /
   `editMessageWithAttachments` (WR-02, WR-03). The hardcoded `"1.13"`
   version string desynchronizes from `pom.xml`/`@project.version@`
   (WR-04).
3. **Comment pollution & dead code** — Flyway migrations carry
   file-header comment blocks restating CLAUDE.md conventions (IN-01),
   `DiscordTimestamps.clock()` is an unused package-private accessor
   (IN-02), and the V8 INSERT relies on column defaults that later
   migrations append to (IN-08).

No Critical (BLOCKER) issues. CSRF, SSRF, log masking, sealed-exception
exhaustiveness, and JPA round-trip semantics all look correct under the
tests provided.

## Warnings

### WR-01: Rate-limit interceptor crashes on malformed Discord headers

**File:** `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java:96-97`
**Issue:** `updateBucket` parses three headers from the response with
unchecked numeric conversions:
```java
int remaining = remainingStr == null ? 0 : Integer.parseInt(remainingStr);
double resetAfter = resetAfterStr == null ? 0.0 : Double.parseDouble(resetAfterStr);
```
A malformed `X-RateLimit-Remaining` or `X-RateLimit-Reset-After` (e.g.
empty string, `"unknown"`, or a non-numeric Cloudflare interstitial)
throws `NumberFormatException` out of `intercept(...)`. Because the
interceptor calls `updateBucket(response.getHeaders())` BEFORE the
status-code branches (line 54), this RuntimeException escapes the
`ClientHttpRequestInterceptor` contract on every code path — including
otherwise-successful 200s. The downstream `DiscordRestClient.execute`
catches `RestClientResponseException` and `ResourceAccessException`,
but NOT `NumberFormatException` — so the operator gets a 500 with the
raw stack trace instead of a friendly `TRANSIENT` flash. Compare with
`parseRetryAfterMs` (lines 102-113), which DOES wrap `Double.parseDouble`
in try/catch and falls back to 0L.

**Fix:**
```java
private void updateBucket(HttpHeaders headers) {
    String bucket = headers.getFirst("X-RateLimit-Bucket");
    if (bucket == null || bucket.isBlank()) {
        return;
    }
    int remaining = parseIntSafe(headers.getFirst("X-RateLimit-Remaining"), 0);
    double resetAfter = parseDoubleSafe(headers.getFirst("X-RateLimit-Reset-After"), 0.0);
    long resetAtMillis = clock.instant().toEpochMilli() + Math.round(resetAfter * 1000.0);
    buckets.put(bucket, new BucketState(remaining, Instant.ofEpochMilli(resetAtMillis)));
}

private static int parseIntSafe(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
        return defaultValue;
    }
    try {
        return Integer.parseInt(value.trim());
    } catch (NumberFormatException _) {
        return defaultValue;
    }
}
// analogous parseDoubleSafe
```
Add a unit test that returns `X-RateLimit-Remaining: garbage` and asserts
the response still surfaces normally (200 propagates, 429 retries).

### WR-02: USER_AGENT_VALUE duplicated between DiscordConfig and DiscordWebhookClient

**File:** `src/main/java/org/ctc/discord/DiscordConfig.java:15-16`
            `src/main/java/org/ctc/discord/DiscordWebhookClient.java:32-33`
**Issue:** Both classes declare the identical literal
```java
private static final String USER_AGENT_VALUE =
        "CTC-Manager (https://github.com/jegr78/ctc-manager, 1.13)";
```
The next release bump (v1.14) will require editing both copies; a
single missed copy gives Discord two different User-Agents from the
same process — invisible until Discord rate-limit groups them
separately or the abuse-team asks which version is which.

**Fix:** Extract to a single source of truth. Either:
- a package-private `DiscordUserAgent.VALUE` constant referenced by both, or
- (preferred) inject via `@Value("${app.discord.user-agent:CTC-Manager (https://github.com/jegr78/ctc-manager, ${app.version})}")`
  so the version follows `pom.xml` automatically (see WR-04).

### WR-03: Multipart upload block duplicated between executeMultipart and editMessageWithAttachments

**File:** `src/main/java/org/ctc/discord/DiscordWebhookClient.java:71-123` vs `147-210`
**Issue:** The 35-line block that
1. Serializes `WebhookPayload` to JSON
2. Wraps in `HttpEntity` with `Content-Type: application/json`
3. Iterates `attachments` building `ByteArrayResource` anonymous subclasses
4. Posts the `MultiValueMap` to the rate-limited webhook
   is duplicated between `executeMultipart` and `editMessageWithAttachments`.
   The only difference is the verb (`post` vs `patch`), the URI builder
   suffix (`""` vs `"/messages/{messageId}"`), and that `editMessageWithAttachments`
   adds an `attachments[]` array to the payload JSON. A bug fix in one
   (e.g., MIME-type detection beyond `image/png`) will silently miss
   the other.

**Fix:** Extract a private helper:
```java
private MultiValueMap<String, HttpEntity<?>> buildMultipart(
        String payloadJson, List<NamedAttachment> attachments) {
    MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
    HttpHeaders payloadHeaders = new HttpHeaders();
    payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
    parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
    for (int i = 0; i < attachments.size(); i++) {
        NamedAttachment att = attachments.get(i);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
            @Override public String getFilename() { return att.filename(); }
        };
        parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
    }
    return parts;
}
```
and call it from both methods.

### WR-04: Hardcoded "1.13" in User-Agent desynchronizes from pom version

**File:** `src/main/java/org/ctc/discord/DiscordConfig.java:16`
            `src/main/java/org/ctc/discord/DiscordWebhookClient.java:33`
**Issue:** The User-Agent embeds the literal string `"1.13"`. The
project already exposes `@project.version@` via `app.version` in
`application.yml:3` — the User-Agent should derive from that, not
duplicate it. When v1.14 ships, the User-Agent will lie about the
client version and Discord's abuse-pipeline logs become misleading.

**Fix:** Inject `@Value("${app.version}")` (or read once at startup
into a `DiscordUserAgent` Spring bean) and template the literal:
```java
@Bean
public String discordUserAgent(@Value("${app.version}") String appVersion) {
    return "CTC-Manager (https://github.com/jegr78/ctc-manager, " + appVersion + ")";
}
```
Inject this bean into both `DiscordConfig#discordBotRestClient` and
`DiscordWebhookClient#forWebhookUrl`.

### WR-05: DiscordWebhookClient.forWebhookUrl rebuilds RestClient on every call

**File:** `src/main/java/org/ctc/discord/DiscordWebhookClient.java:219-225`
**Issue:** `forWebhookUrl(webhookUrl)` is called from every
`execute / executeMultipart / editMessage / editMessageWithAttachments`
invocation, and each call constructs a fresh `RestClient.builder()` ➜
`build()` graph. Performance is out of scope, but the more pressing
problem is that the builder-chain bypasses any future
`@RequestInterceptor` or `defaultStatusHandler` wiring that a
`@Configuration`-managed bean would inherit. There is also no
guarantee that `webhookUrl` was host-validated before the builder
sees it — the four public methods DO call
`hostValidator.requireAllowed(webhookUrl)` before this, but a future
caller that forgets the guard will silently send the request anyway
because the builder accepts any URL.

**Fix:** Centralize webhook-client construction in `DiscordConfig` and
keep `forWebhookUrl` as a single setter, OR guard `forWebhookUrl`
itself: `hostValidator.requireAllowed(webhookUrl);` as the first line.
The second option is the safer narrow fix because it makes the
defense local to the builder.

### WR-06: DiscordConfigControllerErrorCategoryTest does not cover MISSING_PERMISSIONS

**File:** `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java:11-17`
**Issue:** The `@CsvSource` enumerates `TRANSIENT, AUTH, NOT_FOUND,
CATEGORY_FULL` but omits `MISSING_PERMISSIONS`. The controller's
`applyErrorFlash` switch DOES handle it (see
`DiscordConfigController.java:146`), and the matching CSS class
`.error-badge--missing-permissions` exists in `admin.css:402`, so the
test gap allows a silent regression: a future refactor that drops the
MISSING_PERMISSIONS branch from the switch would not be caught here,
and a developer reading this test would conclude the controller only
emits four categories. Sealed `Category` exhaustiveness is the safety
net at compile time, but the BEM-class-suffix contract is asserted only
in this test.

**Fix:** Extend the `@CsvSource`:
```java
@CsvSource({
    "TRANSIENT,transient",
    "AUTH,auth",
    "MISSING_PERMISSIONS,missing-permissions",
    "NOT_FOUND,not-found",
    "CATEGORY_FULL,category-full"
})
```

## Info

### IN-01: Flyway migration file-header comment blocks restate CLAUDE.md conventions

**File:** `src/main/resources/db/migration/V8__discord_global_config.sql:1-4`
            `src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql:1-4`
            `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql:1-4`
**Issue:** Each migration file opens with a comment block of the
form "Compatible with H2 2.x and MariaDB 10.7+ … DO NOT mutate this
file after release (CLAUDE.md "Do Not Modify Flyway Migrations")." This
is exactly the file-header "restating-conventions" pattern that
CLAUDE.md ➜ Conventions ➜ "No Comment Pollution" hard-bans:
"file-header comment blocks restating what the file does or repeating
conventions … Conventions belong here in CLAUDE.md once, not in every
file." The Flyway gate is repo-wide; one rule in CLAUDE.md is enough.

**Fix:** Strip the file-header blocks. Keep only the SQL. CLAUDE.md
remains the canonical source for the no-mutation rule.

### IN-02: DiscordTimestamps.clock() package-private accessor is unused

**File:** `src/main/java/org/ctc/discord/DiscordTimestamps.java:44-46`
**Issue:** The package-private method
```java
Clock clock() { return clock; }
```
is not referenced from any production code or test (grep across
`src/**` returns zero hits). Dead code.

**Fix:** Delete the method. If a future test needs the clock, inject a
`Clock` mock directly via the constructor.

### IN-03: DiscordEmojiCache.refresh briefly empties the cache during refresh

**File:** `src/main/java/org/ctc/discord/DiscordEmojiCache.java:36-37`
**Issue:** `store.clear()` followed by `store.putAll(next)` creates a
window where concurrent readers see an empty map and fall back to the
literal `:name:` form even though the cache is supposed to be fresh.
Not a Critical bug because the fallback is correct behavior and the
window is tiny, but the comment-free intent (atomic-replace) is not
what the code does.

**Fix:** Either replace the underlying `ConcurrentHashMap` reference
(make `store` `volatile Map<...>`), or document the gap with a single
"WHY" comment per CLAUDE.md (it IS a non-obvious invariant since the
test `givenRefresh_whenCalled_thenReturnsEntryCountAndReplacesMap`
operates single-threaded and cannot expose the race).

### IN-04: DiscordRateLimitInterceptor uses shared java.util.Random

**File:** `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java:33,119`
**Issue:** `private final Random random = new Random();` is shared
across all interceptor threads. `Random.nextDouble()` is thread-safe but
suffers contention (a CAS spin on its internal seed). The jitter use-case
has no requirement for reproducibility, so `ThreadLocalRandom.current()`
is strictly better in concurrent code and removes the field entirely.

**Fix:**
```java
private long jitterMs() {
    if (jitterMaxMs <= jitterMinMs) {
        return jitterMinMs;
    }
    return jitterMinMs +
            (long) (ThreadLocalRandom.current().nextDouble() * (jitterMaxMs - jitterMinMs));
}
```
and drop the `Random` field.

### IN-05: WebhookCreateRequest inner record uses constructor-name shadowing

**File:** `src/main/java/org/ctc/discord/DiscordRestClient.java:169`
**Issue:** `private record WebhookCreateRequest(String name)` is the
exact name shadow of `org.ctc.discord.dto.WebhookCreateRequest` if a
future phase introduces one (e.g., for webhook avatar). Subtle
import-collision bug-source.

**Fix:** Inline the JSON body (`Map.of("name", name)`) or rename the
inner record `ChannelWebhookCreateBody` to make the scope obvious.

### IN-06: DiscordTimestampsTest has Javadoc explaining test mechanics

**File:** `src/test/java/org/ctc/discord/DiscordTimestampsTest.java:12-28`
            `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java:11-20`
            `src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java:15-28`
**Issue:** Multi-line class-level Javadoc blocks restating "RED phase:
fails to compile until Task X lands…". This is the
Phase/Plan/Task/Wave reference pattern that CLAUDE.md ➜ Conventions ➜
"No Comment Pollution" hard-bans in tests: "Phase / Plan / Task / UAT /
Wave references … they rot — use git history and PR descriptions
instead."

**Fix:** Strip the Javadoc blocks. The test method names already
express the contract.

### IN-07: DiscordWebhookClient @Nullable import sits in the Spring import block out of order

**File:** `src/main/java/org/ctc/discord/DiscordWebhookClient.java:15-26`
**Issue:** `import org.jspecify.annotations.Nullable;` (line 19) is
interleaved among the `org.springframework.*` imports. CONVENTIONS.md
alphabetical-import-order will flag this on any future
`maven-checkstyle-plugin` pass. Pure style.

**Fix:** Move the `org.jspecify` import above the `org.springframework`
block (alphabetical group order: `org.ctc` ➜ `org.jspecify` ➜
`org.springframework`).

### IN-08: V8 migration INSERT will fail if applied to a database where V8 ran on the original schema

**File:** `src/main/resources/db/migration/V8__discord_global_config.sql:18-21`
**Issue:** The V8 `INSERT` populates only the 8 columns present at V8
time. V9, V13, and V15 then `ALTER TABLE ... ADD COLUMN` for
`current_match_category_id NOT NULL DEFAULT ''`,
`race_results_forum_webhook_url`, `standings_forum_webhook_url`,
`discord_race_results_thread_id`, `discord_standings_thread_id`, and
`matchday_pairings_template`. This is fine in practice because V9/V13/V15
specify a NULL-safe / `DEFAULT ''` clause on each new column, but it is
the kind of cross-migration dependency that is invisible to anyone
reading V8 in isolation. A future migration that adds a NOT NULL column
without DEFAULT will break the singleton seed-row invariant tested by
`DiscordGlobalConfigRepositoryIT.givenFreshMigration_whenCount_thenExactlyOneRow`
only if Flyway-baseline is fresh — silent on existing prod.

**Fix:** Defensive: prefer `INSERT ... ON DUPLICATE KEY UPDATE` style
in future, but per CLAUDE.md "Do Not Modify Flyway Migrations" do NOT
touch V8. Add a `DiscordGlobalConfigRepositoryIT` test that asserts the
singleton row passes a full-field `assertThatNoException()` round trip
through every entity field — this would catch the omission at the
phase that introduces it.

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
