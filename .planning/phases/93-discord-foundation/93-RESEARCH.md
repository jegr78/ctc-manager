# Phase 93: Discord Foundation - Research

**Researched:** 2026-05-21
**Domain:** Spring RestClient + outbound HTTP integration + sealed exception hierarchy + Flyway migration + Thymeleaf admin page
**Confidence:** HIGH (Phase 91 template + existing codebase patterns + Spring 6.1 docs anchored)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 — Live-UAT deferred.** Phase 93 closes on WireMock-IT coverage only. UAT-03 (Live Discord Test Connection + Announcement-Webhook + Refresh-Roles + Refresh-Emoji) is deferred to `STATE.md § Pending UATs` and the operator runs it **before** Phase 94 CHAN-02 starts.
- **D-02 — V8 + seed-row + service-singleton.** Flyway `V8__discord_global_config.sql` ships the schema from Design Spec § 3.3 plus an inline `INSERT` of one empty-string seed row. Repo exposes only `findFirstByOrderByIdAsc()`. Service `getOrInitialize()` returns the seed deterministically; saves update the same row.
- **D-03 — Hand-rolled `ConcurrentHashMap<String, CachedEntry<T>>` with injectable `Clock`.** Identical shape for `DiscordEmojiCache` (TTL 60 min, keyed by `team.shortName`) and `DiscordRateLimitInterceptor.bucketStore` (keyed by `X-RateLimit-Bucket`, holds `BucketState` record). `CachedEntry<T>` lives in `org.ctc.discord.util`. 429: max 3 retries with `Retry-After` sleep + jitter. 5xx: exponential 200ms → 1s → 5s. Exhaustion → `DiscordTransientException`.
- **D-04 — `93-THREAT-MODEL.md` as standalone artifact** in `.planning/phases/93-discord-foundation/` with T-93-01..04 table (Threat / Likelihood / Impact / Mitigation / Verification) PLUS the 6 mitigation-surfaces from INFRA-02.
- **D-05 — Three plans, sequential inline on `gsd/v1.13-discord-integration`** (no worktrees, no subagents per `[[feedback-inline-sequential-execution]]` + `[[feedback-wave-pause]]`). Order: 93-01 clients+utilities → 93-02 threat-model+security → 93-03 admin-config-page+V8.
- **D-06 — Rolling v1.13 milestone PR.** Plan 93-01 onwards updates body via `gh pr edit --body-file`. PR stays Draft until end of Phase 98. Final subject locked: `feat(v1.13): discord integration & carry-forwards`.
- **D-07 — Standard quality gates.** JaCoCo ≥ 88.88%, SpotBugs `BugInstance` = 0 (blocking), CodeQL exit 0 on HIGH/CRITICAL, EXPORT_ORDER = 24, BackupSchema.SCHEMA_VERSION = 1, Flyway V1–V7 immutable; V8 only. `./mvnw verify -Pe2e` ≤ 17:39 ± 20%.
- **D-08 — Per-plan Nyquist VALIDATION.md.** `/gsd-validate-phase 93` runs before `/gsd-execute-phase 94`.
- **D-09 — `@Tag` discipline.** WireMock ITs → `@Tag("integration")`. Mockito-only unit tests untagged. Playwright E2E → `@Tag("e2e")` in `org.ctc.e2e.discord`.
- **D-10 — T-93-01..04 anchors authored in `93-THREAT-MODEL.md` (Plan 93-02).**
- **D-11 — Production code touched only within** `org.ctc.discord.*` + `application*.yml` + Flyway V8 + `admin/discord-config.html` + nav link. No edits to other packages.
- **D-12 — Empty-config state renders with "not configured" badges, NOT a setup wizard.** Test buttons gated on per-field presence with disabled-tooltip explanations.

### Claude's Discretion

- **Package layout for `DiscordConfigController`** — either `org.ctc.admin.controller.discord` (sibling) or `org.ctc.discord.web` (cohesive). RECOMMEND: `org.ctc.discord.web` per Design Spec § 3.2 explicit package layout (`org.ctc.admin.controller.DiscordConfigController` is listed in spec but `org.ctc.discord.web` keeps the integration cohesive; Phase 92 left package layout to planner). Final choice: planner picks at PLAN-time.
- **Exact CSS for "not configured" badges** — `.badge-warning` already exists in `admin.css` line 357 (yellow, `#3b2e0e` bg / `#ffb74d` text — identical palette to `.error-badge--transient`). REUSE this class. The only NEW BEM variant to add is `.error-badge--category-full` (per Design Spec § 3.5 yellow box-icon — same yellow palette as `.badge-warning`/`.error-badge--transient`).
- **Navigation entry placement** — no "Integrations" group exists today in `admin/layout.html`. Existing sidebar groups: League / Master Data / Scoring / Tools / Data. RECOMMEND: add a new `<div class="sidebar-group">` labeled "Integrations" with the single Discord-Config link initially; Phase 94+ will populate it further. Alternative: under "Tools" sibling to "Import" + "GT7 Sync" (precedent for outbound-integration links).
- **`application.yml` `logging.pattern` regex** — see § Logback Masking Approach below. Final regex: `https://discord\.com/api/webhooks/[^/\s]+/[^/\s]+` → `https://discord.com/api/webhooks/***/***`. Anchored at `discord.com` to avoid masking unrelated webhook-URL-like strings.
- **WireMock `@Tag` inheritance** — `WireMockTest`-style parent IT does not exist yet. Each new IT class declares its own `@Tag("integration")` explicitly. No abstract base needed.
- **Exact wording of T-93-01..04 cells** — Plan 93-02 authors. RECOMMEND mirror `docs/security/sast-acceptance.md` prose style (concise + outcome-focused).
- **`DiscordTimestamps` shape (static utility vs Spring `@Component`)** — RECOMMEND Spring `@Component` with constructor-injected `Clock` + `ZoneId`. Reasons: (a) static-utility with `Clock` parameter on every call is awkward for callers (every site has to inject `Clock` separately), (b) `@Component` allows future per-test `Clock.fixed(...)` substitution via `@MockBean` or constructor in tests, (c) Phase 91 precedent for stateless helper services. Static-method alternative is viable if `Clock`+`ZoneId` are passed as parameters at each call site — but every caller already has access to the bean.
- **Test-button POST mode (sync + redirect vs AJAX + inline)** — RECOMMEND synchronous POST + `redirect:` + flash attribute per CLAUDE.md § Flash Attributes pattern. Existing admin pages (driver-import, race-import, backup) all use sync POST + redirect — AJAX precedent is rare (only `csrfFetch` in `layout.html` for a few non-flash actions). Keeps Phase 93 UX consistent.

### Deferred Ideas (OUT OF SCOPE)

