# Phase 95: Match Channel Posts — Pattern Map

**Mapped:** 2026-05-22
**Files analyzed:** 27 (12 production + 15 test)
**Analogs found:** 27 / 27 (one analog has no precedent — `DiscordPostRef` sealed hierarchy)

> **Critical override (from `95-RESEARCH.md` Landmine 1):** CONTEXT D-95-07's "SCHEMA_VERSION 1→2 + DiscordPostMixIn + DiscordPostRestorer" claim is REJECTED. `DiscordPost` lives in `org.ctc.discord.model.*` and is structurally excluded from `BackupSchema.EXPORT_ORDER` by the existing `org.ctc.domain.model.*` package filter. Three guard tests (`BackupSchemaGuardTest` × 2, `DiscordGlobalConfigGuardTest`) pin SCHEMA_VERSION = 1 and EXPORT_ORDER = 24 — they MUST remain green. The Phase-95 analog is a NEW `DiscordPostGuardTest` that mirrors `DiscordGlobalConfigGuardTest` and asserts the same invariants for `discord_post`.

> **Critical override (from `95-RESEARCH.md` Landmine 3):** The Schedule auto-edit hook lives in `MatchService.updateDiscordFields(UUID, MatchForm)` (line 64-72), NOT in `MatchService.save(MatchForm)` (no such method exists). This is the ONLY production code-path that mutates `lobbyHost / raceDirector / streamer`.

> **Critical override (from `95-RESEARCH.md` Landmine 2):** `Race` has no `raceNumber` field. Multipart bundle ordering uses the iteration index over `match.getRaces()` (already `@OrderBy("dateTime ASC NULLS LAST")` per `Match.java:48`). Filename pattern: `"settings-race-" + (i + 1) + ".png"`.

---

## File Classification

### Production Code

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/org/ctc/discord/model/DiscordPost.java` | model (JPA entity) | persistence | `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` | exact (same package, secret-discipline `@ToString.Exclude`) |
| `src/main/java/org/ctc/discord/model/DiscordPostType.java` | model (enum) | enum value | `src/main/java/org/ctc/discord/dto/ArchiveCategory.java` (record) — closest in package; no plain-enum precedent in `discord.*`. Use plain Java enum, no analog needed. | role-match (no entity-coupled enum precedent in Discord package; pattern is standard `@Enumerated(EnumType.STRING)`) |
| `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` | repository | CRUD | `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java` | role-match (same package; new interface adds `JpaSpecificationExecutor` + composite finder) |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | service (domain) | request-response + outbound HTTP + DB write | `src/main/java/org/ctc/discord/service/DiscordChannelService.java` + `DiscordGlobalConfigService.java` | exact (sibling service, same Discord-API + `@Transactional` + sealed-exception pattern) |
| `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` | DTO (sealed interface) | value-object | (no precedent — `org.ctc.discord.dto.*` has only records, no sealed hierarchies) | no analog — planner uses Java 25 sealed-record pattern per D-95-12 |
| `src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java` | DTO (form) | request binding | `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | exact (sibling form DTO, same Lombok + Jakarta-Validation pattern) |
| `src/main/java/org/ctc/discord/web/DiscordPostController.java` | controller (Spring MVC) | request-response | `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | exact (sibling controller, same `applyErrorFlash` + `@Controller` pattern) |
| **MODIFY** `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | infra (HTTP client) | outbound HTTP | self (lines 61-115 — `executeMultipart` POST + `editMessage` JSON-PATCH) | exact (compose two existing methods) |
| **MODIFY** `src/main/java/org/ctc/discord/service/DiscordChannelService.java` | service | request-response | self (lines 48-100 — `createMatchChannel`) | exact (end-of-method hook insertion) |
| **MODIFY** `src/main/java/org/ctc/admin/controller/MatchController.java` | controller | request-response | self (lines 127-181 — `createDiscordChannel` + `moveToArchive` + `applyErrorFlash`) | exact (sibling endpoints, same flash + error-category pattern) |
| **MODIFY** `src/main/java/org/ctc/domain/service/MatchService.java` | service (domain) | request-response | self (lines 63-72 — `updateDiscordFields`) | exact (extend existing method per Landmine 3) |
| `src/main/resources/db/migration/V12__discord_post.sql` | migration (Flyway) | DDL | `src/main/resources/db/migration/V8__discord_global_config.sql` + `V10__add_matches_discord_and_scheduling_fields.sql` | exact (CREATE TABLE pattern + H2/MariaDB-compat comments + immutability marker) |
| `src/main/resources/templates/admin/discord-posts.html` | template (Thymeleaf) | server-render | `src/main/resources/templates/admin/discord-config.html` | role-match (same layout shape, listing-with-filter instead of singleton-form) |
| **MODIFY** `src/main/resources/templates/admin/match-detail.html` | template (Thymeleaf) | server-render | self (lines 22-58 — existing `.discord-actions` cluster + `.discord-actions--posts` placeholder at line 56-58) | exact (fill the placeholder) |
| **MODIFY** `src/main/resources/static/admin/css/admin.css` | stylesheet | static asset | self (lines 211-227 — `.discord-actions` cluster + lines 379-394 — `.error-badge--*` variants) | exact (add new `.error-badge--data-incomplete` variant + optional sub-cluster styling) |

### Test Code

| New / Modified Test File | Role | Tag | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/test/java/org/ctc/discord/service/DiscordPostServiceWireMockIT.java` | service IT | `@Tag("integration")` | `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` | exact (sibling service IT, same WireMock + `@DynamicPropertySource` shape) |
| `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartEditIT.java` | client IT (WireMock multipart) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` | exact (mirror — `WireMock.post(...)` → `WireMock.patch(urlPathEqualTo(... + "/messages/" + msgId))`) |
| `src/test/java/org/ctc/discord/web/DiscordPostFilterControllerIT.java` | controller IT (MockMvc) | `@Tag("integration")` | `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` (MockMvc pattern) + `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` (sibling controller IT) | exact |
| `src/test/java/org/ctc/discord/model/DiscordPostGuardTest.java` | guard IT | `@Tag("integration")` | `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java` | exact (Phase-95 analog per Landmine 1) |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceTeamCardsIT.java` | service IT | `@Tag("integration")` | `DiscordChannelServiceWireMockIT.java` + `DiscordWebhookClientMultipartIT.java` | exact |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceAutoPostHookIT.java` | service IT | `@Tag("integration")` | `DiscordChannelServiceWireMockIT.java` + `DiscordChannelServicePermissionAuditFailIT.java` (hook-failure pattern) | exact |
| `src/test/java/org/ctc/admin/controller/MatchControllerTeamCardsRefreshIT.java` | controller IT (MockMvc) | `@Tag("integration")` | `DiscordChannelArchiveServiceWireMockIT.java` (MockMvc + WireMock + flash assertions) | exact |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java` | service IT | `@Tag("integration")` | `DiscordWebhookClientMultipartIT.java` (N-attachment assertions) | exact |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java` | service IT | `@Tag("integration")` | `DiscordPostServiceSettingsBundleIT.java` (sibling, same plan) | exact |
| `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` | unit (Mockito-only) | untagged | `src/test/java/org/ctc/gt7sync/Gt7SyncServiceTest.java` (Mockito unit pattern per TESTING.md) | role-match |
| `src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java` | controller IT | `@Tag("integration")` | `DiscordChannelArchiveServiceWireMockIT.java` + `MatchControllerTeamCardsRefreshIT.java` | exact |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java` | service IT | `@Tag("integration")` | `DiscordPostServiceWireMockIT.java` + `DiscordWebhookClientMultipartIT.java` | exact |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java` | service IT | `@Tag("integration")` | `DiscordPostServiceWireMockIT.java` + Embed-payload assertions (matchingJsonPath on `$.embeds[0]`) | exact |
| `src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java` | service IT | `@Tag("integration")` | `DiscordChannelArchiveServiceWireMockIT.java` (3-branch hook test pattern) | role-match |
| `src/test/java/org/ctc/admin/controller/MatchDetailMatchResultsStaleIT.java` | controller IT | `@Tag("integration")` | `DiscordChannelArchiveServiceWireMockIT.java` + `MatchControllerPostSettingsPreFlightIT.java` | role-match (timestamp-comparison branches) |
| `src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java` | repository IT (empirical verification) | `@Tag("integration")` | `DiscordGlobalConfigRepositoryIT.java` (repository IT pattern) | role-match |
| `src/test/java/org/ctc/e2e/discord/posts/DiscordPostsListE2ETest.java` | E2E (Playwright) | `@Tag("e2e")` | `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` | exact (sibling — same WireMock-driven, dual viewport pattern) |
| `src/test/java/org/ctc/e2e/discord/posts/MatchDetailTeamCardsButtonsE2ETest.java` | E2E (Playwright) | `@Tag("e2e")` | `src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java` | exact (sibling Match-Detail E2E, same `helper.createMatch` + button visibility assertions) |
| `src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java` | E2E (Playwright) | `@Tag("e2e")` | `MatchDetailTeamCardsButtonsE2ETest.java` (sibling, same plan) | exact |
| `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java` | E2E (Playwright) | `@Tag("e2e")` | `MatchDetailControllerE2ETest.java` + sibling `MatchDetailTeamCardsButtonsE2ETest.java` | exact |
| `src/test/java/org/ctc/e2e/discord/posts/MatchDetailScheduleButtonE2ETest.java` | E2E (Playwright) | `@Tag("e2e")` | sibling E2E tests above | exact |

