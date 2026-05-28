---
phase: 93
plan: 01
slug: discord-foundation
status: shipped
shipped: 2026-05-21
requirement: INFRA-01
---

# Plan 93-01 — INFRA-01 Discord foundation (clients + sealed exceptions + rate-limit + cache + timestamps)

Delivered the entire `org.ctc.discord.*` non-config production surface plus its WireMock IT
coverage. First introduction of Spring `RestClient` into the codebase — this plan establishes
the canonical pattern (Bot-Token interceptor chain, per-call typed-catch + `DiscordApiExceptionMapper`
dispatch) that Phases 94–98 consume without re-introducing infrastructure. Zero new production
dependencies; the single new dep is `org.wiremock:wiremock-standalone` test-scope.

## Files modified

| File | Change |
|------|--------|
| `pom.xml` | Added `org.wiremock:wiremock-standalone:3.9.2` `<scope>test</scope>` (single new dep — never reaches production classpath). |
| `src/main/java/org/ctc/discord/util/CachedEntry.java` | `record CachedEntry<T>(T value, Instant expiresAt) { boolean isValid(Clock); }` — generic Clock-injectable TTL record consumed by `DiscordEmojiCache`. |
| `src/main/java/org/ctc/discord/util/BucketState.java` | `record BucketState(int remaining, Instant resetAt)` — Discord rate-limit bucket snapshot used by `DiscordRateLimitInterceptor`. |
| `src/main/java/org/ctc/discord/exception/DiscordApiException.java` | Sealed abstract class `extends IOException permits DiscordTransientException, DiscordAuthException, DiscordNotFoundException, DiscordCategoryFullException`; 4-value `Category` enum (`TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL`). |
| `src/main/java/org/ctc/discord/exception/Discord{Transient,Auth,NotFound,CategoryFull}Exception.java` | 4 `public final class` permits — each overrides `category()`. |
| `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` | 4 user-visible message constants (`TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL_MESSAGE`); `from(RestClientResponseException)` switches 401/403→Auth, 404→NotFound, 400→`from400` (Jackson `readTree` checks `code == 30013` → CategoryFull, else Transient), default→Transient; `from(IOException)` idempotent on already-typed exceptions. No `e.getMessage()` echo (T-91-02-IL invariant preserved). |
| `src/main/java/org/ctc/discord/DiscordConfig.java` | `@Configuration` with `@Bean Clock systemClock()` (first `@Bean Clock` in the codebase) + `@Bean(name="discordBotRestClient") RestClient` factory (`baseUrl` from `app.discord.base-url`, `Authorization: Bot ${app.discord.bot-token}`, `User-Agent: CTC-Manager (...)`, `DiscordRateLimitInterceptor` in the request chain). SSRF host-whitelist guard intentionally NOT here — Plan 93-02 owns that. |
| `src/main/java/org/ctc/discord/DiscordEmojiCache.java` | `@Component @RequiredArgsConstructor @Slf4j` cache backed by `ConcurrentHashMap<String, CachedEntry<String>>`; 60-min TTL via `Duration.ofMinutes(60)`. `emojiFor(shortName)` returns long-form `<:name:id>` on hit, fallback `:name:` literal on miss/expired. `refresh(Map<String,String>)` atomically replaces the store. Cache holds NO reference to `DiscordRestClient`. |
| `src/main/java/org/ctc/discord/DiscordTimestamps.java` | `@Component` at root `org.ctc.discord` package (util/ is reserved for records). Constructor: `(Clock clock, @Value("${app.timezone:Europe/Berlin}") String zoneIdName)`. 6 public methods cover the full Discord style set: `longDateTime/F`, `shortDateTime/f`, `longDate/D`, `shortDate/d`, `shortTime/t`, `relative/R`. Epoch via `dt.atZone(zone).toEpochSecond()`. |
| `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` | `@Component` implementing `ClientHttpRequestInterceptor`. Constants `MAX_429_RETRIES = 3`, default `FIVE_XX_BACKOFF_MS = {200,1000,5000}` (configurable via `app.discord.rate-limit.fivexx-backoff-ms` so ITs run fast). 429: sleep `Retry-After` + jitter (parsed from `app.discord.rate-limit.jitter-ms` `"100-500"` range), retry up to 3x; exhaustion → `DiscordTransientException("Rate-limit exhausted after 3 retries")`. 5xx: schedule-driven backoff; exhaustion → `DiscordTransientException("5xx exhausted after N retries")`. 200 + `X-RateLimit-Bucket` headers update the `ConcurrentHashMap<String, BucketState>` snapshot. **Never calls `response.getBody()`** — body stream stays available for the downstream caller (Spring RestClient body-stream gotcha). 401 passes through. `bucketState(name)` accessor for tests. |
| `src/main/java/org/ctc/discord/DiscordRestClient.java` | `@Component @Slf4j` taking `@Qualifier("discordBotRestClient") RestClient`. 9 typed methods: `fetchBotUser`, `fetchGuildRoles`, `fetchGuildEmojis` (returns `Map<shortName, "<:name:id>">`), `createChannel`, `modifyChannel`, `listChannels`, `listActiveThreads`, `listArchivedThreads`, `createThread`. All funnel through `execute(RestCall<T>)` — routes `RestClientResponseException` through `DiscordApiExceptionMapper`, unwraps `ResourceAccessException` to surface the interceptor's `DiscordApiException` cause. `BotUser` record nested; `Emoji`/`ThreadList` private nested records. |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | `@Component @Slf4j` with `DiscordRateLimitInterceptor` + `ObjectMapper` constructor injection. 3 typed methods: `execute(url, payload)` (JSON POST), `executeMultipart(url, payload, attachments)` (multipart/form-data POST; >10 attachments → `IllegalArgumentException`, 0 attachments → delegates to JSON `execute`), `editMessage(url, mid, payload)` (PATCH `/messages/{id}`). Multipart assembled as `LinkedMultiValueMap<String, HttpEntity<?>>` directly — `MultipartBodyBuilder` was avoided because it references `org.reactivestreams.Publisher` at load time, which would require a new production dep (forbidden by Phase 93 D-03). Per-call `RestClient` via `forWebhookUrl(url)` because webhook URLs carry their own auth in the path segment. |
| `src/main/java/org/ctc/discord/dto/{Role,Channel,Thread,ChannelCreateRequest,ChannelModifyRequest,ThreadCreateRequest,WebhookPayload,WebhookMessage,Embed,EmbedField,NamedAttachment}.java` | 11 Jackson-friendly record DTOs. snake_case ↔ camelCase via `@JsonProperty`. `@JsonIgnoreProperties(ignoreUnknown = true)` on response shapes so Discord can add fields without breaking deserialization. `@JsonInclude(NON_NULL)` on request shapes so omitted Embed fields aren't serialised. |
| `src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java` | 9 `@Test` methods cover the 4-permit dispatch table + idempotent mapping + sealed exhaustiveness. Untagged unit test (pure JUnit + Mockito). |
| `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` | 5 `@Test` methods cover hit/miss/TTL-expiry/refresh/boundary cases. Hand-rolled `MutableClock` so the same cache instance sees time advance between refresh and lookup. |
| `src/test/java/org/ctc/discord/DiscordTimestampsTest.java` | 7 `@Test` methods cover all 6 styles plus a UTC-vs-Berlin epoch divergence check (Berlin in May = UTC+2 DST, 7200s earlier than the same wall-clock LocalDateTime interpreted as UTC). |
| `src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java` | 6 `@Tag("integration")` tests: 429 → retry → 200; 4× 429 → DiscordTransientException; 500 → retry → 200; 4× 5xx → DiscordTransientException; 200 + `X-RateLimit-*` → bucket state updated; 401 → no retry, DiscordAuthException propagates. WireMock dynamic-port + `@DynamicPropertySource` override `app.discord.*`. |
| `src/test/java/org/ctc/discord/DiscordRestClientIT.java` | 13 `@Tag("integration")` tests: 9 happy-path (each typed method) + 4 sealed permits (401/404/400-30013/503-exhausted). |
| `src/test/java/org/ctc/discord/DiscordWebhookClientIT.java` | 5 `@Tag("integration")` tests: `execute` happy + 401/404/503-exhausted + `editMessage`. |
| `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` | 3 `@Tag("integration")` tests: WireMock `aMultipart(...)` matcher asserts `payload_json` (Content-Type: application/json) + `files[0]` / `files[1]` (Content-Type: image/png) parts; empty-attachment fallback; >10-attachment guard. |
| `config/spotbugs-exclude.xml` | 3 new `<Match>` blocks with rationale (per CLAUDE.md SAST suppression discipline): (1) `EI_EXPOSE_REP*` on `~org\.ctc\.discord\.[A-Z].+` — Spring-injected immutable bean references; (2) `EI_EXPOSE_REP*` on `~org\.ctc\.discord\.(dto|util)\..+` — Jackson record DTOs; (3) `CT_CONSTRUCTOR_THROW` on `DiscordRateLimitInterceptor` — boot-time fail-fast on misconfigured config strings. |
| `.planning/config.json` | Aligned `git.milestone_branch_template` to `gsd/v1.13-discord-integration` (was stale at `gsd/v1.11-tooling-and-cleanup`). |
| `.planning/phases/93-discord-foundation/93-01-VALIDATION.md` | Per-plan Nyquist slice per CONTEXT D-08. |

