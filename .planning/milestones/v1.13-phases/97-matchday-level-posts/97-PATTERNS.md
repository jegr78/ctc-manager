# Phase 97: Matchday-Level Posts — Pattern Map

**Mapped:** 2026-05-24
**Files analyzed:** 23 new/modified files across 3 plans
**Analogs found:** 23 / 23

---

## File Classification

| New/Modified File | Plan | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/discord/event/MatchPreviewFieldsChangedEvent.java` | 97-01 | event-record | event-driven | `MatchScheduleFieldsChangedEvent.java` | exact |
| `src/main/java/org/ctc/admin/dto/MatchPreviewPreFlightResult.java` | 97-01 | dto-record | request-response | `MatchPreviewPreFlightResult` shape in RESEARCH.md; structural analog: `WebhookCredentials` inner record in `DiscordPostService.java` | role-match |
| `src/main/java/org/ctc/domain/service/MatchService.java` (extend `updateDiscordFields`) | 97-01 | service | event-driven | `MatchService.updateDiscordFields` (existing diff+publish block, lines 68–87) | exact |
| `src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java` (add `onMatchPreviewFieldsChanged`) | 97-01 | event-listener | event-driven | `onScheduleFieldsChanged` (lines 49–67) | exact |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` (add `postMatchPreview` + `autoEditMatchPreviewIfNeeded`) | 97-01 | service | request-response | `postSchedule` + `autoEditScheduleIfNeeded` (lines 158–192) | exact |
| `src/main/java/org/ctc/admin/controller/MatchController.java` (add `postMatchPreview` endpoint + enrich `detail`) | 97-01 | controller | request-response | `postSchedule` endpoint (lines 258–269) + `detail` model population (lines 100–124) | exact |
| `src/main/resources/templates/admin/match-detail.html` (append Post Match Preview button) | 97-01 | template | request-response | Existing `.discord-actions--posts` block with disabled-span + re-post form pattern (lines 154–171) | exact |
| `src/main/resources/static/admin/css/admin.css` (optional `.discord-post-status--auto-edit` pill) | 97-01 | css | — | Existing `.discord-post-status--*` class family in `admin.css` | role-match |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` (add `postMatchdayResults` + `postPowerRankings`) | 97-02 | service | request-response | `postRaceResultToForumThread` (lines 421–448) — forum-thread + `?thread_id=` + byte[] attachment pattern | exact |
| `src/main/java/org/ctc/admin/controller/MatchdayController.java` (add 2 POST endpoints + enrich `detail`) | 97-02 | controller | request-response | `postSchedule` in `MatchController` (lines 258–269); `detail` model in `MatchdayController` (lines 52–62) | role-match |
| `src/main/resources/templates/admin/matchday-detail.html` (NEW Discord Actions card with 2 buttons) | 97-02 | template | request-response | `match-detail.html` `.discord-actions--posts` cluster (lines 55–172) | role-match |
| `src/main/java/org/ctc/admin/service/StandingsGraphicService.java` | 97-03 | service | batch | `PowerRankingsGraphicService.java` (lines 1–197) — direct `AbstractGraphicService` extension, `renderToBytes`, `encodeCardBase64` | exact |
| `src/main/resources/templates/admin/standings-render.html` | 97-03 | template | batch | `templates/admin/matchday-results-render.html` — Playwright render: 1920×1080, `fontBase64`, `ctcLogoBase64`, dark theme | exact |
| `src/main/resources/db/migration/V14__add_discord_post_phase_id.sql` | 97-03 | migration | — | `V12__discord_post.sql` (lines 8–11, 16–26) — `UUID NULL` FK column + `ON DELETE SET NULL` + `CREATE INDEX` | exact |
| `src/main/java/org/ctc/discord/model/DiscordPost.java` (add `@ManyToOne SeasonPhase phase`) | 97-03 | model | — | `DiscordPost.java` existing `@Column UUID matchId/seasonId` fields (lines 47–57) | exact |
| `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` (widen `SeasonRef`) | 97-03 | dto-sealed | — | `DiscordPostRef.SeasonRef` (lines 123–148) | exact |
| `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` (add `findBy...SeasonIdAndPhaseId`) | 97-03 | repository | CRUD | Existing `findByChannelIdAndPostTypeAndSeasonId` (line 19–21) | exact |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` (add `postStandings` + update SeasonRef switch) | 97-03 | service | request-response | `postRaceResultToForumThread` (lines 421–448) + existing `SeasonRef` branch in sealed-switch (lines 359–360) | exact |
| `src/main/java/org/ctc/domain/service/StandingsService.java` (add `hasNewerResultsSincePhaseScoped`) | 97-03 | service | CRUD | `StandingsService.calculateStandings(phaseId, groupId)` (line 40) — same service, new `@Transactional(readOnly = true)` query method | role-match |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` (add `postStandings` endpoint + enrich `edit`) | 97-03 | controller | request-response | `RaceController.postRaceResultToForum` (lines 119–131) + `SeasonController.edit` (lines 85–104) | exact |
| `src/main/resources/templates/admin/season-form.html` (append Post Standings button + phase-selector) | 97-03 | template | request-response | `season-form.html` `#discordIntegration` card (lines 170–234); phase-selector `<select>` has no close analog — see "Pattern Conflicts" section | partial |
| `pom.xml` (add `StandingsGraphicService.class` to JaCoCo exclusions) | 97-03 | config | — | Existing `<exclude>org/ctc/admin/service/PowerRankingsGraphicService.class</exclude>` (line 379) | exact |
| `.planning/phases/97-matchday-level-posts/97-UI-SPEC.md` (revision) | 97-01 | docs | — | Current `97-UI-SPEC.md` content (revision only, not a new analog) | — |

---

## Pattern Assignments