---

## Pattern Assignments

### 1. `src/main/java/org/ctc/discord/model/DiscordPost.java` (model, persistence)

**Analog:** `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` (Phase 93, lines 1-48).

**Imports + class annotations + entity skeleton** (`DiscordGlobalConfig.java:1-21`):
```java
package org.ctc.discord.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.ctc.domain.model.BaseEntity;

@Entity
@Getter
@NoArgsConstructor
@Setter
@Table(name = "discord_global_config")
@ToString(exclude = {"announcementWebhookUrl"})
public class DiscordGlobalConfig extends BaseEntity {
```

**Apply for `DiscordPost`:**
- Same annotation set (`@Entity @Getter @NoArgsConstructor @Setter @Table(name = "discord_post") @ToString(exclude = {"webhookToken"})`).
- Same `extends BaseEntity` (provides `createdAt` + `updatedAt` from `BaseEntity.java:17-26`).
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;` — identical PK shape.
- Per-column `@Column(name = "snake_case", length = N, nullable = N)` per V12 schema.
- `post_type` as `@Enumerated(EnumType.STRING) @Column(name = "post_type", nullable = false) private DiscordPostType postType;`.
- `posted_at` as `LocalDateTime` (not auto-managed — set on insert in `DiscordPostService.postOrEdit`).
- `attachments_replaced_at` as `LocalDateTime` (nullable — set on PATCH-with-attachments-change).
- 4 FK columns (`match_id`, `matchday_id`, `race_id`, `season_id`) all `UUID`, all nullable (`ON DELETE SET NULL` per V12).

**Secret-discipline test pattern** (analog: `src/test/java/org/ctc/discord/model/DiscordGlobalConfigToStringTest.java:1-37`):
```java
@Test
void givenWebhookUrlPopulated_whenToString_thenDoesNotEchoTokenFragment() {
    DiscordGlobalConfig config = new DiscordGlobalConfig();
    config.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/100/" + SECRET);
    String rendered = config.toString();
    assertThat(rendered).doesNotContain(SECRET);
}
```
A parallel `DiscordPostToStringTest.java` (untagged Mockito unit, no Spring) is RECOMMENDED but not in the listed test set above — it can fold into the guard test or stand alone.

---

### 2. `src/main/java/org/ctc/discord/model/DiscordPostType.java` (model, enum)

**Analog:** No direct precedent in `org.ctc.discord.*` (it has only DTO records). Use standard plain Java enum.

**Skeleton:**
```java
package org.ctc.discord.model;

public enum DiscordPostType {
    TEAM_CARDS,
    SETTINGS,
    LINEUPS,
    SCHEDULE,
    PROVISIONAL_SCORES,
    MATCH_RESULTS,
    RACE_RESULTS,
    MATCHDAY_PAIRINGS,
    MATCH_PREVIEW,
    MATCHDAY_OVERVIEW,
    POWER_RANKINGS,
    STANDINGS
}
```

**Rationale (RESEARCH Assumption A3):** Declare all 12 values now even though Phase 95 uses only 5 — Phase 96/97 wire the remaining 7 without an enum bump.

---

### 3. `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` (repository, CRUD)

**Analog:** `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java` (Phase 93, lines 1-9).

**Existing pattern (verbatim):**
```java
package org.ctc.discord.repository;

import org.ctc.discord.model.DiscordGlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordGlobalConfigRepository extends JpaRepository<DiscordGlobalConfig, Long> {

    DiscordGlobalConfig findFirstByOrderByIdAsc();
}
```

**Apply for `DiscordPostRepository`:**
- Same `extends JpaRepository<DiscordPost, Long>` shape.
- ADD `extends JpaRepository<DiscordPost, Long>, JpaSpecificationExecutor<DiscordPost>` for `findAll(Specification, Pageable)` (filter listing page, POST-01).
- Composite finder: `Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchId(String channelId, DiscordPostType type, UUID matchId);` — Spring Data derives the `WHERE channel_id = ? AND post_type = ? AND match_id = ?` query.
- Optional extension finders for Phase 96/97 (`findByChannelIdAndPostTypeAndMatchdayId`, `…AndRaceId`, `…AndSeasonId`) — Plan 95-01 can declare only the `matchId` variant; others added in future phases.

---

### 4. `src/main/java/org/ctc/discord/service/DiscordPostService.java` (service, domain + outbound HTTP)

**Analog:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (Phase 94, lines 1-149) — sibling service in the same package, same `@Transactional` + sealed-exception + DB-write-after-Discord-call ordering pattern.

**Imports + class-level annotation order** (`DiscordChannelService.java:11-38`):
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordBotIdentityCache;
import org.ctc.discord.DiscordRestClient;
// …
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelService {
```

**Apply:** `@Slf4j @Service @RequiredArgsConstructor` in alphabetical order; `private final` injected fields (CLAUDE.md "Lombok Usage"); `org.ctc.discord.DiscordWebhookClient` + `DiscordPostRepository` + `TeamCardService` + `SettingsGraphicService` + `LineupGraphicService` + `MatchResultsGraphicService` + `DiscordTimestamps` + `Clock` + `MatchRepository` + `RaceLineupRepository` + `ObjectMapper` (only what each method needs).

