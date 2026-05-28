# Phase 93: Discord Foundation - Pattern Map

**Mapped:** 2026-05-21
**Files analyzed:** ~35 (12 Plan 93-01, 5 Plan 93-02, 13 Plan 93-03, plus tests)
**Analogs found:** 11 / 13 critical analog targets (with concrete excerpts); 2 documented as "no analog — Phase 93 introduces" (Spring `RestClient`, Spring `@Bean Clock`).

## Executive Summary

Phase 93 is overwhelmingly a **verbatim structural port** of established CTC patterns, with one genuinely new dimension: it is the **first introduction** of Spring 6.1+ `RestClient` into the codebase. There is no existing `RestClient` / `RestTemplate` / `java.net.http.HttpClient` usage anywhere under `src/main/java/org/ctc/` (verified by grep) — every existing outbound API (Google Sheets, Google Calendar, GT7 scraper) goes through SDK-specific clients, not generic HTTP. Plan 93-01 therefore writes the canonical `RestClient` template that Phases 94+ will copy.

Beyond that one novelty, Phase 93 is mechanical:

- **Sealed exception hierarchy + Mapper + 4 permits + typed-catch + flash-attribute UX** — 1:1 port of Phase 91 `org.ctc.dataimport.exception.*` to `org.ctc.discord.exception.*`. Swap `GoogleJsonResponseException` for `RestClientResponseException`, swap `Permission` permit for `CategoryFull`, swap Google SDK error-reason inspection for Discord JSON `code` field inspection. The Mapper class signature, sealed-class shape, permit-class shape, hardcoded user-message constants, and Phase 91 D-07 flash-attribute pattern (`errorMessage` + `errorCategory` + BEM `.error-badge--{category}` CSS) all carry forward unchanged.
- **SSRF positive-whitelist** — inverts the existing `FileStorageService.validateHostname` (Phase 5/12) negative blocklist into a positive whitelist (`discord.com` only). Same `URI.create(url).getHost().toLowerCase()` shape; same CodeQL FP 3-layer suppression invariant available if CodeQL flags the new constructor guard.
- **Flyway V8 + service-side singleton** — V8 follows the `V7__data_import_audit.sql` shape (CURRENT_TIMESTAMP literal, snake_case columns, no LONGTEXT to avoid Phase 72 D-09 drift); seed-row INSERT in same file. Repository pattern follows `DataImportAuditRepository` minimalism (`extends JpaRepository<E, ID>` with a single named-method finder `findFirstByOrderByIdAsc()` — currently zero such finders exist; Phase 93 introduces the pattern).
- **Form DTO + Controller + Thymeleaf** — `MatchdayForm` shape ports verbatim (`@Getter @Setter @NoArgsConstructor` + `jakarta.validation.constraints`); `DriverSheetImportController` provides the EXACT typed-catch + `errorCategory` flash dispatch template; `admin/layout.html` is the canonical flash-attribute renderer at lines 85-92. Existing `.error-badge--{auth,transient,not-found,permission}` palette is at `admin.css:371-374` — Phase 93 adds one new `.error-badge--category-full` variant.
- **Playwright E2E + WireMock** — `PlaywrightConfig` parent class (RANDOM_PORT + `setupPage()/teardownPage()`) is the reused base; `AdminWorkflowE2ETest` shows the canonical `@Tag("e2e")` + Page setup. **WireMock is NOT currently a project dependency** (zero hits across `src/test/java/`) — Plan 93-01 introduces `org.wiremock:wiremock-standalone` as test-scope.

The two "no existing analog" surfaces (Spring `RestClient`, `@Bean Clock systemClock()`) are documented in the per-file mapping with reference to RESEARCH.md code sketches as the substitute template.

## File Classification

### Plan 93-01 — INFRA-01 Discord Clients + Utilities

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/discord/DiscordConfig.java` | config | bean-wiring | `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` | role-match (no RestClient analog) |
| `src/main/java/org/ctc/discord/DiscordRestClient.java` | api-client | request-response | RESEARCH.md sketch (Q1) | **no analog — first RestClient in codebase** |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | api-client | request-response + multipart | RESEARCH.md sketch (Q1, Q4) | **no analog — first RestClient in codebase** |
| `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` | middleware | request-response | RESEARCH.md sketch (Q2) | **no analog — first ClientHttpRequestInterceptor in codebase** |
| `src/main/java/org/ctc/discord/exception/DiscordApiException.java` | exception | n/a (type hierarchy) | `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` | exception | transform | `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordTransientException.java` | exception | n/a (type) | `src/main/java/org/ctc/dataimport/exception/TransientGoogleApiException.java` | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordAuthException.java` | exception | n/a (type) | `src/main/java/org/ctc/dataimport/exception/AuthGoogleApiException.java` | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordNotFoundException.java` | exception | n/a (type) | `src/main/java/org/ctc/dataimport/exception/NotFoundGoogleApiException.java` | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordCategoryFullException.java` | exception | n/a (type) | `src/main/java/org/ctc/dataimport/exception/PermissionGoogleApiException.java` | role-match (sibling permit, different status-code mapping) |
| `src/main/java/org/ctc/discord/DiscordEmojiCache.java` | utility | cache | RESEARCH.md sketch (D-03) | **no analog — hand-rolled ConcurrentHashMap cache is new pattern** |
| `src/main/java/org/ctc/discord/DiscordTimestamps.java` | utility | pure-fn | RESEARCH.md sketch (Q12) | **no analog — first time/clock service in codebase** (root `org.ctc.discord` package — Spring beans live there; `util` is reserved for records) |
| `src/main/java/org/ctc/discord/util/CachedEntry.java` | utility (record) | n/a (record) | n/a | new |
| `src/main/java/org/ctc/discord/util/BucketState.java` | utility (record) | n/a (record) | n/a | new |

### Plan 93-02 — INFRA-02 Threat Model + Security Surfaces

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | documentation | n/a | `docs/security/sast-acceptance.md` | role-match |
| `src/main/resources/application.yml` (modified — add `app.discord.*` + `logging.pattern`) | config | n/a | existing `application.yml` lines 38-43 (`google.calendar.id: ${GOOGLE_CALENDAR_ID:}`) | **exact env-var pattern** |
| `src/main/resources/application-local.yml` (optional doc-only modification) | config | n/a | existing `application-local.yml` | role-match |
| Constructor guards in `DiscordRestClient` / `DiscordWebhookClient` | api-client (security) | request-validation | `src/main/java/org/ctc/domain/service/FileStorageService.java` lines 126-159 (`validateHostname`) | **exact pattern, inverted polarity** |
| `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` | test (integration) | log-snapshot | none (new IT pattern with `OutputCaptureExtension`) | role-match |
| `src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java` | test (unit) | constructor-validation | none directly; conceptual sibling to FileStorageServiceTest | role-match |

### Plan 93-03 — INFRA-03 Admin Config Page + V8

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V8__discord_global_config.sql` | migration | DDL + seed INSERT | `src/main/resources/db/migration/V7__data_import_audit.sql` | **exact** |
| `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` | entity | n/a | `src/main/java/org/ctc/backup/audit/DataImportAudit.java` (out-of-`org.ctc.domain.model.*` precedent) + `src/main/java/org/ctc/domain/model/Team.java` (`@ToString(exclude)` pattern) | hybrid match |
| `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java` | repository | CRUD + singleton-finder | `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java` | **exact base; new finder method** |
| `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` | service | CRUD + bean-orchestration | service classes generally (`@Service @RequiredArgsConstructor @Slf4j`) | role-match |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | dto | form-bind | `src/main/java/org/ctc/admin/dto/MatchdayForm.java` | **exact** |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | controller | request-response + flash | `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (typed-catch on sealed `GoogleApiException`) | **exact (Phase 91 D-06/D-07 carry-forward)** |
| `src/main/resources/templates/admin/discord-config.html` | template | view | `src/main/resources/templates/admin/driver-import.html` + `admin/layout.html:85-92` (flash renderer) | role-match |
| `src/main/resources/templates/admin/layout.html` (modified — add nav link) | template (fragment) | n/a | existing `<div class="sidebar-group">` blocks at lines 45-77 | **exact** |
| `src/main/resources/static/admin/css/admin.css` (modified — add `.error-badge--category-full`) | stylesheet | n/a | existing `.error-badge--*` at lines 371-374 | **exact** |
| `src/test/java/org/ctc/discord/DiscordGlobalConfigRepositoryIT.java` | test (integration) | DB | `src/test/java/db/migration/V7DataImportAuditMigrationIT.java` | role-match |
| `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` | test (integration) | MockMvc | `src/test/java/org/ctc/backup/BackupControllerIT.java` | **exact** |
| `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` | test (e2e) | Playwright | `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` | **exact (extends PlaywrightConfig)** |

---

## Pattern Assignments

### Plan 93-01 — INFRA-01 Discord Clients + Utilities