## ./mvnw clean verify -Pe2e summary

- BUILD SUCCESS — total time **07:59 min**.
- Tests run: **1480 surefire** (Failures 0, Errors 0, Skipped 1) + **277 failsafe ITs** (Failures 0, Errors 0, Skipped 2) + **38 Playwright E2E** (Failures 0, Errors 0).
- JaCoCo line coverage: **89.5917 %** (covered=8031, missed=933 — above the 88.88 % v1.11 baseline target and the 82 % pom gate).
- SpotBugs `BugInstance` count: **0** (3 Medium findings on Phase 93 surface are suppressed in `config/spotbugs-exclude.xml` with rationale comments).
- `target/site/jacoco/jacoco.csv` header guard: columns 8/9 confirmed `LINE_MISSED`/`LINE_COVERED`.
- Surefire/Failsafe routing: untagged unit tests under Surefire, `*IT.java` `@Tag("integration")` under Failsafe, `@Tag("e2e")` under `-Pe2e` Failsafe — no tag drift.

## INFRA-01 acceptance

- 8 typed Bot REST methods on `DiscordRestClient` (in addition to `fetchBotUser`) — all routed via `DiscordApiExceptionMapper`. `grep -c "DiscordApiExceptionMapper.from" src/main/java/org/ctc/discord/DiscordRestClient.java` returns **2** (single `execute()` helper centralises both branches); per-method routing happens through the helper — 9 invocations total share 1 helper.
- 3 typed webhook methods on `DiscordWebhookClient`.
- Sealed `DiscordApiException` with exactly 4 permits — `grep -c permits src/main/java/org/ctc/discord/exception/DiscordApiException.java` returns **1**.
- Body-stream gotcha guard: `grep -c "\.getBody()" src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` returns **0**.
- DiscordEmojiCache zero coupling to DiscordRestClient: `grep -c DiscordRestClient src/main/java/org/ctc/discord/DiscordEmojiCache.java` returns **0**.
- WireMock test-scope only: `./mvnw dependency:tree -DincludeScope=runtime | grep -c wiremock` returns **0**.
- Branch identity end-to-end: `git branch --show-current` returns `gsd/v1.13-discord-integration` ✓.