**Core dispatch pattern** (`DiscordChannelService.createMatchChannel`, lines 48-100):
```java
@Transactional
public void createMatchChannel(Match match) throws DiscordApiException {
    DiscordGlobalConfig cfg = configService.getOrInitialize();
    assertPreconditions(match, cfg);
    // ... business logic ...
    Channel channel = restClient.createChannel(guildId, req);
    Webhook webhook = restClient.createWebhook(channel.id(), WEBHOOK_NAME);
    try {
        assertPermissionAudit(channel.id(), teamRoleIds, botUserId);
    } catch (DiscordAuthException auditEx) {
        // cleanup + re-throw
    }
    match.setDiscordChannelId(channel.id());
    match.setDiscordChannelWebhookUrl(webhook.url());
    matchRepository.save(match);
    log.info("Discord channel created for match {} → {} (channelId={})", ...);
}
```

**Apply to `postOrEdit`** (the idempotency pivot per RESEARCH Pattern 1):
- `@Transactional` boundary; Discord call FIRST, DB write SECOND.
- Lookup existing row via `discordPostRepository.findByChannelIdAndPostTypeAndMatchId(...)`.
- If present → `webhookClient.editMessage` (no attachments) OR `webhookClient.editMessageWithAttachments` (with attachments); save row.
- If absent → `webhookClient.execute` OR `webhookClient.executeMultipart`; INSERT row.
- Logging: `log.info("Posted {} for match {} (messageId={})", type, ref.matchId(), msg.id())`.

**Pre-flight predicate pattern** (`DiscordChannelService.assertPreconditions`, lines 102-113):
```java
private static void assertPreconditions(Match match, DiscordGlobalConfig cfg) {
    boolean missing = match.getHomeTeam() == null
            || match.getAwayTeam() == null
            || ... ;
    if (missing) {
        throw new BusinessRuleException(
                "Channel creation requires both team Discord roles and a current match category.");
    }
}
```

**Apply for `matchHasCompleteSettings` / `matchHasCompleteLineups`** (read-only predicates, Plan 95-03):
```java
public boolean matchHasCompleteSettings(Match match) {
    List<Race> races = match.getRaces();
    return !races.isEmpty() && races.stream().allMatch(r -> r.getSettings() != null);
}

public boolean matchHasCompleteLineups(Match match) {
    return !match.getRaces().isEmpty()
            && match.getRaces().stream()
                    .allMatch(r -> !raceLineupRepository.findByRaceId(r.getId()).isEmpty());
}
```

---

### 5. `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` (DTO, sealed interface)

**Analog:** No precedent in the codebase. Use Java 25 sealed-record syntax per D-95-12.

**Skeleton:**
```java
package org.ctc.discord.dto;

import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;

public sealed interface DiscordPostRef
        permits DiscordPostRef.MatchRef,
                DiscordPostRef.MatchdayRef,
                DiscordPostRef.RaceRef,
                DiscordPostRef.SeasonRef {

    void applyTo(DiscordPost row);

    UUID matchId();   // returns null for non-match refs

    static DiscordPostRef match(Match m)       { return new MatchRef(m.getId()); }
    static DiscordPostRef matchday(Matchday m) { return new MatchdayRef(m.getId()); }
    static DiscordPostRef race(Race r)         { return new RaceRef(r.getId()); }
    static DiscordPostRef season(Season s)     { return new SeasonRef(s.getId()); }

    record MatchRef(UUID id) implements DiscordPostRef {
        @Override public void applyTo(DiscordPost row) { row.setMatchId(id); }
        @Override public UUID matchId() { return id; }
    }
    // ... MatchdayRef / RaceRef / SeasonRef analog ...
}
```

**Fallback (per D-95-12 planner-discretion):** A plain 4-field record (`new DiscordPostRef(UUID matchId, UUID matchdayId, UUID raceId, UUID seasonId)`) with all-but-one-null factory methods is ACCEPTABLE if the sealed hierarchy explodes plan size. Phase 95 uses ONLY `MatchRef` at runtime; the other 3 permits exist for Phase 96/97 forward-extension.

---

### 6. `src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java` (DTO, form)

**Analog:** `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (Phase 93, lines 1-40).

**Existing pattern (verbatim, lines 1-40):**
```java
package org.ctc.discord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Setter
public class DiscordConfigForm {

    private static final String WEBHOOK_REGEX = "^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$";

    @Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
    private String guildId = "";
    // ... more validated fields ...
}
```

**Apply for `DiscordPostFilterForm`:**
- Same Lombok stack (`@Getter @Setter @NoArgsConstructor`).
- Fields:
  - `private UUID seasonId;` (nullable — "any season").
  - `private UUID matchId;` (nullable).
  - `private DiscordPostType postType;` (nullable — Spring auto-binds enum from request param via `String → enum` conversion).
- All fields nullable for "no filter" semantics. No Jakarta validation needed (the form is read-only filter input, not a write).
- Per RESEARCH "Claude's Discretion" recommendation: UUID dropdowns for season + match (small operator-facing list), enum-select for `postType`.

---

### 7. `src/main/java/org/ctc/discord/web/DiscordPostController.java` (controller)

**Analog:** `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (Phase 93, lines 1-173).

**Imports + class-level annotation + private final injection** (`DiscordConfigController.java:1-43`):
```java
package org.ctc.discord.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/discord-config")
@RequiredArgsConstructor
@Slf4j
public class DiscordConfigController {

    private static final String REDIRECT = "redirect:/admin/discord-config";
    private static final String VIEW = "admin/discord-config";

    private final DiscordBotIdentityCache botIdentityCache;
    // ... more final dependencies ...
}
```

**Apply for `DiscordPostController`:**
- `@RequestMapping("/admin/discord/posts")`.
- Constants: `REDIRECT = "redirect:/admin/discord/posts"`, `VIEW = "admin/discord-posts"`.
- Inject `DiscordPostService`, `DiscordPostRepository`, `SeasonRepository`, `MatchRepository`.
- `@GetMapping` for listing — binds `@ModelAttribute("filter") DiscordPostFilterForm`, builds `Specification<DiscordPost>`, calls `findAll(spec, pageable)`, populates `model.addAttribute("posts", page)` + `seasons` + `matches` dropdowns + `postTypes = Arrays.asList(DiscordPostType.values())`.
- `model.addAttribute("activeRoute", "discord-posts");` (sidebar highlight per Discord-Config precedent at line 52).
- `@PostMapping("/{id}/re-post")` — calls `discordPostService.postOrEdit(...)` with the existing row's webhook + ref; routes errors through `applyErrorFlash`.

**Error flash pattern — REUSE WITHOUT REWRITE** (`DiscordConfigController.applyErrorFlash`, lines 142-156):
```java
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    // ... log + flash ...
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}
```
**Apply:** Copy verbatim. Plan 95-03 adds a `BusinessRuleException → "data-incomplete"` branch in `MatchController.applyErrorFlash` (NOT in `DiscordPostController` — pre-flight failures fire from `MatchController` POST endpoints).

---

### 8. MODIFY `src/main/java/org/ctc/discord/DiscordWebhookClient.java` (D-95-03a)

**Analog:** self (lines 61-103 `executeMultipart` POST + lines 105-115 `editMessage` JSON-PATCH).

**Source method 1 — `executeMultipart` (lines 61-103):**
```java
public WebhookMessage executeMultipart(
        String webhookUrl, WebhookPayload payload, List<NamedAttachment> attachments)
        throws DiscordApiException {
    if (attachments.size() > MAX_ATTACHMENTS) {
        throw new IllegalArgumentException(
                "Discord allows at most " + MAX_ATTACHMENTS + " attachments per webhook (got "
                        + attachments.size() + ")");
    }
    if (attachments.isEmpty()) {
        return execute(webhookUrl, payload);
    }
    hostValidator.requireAllowed(webhookUrl);
    String payloadJson;
    try {
        payloadJson = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
    MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
    HttpHeaders payloadHeaders = new HttpHeaders();
    payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
    parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
    for (int i = 0; i < attachments.size(); i++) {
        NamedAttachment att = attachments.get(i);
        final String filename = att.filename();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
            @Override public String getFilename() { return filename; }
        };
        parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
    }
    return execute(() -> forWebhookUrl(webhookUrl)
            .post()
            .uri("")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(WebhookMessage.class));
}
```