### Plan 97-01 — POST-06 Match Preview + Auto-Edit Hook

---

#### `MatchPreviewFieldsChangedEvent.java` (new — event-record)

**Analog:** `src/main/java/org/ctc/discord/event/MatchScheduleFieldsChangedEvent.java` (lines 1–6)

**Full file to copy verbatim (change class name only):**
```java
package org.ctc.discord.event;

import java.util.UUID;

public record MatchPreviewFieldsChangedEvent(UUID matchId) {
}
```

---

#### `MatchPreviewPreFlightResult.java` (new — dto-record)

**Analog:** `DiscordPostService.WebhookCredentials` inner record (line 513) for the record shape; method signatures from RESEARCH.md.

**Pattern to copy:**
```java
package org.ctc.admin.dto;

import org.jspecify.annotations.Nullable;

public record MatchPreviewPreFlightResult(boolean canPost, @Nullable String disabledReason) {
}
```

Note: `@Nullable` matches the project's `org.jspecify.annotations.Nullable` import convention (used in `DiscordPostService.java` line 51).

---

#### `MatchService.java` — extend `updateDiscordFields` with MATCH_PREVIEW diff + event publish

**Analog:** `MatchService.updateDiscordFields` (lines 68–87) — exact diff+publish pattern

**Existing block to extend (lines 68–87):**
```java
@Transactional
public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);
    String beforeLobbyHost = match.getLobbyHost();
    String beforeRaceDirector = match.getRaceDirector();
    String beforeStreamer = match.getStreamer();

    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    Match saved = matchRepository.save(match);

    boolean scheduleFieldsChanged = !Objects.equals(beforeLobbyHost, form.getLobbyHost())
            || !Objects.equals(beforeRaceDirector, form.getRaceDirector())
            || !Objects.equals(beforeStreamer, form.getStreamer());
    if (scheduleFieldsChanged) {
        eventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(saved.getId()));
    }
}
```

**Addition pattern** — snapshot `discordTeaser` + `streamLink` BEFORE save, then compare AFTER save:
```java
// Before match.set*() calls — snapshot the two MATCH_PREVIEW fields:
String beforeTeaser = match.getDiscordTeaser();
String beforeStreamLink = match.getStreamLink();

// ... existing save block unchanged ...

boolean previewFieldsChanged = !Objects.equals(beforeTeaser, form.getDiscordTeaser())
        || !Objects.equals(beforeStreamLink, form.getStreamLink());
if (previewFieldsChanged) {
    eventPublisher.publishEvent(new MatchPreviewFieldsChangedEvent(saved.getId()));
}
```

Import to add: `import org.ctc.discord.event.MatchPreviewFieldsChangedEvent;`

---

#### `DiscordAutoPostListener.java` — add `onMatchPreviewFieldsChanged`

**Analog:** `onScheduleFieldsChanged` (lines 49–67) — mirror verbatim, change event type and service call