- Guava `CacheBuilder` promotion to first-class prod-dep (v1.14 backlog).
- Spring `@Cacheable` + `@Scheduled`-evict adoption.
- CodeQL FP suppression for positive-whitelist (only if CodeQL flags it — Plan 93-02 reactive).
- `docs/operations/discord-integration.md` operator runbook (Phase 98 DOCS-02).
- Multi-Guild support (DISC-FUTURE-04).
- Always-online deployment + inbound interaction (DISC-FUTURE-01).
- Per-user timezone override.
- Caffeine adoption.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | `DiscordRestClient` (Spring `RestClient`, Bot-Token auth, `/api/v10` versioned, 8 typed methods) + `DiscordWebhookClient` (execute + multipart + edit) + sealed `DiscordApiException` hierarchy (4 permits) + `DiscordRateLimitInterceptor` (per-bucket token-bucket, 429 retry, 5xx exponential backoff) + `DiscordTimestamps` (5 styles from `LocalDateTime`+`ZoneId`) + `DiscordEmojiCache` (60-min TTL, `team.shortName` → `<:name:id>`). Verified by WireMock-backed ITs for all 4 exception paths + rate-limit retry + multipart-upload + emoji-lookup. | § Spring RestClient + Interceptor Patterns, § Sealed Exception Hierarchy + Mapper, § Multipart Upload Mechanics, § WireMock Integration Test Patterns, § DiscordTimestamps Utility Shape |
| INFRA-02 | Threat model surfaces: (a) `DISCORD_BOT_TOKEN` env-var pattern, (b) `app.discord.allowed-hosts=discord.com` SSRF whitelist, (c) log-pattern mask for webhook URLs, (d) `@ToString.Exclude` discipline, (e) CSRF on all `POST /admin/discord/**`, (f) `DiscordConfigForm` DTO mass-assignment defense. Verified by SpotBugs (0 findings), log-snapshot test, host-whitelist test. | § Logback Masking Approach, § CodeQL Positive-Whitelist Pattern, § Sealed Exception Hierarchy + Mapper (for `@ToString.Exclude` discipline) |
| INFRA-03 | `/admin/discord-config` page (Flyway V8 `discord_global_config`) — 6 form fields + 4 test/refresh buttons. Verified by Playwright E2E filling the form + clicking each button + asserting WireMock receives the outbound calls. | § JPA Singleton Repository Pattern, § Admin Template + Typed-Catch + BEM CSS, § Playwright E2E with WireMock Backing, § Flyway V8 Cross-Engine Compatibility |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** Minimum 82% line coverage (Phase 93 holds ≥ 88.88% per D-07 baseline).
- **Flyway:** V1–V7 immutable; Phase 93 adds **V8 only** (no edits to existing migrations).
- **Profiles:** Auth only on `prod`/`docker`; `dev`/`local` remain unauthenticated.
- **OSIV:** Enabled. Use `@EntityGraph` for any optimization (none expected for Phase 93's singleton-row reads).
- **Backward Compatibility:** No breaking changes to existing URLs/endpoints.
- **Playwright:** Compile scope. Playwright E2E only in `org.ctc.e2e.*` packages, `@Tag("e2e")`.
- **Spring-native preference:** RestClient over `java.net.http.HttpClient`. Already locked by Design Spec § 3.2.
- **No inline styles on buttons:** Use BEM classes from `admin.css`.
- **DTOs not entities in `@ModelAttribute` POST:** Mass-assignment defense. `DiscordConfigForm` required.
- **Test naming:** `givenContext_whenAction_thenExpectedResult()` with `// given / // when / // then` blocks.
- **`@Tag` discipline:** `@Tag("integration")` on every new `*IT.java`. `@Tag("e2e")` on Playwright in `org.ctc.e2e.*`.
- **Static Analysis:** SpotBugs `BugInstance` count = 0 (blocking); `@SuppressFBWarnings({"CODE"}, justification="…")` only when needed.
- **CodeQL SAST:** Gate-step exit 0 on PR HEAD SHA; 3-layer FP suppression invariant (codeql-config.yml query-filter + source-marker + `sast-acceptance.md` row).
- **Lombok:** Entities `@Getter @Setter @NoArgsConstructor`, `@ToString(exclude=…)`; Services/Controllers `@RequiredArgsConstructor @Slf4j`. Alphabetical annotation order on Spring components (`@Slf4j` first).
- **No git tags locally** — release workflow tags on squash-merge.
- **Logging:** `log.info()` for state changes; `log.debug()` for calculations; parameterized `{}` format always.
- **Documentation language:** English. Communication language (with operator): German.

## Summary

Phase 93 is a textbook INFRA platform delivery: take Spring 6.1's `RestClient` (zero new prod deps), build two thin wrapping clients (`DiscordRestClient` for Bot REST + `DiscordWebhookClient` for webhook POST/PATCH), wire a single `ClientHttpRequestInterceptor` for rate-limit + backoff, and reuse Phase 91's **sealed `XxxApiException` + 4 permits + Mapper** pattern verbatim with names swapped. The admin page, V8 migration, and emoji cache are mechanical follow-throughs against established CTC conventions (DTO mass-assignment, OSIV-driven Thymeleaf, BEM error-badge palette extended by one yellow variant).

The three highest-risk technical surfaces are: (1) the **rate-limit interceptor body-stream gotcha** — `ClientHttpResponse.getBody()` is a once-only `InputStream`, so the interceptor MUST NOT read the body before passing the response back; it inspects headers only, sleeps if needed, retries the **request** (not the response), and never re-reads the body; (2) the **Logback `%replace` regex pattern** must live in `application.yml`'s `logging.pattern.console` + `logging.pattern.file` and use double-escaped backslashes (`\\s`); WireMock-tested via `OutputCaptureExtension`; (3) the **WireMock + Spring Boot 4 wiring** — WireMock is not yet a project dependency, so Plan 93-01 introduces `wiremock-standalone` 3.x + `wiremock-spring-boot` (optional sugar) as test-scope dependencies; this is the single new dependency introduced in Phase 93, scope is `test`, no production-classpath impact.

**Primary recommendation:** Treat Phase 91's `GoogleApiException` + `GoogleApiExceptionMapper` as the literal template — copy file shapes verbatim, swap names + status-code switch, swap Phase 91's `GoogleJsonResponseException` for Spring's `RestClientResponseException`. Then build outward from there. Every decision in CONTEXT.md is already strongly anchored; research's role is to make implementation mechanical for the planner.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Bot REST calls (channel, role, emoji, user) | API/Backend (`org.ctc.discord.DiscordRestClient`) | — | Outbound HTTP; auth header injection; rate-limit interceptor. |
| Webhook execute + edit (per-URL client) | API/Backend (`org.ctc.discord.DiscordWebhookClient`) | — | Webhook URL is per-channel; no shared auth; multipart for graphics. |
| Rate-limit accounting | API/Backend (`org.ctc.discord.DiscordRateLimitInterceptor`) | — | Per-bucket token-bucket; pre-request sleep; 429-retry; 5xx exponential. |
| Sealed exception mapping (HTTP → typed) | API/Backend (`org.ctc.discord.exception.DiscordApiExceptionMapper`) | — | Status code + Discord JSON `code` field switch → 4-permit dispatch. |
| Emoji cache (60-min TTL) | API/Backend (`org.ctc.discord.DiscordEmojiCache`) | — | In-memory; hand-rolled; injectable `Clock`. |
| Timestamp formatting (`<t:N:STYLE>`) | API/Backend (`org.ctc.discord.util.DiscordTimestamps`) | — | Pure helper; `Clock` + `ZoneId` injectable. |
| Singleton config row | Database/Storage (`discord_global_config`) | API (`DiscordGlobalConfigService.getOrInitialize`) | Seed-row + service-singleton enforcement; no UI-level wizard. |
| Admin config page (form + 4 buttons) | Frontend (Thymeleaf `admin/discord-config.html`) | API/Backend (controller delegates) | Server-side rendering; CSRF chain; flash-attribute UX. |
| Form-DTO mass-assignment defense | API/Backend (`org.ctc.admin.dto.DiscordConfigForm` or `org.ctc.discord.web.DiscordConfigForm`) | — | Per CLAUDE.md § DTOs not Entities in Controllers. |
| SSRF host whitelist | API/Backend (constructor guard in both clients) | — | Positive whitelist `discord.com`; analog Phase 5 `FileStorageService.validateHostname`. |
| Log masking (webhook URL redaction) | Cross-cutting (`logging.pattern` in `application.yml`) | — | Logback `%replace` converter; verified by `DiscordLogMaskingTest`. |

## Standard Stack

### Core (already on classpath — zero new prod deps)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring RestClient (in `spring-web` via `spring-boot-starter-webmvc`) | 6.1.x via Boot 4.0.6 [VERIFIED: codebase pom.xml] | HTTP client (Bot REST + Webhook) | Spring 6.1+ native synchronous client; replaces `RestTemplate`; supports `ClientHttpRequestInterceptor` reuse [CITED: spring.io/blog/2023/07/13/new-in-spring-6-1-restclient/] |
| Spring `MultipartBodyBuilder` (in `spring-web`) | 6.1.x [VERIFIED: codebase] | Multipart `Content-Type: multipart/form-data` body construction | Discord webhook file-attach contract requires `payload_json` part + `files[N]` parts [CITED: discord.food/resources/webhook + spring docs MultipartBodyBuilder] |
| Spring Data JPA (`spring-boot-starter-data-jpa`) | 4.0.6 [VERIFIED: codebase] | `DiscordGlobalConfigRepository` | Existing pattern for all repositories |
| Spring Boot Validation (`spring-boot-starter-validation`) | 4.0.6 [VERIFIED: codebase] | `DiscordConfigForm` `@Valid`/`@Pattern`/`@NotBlank` | Existing pattern for all forms |
| Flyway (`spring-boot-starter-flyway`) | 4.0.6 [VERIFIED: codebase] | V8 migration | Existing pattern; CURRENT_TIMESTAMP literal + LONGTEXT discipline (Phase 72 D-09) carries forward |
| Lombok | 1.18.46 [VERIFIED: codebase] | `@Getter @Setter @NoArgsConstructor @ToString(exclude)` + `@RequiredArgsConstructor @Slf4j` | Existing project convention; `@ToString.Exclude` discipline for token-fields |
| Jackson (`jackson-databind` + `jackson-datatype-jsr310`) | 2.21.x via Boot [VERIFIED: codebase] | Discord JSON response parsing + `payload_json` serialization | Already transitive on classpath |

### Supporting (test scope only)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| WireMock standalone | 3.9.x or later [ASSUMED — newest stable] | Mock Discord REST + webhook endpoints in ITs | All `*IT.java` exercising outbound HTTP. Plan 93-01 introduces this dep. |
| `wiremock-spring-boot` (optional sugar) | 3.x [ASSUMED] | Convenience annotation `@EnableWireMock` for Spring-Boot 3+/4 | Optional — `@RegisterExtension` static field works without it. Recommend WITHOUT this sugar dep to minimize test-scope footprint. |
| AssertJ | 3.27.x via Boot [VERIFIED: codebase] | Fluent assertions | Existing project convention |
| Mockito | 5.x via Boot [VERIFIED: codebase] | Unit-test stubs | Existing project convention |
| `spring-boot-starter-test` (already classpath) | 4.0.6 [VERIFIED: codebase] | `OutputCaptureExtension` for log-masking tests | Built into Boot Test starter |
| Awaitility | 4.x [ASSUMED] | Optional for time-sensitive retry assertions | Use only if WireMock fixed-delay + JUnit timeout isn't enough; prefer simpler tests if possible. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring `RestClient` | `java.net.http.HttpClient` (JDK) | Rejected per `[[feedback-spring-native-preference]]` + Design Spec § 3.2 + zero-new-deps; RestClient gives interceptor chain for free |
| Spring `RestClient` | JDA / Discord4J | Rejected per Design Spec § 8 (zero new prod deps) + Phase 92 PR mechanics (no dependency-review noise) |
| WireMock | Spring `MockRestServiceServer` | Rejected — `MockRestServiceServer` is only for `RestTemplate` legacy + binds to a single `RestTemplate`; WireMock is the canonical test-double for `RestClient` integration tests; supports rate-limit-header simulation, multipart-body assertions, dynamic ports, and is industry-standard |
| Hand-rolled `ConcurrentHashMap` cache | Caffeine | Per D-03 — defer Caffeine to v1.14 backlog if a 3rd cache emerges |
| Hand-rolled `ConcurrentHashMap` cache | Spring `@Cacheable` + `ConcurrentMapCacheManager` | Per D-03 rejection — no out-of-the-box TTL; would need `@Scheduled` evict-task; doesn't fit per-bucket-state token-bucket |
| `RestClient.defaultStatusHandler(...)` exception mapping | Per-call `RestClientResponseException` catch + `DiscordApiExceptionMapper.from(...)` | RECOMMEND per-call mapping inside typed methods (`fetchBotUser()` etc.). `defaultStatusHandler` is awkward when you want to inspect Discord's JSON `code` field for `30013` (category-full) before throwing — easier to catch `RestClientResponseException` and dispatch in the mapper. Phase 91 uses the same pattern (catch + mapper). |

**Installation (Plan 93-01 dependencies — `pom.xml`):**

```xml
<!-- New test-scope dependency -->
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <version>3.9.2</version>  <!-- Plan 93-01: verify latest stable at impl time -->
  <scope>test</scope>
</dependency>
```

**Version verification (run at plan-impl time):**

```bash
# Confirm latest stable WireMock 3.x
curl -s https://search.maven.org/solrsearch/select?q=g:org.wiremock+AND+a:wiremock-standalone | jq '.response.docs[0].v'
```

WireMock 3.x is the current major; ships its own JUnit-Jupiter extension (`com.github.tomakehurst.wiremock.junit5.WireMockExtension`). No `wiremock-jre8`-style legacy artifact needed.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `org.wiremock:wiremock-standalone` | Maven Central | 7+ yrs (org.wiremock since 3.x rebrand 2023; predecessor `com.github.tomakehurst:wiremock-jre8` 11+ yrs) | High (industry-standard mock HTTP server) | github.com/wiremock/wiremock | [OK] [ASSUMED — slopcheck not installed; verified via Maven Central + WireMock docs at wiremock.org/docs/junit-jupiter/] | Approved |

*slopcheck was unavailable at research time (Python `pip install slopcheck` not run — Java/Maven ecosystem; ecosystem-specific verification via Maven Central is the canonical check). The single new dependency (`org.wiremock:wiremock-standalone`) is a 10+ year industry-standard test library with no historical slopsquat incidents. Planner inserts a `checkpoint:human-verify` task before `<dependency>` addition in `pom.xml` as a belt-and-braces measure.*

## Spring RestClient + Interceptor Patterns

### Q1 — Canonical builder chain (Bot vs Webhook)

#### Bot REST client

```java
// Source: spring.io/blog/2023/07/13/new-in-spring-6-1-restclient/ + Spring 6.1 docs
@Configuration
public class DiscordConfig {

    @Bean
    public Clock systemClock() { return Clock.systemUTC(); }

    @Bean(name = "discordBotRestClient")
    public RestClient discordBotRestClient(
            @Value("${app.discord.bot-token:}") String botToken,
            @Value("${app.discord.allowed-hosts:discord.com}") String allowedHostsCsv,
            DiscordRateLimitInterceptor rateLimitInterceptor) {

        // SSRF positive-whitelist enforced before builder (Phase 5 pattern, inverted)
        Set<String> allowedHosts = Arrays.stream(allowedHostsCsv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        String baseUrl = "https://discord.com/api/v10";
        URI baseUri = URI.create(baseUrl);
        if (!allowedHosts.contains(baseUri.getHost().toLowerCase())) {
            throw new IllegalArgumentException(
                "Discord host blocked: " + baseUri.getHost());
        }

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + botToken)
                .defaultHeader(HttpHeaders.USER_AGENT, "CTC-Manager (https://github.com/jegr78/ctc-manager, 1.13)")
                .requestInterceptor(rateLimitInterceptor)
                .build();
    }
}
```

Notes:
- `defaultHeader(...)` sets the Bot-Token on every request — never logged (Logback mask + `@ToString.Exclude` on any future entity field).
- `requestInterceptor(...)` is the Spring 6.1+ `RestClient.Builder` method that registers `ClientHttpRequestInterceptor` — same shape as legacy `RestTemplate.setInterceptors(...)` [CITED: spring.io docs ClientHttpRequestInterceptor].
- `User-Agent` is **required** by Discord API; missing UA returns Cloudflare 403 instead of Discord 4xx. Always set it.

#### Webhook client (per-URL construction)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordWebhookClient {

    private final Set<String> allowedHosts;  // injected from @Value
    private final DiscordRateLimitInterceptor rateLimitInterceptor;

    public RestClient forWebhookUrl(String webhookUrl) {
        URI uri = URI.create(webhookUrl);
        String host = uri.getHost();
        if (host == null || !allowedHosts.contains(host.toLowerCase())) {
            throw new IllegalArgumentException("Discord host blocked: " + host);
        }
        return RestClient.builder()
                .baseUrl(webhookUrl)     // full URL incl. ID/token in path
                .defaultHeader(HttpHeaders.USER_AGENT, "CTC-Manager (1.13)")
                .requestInterceptor(rateLimitInterceptor)
                .build();
        // NO Authorization header — webhook URL carries auth via path segment {token}
    }
}
```

The webhook client builds a fresh `RestClient` per call (cheap — `RestClient.Builder` is lightweight). Alternative: build once in a `Map<webhookUrl, RestClient>` if profiling shows hot path. For Phase 93 stick with per-call construction (simpler).

### Q2 — Rate-limit interceptor design (the body-stream gotcha)

```java
// Source: docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final Clock clock;
    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();
    private static final int MAX_429_RETRIES = 3;
    private static final long[] FIVE_XX_BACKOFF_MS = {200, 1000, 5000};

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        // Pre-request: if we know the bucket, honor its sleep window
        // (Bucket key is per-route; we don't know it pre-first-call. So skip pre-sleep
        //  on the very first request; only sleep on retries when bucket is known.)

        ClientHttpResponse response = execution.execute(request, body);
        // Snapshot headers BEFORE returning — never read body here (once-only stream)
        HttpStatusCode status = response.getStatusCode();
        HttpHeaders headers = response.getHeaders();
        String bucket = headers.getFirst("X-RateLimit-Bucket");
        if (bucket != null) {
            int remaining = Integer.parseInt(headers.getFirst("X-RateLimit-Remaining"));
            double resetAfter = Double.parseDouble(headers.getFirst("X-RateLimit-Reset-After"));
            Instant resetAt = clock.instant().plusMillis((long)(resetAfter * 1000));
            buckets.put(bucket, new BucketState(remaining, resetAt));
        }

        if (status.value() == 429) {
            // Caller never sees this response — we close it, sleep, retry inline
            long retryAfterMs = parseRetryAfter(headers);
            response.close();
            return retry429(request, body, execution, retryAfterMs, 0);
        }
        if (status.is5xxServerError()) {
            response.close();
            return retry5xx(request, body, execution, 0);
        }
        return response;  // 2xx/4xx-not-429 → return as-is, caller reads body
    }

    private ClientHttpResponse retry429(HttpRequest req, byte[] body,
            ClientHttpRequestExecution exec, long retryAfterMs, int attempt) throws IOException {
        if (attempt >= MAX_429_RETRIES) {
            throw new DiscordTransientException(
                DiscordApiExceptionMapper.TRANSIENT_MESSAGE,
                new IOException("Rate-limit exhausted after " + MAX_429_RETRIES + " retries"));
        }
        long sleepMs = retryAfterMs + ThreadLocalRandom.current().nextLong(100, 500); // jitter
        try { Thread.sleep(sleepMs); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException(ie); }
        ClientHttpResponse retry = exec.execute(req, body);
        if (retry.getStatusCode().value() == 429) {
            long nextRetryAfter = parseRetryAfter(retry.getHeaders());
            retry.close();
            return retry429(req, body, exec, nextRetryAfter, attempt + 1);
        }
        return retry;
    }

    private ClientHttpResponse retry5xx(HttpRequest req, byte[] body,
            ClientHttpRequestExecution exec, int attempt) throws IOException {
        if (attempt >= FIVE_XX_BACKOFF_MS.length) {
            throw new DiscordTransientException(
                DiscordApiExceptionMapper.TRANSIENT_MESSAGE,
                new IOException("5xx exhausted after " + FIVE_XX_BACKOFF_MS.length + " retries"));
        }
        try { Thread.sleep(FIVE_XX_BACKOFF_MS[attempt]); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException(ie); }
        ClientHttpResponse retry = exec.execute(req, body);
        if (retry.getStatusCode().is5xxServerError()) {
            retry.close();
            return retry5xx(req, body, exec, attempt + 1);
        }
        return retry;
    }

    private long parseRetryAfter(HttpHeaders headers) {
        String h = headers.getFirst("Retry-After");
        if (h == null) return 1000;
        try { return (long)(Double.parseDouble(h) * 1000); }
        catch (NumberFormatException e) { return 1000; }
    }
}
```

**Critical design notes (the body-stream gotcha):**

1. **`ClientHttpResponse.getBody()` returns a once-only `InputStream`.** The interceptor MUST NOT call `getBody()` — it inspects headers + status only. If body is needed for a 4xx response (e.g., to read Discord's JSON `code` field for `30013` category-full), that work happens at the **caller** (the typed `DiscordRestClient.createChannel()` method) via `RestClientResponseException.getResponseBodyAsString()` AFTER the interceptor returns. The interceptor is intentionally body-agnostic.
2. **On 429/5xx retries, we `response.close()` then re-execute.** Spring's `ClientHttpResponse` extends `Closeable`; calling `close()` releases the connection and allows `execution.execute(...)` to be invoked again on the same `HttpRequest` + `body[]` pair. This is the canonical retry pattern [CITED: spring docs ClientHttpRequestInterceptor].
3. **Exhaustion throws `DiscordTransientException` from inside the interceptor.** It propagates up through `RestClient.retrieve().body(...)` as a checked `IOException` (Phase 91 sealed exception is `extends IOException` — same here). Caller-side `try/catch (DiscordApiException e)` catches it cleanly.
4. **Pre-request bucket-sleep is OPTIONAL for Phase 93.** Simplest viable interceptor: react to 429 + 5xx only. The pre-request bucket check (sleep until `resetAt` if `remaining == 0`) is a micro-optimization that requires per-route bucket-key resolution which Discord only provides AFTER the first call. Recommend deferring the pre-request optimization to a future plan or leaving it as a TODO; the post-response 429 path covers correctness.

**Sources:**
- [Spring docs — ClientHttpRequestInterceptor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html) [CITED]
- [Couchbase blog — Spring WebClient 429 RateLimit](https://www.couchbase.com/blog/spring-webclient-429-ratelimit-errors/) [CITED]
- [RetryableClientHttpRequestInterceptor GitHub](https://github.com/making/retryable-client-http-request-interceptor) — reference impl with retry-on-429 [CITED]

## Sealed Exception Hierarchy + Mapper

### Q3 — Sealed exception design + Mapper signature

#### Compile constraints (Java 25)

- `abstract sealed class DiscordApiException extends IOException permits ...` — all four permits must be in the **same Java module** as the base (CTC manager is unnamed module → must be in the same package OR explicitly `non-sealed`-permitted from outside) [CITED: docs.oracle.com/javase/specs/jls/se16/preview/specs/sealed-classes-jls.html]. CTC has no module-info.java today, so the simplest rule: **place all 5 files in `org.ctc.discord.exception` package** — identical to Phase 91's `org.ctc.dataimport.exception` package layout.
- Each permit class is `public final class XxxDiscordApiException extends DiscordApiException`. Phase 91 template uses `final` — copy verbatim.

#### File layout (mirrors Phase 91)

```
src/main/java/org/ctc/discord/exception/
├── DiscordApiException.java                  // abstract sealed class
├── DiscordApiExceptionMapper.java            // public final class, private ctor, static from(...)
├── DiscordTransientException.java            // final class extends DiscordApiException
├── DiscordAuthException.java                 // 401, 403 token, network IO
├── DiscordNotFoundException.java             // 404 channel/role/webhook/user
└── DiscordCategoryFullException.java         // Discord JSON code 30013 (50 channels/category)
```

#### Base sealed class

```java
package org.ctc.discord.exception;