**Source method 2 — `editMessage` (lines 105-115):**
```java
public WebhookMessage editMessage(String webhookUrl, String messageId, WebhookPayload payload)
        throws DiscordApiException {
    hostValidator.requireAllowed(webhookUrl);
    return execute(() -> forWebhookUrl(webhookUrl)
            .patch()
            .uri("/messages/{messageId}", messageId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(WebhookMessage.class));
}
```

**New method — `editMessageWithAttachments` (compose the two):**
- Copy `executeMultipart` lines 64-95 verbatim (size check, empty-fallback to `editMessage`, host-validator, payload-json, parts-loop).
- In the final `execute(() -> …)` block, replace `.post().uri("")` with `.patch().uri("/messages/{messageId}", messageId)`.
- Per RESEARCH Pitfall 5: NO `?wait=true` query parameter — Discord returns the message body on PATCH by default.
- Per RESEARCH Pitfall 9 (SECURITY): MUST call `hostValidator.requireAllowed(webhookUrl)` as the first network-touching line — copy from `executeMultipart:72` and `editMessage:107`.

**Refactor opportunity (RESEARCH Open Question 1):** `executeMultipart` and `editMessageWithAttachments` differ only by 1 builder line. Planner may extract a private `multipartCall(RestClient.RequestBodyUriSpec spec, MultiValueMap parts)` helper OR keep them parallel (~ 12 LOC duplication). Both acceptable; parallel is simpler.

---

### 9. MODIFY `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (D-95-01)

**Analog:** self (lines 48-100, `createMatchChannel`).

**Insert hook AFTER `matchRepository.save(match)` at line 97**, BEFORE the `log.info` at line 98:
```java
match.setDiscordChannelId(channel.id());
match.setDiscordChannelWebhookUrl(webhook.url());
matchRepository.save(match);

// HOOK INSERTION POINT
Optional<String> teamCardsError = Optional.empty();
try {
    discordPostService.postTeamCards(match);
} catch (DiscordApiException e) {
    teamCardsError = Optional.of(e.category().name().toLowerCase().replace('_', '-'));
    log.warn("Auto-post TEAM_CARDS failed for match {}: category={}", match.getId(), teamCardsError.get());
} catch (RuntimeException e) {
    teamCardsError = Optional.of("transient");
    log.warn("Auto-post TEAM_CARDS failed for match {}: {}", match.getId(), e.toString());
}

log.info("Discord channel created for match {} → {} (channelId={})", ...);
```

**Return-type bump:** RESEARCH Pattern 3 + Pitfall 3 — change return type from `void` to `ChannelCreationResult` (new public record inside `DiscordChannelService`) so the controller can propagate the team-cards-error category to the flash badge. Wire updated callers (only `MatchController.createDiscordChannel` at line 130).

**Pitfall 3 mitigation (transaction propagation):** RESEARCH recommends Option B (catch + log + record category + never re-throw) — matches D-95-01a literally. The hook MUST NEVER re-throw — that would roll back the channel + webhook persistence. Optionally `@Transactional(propagation = REQUIRES_NEW)` on `DiscordPostService.postTeamCards` for extra isolation.

---

### 10. MODIFY `src/main/java/org/ctc/admin/controller/MatchController.java`

**Analog:** self (lines 127-181, `createDiscordChannel` + `moveToArchive` + `applyErrorFlash`).

**Existing endpoint shape — copy as template for the 6 new endpoints** (`MatchController.createDiscordChannel`, lines 127-139):
```java
@PostMapping("/{id}/create-discord-channel")
public String createDiscordChannel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordChannelService.createMatchChannel(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Discord channel created.");
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCategory", "not-found");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Create Discord Channel");
    }
    return "redirect:/admin/matches/" + id;
}
```

**Apply for 6 new endpoints (one per row):**
- `POST /admin/matches/{id}/post-team-cards` → `discordPostService.postTeamCards(matchService.findById(id))`
- `POST /admin/matches/{id}/refresh-team-cards` → regenerate + repost (D-95-02)
- `POST /admin/matches/{id}/post-settings` → `discordPostService.postSettings(matchService.findById(id))`
- `POST /admin/matches/{id}/post-lineups` → `discordPostService.postLineups(matchService.findById(id))`
- `POST /admin/matches/{id}/post-match-results` → `discordPostService.postMatchResults(matchService.findById(id))`
- `POST /admin/matches/{id}/post-schedule` → `discordPostService.postSchedule(matchService.findById(id))`

Each method:
1. `try` block calling the service.
2. `catch (BusinessRuleException e)` → flash `errorMessage` + `errorCategory="data-incomplete"` (per D-95-03b — new category for Pre-Flight failures).
3. `catch (DiscordApiException e)` → existing `applyErrorFlash(...)`.
4. `return "redirect:/admin/matches/" + id;`.

**Extend `applyErrorFlash` (lines 167-181):** No code change to the existing method — `data-incomplete` flows through the `catch (BusinessRuleException)` branch which sets the flash directly (analog to lines 132-134 of the current `createDiscordChannel`).

**`detail` method enhancement (Plan 95-04 / POST-04 Stale-Detection)** — per RESEARCH Pattern 5: preload `DiscordPost` rows + compute `matchResultsStale` boolean as model attributes (line 84-95):
```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    MatchDetailData data = matchService.getDetailData(id);
    Match match = data.match();
    // ... existing attrs ...

    // PHASE 95 ADD: preload Discord-post rows + stale flag for Match-Detail buttons
    if (match.getDiscordChannelId() != null) {
        Optional<DiscordPost> matchResultsPost = discordPostRepository
                .findByChannelIdAndPostTypeAndMatchId(
                        match.getDiscordChannelId(), DiscordPostType.MATCH_RESULTS, match.getId());
        model.addAttribute("matchResultsPost", matchResultsPost.orElse(null));
        model.addAttribute("matchResultsStale", matchResultsPost
                .map(p -> p.getUpdatedAt().isBefore(match.getUpdatedAt()))
                .orElse(false));
        // ... similar lookups for TEAM_CARDS, SETTINGS, LINEUPS, SCHEDULE ...
        model.addAttribute("matchHasCompleteSettings", discordPostService.matchHasCompleteSettings(match));
        model.addAttribute("matchHasCompleteLineups", discordPostService.matchHasCompleteLineups(match));
    }
    return "admin/match-detail";
}
```

---

### 11. MODIFY `src/main/java/org/ctc/domain/service/MatchService.java` (D-95-04 — RESEARCH Landmine 3)

**Analog:** self (lines 63-72, `updateDiscordFields`). **Method name is `updateDiscordFields(UUID, MatchForm)`, NOT `save(MatchForm)`.**

**Existing method (verbatim, lines 63-72):**
```java
@Transactional
public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);
    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    matchRepository.save(match);
}
```