**Pattern (lines 49–67):**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onScheduleFieldsChanged(MatchScheduleFieldsChangedEvent event) {
    Match match = matchRepository.findById(event.matchId()).orElse(null);
    if (match == null) {
        log.warn("Auto-edit SCHEDULE skipped — match {} not found post-commit", event.matchId());
        return;
    }
    try {
        discordPostService.autoEditScheduleIfNeeded(match);
    } catch (DiscordApiException e) {
        log.warn("Auto-edit SCHEDULE failed for match {}: category={}",
                event.matchId(), e.category().name());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, e.category().name().toLowerCase().replace('_', '-'));
    } catch (RuntimeException e) {
        log.warn("Auto-edit SCHEDULE failed for match {}: {}", event.matchId(), e.toString());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, "transient");
    }
}
```

New method substitutions: event type → `MatchPreviewFieldsChangedEvent`; log string `"SCHEDULE"` → `"MATCH_PREVIEW"`; service call → `discordPostService.autoEditMatchPreviewIfNeeded(match)`.

Import to add: `import org.ctc.discord.event.MatchPreviewFieldsChangedEvent;`

---

#### `DiscordPostService.java` — add `postMatchPreview` + `autoEditMatchPreviewIfNeeded`

**Analog for `autoEditMatchPreviewIfNeeded`:** `autoEditScheduleIfNeeded` (lines 173–192)

```java
@Transactional
public void autoEditScheduleIfNeeded(Match match) throws DiscordApiException {
    Optional<DiscordPost> existing = discordPostRepository
            .findByChannelIdAndPostTypeAndMatchId(
                    match.getDiscordChannelId(), DiscordPostType.SCHEDULE, match.getId());
    if (existing.isEmpty()) {
        return;
    }
    Optional<LocalDateTime> firstRaceTime = firstRaceTime(match);
    if (firstRaceTime.isEmpty()) {
        return;
    }
    WebhookPayload payload = buildSchedulePayload(match, firstRaceTime.get());
    postOrEdit(
            match.getDiscordChannelId(),
            match.getDiscordChannelWebhookUrl(),
            DiscordPostType.SCHEDULE,
            payload,
            List.of(),
            DiscordPostRef.match(match));
}
```

For `autoEditMatchPreviewIfNeeded`: channel ID comes from `parseWebhookUrl(config.getAnnouncementWebhookUrl()).id()` (not `match.getDiscordChannelId()`); existing-row check uses `findByChannelIdAndPostTypeAndMatchId(announcementChannelId, MATCH_PREVIEW, match.getId())`; if present, rebuild Markdown payload and call `postOrEdit`.

**Analog for `postMatchPreview` (multipart + Markdown):** `postRaceResultToForumThread` (lines 421–448) for the try/catch + `NamedAttachment` + `postOrEdit` call shape; `postSettings`/`postLineups` (lines 278–315) for the `readPng` + multi-attachment pattern.

```java
@Transactional
public DiscordPost postRaceResultToForumThread(Race race) throws DiscordApiException {
    // ... config + threadId + webhookUrl resolution ...
    WebhookCredentials creds = parseWebhookUrl(webhookUrl);
    try {
        byte[] png = resultsGraphicService.generateResultsBytes(race);
        String filename = "race-result-...png";
        NamedAttachment attachment = new NamedAttachment(filename, png);
        return postOrEdit(
                creds.id(),
                webhookUrl,
                DiscordPostType.RACE_RESULTS,
                WebhookPayload.empty(),
                List.of(attachment),
                DiscordPostRef.race(race),
                threadId);
    } catch (IOException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
}
```

For `postMatchPreview`: no `threadId` (announcement-webhook is a regular channel, not a forum thread); attachments come from `readPng(settingsGraphicService.generateSettings(race))` + `readPng(lineupGraphicService.generateLineup(race))` for each race (using the existing private `readPng` method); `WebhookPayload` is built with Markdown content string.

**`matchHasCompleteLineups` pre-flight already exists (line 239–243):**
```java
public boolean matchHasCompleteLineups(Match match) {
    List<Race> races = match.getRaces();
    return !races.isEmpty()
            && races.stream().allMatch(r -> !raceLineupRepository.findByRaceId(r.getId()).isEmpty());
}
```

`canPostMatchPreview` follows the same shape but returns `MatchPreviewPreFlightResult` — evaluate 5 predicates in order, return first failing reason.

---

#### `MatchController.java` — add `postMatchPreview` endpoint + enrich `detail`

**Analog for endpoint:** `postSchedule` (lines 258–269)

```java
@PostMapping("/{id}/post-schedule")
public String postSchedule(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postSchedule(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Schedule posted.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post schedule");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post schedule");
    }
    return "redirect:/admin/matches/" + id;
}
```

New endpoint: URL `/{id}/post-match-preview`; success message `"Match preview posted."`; same two-catch pattern.

**Analog for `detail` enrichment:** existing `detail` model population (lines 110–124):
```java
DiscordPost matchResultsPost = findMatchPost(match, DiscordPostType.MATCH_RESULTS);
model.addAttribute("matchResultsPost", matchResultsPost);
model.addAttribute("matchResultsStale", isStale(matchResultsPost, latestRaceResultUpdate(match)));
```

Add: `model.addAttribute("matchPreviewPost", ...)` + `model.addAttribute("matchPreviewPreFlight", discordPostService.canPostMatchPreview(match))`.

**`findMatchPost` helper pattern (lines 326+):**
```java
private DiscordPost findMatchPost(Match match, DiscordPostType type) {
    return discordPostRepository
            .findByChannelIdAndPostTypeAndMatchId(
                    match.getDiscordChannelId(), type, match.getId())
            .orElse(null);
}
```

For MATCH_PREVIEW: channel ID is `parseWebhookUrl(config.getAnnouncementWebhookUrl()).id()`, not `match.getDiscordChannelId()`. Requires loading `DiscordGlobalConfig` in `detail`.

---

#### `match-detail.html` — append Post Match Preview button

**Analog:** `schedulePost` block (lines 154–171) — disabled-span + initial + re-post form pattern

```html
<!-- disabled span when pre-flight fails -->
<span th:if="${matchPreviewPost == null and not matchPreviewPreFlight.canPost}"
      class="btn btn-secondary btn-sm disabled"
      data-testid="post-match-preview-disabled"
      th:title="${matchPreviewPreFlight.disabledReason}">Post Match Preview</span>

<!-- initial post form when pre-flight passes -->
<form th:if="${matchPreviewPost == null and matchPreviewPreFlight.canPost}"
      th:action="@{/admin/matches/{id}/post-match-preview(id=${match.id})}"
      method="post" class="form-inline">
    <button type="submit" class="btn btn-primary btn-sm" data-testid="post-match-preview">
        Post Match Preview
    </button>
</form>

<!-- re-post form when row exists -->
<form th:if="${matchPreviewPost != null}"
      th:action="@{/admin/matches/{id}/post-match-preview(id=${match.id})}"
      method="post" class="form-inline">
    <button type="submit" class="btn btn-secondary btn-sm" data-testid="repost-match-preview">
        Re-Post Match Preview
    </button>