#### `src/main/java/org/ctc/discord/exception/DiscordApiException.java` (exception, sealed base)

**Closest analog:** `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` (lines 1-25)
**Match quality:** EXACT — port verbatim, swap names + permits

**Full file excerpt** (verbatim Phase 91 template):

```java
package org.ctc.dataimport.exception;

import java.io.IOException;

/**
 * Sealed base for the four typed Google API failure modes surfaced to the operator
 * UI as categorized flash badges. Extends {@link IOException} so existing
 * {@code catch (IOException ...)} sites remain backward-compatible while typed
 * catches at the controller boundary can dispatch on the four exhaustive subtypes.
 */
public abstract sealed class GoogleApiException extends IOException
		permits TransientGoogleApiException,
		        AuthGoogleApiException,
		        NotFoundGoogleApiException,
		        PermissionGoogleApiException {

	public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }

	protected GoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public abstract Category category();
}
```

**Adaptation notes:**
- Package: `org.ctc.dataimport.exception` → `org.ctc.discord.exception`.
- Permits: `Permission` → `CategoryFull` (other 3 permits keep their `Transient/Auth/NotFound` names but with the `Discord` prefix).
- `Category` enum: `PERMISSION` → `CATEGORY_FULL`.
- `extends IOException` is invariant — preserves existing `catch (IOException)` compatibility AND lets the `DiscordRateLimitInterceptor` (which implements `ClientHttpRequestInterceptor`) throw `DiscordTransientException` from its `IOException`-declaring `intercept(...)` method (research § Q2 critical design note).
- All 5 files (base + 4 permits + Mapper) MUST sit in the same package for the sealed-permit module rule (no `module-info.java` in CTC; co-located in `org.ctc.discord.exception` is the simplest viable layout — matches Phase 91 exactly).

---

#### `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` (exception, transform)

**Closest analog:** `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java` (lines 1-75)
**Match quality:** EXACT structurally; HTTP-input differs (Spring `RestClientResponseException` vs Google SDK `GoogleJsonResponseException`)

**Hardcoded user-message constants pattern** (lines 23-31):

```java
/** Hardcoded user-visible message strings — mirrored verbatim in
 * controllers and {@code docs/operations/google-integration.md § Error
 * Categories}. */
public static final String TRANSIENT_MESSAGE = "Connection problem — retry";
public static final String AUTH_MESSAGE = "Authentication problem — re-link Google account";
public static final String NOT_FOUND_MESSAGE = "Sheet not found — check ID";
public static final String PERMISSION_MESSAGE =
		"Access denied — share the sheet with the service account";
```

**Private-constructor + static `from(...)` pattern** (lines 35-58):

```java
private GoogleApiExceptionMapper() {
}

public static GoogleApiException from(IOException e) {
	if (e instanceof GoogleJsonResponseException gjre) {
		return fromGoogleJson(gjre);
	}
	return new TransientGoogleApiException(TRANSIENT_MESSAGE, e);
}

public static AuthGoogleApiException from(GeneralSecurityException e) {
	return new AuthGoogleApiException(AUTH_MESSAGE, e);
}

private static GoogleApiException fromGoogleJson(GoogleJsonResponseException gjre) {
	// 408, 429, 5xx, and unknown status codes all map to TRANSIENT (lenient default);
	// only 401 / 403 / 404 dispatch to specific subtypes per RESEARCH § Pattern 2.
	return switch (gjre.getStatusCode()) {
		case 401 -> new AuthGoogleApiException(AUTH_MESSAGE, gjre);
		case 403 -> from403(gjre);
		case 404 -> new NotFoundGoogleApiException(NOT_FOUND_MESSAGE, gjre);
		default -> new TransientGoogleApiException(TRANSIENT_MESSAGE, gjre);
	};
}
```

**Adaptation notes:**
- Input exception type: `GoogleJsonResponseException` → `org.springframework.web.client.RestClientResponseException`.
- Status code accessor: `gjre.getStatusCode()` (int) → `e.getStatusCode().value()` (Spring `HttpStatusCode.value()` returns int).
- Sub-dispatch on `403`: Google has `AUTH_REASONS` Set check via `gjre.getDetails().getErrors().get(0).getReason()` — Phase 93 removes this branch (Discord 403 always means AUTH/permission, no Google-style multi-reason discrimination).
- New sub-dispatch on `400`: parse `e.getResponseBodyAsString()` via Jackson `ObjectMapper.readTree(...)`; if root `code` field equals `30013` → throw `DiscordCategoryFullException`. Otherwise → `DiscordTransientException`. (research § Q3).
- 4 user-message constants are new wording per RESEARCH.md § Q3: `TRANSIENT_MESSAGE`, `AUTH_MESSAGE`, `NOT_FOUND_MESSAGE`, `CATEGORY_FULL_MESSAGE`.
- Constants are referenced verbatim from `DiscordConfigController.applyErrorFlash(...)` (no `e.getMessage()` echoed — T-91-02-IL info-leak invariant carries forward).

---

#### `src/main/java/org/ctc/discord/exception/DiscordTransientException.java` (exception permit) — and the 3 sibling permits

**Closest analog:** `src/main/java/org/ctc/dataimport/exception/TransientGoogleApiException.java` (lines 1-17)
**Match quality:** EXACT — port verbatim, swap class name + Category

**Full file excerpt** (verbatim — duplicate the same shape for all 4 permits):

```java
package org.ctc.dataimport.exception;

/**
 * Retry-friendly Google API failure: network/socket errors, 408/429/5xx
 * responses, and unrecognised status codes default here.
 */
public final class TransientGoogleApiException extends GoogleApiException {

	public TransientGoogleApiException(String message, Throwable cause) {
		super(message, cause);
	}

	@Override
	public Category category() {
		return Category.TRANSIENT;
	}
}
```