**Phase-95 extension (per RESEARCH Pattern 4):**
```java
@Transactional
public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);

    boolean scheduleFieldsChanged =
            !Objects.equals(match.getLobbyHost(),    form.getLobbyHost())
         || !Objects.equals(match.getRaceDirector(), form.getRaceDirector())
         || !Objects.equals(match.getStreamer(),     form.getStreamer());

    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    Match saved = matchRepository.save(match);

    if (scheduleFieldsChanged) {
        try {
            discordPostService.autoEditScheduleIfNeeded(saved);
        } catch (DiscordApiException e) {
            log.warn("Auto-edit SCHEDULE failed for match {}: {}", saved.getId(), e.category());
        }
    }
}
```

**Critical:** The "before" values come from `match.getLobbyHost()` / `getRaceDirector()` / `getStreamer()` BEFORE the setters fire. Order matters.

**Inject** `private final DiscordPostService discordPostService;` (Lombok `@RequiredArgsConstructor`). NEW field at top of constructor-args.

---

### 12. `src/main/resources/db/migration/V12__discord_post.sql` (migration)

**Analog:** `src/main/resources/db/migration/V8__discord_global_config.sql` (Phase 93, CREATE TABLE pattern) + `V10__add_matches_discord_and_scheduling_fields.sql` (Phase 94, H2 + MariaDB compatibility comments).

**V8 pattern (verbatim, lines 1-22):**
```sql
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm Jakarta-Validation owns the
-- snowflake/webhook regex contract instead of the DB schema.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

CREATE TABLE discord_global_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    guild_id VARCHAR(32) NOT NULL DEFAULT '',
    -- ... per-column ...
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO discord_global_config (...) VALUES (...);
```

**NOTE on the comment block:** Per CLAUDE.md "No Comment Pollution" rule (file-header comment blocks restating what the file does, banned), Phase 95's `V12__*.sql` MUST omit the V8-style "Compatible with H2…" + "DO NOT mutate" banner. The conventions live in CLAUDE.md once; do not duplicate per-file. Migration filename is self-documenting.

**Apply for V12:**
- Table `discord_post` with columns per Design-Spec § 3.3:
  - `id BIGINT PRIMARY KEY AUTO_INCREMENT`
  - `channel_id VARCHAR(32) NOT NULL`
  - `message_id VARCHAR(32) NOT NULL`
  - `webhook_id VARCHAR(32) NOT NULL`
  - `webhook_token VARCHAR(200) NOT NULL`
  - `post_type VARCHAR(40) NOT NULL` (string-encoded enum)
  - `match_id BINARY(16)` (UUID, nullable, FK to `matches` with `ON DELETE SET NULL`)
  - `matchday_id BINARY(16)` (nullable, FK)
  - `race_id BINARY(16)` (nullable, FK)
  - `season_id BINARY(16)` (nullable, FK)
  - `posted_at TIMESTAMP NOT NULL`
  - `attachments_replaced_at TIMESTAMP NULL`
  - `created_at TIMESTAMP NOT NULL`
  - `updated_at TIMESTAMP NOT NULL`
- 5 FK indexes (`idx_dp_match_id`, `idx_dp_matchday_id`, `idx_dp_race_id`, `idx_dp_season_id`, `idx_dp_channel_post_type` composite for the hot lookup).
- 4 FK constraints with `ON DELETE SET NULL`.
- NO `INSERT INTO ... VALUES (...)` seed row — Phase 95 starts empty (rows created on first post).

**H2 + MariaDB compatibility:** RESEARCH explicit — no `CHECK` constraints, no `LONGTEXT`. Use `VARCHAR(N)`. UUID columns as `BINARY(16)` per existing Phase 90+ migrations (verify against `V10__add_matches_discord_and_scheduling_fields.sql` — that file uses `VARCHAR(32)` for snowflake-strings + relies on existing UUID-PK convention from V1).

---

### 13. `src/main/resources/templates/admin/discord-posts.html` (template, new)

**Analog:** `src/main/resources/templates/admin/discord-config.html` (Phase 93, lines 1-99) — sibling listing/admin page in the same section.

**Layout-replace pattern (verbatim, lines 1-7):**
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Discord Config', ~{::section})}">
<body>
<section>
    <h1>Discord Config</h1>
```

**Apply:**
- `th:replace="~{admin/layout :: layout('Discord Posts', ~{::section})}"`.
- `<h1>Discord Posts</h1>` + filter card + table card + pagination.
- Filter form: `<form method="get" th:object="${filter}">` with three `<select>` controls for `seasonId`, `matchId`, `postType`.
- Table rows: one per `DiscordPost`, columns `channel | type | matchId | postedAt | updatedAt | actions(Re-Post button)`.
- Re-Post button: `<form method="post" th:action="@{/admin/discord/posts/{id}/re-post(id=${post.id})}"><button class="btn btn-secondary btn-sm">Re-Post</button></form>` (D-95-Claude's-Discretion: single "Re-Post" button, NOT separate Re-Edit + Re-Post — both funnel to `postOrEdit`).

**Pagination:** Use Spring Data `Page<DiscordPost>` model attribute + standard `th:each` over `page.content` + `page.number` / `page.totalPages` controls.

**No inline styles** — use `.btn`, `.btn-sm`, `.btn-secondary` from `admin.css` (CLAUDE.md "No Inline Styles on Buttons").

---

### 14. MODIFY `src/main/resources/templates/admin/match-detail.html`

**Analog:** self (lines 22-58 — existing Discord-Actions card with `.discord-actions--posts` placeholder).

**Existing placeholder (verbatim, lines 22-58):**
```html
<!-- Discord Actions -->
<div class="card">
    <h2>Discord Actions</h2>
    <div class="discord-actions">
        <form th:if="${match.discordChannelId == null}"
              th:action="@{/admin/matches/{id}/create-discord-channel(id=${match.id})}"
              method="post" class="form-inline">
            <button type="submit" class="btn btn-primary btn-sm" data-testid="create-discord-channel"
                    th:disabled="${match.homeTeam.effectiveDiscordRoleId == null
                            or match.awayTeam == null
                            or match.awayTeam.effectiveDiscordRoleId == null}">
                Create Discord Channel
            </button>
        </form>
        <!-- channel-id badge + archived badge + Move to Archive button -->
    </div>

    <!-- Phase 95 placeholder: POST buttons land here -->
    <div class="discord-actions discord-actions--posts">
        <!-- TODO Phase 95: POST buttons land here (POST-01..POST-05) -->
    </div>
</div>
```

**Apply — fill the placeholder with the button matrix** (per RESEARCH Code Example "Match-Detail button visibility" + D-95-01 / D-95-02 / D-95-03b):
```html
<div class="discord-actions discord-actions--posts" th:if="${match.discordChannelId != null}">

  <!-- TEAM_CARDS — D-95-01 hybrid -->
  <form th:if="${teamCardsPost == null}"
        th:action="@{/admin/matches/{id}/post-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-primary btn-sm" data-testid="post-team-cards">Post Team Cards</button>
  </form>
  <form th:if="${teamCardsPost != null}"
        th:action="@{/admin/matches/{id}/post-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-secondary btn-sm" data-testid="repost-team-cards">Re-Post Team Cards</button>
  </form>
  <form th:if="${teamCardsPost != null}"
        th:action="@{/admin/matches/{id}/refresh-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-secondary btn-sm" data-testid="refresh-team-cards">↻ Refresh Cards</button>
  </form>

  <!-- SETTINGS — D-95-03b pre-flight gated -->
  <form th:if="${settingsPost == null and matchHasCompleteSettings}"
        th:action="@{/admin/matches/{id}/post-settings(id=${match.id})}" method="post">
    <button class="btn btn-primary btn-sm" data-testid="post-settings">Post Settings</button>
  </form>
  <span th:if="${settingsPost == null and not matchHasCompleteSettings}"
        class="btn btn-secondary btn-sm disabled"
        title="Configure settings for all races first">Post Settings</span>
  <!-- Re-Post Settings if exists ... LINEUPS analog ... MATCH_RESULTS with stale-label ... SCHEDULE ... -->