</form>
```

The button block is appended inside the existing `.discord-actions--posts` `<div>`. The outer `th:if="${match.discordChannelId != null}"` guard (line 55) does NOT apply here since MATCH_PREVIEW targets the announcement webhook, not the match channel. Plan must insert a separate outer condition (or append after the `</div>` that closes the channel-posts cluster) gated on `matchPreviewPreFlight != null` (i.e., announcement webhook configured).

---

### Plan 97-02 — POST-07a (Match Day Results) + POST-07b (Power Rankings)

---

#### `DiscordPostService.java` — add `postMatchdayResults` + `postPowerRankings`

**Analog:** `postRaceResultToForumThread` (lines 421–448) — exact forum-thread pattern with `threadId` and `creds.id()` as `channelId`

```java
@Transactional
public DiscordPost postRaceResultToForumThread(Race race) throws DiscordApiException {
    DiscordGlobalConfig config = globalConfigService.getOrInitialize();
    if (!canPostRaceResultToForum(race, config)) {
        throw new BusinessRuleException("...");
    }
    Season season = race.getMatchday().getSeason();
    String threadId = season.getDiscordRaceResultsThreadId();
    String webhookUrl = config.getRaceResultsForumWebhookUrl();
    WebhookCredentials creds = parseWebhookUrl(webhookUrl);
    try {
        byte[] png = resultsGraphicService.generateResultsBytes(race);
        int raceNumber = race.getMatchday().getRaces().indexOf(race) + 1;
        String filename = "race-result-" + race.getMatchday().getLabel() + "-race-" + raceNumber + ".png";
        NamedAttachment attachment = new NamedAttachment(filename, png);
        return postOrEdit(
                creds.id(),
                webhookUrl,
                DiscordPostType.RACE_RESULTS,
                WebhookPayload.empty(),
                List.of(attachment),
                DiscordPostRef.race(race),
                threadId);
    } catch (IOException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
}
```

For `postMatchdayResults`: `matchdayResultsGraphicService.generateResults(matchday) → byte[]` (already byte[], no `readPng` needed); `DiscordPostRef.matchday(matchday)`; type `MATCHDAY_OVERVIEW`; `threadId = matchday.getSeason().getDiscordRaceResultsThreadId()`.

For `postPowerRankings`: `powerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds) → byte[]`; `DiscordPostRef.matchday(matchday)`; type `POWER_RANKINGS`; same `threadId`. New injections needed: `MatchdayResultsGraphicService`, `PowerRankingsGraphicService` (add to constructor — both already JaCoCo-excluded; `MatchdayResultsGraphicService` is NOT in JaCoCo exclusions, see RESEARCH.md).

**Note:** `DiscordPostRef.MatchdayRef` already exists (lines 69–94 of `DiscordPostRef.java`) and `DiscordPostRepository.findByChannelIdAndPostTypeAndMatchdayId` already exists (line 22–24). No sealed-interface changes needed for Plan 97-02.

---

#### `MatchdayController.java` — add 2 POST endpoints + enrich `detail`

**Analog for endpoints:** `MatchController.postSchedule` (lines 258–269) — exact structure

```java
@PostMapping("/{id}/post-schedule")
public String postSchedule(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postSchedule(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Schedule posted.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post schedule");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post schedule");
    }
    return "redirect:/admin/matches/" + id;
}
```

New endpoints: `/{id}/post-matchday-results` → `"Match day results posted."` + `/{id}/post-power-rankings` → `"Power rankings posted."`. Both redirect to `/admin/matchdays/{id}`.

**Analog for `detail` enrichment:** `RaceController.populateRaceForumPostModel` (lines 92–102) — separate helper that loads config, post existence, and disabled reason.

```java
private void populateRaceForumPostModel(Model model, Race race) {
    DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
    boolean canPost = discordPostService.canPostRaceResultToForum(race, config);
    String disabledReason = computeForumPostDisabledReason(race, config);
    DiscordPost existingPost = discordPostRepository
            .findByPostTypeAndRaceId(DiscordPostType.RACE_RESULTS, race.getId())
            .orElse(null);
    model.addAttribute("canPostRaceResultToForum", canPost);
    model.addAttribute("forumPostDisabledReason", disabledReason);
    model.addAttribute("raceResultsForumPost", existingPost);
}
```

Add to `MatchdayController`: inject `DiscordPostService`, `DiscordPostRepository`, `DiscordGlobalConfigService` (via `final` fields + `@RequiredArgsConstructor`). Add `populateMatchdayDiscordModel` helper analogous to the RaceController pattern.

**Stale-detection analog:** `MatchController.isStale` helper (lines 127–132):
```java
private static boolean isStale(DiscordPost post, java.time.LocalDateTime latestRaceResultUpdate) {
    if (post == null || post.getUpdatedAt() == null || latestRaceResultUpdate == null) {
        return false;
    }
    return post.getUpdatedAt().isBefore(latestRaceResultUpdate);
}
```

POST-07a stale: any `RaceResult.updatedAt` in matchday > `matchdayOverviewPost.updatedAt`. POST-07b stale: `MAX(SeasonTeam.updatedAt)` for season > `powerRankingsPost.updatedAt`.

---

#### `matchday-detail.html` — NEW Discord Actions card with 2 buttons

**Analog:** `match-detail.html` `.discord-actions--posts` cluster (lines 55–172)

The new card wraps a single `.discord-actions discord-actions--posts` div. Structure:

```html
<!-- Discord Actions card (only when race-results forum thread and webhook are configured) -->
<div th:if="${matchdayDiscordActive}" class="card mt-md">
    <h2>Discord Actions</h2>
    <div class="discord-actions discord-actions--posts">

        <!-- POST-07a: Match Day Results -->
        <span th:if="${matchdayOverviewPost == null and not canPostMatchdayResults}"
              class="btn btn-secondary btn-sm disabled"
              data-testid="post-matchday-results-disabled"
              th:title="${matchdayResultsDisabledReason}">Post Match Day Results</span>
        <form th:if="${matchdayOverviewPost == null and canPostMatchdayResults}"
              th:action="@{/admin/matchdays/{id}/post-matchday-results(id=${matchday.id})}"
              method="post" class="form-inline">
            <button type="submit" class="btn btn-primary btn-sm"
                    data-testid="post-matchday-results">Post Match Day Results</button>
        </form>
        <form th:if="${matchdayOverviewPost != null}"
              th:action="@{/admin/matchdays/{id}/post-matchday-results(id=${matchday.id})}"
              method="post" class="form-inline">
            <button type="submit" class="btn btn-secondary btn-sm"
                    th:attr="data-testid=${matchdayResultsStale ? 'update-matchday-results' : 'repost-matchday-results'}"
                    th:text="${matchdayResultsStale ? 'Update Match Day Results' : 'Re-Post Match Day Results'}"></button>
        </form>

        <!-- POST-07b: Power Rankings -->
        <span th:if="${powerRankingsPost == null and not canPostPowerRankings}"
              class="btn btn-secondary btn-sm disabled"
              data-testid="post-power-rankings-disabled"
              th:title="${powerRankingsDisabledReason}">Post Power Rankings</span>
        <form th:if="${powerRankingsPost == null and canPostPowerRankings}"
              th:action="@{/admin/matchdays/{id}/post-power-rankings(id=${matchday.id})}"
              method="post" class="form-inline">
            <button type="submit" class="btn btn-primary btn-sm"
                    data-testid="post-power-rankings">Post Power Rankings</button>
        </form>
        <form th:if="${powerRankingsPost != null}"
              th:action="@{/admin/matchdays/{id}/post-power-rankings(id=${matchday.id})}"
              method="post" class="form-inline">
            <button type="submit" class="btn btn-secondary btn-sm"
                    th:attr="data-testid=${powerRankingsStale ? 'update-power-rankings' : 'repost-power-rankings'}"
                    th:text="${powerRankingsStale ? 'Update Power Rankings' : 'Re-Post Power Rankings'}"></button>
        </form>

    </div>