## T-93-04 (Rate-limit burst → Discord-bot-ban) mitigation

Reactive (post-response) rate-limit handling:

- Per-bucket `ConcurrentHashMap<String, BucketState>` keyed by `X-RateLimit-Bucket`.
- 429 retries capped at 3 with `Retry-After` + jitter sleep.
- 5xx exponential backoff schedule (default `200/1000/5000 ms`).
- Exhaustion of either path → `DiscordTransientException` (sealed permit) routes to the caller's typed-catch.

Verification: `DiscordRateLimitInterceptorIT` (6 methods covering all 4 paths + bucket-update + no-retry-on-401).

Residual: pre-request bucket-sleep is deferred per RESEARCH.md Q2 (documented in 93-THREAT-MODEL.md as Plan 93-02 deliverable).

## Notes

- `MultipartBodyBuilder` was the canonical multipart approach in PATTERNS.md / RESEARCH.md but its class file references `org.reactivestreams.Publisher`; the `webmvc` starter does not pull `reactive-streams` transitively in Spring Boot 4.0.6. Adding a production dep was forbidden (D-03), so the code uses `LinkedMultiValueMap<String, HttpEntity<?>>` directly. Same WireMock `aMultipart(...)` matcher contract holds.
- `MAX_429_RETRIES = 3` is hardcoded per CONTEXT.md INFRA-01; `FIVE_XX_BACKOFF_MS` was extracted to `@Value` so ITs can compress backoff to keep test runtime under a second. Production default (`200/1000/5000`) is preserved when the override is absent.
- The 3 SpotBugs suppressions follow the project precedent (admin.dto / backup.dto / sitegen.model exclusion rules) — Spring-injected singleton references + Jackson record DTOs are framework-owned and never genuinely "external mutable" at runtime.

## Rolling Draft milestone PR

- Body update tracked under Task 15 (pending — PR-edit step happens after this commit lands).
EOF
