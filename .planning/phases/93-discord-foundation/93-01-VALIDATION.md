---
phase: 93
plan: 01
slug: discord-foundation
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 93-01 — Validation Slice

> Per-plan slice of `93-VALIDATION.md` per CONTEXT D-08.
> Substance: 15 rows 93-01-01..15 covering INFRA-01 Discord client infrastructure.

---

## Sampling Rate

- **Per-task compile gate** (after every implementation task): `./mvnw -q clean test-compile` (~10 s)
- **Per-test-class gate** (after each TDD pair): `./mvnw test -Dtest=<ClassName>` for unit, `./mvnw failsafe:integration-test failsafe:verify -Dit.test=<ClassName>` for ITs (~10–40 s)
- **Per-plan full gate** (Task 15): `./mvnw clean verify -Pe2e` (~07:59 min, 1480 surefire + 277 failsafe + 38 E2E + JaCoCo + SpotBugs)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-01-01 | 01 | 1 | INFRA-01 | n/a | WireMock 3.9.2 on test classpath; never reaches production scope | dep-tree check | `./mvnw dependency:tree \| grep wiremock` returns `org.wiremock:wiremock-standalone:jar:3.9.2:test` | ✅ | ✅ green |
| 93-01-02 | 01 | 1 | INFRA-01 | n/a | `CachedEntry<T>.isValid(Clock)` and `BucketState` records compile and are publicly consumable from sibling discord classes | compile gate | `./mvnw -q clean test-compile` | ✅ | ✅ green |
| 93-01-03 | 01 | 1 | INFRA-01 | T-93-01 | RED `DiscordApiExceptionMapperTest` drives the sealed hierarchy creation (compile error proves the contract is missing) | RED test | `./mvnw test -Dtest=DiscordApiExceptionMapperTest` fails compile before Task 4 | ✅ | ✅ green |
| 93-01-04 | 01 | 1 | INFRA-01 | T-93-01 | 4 sealed permits + `DiscordApiExceptionMapper.from(RestClientResponseException)`/`from(IOException)`; 4 hardcoded user-visible message constants only, never echoes `e.getMessage()` (T-91-02-IL invariant preserved on Discord layer) | unit (9 methods) | `./mvnw test -Dtest=DiscordApiExceptionMapperTest` → 9/9 pass | ✅ | ✅ green |
| 93-01-05 | 01 | 1 | INFRA-01 | n/a | RED `DiscordEmojiCacheTest` drives DiscordEmojiCache + @Bean Clock | RED test | `./mvnw test -Dtest=DiscordEmojiCacheTest` fails compile before Task 6 | ✅ | ✅ green |
| 93-01-06 | 01 | 1 | INFRA-01 | n/a | DiscordEmojiCache: Clock-injected, 60-min TTL via `Duration.ofMinutes(60)`, ConcurrentHashMap, refresh(Map) replaces atomically. Zero coupling to DiscordRestClient. | unit (5 methods) | `./mvnw test -Dtest=DiscordEmojiCacheTest` → 5/5 pass + `grep -c DiscordRestClient src/main/java/org/ctc/discord/DiscordEmojiCache.java` returns 0 | ✅ | ✅ green |
| 93-01-07 | 01 | 1 | INFRA-01 | n/a | RED `DiscordTimestampsTest` drives DiscordTimestamps | RED test | `./mvnw test -Dtest=DiscordTimestampsTest` fails compile before Task 8 | ✅ | ✅ green |
| 93-01-08 | 01 | 1 | INFRA-01 | n/a | DiscordTimestamps `@Component` at `org.ctc.discord` (root, not util). 6 styles F/f/D/d/t/R. Berlin DST handling verified — UTC and Europe/Berlin produce epochs that differ by 7200s in May. | unit (7 methods) | `./mvnw test -Dtest=DiscordTimestampsTest` → 7/7 pass | ✅ | ✅ green |
| 93-01-09 | 01 | 1 | INFRA-01 | T-93-04 | RED `DiscordRateLimitInterceptorIT` drives interceptor + RestClient bean creation | RED IT | `./mvnw failsafe:integration-test -Dit.test=DiscordRateLimitInterceptorIT` fails compile before Task 10 | ✅ | ✅ green |
| 93-01-10 | 01 | 1 | INFRA-01 | T-93-04 | DiscordRateLimitInterceptor: 429-retry × 3 with Retry-After + jitter; 5xx backoff schedule; exhaustion → DiscordTransientException; per-bucket ConcurrentHashMap; **never calls `.getBody()`** (body-stream gotcha guard); 401 passes through. DiscordConfig provides `@Bean(name="discordBotRestClient") RestClient`. | integration (6 methods) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordRateLimitInterceptorIT` → 6/6 pass + `grep -c "\.getBody()" src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` returns 0 | ✅ | ✅ green |
| 93-01-11 | 01 | 1 | INFRA-01 | T-93-01 | RED `DiscordRestClientIT` drives 8 missing typed methods + 6 DTO records | RED IT | `./mvnw failsafe:integration-test -Dit.test=DiscordRestClientIT` fails compile before Task 12 | ✅ | ✅ green |
| 93-01-12 | 01 | 1 | INFRA-01 | T-93-01 | 9 typed `DiscordRestClient` methods + 6 DTO records under `org.ctc.discord.dto`. All routed through `DiscordApiExceptionMapper.from(...)`. No `e.getMessage()` echo. | integration (13 methods) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordRestClientIT` → 13/13 pass + `grep -c "e.getMessage()" src/main/java/org/ctc/discord/DiscordRestClient.java` returns 0 | ✅ | ✅ green |
| 93-01-13 | 01 | 1 | INFRA-01 | T-93-02 | RED `DiscordWebhookClientIT` + `DiscordWebhookClientMultipartIT` drive webhook client + multipart contract | RED IT | `./mvnw failsafe:integration-test -Dit.test='DiscordWebhookClientIT,DiscordWebhookClientMultipartIT'` fails compile before Task 14 | ✅ | ✅ green |
| 93-01-14 | 01 | 1 | INFRA-01 | T-93-02 | `DiscordWebhookClient`: execute/executeMultipart/editMessage. Multipart assembled as LinkedMultiValueMap<String,HttpEntity<?>> (no MultipartBodyBuilder, which would require a new prod dep). >10 attachments → IllegalArgumentException; empty list → delegates to JSON execute(). 5 webhook DTOs added under dto/. | integration (8 methods) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test='DiscordWebhookClientIT,DiscordWebhookClientMultipartIT'` → 8/8 pass | ✅ | ✅ green |
| 93-01-15 | 01 | 1 | INFRA-01 | all | Full pipeline green: 1480 surefire + 277 failsafe + 38 E2E + JaCoCo line coverage **89.5917 %** (≥ 88.88 % target, ≥ 82 % pom gate) + SpotBugs BugInstance count 0 (3 Medium findings suppressed in `config/spotbugs-exclude.xml` with rationale). Branch identity `gsd/v1.13-discord-integration`. | full pipeline | `./mvnw clean verify -Pe2e` exits 0; `git branch --show-current` returns `gsd/v1.13-discord-integration` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `pom.xml` (Task 1) — WireMock 3.9.2 test scope
- [x] `src/main/java/org/ctc/discord/util/{CachedEntry,BucketState}.java` (Task 2)
- [x] `src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java` (Task 3, RED → Task 4 GREEN)
- [x] `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` (Task 5, RED → Task 6 GREEN)
- [x] `src/test/java/org/ctc/discord/DiscordTimestampsTest.java` (Task 7, RED → Task 8 GREEN)
- [x] `src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java` (Task 9, RED → Task 10 GREEN)
- [x] `src/test/java/org/ctc/discord/DiscordRestClientIT.java` (Task 11, RED → Task 12 GREEN)
- [x] `src/test/java/org/ctc/discord/DiscordWebhookClient{,Multipart}IT.java` (Task 13, RED → Task 14 GREEN)
- [x] Task 15 closes with full `./mvnw clean verify -Pe2e` GREEN.

---

## Sign-Off

| Field | Value |
|-------|-------|
| Closed by | Plan 93-01 execution (15 tasks, TDD-paired) |
| Closed at | 2026-05-21 |
| Closing commit | TBD (final commit SHA recorded in `93-VALIDATION.md` per CONTEXT D-08) |
| Coverage at close | 89.5917 % line coverage (target 88.88 %) |
| SpotBugs at close | 0 BugInstance (3 Medium suppressed with rationale) |
| Threat coverage | T-93-01 (Bot-Token leak, via no-info-leak Mapper constants + sealed permits), T-93-04 (Rate-limit burst, via DiscordRateLimitInterceptor). T-93-02 (Webhook-URL leak) and T-93-03 (Channel-permission bypass) are forward-referenced — owned by Plans 93-02 + 94 respectively. |