</div>
```

`matchdayDiscordActive` = `threadLinked && webhookConfigured` (computed in `detail` model). Mobile responsive via existing `.discord-actions` flex-column rule in `admin.css` (lines 221–228) — no new CSS needed.

---

### Plan 97-03 — POST-08 Standings + StandingsGraphicService + V14

---

#### `StandingsGraphicService.java` (new — Playwright-based graphic service)

**Analog:** `PowerRankingsGraphicService.java` (lines 1–197) — extends `AbstractGraphicService` directly, has `renderToBytes` + `renderTemplate`, accesses `encodeCardBase64`, returns `byte[]`

**Class skeleton to copy:**
```java
@Slf4j
@Service
public class StandingsGraphicService extends AbstractGraphicService {

    private static final String DEFAULT_TEMPLATE_PATH = "admin/standings-render";

    private final StandingsService standingsService;
    private final SeasonTeamRepository seasonTeamRepository;
    // SeasonPhaseService injected for phase.getGroups() + phase.getLayout()

    public StandingsGraphicService(TemplateEngine templateEngine,
                                   StandingsService standingsService,
                                   SeasonTeamRepository seasonTeamRepository,
                                   SeasonPhaseService seasonPhaseService,
                                   @Value("${app.upload-dir:uploads}") String uploadDir) {
        super(templateEngine, uploadDir);
        // assign fields
    }

    public List<byte[]> generateStandingsBytes(Season season, SeasonPhase phase) throws IOException {
        // branch: phase.getLayout() == PhaseLayout.GROUPS → iterate phase.getGroups() sorted by sortIndex ASC
        //         else → single render
        // delegate to renderSingleStandings(season, phase, groupId)
    }

    private byte[] renderSingleStandings(Season season, SeasonPhase phase, UUID groupId) throws IOException {
        List<TeamStanding> standings = standingsService.calculateStandings(phase.getId(), groupId);
        var ctx = new Context();
        ctx.setVariable("standings", standings);
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
        ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
        // encode team logos for each standing
        String html = templateEngine.process(DEFAULT_TEMPLATE_PATH, ctx);
        return renderToBytes(html);
    }