import java.io.IOException;

public abstract sealed class DiscordApiException extends IOException
        permits DiscordTransientException,
                DiscordAuthException,
                DiscordNotFoundException,
                DiscordCategoryFullException {

    public enum Category { TRANSIENT, AUTH, NOT_FOUND, CATEGORY_FULL }

    protected DiscordApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract Category category();
}
```

#### Mapper signature (HTTP-based, not Google-SDK-based)

```java
package org.ctc.discord.exception;

import java.io.IOException;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DiscordApiExceptionMapper {

    public static final String TRANSIENT_MESSAGE = "Discord connection problem — retry";
    public static final String AUTH_MESSAGE      = "Discord authentication problem — check bot token";
    public static final String NOT_FOUND_MESSAGE = "Discord resource not found — verify guild/channel/webhook ID";
    public static final String CATEGORY_FULL_MESSAGE =
        "Discord archive category is full (50 channels). Create a new archive category.";

    private static final ObjectMapper OM = new ObjectMapper();

    private DiscordApiExceptionMapper() {}

    public static DiscordApiException from(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401 -> new DiscordAuthException(AUTH_MESSAGE, e);
            case 403 -> new DiscordAuthException(AUTH_MESSAGE, e);
            case 404 -> new DiscordNotFoundException(NOT_FOUND_MESSAGE, e);
            case 400 -> from400(e);
            default  -> new DiscordTransientException(TRANSIENT_MESSAGE, e);
        };
    }

    public static DiscordApiException from(IOException e) {
        if (e instanceof DiscordApiException dae) return dae; // already mapped (e.g., from interceptor)
        return new DiscordTransientException(TRANSIENT_MESSAGE, e);
    }

    private static DiscordApiException from400(RestClientResponseException e) {
        // Discord returns JSON with "code" field. Code 30013 = max channels in category.
        try {
            JsonNode root = OM.readTree(e.getResponseBodyAsString());
            int code = root.path("code").asInt(0);
            if (code == 30013) {
                return new DiscordCategoryFullException(CATEGORY_FULL_MESSAGE, e);
            }
        } catch (IOException ignored) { /* fall through */ }
        return new DiscordTransientException(TRANSIENT_MESSAGE, e);
    }
}
```

#### Phase 91 vs Phase 93 structural diff

| Aspect | Phase 91 (`GoogleApiExceptionMapper`) | Phase 93 (`DiscordApiExceptionMapper`) |
|--------|--------------------------------------|----------------------------------------|
| Input exception | `GoogleJsonResponseException` (Google SDK) | `RestClientResponseException` (Spring) |
| Status access | `gjre.getStatusCode()` (int) | `e.getStatusCode().value()` (Spring `HttpStatusCode`) |
| Body access | `gjre.getDetails().getErrors().get(0).getReason()` | `e.getResponseBodyAsString()` → Jackson `ObjectMapper.readTree(...)` |
| Discrimination | Status code + reason string from typed error | Status code + JSON `code` field (Discord-specific) |
| 403 sub-dispatch | `AUTH_REASONS` Set check | None — 403 → AUTH directly (Discord 403 always means auth/permission, not the Google-style "shared with wrong account" case) |
| Permits | `Transient/Auth/NotFound/Permission` (4) | `Transient/Auth/NotFound/CategoryFull` (4) |
| Network IO mapping | `from(IOException)` default → Transient | Same |

#### Typed-catch usage at call site

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordChannelService {

    @Qualifier("discordBotRestClient")
    private final RestClient bot;
    private final DiscordGlobalConfigService configService;

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
}
```

Note: `defaultStatusHandler(...)` on `RestClient.Builder` is an alternative — but it makes JSON-body inspection awkward (the handler receives `ClientHttpResponse` whose body is once-only). The per-call catch pattern keeps Mapper logic clean and matches Phase 91.

## Multipart Upload Mechanics

### Q4 — `MultipartBodyBuilder` shape for Discord webhook multipart

```java
// Source: Spring MultipartBodyBuilder docs + Discord webhook docs
// docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/MultipartBodyBuilder.html
// discord.food/resources/webhook + birdie0.github.io/discord-webhooks-guide/structure/file.html
public DiscordWebhookMessageDto executeMultipart(
        String webhookUrl,
        WebhookPayload payload,                              // serializes to payload_json JSON
        List<NamedAttachment> attachments) throws DiscordApiException {

    MultipartBodyBuilder builder = new MultipartBodyBuilder();

    // 1) payload_json part — JSON-encoded message content/embeds/etc.
    String payloadJson;
    try {
        payloadJson = objectMapper.writeValueAsString(payload);
    } catch (IOException e) {
        throw DiscordApiExceptionMapper.from(e);
    }
    builder.part("payload_json", payloadJson, MediaType.APPLICATION_JSON);

    // 2) files[N] parts — Discord allows up to 10 attachments per webhook
    for (int i = 0; i < attachments.size(); i++) {
        NamedAttachment att = attachments.get(i);
        builder.part("files[" + i + "]",
                new ByteArrayResource(att.bytes()) {
                    @Override public String getFilename() { return att.filename(); }
                },
                MediaType.IMAGE_PNG);
    }

    MultiValueMap<String, HttpEntity<?>> parts = builder.build();

    try {
        return forWebhookUrl(webhookUrl)
                .post()
                .uri("")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(DiscordWebhookMessageDto.class);
    } catch (RestClientResponseException e) {
        throw DiscordApiExceptionMapper.from(e);
    }
}

public record NamedAttachment(String filename, byte[] bytes) {}
```

**Multipart contract notes:**
- `payload_json` part MUST be `Content-Type: application/json` (Discord rejects `text/plain` here). `MultipartBodyBuilder.part(name, value, mediaType)` sets the part's Content-Type explicitly.
- `files[N]` keys are exactly `files[0]`, `files[1]`, ... (with brackets). Discord parses this convention literally.
- `ByteArrayResource` subclass override of `getFilename()` is the Spring-idiomatic way to attach a logical filename to a binary part. Without it, Spring uses a UUID-ish default which Discord still accepts but isn't displayed as the filename in the message.
- The webhook URL should be POSTed with empty `uri("")` since the full URL is already the `baseUrl` of the per-webhook RestClient.

### Q11 — WireMock matcher for multipart-request assertion

```java
import static com.github.tomakehurst.wiremock.client.WireMock.*;

stubFor(post(urlPathEqualTo("/api/webhooks/123/abc-token"))
    .withHeader("Content-Type", containing("multipart/form-data"))
    .withMultipartRequestBody(
        aMultipart("payload_json")
            .withHeader("Content-Type", containing("application/json"))
            .withBody(matchingJsonPath("$.content", containing("Game On!"))))
    .withMultipartRequestBody(
        aMultipart("files[0]")
            .withHeader("Content-Type", equalTo("image/png"))
            .withBody(binaryEqualTo(expectedPngBytes)))
    .willReturn(okJson("""
        {"id": "999999999999999999", "channel_id": "888888888888888888"}
        """)));
```

WireMock 3.x's `withMultipartRequestBody(aMultipart(name)...)` matcher is the canonical way to assert per-part headers + body content. `matchingJsonPath` supports JSONPath against the `payload_json` value. `binaryEqualTo(byte[])` supports exact-byte comparison for PNG attachments.

For PNG-signature-only assertion (not full byte comparison):

```java
.withMultipartRequestBody(
    aMultipart("files[0]")
        .withHeader("Content-Type", equalTo("image/png"))
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        .withBody(matching("(?s)^\\x89PNG\\r\\n\\x1a\\n.*")))
```

## WireMock Integration Test Patterns

### Q5 — Canonical IT scaffold with WireMock + Spring Boot 4

```java
package org.ctc.discord;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordRestClientWireMockIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordBaseUrl(DynamicPropertyRegistry registry) {
        // Override the production base URL to point at WireMock
        registry.add("app.discord.base-url", () -> wm.baseUrl());
        // Plus allow the localhost host in SSRF whitelist for tests
        registry.add("app.discord.allowed-hosts",
                () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.bot-token", () -> "test-bot-token");
    }

    @Autowired
    private DiscordRestClient discordRestClient;

    @Test
    void given429WithRetryAfter_whenFetchBotUser_thenInterceptorRetriesAndSucceeds() {
        // given — first call returns 429 with Retry-After: 0 (fast test)
        wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Retry-After", "0")
                        .withHeader("X-RateLimit-Bucket", "bucket-a")
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset-After", "0.1"))
                .willSetStateTo("retried"));
        wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retried")
                .willReturn(okJson("""
                    {"id":"123","username":"CTC-Bot","discriminator":"0001"}
                    """)));

        // when
        DiscordUserDto user = discordRestClient.fetchBotUser();

        // then
        assertThat(user.username()).isEqualTo("CTC-Bot");
        wm.verify(2, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
    }

    @Test
    void given401_whenFetchBotUser_thenThrowsDiscordAuthException() {
        // given
        wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"Unauthorized","code":0}""")));

        // when / then
        assertThatThrownBy(() -> discordRestClient.fetchBotUser())
                .isInstanceOf(DiscordAuthException.class)
                .hasMessageContaining("authentication problem");
    }

    @Test
    void given404_whenFetchGuildRoles_thenThrowsDiscordNotFoundException() {
        wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/999/roles"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"Unknown Guild","code":10004}""")));
        assertThatThrownBy(() -> discordRestClient.fetchGuildRoles("999"))
                .isInstanceOf(DiscordNotFoundException.class);
    }

    @Test
    void given400Code30013_whenCreateChannel_thenThrowsDiscordCategoryFullException() {
        wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/1/channels"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message":"Maximum number of channels in category reached","code":30013}""")));
        assertThatThrownBy(() -> discordRestClient.createChannel("1",
                new ChannelCreateRequest("test", 0L, "parent-id")))
            .isInstanceOf(DiscordCategoryFullException.class);
    }
}
```

**Pattern notes:**

1. **`@RegisterExtension static WireMockExtension wm` with `dynamicPort()`** — JUnit 5 picks up the static field; WireMock allocates a random port at class load. The static field is referenced by `@DynamicPropertySource` which runs BEFORE Spring context loads, so `wm.baseUrl()` is available at the right time [CITED: wiremock.org/docs/junit-jupiter/].
2. **`@DynamicPropertySource`** — registers `app.discord.base-url` (read by `DiscordConfig` to construct the `RestClient.builder().baseUrl(...)`) pointing at WireMock. Plan 93-01 introduces this property — the production `application.yml` value is `https://discord.com/api/v10`; tests override to `http://localhost:{random}/api/v10`.
3. **`allowed-hosts` test override** — adds `localhost,127.0.0.1` so SSRF whitelist accepts WireMock. Production stays `discord.com`-only.
4. **Scenarios** — `inScenario(...)` + `whenScenarioStateIs(...)` + `willSetStateTo(...)` is WireMock's stateful-stub pattern; perfect for "first call 429, second call 200" retry tests.
5. **`Retry-After: 0`** — keeps tests fast. The interceptor still sleeps for the random-jitter window (100–500 ms) which is acceptable for one or two ITs but adds up if every retry test runs full sleep. Recommend: parameterize jitter via `@Value("${app.discord.rate-limit.jitter-ms:100-500}")` with test override `0-1` for fast tests. Lock this in 93-01 plan.