**Adaptation notes:**
- `final class` is invariant (sealed-permit requirement).
- 4 Phase-93 analogs:
  - `DiscordTransientException` — 5xx, 429-exhausted, network IO.
  - `DiscordAuthException` — 401 + 403 (Discord-403 always means auth/permission, no sub-dispatch).
  - `DiscordNotFoundException` — 404.
  - `DiscordCategoryFullException` — 400 + JSON `code=30013` (Discord's `Maximum number of channels in category reached`).
- Each permit overrides `category()` returning the matching `Category` enum constant.

---

#### `src/main/java/org/ctc/discord/DiscordConfig.java` (config, bean-wiring)

**Closest analog:** `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` (lines 1-72) — generic `@Configuration` with `@Bean` factory methods
**Match quality:** ROLE-MATCH — Discord-specific beans (`Clock`, two named `RestClient` beans, `Set<String> allowedHosts`) have no direct analog; structural shape carries

**Pattern excerpt** (analog from `BackupObjectMapperConfig.java` lines 38-71):

```java
@Configuration
public class BackupObjectMapperConfig {

    @Bean
    @Primary
    public ObjectMapper defaultObjectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }

    @Bean
    @Qualifier("backupObjectMapper")
    public ObjectMapper backupObjectMapper(List<Module> backupMixInModules) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        backupMixInModules.forEach(mapper::registerModule);
        return mapper;
    }
}
```

**Adaptation notes** (RESEARCH.md § Q1 is the authoritative template):
- `DiscordConfig` declares `@Bean Clock systemClock()` returning `Clock.systemUTC()` — **NO existing `@Bean Clock` definition anywhere in `src/main/java/org/ctc/`** (verified by grep). Phase 93 is the first introduction.
- `@Bean(name = "discordBotRestClient") RestClient discordBotRestClient(...)` — first `RestClient` in the codebase (verified — zero matches for `RestClient.builder` / `RestClient.create` / `RestTemplate` / `HttpClient` across `src/main/java/`). RESEARCH.md § Q1 provides the canonical builder chain: `RestClient.builder().baseUrl(...).defaultHeader(AUTHORIZATION, "Bot " + token).defaultHeader(USER_AGENT, "CTC-Manager (...)").requestInterceptor(rateLimitInterceptor).build()`.
- Constructor enforces SSRF positive-whitelist before builder runs (see § Shared Patterns / SSRF Positive Whitelist).
- `@Qualifier` discriminates `discordBotRestClient` (Bot-Token-authed, `/api/v10` base) from the per-call webhook-builder used by `DiscordWebhookClient`.

---

#### `src/main/java/org/ctc/discord/DiscordRestClient.java` (api-client, request-response)

**Closest analog:** **NONE in codebase** — Phase 93 is the first `RestClient` introduction.
**Substitute reference:** RESEARCH.md § Q1 (canonical builder chain) + § Q3 typed-catch call site.

**Pattern excerpt** (from RESEARCH.md § Q3 — typed-catch wraps every typed method):

```java
public DiscordChannelDto createChannel(String guildId, ChannelCreateRequest req)
        throws DiscordApiException {
    try {
        return bot.post()
                .uri("/guilds/{gid}/channels", guildId)
                .body(req)
                .retrieve()
                .body(DiscordChannelDto.class);
    } catch (RestClientResponseException e) {
        throw DiscordApiExceptionMapper.from(e);
    } catch (DiscordApiException e) {
        // Interceptor already threw — re-throw unchanged
        throw e;
    } catch (IOException e) {
        throw DiscordApiExceptionMapper.from(e);
    }
}
```

**Adaptation notes:**
- Class is a `@Component` injected with `@Qualifier("discordBotRestClient")` `RestClient bot` (the bean from `DiscordConfig`).
- 8 typed methods (per CONTEXT.md INFRA-01 scope): `fetchBotUser()`, `fetchGuildRoles(guildId)`, `fetchGuildEmojis(guildId)`, `createChannel(guildId, req)`, `modifyChannel(channelId, req)`, `listChannels(guildId)`, `listActiveThreads(guildId)`, `listArchivedThreads(channelId)`, `createThread(channelId, req)`.
- Every method wraps the `RestClient` call in the typed-catch pattern above — Mapper handles all dispatch.
- Phase 91 NO-INFO-LEAK invariant: never echo `e.getMessage()` outward; only `DiscordApiExceptionMapper.*_MESSAGE` constants reach the UI.

---

#### `src/main/java/org/ctc/discord/DiscordWebhookClient.java` (api-client, request-response + multipart)

**Closest analog:** **NONE in codebase** — Phase 93 first `RestClient`; multipart-via-`MultipartBodyBuilder` also first introduction (`@Multipart`-style entity builders only exist transitively via Spring's WebMvc dispatcher).
**Substitute reference:** RESEARCH.md § Q1 webhook builder, § Q4 multipart contract.

**Multipart pattern excerpt** (from RESEARCH.md § Q4):

```java
MultipartBodyBuilder builder = new MultipartBodyBuilder();
builder.part("payload_json", payloadJson, MediaType.APPLICATION_JSON);
for (int i = 0; i < attachments.size(); i++) {
    NamedAttachment att = attachments.get(i);
    builder.part("files[" + i + "]",
            new ByteArrayResource(att.bytes()) {
                @Override public String getFilename() { return att.filename(); }
            },
            MediaType.IMAGE_PNG);
}
MultiValueMap<String, HttpEntity<?>> parts = builder.build();
return forWebhookUrl(webhookUrl)
        .post()
        .uri("")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(DiscordWebhookMessageDto.class);
```

**Adaptation notes:**
- Per-URL `RestClient` construction (webhook URLs carry their own auth via the `/{id}/{token}` path segment — no shared `Authorization` header).
- Constructor / `forWebhookUrl(url)` factory enforces SSRF positive-whitelist (same shape as Bot client; see § Shared Patterns / SSRF Positive Whitelist).
- 3 typed methods per CONTEXT.md INFRA-01: `execute(webhookUrl, payload)`, `executeMultipart(webhookUrl, payload, attachments)`, `editMessage(webhookUrl, messageId, payload)` (Discord PATCH `/webhooks/{id}/{token}/messages/{messageId}`).
- Multipart contract: `payload_json` part MUST be `application/json` (Discord rejects `text/plain`); `files[N]` (with literal brackets) per Discord webhook spec.

---

#### `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` (middleware, request-response)

**Closest analog:** **NONE** — no existing `ClientHttpRequestInterceptor` implementation in `src/main/java/org/ctc/`.
**Substitute reference:** RESEARCH.md § Q2 — full 80-line interceptor sketch with body-stream gotcha, 429 retry, 5xx exponential backoff.

**Critical body-stream gotcha** (from RESEARCH.md § Q2):
- `ClientHttpResponse.getBody()` returns a **once-only `InputStream`**. The interceptor MUST NOT call `getBody()`; it inspects headers + status only.
- On 429/5xx retry: `response.close()` then re-execute (`execution.execute(request, body)`).
- Exhaustion throws `DiscordTransientException` (which `extends IOException` per sealed-base contract — fits the interceptor's `IOException`-declaring `intercept(...)` signature naturally).

**Adaptation notes:**
- `@Component @RequiredArgsConstructor @Slf4j` (alphabetical Lombok per CLAUDE.md § Lombok Usage).
- Constructor injection: `Clock clock` (from `DiscordConfig` bean), no other deps.
- Internal state: `ConcurrentHashMap<String, BucketState>` keyed by `X-RateLimit-Bucket` (D-03 cache pattern).
- Constants: `MAX_429_RETRIES = 3`, `FIVE_XX_BACKOFF_MS = {200, 1000, 5000}` per CONTEXT.md INFRA-01.
- Jitter: `ThreadLocalRandom.current().nextLong(100, 500)` (RESEARCH.md § Q2). Plan 93-01 SHOULD parameterize jitter via `@Value("${app.discord.rate-limit.jitter-ms:100-500}")` so WireMock ITs can override to `0-1` for fast tests.

---

#### `src/main/java/org/ctc/discord/DiscordEmojiCache.java` (utility, cache)

**Closest analog:** **NONE** — first hand-rolled `ConcurrentHashMap` TTL cache in the codebase.
**Substitute reference:** RESEARCH.md § D-03 + § Q12.

**Adaptation notes:**
- `@Component @RequiredArgsConstructor @Slf4j` (alphabetical Lombok).
- Internal state: `ConcurrentHashMap<String, CachedEntry<String>>` keyed by `team.shortName`; value is `<:NAME:id>` long-form Discord emoji literal.
- TTL: 60 minutes per CONTEXT.md D-03; `expiresAt` computed from injected `Clock.instant().plus(Duration.ofMinutes(60))`.
- `emojiFor(shortName)` returns long-form on cache-hit-valid; on miss or expired, returns `:NAME:` fallback literal AND triggers async refresh (or returns fallback synchronously per planner discretion — research recommends synchronous fallback for predictability).
- `refresh(guildId)` calls `DiscordRestClient.fetchGuildEmojis(guildId)` and bulk-replaces the map; returns the new entry count for UI flash-message.
- Manual refresh button on `/admin/discord-config` calls `refresh(guildId)` via `DiscordConfigController.refreshEmojiCache()`.

---

#### `src/main/java/org/ctc/discord/DiscordTimestamps.java` (utility, pure-fn)

**Closest analog:** **NONE** — first `Clock`-injected time service in the codebase (verified — zero existing `@Bean Clock` or `systemClock` references).
**Substitute reference:** RESEARCH.md § Q12 (Spring `@Component` recommendation over static utility).

**Pattern excerpt** (from RESEARCH.md § Q12):

```java
@Component
public class DiscordTimestamps {
    private final Clock clock;
    private final ZoneId zone;

    public DiscordTimestamps(Clock clock,
                             @Value("${app.timezone:Europe/Berlin}") String zoneIdName) {
        this.clock = clock;
        this.zone = ZoneId.of(zoneIdName);
    }

    public String longDateTime(LocalDateTime dt) { return format(dt, "F"); }
    public String shortDateTime(LocalDateTime dt) { return format(dt, "f"); }
    public String longDate(LocalDateTime dt)     { return format(dt, "D"); }
    public String shortDate(LocalDateTime dt)    { return format(dt, "d"); }
    public String shortTime(LocalDateTime dt)    { return format(dt, "t"); }
    public String relative(LocalDateTime dt)     { return format(dt, "R"); }

    private String format(LocalDateTime dt, String style) {
        long epoch = dt.atZone(zone).toEpochSecond();
        return "<t:" + epoch + ":" + style + ">";
    }
}
```

**Adaptation notes:**
- `@Component` (not static utility) chosen for `Clock` injection ergonomics per RESEARCH.md § Q12.
- `app.timezone` default `Europe/Berlin` lands in `application.yml` in Plan 93-02.
- Unit-test ergonomics: instantiate with `new DiscordTimestamps(Clock.fixed(...), "UTC")` for deterministic epoch-second output.

---

#### `src/main/java/org/ctc/discord/util/CachedEntry.java` + `BucketState.java` (utility records)

**Closest analog:** **NONE** — no existing records in `org.ctc.discord` because the package itself is new.
**Reference:** `record DataImportAuditService.AuditOutcome(...)` and similar internal records exist in `org.ctc.*` — Java-record syntax is well-established in CTC.

**Adaptation notes:**
- `record CachedEntry<T>(T value, Instant expiresAt) { public boolean isValid(Clock c) { return c.instant().isBefore(expiresAt); } }` — generic, reused by `DiscordEmojiCache` AND `DiscordRateLimitInterceptor` bucket store.
- `record BucketState(int remaining, Instant resetAt) {}` — Discord-specific rate-limit bucket. NO generic reuse; lives next to interceptor.

---

### Plan 93-02 — INFRA-02 Threat Model + Security Surfaces

#### `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` (documentation)

**Closest analog:** `docs/security/sast-acceptance.md` (Update-on-Triage discipline, single source of truth for SAST decisions per CLAUDE.md § CodeQL SAST).
**Match quality:** ROLE-MATCH — security audit doc, but Phase 93's threat model is phase-scoped (per-phase artifact under `.planning/phases/`) whereas `sast-acceptance.md` is project-wide.

**Adaptation notes:**
- Per D-04: tabular T-93-01..04 with columns `Threat | Likelihood | Impact | Mitigation | Verification`.
- Plus the 6 Mitigation-Surfaces from INFRA-02 (a–f): bot-token env-var, SSRF whitelist, log-pattern mask, `@ToString.Exclude`, CSRF, DTO mass-assignment.
- Prose style mirrors `sast-acceptance.md` — concise + outcome-focused; cross-references CLAUDE.md and the SAST acceptance doc.

---

#### `src/main/resources/application.yml` (modified — add `app.discord.*` block + `logging.pattern` mask)

**Closest analog:** existing `application.yml` lines 38-43 (Google env-var pattern):

```yaml
# Google Integration
google:
  sheets:
    credentials-path: google-credentials.json
  calendar:
    id: ${GOOGLE_CALENDAR_ID:}
```

**Match quality:** EXACT for env-var injection pattern (`${ENV_VAR:default}`).

**Adaptation notes** (RESEARCH.md § Logback Masking final regex):
- Add `app.discord.bot-token: ${DISCORD_BOT_TOKEN:}` — same `${...:default}` syntax.
- Add `app.discord.allowed-hosts: discord.com` — single host whitelist (CSV-friendly for tests).
- Add `app.discord.base-url: https://discord.com/api/v10` — production URL; tests override via `@DynamicPropertySource`.
- Add `app.timezone: Europe/Berlin` — consumed by `DiscordTimestamps`.
- Add `logging.pattern.console` + `logging.pattern.file` with `%replace(%m%n%wEx){...}` Logback converter masking the webhook-URL regex `https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+` → `https://***/api/webhooks/***/***` (RESEARCH.md § Logback final recommendation — loosened pattern to accept WireMock loopback URLs).
- The existing `logging.level.org.springframework.orm.jpa...` block at lines 27-30 stays untouched.

---

#### `src/main/resources/logback-spring.xml` (modified — extend pattern OR leave untouched and rely on `application.yml` `logging.pattern`)

**Closest analog:** existing `src/main/resources/logback-spring.xml` (loaded by Spring Boot for all profiles).

**Existing structure** (lines 1-50):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Spring Boot Defaults (CONSOLE_LOG_PATTERN, Farben, etc.) -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Log-Verzeichnis: profil-abhaengig -->
    <springProfile name="docker | prod">
        <property name="LOG_DIR" value="/app/logs"/>
    </springProfile>
    <!-- ... -->

    <!-- Console Appender (Spring Boot Default-Pattern) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- File Appender mit Rolling Policy -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/app.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
        <!-- ... -->
    </appender>
```

**Adaptation notes:**
- Two viable approaches per RESEARCH.md § Logback Masking:
  1. **YAML-only** (RECOMMENDED): set `logging.pattern.console` and `logging.pattern.file` in `application.yml`. Spring Boot's `${CONSOLE_LOG_PATTERN}` is then overridden by the YAML property; existing `logback-spring.xml` needs no edit.
  2. **XML-only:** modify both `<pattern>` elements in `logback-spring.xml` directly with `%replace(%m%n%wEx){...}`.
- Plan 93-02 should pick (1) — keeps masking config co-located with other Discord settings in `application.yml`, leaves the existing XML rolling-policy structure untouched.

---

#### SSRF positive-whitelist constructor guard in `DiscordRestClient` + `DiscordWebhookClient`

**Closest analog:** `src/main/java/org/ctc/domain/service/FileStorageService.java` lines 126-159 (`validateHostname` — NEGATIVE blocklist)
**Match quality:** EXACT pattern, INVERTED polarity (positive whitelist instead of negative blocklist)

**Pattern excerpt** (analog NEGATIVE blocklist from `FileStorageService.java:129-158`):

```java
// SSRF defense: find-sec-bugs cannot recognize startsWith-chain hostname blocklists as
// sanitizers. This method is the suppressed sanitizer. See config/spotbugs-exclude.xml
// FileStorageService SSRF_SPRING,SSRF entry for the corresponding suppression rationale.
private void validateHostname(String sourceUrl) {
    String hostname = java.net.URI.create(sourceUrl).getHost();
    if (hostname == null) {
        throw new IllegalArgumentException("URL hostname blocked: <null>");
    }
    hostname = hostname.toLowerCase();
    if ("localhost".equals(hostname) || "[::1]".equals(hostname)) {
        log.warn("Blocked SSRF attempt to internal host: {}", hostname);
        throw new IllegalArgumentException("URL hostname blocked: " + hostname);
    }
    if (hostname.startsWith("127.") || hostname.startsWith("10.") || hostname.startsWith("192.168.")
            || hostname.startsWith("169.254.")) {
        log.warn("Blocked SSRF attempt to internal host: {}", hostname);
        throw new IllegalArgumentException("URL hostname blocked: " + hostname);
    }
    // ... 172.16.0.0/12 range check elided ...
}
```

**Existing CodeQL FP-marker pattern** (`FileStorageService.java:86`):

```java
// CodeQL FP: java/ssrf — startsWith-chain hostname blocklist (validateHostname, lines 128-159) not recognized as sanitizer; see docs/security/sast-acceptance.md
public String storeFromUrl(String subDir, UUID entityId, String sourceUrl, String filename) throws IOException {
    if (sourceUrl == null || !sourceUrl.toLowerCase().startsWith("https://")) {
        log.warn("Rejected non-HTTPS URL: {}", sourceUrl);
        throw new IllegalArgumentException("Only HTTPS URLs allowed: " + sourceUrl);
    }
    validateHostname(sourceUrl);
    // ... rest of method ...
}
```

**Adaptation notes:**
- Polarity INVERTED: `if (!allowedHosts.contains(host.toLowerCase())) throw new IllegalArgumentException("Discord host blocked: " + host);`.
- `allowedHosts` derived from `@Value("${app.discord.allowed-hosts:discord.com}")` CSV split → `Set<String>` populated in `DiscordConfig` and injected into both clients.
- CodeQL FP source-marker REQUIRED only IF CodeQL flags the new constructor guard. Per RESEARCH.md § CodeQL Positive-Whitelist:
  - Phase 5 negative blocklist needed a 3-layer suppression (`config/spotbugs-exclude.xml` Match entry + source-marker + `docs/security/sast-acceptance.md` row).
  - Phase 93 positive whitelist *may* not need any suppression (CodeQL's `java/ssrf` query officially recommends positive whitelists — RESEARCH § Q7). If CodeQL still flags it, Plan 93-02 reactively adds the same 3-layer suppression triad pointing at the new SSRF-marker line.
- Pattern shared by BOTH `DiscordRestClient` (validates `app.discord.base-url`) AND `DiscordWebhookClient` (validates every per-call webhook URL via `forWebhookUrl(url)` factory).

---

#### `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` (integration test, log-snapshot)

**Closest analog:** None directly — `OutputCaptureExtension` not currently used in any IT.
**Substitute reference:** RESEARCH.md § `DiscordLogMaskingTest` shape; `org.springframework.boot.test.system.OutputCaptureExtension` documented in Spring Boot Test reference.

**Adaptation notes:**
- `@SpringBootTest @ActiveProfiles("dev") @Tag("integration") @ExtendWith(OutputCaptureExtension.class)` annotations.
- `@RegisterExtension static WireMockExtension wm = WireMockExtension.newInstance().options(options().dynamicPort()).build();` — same pattern as RESEARCH.md § Q5 IT scaffold.
- `@DynamicPropertySource` overrides `app.discord.allowed-hosts` to include `localhost,127.0.0.1` (test-only override; production stays `discord.com`-only).
- Test method asserts `assertThat(out.getAll()).doesNotContain(WEBHOOK_TOKEN_FRAGMENT).contains("***/***")` — proves the mask actually fires on transient-exception path.

---

### Plan 93-03 — INFRA-03 Admin Config Page + V8

#### `src/main/resources/db/migration/V8__discord_global_config.sql` (migration, DDL + seed INSERT)

**Closest analog:** `src/main/resources/db/migration/V7__data_import_audit.sql` (full file)
**Match quality:** EXACT — same dialect-portability rationale, header-comment pattern, NOT NULL TIMESTAMP shape

**Full V7 file excerpt** (the structural template):

```sql
-- Phase 72: data_import_audit table — operational audit log for backup imports.
-- This table is PERMANENTLY OUT OF EXPORT SCOPE (IMPORT-08, see PROJECT.md Decisions row).
-- ...
-- Column type rationale (Phase 72 D-09):
--   - id UUID:                  portable across H2 2.x + MariaDB 10.7+ (UUID is native on both).
--   - LONGTEXT (not JSON):      MariaDB JSON is itself a LONGTEXT alias with CHECK JSON_VALID();
--                               H2's JSON validates differently. Keeping textual avoids dialect drift.
--   - TIMESTAMP NOT NULL:       portable; H2 and MariaDB both accept and store as DATETIME-equivalent.
--   - BOOLEAN:                  H2 native; MariaDB stores as TINYINT(1) — both accept the keyword.
--
-- Compatible with H2 2.x and MariaDB 10.7+.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

CREATE TABLE data_import_audit (
    id UUID PRIMARY KEY,
    executed_at TIMESTAMP NOT NULL,
    executed_by VARCHAR(255) NOT NULL,
    schema_version INT NOT NULL,
    table_counts_wiped LONGTEXT NOT NULL,
    table_counts_restored LONGTEXT NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    success BOOLEAN NOT NULL
);

CREATE INDEX idx_data_import_audit_executed_at ON data_import_audit (executed_at);
```

**Adaptation notes** (RESEARCH.md § Flyway V8 cross-engine):
- Header comment with column-type rationale block (Phase 72 D-09 style) — preserve invariants for future maintainers.
- Table name plural snake_case: `discord_global_config` (singleton table name MAY be debated; CONTEXT D-02 locks the choice).
- Column type changes from V7:
  - `id BIGINT PRIMARY KEY AUTO_INCREMENT` (not UUID — singleton with deterministic id=1 simplifies `findFirstByOrderByIdAsc()`).
  - All snowflake VARCHAR columns use `VARCHAR(32) NOT NULL DEFAULT ''` — empty-string default is the D-02 seed pattern.
  - `vs_emoji_name VARCHAR(50) NOT NULL DEFAULT 'CTC'`.
  - `bot_application_id VARCHAR(32)` (nullable — operator may not fill).
  - `created_at TIMESTAMP NOT NULL` + `updated_at TIMESTAMP NOT NULL` (same column names as `BaseEntity`'s `@CreatedDate` / `@LastModifiedDate` — JPA can map these directly if entity extends `BaseEntity`).
- NO `LONGTEXT` columns (avoid Phase 72 D-09 dialect drift). NO CHECK constraints (D-02 rationale — H2/MariaDB enforcement drifts).
- Seed-row INSERT inline at bottom of file: `INSERT INTO discord_global_config (...) VALUES ('', '', '', '', 'CTC', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);` — `CURRENT_TIMESTAMP` literal works on both engines.

---

#### `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` (entity)

**Closest analog (precedent for package-layout):** `src/main/java/org/ctc/backup/audit/DataImportAudit.java`
**Closest analog (precedent for `@ToString(exclude)`):** `src/main/java/org/ctc/domain/model/Team.java` line 18 (and 16 other entities — see exhaustive grep in research notes)
**Match quality:** HYBRID — package-layout precedent (out-of-`org.ctc.domain.model.*` to avoid `BackupSchema.EXPORT_ORDER`) + Lombok-annotation precedent

**Package-layout precedent excerpt** (`DataImportAudit.java:20-43`):

```java
/**
 * Operational audit log of backup imports — one row per import attempt (success or failure).
 *
 * <p><strong>Does NOT extend {@code BaseEntity}.</strong> {@code executedAt} is set explicitly;
 * bypassing {@code AuditingEntityListener} ensures imported timestamps survive rather than
 * being overwritten by {@code @CreatedDate}/{@code @LastModifiedDate}.
 *
 * <p><strong>Lives in {@code org.ctc.backup.audit}, NOT {@code org.ctc.domain.model}.</strong>
 * {@code BackupSchema.@PostConstruct} filters JPA Metamodel entities by
 * {@code startsWith("org.ctc.domain.model")}, so this class is structurally excluded from
 * {@code BackupSchema.exportOrder} — no marker annotation, no opt-in, no developer memory.
 */
@Entity
@Table(name = "data_import_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DataImportAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
```

**`@ToString(exclude)` precedent excerpt** (`Team.java:18`):

```java
@ToString(exclude = {"seasonDrivers", "parentTeam", "subTeams"})
```

**`BaseEntity` shape** (`BaseEntity.java:13-26`):

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}
```

**Adaptation notes** (RESEARCH.md § Q8 entity shape):
- Lives at `org.ctc.discord.model` — STRUCTURALLY EXCLUDED from `BackupSchema.EXPORT_ORDER` via Phase 72 D-15 package filter `startsWith("org.ctc.domain.model")`. `BackupSchemaGuardTest` stays at 24 entities.
- Choice: `extends BaseEntity` (research recommends) OR self-managed timestamps (DataImportAudit pattern). RESEARCH § Q8 picks BaseEntity extension — gets `createdAt` / `updatedAt` for free via `AuditingEntityListener`. Note: BaseEntity uses `LocalDateTime` (not `Instant`); the V8 migration's `TIMESTAMP NOT NULL` columns map directly. If extending `BaseEntity`, REMOVE the explicit `createdAt`/`updatedAt` fields from `DiscordGlobalConfig` (BaseEntity provides them).
- `@ToString(exclude = {"announcementWebhookUrl"})` — T-93-02 webhook-URL leak mitigation. Adds the field name to the existing entity convention (16+ existing entities use this exact Lombok annotation shape).
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;` — matches V8's `BIGINT AUTO_INCREMENT`. (DataImportAudit uses UUID; Phase 93 uses Long/identity since singleton row deterministically gets id=1.)
- All snowflake columns: `@Column(name = "guild_id", length = 32, nullable = false) private String guildId;` etc.

---

#### `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java` (repository)

**Closest analog:** `src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java`
**Match quality:** EXACT for base interface; new finder method `findFirstByOrderByIdAsc()` is novel (verified — zero `findFirstByOrderBy*` finders exist in `src/main/java/`)

**Full analog file excerpt** (`DataImportAuditRepository.java:1-15`):

```java
package org.ctc.backup.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DataImportAudit}. No custom finders.
 *
 * <p>Co-located with {@link DataImportAudit} so the IMPORT-08 package-name filter
 * ({@code startsWith("org.ctc.domain.model")}) remains the single source of truth for
 * export-scope exclusion — no marker annotation needed.
 */
public interface DataImportAuditRepository extends JpaRepository<DataImportAudit, UUID> {
}
```

**Adaptation notes** (RESEARCH.md § Q8 repository):
- Package: `org.ctc.discord.repository` — co-located with the entity per the same Phase 72 package-filter discipline.
- Generic params: `JpaRepository<DiscordGlobalConfig, Long>` (entity has Long id).
- Add ONE Spring-Data derived-finder method: `DiscordGlobalConfig findFirstByOrderByIdAsc();` — returns non-Optional because V8 seed-row INSERT guarantees presence. (`getOrInitialize()` in the service layer defensively handles a `null` return for DB-corruption resilience.)

---

#### `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` (service)

**Closest analog:** any `@Service @RequiredArgsConstructor @Slf4j` class in `src/main/java/org/ctc/domain/service/` or `src/main/java/org/ctc/admin/service/`.
**Match quality:** ROLE-MATCH — service is novel (singleton-row orchestrator); annotation order + DI pattern are standard.

**Adaptation notes** (RESEARCH.md § Q8 service):
- Annotations (alphabetical per CLAUDE.md): `@Slf4j @Service @RequiredArgsConstructor`.
- Constructor injection: `DiscordGlobalConfigRepository repo, Clock clock`.
- Two public methods: `@Transactional getOrInitialize()` and `@Transactional save(DiscordConfigForm form)`.
- `getOrInitialize()` returns the seed row (defensively re-inserts if null).
- `save(form)` calls `getOrInitialize()` first, mutates field-by-field, then `repo.save(current)` — id present → JPA UPDATE not INSERT.
- `log.info("Updated discord_global_config (id={}, guildId={})", current.getId(), current.getGuildId())` — parameterized format per CLAUDE.md § Logging; NEVER log the webhook URL.

---

#### `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (DTO, mass-assignment defense)

**Closest analog:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java`
**Match quality:** EXACT structural template

**Full analog file excerpt** (`MatchdayForm.java:1-24`):

```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    @NotNull
    private UUID seasonId;
}
```

**Adaptation notes** (RESEARCH.md § Q8 form):
- Package: `org.ctc.discord.dto` (cohesive with rest of Discord integration; sibling option `org.ctc.admin.dto` is also valid per CLAUDE.md, planner discretion).
- Lombok: `@Getter @Setter @NoArgsConstructor` — identical to MatchdayForm.
- Validation constraints (snowflake-aware, accept empty for bootstrap state per D-12):
  - `@Pattern(regexp = "^$|^\\d{17,20}$", ...)` on `guildId`, `raceResultsForumChannelId`, `standingsForumChannelId`.
  - `@Size(max = 500) @Pattern(regexp = "^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$", ...)` on `announcementWebhookUrl`.
  - `@NotBlank @Size(max = 50)` on `vsEmojiName` (seed defaults to "CTC", form rejects empty).
- NO entity fields — only primitive `String`s + validation annotations (mass-assignment defense per CLAUDE.md § Controllers).

---

#### `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (controller, request-response + flash)

**Closest analog:** `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` (lines 1-175, full file)
**Match quality:** EXACT structural template — typed-catch on sealed `GoogleApiException` + `errorMessage` + `errorCategory` flash attributes = Phase 91 D-06/D-07 carry-forward.

**Typed-catch + flash-dispatch pattern excerpt** (`DriverSheetImportController.java:60-92`):

```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Authentication problem — re-link Google account");
    model.addAttribute("errorCategory", "AUTH");
    return "admin/driver-import";
} catch (NotFoundGoogleApiException e) {
    log.error("Google Sheet not found during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Sheet not found — check ID");
    model.addAttribute("errorCategory", "NOT_FOUND");
    return "admin/driver-import";
} catch (PermissionGoogleApiException e) {
    log.error("Permission denied on Google Sheet during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Access denied — share the sheet with the service account");
    model.addAttribute("errorCategory", "PERMISSION");
    return "admin/driver-import";
} catch (TransientGoogleApiException e) {
    log.warn("Transient Google API failure during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/driver-import";
} catch (GoogleApiException e) {
    // Defensive catch on the sealed base — unreachable at runtime (the 4
    // permits above are exhaustive) but required by javac since sealed
    // exhaustiveness on catch blocks is not yet a language feature.
    log.error("Unexpected GoogleApiException subtype during driver import preview", e);
    addCommonAttributes(model);
    model.addAttribute("errorMessage", "Connection problem — retry");
    model.addAttribute("errorCategory", "TRANSIENT");
    return "admin/driver-import";
}
```

**Redirect-flow variant** (`DriverSheetImportController.java:134-155`) — for POST handlers that redirect:

```java
} catch (AuthGoogleApiException e) {
    log.error("Google Sheets authentication failed during driver import execute", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
}
// ... 3 more catches ...
return "redirect:/admin/drivers/import";
```

**Adaptation notes** (RESEARCH.md § Q9 controller):
- Class annotations: `@Slf4j @Controller @RequiredArgsConstructor @RequestMapping("/admin/discord-config")`.
- 4 POST endpoints for test/refresh buttons (CONTEXT D-12 + RESEARCH § Q9):
  - `POST /admin/discord-config/save` — form save, `@Valid @ModelAttribute("form") DiscordConfigForm + BindingResult`.
  - `POST /admin/discord-config/test-connection` — calls `discord.fetchBotUser()`, flashes `successMessage` or dispatched error.
  - `POST /admin/discord-config/test-webhook` — calls `webhook.execute(cfg.getAnnouncementWebhookUrl(), ...)`.
  - `POST /admin/discord-config/refresh-roles-cache` — calls `discord.fetchGuildRoles(cfg.getGuildId())`.
  - `POST /admin/discord-config/refresh-emoji-cache` — calls `emojiCache.refresh(cfg.getGuildId())`.
- Each POST handler wraps the service/client call in the exhaustive typed-catch above:
  - 4 permits: `DiscordTransientException` / `DiscordAuthException` / `DiscordNotFoundException` / `DiscordCategoryFullException`.
  - Defensive sealed-base catch: `catch (DiscordApiException e)` (required by javac).
- Single private helper `applyErrorFlash(RedirectAttributes, DiscordApiException)` collapses the 4-branch switch (RESEARCH § Q9 shows the cleaner `switch (e)` pattern-matching variant — uses sealed exhaustiveness on the Java 25 pattern-matching `switch`).
- `errorCategory` flash values used by template: `"transient"`, `"auth"`, `"not-found"`, `"category-full"` (lowercase + hyphen — admin/layout.html composes `error-badge--{category}` via `th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"`, see layout.html:89).
- All POSTs return `"redirect:/admin/discord-config"` (sync POST + redirect + flash — CTC convention).

---

#### `src/main/resources/templates/admin/discord-config.html` (template)

**Closest analog:** `src/main/resources/templates/admin/driver-import.html` (existing flash-attribute consumer); `admin/layout.html` lines 85-92 (flash renderer at the layout level)
**Match quality:** ROLE-MATCH — new page composition; layout-level flash rendering is inherited automatically.

**Layout-level flash renderer** (`admin/layout.html:85-92`) — already exists, Phase 93 needs no edits to this fragment:

```html
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
<div th:replace="${content}"></div>
```

**Adaptation notes** (RESEARCH.md § Q9 template):
- New page lives at `src/main/resources/templates/admin/discord-config.html`.
- Standard `<html th:replace="~{admin/layout :: layout('Discord Configuration', ~{::section})}">` shape — the layout's existing flash-renderer block at lines 85-92 handles `successMessage` / `errorMessage` / `errorCategory` automatically. Phase 93 controllers just need to populate the flash attributes; no template-side conditional logic.
- 6 form fields (per CONTEXT D-12): guild ID, bot-token-status indicator (READ-ONLY — derived from `@discordTokenStatus` bean exposing `Optional<String>.isPresent()`), announcement-webhook-URL, race-results-forum-channel-ID, standings-forum-channel-ID, vs-emoji-name.
- "not configured" badges on each empty field: `<span th:if="${#strings.isEmpty(form.X)}" class="badge-warning">not configured</span>` — REUSES existing `.badge-warning` class (admin.css:357).
- 4 test/refresh buttons in inline `<form>` elements (CSRF requires per-button form per CLAUDE.md):
  - `data-testid="test-connection"` — gated `th:disabled="${!@discordTokenStatus.isPresent()}"` + tooltip.
  - `data-testid="test-webhook"` — gated `th:disabled="${#strings.isEmpty(config.announcementWebhookUrl)}"`.
  - `data-testid="refresh-roles"` — gated `th:disabled="${#strings.isEmpty(config.guildId)}"`.
  - `data-testid="refresh-emoji"` — gated `th:disabled="${#strings.isEmpty(config.guildId)}"`.
- NO inline styles on buttons (CLAUDE.md § "No Inline Styles on Buttons"). `style="display:inline"` is on the `<form>` element, not the button (acceptable — research notes this is the CSRF-per-form requirement).

---

#### `src/main/resources/templates/admin/layout.html` (modified — add nav link)

**Closest analog:** the existing `<div class="sidebar-group">` blocks at lines 45-77.

**Pattern excerpt** (existing nav-group structure from `layout.html:45-77`):

```html
<div class="sidebar-group">
    <span class="sidebar-group-label">League</span>
    <a th:href="@{/admin/seasons}" th:classappend="${title.contains('Season') ? 'active' : ''}">Seasons</a>
    <a th:href="@{/admin/matchdays}" th:classappend="${title.contains('Matchday') ? 'active' : ''}">Matchdays</a>
    <a th:href="@{/admin/races}" th:classappend="${title.contains('Race') ? 'active' : ''}">Races</a>
    <a th:href="@{/admin/playoffs}" th:classappend="${title.contains('Playoff') || title.contains('Matchup') ? 'active' : ''}">Playoffs</a>
</div>
<!-- ... Master Data, Scoring, Tools, Data ... -->
<div class="sidebar-group">
    <span class="sidebar-group-label">Data</span>
    <a th:href="@{/admin/backup}" th:classappend="${title.contains('Backup') ? 'active' : ''}">Backup</a>
</div>
```

**Adaptation notes:**
- Existing nav groups (verified): League / Master Data / Scoring / Tools / Data. **No "Integrations" group exists today.**
- RESEARCH.md § Claude's Discretion recommends adding a NEW `<div class="sidebar-group"><span class="sidebar-group-label">Integrations</span>...</div>` group AFTER the "Data" group (line 77) — Phase 94+ will populate it with more entries (team-role-mapping, etc.).
- Single Phase-93 entry: `<a th:href="@{/admin/discord-config}" th:classappend="${title.contains('Discord') ? 'active' : ''}">Discord</a>`.
- Alternative (also planner-acceptable): add as 5th entry to the existing "Tools" group, sibling to "Import" / "GT7 Sync". CONTEXT.md leaves this to planner discretion.

---

#### `src/main/resources/static/admin/css/admin.css` (modified — add `.error-badge--category-full` variant)

**Closest analog:** existing `.error-badge--*` definitions at lines 371-374 (the 4-variant Phase 91 palette).

**Pattern excerpt** (`admin.css:355-374`):

```css
.badge-active { background: var(--success-bg); color: #66bb6a; }
.badge-inactive { background: #222; color: #999; }
.badge-warning { background: #3b2e0e; color: #ffb74d; }

/* Google API error categories (Phase 91 / UX-01) */
.error-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-right: 8px;
    vertical-align: middle;
}
.error-badge--transient  { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
.error-badge--auth       { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.error-badge--not-found  { background: #2a2a3a; color: #90caf9; border: 1px solid #1976d2; }
.error-badge--permission { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
```

**Adaptation notes:**
- ADD exactly ONE new line below line 374: `.error-badge--category-full { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }` — same yellow palette as `.error-badge--transient` and `.badge-warning` (distinguishable by the badge label text "category-full" vs "transient" rendered uppercase).
- NO other CSS changes. `.badge-warning` already exists at line 357 — Phase 93 reuses it for "not configured" badges per D-12.
- Update the existing section header comment `/* Google API error categories (Phase 91 / UX-01) */` to `/* Google + Discord API error categories (Phase 91 / Phase 93 INFRA-02) */`.

---

#### `src/test/java/org/ctc/discord/DiscordGlobalConfigRepositoryIT.java` (integration test, DB)

**Closest analog:** `src/test/java/db/migration/V7DataImportAuditMigrationIT.java`
**Match quality:** ROLE-MATCH — V7 IT tests Flyway shape via raw JDBC `DatabaseMetaData`; Phase 93 IT tests via Spring Data `repo.findFirstByOrderByIdAsc()` semantics.

**V7 analog excerpt** (`V7DataImportAuditMigrationIT.java:28-57`):

```java
@CtcDevSpringBootContext
@Tag("integration")
class V7DataImportAuditMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void givenH2WithV7Applied_whenInspectingDataImportAuditColumns_thenAllExpectedColumnsExist() throws Exception {
        // when
        Set<String> columns = new HashSet<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "DATA_IMPORT_AUDIT", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        // then — exactly 8 columns per D-09
        assertThat(columns).containsExactlyInAnyOrder(
                "id", "executed_at", "executed_by", "schema_version",
                "table_counts_wiped", "table_counts_restored",
                "source_filename", "success");
    }
}
```

**Adaptation notes:**
- Use `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")` (V7 IT uses a custom `@CtcDevSpringBootContext` meta-annotation; planner may use either form).
- Tests via the Spring Data repo, NOT raw JDBC — more idiomatic for entity-level invariants:
  - `givenFreshDb_whenFindFirst_thenSeedRowReturned()` — asserts `repo.findFirstByOrderByIdAsc()` returns non-null with `guildId == ""` and `vsEmojiName == "CTC"`.
  - `givenSeedRow_whenServiceSavesForm_thenSameRowUpdated_noSecondInsert()` — asserts `repo.count() == 1` before AND after save.
  - `givenEntity_whenToString_thenWebhookUrlNotPresent()` — asserts `@ToString(exclude = {"announcementWebhookUrl"})` works.
- Plus a Flyway-shape smoke (`DatabaseMetaData.getColumns(...)`) inherited from the V7 IT pattern to assert all 9 columns exist on H2.

---

#### `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` (integration test, MockMvc)

**Closest analog:** `src/test/java/org/ctc/backup/BackupControllerIT.java`
**Match quality:** EXACT — same `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Tag("integration")` quartet.

**Analog excerpt** (`BackupControllerIT.java:28-45`):

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class BackupControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings() throws Exception {
        // when / then
        mockMvc.perform(get("/admin/backup"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/backup"))
                .andExpect(content().string(Matchers.containsString("Export Backup")))
                .andExpect(content().string(Matchers.containsString("all 24 entities")));
    }
}
```

**Adaptation notes:**
- Same annotation set + `MockMvc` injection.
- Tests:
  - `givenEmptyConfig_whenGet_thenViewRendersWithBadgeWarning()` — `mockMvc.perform(get("/admin/discord-config")).andExpect(content().string(Matchers.containsString("not configured")))`.
  - `givenValidForm_whenPostSave_thenFlashSuccessAndRedirect()` — `mockMvc.perform(post("/admin/discord-config/save").param("guildId", "1234567890123456789").param("vsEmojiName", "CTC")...).andExpect(redirectedUrl("/admin/discord-config")).andExpect(flash().attributeExists("successMessage"))`.
  - `givenInvalidSnowflake_whenPostSave_thenFlashErrorAndStayOnPage()` — `@Pattern` violation behavior.
- WireMock-backed test-button paths (covering `DiscordRestClient` outbound) are split into a SEPARATE class `DiscordRestClientWireMockIT.java` (RESEARCH § Q5 scaffold) since they need `@RegisterExtension WireMockExtension` + `@DynamicPropertySource`.

---

#### `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` (E2E, Playwright)

**Closest analog:** `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` + `src/test/java/org/ctc/e2e/PlaywrightConfig.java`
**Match quality:** EXACT — extends `PlaywrightConfig`, same `@Tag("e2e")`.

**PlaywrightConfig base excerpt** (lines 1-53, full file):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {

	static Playwright playwright;
	static Browser browser;

	@LocalServerPort
	int port;

	BrowserContext context;
	Page page;

	@BeforeAll
	static void setupBrowser() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
	}
	// ...
	protected void setupPage() {
		context = browser.newContext();
		page = context.newPage();
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}
}
```

**`AdminWorkflowE2ETest` analog test** (lines 10-31):

```java
@Tag("e2e")
class AdminWorkflowE2ETest extends PlaywrightConfig {

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void whenNavigateToRoot_thenRedirectsToSeasons() {
		// when
		page.navigate(url("/"));
		// then
		assertThat(page).hasTitle("CTC Admin - Seasons");
		assertThat(page.locator("h1")).containsText("Seasons");
	}
```

**Adaptation notes** (RESEARCH.md § Q10):
- Package: `org.ctc.e2e.discord` (CONTEXT.md D-09 locks this — per `.planning/codebase/TESTING.md § Test Categorization`).
- `@Tag("e2e")` (mandatory).
- Extends `PlaywrightConfig` — inherits `RANDOM_PORT` Spring server + Playwright `Page` + `url(path)` helper.
- `@RegisterExtension static WireMockExtension wm` + `@DynamicPropertySource` override `app.discord.base-url` to WireMock's `dynamicPort()` baseUrl + add `localhost,127.0.0.1` to `allowed-hosts`.
- 4+ test methods covering: empty-config render (badge-warning visible, buttons disabled), test-connection success path (WireMock OK + alert-success), test-connection 401 (alert-error + `.error-badge--auth`), refresh-emoji-cache (form-save guildId + button-click + `wm.verify()` outbound).
- `data-testid="..."` selectors on the template buttons drive Playwright locator queries.
- Per `[[feedback-playwright-cli]]` + CONTEXT.md D-09: at least one visual-verification test should run on BOTH Desktop and Mobile viewports — see `LegacyMigratedSeasonE2ETest` for the parameterized-viewport reference (existing pattern).

---

## Shared Patterns

### Pattern S-01: Sealed Exception Hierarchy + Typed-Catch Flash Dispatch (Phase 91 D-06/D-07 carry-forward)

**Source:** `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java` (sealed base) + `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java:60-92, 134-155` (typed-catch + `errorCategory` flash)
**Apply to:** Plan 93-01 ALL exception files + Plan 93-03 `DiscordConfigController` (all 4 POST handlers)

**Excerpt:**

```java
public abstract sealed class GoogleApiException extends IOException
		permits TransientGoogleApiException, AuthGoogleApiException,
		        NotFoundGoogleApiException, PermissionGoogleApiException {
	public enum Category { TRANSIENT, AUTH, NOT_FOUND, PERMISSION }
	protected GoogleApiException(String message, Throwable cause) { super(message, cause); }
	public abstract Category category();
}
```

```java
} catch (AuthGoogleApiException e) {
    log.error("...", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Authentication problem — re-link Google account");
    redirectAttributes.addFlashAttribute("errorCategory", "AUTH");
} catch (GoogleApiException e) {
    // Defensive catch on the sealed base — unreachable at runtime but required by javac.
    log.error("Unexpected GoogleApiException subtype", e);
    redirectAttributes.addFlashAttribute("errorMessage", "Connection problem — retry");
    redirectAttributes.addFlashAttribute("errorCategory", "TRANSIENT");
}
```

**No-info-leak invariant:** controllers MUST NOT echo `e.getMessage()` outward. Only the `*_MESSAGE` constants from `GoogleApiExceptionMapper` / `DiscordApiExceptionMapper` reach the UI. Phase 91 T-91-02-IL discipline carries forward.

---

### Pattern S-02: SSRF Positive-Whitelist Constructor Guard (inversion of Phase 5 negative blocklist)

**Source:** `src/main/java/org/ctc/domain/service/FileStorageService.java:86-104, 126-159`
**Apply to:** `DiscordConfig` (Bot client builder) + `DiscordWebhookClient.forWebhookUrl(url)` (per-call validation)

**Inverted-polarity excerpt** (Phase 93 shape):

```java
private static void requireDiscordHost(String url, Set<String> allowedHosts) {
    String host = java.net.URI.create(url).getHost();
    if (host == null || !allowedHosts.contains(host.toLowerCase())) {
        log.warn("Blocked SSRF attempt to non-whitelisted host: {}", host);
        throw new IllegalArgumentException("Discord host blocked: " + host);
    }
}
```

**CodeQL FP source-marker (only if CodeQL flags it):**

```java
// CodeQL FP: java/ssrf — positive whitelist via Set.contains(host.toLowerCase());
// app.discord.allowed-hosts is config-only (no user input). See docs/security/sast-acceptance.md.
```

Per RESEARCH § Q7: CodeQL's `java/ssrf` query officially recommends positive whitelists, so it MAY not flag this pattern. Plan 93-02 implements first; reactively adds the 3-layer suppression triad (codeql-config.yml + source-marker + sast-acceptance.md row) IF a finding appears.

---

### Pattern S-03: `@ToString.Exclude` Discipline (sensitive-field redaction)

**Source:** `src/main/java/org/ctc/domain/model/Team.java:18` (and 16+ other entities)
**Apply to:** `DiscordGlobalConfig.announcementWebhookUrl` field

**Excerpt:**

```java
@ToString(exclude = {"seasonDrivers", "parentTeam", "subTeams"})
public class Team extends BaseEntity {
    // ...
}
```

**Adaptation for Phase 93:** `@ToString(exclude = {"announcementWebhookUrl"})` on `DiscordGlobalConfig` — webhook URL contains the auth token and must never be logged via implicit `toString()`. Verified by `DiscordGlobalConfigToStringTest` (unit test in Plan 93-02 or 93-03).

---

### Pattern S-04: Flash-Attribute Renderer with BEM Error-Badge (existing layout-level pattern)

**Source:** `src/main/resources/templates/admin/layout.html:85-92`
**Apply to:** `admin/discord-config.html` (no edit needed — automatic inheritance via `th:replace` layout fragment)

**Excerpt (already in production):**

```html
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
```

**Phase 93 contract:** controller flashes `errorCategory ∈ {"transient", "auth", "not-found", "category-full"}` (lowercase + hyphen). Template applies `error-badge--{category}` class automatically. Phase 93 adds CSS `.error-badge--category-full` (one new line in admin.css after line 374).

---

### Pattern S-05: Flyway Migration Header + Cross-Engine Discipline

**Source:** `src/main/resources/db/migration/V7__data_import_audit.sql` (full file)
**Apply to:** `V8__discord_global_config.sql`

**Pattern (header comment + column-type rationale + DDL + INDEX):**

```sql
-- Phase NN: <table> table — <one-line purpose>.
-- ...
-- Column type rationale (Phase NN D-XX):
--   - TIMESTAMP NOT NULL:       portable; H2 and MariaDB both accept and store as DATETIME-equivalent.
--   - VARCHAR(N) NOT NULL DEFAULT '': portable empty-string default; H2 enforces; MariaDB accepts.
--   - BIGINT AUTO_INCREMENT:    native on both engines.
--
-- Compatible with H2 2.x and MariaDB 10.7+.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

CREATE TABLE ... ( ... );

INSERT INTO ... VALUES (...);  -- D-02 seed-row
```

Phase 93 V8 follows the V7 invariants: no LONGTEXT, no CHECK constraints, CURRENT_TIMESTAMP literal in INSERT.

---

### Pattern S-06: `@Tag` Discipline for Test Categorization

**Source:** CLAUDE.md § "Tag Tests by Category" + `.planning/codebase/TESTING.md`
**Apply to:** ALL Phase 93 test files

| Test Class Suffix | Location | `@Tag` Value | Forked Surefire/Failsafe |
|-------------------|----------|--------------|--------------------------|
| `*IT.java` (Spring-context) | `src/test/java/org/ctc/discord/...` | `@Tag("integration")` | Failsafe |
| `*E2ETest.java` (Playwright) | `src/test/java/org/ctc/e2e/discord/...` | `@Tag("e2e")` | Failsafe `-Pe2e` |
| `*Test.java` (Mockito-only unit) | `src/test/java/org/ctc/discord/...` | untagged (project convention) | Surefire |

Verified existing references:
- Integration tag example: `src/test/java/org/ctc/backup/BackupControllerIT.java:31` → `@Tag("integration")`.
- E2E tag example: `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java:10` → `@Tag("e2e")`.
- Untagged unit-test example: vast majority of files under `src/test/java/org/ctc/domain/`.

---

## No Analog Found

Files for which no close codebase analog exists; planner consumes RESEARCH.md sketches as substitute template:

| New File | Role | Why No Analog | Substitute Reference |
|----------|------|---------------|----------------------|
| `src/main/java/org/ctc/discord/DiscordRestClient.java` | api-client | First Spring `RestClient` in CTC codebase (verified zero existing usage of `RestClient` / `RestTemplate` / `java.net.http.HttpClient`). | RESEARCH.md § Q1 builder chain + § Q3 typed-catch call site |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | api-client | Same as above + first `MultipartBodyBuilder` usage. | RESEARCH.md § Q1 webhook builder + § Q4 multipart contract |
| `src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` | middleware | First `ClientHttpRequestInterceptor` implementation in CTC. | RESEARCH.md § Q2 (80-LOC sketch with body-stream gotcha) |
| `src/main/java/org/ctc/discord/DiscordConfig.java` (specifically the `@Bean Clock systemClock()` line) | config | Zero existing `@Bean Clock` definitions in codebase. | RESEARCH.md § Q1 (`@Bean public Clock systemClock() { return Clock.systemUTC(); }`) |
| `src/main/java/org/ctc/discord/DiscordEmojiCache.java` | utility | First hand-rolled `ConcurrentHashMap` TTL cache in CTC. | RESEARCH.md § D-03 + § JPA Singleton Repository Pattern adjacent sketches |
| `src/main/java/org/ctc/discord/DiscordTimestamps.java` | utility | First `Clock`-injected time/timezone formatter. Lives at the root `org.ctc.discord` package (Spring `@Component`); `util` package reserved for records (CachedEntry, BucketState). | RESEARCH.md § Q12 |
| `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` | test (integration) | No existing IT uses `OutputCaptureExtension`. | RESEARCH.md § `DiscordLogMaskingTest` shape |
| `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | doc | First phase-scoped threat-model artifact (lifecycle convention introduced by D-04). | `docs/security/sast-acceptance.md` (style reference only) + CONTEXT D-10 (T-93-01..04 anchors) |

WireMock test-scope dependency:
- **NOT YET ON CLASSPATH** — verified by grep across `src/test/java/` (zero matches for `WireMock` / `WireMockExtension` / `WireMockServer`).
- Plan 93-01 introduces `org.wiremock:wiremock-standalone:3.9.x` test-scope to `pom.xml`. Single new dependency in Phase 93. Per RESEARCH.md § Package Legitimacy Audit: industry-standard, Maven Central, 7+ years.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/dataimport/exception/` (sealed-exception template — 6 files)
- `src/main/java/org/ctc/admin/controller/` (typed-catch + flash dispatch template)
- `src/main/java/org/ctc/admin/dto/` (Form-DTO template)
- `src/main/java/org/ctc/domain/model/` (Entity + `@ToString(exclude)` template — 16+ files surveyed via grep)
- `src/main/java/org/ctc/domain/service/FileStorageService.java` (SSRF validation pattern)
- `src/main/java/org/ctc/backup/audit/` (out-of-`domain.model` repository pattern)
- `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` (@Configuration + @Bean pattern)
- `src/main/resources/db/migration/` (V1-V7 — 4 migrations surveyed)
- `src/main/resources/templates/admin/` (layout.html flash renderer + driver-import.html consumer)
- `src/main/resources/static/admin/css/admin.css` (BEM error-badge palette lines 357-374)
- `src/test/java/org/ctc/e2e/` (PlaywrightConfig + AdminWorkflowE2ETest)
- `src/test/java/org/ctc/backup/` (BackupControllerIT MockMvc pattern)
- `src/test/java/db/migration/` (V7DataImportAuditMigrationIT pattern)
- `src/main/resources/application.yml` + `application-local.yml` (env-var injection pattern)
- `src/main/resources/logback-spring.xml` (Logback config existing structure)

**Negative results (grep searches that returned ZERO matches — drive "no analog" classifications):**
- `RestClient.builder` / `RestClient.create` in `src/main/java/`
- `RestTemplate` / `HttpClient` in `src/main/java/`
- `WireMock` / `WireMockExtension` / `WireMockServer` in `src/test/java/`
- `@Bean.*Clock` / `systemClock` in `src/main/java/`
- `findFirstByOrderBy` in `src/main/java/`

**Files scanned:** ~50 Java sources, ~5 SQL migrations, ~12 templates, ~2 YAML configs.
**Pattern extraction date:** 2026-05-21