    private byte[] renderToBytes(String html) throws IOException {
        Path tempFile = Files.createTempFile("standings-graphic-", ".png");
        try {
            renderScreenshot(html, tempFile);
            return Files.readAllBytes(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
```

**`encodeLogoBase64` pattern from `AbstractMatchdayGraphicService` (lines 113–119):**
```java
private String encodeLogoBase64(Team team, SeasonTeam seasonTeam) {
    String logoUrl = seasonTeam != null ? seasonTeam.getEffectiveLogoUrl() : team.getLogoUrl();
    if (logoUrl == null) {
        return null;
    }
    return encodeCardBase64(logoUrl);
}
```

JaCoCo: must add `<exclude>org/ctc/admin/service/StandingsGraphicService.class</exclude>` to `pom.xml` — mirror line 379 pattern.

---

#### `standings-render.html` (new — Playwright-input template)

**Analog:** `templates/admin/matchday-results-render.html` — 1920×1080, dark theme, `fontBase64` via `@font-face`, `ctcLogoBase64` in header, Thymeleaf `th:each` for rows.

**Structure to copy (header section from `matchday-results-render.html` lines 1–55):**
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <style th:inline="text">
        @font-face {
            font-family: 'Conthrax';
            src: url([[${fontBase64}]]) format('woff2');
            font-weight: 600;
        }
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { width: 1920px; height: 1080px; overflow: hidden;
               font-family: 'Conthrax', sans-serif; background: #000; color: white; }
        /* standings-specific layout */
    </style>
</head>
<body>
    <img th:src="${ctcLogoBase64}" class="ctc-logo" alt="CTC"/>
    <h1 th:text="${seasonName}"></h1>
    <!-- standings table with position, team logo, team name, W-D-L, Pts -->
    <table>
        <tr th:each="standing, iter : ${standings}">
            <td th:text="${iter.count}"></td>
            <td><img th:src="${standing.logoBase64}"/><span th:text="${standing.team.shortName}"></span></td>
            <!-- W / D / L / Pts -->
        </tr>
    </table>
</body>
</html>
```

Data shape from `StandingsService.TeamStanding` (fields: `team`, `played`, `wins`, `losses`, `draws`, `points`). Add team logo via `encodeLogoBase64` in the service before passing to template context.

---

#### `V14__add_discord_post_phase_id.sql` (new — Flyway migration)

**Analog:** `V12__discord_post.sql` (lines 8–10, 16–20, 22–26) — UUID NULL FK + ON DELETE SET NULL + CREATE INDEX

```sql
ALTER TABLE discord_post ADD COLUMN phase_id UUID NULL;
ALTER TABLE discord_post ADD CONSTRAINT fk_discord_post_phase
    FOREIGN KEY (phase_id) REFERENCES season_phases(id) ON DELETE SET NULL;
CREATE INDEX idx_discord_post_phase_id ON discord_post (phase_id);
```

H2 + MariaDB compatibility: confirmed by V12 pattern. No `CHECK` constraints, no `LONGTEXT`.

---

#### `DiscordPost.java` — add `@ManyToOne SeasonPhase phase` field

**Analog:** Existing `@Column` UUID fields (lines 47–57):
```java
@Column(name = "match_id")
private UUID matchId;
```

New field pattern — nullable `@ManyToOne` (consistent with project OSIV active + lazy loading):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "phase_id")
private SeasonPhase phase;
```

No `nullable = false` on `@JoinColumn` — column is nullable (existing posts keep `phase_id = NULL`).

Import: `import org.ctc.domain.model.SeasonPhase;` (or wherever `SeasonPhase` lives — verify package).

---

#### `DiscordPostRef.java` — widen `SeasonRef` to carry optional `phaseId`

**Analog:** Existing `SeasonRef(UUID id)` (lines 123–148) — widen to `SeasonRef(UUID seasonId, @Nullable UUID phaseId)`

**Current pattern (lines 123–148):**
```java
record SeasonRef(UUID id) implements DiscordPostRef {
    @Override
    public void applyTo(DiscordPost row) {
        row.setSeasonId(id);
    }
    @Override public UUID matchId() { return null; }
    @Override public UUID matchdayId() { return null; }
    @Override public UUID raceId() { return null; }
    @Override public UUID seasonId() { return id; }
}
```

**Widened pattern:**
```java
record SeasonRef(UUID seasonId, @Nullable UUID phaseId) implements DiscordPostRef {
    @Override
    public void applyTo(DiscordPost row) {
        row.setSeasonId(seasonId);
        // phaseId applied separately in postOrEdit when non-null
    }
    @Override public UUID matchId() { return null; }
    @Override public UUID matchdayId() { return null; }
    @Override public UUID raceId() { return null; }
    @Override public UUID seasonId() { return seasonId; }
}
```

Factory methods:
```java
static DiscordPostRef season(Season s) { return new SeasonRef(s.getId(), null); }
static DiscordPostRef seasonPhase(Season s, SeasonPhase p) { return new SeasonRef(s.getId(), p.getId()); }
```

Sealed interface `permits` list already includes `SeasonRef` — no change needed. Import `org.ctc.domain.model.SeasonPhase` in `DiscordPostRef.java`.

---

#### `DiscordPostRepository.java` — add `findByChannelIdAndPostTypeAndSeasonIdAndPhaseId`

**Analog:** Existing `findByChannelIdAndPostTypeAndSeasonId` (lines 19–21):
```java
Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonId(
        String channelId, DiscordPostType postType, UUID seasonId);
```

New derived query to add:
```java
Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(
        String channelId, DiscordPostType postType, UUID seasonId, UUID phaseId);
```

JPA derives the query from field names `phase_id` → `phaseId` (via `DiscordPost.phase` `@JoinColumn(name="phase_id")`). Note: derived-query on `@ManyToOne` in Spring Data JPA uses the association field name `phase` + `id` → `findBy...PhaseId`. Verify with a test that `phase_id` column is accessible as `phaseId` after V14 migration.

---

#### `DiscordPostService.java` — add `postStandings` + update SeasonRef sealed-switch branch

**Analog for `postStandings`:** `postRaceResultToForumThread` (lines 421–448) — thread-targeted post with `creds.id()` as `channelId`

**Sealed-switch update in `postOrEdit` (lines 359–362):**
```java
case DiscordPostRef.SeasonRef s ->
        discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id());
```

Updated to:
```java
case DiscordPostRef.SeasonRef s -> s.phaseId() != null
        ? discordPostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(
                channelId, type, s.seasonId(), s.phaseId())
        : discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(
                channelId, type, s.seasonId());
```

Also: when creating new `DiscordPost` row and `ref` is `SeasonRef` with non-null `phaseId`, set `row.setPhase(entityManager.getReference(SeasonPhase.class, s.phaseId()))` before saving. This requires injecting `EntityManager` or `SeasonPhaseRepository` into `DiscordPostService`.

**New injections for `DiscordPostService`:** `MatchdayResultsGraphicService`, `PowerRankingsGraphicService`, `StandingsGraphicService`, `SeasonPhaseRepository` (or `EntityManager` for `getReference`). Update the `@SuppressFBWarnings` justification comment to add new beans.

---

#### `StandingsService.java` — add `hasNewerResultsSincePhaseScoped`

**Analog:** Existing `calculateStandings` method signature + `@Transactional(readOnly = true)` pattern in `StandingsService.java` (line 40)

```java
@Transactional(readOnly = true)
public boolean hasNewerResultsSincePhaseScoped(UUID seasonId, UUID phaseId, LocalDateTime since) {
    // Use RaceResultRepository.findByRaceMatchdayPhaseId(phaseId) for REGULAR/PLACEMENT
    // Use findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId) for PLAYOFF
    // Return any result with updatedAt > since
}
```

`RaceResultRepository` already has `findByRaceMatchdayPhaseId(UUID phaseId)` (verified in RESEARCH.md line 45). PLAYOFF variant `findByRacePlayoffMatchupRoundPlayoffPhaseId(UUID phaseId)` also verified (lines 47–48).

---

#### `SeasonController.java` — add `postStandings` endpoint + enrich `edit`

**Analog for endpoint:** `RaceController.postRaceResultToForum` (lines 119–131) — exact pattern

```java
@PostMapping("/{id}/post-race-result-to-forum")
public String postRaceResultToForum(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    Race race = raceService.getRaceDetailData(id).race();
    try {
        discordPostService.postRaceResultToForumThread(race);
        redirectAttributes.addFlashAttribute("successMessage", "Race result posted to forum-thread.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post race result to forum");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post race result to forum");
    }
    return "redirect:/admin/races/" + id;
}
```

New endpoint: `POST /{id}/post-standings` with `@RequestParam UUID phaseId`; success `"Standings posted."`. Redirect to `/admin/seasons/{id}/edit`.

**Analog for `edit` enrichment:** `SeasonController.populateDiscordIntegrationModel` (lines 107–123) — add phase list + per-phase stale map + per-phase post map.

```java
List<SeasonPhase> allPhases = seasonPhaseService.findAllPhases(id);
// standingsPostByPhase: Map<UUID, DiscordPost>
// standingsStaleByPhase: Map<UUID, Boolean> (from StandingsService.hasNewerResultsSincePhaseScoped)
model.addAttribute("allPhases", allPhases);
model.addAttribute("canPostStandings", canPostStandings);
model.addAttribute("standingsPostByPhase", standingsPostByPhase);
model.addAttribute("standingsStaleByPhase", standingsStaleByPhase);
```

New injection for `SeasonController`: `DiscordPostService`, `DiscordPostRepository`, `StandingsService`.

---

#### `season-form.html` — append Post Standings button + phase-selector dropdown

**Analog for button block:** `match-detail.html` disabled-span + form pattern (lines 154–171):
```html
<span th:if="${schedulePost == null and not scheduleVisible}"
      class="btn btn-secondary btn-sm disabled"
      title="Schedule a race time first">Post Schedule</span>
<form th:if="${scheduleVisible and schedulePost == null}"
      th:action="..." method="post" class="form-inline">
    <button type="submit" class="btn btn-primary btn-sm">Post Schedule</button>
</form>
```

New block appended at the BOTTOM of `#discordIntegration` card (after existing thread-link rows), gated on `canPostStandings`:

```html
<!-- Post Standings section (only when standingsThread + webhook configured) -->
<div th:if="${canPostStandings}" class="discord-actions discord-actions--posts">

    <!-- Phase selector: hidden when exactly 1 phase, shown as <select> otherwise -->
    <form th:action="@{/admin/seasons/{id}/post-standings(id=${seasonForm.id})}" method="post" class="form-inline">
        <!-- multi-phase: dropdown -->
        <select th:if="${allPhases.size() > 1}" name="phaseId" class="form-control form-control-sm">
            <option th:each="p : ${allPhases}"
                    th:value="${p.id}"
                    th:text="${p.phaseType.name()}"></option>
        </select>
        <!-- single-phase: hidden input -->
        <input th:if="${allPhases.size() == 1}"
               type="hidden" name="phaseId" th:value="${allPhases[0].id}"/>

        <!-- button varies per selected phase post/stale state -->
        <!-- ... post/repost/update logic per phase ... -->
    </form>
</div>
```

Note: phase-selector dropdown `<select>` with `<option th:each>` has no close analog in `season-form.html`. The closest `th:each` select in the codebase is the Power Rankings download form or the matchday generator form — see "Pattern Conflicts" section.

---

## Shared Patterns

### `@TransactionalEventListener` + `@Transactional(REQUIRES_NEW)` Event-Listener Pattern
**Source:** `DiscordAutoPostListener.java` (lines 29–47 `onChannelCreated`, lines 49–67 `onScheduleFieldsChanged`)
**Apply to:** `onMatchPreviewFieldsChanged` in Plan 97-01

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onScheduleFieldsChanged(MatchScheduleFieldsChangedEvent event) {
    Match match = matchRepository.findById(event.matchId()).orElse(null);
    if (match == null) {
        log.warn("Auto-edit SCHEDULE skipped — match {} not found post-commit", event.matchId());
        return;
    }
    try {
        discordPostService.autoEditScheduleIfNeeded(match);
    } catch (DiscordApiException e) {
        log.warn("Auto-edit SCHEDULE failed for match {}: category={}",
                event.matchId(), e.category().name());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, e.category().name().toLowerCase().replace('_', '-'));
    } catch (RuntimeException e) {
        log.warn("Auto-edit SCHEDULE failed for match {}: {}", event.matchId(), e.toString());
        recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, "transient");
    }
}
```

### Controller Discord-Error Flash Pattern
**Source:** `MatchController.applyErrorFlash` (lines 349–370)
**Apply to:** All new Discord POST endpoints (Plan 97-01, 97-02, 97-03)

```java
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case MISSING_PERMISSIONS -> DiscordApiExceptionMapper.MISSING_PERMISSIONS_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    log.warn("{} failed: category={}, ...", action, category, ...);
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}
```

### Forum-Thread Targeted `postOrEdit` Call Pattern
**Source:** `DiscordPostService.postRaceResultToForumThread` (lines 430–444)
**Apply to:** `postMatchdayResults`, `postPowerRankings`, `postStandings` in Plans 97-02 and 97-03

```java
WebhookCredentials creds = parseWebhookUrl(webhookUrl);
// ...
return postOrEdit(
        creds.id(),       // channelId = webhook ID parsed from URL
        webhookUrl,
        DiscordPostType.RACE_RESULTS,
        WebhookPayload.empty(),
        List.of(attachment),
        DiscordPostRef.race(race),
        threadId);        // 7-arg overload: auto-unarchive + thread_id query param