**Sources:**
- [WireMock JUnit 5+ Jupiter docs](https://wiremock.org/docs/junit-jupiter/) [CITED]
- [JavaThinking — WireMock random port Spring Boot](https://www.javathinking.com/blog/set-property-with-wiremock-random-port-in-spring-boot-test/) [CITED]
- [Rieckpil — Spring Boot integration tests WireMock JUnit 5](https://rieckpil.de/spring-boot-integration-tests-with-wiremock-and-junit-5/) [CITED]

## Logback Masking Approach

### Q6 — Webhook URL mask via Logback `%replace`

**Approach 1 — `application.yml` `logging.pattern.console` (simplest, no XML file):**

```yaml
# application.yml — applies to ALL profiles
logging:
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %replace(%m){'https://discord\\.com/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://discord.com/api/webhooks/***/***'}%n%wEx"
    file:    "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%15.15t] %-40.40logger{39} : %replace(%m){'https://discord\\.com/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://discord.com/api/webhooks/***/***'}%n%wEx"
```

**Key details:**
- `%replace(P){regex,replacement}` is a built-in Logback converter [CITED: copyprogramming.com/howto/mask-sensitive-data-in-logs-with-logback]; no custom Java class required.
- `%m` is the log-message placeholder (not the entire log line). Mask only the message body; preserve timestamp/level/logger structure.
- Backslashes must be doubled for YAML escape: `\\.` → `\.` regex, `\\s` → `\s` regex.
- `%wEx` at the end masks the stacktrace ONLY if it's also wrapped — by default `%wEx` (default exception conversion) does NOT pass through `%replace`. To mask the URL in exception messages too, use `%replace(%wEx){...}` — but that requires more careful pattern definition.

**Better approach — apply `%replace` to the whole message including exception:**

```yaml
logging:
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %replace(%m%n%wEx){'https://discord\\.com/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://discord.com/api/webhooks/***/***'}"
    file:    "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%15.15t] %-40.40logger{39} : %replace(%m%n%wEx){'https://discord\\.com/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://discord.com/api/webhooks/***/***'}"
```

Wrapping `%m%n%wEx` together inside `%replace(...)` masks the message AND the stacktrace lines. The regex anchors at `https://discord.com/api/webhooks/` to avoid false positives.

**Approach 2 — `logback-spring.xml` (richer, supports multiple patterns):** Only adopt if Approach 1 proves insufficient. Default to YAML.

### `DiscordLogMaskingTest` shape

```java
package org.ctc.discord;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class DiscordLogMaskingIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort()).build();

    private static final String WEBHOOK_URL_TOKEN_FRAGMENT = "secret-token-xyz-12345";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
    }

    @Autowired
    private DiscordWebhookClient webhookClient;

    @Test
    void givenTransientException_whenLogContainsWebhookUrl_thenUrlIsMasked(CapturedOutput out) {
        // given — WireMock 500 will provoke retry-loop-exhaust → DiscordTransientException
        wm.stubFor(post(urlPathMatching("/api/webhooks/.*"))
                .willReturn(aResponse().withStatus(500)));
        String webhookUrl = wm.baseUrl() + "/api/webhooks/999/" + WEBHOOK_URL_TOKEN_FRAGMENT;

        // when
        assertThatThrownBy(() -> webhookClient.execute(webhookUrl, new WebhookPayload("hi")))
                .isInstanceOf(DiscordTransientException.class);

        // then — captured logs include the masking pattern but NOT the original secret
        assertThat(out.getAll())
                .doesNotContain(WEBHOOK_URL_TOKEN_FRAGMENT)
                .contains("***/***");
    }
}
```

**Critical test design note:** The Logback `%replace` regex is anchored at `https://discord.com/api/webhooks/` but the test webhook URL uses `http://localhost:{port}/api/webhooks/` — so the regex would NOT mask the test URL. **Two options:**

1. **Loosen the regex** to also accept `localhost` test URLs — but this expands the masking pattern unnecessarily.
2. **Use a profile-specific override:** `application-test.yml` has a relaxed pattern like `https?://[^/]+/api/webhooks/[^/\s]+/[^/\s]+`. This is the better choice — production stays strict, test profile gets the broader pattern.

Recommend Plan 93-02 lock this as: production `logging.pattern` is strict (`https://discord.com/...`), test profile (`@ActiveProfiles("dev")`) inherits the production pattern — so the test must use a real-shape Discord URL (e.g., point WireMock at `discord.com` via DNS override) OR use a loosened pattern. The DNS-override path is brittle; recommend the **loosened-for-test** approach: `application.yml` uses a pattern matching `https?://[^/]+/api/webhooks/[^/\s]+/[^/\s]+` which masks both production and test URLs but only ones with the `/api/webhooks/` path segment. False-positive risk is low (no other system writes "api/webhooks" path segments to logs).

**Final recommendation for `application.yml`:**

```yaml
logging:
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %replace(%m%n%wEx){'https?://[^/\\s]+/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://***/api/webhooks/***/***'}"
    file:    "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%15.15t] %-40.40logger{39} : %replace(%m%n%wEx){'https?://[^/\\s]+/api/webhooks/[^/\\s]+/[^/\\s]+', 'https://***/api/webhooks/***/***'}"
```

This masks BOTH real Discord URLs and WireMock-loopback test URLs by replacing both host and token segments. False-positive radius is bounded to literal `/api/webhooks/` path strings.

**Sources:**
- [BootcampToProd — Mask Sensitive Data in Logs](https://bootcamptoprod.com/how-to-mask-sensitive-data-in-logs/) [CITED]
- [HowToDoInJava — Logback masking sensitive data](https://howtodoinjava.com/logback/masking-sensitive-data/) [CITED]
- [Spring Boot `OutputCaptureExtension` Javadoc](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/test/system/OutputCaptureExtension.html) [CITED]

## CodeQL Positive-Whitelist Pattern

### Q7 — Does positive-whitelist need FP suppression?

**Probable answer: YES, but reactively (only if CodeQL flags it).**

Per [CodeQL java/ssrf query help](https://codeql.github.com/codeql-query-help/java/java-ssrf/), the recommended SSRF mitigation IS a positive whitelist:

> "To guard against SSRF attacks, you should maintain a list of authorized URLs on the server and choose from that list based on the input provided"

So the official guidance suggests a positive-whitelist should NOT trip the rule. However, [github/codeql Issue #20117](https://github.com/github/codeql/issues/20117) and [Issue #9306](https://github.com/github/codeql/issues/9306) document cases where CodeQL DOES still flag whitelist-validated input — particularly when the whitelist check uses a method call that CodeQL doesn't recognize as a sanitizer (`Set.contains(host)` is not on CodeQL's known-sanitizer list).

**Therefore:**
1. **Implement the positive-whitelist first** — `allowedHosts.contains(uri.getHost().toLowerCase())`.
2. **Run CodeQL locally** via `./mvnw verify` + check the SARIF report.
3. **If flagged:**
   - Add source-marker comment immediately above the validation:
     ```java
     // CodeQL FP: java/ssrf — positive whitelist via Set.contains(host.toLowerCase());
     // app.discord.allowed-hosts is config-only (no user input). See docs/security/sast-acceptance.md.
     ```
   - Add a `<query-filter>` entry in `.github/codeql/codeql-config.yml` for the specific Alert-ID.
   - Add a corresponding row in `docs/security/sast-acceptance.md` § SSRF table with rationale.
4. **If NOT flagged:** Plan 93-02 moves on — no suppression needed.

**Comparison to Phase 5 `validateHostname`:**
- Phase 5 = NEGATIVE blocklist (`if (hostname.startsWith("127.")) throw IllegalArgument`). CodeQL needs to be told `validateHostname` is a sanitizer.
- Phase 93 = POSITIVE whitelist (`if (!allowedHosts.contains(host)) throw IllegalArgument`). CodeQL's heuristics may or may not recognize this as a sanitizer depending on how the host string flows.

**SpotBugs/find-sec-bugs side:** the `SSRF_SPRING`/`SSRF` detector in find-sec-bugs is bytecode-based; positive whitelist likely passes without suppression. Phase 5's existing `<Match>` entry in `config/spotbugs-exclude.xml` for `FileStorageService` is for the NEGATIVE blocklist; Phase 93 should NOT inherit that suppression for `DiscordRestClient`/`DiscordWebhookClient`.

**Decision for Plan 93-02:** Implement positive-whitelist; run `./mvnw verify` + check CodeQL workflow output on the PR; only add 3-layer suppression IF an alert appears. Document the result in `93-THREAT-MODEL.md` § Verification column for T-93-01/02.

## JPA Singleton Repository Pattern

### Q8 — `DiscordGlobalConfig` entity, repo, service

#### Entity

```java
package org.ctc.discord.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.ctc.domain.model.BaseEntity;

@Entity
@Table(name = "discord_global_config")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"announcementWebhookUrl"})  // T-93-02 webhook URL leak mitigation
public class DiscordGlobalConfig extends BaseEntity {

    @Column(name = "guild_id", length = 32, nullable = false)
    private String guildId;

    @Column(name = "announcement_webhook_url", length = 500, nullable = false)
    @ToString.Exclude  // double-cover: Lombok 1.18+ supports both class-level and field-level
    private String announcementWebhookUrl;

    @Column(name = "race_results_forum_channel_id", length = 32, nullable = false)
    private String raceResultsForumChannelId;

    @Column(name = "standings_forum_channel_id", length = 32, nullable = false)
    private String standingsForumChannelId;

    @Column(name = "vs_emoji_name", length = 50, nullable = false)
    private String vsEmojiName = "CTC";

    @Column(name = "bot_application_id", length = 32)
    private String botApplicationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

**Important: Verify `BaseEntity` actually lives in `org.ctc.domain.model`** — per Phase 72 D-15, the backup `EXPORT_ORDER` filter is `org.ctc.domain.model.*`. `DiscordGlobalConfig` lives in `org.ctc.discord.model` — STRUCTURALLY EXCLUDED from backup. `BaseEntity` reuse is fine; it's a parent class, not a leaf entity.

**Verification step in Plan 93-03:** confirm `BackupSchemaGuardTest` still reports `EXPORT_ORDER == 24`. The guard test scans `org.ctc.domain.model.*` only; entities under `org.ctc.discord.model.*` are invisible to it.

#### Repository

```java
package org.ctc.discord.repository;

import org.ctc.discord.model.DiscordGlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscordGlobalConfigRepository extends JpaRepository<DiscordGlobalConfig, Long> {
    /** Returns the seed-row (V8 INSERT guarantees presence). */
    DiscordGlobalConfig findFirstByOrderByIdAsc();
}
```

Note: return type is **non-Optional** because V8 seeds one row. If the row somehow doesn't exist (DB corruption), Spring Data returns `null` and `getOrInitialize()` should defensively re-insert OR throw — see below.

#### Service

```java
package org.ctc.discord.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordGlobalConfigService {

    private final DiscordGlobalConfigRepository repo;
    private final Clock clock;

    /**
     * Returns the singleton config row. V8 seed-row INSERT (D-02) guarantees presence;
     * if the row is missing (DB corruption), defensively create + persist.
     */
    @Transactional
    public DiscordGlobalConfig getOrInitialize() {
        DiscordGlobalConfig existing = repo.findFirstByOrderByIdAsc();
        if (existing != null) return existing;

        log.warn("discord_global_config seed row missing — re-initializing");
        DiscordGlobalConfig fresh = new DiscordGlobalConfig();
        fresh.setGuildId("");
        fresh.setAnnouncementWebhookUrl("");
        fresh.setRaceResultsForumChannelId("");
        fresh.setStandingsForumChannelId("");
        fresh.setVsEmojiName("CTC");
        fresh.setBotApplicationId(null);
        fresh.setCreatedAt(clock.instant());
        fresh.setUpdatedAt(clock.instant());
        return repo.save(fresh);
    }

    /**
     * Updates the singleton row from the form DTO. Always updates id from getOrInitialize()
     * so save() never produces a 2nd INSERT.
     */
    @Transactional
    public DiscordGlobalConfig save(DiscordConfigForm form) {
        DiscordGlobalConfig current = getOrInitialize();
        current.setGuildId(form.getGuildId());
        current.setAnnouncementWebhookUrl(form.getAnnouncementWebhookUrl());
        current.setRaceResultsForumChannelId(form.getRaceResultsForumChannelId());
        current.setStandingsForumChannelId(form.getStandingsForumChannelId());
        current.setVsEmojiName(form.getVsEmojiName());
        current.setUpdatedAt(clock.instant());
        log.info("Updated discord_global_config (id={}, guildId={})", current.getId(), current.getGuildId());
        return repo.save(current);  // id present → UPDATE, not INSERT
    }
}
```

#### Form DTO

```java
package org.ctc.discord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class DiscordConfigForm {

    @Pattern(regexp = "^$|^\\d{17,20}$",
             message = "guild ID must be empty or a Discord snowflake (17–20 digits)")
    private String guildId;

    @Size(max = 500)
    @Pattern(regexp = "^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$",
             message = "announcement webhook URL must be empty or a Discord webhook URL")
    private String announcementWebhookUrl;

    @Pattern(regexp = "^$|^\\d{17,20}$")
    private String raceResultsForumChannelId;

    @Pattern(regexp = "^$|^\\d{17,20}$")
    private String standingsForumChannelId;

    @NotBlank
    @Size(max = 50)
    private String vsEmojiName;
}
```

The `^$|^...$` pattern accepts BOTH empty (for the bootstrap empty-config state per D-12) AND validly-formatted snowflake/URL strings. `vsEmojiName` is `@NotBlank` because the seed-row defaults to `CTC`.

## Admin Template + Typed-Catch + BEM CSS

### Q9 — Thymeleaf template + controller + new CSS variant

#### CSS additions (Plan 93-02 or 93-03 — small, add wherever convenient)

```css
/* admin.css — ADD ONE new variant; .badge-warning already exists at line 357 */
.error-badge--category-full { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
/* Identical palette to .error-badge--transient and .badge-warning; visually distinguishable
   by the badge label text ("category-full" vs "transient"). */
```

**No NEW neutral badge needed for "not configured"** — `.badge-warning` already exists at line 357 with the yellow palette (`#3b2e0e` bg, `#ffb74d` text). Reuse.

#### Controller skeleton

```java
package org.ctc.discord.web;  // or org.ctc.admin.controller.discord — planner discretion

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.exception.*;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/discord-config")
public class DiscordConfigController {

    private final DiscordGlobalConfigService configService;
    private final DiscordRestClient discord;
    private final DiscordWebhookClient webhook;
    private final DiscordEmojiCache emojiCache;
    // private final DiscordRoleCache roleCache;  // Phase 94 scope; placeholder

    @GetMapping
    public String show(Model model) {
        DiscordGlobalConfig cfg = configService.getOrInitialize();
        model.addAttribute("config", cfg);
        DiscordConfigForm form = new DiscordConfigForm();
        form.setGuildId(cfg.getGuildId());
        form.setAnnouncementWebhookUrl(cfg.getAnnouncementWebhookUrl());
        form.setRaceResultsForumChannelId(cfg.getRaceResultsForumChannelId());
        form.setStandingsForumChannelId(cfg.getStandingsForumChannelId());
        form.setVsEmojiName(cfg.getVsEmojiName());
        model.addAttribute("form", form);
        return "admin/discord-config";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") DiscordConfigForm form,
                       BindingResult br,
                       RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Form has validation errors");
            ra.addFlashAttribute("errorCategory", "auth");  // visual hint only
            return "redirect:/admin/discord-config";
        }
        configService.save(form);
        ra.addFlashAttribute("successMessage", "Configuration saved");
        return "redirect:/admin/discord-config";
    }

    @PostMapping("/test-connection")
    public String testConnection(RedirectAttributes ra) {
        try {
            var user = discord.fetchBotUser();
            ra.addFlashAttribute("successMessage",
                "Test Connection OK — Bot: " + user.username());
        } catch (DiscordApiException e) {
            applyErrorFlash(ra, e);
        }
        return "redirect:/admin/discord-config";
    }

    @PostMapping("/test-webhook")
    public String testWebhook(RedirectAttributes ra) {
        DiscordGlobalConfig cfg = configService.getOrInitialize();
        if (cfg.getAnnouncementWebhookUrl().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Announcement webhook URL not configured");
            ra.addFlashAttribute("errorCategory", "not-found");
            return "redirect:/admin/discord-config";
        }
        try {
            webhook.execute(cfg.getAnnouncementWebhookUrl(),
                new WebhookPayload("CTC Manager hello — " + java.time.Instant.now()));
            ra.addFlashAttribute("successMessage", "Test Webhook posted to announcement channel");
        } catch (DiscordApiException e) {
            applyErrorFlash(ra, e);
        }
        return "redirect:/admin/discord-config";
    }

    @PostMapping("/refresh-emoji-cache")
    public String refreshEmojiCache(RedirectAttributes ra) {
        DiscordGlobalConfig cfg = configService.getOrInitialize();
        if (cfg.getGuildId().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Guild ID not configured");
            ra.addFlashAttribute("errorCategory", "not-found");
            return "redirect:/admin/discord-config";
        }
        try {
            int n = emojiCache.refresh(cfg.getGuildId());
            ra.addFlashAttribute("successMessage", "Refreshed " + n + " emojis");
        } catch (DiscordApiException e) {
            applyErrorFlash(ra, e);
        }
        return "redirect:/admin/discord-config";
    }

    @PostMapping("/refresh-roles-cache")
    public String refreshRolesCache(RedirectAttributes ra) {
        // Phase 94 owns role cache; Phase 93 stub returns "not yet implemented"
        // OR simply calls discord.fetchGuildRoles() and counts without caching
        DiscordGlobalConfig cfg = configService.getOrInitialize();
        if (cfg.getGuildId().isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Guild ID not configured");
            ra.addFlashAttribute("errorCategory", "not-found");
            return "redirect:/admin/discord-config";
        }
        try {
            int n = discord.fetchGuildRoles(cfg.getGuildId()).size();
            ra.addFlashAttribute("successMessage", "Fetched " + n + " roles");
        } catch (DiscordApiException e) {
            applyErrorFlash(ra, e);
        }
        return "redirect:/admin/discord-config";
    }

    // Typed-catch per Phase 91 D-06/D-07 — exhaustive switch on sealed permits
    private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e) {
        String category = switch (e) {
            case DiscordTransientException ignored     -> "transient";
            case DiscordAuthException ignored          -> "auth";
            case DiscordNotFoundException ignored      -> "not-found";
            case DiscordCategoryFullException ignored  -> "category-full";
        };
        // Whitelisted user-message ONLY — never e.getMessage() (T-91-02-IL invariant)
        String userMessage = switch (e.category()) {
            case TRANSIENT     -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
            case AUTH          -> DiscordApiExceptionMapper.AUTH_MESSAGE;
            case NOT_FOUND     -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
            case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
        };
        ra.addFlashAttribute("errorMessage", "Discord operation failed: " + userMessage);
        ra.addFlashAttribute("errorCategory", category);
        log.warn("Discord operation failed: category={} cause={}", category, e.toString());
    }
}
```

#### Thymeleaf template skeleton (`admin/discord-config.html`)

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Discord Configuration', ~{::section})}">
<body>
<section>
  <h1>Discord Configuration</h1>

  <!-- successMessage / errorMessage rendered by layout.html (existing pattern) -->

  <form th:action="@{/admin/discord-config/save}" th:object="${form}" method="post" class="admin-form">
    <div class="form-row">
      <label for="guildId">Guild ID
        <span th:if="${#strings.isEmpty(form.guildId)}" class="badge-warning">not configured</span>
      </label>
      <input id="guildId" type="text" th:field="*{guildId}" placeholder="e.g. 1234567890123456789">
      <small th:if="${#fields.hasErrors('guildId')}" th:errors="*{guildId}" class="form-error"></small>
    </div>

    <div class="form-row">
      <label>Bot Token Status
        <span th:if="${@discordTokenStatus.isPresent()}" class="success-badge">configured (env var)</span>
        <span th:unless="${@discordTokenStatus.isPresent()}" class="badge-warning">not configured (set DISCORD_BOT_TOKEN)</span>
      </label>
      <!-- read-only; token never rendered in form -->
    </div>

    <div class="form-row">
      <label for="announcementWebhookUrl">Announcement Webhook URL
        <span th:if="${#strings.isEmpty(form.announcementWebhookUrl)}" class="badge-warning">not configured</span>
      </label>
      <input id="announcementWebhookUrl" type="text" th:field="*{announcementWebhookUrl}"
             placeholder="https://discord.com/api/webhooks/…">
      <small th:if="${#fields.hasErrors('announcementWebhookUrl')}" th:errors="*{announcementWebhookUrl}" class="form-error"></small>
    </div>

    <div class="form-row">
      <label for="raceResultsForumChannelId">Race Results Forum Channel ID
        <span th:if="${#strings.isEmpty(form.raceResultsForumChannelId)}" class="badge-warning">not configured</span>
      </label>
      <input id="raceResultsForumChannelId" type="text" th:field="*{raceResultsForumChannelId}">
    </div>

    <div class="form-row">
      <label for="standingsForumChannelId">Standings Forum Channel ID
        <span th:if="${#strings.isEmpty(form.standingsForumChannelId)}" class="badge-warning">not configured</span>
      </label>
      <input id="standingsForumChannelId" type="text" th:field="*{standingsForumChannelId}">
    </div>

    <div class="form-row">
      <label for="vsEmojiName">VS Emoji Name</label>
      <input id="vsEmojiName" type="text" th:field="*{vsEmojiName}" placeholder="CTC">
    </div>

    <button type="submit" class="btn btn-primary">Save</button>
  </form>

  <h2>Test &amp; Refresh</h2>
  <div class="button-row">

    <form th:action="@{/admin/discord-config/test-connection}" method="post" style="display:inline">
      <button type="submit" class="btn"
              th:disabled="${!@discordTokenStatus.isPresent()}"
              th:title="${!@discordTokenStatus.isPresent() ? 'Set DISCORD_BOT_TOKEN env var first' : 'Test Bot REST connectivity (GET /users/@me)'}"
              data-testid="test-connection">Test Connection</button>
    </form>

    <form th:action="@{/admin/discord-config/test-webhook}" method="post" style="display:inline">
      <button type="submit" class="btn"
              th:disabled="${#strings.isEmpty(config.announcementWebhookUrl)}"
              th:title="${#strings.isEmpty(config.announcementWebhookUrl) ? 'Fill Announcement Webhook URL first' : 'Send a hello message to the announcement webhook'}"
              data-testid="test-webhook">Test Announcement-Webhook</button>
    </form>

    <form th:action="@{/admin/discord-config/refresh-roles-cache}" method="post" style="display:inline">
      <button type="submit" class="btn"
              th:disabled="${#strings.isEmpty(config.guildId)}"
              th:title="${#strings.isEmpty(config.guildId) ? 'Fill Guild ID first' : 'Refresh server-roles cache'}"
              data-testid="refresh-roles">Refresh Server-Roles Cache</button>
    </form>

    <form th:action="@{/admin/discord-config/refresh-emoji-cache}" method="post" style="display:inline">
      <button type="submit" class="btn"
              th:disabled="${#strings.isEmpty(config.guildId)}"
              th:title="${#strings.isEmpty(config.guildId) ? 'Fill Guild ID first' : 'Refresh server-emoji cache'}"
              data-testid="refresh-emoji">Refresh Emoji Cache</button>
    </form>

  </div>
</section>
</body>
</html>
```

**Notes:**
- `@discordTokenStatus` — Spring `@Bean` that exposes `Optional<String>` of the bot-token presence (NEVER the value itself). Returns `Optional.empty()` if env-var unset.
- `style="display:inline"` on `<form>` — wrapping each button in its own form is required because CSRF is per-form. Inline style here is on the `<form>`, not the button, so it doesn't violate CLAUDE.md § "No Inline Styles on Buttons". Alternative: a `button-row` CSS rule that handles form layout.
- `data-testid` attributes drive the Playwright E2E.
- `th:disabled` + `th:title` deliver the D-12 "gated buttons with tooltip" UX.
- The 5 BEM error-badge variants (`transient/auth/not-found/permission/category-full`) and the 1 neutral `.badge-warning` cover all states. `.error-badge--permission` is unused by Phase 93 (Discord 403 → AUTH) but stays in the palette for Phase 94 forward.
- All "not configured" badges use `class="badge-warning"` per D-12.

#### Sidebar nav addition (`admin/layout.html`)

```html
<div class="sidebar-group">
    <span class="sidebar-group-label">Integrations</span>
    <a th:href="@{/admin/discord-config}"
       th:classappend="${title.contains('Discord') ? 'active' : ''}">Discord</a>
</div>
```

Insert after the "Data" group (which currently contains "Backup") — keeps Integrations as a separate concern group. Phase 94+ adds more entries to this group.

## Playwright E2E with WireMock Backing

### Q10 — Test class scaffold

```java
package org.ctc.e2e.discord;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class DiscordConfigPageE2ETest extends PlaywrightConfig {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url",   () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.bot-token",  () -> "test-bot-token");
    }

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void givenEmptyConfig_whenLoadingPage_thenNotConfiguredBadgesShown() {
        page.navigate(url("/admin/discord-config"));
        assertThat(page.locator("label:has-text('Guild ID') .badge-warning")).isVisible();
        assertThat(page.locator("label:has-text('Announcement Webhook URL') .badge-warning")).isVisible();
        // Test-Connection button disabled until token configured
        assertThat(page.locator("[data-testid=test-connection]")).isDisabled();
    }

    @Test
    void givenWireMockOk_whenClickingTestConnection_thenSuccessFlashShown() {
        // given — token property is set in @DynamicPropertySource, button is enabled
        wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
                .willReturn(okJson("""
                    {"id":"123","username":"CTC-TestBot","discriminator":"0001"}
                    """)));

        // Save the guild ID to satisfy form first (optional — Test-Connection only needs token)
        page.navigate(url("/admin/discord-config"));

        // when
        page.locator("[data-testid=test-connection]").click();
        page.waitForURL("**/admin/discord-config");

        // then
        assertThat(page.locator(".alert-success")).containsText("CTC-TestBot");
        wm.verify(1, getRequestedFor(urlPathEqualTo("/api/v10/users/@me")));
    }

    @Test
    void givenWireMock401_whenClickingTestConnection_thenAuthErrorBadgeShown() {
        // given
        wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
                .willReturn(aResponse().withStatus(401).withBody("""{"message":"Unauthorized"}""")));

        // when
        page.navigate(url("/admin/discord-config"));
        page.locator("[data-testid=test-connection]").click();
        page.waitForURL("**/admin/discord-config");

        // then
        assertThat(page.locator(".error-badge--auth")).isVisible();
        assertThat(page.locator(".alert-error")).containsText("authentication problem");
    }

    @Test
    void givenSavedGuildId_whenClickingRefreshEmojiCache_thenWireMockReceivesCall() {
        // given — first save guildId via form
        wm.stubFor(get(urlPathMatching("/api/v10/guilds/.+/emojis"))
                .willReturn(okJson("""
                    [{"id":"e1","name":"AHR","animated":false},
                     {"id":"e2","name":"CTC","animated":false}]
                    """)));
        page.navigate(url("/admin/discord-config"));
        page.locator("#guildId").fill("1234567890123456789");
        page.locator("button[type=submit]:has-text('Save')").click();
        page.waitForURL("**/admin/discord-config");

        // when
        page.locator("[data-testid=refresh-emoji]").click();
        page.waitForURL("**/admin/discord-config");

        // then
        assertThat(page.locator(".alert-success")).containsText("Refreshed 2 emojis");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/v10/guilds/1234567890123456789/emojis")));
    }
}
```

**Critical gotchas to call out for the planner:**

1. **Spring Boot's `@RANDOM_PORT` vs WireMock's dynamic port** — `PlaywrightConfig` uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` for the Spring HTTP server. WireMock uses its own `dynamicPort()` for the Discord mock server. These are two different ports. `@LocalServerPort` (Playwright target) ≠ `wm.getPort()` (WireMock target).
2. **`@DynamicPropertySource` runs BEFORE Spring context loads** — `wm.baseUrl()` is available because `@RegisterExtension static` fields initialize at class load, BEFORE `@DynamicPropertySource`. This ordering is documented and supported [CITED: rieckpil.de + wiremock.org/docs/junit-jupiter/].
3. **The Spring-Boot-side `RestClient` actually talks to `wm.baseUrl()`** — verify by `wm.verify(getRequestedFor(...))`. The Playwright session talks to the Spring HTTP server which internally invokes the RestClient which hits WireMock.
4. **PlaywrightConfig is the existing base class** — reuse via `extends PlaywrightConfig`. No new test infra needed.
5. **Mobile + Desktop variants** — `[[feedback-playwright-cli]]` requires both viewports for UI-touching phases. Add a `@ParameterizedTest` source over `Desktop` + `Mobile` viewports in Plan 93-03 — see `LegacyMigratedSeasonE2ETest` for existing reference. NOT required for every test, just the visual-verification ones.

## DiscordTimestamps Utility Shape

### Q12 — Recommended bean shape

**RECOMMEND: Spring `@Component` with constructor-injected `Clock` + `ZoneId`.**

```java
package org.ctc.discord.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;

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

**Why a bean, not a static utility:**

1. **Testability** — replace `Clock` with `Clock.fixed(...)` in unit tests via constructor:
   ```java
   var ts = new DiscordTimestamps(Clock.fixed(Instant.parse("2026-05-21T10:00:00Z"), ZoneOffset.UTC), "UTC");
   assertThat(ts.longDateTime(LocalDateTime.of(2026, 5, 21, 10, 0))).isEqualTo("<t:1779696000:F>");
   ```
2. **ZoneId centrally configured** — `app.timezone` reads once at startup; per-test override via `@DynamicPropertySource`.
3. **No `Clock` parameter ergonomics tax** — callers inject `DiscordTimestamps` and call `ts.longDateTime(dt)` — clean.
4. **Future-proof** — if Phase 96+ needs per-user TZ (deferred per DISC-FUTURE), the bean shape supports adding a `ZoneId override` parameter without breaking existing callers.

Design Spec § 3.7 shows the static signature `longDateTime(LocalDateTime dt, ZoneId zone)` — that's the wire shape, not necessarily the implementation shape. Bean wraps it cleanly.

## Flyway V8 Cross-Engine Compatibility

### Q13 — V8 SQL + cross-engine verification

```sql
-- src/main/resources/db/migration/V8__discord_global_config.sql
-- Phase 93 INFRA-03: Discord global configuration singleton.
-- The table holds exactly ONE row (D-02 service-side singleton via findFirstByOrderByIdAsc()).
-- The seed-row INSERT below guarantees presence on fresh DBs so the admin page renders
-- "not configured" badges (D-12) without requiring a setup wizard.
--
-- Column type rationale (cross-engine H2 2.x + MariaDB 10.7+):
--   - id BIGINT AUTO_INCREMENT: native on both engines.
--   - VARCHAR(32) for Discord snowflakes: 17-20 digits max; 32 leaves room.
--   - VARCHAR(500) for webhook URL: Discord webhook URLs are ~125 chars; 500 = generous.
--   - VARCHAR(50) for vs_emoji_name: Discord emoji names max 32 chars; 50 = generous.
--   - TIMESTAMP NOT NULL: H2 + MariaDB both accept; CURRENT_TIMESTAMP literal works.
--   - DEFAULT '' on string columns: H2 + MariaDB both accept empty-string default.
--
-- DO NOT modify this file after release (CLAUDE.md Constraints).

CREATE TABLE discord_global_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    guild_id VARCHAR(32) NOT NULL DEFAULT '',
    announcement_webhook_url VARCHAR(500) NOT NULL DEFAULT '',
    race_results_forum_channel_id VARCHAR(32) NOT NULL DEFAULT '',
    standings_forum_channel_id VARCHAR(32) NOT NULL DEFAULT '',
    vs_emoji_name VARCHAR(50) NOT NULL DEFAULT 'CTC',
    bot_application_id VARCHAR(32),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Seed-row INSERT (D-02): guarantees findFirstByOrderByIdAsc() always returns one row.
-- Empty-string fields trigger the "not configured" badges (D-12) on the admin page.
INSERT INTO discord_global_config
    (guild_id, announcement_webhook_url, race_results_forum_channel_id,
     standings_forum_channel_id, vs_emoji_name, bot_application_id,
     created_at, updated_at)
VALUES
    ('', '', '', '', 'CTC', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

**Cross-engine verification checks (Plan 93-03 must run):**

1. **`./mvnw verify` on H2 (default `dev` profile)** — V8 migration applies, seed-row inserts, `findFirstByOrderByIdAsc()` returns the seed.
2. **`./mvnw verify` on Testcontainers MariaDB** (existing `.github/workflows/mariadb-migration-smoke.yml`) — V8 migration applies on MariaDB:11, same row count.
3. **`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`** — manual smoke test against local MariaDB (carried-forward QUAL-02 UAT). Confirm V8 applies and `/admin/discord-config` renders.

**Findings:**
- `CURRENT_TIMESTAMP` literal in `INSERT` works on both H2 2.x and MariaDB.
- `DEFAULT ''` on VARCHAR works on both. (H2 enforces; MariaDB accepts.)
- `BIGINT AUTO_INCREMENT` works on both. The seed-row gets `id=1` on a fresh database deterministically; `findFirstByOrderByIdAsc()` returns it.
- No `CHECK` constraints — per D-02 rationale, CHECK enforcement drifts between H2 strict mode and older MariaDB. Service-side singleton via `findFirstByOrderByIdAsc()` is dialect-neutral.
- No `LONGTEXT` columns — Phase 72 D-09 drift risk avoided.

**Future migration (V9+) NOT in Phase 93 scope** but worth noting for planner:
- V9 will `ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32)` (Phase 94).
- V10/V11/V12 — Phases 94-96.
- Phase 93's V8 is structurally independent — additive only, no FK to anything.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot 4.0.6 test starter [VERIFIED: pom.xml] |
| Config file | `pom.xml` Surefire/Failsafe (tag-routed); no separate config file |
| Quick run command | `./mvnw -Dtest=Discord* test` (unit tests only) |
| Full suite command | `./mvnw verify -Pe2e` (unit + integration + Playwright E2E) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| INFRA-01 | `DiscordRestClient.fetchBotUser()` returns parsed `DiscordUserDto` on 200 | integration | `./mvnw -Dit.test=DiscordRestClientWireMockIT verify` | ❌ Wave 0 |
| INFRA-01 | `DiscordRestClient.fetchBotUser()` throws `DiscordAuthException` on 401 | integration | `./mvnw -Dit.test=DiscordRestClientWireMockIT verify` | ❌ Wave 0 |
| INFRA-01 | `DiscordRestClient` throws `DiscordAuthException` on 403 | integration | (same IT class) | ❌ Wave 0 |
| INFRA-01 | `DiscordRestClient.fetchGuildRoles()` throws `DiscordNotFoundException` on 404 | integration | (same IT class) | ❌ Wave 0 |
| INFRA-01 | `DiscordRestClient.createChannel()` throws `DiscordCategoryFullException` on 400 + code 30013 | integration | (same IT class) | ❌ Wave 0 |
| INFRA-01 | `DiscordRateLimitInterceptor` retries 3× on 429 with `Retry-After` sleep | integration | `./mvnw -Dit.test=DiscordRateLimitInterceptorIT verify` | ❌ Wave 0 |
| INFRA-01 | `DiscordRateLimitInterceptor` exponential 5xx backoff exhaustion → `DiscordTransientException` | integration | (same IT class) | ❌ Wave 0 |
| INFRA-01 | `DiscordWebhookClient.executeMultipart()` sends `payload_json` + `files[0]` parts | integration | `./mvnw -Dit.test=DiscordWebhookClientMultipartIT verify` | ❌ Wave 0 |
| INFRA-01 | `DiscordEmojiCache.emojiFor()` returns cached `<:NAME:id>` long-form within TTL | unit | `./mvnw -Dtest=DiscordEmojiCacheTest test` | ❌ Wave 0 |
| INFRA-01 | `DiscordEmojiCache.emojiFor()` falls back to `:NAME:` literal on missing emoji | unit | (same test class) | ❌ Wave 0 |
| INFRA-01 | `DiscordEmojiCache.refresh()` rebuilds via `DiscordRestClient.fetchGuildEmojis()` | integration | `./mvnw -Dit.test=DiscordEmojiCacheRefreshIT verify` | ❌ Wave 0 |
| INFRA-01 | `DiscordApiExceptionMapper.from(RestClientResponseException)` switches on status + JSON code | unit | `./mvnw -Dtest=DiscordApiExceptionMapperTest test` | ❌ Wave 0 |
| INFRA-01 | `DiscordTimestamps.longDateTime(LocalDateTime)` returns `<t:UNIX:F>` | unit | `./mvnw -Dtest=DiscordTimestampsTest test` | ❌ Wave 0 |
| INFRA-01 | `DiscordTimestamps` covers all 6 styles (F/f/D/d/t/R) | unit | (same test class) | ❌ Wave 0 |
| INFRA-02 | Webhook-URL never appears unmasked in logs on transient-exception path | integration | `./mvnw -Dit.test=DiscordLogMaskingIT verify` | ❌ Wave 0 |
| INFRA-02 | `DiscordRestClient` constructor rejects non-`discord.com` host | unit | `./mvnw -Dtest=DiscordClientHostWhitelistTest test` | ❌ Wave 0 |
| INFRA-02 | `DiscordWebhookClient.forWebhookUrl()` rejects non-`discord.com` host | unit | (same test class) | ❌ Wave 0 |
| INFRA-02 | `@ToString.Exclude` discipline holds: `DiscordGlobalConfig.toString()` does not contain webhook URL | unit | `./mvnw -Dtest=DiscordGlobalConfigToStringTest test` | ❌ Wave 0 |
| INFRA-02 | `DiscordConfigForm` rejects invalid snowflake-pattern guild ID | unit | `./mvnw -Dtest=DiscordConfigFormTest test` | ❌ Wave 0 |
| INFRA-03 | Flyway V8 migration applies cleanly on H2 + MariaDB; seed-row present | integration | `./mvnw -Dit.test=DiscordGlobalConfigRepositoryIT verify` | ❌ Wave 0 |
| INFRA-03 | `DiscordGlobalConfigService.getOrInitialize()` returns seed-row | integration | (same IT class) | ❌ Wave 0 |
| INFRA-03 | `DiscordGlobalConfigService.save(form)` updates same row (no second INSERT) | integration | (same IT class) | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config` GET renders with badge-warning for empty fields | integration | `./mvnw -Dit.test=DiscordConfigControllerIT verify` | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config/save` POST validates form + flashes success | integration | (same IT class) | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config/test-connection` calls WireMock + renders success | E2E | `./mvnw -Dtest=DiscordConfigPageE2ETest verify -Pe2e` | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config/test-webhook` calls WireMock + renders success | E2E | (same E2E class) | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config/refresh-emoji-cache` calls WireMock + renders count | E2E | (same E2E class) | ❌ Wave 0 |
| INFRA-03 | `/admin/discord-config/refresh-roles-cache` calls WireMock + renders count | E2E | (same E2E class) | ❌ Wave 0 |
| INFRA-03 | Each error category renders the correct BEM badge variant | E2E | (same E2E class) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw -Dtest=Discord* test` (unit tests, ~30 seconds)
- **Per wave merge:** `./mvnw verify` (unit + integration, ~6 min)
- **Phase gate:** `./mvnw verify -Pe2e` green before `/gsd-verify-work` (~17–20 min including Playwright)

### Wave 0 Gaps

All Phase 93 test files are NEW. Plan structure should create these in order:

- [ ] `src/test/java/org/ctc/discord/DiscordApiExceptionMapperTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordTimestampsTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordConfigFormTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordGlobalConfigToStringTest.java` — unit
- [ ] `src/test/java/org/ctc/discord/DiscordRestClientWireMockIT.java` — integration (4 sealed-exception paths)
- [ ] `src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java` — integration (429 retry + 5xx backoff)
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` — integration (multipart contract)
- [ ] `src/test/java/org/ctc/discord/DiscordEmojiCacheRefreshIT.java` — integration (refresh path)
- [ ] `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` — integration (log-snapshot regression)
- [ ] `src/test/java/org/ctc/discord/DiscordGlobalConfigRepositoryIT.java` — integration (V8 + repo)
- [ ] `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` — integration (MockMvc)
- [ ] `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` — E2E (Playwright + WireMock)
- [ ] `pom.xml` — add `org.wiremock:wiremock-standalone` test-scope dependency

### Coverage Math

JaCoCo target: ≥ 88.88% line coverage (D-07 baseline).

Estimated new lines of production code: ~600 LOC (`DiscordRestClient` ~120 + `DiscordWebhookClient` ~80 + interceptor ~100 + 4 exception files + mapper ~80 + emoji cache ~70 + timestamps ~40 + entity/repo/service ~100 + controller ~120 + DTO ~40 + util records ~30).

Estimated new test LOC: ~1500 LOC across 14 test files. Coverage ratio ~2.5× — comfortable for hitting ≥ 88.88%.

Exclusions to apply in `pom.xml` JaCoCo config (planner verifies in Plan 93-03):
- `DiscordTimestamps` — pure helper, full coverage trivial.
- Spring `@Configuration` (`DiscordConfig`) — auto-config beans, exclude or cover via `@SpringBootTest` autowire.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Discord Bot-Token via env-var only; never logged; `@ToString.Exclude`; profile-scoped (`prod`/`docker` enforce Spring Security; `dev`/`local` no auth per CLAUDE.md) |
| V3 Session Management | yes | Existing Spring Session (Phase 30); no changes |
| V4 Access Control | yes | Existing CSRF chain on all `POST /admin/discord/**`; Phase 30 + OpenSecurityConfig dual-profile pattern unchanged |
| V5 Input Validation | yes | `DiscordConfigForm` `@Pattern` snowflake regex `^$|^\d{17,20}$`; webhook-URL regex; `@NotBlank` on vsEmojiName |
| V6 Cryptography | no (this phase) | Discord HTTPS only — TLS handled by JDK + Spring; no app-level crypto |
| V7 Error Handling | yes | Sealed `DiscordApiException` hierarchy — hardcoded user-message constants; `e.getMessage()` never echoed to flash (T-91-02-IL invariant carry-forward) |
| V10 Malicious Code | no | No code execution from external input |
| V12 API and Web Service | yes | `RestClient` with `User-Agent`, Bot-Token in `Authorization` header, host-allowlist SSRF defense, CodeQL `java/ssrf` gate |
| V14 Configuration | yes | `application*.yml` token-env-var pattern; no secret in committed YAML; `logging.pattern` mask |

### Known Threat Patterns for Spring Boot + outbound HTTP

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF via webhook URL injection | Tampering / Info Disclosure | Positive whitelist `app.discord.allowed-hosts=discord.com` enforced in client constructors; SpotBugs find-sec-bugs scan; CodeQL `java/ssrf` query |
| Bot-Token leak via logs | Info Disclosure | `@ToString.Exclude`, Logback `%replace` mask for webhook URLs; SpotBugs scan; never `e.getMessage()` in flash (whitelisted constants only) |
| Webhook-URL leak via stacktrace | Info Disclosure | `logging.pattern` `%replace` mask wraps `%m%n%wEx`; `DiscordLogMaskingIT` regression |
| CSRF on admin POST endpoints | Tampering | Phase 30 CSRF chain inherited; `<form>` per-button pattern with `_csrf` token |
| Mass-assignment via direct entity binding | Tampering | `DiscordConfigForm` DTO with explicit fields; `@Valid` + `BindingResult`; entity never `@ModelAttribute`'d |
| 4xx info-leak via raw error echo | Info Disclosure | Whitelisted user-message constants in `DiscordApiExceptionMapper`; controller never echoes `e.getMessage()` |
| Rate-limit-burst → bot-ban | DoS (against ourselves) | `DiscordRateLimitInterceptor` token-bucket + max-3 retry + exponential 5xx backoff |
| Channel-permission bypass via wrong role-mapping | Tampering (Phase 94 scope) | Phase 94 CHAN-02 post-create permission-audit assertion; documented as T-93-03 forward-reference in `93-THREAT-MODEL.md` |

## Architecture Patterns

### System Architecture Diagram

```
                ┌─────────────────────────────────────────────────────┐
                │ Operator (Browser, Desktop + Mobile)                │
                └────────────────┬────────────────────────────────────┘
                                 │ HTTP + CSRF
                 ┌───────────────┴───────────────────┐
                 │ /admin/discord-config (Thymeleaf) │
                 │ • 6 fields + 4 test buttons       │
                 │ • flash-attribute success/error   │
                 └───────────────┬───────────────────┘
                                 │
        ┌────────────────────────┴────────────────────────────────┐
        │ DiscordConfigController (CSRF-protected)                │
        │ • Validates form (@Valid DiscordConfigForm)             │
        │ • Delegates to services                                 │
        │ • Typed-catch → flash errorCategory                     │
        └────┬───────────────┬────────────────────────────┬───────┘
             │               │                            │
   ┌─────────▼───────┐ ┌─────▼──────────────┐ ┌──────────▼─────────┐
   │ DiscordGlobalCfg│ │ DiscordRestClient  │ │ DiscordWebhookClient│
   │ Service         │ │  • Bot-Token auth  │ │  • per-URL builder  │
   │  • getOrInit()  │ │  • /api/v10 base   │ │  • multipart support│
   │  • save(form)   │ │  • interceptor     │ │  • interceptor      │
   └────────┬────────┘ └────────┬───────────┘ └──────────┬──────────┘
            │                   │                        │
   ┌────────▼────────┐ ┌────────▼─────────────┐          │
   │ JPA Repository  │ │ RateLimitInterceptor │          │
   │ findFirstBy…    │ │  • token-bucket map  │          │
   └────────┬────────┘ │  • 429 retry         │          │
            │          │  • 5xx exp backoff   │          │
   ┌────────▼─────────┐│  • Clock injectable  │          │
   │ V8 discord_glob… ││  • throws Transient  │          │
   │  • seed row      │└────────┬─────────────┘          │
   │  • singleton     │         │                        │
   └──────────────────┘         │                        │
                                │                        │
                                ▼                        ▼
                       ┌──────────────────────────────────┐
                       │ HTTPS (discord.com only —        │
                       │ SSRF whitelist enforced)         │
                       │ • Webhook-URL log-masked         │
                       │ • Bot-Token never logged         │
                       └──────────────────────────────────┘

   Cross-cutting:
   ┌──────────────────────────────────────────────────────────┐
   │ DiscordApiException (sealed, IOException)                │
   │   permits: Transient | Auth | NotFound | CategoryFull    │
   │ DiscordApiExceptionMapper.from(RestClientResponseEx)     │
   │   → status code switch + JSON.code field discrimination  │
   └──────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────────────┐
   │ DiscordEmojiCache (ConcurrentHashMap<String, Cached<>>)  │
   │   • 60-min TTL, manual refresh                           │
   │   • Clock injectable                                     │
   │                                                          │
   │ DiscordTimestamps (@Component)                           │
   │   • <t:UNIX:F/f/D/d/t/R> from LocalDateTime              │
   │   • Clock + ZoneId injectable                            │
   └──────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
src/main/java/org/ctc/discord/
├── DiscordConfig.java                      // @Configuration; @Bean Clock, RestClient builders, allowedHosts
├── DiscordRestClient.java                  // Bot REST client (8 typed methods)
├── DiscordWebhookClient.java               // Webhook POST/PATCH per-URL
├── DiscordRateLimitInterceptor.java        // ClientHttpRequestInterceptor
├── DiscordEmojiCache.java                  // 60-min TTL cache
├── dto/
│   ├── DiscordConfigForm.java              // Phase 93 form DTO
│   ├── DiscordUserDto.java                 // GET /users/@me response
│   ├── DiscordChannelDto.java
│   ├── DiscordRoleDto.java
│   ├── DiscordEmojiDto.java
│   └── WebhookPayload.java                 // POST body for webhook execute
├── exception/
│   ├── DiscordApiException.java            // sealed base
│   ├── DiscordApiExceptionMapper.java
│   ├── DiscordTransientException.java
│   ├── DiscordAuthException.java
│   ├── DiscordNotFoundException.java
│   └── DiscordCategoryFullException.java
├── model/
│   └── DiscordGlobalConfig.java            // JPA entity (NOT in org.ctc.domain.model — excluded from EXPORT_ORDER)
├── repository/
│   └── DiscordGlobalConfigRepository.java  // findFirstByOrderByIdAsc()
├── service/
│   └── DiscordGlobalConfigService.java     // getOrInitialize, save
├── util/
│   ├── DiscordTimestamps.java              // @Component
│   ├── CachedEntry.java                    // record CachedEntry<T>(T value, Instant expiresAt)
│   └── BucketState.java                    // record BucketState(int remaining, Instant resetAt)
└── web/
    └── DiscordConfigController.java        // /admin/discord-config GET + POST + 4 test endpoints

src/main/resources/
├── db/migration/V8__discord_global_config.sql
└── templates/admin/discord-config.html
```

### Pattern 1: Sealed Exception Hierarchy + Mapper (Phase 91 carry-forward)

**What:** Single base `abstract sealed class` extending `IOException`; 4 `final` permits; static `Mapper.from(...)` dispatching on HTTP status + body JSON code.

**When to use:** Every typed-error contract where consumer code needs an exhaustive `switch` over error categories.

**Example:** See § Sealed Exception Hierarchy + Mapper above.

### Pattern 2: Singleton-Row Entity via Seed-Insert (D-02)

**What:** Flyway migration `CREATE TABLE` + inline `INSERT` of one empty-string row; repository exposes only `findFirstByOrderByIdAsc()`; service `getOrInitialize()` returns the seed deterministically.

**When to use:** Operator-configurable singletons that should "always exist" without a setup wizard.

### Pattern 3: Hand-rolled `ConcurrentHashMap<K, CachedEntry<T>>` (D-03)

**What:** Shared `record CachedEntry<T>(T value, Instant expiresAt)` consumed by emoji cache + rate-limit bucket store. `Clock` injectable for test fast-forward.

### Pattern 4: Typed-Catch Flash Pattern (Phase 91 D-06/D-07)

**What:** Controller catches sealed exception, exhaustive `switch` on permits → `errorCategory` flash attribute → Thymeleaf BEM `.error-badge--{category}` renders.

### Anti-Patterns to Avoid

- **Reading the response body inside an interceptor.** `ClientHttpResponse.getBody()` returns a once-only `InputStream`; the body must be consumed by `RestClient.retrieve().body(...)`. Interceptors inspect headers + status ONLY.
- **`e.getMessage()` echoed to flash attributes.** T-91-02-IL info-leak invariant: ALWAYS use whitelisted hardcoded `*_MESSAGE` constants from the Mapper.
- **Static `DiscordTimestamps` without `Clock` injection** — tests cannot fast-forward time.
- **Entity-level `@ModelAttribute`** for form binding — mass-assignment risk. Use `DiscordConfigForm` DTO.
- **CHECK constraint `id = 1`** — drifts between H2 strict and older MariaDB lenient; D-02 explicitly rejects this approach.
- **`@SuppressWarnings("all")` blanket suppression** — forbidden per CLAUDE.md; use targeted `@SuppressFBWarnings({"CODE"}, justification=...)`.
- **Static-method utility for `DiscordTimestamps` without `ZoneId`/`Clock` parameters** — calls become unergonomic; bean shape preferred.
- **Per-call `MockRestServiceServer`** — limited to `RestTemplate`; doesn't fit `RestClient` cleanly. Use WireMock.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client | `java.net.http.HttpClient` wrapper | Spring `RestClient` | Spring 6.1+ native; interceptor chain; baseUrl + defaultHeader; idiomatic |
| Multipart body construction | Hand-built `String boundary = "----...";` | Spring `MultipartBodyBuilder` | Correct Content-Type headers, per-part media-type, multi-value support |
| Rate-limit token bucket | DIY scheduler | `ClientHttpRequestInterceptor` + `ConcurrentHashMap<String, BucketState>` (D-03) | Hand-rolled is fine here per D-03; the ALTERNATIVE you'd build naively is a `@Scheduled` task — that doesn't fit per-request bucket semantics |
| HTTP mocking in ITs | Local Spring `MockMvc` for outbound calls | WireMock + `@RegisterExtension` | `MockMvc` is for inbound testing of YOUR controllers; outbound HTTP needs a network-level mock |
| JSON parsing | DIY Discord response parsers | Jackson `ObjectMapper` + DTO records | Already classpath; auto-config; well-known |
| Sealed exception dispatch | If/else cascade | `switch (e) { case ... }` exhaustive pattern match | Java 21+ pattern matching; Phase 91 precedent |
| Log masking | Custom Logback appender | `%replace(%m){...}` converter (built-in Logback) | Zero code; YAML-only config; deployment-safe |
| Singleton table enforcement | DB CHECK constraint | Service-side `findFirstByOrderByIdAsc()` + seed-row INSERT | D-02 — H2/MariaDB CHECK behavior differs |

**Key insight:** Spring 6.1's `RestClient` + `MultipartBodyBuilder` + `ClientHttpRequestInterceptor` covers 95% of the integration mechanics. The 5% that requires CTC-specific code is the **sealed exception mapper**, the **rate-limit interceptor logic** (token-bucket state), and the **typed-catch flash UX** — all three are direct ports of established CTC patterns (Phase 91 + Phase 5 + Phase 91 D-06/D-07).

## Runtime State Inventory

This is a **greenfield** capability addition, not a rename/refactor. No prior Discord runtime state exists to migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no existing Discord tables (verified by `grep -rn "discord" src/main/resources/db/migration/`) | N/A |
| Live service config | None — no Discord webhooks or external Discord service config exist today | N/A |
| OS-registered state | None — no scheduled tasks or pm2/launchd registrations | N/A |
| Secrets/env vars | `DISCORD_BOT_TOKEN` is NEW env-var; documented in `application-local.yml` (Plan 93-02) | New env-var; operator sets before live UAT-03 (D-01) |
| Build artifacts | None — no existing Discord-related compiled artifacts | N/A |

## Common Pitfalls

### Pitfall 1: Reading the Response Body Inside an Interceptor

**What goes wrong:** Calling `response.getBody()` inside `intercept(...)` consumes the once-only `InputStream`; the caller-side `RestClient.retrieve().body(...)` then reads an empty stream → silent JSON-parse failure or null result.

**Why it happens:** Naive interceptor design assumes the response is reusable.

**How to avoid:** Inspect `response.getStatusCode()` + `response.getHeaders()` ONLY. Never call `response.getBody()` in the interceptor.

**Warning signs:** Test assertions on body content fail with "empty payload" while WireMock confirms the body was sent.

### Pitfall 2: Spring Boot 4 + WireMock Port Race Condition

**What goes wrong:** Spring context loads before WireMock starts → `app.discord.base-url` is unset or points at the wrong host.

**Why it happens:** `@DynamicPropertySource` runs at Spring boot, but only if the static `@RegisterExtension` field is already initialized (which JUnit 5 guarantees IF the field is `static`).

**How to avoid:** Always declare `WireMockExtension` as `static @RegisterExtension`; never instance-level.

**Warning signs:** `IllegalArgumentException: URL hostname blocked: null` from the SSRF whitelist on test startup.

### Pitfall 3: Logback `%replace` Regex Escape Confusion

**What goes wrong:** YAML escape eats backslashes; the regex pattern fails to match.

**Why it happens:** YAML requires `\\` for a literal backslash; Logback's regex parser then sees `\` for the regex metacharacter.

**How to avoid:** Test the pattern with `DiscordLogMaskingIT` BEFORE shipping. Use IDE syntax-highlighting on the YAML file.

**Warning signs:** Log output contains the original URL with no `***` substitution.

### Pitfall 4: Discord 429 Without `Retry-After` Header

**What goes wrong:** Discord's global rate-limit returns 429 with body `{"global": true, "retry_after": 0.5}` but the header `Retry-After` may be missing or set to 0.

**Why it happens:** Discord uses two rate-limit categories (per-route bucket vs global) with slightly different response shapes.

**How to avoid:** Default `parseRetryAfter` to 1000ms when the header is missing; for completeness, also parse the JSON body's `retry_after` field as fallback. Plan 93-01 owns this nuance.

**Warning signs:** Hot-loop retries with `NumberFormatException` swallowed by default fallback.

### Pitfall 5: Webhook PATCH Missing `webhook_id`/`webhook_token` Pair

**What goes wrong:** Webhook-PATCH requires the full `{webhook_id}/{webhook_token}/messages/{message_id}` URL; if only `webhook_id` is stored, edit fails.

**Why it happens:** Webhook URLs are `https://discord.com/api/webhooks/{id}/{token}` — the `token` is a SECRET that's not retrievable from the Bot API.

**How to avoid:** Phase 93's `DiscordWebhookClient` stores neither — Phase 95 (POST-01) introduces the `discord_post` table with `webhook_id`, `webhook_token`, `message_id` columns. Phase 93 only ensures `executeMultipart()` works for the test-button on `/admin/discord-config` (announcement-webhook); editing capability is verified in Phase 95.

**Warning signs:** N/A in Phase 93; surfaces in Phase 95.

### Pitfall 6: H2 vs MariaDB `CURRENT_TIMESTAMP` Precision

**What goes wrong:** H2 stores `TIMESTAMP` with millisecond precision; MariaDB stores with second precision (without explicit `TIMESTAMP(3)`).

**Why it happens:** Default precision differs between engines.

**How to avoid:** For Phase 93's audit-equivalent fields (`created_at` / `updated_at`), second-precision is acceptable. Don't promote to `TIMESTAMP(3)` unless a future requirement needs millisecond-resolution comparisons.

**Warning signs:** `DiscordGlobalConfigRepositoryIT` assertions on timestamp equality fail on MariaDB but pass on H2.

### Pitfall 7: CSRF Token Missing on AJAX `csrfFetch`

**What goes wrong:** If the 4 test buttons are converted to AJAX (planner discretion area), missing CSRF header → 403 from Spring Security on `prod`/`docker` profile.

**Why it happens:** `csrfFetch` helper in `layout.html` correctly attaches the CSRF header — but only IF caller uses it.

**How to avoid:** Stick with sync POST + `<form>` per-button (RECOMMEND above). If AJAX, MUST use `csrfFetch` helper.

**Warning signs:** Buttons silently fail on `prod` profile; work fine on `dev`.

## Code Examples

### Bot REST Client typed method

```java
public DiscordUserDto fetchBotUser() throws DiscordApiException {
    try {
        return bot.get()
            .uri("/users/@me")
            .retrieve()
            .body(DiscordUserDto.class);
    } catch (RestClientResponseException e) {
        throw DiscordApiExceptionMapper.from(e);
    } catch (IOException e) {
        throw DiscordApiExceptionMapper.from(e);
    }
}

public List<DiscordRoleDto> fetchGuildRoles(String guildId) throws DiscordApiException {
    try {
        return bot.get()
            .uri("/guilds/{gid}/roles", guildId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<DiscordRoleDto>>() {});
    } catch (RestClientResponseException e) {
        throw DiscordApiExceptionMapper.from(e);
    }
}
```

### EmojiCache shape

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordEmojiCache {
    private final DiscordRestClient discord;
    private final Clock clock;
    private static final Duration TTL = Duration.ofMinutes(60);
    private final ConcurrentHashMap<String, CachedEntry<String>> cache = new ConcurrentHashMap<>();

    public String emojiFor(String shortName) {
        CachedEntry<String> entry = cache.get(shortName);
        if (entry != null && entry.expiresAt().isAfter(clock.instant())) {
            return entry.value();
        }
        return ":" + shortName + ":";  // fallback literal (D-03 + Design Spec § 3.8)
    }

    public int refresh(String guildId) throws DiscordApiException {
        List<DiscordEmojiDto> emojis = discord.fetchGuildEmojis(guildId);
        Instant expires = clock.instant().plus(TTL);
        cache.clear();
        for (DiscordEmojiDto e : emojis) {
            String longForm = "<:" + e.name() + ":" + e.id() + ">";
            cache.put(e.name(), new CachedEntry<>(longForm, expires));
        }
        log.info("DiscordEmojiCache refreshed: {} emojis cached", emojis.size());
        return emojis.size();
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` | `RestClient` (Spring 6.1+) | Spring 6.1 (Nov 2023) | New synchronous client; reuses interceptors |
| Discord4J / JDA | Self-built on `RestClient` | Phase 93 design (D-Spec-1) | Zero new prod deps; minimal surface area; outbound-only |
| Auto-trigger on DB event | Button-triggered | Phase 93 design (D-Operator-Control) | Operator control over Discord output |
| Per-team emoji DB column | Convention-based via `team.shortName` + cache | Phase 93 design (D-Spec-15) | No new DB column; reflects "Discord server is source of truth" |
| `<unix-timestamp>` server-rendered | `<t:UNIX:STYLE>` Discord-native | Phase 93 design (D-Spec-14) | Per-viewer TZ rendering by Discord client |

**Deprecated/outdated:**
- `RestTemplate` is still maintained but considered legacy; new code uses `RestClient`.
- Apache HttpClient 4.x — superseded by JDK `HttpClient` + Spring `RestClient`.
- WireMock 2.x `com.github.tomakehurst:wiremock-jre8` — superseded by WireMock 3.x `org.wiremock:wiremock-standalone`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | WireMock `org.wiremock:wiremock-standalone` is the canonical 3.x test-dep (versus deprecated `com.github.tomakehurst:wiremock-jre8`) | Standard Stack | LOW — both produce equivalent behavior; planner verifies latest stable via Maven Central at plan-impl time |
| A2 | Discord error code `30013` = "Maximum number of channels in category" | Sealed Exception Hierarchy + Mapper | MEDIUM — if code is wrong, `DiscordCategoryFullException` never fires; Plan 93-01 verifies via WireMock fixture w/ real-shape response, or against live Discord during UAT-03 |
| A3 | Discord rate-limit headers (`X-RateLimit-Bucket`/`-Remaining`/`-Reset-After`) are reliably present on successful responses | Spring RestClient + Interceptor Patterns | LOW — Discord's documented contract; the interceptor handles missing headers gracefully (defensive null checks) |
| A4 | `RestClientResponseException` is the canonical exception thrown by `RestClient.retrieve()` on 4xx/5xx | Sealed Exception Hierarchy + Mapper | LOW — Spring docs verified; Phase 93 catches both `RestClientResponseException` (typed) and `IOException` (defensive) |
| A5 | `OutputCaptureExtension` captures all Logback output including stacktraces | Logback Masking Approach | LOW — documented behavior; verified pattern in CTC codebase isn't widely used but stdlib feature |
| A6 | The webhook-URL log-mask regex won't false-positive on unrelated `/api/webhooks/` paths in app logs | Logback Masking Approach | LOW — CTC doesn't log any other `/api/webhooks/` paths today; verified by grep across `src/main/java` |
| A7 | Phase 91's typed-catch flash pattern is verbatim portable (no Spring 4.x breakage) | Admin Template + Typed-Catch + BEM CSS | LOW — Phase 91 just shipped; pattern is current |
| A8 | CodeQL `java/ssrf` will NOT flag positive-whitelist via `Set.contains(host.toLowerCase())` | CodeQL Positive-Whitelist Pattern | MEDIUM — could go either way; Plan 93-02 verifies post-impl and applies 3-layer suppression only if flagged |
| A9 | H2 2.x + MariaDB 10.7+ both accept `DEFAULT ''` on `VARCHAR NOT NULL` columns | Flyway V8 Cross-Engine Compatibility | LOW — Phase 72 D-09 cross-engine discipline verified this pattern is portable |
| A10 | `bot_application_id VARCHAR(32) NULL` (no NOT NULL) is accepted by both engines as nullable | Flyway V8 Cross-Engine Compatibility | LOW — standard SQL |
| A11 | Phase 95+ `discord_post.webhook_token` introduces new secret-leak surface; Phase 93 only sets the discipline (`@ToString.Exclude`) | Security Domain | LOW — Phase 95 inherits the discipline via threat-model forward-reference T-93-01 |
| A12 | `DiscordTimestamps` as `@Component` is preferred over static utility | DiscordTimestamps Utility Shape | LOW — planner discretion; both work |

## Open Questions

1. **Should `DiscordEmojiCache` cold-load on startup or on first `emojiFor()` call?**
   - What we know: D-03 says "manual refresh button on `/admin/discord-config`"; D-12 says "Refresh Emoji Cache button gated on guild_id present".
   - What's unclear: First-call semantics. If guild_id is empty (D-12), `emojiFor` returns `:NAME:` fallback. If guild_id is set but cache cold, does `emojiFor` lazy-refresh or return fallback?
   - Recommendation: Return fallback `:NAME:` until manual refresh OR background `@Scheduled` refresh (NOT in Phase 93 scope — defer to Phase 97 if matchday-batch posting starts failing). Phase 93 ships with manual-refresh-only.

2. **Should pre-request bucket-sleep be implemented in Phase 93 or deferred?**
   - What we know: D-03 says "Per-bucket token-bucket semantics: pre-request check `bucket.remaining > 0` else sleep until `resetAt`".
   - What's unclear: First-call bucket-key resolution. Discord only returns `X-RateLimit-Bucket` AFTER the call; we don't know which bucket a new URI will land in.
   - Recommendation: Implement reactive (post-response) 429-handling in Phase 93. Implement pre-request bucket-sleep ONLY when first-call returns a known bucket-key + subsequent call to same route hits `remaining == 0`. Document the gap in `93-THREAT-MODEL.md` T-93-04 verification column.

3. **Does the Test-Roles button need a server-roles cache in Phase 93 or just fetch + count?**
   - What we know: D-03 + Design Spec § 4.3 mention "Refresh Server-Roles Cache" button.
   - What's unclear: Cache vs uncached. Phase 93 only has `DiscordEmojiCache` per CONTEXT.md; Phase 94 CHAN-01 adds the live-dropdown that requires a roles cache.
   - Recommendation: Phase 93 wires "Refresh Server-Roles Cache" button to a `fetchGuildRoles().size()` call without a cache — the success flash shows the count. Phase 94 introduces the actual `DiscordRolesCache`. Document the gap in 93-03 PLAN.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | All Phase 93 code | ✓ [VERIFIED: codebase] | 25 | — |
| Maven (`./mvnw`) | Build | ✓ [VERIFIED: codebase] | 3.x | — |
| H2 2.x | Test/dev | ✓ [VERIFIED: codebase] | 2.x via Boot | — |
| MariaDB 11 | CI smoke test | ✓ [VERIFIED: codebase] | 11 via Testcontainers | — |
| Playwright 1.59.0 | E2E test | ✓ [VERIFIED: codebase] | 1.59.0 | — |
| WireMock | INTEGRATION tests | ✗ NEW DEP | 3.9.x [ASSUMED — verify latest at impl time] | — (no fallback; Plan 93-01 adds dep) |
| Discord API (test guild) | Live UAT-03 (deferred to Phase 94 prerequisite) | ✗ Operator-side | — | UAT-03 deferred per D-01 |

**Missing dependencies with no fallback:** None blocking Phase 93 implementation.

**Missing dependencies with fallback:** WireMock — Plan 93-01 adds `org.wiremock:wiremock-standalone` 3.9.x test-scope to `pom.xml`. Slopcheck audit table above lists this as `[OK]` with Maven-Central legitimacy.

## Implementation Risks

1. **Rate-limit interceptor body-stream gotcha** — see Pitfall 1. The interceptor MUST NOT call `response.getBody()`. WireMock test verifies retry happens but doesn't read body in interceptor.
2. **WireMock + Spring Boot 4 first-time setup friction** — `@RegisterExtension static` + `@DynamicPropertySource` ordering must be exact. Plan 93-01 should create a base IT class `AbstractDiscordWireMockIT` if 3+ ITs duplicate the setup; otherwise inline per class.
3. **Logback regex YAML escape** — `\\s` vs `\s` confusion. Verify visually + via `DiscordLogMaskingIT`.
4. **CSRF + flash attribute composition** — 4 buttons each in their own `<form>` for CSRF — verify in `DiscordConfigControllerIT` MockMvc + Playwright E2E.
5. **CodeQL positive-whitelist may need FP suppression** — see § CodeQL Positive-Whitelist Pattern. Reactive — apply 3-layer triad ONLY if CodeQL flags. Add a contingency line item in Plan 93-02.
6. **EXPORT_ORDER = 24 invariant** — `DiscordGlobalConfig` lives in `org.ctc.discord.model`, excluded by Phase 72 D-15 package filter. Verify `BackupSchemaGuardTest` stays at 24 by running it after V8 migration applies.
7. **Multipart upload `payload_json` Content-Type** — Discord requires `application/json` on the `payload_json` part; default `text/plain` rejection is silent (200 but ignored). Test asserts via WireMock matcher.
8. **The "Refresh Server-Roles Cache" button in Phase 93** — no actual cache exists yet (Phase 94 owns it). Phase 93 wires the button to a `fetchGuildRoles().size()` count + success flash. Plan 93-03 documents this explicitly.
9. **Test infrastructure JaCoCo exclusions** — `DiscordTimestamps` + `DiscordConfig` (Spring `@Configuration`) — verify they don't drag coverage down without contributing meaningful test surface. Plan 93-01 + 93-03 plan-time decision.
10. **Phase 93's V8 + Phase 92's clean baseline = 88.88%** — Phase 92 hasn't shipped yet (per STATE.md, Phase 92 "Ready to execute"). Phase 93 RESEARCH assumes the baseline lands before Phase 93 execution starts. If Phase 92 doesn't fully restore, Phase 93 starts under the 88.44% reality but still must hit ≥ 88.88% on its own delta.

## Sources

### Primary (HIGH confidence)

- [Spring 6.1 RestClient announcement (spring.io blog)](https://spring.io/blog/2023/07/13/new-in-spring-6-1-restclient/) — builder API, interceptor support
- [Spring docs — `ClientHttpRequestInterceptor`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html) — interceptor contract; response body stream semantics
- [Spring docs — `MultipartBodyBuilder`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/client/MultipartBodyBuilder.html) — multipart construction shape
- [WireMock JUnit 5+ Jupiter docs](https://wiremock.org/docs/junit-jupiter/) — `@RegisterExtension` + `dynamicPort()` pattern
- [CodeQL `java/ssrf` query help](https://codeql.github.com/codeql-query-help/java/java-ssrf/) — positive-whitelist as recommended mitigation
- [Discord webhook docs (food)](https://docs.discord.food/resources/webhook) — `payload_json` + `files[N]` multipart contract
- [Discord webhook file guide (birdie0)](https://birdie0.github.io/discord-webhooks-guide/structure/file.html) — multipart contract examples
- [Phase 91 `GoogleApiException` template (codebase)](file:///Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/dataimport/exception/GoogleApiException.java) [VERIFIED]
- [Phase 91 `GoogleApiExceptionMapper` template (codebase)](file:///Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java) [VERIFIED]
- [`FileStorageService.validateHostname` (codebase lines 126-159)](file:///Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/domain/service/FileStorageService.java) [VERIFIED]
- [`MatchdayForm` DTO template (codebase)](file:///Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/dto/MatchdayForm.java) [VERIFIED]
- [V7 migration template (codebase)](file:///Users/jegr/Documents/github/ctc-manager/src/main/resources/db/migration/V7__data_import_audit.sql) [VERIFIED]
- [`PlaywrightConfig` base class (codebase)](file:///Users/jegr/Documents/github/ctc-manager/src/test/java/org/ctc/e2e/PlaywrightConfig.java) [VERIFIED]
- [Design Spec `docs/superpowers/specs/2026-05-20-discord-integration-design.md`](file:///Users/jegr/Documents/github/ctc-manager/docs/superpowers/specs/2026-05-20-discord-integration-design.md) [VERIFIED]

### Secondary (MEDIUM confidence)

- [Rieckpil — Spring Boot integration tests WireMock JUnit 5](https://rieckpil.de/spring-boot-integration-tests-with-wiremock-and-junit-5/) — verified pattern
- [JavaThinking — WireMock random port Spring Boot](https://www.javathinking.com/blog/set-property-with-wiremock-random-port-in-spring-boot-test/)
- [BootcampToProd — Mask Sensitive Data in Logs](https://bootcamptoprod.com/how-to-mask-sensitive-data-in-logs/) — `%replace` pattern verified
- [HowToDoInJava — Logback masking sensitive data](https://howtodoinjava.com/logback/masking-sensitive-data/)
- [Couchbase blog — Spring WebClient 429 RateLimit](https://www.couchbase.com/blog/spring-webclient-429-ratelimit-errors/) — retry strategy (WebClient, but pattern applies)
- [GitHub `making/retryable-client-http-request-interceptor`](https://github.com/making/retryable-client-http-request-interceptor) — RetryableClientHttpRequestInterceptor reference impl
- [github/codeql Issue #20117](https://github.com/github/codeql/issues/20117) — SSRF whitelist FP discussion
- [Sealed Classes JLS](https://docs.oracle.com/javase/specs/jls/se16/preview/specs/sealed-classes-jls.html) — same-module/package constraints

### Tertiary (LOW confidence — verify at impl time)

- WireMock 3.9.x version — confirm against Maven Central at Plan 93-01 dependency-add task
- Discord error code 30013 — verify against Discord live UAT-03 fixture (deferred per D-01); current value is from Discord docs but historically codes are stable

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — Spring 6.1 RestClient + MultipartBodyBuilder + WireMock 3.x are all current and well-documented; zero new prod deps invariant simplifies the decision space.
- Architecture: HIGH — Phase 91 sealed-exception + Phase 5 SSRF-whitelist + Phase 91 D-06/D-07 typed-catch flash patterns are direct ports with names swapped. Mechanical implementation.
- Pitfalls: MEDIUM — body-stream gotcha is the highest-risk implementation detail; the rest are well-documented codebase conventions.
- Validation: HIGH — 14 new test files mapped to 30+ test cases across unit/integration/E2E tiers; WireMock provides full HTTP fixture coverage.
- Security: HIGH — 5 of the 6 mitigation surfaces are direct carry-forward from established CTC patterns (Phase 5 SSRF, Phase 29 mass-assignment, Phase 30 CSRF, Phase 91 typed-catch, T-91-02-IL whitelisted user-message). Only the Logback `%replace` regex is new — well-documented.

**Research date:** 2026-05-21
**Valid until:** 2026-06-20 (30 days for stable Spring framework + WireMock + Discord API surface; Discord-side error codes are stable historically)

## RESEARCH COMPLETE