</div>
```

**Critical:** NO inline styles (`style="…"`). All visibility logic via `th:if` on pre-computed model attributes. All button styling via CSS classes from `admin.css`.

---

### 15. MODIFY `src/main/resources/static/admin/css/admin.css`

**Analog:** self (lines 211-227 — `.discord-actions` cluster + lines 379-394 — `.error-badge--*` variants).

**Existing `.discord-actions` cluster (verbatim, lines 211-227):**
```css
/* Discord-page action cluster — responsive-wrap variant shared by discord-config + match-detail. */
.discord-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
}
@media (max-width: 640px) {
    .discord-actions {
        flex-direction: column;
        align-items: stretch;
    }
    .discord-actions > * { width: 100%; }
    .discord-actions .btn { width: 100%; }
}
```

**Existing `.error-badge--*` variants (verbatim, lines 379-394):**
```css
.error-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: var(--radius-sm);
    font-size: 11px;
    font-weight: 600;
    /* ... */
}
.error-badge--transient     { background: #3b2e0e; color: #ffb74d; border: 1px solid #b26a00; }
.error-badge--auth          { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.error-badge--not-found     { background: #2a2a3a; color: #90caf9; border: 1px solid #1976d2; }
.error-badge--permission    { background: var(--danger-bg); color: #ef5350; border: 1px solid #d32f2f; }
.error-badge--category-full { background: #3b2a0a; color: #ffcc80; border: 1px solid #e69138; }
```

**Apply (Plan 95-03):** Add ONE new variant matching the existing palette:
```css
.error-badge--data-incomplete { background: #2a2a3a; color: #ffb74d; border: 1px solid #b26a00; }
```
(Color choice: orange-amber to distinguish "data not ready" from the red `auth`/`permission` errors.)

**Optional sub-cluster styling** (Plan 95-02 — if needed for visual grouping):
```css
.discord-actions--posts { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--border); }
```
(Verify with playwright-cli per [[feedback-playwright-cli]] before committing.)

---

## Test Pattern Assignments

### Test 1. `DiscordPostServiceWireMockIT` (Plan 95-01)

**Analog:** `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` (Phase 94, lines 1-120 — sibling service IT).

**Existing class-level setup (verbatim, lines 41-89):**
```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordChannelServiceWireMockIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.bot-token", () -> "test-bot-token");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
        registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
    }

    @Autowired DiscordChannelService service;
    @Autowired DiscordGlobalConfigRepository configRepo;
    @Autowired MatchRepository matchRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TestHelper helper;

    @BeforeEach
    void resetWireMock() { wm.resetAll(); /* + stubBotIdentity() */ }
```

**Apply:** Copy verbatim — `@SpringBootTest @ActiveProfiles("dev") @Tag("integration") @Transactional` + `WireMockExtension` + `@DynamicPropertySource` block + `resetWireMock()` + `@Autowired` for `DiscordPostService`, `DiscordPostRepository`, `MatchRepository`, `TestHelper`.

**Test methods to write — per RESEARCH Phase Req → Test Map (lines 789-795):**
- `givenNoExistingPost_whenPostOrEdit_thenMultipartPostAndInsertRow()` — happy POST branch.
- `givenExistingPost_whenPostOrEdit_thenMultipartPatchAndUpdateRow()` — happy PATCH branch.
- `givenEmptyAttachments_whenPostOrEditWithEmbed_thenJsonPostOrPatch()` — SCHEDULE-like Embed-only branch.
- `givenDiscordReturns5xx_whenPostOrEdit_thenThrowsDiscordTransientException()` — failure-mode propagation.

---

### Test 2. `DiscordWebhookClientMultipartEditIT` (Plan 95-01, D-95-03a)

**Analog:** `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` (Phase 93, lines 1-114).

**Existing test (verbatim, lines 63-86):**
```java
@Test
void givenAttachments_whenExecuteMultipart_thenAssertsPerPartHeadersAndPayload() throws Exception {
    String webhookPath = "/api/v10/webhooks/100/abc";
    wm.stubFor(post(urlPathEqualTo(webhookPath))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withMultipartRequestBody(aMultipart("payload_json")
                    .withHeader("Content-Type", containing("application/json"))
                    .withBody(matchingJsonPath("$.content", containing("Game On!"))))
            .withMultipartRequestBody(aMultipart("files[0]")
                    .withHeader("Content-Type", equalTo("image/png")))
            .withMultipartRequestBody(aMultipart("files[1]")
                    .withHeader("Content-Type", equalTo("image/png")))
            .willReturn(okJson("{\"id\":\"msg-2\",\"channel_id\":\"chan-1\"}")));

    var payload = new WebhookPayload("Game On!", List.of());
    var attachments = List.of(
            new NamedAttachment("a.png", PNG_BYTES),
            new NamedAttachment("b.png", PNG_BYTES));

    WebhookMessage out = client.executeMultipart(wm.baseUrl() + webhookPath, payload, attachments);

    assertThat(out.id()).isEqualTo("msg-2");
    wm.verify(postRequestedFor(urlPathEqualTo(webhookPath)));
}
```

**Apply (mirror):** Same class-level setup; replace `WireMock.post(...)` with `WireMock.patch(urlPathEqualTo(webhookPath + "/messages/msg-1"))`; assert `wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-1")))`.

**Test methods:**
- `givenAttachments_whenEditMessageWithAttachments_thenMultipartPatchWithPerPartHeaders()` (mirror).
- `givenEmptyAttachments_whenEditMessageWithAttachments_thenDelegatesToEditMessageJsonPatch()` (mirror line 88-99 — empty → JSON-PATCH).
- `givenTooManyAttachments_whenEditMessageWithAttachments_thenThrowsIllegalArgumentException()` (mirror line 101-113).
- `givenDisallowedHost_whenEditMessageWithAttachments_thenThrowsHostValidatorException()` (NEW — RESEARCH Pitfall 9 SSRF guard verification).

---

### Test 3. `DiscordPostFilterControllerIT` (Plan 95-01)

**Analog:** `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` (Phase 94 — `@AutoConfigureMockMvc` + `MockMvc.perform(get(...))` + flash assertions).

**Setup pattern (verbatim, lines 41-69):**
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordChannelArchiveServiceWireMockIT {
    // ... WireMockExtension + @DynamicPropertySource ...
    @Autowired MockMvc mockMvc;
    @Autowired MatchRepository matchRepository;
    @Autowired TestHelper helper;
```

**Filter binding assertion pattern (`mockMvc.perform(get(...))` + content matchers):**
```java
mockMvc.perform(get("/admin/discord/posts").param("postType", "TEAM_CARDS"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Re-Post")));
```

**Test methods:**
- `givenEmptyTable_whenGetList_thenRendersEmptyState()`
- `givenPopulatedTable_whenGetListWithFilter_thenRendersFilteredRows()`
- `givenExistingRow_whenPostRePost_thenInvokesPostOrEditAndRedirects()`

---

### Test 4. `DiscordPostGuardTest` (Plan 95-01 — RESEARCH Landmine 1)

**Analog:** `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java` (Phase 93, lines 1-34, verbatim):
```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordGlobalConfigGuardTest {

    @Autowired
    private BackupSchema backupSchema;

    @Test
    void givenPhase93Migration_whenInspectExportOrder_thenCountStaysAt24() {
        assertThat(backupSchema.getExportOrder())
                .as("Phase 93 must not bump BackupSchema.EXPORT_ORDER; DiscordGlobalConfig is "
                        + "structurally excluded by the org.ctc.domain.model.* package filter.")
                .hasSize(24);
    }

    @Test
    void givenPhase93Migration_whenInspectExportOrder_thenDoesNotContainDiscordGlobalConfig() {
        assertThat(backupSchema.getExportOrder())
                .as("DiscordGlobalConfig must not appear in the backup export order.")
                .noneMatch(ref -> "discord_global_config".equalsIgnoreCase(ref.tableName()));
    }
}
```

**Apply (mirror for DiscordPost):**
- Class name: `DiscordPostGuardTest`.
- Location: `src/test/java/org/ctc/discord/model/DiscordPostGuardTest.java` (RESEARCH Landmine 1 places it under `model/`; CONTEXT files-to-read also suggests `org.ctc.discord.model`).
- Same `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")`.
- Test 1: assert `backupSchema.getExportOrder()` has size 24 (NOT 25) — pins the invariant against future regressions.
- Test 2: assert `getExportOrder()` does NOT contain `tableName == "discord_post"`.
- The assertion-as-message string MUST cite Phase 95 + the package-filter rationale (mirror line 23-24).

---

### Test 5-7. Plan 95-02 ITs

**`DiscordPostServiceTeamCardsIT`** — analog `DiscordWebhookClientMultipartIT` (2-attachment assertions). Tests: happy multipart-POST with both card PNGs; multipart-PATCH on re-post; auto-gen `teamCardService.generateCard()` invocation when `cardExists() == false`.

**`DiscordChannelServiceAutoPostHookIT`** — analog `DiscordChannelServicePermissionAuditFailIT` (hook-failure-keeps-state pattern). Tests: WireMock-stubbed Discord 5xx for the team-cards POST → assert `matchRepository.findById(...).discordChannelId` still persisted (RESEARCH Pitfall 3 verification); auto-post success path → assert `DiscordPost` row inserted.

**`MatchControllerTeamCardsRefreshIT`** — analog `DiscordChannelArchiveServiceWireMockIT` (MockMvc + WireMock + flash). Tests: `POST /admin/matches/{id}/refresh-team-cards` → assert WireMock saw POST or PATCH with new attachments; success flash; failure-flash with error category.

---

### Test 8-12. Plan 95-03 ITs

**`DiscordPostServiceSettingsBundleIT`** — analog `DiscordWebhookClientMultipartIT.givenAttachments_whenExecuteMultipart_thenAssertsPerPartHeadersAndPayload` (lines 63-86). 4-attachment assertion for `match.getRaces().size() == 4`. Filename pattern `"settings-race-1.png" / "settings-race-2.png" / "settings-race-3.png" / "settings-race-4.png"` (RESEARCH Landmine 2 — 1-indexed iteration over `match.getRaces()`).

**`DiscordPostServiceLineupsBundleIT`** — sibling mirror.

**`DiscordPostServicePreFlightTest`** — Mockito-only unit (untagged per CLAUDE.md "Tag Tests by Category"). Analog: `src/test/java/org/ctc/gt7sync/Gt7SyncServiceTest.java` (TESTING.md `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` pattern). Tests: 3 branches per predicate × 2 predicates = 6 branches.

**`MatchControllerPostSettingsPreFlightIT`** — analog `DiscordChannelArchiveServiceWireMockIT`. Tests: incomplete-settings → `BusinessRuleException` → `errorMessage` flash + `errorCategory="data-incomplete"`.

---

### Test 13-17. Plan 95-04 ITs

**`DiscordPostServiceMatchResultsIT`** — analog `DiscordWebhookClientMultipartIT` (1-attachment). Tests: happy POST + stale-detection comparison.

**`DiscordPostServiceScheduleIT`** — analog `DiscordPostServiceWireMockIT` + JSON-Path assertion on embed shape (`matchingJsonPath("$.embeds[0].fields[0].name", "Date")`, `matchingJsonPath("$.embeds[0].fields[1].value", "_TBD_")` for null `lobbyHost`).

**`MatchServiceScheduleEditHookIT`** — RESEARCH Pattern 4 + Pitfall 3-branch test pattern. Tests: (a) schedule fields changed + SCHEDULE row exists → PATCH; (b) schedule fields unchanged → NO PATCH; (c) schedule fields changed + NO SCHEDULE row → NO PATCH.

**`MatchDetailMatchResultsStaleIT`** — controller IT. Tests: row.updatedAt < match.updatedAt → button label "Update Match Results"; row.updatedAt >= match.updatedAt → "Re-Post Match Results".

**`MatchUpdatedAtNoopSaveIT`** — RESEARCH Pitfall 4 + Assumption A1 empirical verification. Tests: `findById` → `matchRepository.save(match)` without any setter calls → assert `match.updatedAt` unchanged (i.e., Spring `@LastModifiedDate` does NOT advance on no-op).

---

### Test 18-22. Playwright E2E Tests (`org.ctc.e2e.discord.posts.*`)

**Analog (base class + WireMock + dual viewport):** `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` (Phase 93, lines 1-80).

**Setup pattern (verbatim, lines 25-61):**
```java
@Tag("e2e")
class DiscordConfigPageE2ETest extends PlaywrightConfig {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @Autowired DiscordGlobalConfigRepository repo;

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        // ...
    }

    @BeforeEach
    void setUp() {
        setupPage();
        wm.resetAll();
        resetConfigToEmptyDefaults();
    }
```

**Match-Detail E2E test seeding pattern** — analog `src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java` (Phase 94, lines 50-70):
```java
private Match seedMatch(String suffix, String homeRoleId, String awayRoleId) {
    Season season = helper.createSeason("E2E Match " + suffix);
    Matchday md = helper.createMatchdayInRegularPhase(season, "MD-E2E-" + suffix, 0);
    Team home = helper.createTeam("Home " + suffix, "he" + suffix);
    Team away = helper.createTeam("Away " + suffix, "ae" + suffix);
    home.setDiscordRoleId(homeRoleId);
    away.setDiscordRoleId(awayRoleId);
    teamRepository.save(home);
    teamRepository.save(away);
    return helper.createMatch(md, home, away);
}
```

**Mobile-viewport sweep pattern (verbatim from `DiscordConfigPageE2ETest:74-87`):**
```java
try (BrowserContext mobileContext = browser.newContext(
        new com.microsoft.playwright.Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(375, 667)))) {
    Page mobile = mobileContext.newPage();
    mobile.navigate(url("/admin/matches/" + match.getId()));
    assertThat(mobile.locator("[data-testid='post-team-cards']")).isVisible();
}
```

**Apply per plan:**
- **Plan 95-01:** `DiscordPostsListE2ETest` — desktop + mobile viewport on `/admin/discord/posts`.
- **Plan 95-02:** `MatchDetailTeamCardsButtonsE2ETest` — pre-seed DB with `DiscordPost(TEAM_CARDS)` row → assert `[data-testid='repost-team-cards']` visible + `[data-testid='refresh-team-cards']` visible; no row → assert `[data-testid='post-team-cards']` visible.
- **Plan 95-03:** `MatchDetailSettingsLineupsButtonsE2ETest` — pre-flight branches (complete vs. incomplete).
- **Plan 95-04:** `MatchDetailMatchResultsButtonE2ETest` (stale-label branches) + `MatchDetailScheduleButtonE2ETest` (Schedule visibility when `firstRaceTime != null`).

**Critical:** All E2E tests live in package `org.ctc.e2e.discord.posts.*` per CONTEXT D-95-09 + TESTING.md `org.ctc.e2e.*` convention.

---

## Shared Patterns

### Shared 1. Annotation Order on Spring Components

**Source:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:35-38` + CLAUDE.md "Annotation Order".

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelService { ... }
```

**Apply to:** all new Spring components (`DiscordPostService`, `DiscordPostController`). Alphabetical order: `@Slf4j` first, then `@Service`/`@Controller`, then `@RequiredArgsConstructor`. NEVER mix order.

---

### Shared 2. Secret-Discipline `@ToString(exclude = ...)` on Entities

**Source:** `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java:20` + `src/main/java/org/ctc/domain/model/Match.java:19` (excludes `discordChannelWebhookUrl`).

```java
@ToString(exclude = {"announcementWebhookUrl"})
public class DiscordGlobalConfig extends BaseEntity { ... }
```

**Apply to:** `DiscordPost` — `@ToString(exclude = {"webhookToken"})`. Verify with a `DiscordPostToStringTest` (Mockito unit, untagged) mirroring `DiscordGlobalConfigToStringTest.java:1-37`.

---

### Shared 3. Flash-Attribute Error Category Mapping

**Source:** `src/main/java/org/ctc/discord/web/DiscordConfigController.java:142-156` + `src/main/java/org/ctc/admin/controller/MatchController.java:167-181`.

```java
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    log.warn("{} failed: category={}, exception={}, cause={}", ...);
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}
```

**Apply to:** all 6 new POST endpoints in `MatchController`. Existing `applyErrorFlash` is REUSED unchanged. `BusinessRuleException` (Pre-Flight failures) routes through a separate `catch (BusinessRuleException e)` block that sets `errorCategory="data-incomplete"` directly (NOT through `applyErrorFlash` — different exception type).

---

### Shared 4. `hostValidator.requireAllowed(webhookUrl)` SSRF Guard

**Source:** `DiscordWebhookClient.executeMultipart:72` + `editMessage:107` + `execute:46`.

**Apply to:** `editMessageWithAttachments` MUST call `hostValidator.requireAllowed(webhookUrl)` as the FIRST network-touching line (after the size-check guards). RESEARCH Pitfall 9 — SECURITY-blocking; verified by `DiscordClientHostWhitelistTest`.

---

### Shared 5. Parameterized Logging

**Source:** CLAUDE.md "Logging" + ubiquitous codebase pattern.

```java
log.info("Discord channel created for match {} → {} (channelId={})",
        match.getId(), channel.name(), channel.id());
```

**Apply to:** all new `log.info`, `log.warn`, `log.debug` statements in `DiscordPostService`, `MatchController`, `MatchService`. NEVER string concatenation.

---

### Shared 6. No Comment Pollution

**Source:** CLAUDE.md § "No Comment Pollution".

**Apply to ALL new files:**
- NO `// Phase 95 …` / `// Plan 95-NN …` / `// POST-0N …` / `// UAT-0N …` references in source files (Java, SQL, Thymeleaf, YAML, tests).
- NO file-header comment blocks restating what the file does or repeating conventions (e.g. `Compatible with H2 + MariaDB`, `DO NOT mutate this file after release`).
- NO `// Added for X` / `// used by Y` / `// called from Z` cross-references.
- NO multi-line Javadoc on obvious getters/setters or one-line methods.
- ALLOWED (rare): single-line WHY comments for non-obvious constraints (RESEARCH Pitfalls 1-9 highlight a few such cases).
- This applies equally to subagents — every prompt that writes code must reference this rule.

**Specific watch-outs for Plan 95-01:**
- V12 migration MUST omit the V8/V10-style banner comments — those are pollution by today's standard (the V10/V11 banners predate the explicit anti-pollution rule).
- New entity, repository, service, controller, DTO classes MUST NOT have file-level Javadoc summarizing their purpose.

---

### Shared 7. CSS Classes Over Inline Styles

**Source:** CLAUDE.md "No Inline Styles on Buttons" + existing `admin.css` patterns.

**Apply to:** all new Thymeleaf templates and template edits. Buttons use `.btn`, `.btn-sm`, `.btn-primary`, `.btn-secondary`, `.btn-danger` (`admin.css:229-394`). Pre-flight-disabled "buttons" use `<span class="btn btn-secondary btn-sm disabled">` (`pseudo-button` pattern, RESEARCH Code Example).

---

### Shared 8. `@Tag` Test Categorization

**Source:** CLAUDE.md + TESTING.md § "Test Categorization (`@Tag`)" + CONTEXT D-95-09.

**Apply:**
- `@Tag("integration")` on every `*IT.java` (`DiscordPostServiceWireMockIT`, `DiscordWebhookClientMultipartEditIT`, `DiscordPostFilterControllerIT`, `DiscordPostGuardTest`, `DiscordPostServiceTeamCardsIT`, `DiscordChannelServiceAutoPostHookIT`, `MatchControllerTeamCardsRefreshIT`, `DiscordPostServiceSettingsBundleIT`, `DiscordPostServiceLineupsBundleIT`, `MatchControllerPostSettingsPreFlightIT`, `DiscordPostServiceMatchResultsIT`, `DiscordPostServiceScheduleIT`, `MatchServiceScheduleEditHookIT`, `MatchDetailMatchResultsStaleIT`, `MatchUpdatedAtNoopSaveIT`).
- UNTAGGED for Mockito-only units (`DiscordPostServicePreFlightTest`).
- `@Tag("e2e")` + package `org.ctc.e2e.discord.posts.*` on all Playwright tests.

---

## No-Analog Files

| File | Why no analog | Source for pattern |
|------|---------------|--------------------|
| `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` (sealed interface) | No sealed-record-hierarchy exists in this codebase yet. | Java 25 language spec + CONTEXT D-95-12 + RESEARCH Pattern 1. Planner-discretion fallback: plain 4-field record. |

All other planned files have a concrete, named codebase analog above.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/discord/` (model, repository, service, web, dto, exception, util)
- `src/main/java/org/ctc/admin/controller/`
- `src/main/java/org/ctc/admin/service/`
- `src/main/java/org/ctc/domain/service/`
- `src/main/java/org/ctc/domain/model/` (BaseEntity, Match)
- `src/main/resources/db/migration/`
- `src/main/resources/templates/admin/`
- `src/main/resources/static/admin/css/`
- `src/test/java/org/ctc/discord/`
- `src/test/java/org/ctc/e2e/discord/`

**Files scanned (direct Read calls):** 25 (incl. CONTEXT, RESEARCH, ARCHITECTURE, CONVENTIONS, TESTING, 7 production analogs, 4 test analogs, 1 entity, 4 DTOs, 1 controller, 1 service, 1 template, 1 CSS region, 4 migration files).

**Pattern extraction date:** 2026-05-22.

**Critical RESEARCH overrides applied:**
1. Landmine 1 — `BackupSchema` SCHEMA_VERSION + EXPORT_ORDER do NOT change. NO `DiscordPostMixIn` or `DiscordPostRestorer`. ADD `DiscordPostGuardTest` mirror.
2. Landmine 2 — `match.getRaces()` iteration index (1-indexed) for multipart bundle filenames; no `Race.raceNumber` field exists.
3. Landmine 3 — Schedule auto-edit hook lives in `MatchService.updateDiscordFields(UUID, MatchForm)`, NOT in `MatchService.save(MatchForm)`.