```

### Playwright `renderToBytes` Pattern
**Source:** `PowerRankingsGraphicService.renderToBytes` (lines 158–165) + `AbstractMatchdayGraphicService.renderToBytes` (lines 155–163)
**Apply to:** `StandingsGraphicService.renderToBytes` in Plan 97-03

```java
private byte[] renderToBytes(String html) throws IOException {
    Path tempFile = Files.createTempFile("power-rankings-", ".png");
    try {
        renderScreenshot(html, tempFile);
        return Files.readAllBytes(tempFile);
    } finally {
        Files.deleteIfExists(tempFile);
    }
}
```

### Template `th:if` Disabled-Span + Pre-Flight Button Pattern
**Source:** `match-detail.html` (lines 85–95, 104–113, 161–170)
**Apply to:** All new Discord post buttons in Plans 97-01, 97-02, 97-03

```html
<!-- disabled when pre-flight fails -->
<span th:if="${post == null and not canPost}"
      class="btn btn-secondary btn-sm disabled"
      data-testid="post-X-disabled"
      th:title="${disabledReason}">Post X</span>
<!-- enabled when pre-flight passes + no existing post -->
<form th:if="${post == null and canPost}" th:action="@{/...}" method="post" class="form-inline">
    <button type="submit" class="btn btn-primary btn-sm" data-testid="post-X">Post X</button>
</form>
<!-- re-post when row exists -->
<form th:if="${post != null}" th:action="@{/...}" method="post" class="form-inline">
    <button type="submit" class="btn btn-secondary btn-sm" data-testid="repost-X">Re-Post X</button>
</form>
```

### `DiscordPost` Polymorphic FK Column Shape
**Source:** `DiscordPost.java` (lines 47–57) + `V12__discord_post.sql` (lines 8–10, 16–20)
**Apply to:** `phase_id` FK column in Plan 97-03

```java
// Entity:
@Column(name = "phase_id")
private UUID phaseId;   // plain UUID column (DiscordPost uses plain @Column not @ManyToOne)
```

**Critical correction vs. RESEARCH.md draft:** RESEARCH.md line 383 suggests `@ManyToOne SeasonPhase phase`. However, `DiscordPost` uses plain `@Column UUID` for all other FK references (`matchId`, `matchdayId`, `raceId`, `seasonId`) — NOT `@ManyToOne`. This is the documented pattern: "DiscordPost uses plain @Column UUID not @ManyToOne" (RESEARCH.md summary line 74). The `SeasonRef.applyTo` calls `row.setSeasonId(id)` — a plain UUID setter, not an entity reference. Plan 97-03 must follow the SAME convention: add `@Column(name = "phase_id") private UUID phaseId;` to `DiscordPost`. The `@ManyToOne` in RESEARCH.md section "DiscordPost Entity Change" contradicts the pattern; plain `@Column UUID` is correct.

---

## Pattern Conflicts / Missing Analogs

| File | Issue | Recommendation |
|------|-------|----------------|
| `season-form.html` — `<select name="phaseId">` phase-selector dropdown | No existing `<select th:each>` on the `#discordIntegration` card. Closest analog is the phase-team assignment forms (`season-form.html` elsewhere) or the Power Rankings download form (`power-rankings-download.html`) — neither is in the same card. | Use standard Thymeleaf `<select>` + `<option th:each="p : ${allPhases}" th:value="${p.id}" th:text="${p.phaseType.name()}">`. No custom CSS class needed — `form-control form-control-sm` is the project standard for inline selects. Auto-hide when `allPhases.size() == 1` via `th:if` + `<input type="hidden">` fallback. |
| `DiscordPostService.java` — `postMatchPreview` Markdown builder | No existing Markdown-body `WebhookPayload` in the codebase; all current posts use `WebhookPayload.empty()` (attachments-only) or embed payloads (`buildSchedulePayload`). `postMatchPreview` is the first plain-Markdown-content post. | Use `new WebhookPayload(contentString, List.of())` — the `WebhookPayload` record already accepts a nullable `content` string. Pattern for `DiscordTimestamps.longDateTime` usage exists in `buildSchedulePayload` (lines 201–203). `DiscordEmojiCache.emojiFor` is a new injection (not yet in `DiscordPostService` constructor) — follow the `@SuppressFBWarnings` constructor extension pattern (lines 82–124). |
| `StandingsGraphicService.java` — multi-PNG `List<byte[]>` return | No existing graphic service returns `List<byte[]>`. All return `byte[]` (single PNG). | `generateStandingsBytes` returns `List<byte[]>` (1 element for non-GROUPS, N elements for GROUPS). `postStandings` in `DiscordPostService` iterates the list and builds `List<NamedAttachment>` before calling `postOrEdit` — analog to `postMatchResults` (lines 136–145) which builds `List<NamedAttachment>` from multiple `byte[]` values. |
| `DiscordPostService.java` — setting `phase_id` on new `DiscordPost` row | `postOrEdit` calls `ref.applyTo(row)` which sets the UUID field. After widening `SeasonRef`, `applyTo` sets `row.setSeasonId(seasonId)`. But `phaseId` needs to be set separately. | Add `if (ref instanceof DiscordPostRef.SeasonRef s && s.phaseId() != null) { row.setPhaseId(s.phaseId()); }` after `ref.applyTo(row)` in the new-row branch of `postOrEdit`. This follows the existing pattern where `row.setAttachmentsReplacedAt(now)` is set conditionally. No `EntityManager.getReference` needed if `DiscordPost.phase` is a plain `@Column UUID phaseId` (see correction above). |
| `StandingsService.hasNewerResultsSincePhaseScoped` | Combines two `RaceResultRepository` query methods into a boolean. No existing service method follows this exact shape. | Return `anyMatch(r -> r.getUpdatedAt() != null && r.getUpdatedAt().isAfter(since))` over `findByRaceMatchdayPhaseId(phaseId)` UNION `findByRacePlayoffMatchupRoundPlayoffPhaseId(phaseId)`. Simplest correct implementation. |

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/discord/`, `src/main/java/org/ctc/admin/controller/`, `src/main/java/org/ctc/admin/service/`, `src/main/java/org/ctc/domain/service/`, `src/main/resources/templates/admin/`, `src/main/resources/db/migration/`
**Files read:** 20 source files
**Pattern extraction date:** 2026-05-24
