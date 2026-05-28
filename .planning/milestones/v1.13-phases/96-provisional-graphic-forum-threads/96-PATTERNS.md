# Phase 96: Provisional Graphic + Forum Threads — Pattern Map

**Mapped:** 2026-05-23
**Files analyzed:** 22 (3 NEW + 19 EXTEND)
**Analogs found:** 22 / 22 (100% — all production paths have an in-repo analog)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| **PLAN 96-01 (GRAFX-01)** | | | | |
| `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java` | NEW service | template-render + byte[] | `MatchResultsGraphicService.java` | exact (sibling) |
| `src/main/resources/templates/admin/provisional-scores-render.html` | NEW template | static-html screenshot | `match-results-render.html` | exact (sibling) |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | EXTEND service | multipart-bundle post | `DiscordPostService.postLineups` / `postRaceBundle` lines 237-265 | exact (in-file pattern) |
| `src/main/java/org/ctc/admin/controller/MatchController.java` | EXTEND controller | request-response form-POST | `MatchController.postMatchResults` lines 230-241 | exact (in-file pattern) |
| `src/main/resources/templates/admin/match-detail.html` | EXTEND template | rendered buttons | match-detail.html lines 78-114 (Settings/Lineups cluster) | exact (in-file pattern) |
| **PLAN 96-02 (FORUM-01)** | | | | |
| `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql` | NEW migration | DDL ADD COLUMN | `V10__add_matches_discord_and_scheduling_fields.sql` | exact (ALTER TABLE x4) |
| `src/main/java/org/ctc/discord/service/DiscordForumService.java` | NEW service | REST listing + sort | `DiscordPostService.java` + `DiscordRestClient` | role-match |
| `src/main/java/org/ctc/discord/dto/Thread.java` | EXTEND DTO | JSON deserialization | existing `Thread` record (8 LOC) | exact (additive record-fields) |
| `src/main/java/org/ctc/discord/dto/ThreadMetadata.java` (NEW supporting) | NEW DTO | JSON deserialization | `Webhook.java` / `Channel.java` records | role-match |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | EXTEND form-DTO | bind + validate | existing `announcementWebhookUrl` field (lines 21-23) | exact (in-file pattern) |
| `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` | EXTEND entity | JPA @Column + @ToString.Exclude | existing `announcementWebhookUrl` field (lines 30-31) + `@ToString(exclude=…)` line 20 | exact (in-file pattern) |
| `src/main/resources/templates/admin/discord-config.html` | EXTEND template | form-input + th:field | discord-config.html lines 28-34 (announcementWebhookUrl form-group) | exact (in-file pattern) |
| `src/main/java/org/ctc/domain/model/Season.java` | EXTEND entity | JPA @Column (nullable String) | `Match.discordChannelId` style (V10 precedent) | role-match |
| `src/main/java/org/ctc/admin/dto/SeasonForm.java` | EXTEND form-DTO | bind | existing `SeasonForm` (26 LOC) | exact (additive) |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` | EXTEND controller | request-response form-POST | `MatchController.moveToArchive` lines 277-301 (Modal-confirm endpoint) | role-match |
| `src/main/resources/templates/admin/season-form.html` | EXTEND template | + modal block | match-detail.html `archiveModal` lines 191-221 | role-match (modal pattern) |
| `src/main/java/org/ctc/backup/serialization/SeasonMixIn.java` | EXTEND MixIn | Jackson serialization | existing `SeasonMixIn` @JsonIgnoreProperties (line 27-29) | exact (additive — DEFAULT: include thread-IDs) |
| `src/main/java/org/ctc/backup/serialization/DiscordGlobalConfigMixIn.java` | NEW MixIn (if absent) | Jackson serialization | `SeasonMixIn` shape (37 LOC) | role-match |
| **PLAN 96-03 (FORUM-02)** | | | | |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | EXTEND service | sealed-switch + Auto-Unarchive | `postOrEdit` lines 276-326 (replace instanceof guard) | exact (in-file refactor) |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | EXTEND client | URL query-param + overloads | `executeMultipart` lines 63-105 + `forWebhookUrl` lines 173-179 | exact (in-file pattern) |
| `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` | EXTEND DTO | (Phase 95 already has all 4 permits) | DiscordPostRef.MatchRef record lines 42-67 | exact — only callsite changes |
| `src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java` | EXTEND DTO | + Boolean archived + factory | existing 2-field record (10 LOC) | exact (additive) |
| `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` | EXTEND repo | Spring-Data derived queries | `findByChannelIdAndPostTypeAndMatchId` line 13 | exact (additive) |
| `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` | EXTEND service | + byte[] variant | `MatchResultsGraphicService.generateMatchResults` lines 80-88 (temp-file + readAllBytes pattern) | exact (cross-service pattern) |
| `src/main/java/org/ctc/admin/controller/RaceController.java` | EXTEND controller | request-response form-POST | `MatchController.postMatchResults` lines 230-241 | role-match |
| `src/main/resources/templates/admin/race-detail.html` | EXTEND template | NEW Discord-Actions cluster | match-detail.html lines 23-154 (`.discord-actions--posts`) | exact (template-pattern reuse) |
| `src/main/resources/static/admin/css/admin.css` | EXTEND CSS | additive — modal/section/button styling | `.discord-actions` lines 211-227 + `.modal-overlay` lines 1124-1141 | exact (in-file pattern) |

---

## PLAN 96-01 — Pattern Assignments (Provisional Graphic + Multipart-Post)

### `ProvisionalScoresGraphicService.java` (NEW, service, template-render + byte[])

**Analog:** `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java` (157 LOC — read in full)

**Imports + class declaration** (lines 1-22):
```java
package org.ctc.admin.service;
// java.io.IOException, java.nio.file.{Files, Path}, lombok.extern.slf4j.Slf4j,
// org.ctc.domain.model.{Match, Race, RaceResult}, org.ctc.domain.service.ScoringService,
// org.springframework.beans.factory.annotation.Value, org.springframework.stereotype.Service,
// org.thymeleaf.TemplateEngine, org.thymeleaf.context.Context
@Slf4j
@Service
public class MatchResultsGraphicService extends AbstractGraphicService implements TemplateManageable {
    private static final String DEFAULT_TEMPLATE = "templates/admin/match-results-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "match-results-template.html";
    private final ScoringService scoringService;
```
**Apply:** rename to `ProvisionalScoresGraphicService`, `provisional-scores-render.html`, `provisional-scores-template.html`.

**Constructor pattern** (lines 29-34):
```java
public MatchResultsGraphicService(TemplateEngine templateEngine,
                                  ScoringService scoringService,
                                  @Value("${app.upload-dir:uploads}") String uploadDir) {
    super(templateEngine, uploadDir);
    this.scoringService = scoringService;
}
```

**Core pattern — render + screenshot + byte[]** (lines 36-89):
```java
public byte[] generateMatchResults(Match match) throws IOException {
    if (match.getHomeTeam() == null) throw new IllegalStateException("Match has no home team");
    if (match.getAwayTeam() == null) throw new IllegalStateException("Match has no away team");
    if (match.getRaces().isEmpty()) throw new IllegalStateException("Match has no races");
    var homeTeam = match.getHomeTeam();
    var season = match.getMatchday().getSeason();
    String homeCardBase64 = encodeCardBase64(buildCardPath(season.getId().toString(), homeTeam.getShortName()));
    if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
    var ctx = new Context();
    ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
    ctx.setVariable("matchdayName", match.getMatchday().getLabel());
    ctx.setVariable("homeCardBase64", homeCardBase64);
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
    String html = renderTemplate(ctx);
    Path tempFile = Files.createTempFile("match-results-", ".png");
    try {
        renderScreenshot(html, tempFile);
        return Files.readAllBytes(tempFile);
    } finally { Files.deleteIfExists(tempFile); }
}
```
**Apply:** signature → `byte[] generateProvisional(Race race)`. Drive `var homeTeam = race.getHomeTeam(); var awayTeam = race.getAwayTeam(); var season = race.getMatchday().getSeason();` (Race already has home/away accessors). New empty-results guard: `if (race.getResults().isEmpty()) throw new IllegalStateException("No results for this race");`. Replace `raceRows` with `homeRows` + `awayRows` per-driver row lists (8 columns each).

**Template-resolver helper + `TemplateManageable` boilerplate** (lines 116-153) — copy verbatim with renamed paths.

**Pitfall — race-ordering filename stability** (RESEARCH.md Pitfall 2): use iterator-counter not `dateTime`. Reference: `MatchResultsGraphicService.buildRaceRows` line 94 (`int raceNumber = 0; for (Race race : match.getRaces()) { if (race.getResults().isEmpty()) continue; raceNumber++; ... }`). Multipart-bundle in `DiscordPostService.postProvisionalScores` uses the same counter to derive `provisional-race-N.png`.

---

### `provisional-scores-render.html` (NEW, Thymeleaf graphic template)

**Analog:** `src/main/resources/templates/admin/match-results-render.html` (235 LOC — read in full)

**Header + font-embed pattern** (lines 1-50):
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
               font-family: 'Conthrax', -apple-system, sans-serif;
               display: flex; flex-direction: column; }
        .header { background: linear-gradient(180deg, #c0c0c0 0%, #a0a0a0 100%);
                  padding: 24px 96px; display: flex; justify-content: space-between;
                  align-items: center; min-height: 130px; }
```

**`match-card`-header structure** (lines 194-206):
```html
<div class="header">
    <div class="header-left">
        <div class="title">Community<br>Team Cup</div>
        <div class="year" th:text="${seasonYear}"></div>
    </div>
    <div class="header-center">
        <div class="scorecard-title">Match Results</div>     <!-- → "Provisional Scores" -->
        <div class="matchday" th:text="${matchdayName}"></div>
    </div>
    <div class="header-right">
        <img th:if="${ctcLogoBase64 != null}" th:src="${ctcLogoBase64}" alt="CTC">
    </div>
</div>
```

**Apply:** rename scorecard-title to `Provisional Scores`. Replace `<div class="main">` (lines 208-227) — match-results uses a centered 3-column flex (home-card | race-rows | away-card). Provisional needs **2 stacked team-blocks** (home top, away below) with **per-driver detail-rows** (Driver | Position | Quali | FL | Pts-Race | Pts-Quali | Pts-FL | Total) + Overall footer-row per block. Layout: top-half home team-card+name + 8-col table; bottom-half away team-card+name + 8-col table. Iteration via playwright-cli per [[feedback-graphic-pixel-positioning]] + [[feedback-graphic-design-iteration]] — User provides reference at `.screenshots/96-01/provisional-reference.png`.

**Diff scope hint:** keep `header` + `footer` blocks (lines 194-206, 229-233) **verbatim** (rename "Match Results" → "Provisional Scores"). Replace `.main` content (lines 208-227) with 2-team-block detail layout. Footer can drop or keep — User direction: "kombinieren mit Race Results" suggests keeping match-totals at bottom is acceptable.

---

### `DiscordPostService.postProvisionalScores(Match)` (EXTEND in-file)

**Analog (same file):** `DiscordPostService.postLineups` + `postRaceBundle` lines 237-265.

**Exact pattern to mirror** (lines 237-265):
```java
@Transactional
public DiscordPost postLineups(Match match) throws DiscordApiException {
    if (!matchHasCompleteLineups(match)) {
        throw new BusinessRuleException("Configure lineups for all races first");
    }
    return postRaceBundle(match, DiscordPostType.LINEUPS, "lineups-race-",
            race -> readRaceGraphic(lineupGraphicService.generateLineup(race)));
}

private DiscordPost postRaceBundle(
        Match match, DiscordPostType type, String filenamePrefix, RaceGraphicLoader loader)
        throws DiscordApiException {
    List<Race> races = match.getRaces();
    List<NamedAttachment> attachments = new ArrayList<>(races.size());
    try {
        for (int i = 0; i < races.size(); i++) {
            byte[] bytes = loader.load(races.get(i));
            attachments.add(new NamedAttachment(filenamePrefix + (i + 1) + ".png", bytes));
        }
    } catch (IOException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
    return postOrEdit(match.getDiscordChannelId(), match.getDiscordChannelWebhookUrl(),
            type, WebhookPayload.empty(), attachments, DiscordPostRef.match(match));
}
```

**Apply:** new `postProvisionalScores(Match)` with new pre-flight predicate `matchHasProvisionalData(Match m)` (≥1 race has results AND match not final). `postRaceBundle` is **NOT directly reusable** as-is because Provisional must filter races by `!r.getResults().isEmpty()` before bundling (PostLineups bundles all races); add a sibling helper OR inline the loop. Recommended: keep `postRaceBundle` for the all-race types (Settings/Lineups), inline the filtered-race loop for Provisional:
```java
@Transactional
public DiscordPost postProvisionalScores(Match match) throws DiscordApiException {
    if (!matchHasProvisionalData(match)) {
        throw new BusinessRuleException("Provisional needs at least one completed race");
    }
    List<NamedAttachment> attachments = new ArrayList<>();
    try {
        int raceNumber = 0;
        for (Race race : match.getRaces()) {
            if (race.getResults().isEmpty()) continue;
            raceNumber++;
            byte[] png = provisionalScoresGraphicService.generateProvisional(race);
            attachments.add(new NamedAttachment("provisional-race-" + raceNumber + ".png", png));
        }
    } catch (IOException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
    return postOrEdit(match.getDiscordChannelId(), match.getDiscordChannelWebhookUrl(),
            DiscordPostType.PROVISIONAL_SCORES, WebhookPayload.empty(), attachments,
            DiscordPostRef.match(match));
}

public boolean matchHasProvisionalData(Match match) {
    return !match.getRaces().isEmpty()
            && match.getRaces().stream().anyMatch(r -> !r.getResults().isEmpty());
}
```

**Diff scope hint:**
- **Plan 96-01 only:** add 2 new methods + inject `provisionalScoresGraphicService` (constructor + `@SuppressFBWarnings` justification-list update line 73-78).
- `DiscordPostType.PROVISIONAL_SCORES` enum constant must be added (check `org.ctc.discord.model.DiscordPostType`).
- The 6-arg `postOrEdit` is still in Phase 95 shape — Plan 96-01 does **NOT** touch `postOrEdit`. (Plan 96-03 does.)

---

### `MatchController.postProvisional` (EXTEND in-file)

**Analog (same file):** `MatchController.postMatchResults` lines 230-241.

```java
@PostMapping("/{id}/post-match-results")
public String postMatchResults(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postMatchResults(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Match results posted.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post match results");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post match results");
    }
    return "redirect:/admin/matches/" + id;
}
```

**Apply:** copy 1:1 to a new `@PostMapping("/{id}/post-provisional")` → `discordPostService.postProvisionalScores(...)`, action label `"Post provisional scores"`. The same `applyErrorFlash` helpers (lines 334-354) are already reusable as-is.

**Model attributes in `detail()` (lines 99-123):** add `model.addAttribute("provisionalPost", findMatchPost(match, DiscordPostType.PROVISIONAL_SCORES));` and `model.addAttribute("matchHasProvisionalData", discordPostService.matchHasProvisionalData(match));`.

---

### `match-detail.html` — Provisional buttons in `.discord-actions--posts` cluster (EXTEND in-file)

**Analog (same file):** lines 78-95 (Post Settings / disabled / Re-Post Settings triplet).

```html
<form th:if="${settingsPost == null and matchHasCompleteSettings}"
      th:action="@{/admin/matches/{id}/post-settings(id=${match.id})}"
      method="post" class="form-inline">
    <button type="submit" class="btn btn-primary btn-sm" data-testid="post-settings">
        Post Settings
    </button>
</form>
<span th:if="${settingsPost == null and not matchHasCompleteSettings}"
      class="btn btn-secondary btn-sm disabled"
      data-testid="post-settings-disabled"
      title="Configure settings for all races first">Post Settings</span>
<form th:if="${settingsPost != null}"
      th:action="@{/admin/matches/{id}/post-settings(id=${match.id})}"
      method="post" class="form-inline">
    <button type="submit" class="btn btn-secondary btn-sm" data-testid="repost-settings">
        Re-Post Settings
    </button>
</form>
```

**Apply:** copy 1:1 with substitution `settingsPost → provisionalPost`, `matchHasCompleteSettings → matchHasProvisionalData`, `/post-settings → /post-provisional`, data-testids `post-provisional / post-provisional-disabled / repost-provisional`, label "Post Provisional Scores" / "Re-Post Provisional Scores", disabled-tooltip "Provisional needs at least one completed race".

**Open question / discretion:** D-96-GRX-1b says Provisional cluster lives alongside Settings/Lineups; suggest placing the new triplet immediately before the Match-Results triplet (lines 116-133) since Provisional is the precursor to final Match-Results.

---

## PLAN 96-02 — Pattern Assignments (V13 + Discord-Config + DiscordForumService + Season-Edit Section)

### `V13__add_seasons_discord_threads_and_forum_webhooks.sql` (NEW migration)

**Analog:** `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` (15 LOC).

```sql
ALTER TABLE matches ADD COLUMN discord_channel_id VARCHAR(32);
ALTER TABLE matches ADD COLUMN discord_channel_webhook_url VARCHAR(500);
ALTER TABLE matches ADD COLUMN discord_teaser VARCHAR(2000);
ALTER TABLE matches ADD COLUMN stream_link VARCHAR(500);
ALTER TABLE matches ADD COLUMN lobby_host VARCHAR(100);
ALTER TABLE matches ADD COLUMN race_director VARCHAR(100);
ALTER TABLE matches ADD COLUMN streamer VARCHAR(100);
```

**Apply (RESEARCH Example 1):**
```sql
ALTER TABLE discord_global_config ADD COLUMN race_results_forum_webhook_url VARCHAR(500);
ALTER TABLE discord_global_config ADD COLUMN standings_forum_webhook_url VARCHAR(500);
ALTER TABLE seasons ADD COLUMN discord_race_results_thread_id VARCHAR(32);
ALTER TABLE seasons ADD COLUMN discord_standings_thread_id VARCHAR(32);
```

**Diff scope hint:** V10 header comments in original file include phase-references (`Phase 94 V10 D-13`); per CLAUDE.md No-Comment-Pollution that is a regression-mode now banned for new files. V13 must NOT include phase/plan references; conventional CLAUDE.md anchors only (`-- Compatible with H2 + MariaDB`). V8 (lines 1-4) shows the acceptable header shape — copy that, not V10.

---

### `DiscordGlobalConfig.java` — +2 webhook URL fields (EXTEND in-file)

**Analog (same file):** lines 20, 30-31.

```java
@ToString(exclude = {"announcementWebhookUrl"})
public class DiscordGlobalConfig extends BaseEntity {
    // ...
    @Column(name = "announcement_webhook_url", length = 500, nullable = false)
    private String announcementWebhookUrl = "";
```

**Apply:**
```java
@ToString(exclude = {"announcementWebhookUrl", "raceResultsForumWebhookUrl", "standingsForumWebhookUrl"})
// ...
@Column(name = "race_results_forum_webhook_url", length = 500)
private String raceResultsForumWebhookUrl = "";

@Column(name = "standings_forum_webhook_url", length = 500)
private String standingsForumWebhookUrl = "";
```

**Diff scope hint:** V13 columns are nullable per CONTEXT D-96-FOR-1; existing `announcementWebhookUrl` is `nullable = false DEFAULT ''`. Match the V13 ADD COLUMN shape (no `NOT NULL` / no `DEFAULT ''` in the column annotation) — see RESEARCH A10.

---

### `DiscordConfigForm.java` — +2 fields (EXTEND in-file)

**Analog (same file):** lines 15-23 (WEBHOOK_REGEX + announcementWebhookUrl).

```java
private static final String WEBHOOK_REGEX = "^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$";
private static final String WEBHOOK_MESSAGE = "Must be an empty string or a discord.com webhook URL";

@Size(max = 500)
@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
private String announcementWebhookUrl = "";
```

**Apply:** add fields `raceResultsForumWebhookUrl = ""` and `standingsForumWebhookUrl = ""` with **identical** `@Size(max=500)` + `@Pattern(regexp=WEBHOOK_REGEX, message=WEBHOOK_MESSAGE)` annotations. Match the in-file constant naming — DO NOT introduce a new `@URL` Jakarta validator (RESEARCH says `@URL` was suggested but the existing pattern reuses `@Pattern + WEBHOOK_REGEX`; stay consistent with what discord-config-form does today).

---

### `discord-config.html` — +2 form-groups (EXTEND in-file)

**Analog (same file):** lines 28-34 (announcementWebhookUrl form-group).

```html
<div class="form-group">
    <label for="announcementWebhookUrl">Announcement Webhook URL</label>
    <input type="text" id="announcementWebhookUrl" th:field="*{announcementWebhookUrl}"
           placeholder="https://discord.com/api/webhooks/…">
    <span th:if="${#strings.isEmpty(form.announcementWebhookUrl)}" class="badge-warning">not configured</span>
    <span th:errors="*{announcementWebhookUrl}" class="error-badge error-badge--auth"></span>
</div>
```

**Apply:** insert 2 new form-groups directly below this one, IDs `raceResultsForumWebhookUrl` + `standingsForumWebhookUrl`, labels "Race-Results Forum Webhook URL" + "Standings Forum Webhook URL". Same placeholder + badge + error pattern.

---

### `Thread.java` — +flags + thread_metadata (EXTEND DTO)

**Analog (same file):** current 8-line record.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Thread(String id, String name, @JsonProperty("parent_id") String parentId) {
}
```

**Apply (RESEARCH Example 4):**
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Thread(String id, String name, @JsonProperty("parent_id") String parentId,
                     int flags, @JsonProperty("thread_metadata") ThreadMetadata threadMetadata,
                     @JsonProperty("last_message_id") String lastMessageId) {
    public static final int FLAG_PINNED = 1 << 1;
    public boolean pinned() { return (flags & FLAG_PINNED) != 0; }
    public boolean archived() { return threadMetadata != null && threadMetadata.archived(); }
}
```

**Diff scope hint:** record-component addition reorders the canonical constructor — check callers (only the Jackson deserializer constructs it from JSON; existing Phase 95 callers don't `new Thread(...)`). Verify with `grep -rn "new Thread(" src/`. Also create `ThreadMetadata.java` (NEW DTO, sibling).

---

### `DiscordForumService.java` (NEW service)

**Analog A (file-shape):** `DiscordPostService.java` lines 48-106 (constructor + `@SuppressFBWarnings EI_EXPOSE_REP2` block).
**Analog B (REST orchestration):** `DiscordRestClient.listActiveThreads` + `listArchivedThreads` lines 94-108.

**Apply (RESEARCH Example 5):**
```java
@Slf4j
@Service
@RequiredArgsConstructor   // or @SuppressFBWarnings constructor if explicit injection needed
public class DiscordForumService {
    private final DiscordRestClient restClient;
    private final DiscordGlobalConfigService globalConfigService;

    public List<Thread> listThreads(String forumChannelId) throws DiscordApiException {
        String guildId = globalConfigService.getOrThrow().getGuildId();
        List<Thread> active = restClient.listActiveThreads(guildId).stream()
                .filter(t -> Objects.equals(t.parentId(), forumChannelId))
                .toList();
        List<Thread> archived = restClient.listArchivedThreads(forumChannelId);
        return Stream.concat(active.stream(), archived.stream())
                .sorted(THREAD_PICKER_ORDER)
                .toList();
    }

    private static final Comparator<Thread> THREAD_PICKER_ORDER =
            Comparator.comparing(Thread::pinned, Comparator.reverseOrder())
                    .thenComparing(Thread::archived, Comparator.naturalOrder())
                    .thenComparing(Thread::lastMessageId, Comparator.nullsLast(Comparator.reverseOrder()));
}
```

**Pitfall** (RESEARCH Pitfall 4): the `parentId`-filter on `listActiveThreads` is critical — `GET /guilds/{guildId}/threads/active` returns ALL guild threads not just forum.

---

### `Season.java` — +2 Discord-thread-ID fields (EXTEND entity)

**Analog (same file):** lines 17 (`@ToString(exclude=...)`), 26 (`@Column(nullable=false)`), 39-47 (other @Column fields).

**Apply (insertion site: between line 38 `private boolean active;` and line 39 `@OneToMany ... phases`):**
```java
@Column(name = "discord_race_results_thread_id", length = 32)
private String discordRaceResultsThreadId;

@Column(name = "discord_standings_thread_id", length = 32)
private String discordStandingsThreadId;
```

**Diff scope hint:** `Season.@ToString(exclude=...)` line 17 does NOT need new excludes — thread-IDs are not secret. `getDisplayLabel()` and other convenience-methods at lines 73-180 stay unchanged. Re-check `EntityGraph`/lazy-load implications: thread-IDs are scalars on `seasons` so no fetch-strategy concern.

---

### `SeasonForm.java` — +2 fields (EXTEND form-DTO)

**Analog (same file):** existing fields lines 14-25.

**Apply:** add `private String discordRaceResultsThreadId;` + `private String discordStandingsThreadId;` (no `@NotBlank` — optional per CONTEXT). Bean-validation `@Pattern(regexp=DiscordSnowflake.PATTERN, message=DiscordSnowflake.MESSAGE)` to match other snowflake fields in `DiscordConfigForm` (lines 18, 25, 28).

---

### `SeasonController.linkThread` / `unlinkThread` (EXTEND in-file)

**Analog (cross-file):** `MatchController.moveToArchive` lines 277-301 (modal-confirm endpoint + DiscordRestClient call + flash-attribute messaging).

```java
@PostMapping("/{id}/move-to-archive")
public String moveToArchive(@PathVariable UUID id,
                            @RequestParam(required = false) String categoryId,
                            RedirectAttributes redirectAttributes) {
    try {
        Match match = matchService.findById(id);
        if (match.getDiscordChannelId() == null) {
            throw new BusinessRuleException("Match has no Discord channel to archive.");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new DiscordCategoryFullException(...);
        }
        discordRestClient.modifyChannel(match.getDiscordChannelId(),
                new ChannelModifyRequest(null, categoryId));
        matchService.markChannelArchived(id);
        redirectAttributes.addFlashAttribute("successMessage", "Channel moved to archive.");
    } catch (BusinessRuleException e) { /* flash */ }
      catch (DiscordApiException e) { applyErrorFlash(redirectAttributes, e, "Move to Archive"); }
    return "redirect:/admin/matches/" + id;
}
```

**Apply:**
```java
@PostMapping("/{id}/link-thread")
public String linkThread(@PathVariable UUID id,
                         @RequestParam String threadId,
                         @RequestParam String type, // "race-results" | "standings"
                         RedirectAttributes redirectAttributes) {
    try {
        if ("race-results".equals(type)) {
            seasonManagementService.linkRaceResultsThread(id, threadId);
        } else if ("standings".equals(type)) {
            seasonManagementService.linkStandingsThread(id, threadId);
        } else {
            throw new BusinessRuleException("Unknown thread type: " + type);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Thread linked.");
    } catch (BusinessRuleException e) { /* flash */ }
    return "redirect:/admin/seasons/" + id + "/edit";
}
```
**Note:** `SeasonController` does NOT currently inject `discordRestClient` or `DiscordApiException` handling — Link/Unlink are pure local-DB mutations. The Modal-Picker GETs the thread-list via a separate endpoint (consider `@GetMapping("/{id}/threads")` returning JSON OR pre-loading on the edit-page Model — discretion).

---

### `season-form.html` — Discord-Integration section + Link-Modal (EXTEND in-file)

**Analog A (section card):** season-form.html lines 84-125 (the "Car Pool" `<div class="card mt-md" id="…">` cluster).
**Analog B (modal):** match-detail.html lines 191-221 (Archive Modal — modal-overlay + modal-body + radio-list + Confirm/Cancel).

```html
<div th:if="${seasonForm.id != null}" class="card mt-md" id="carPool">
    <h2 th:text="'Car Pool (' + ${season.cars.size()} + ')'">Car Pool</h2>
    <!-- form content -->
</div>
```

```html
<div id="archiveModal" class="modal-overlay">
    <div class="modal-body modal-body--md">
        <h3 class="modal-title">Move Channel to Archive</h3>
        <form th:action="@{/admin/matches/{id}/move-to-archive(id=${match.id})}" method="post">
            <div th:each="cat,iter : ${archiveCategories}" class="form-check">
                <input type="radio" th:id="'cat-' + ${iter.index}" name="categoryId"
                       th:value="${cat.id}"
                       th:checked="${defaultSelectionId != null and defaultSelectionId.equals(cat.id)}">
                <label th:for="'cat-' + ${iter.index}"
                       th:text="${cat.name} + ' — ' + ${cat.currentChannelCount} + '/50'"></label>
            </div>
            <div class="actions">
                <button type="submit" class="btn btn-primary">Confirm</button>
                <button type="button" class="btn btn-secondary"
                        onclick="document.getElementById('archiveModal').style.display='none'">Cancel</button>
            </div>
        </form>
    </div>
</div>
```

**Apply:** new card `<div class="card mt-md" id="discordIntegration">` (after Car Pool / Track Pool blocks) with 2 thread-linker widgets — current-link badge + "Link existing..." button + "Unlink" button per type. Modal: one shared modal `id="linkThreadModal"` with thread-list radio-set; submit posts to `/admin/seasons/{id}/link-thread` with `threadId` + `type` hidden inputs. The pinned thread's radio gets `th:checked="${thread.pinned()}"` for auto-pre-select (D-96-FOR-2 / RESEARCH Pitfall 5).

**Open question / discretion:** D-96-FOR-2b — Form-inline (this pattern) vs dedicated sub-page. Recommendation: form-inline section (≤ 4 form-groups added) — keeps the Edit-page atomic, matches season-form.html's existing 3-card layout (Edit-Form / Teams / Pools).

---

### `SeasonMixIn.java` — +2 thread-ID fields (EXTEND MixIn)

**Analog (same file):** lines 27-29 (`@JsonIgnoreProperties` list).

```java
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "phases", "seasonDrivers", "seasonTeams",
        "displayLabel", "teams", "matchdays", "activeTeams", "eligibleTeams"})
public abstract class SeasonMixIn {
```

**Apply:** **default recommendation per RESEARCH D-96-07 = include thread-IDs in export** (Saison-Identity). Therefore **NO change** to `@JsonIgnoreProperties` — they auto-export via Lombok-generated getters since they're not in the ignore-list. Confirm: `BackupSchema.SCHEMA_VERSION` stays 2 (Jackson ignoreUnknown handles older backups). Verified per RESEARCH A10.

**Open question:** if Operator decides webhook-URL-export is acceptable risk after all → that's a `DiscordGlobalConfigMixIn` decision (Plan 96-02 separate task) not a `SeasonMixIn` decision.

---

### `DiscordGlobalConfigMixIn.java` (NEW MixIn, or N/A if absent)

**Open question (RESEARCH Open Question 3):** does `DiscordGlobalConfig` currently participate in `EXPORT_ORDER`? Plan 96-02 first task: `grep -rn "DiscordGlobalConfig" src/main/java/org/ctc/backup/` — if absent, no new MixIn needed (operator restores webhook-URLs out-of-band). If present, follow analog:

**Analog:** `SeasonMixIn.java` lines 1-37.

```java
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "announcementWebhookUrl", "raceResultsForumWebhookUrl", "standingsForumWebhookUrl"})
public abstract class DiscordGlobalConfigMixIn {
}
```

**Apply:** secret-discipline default — ignore all 3 webhook-URL fields. Operator pastes them post-restore. Channel-IDs + Guild-ID are exported (they're identifiers, not secrets).

---

## PLAN 96-03 — Pattern Assignments (DiscordPostService SeasonRef/RaceRef + ?thread_id= + Auto-Unarchive + Race-Detail Button)

### `DiscordPostService.postOrEdit` — Sealed-Switch + Auto-Unarchive (EXTEND in-file)

**Analog (same file, REPLACE):** lines 276-326 (current `postOrEdit` 6-arg signature + UnsupportedOperationException guard).

```java
@Transactional
public DiscordPost postOrEdit(
        String channelId, String webhookUrl, DiscordPostType type,
        WebhookPayload payload, List<NamedAttachment> attachments,
        DiscordPostRef ref) throws DiscordApiException {
    hostValidator.requireAllowed(webhookUrl);
    if (!(ref instanceof DiscordPostRef.MatchRef)) {
        throw new UnsupportedOperationException(
                "DiscordPostService.postOrEdit currently supports MatchRef only ...");
    }
    WebhookCredentials creds = parseWebhookUrl(webhookUrl);
    Optional<DiscordPost> existing = discordPostRepository
            .findByChannelIdAndPostTypeAndMatchId(channelId, type, ref.matchId());
    LocalDateTime now = LocalDateTime.now(clock);
    if (existing.isPresent()) {
        DiscordPost row = existing.get();
        if (attachments.isEmpty()) {
            webhookClient.editMessage(webhookUrl, row.getMessageId(), payload);
        } else {
            webhookClient.editMessageWithAttachments(webhookUrl, row.getMessageId(), payload, attachments);
            row.setAttachmentsReplacedAt(now);
        }
        // save + return
    }
    WebhookMessage msg = attachments.isEmpty()
            ? webhookClient.execute(webhookUrl, payload)
            : webhookClient.executeMultipart(webhookUrl, payload, attachments);
    // build DiscordPost row, ref.applyTo(row), save, return
}
```

**Apply (RESEARCH Example 2):** 7-arg overload + sealed-switch dispatch + `unarchiveIfArchived` hook. The 6-arg overload stays as a delegating method (5-arg call passes `threadId=null`). See RESEARCH Pitfall 1 — use `m.id()` not `m.matchId()` inside the switch.

```java
@Transactional
public DiscordPost postOrEdit(
        String channelId, String webhookUrl, DiscordPostType type,
        WebhookPayload payload, List<NamedAttachment> attachments,
        DiscordPostRef ref, @Nullable String threadId) throws DiscordApiException {
    hostValidator.requireAllowed(webhookUrl);
    if (threadId != null) unarchiveIfArchived(threadId);
    WebhookCredentials creds = parseWebhookUrl(webhookUrl);
    Optional<DiscordPost> existing = switch (ref) {
        case DiscordPostRef.MatchRef m    -> discordPostRepository.findByChannelIdAndPostTypeAndMatchId(channelId, type, m.id());
        case DiscordPostRef.RaceRef r     -> discordPostRepository.findByChannelIdAndPostTypeAndRaceId(channelId, type, r.id());
        case DiscordPostRef.SeasonRef s   -> discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id());
        case DiscordPostRef.MatchdayRef d -> discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.id());
    };
    LocalDateTime now = LocalDateTime.now(clock);
    if (existing.isPresent()) {
        DiscordPost row = existing.get();
        if (attachments.isEmpty()) webhookClient.editMessage(webhookUrl, row.getMessageId(), payload, threadId);
        else { webhookClient.editMessageWithAttachments(webhookUrl, row.getMessageId(), payload, attachments, threadId);
               row.setAttachmentsReplacedAt(now); }
        return discordPostRepository.save(row);
    }
    WebhookMessage msg = attachments.isEmpty()
            ? webhookClient.execute(webhookUrl, payload, threadId)
            : webhookClient.executeMultipart(webhookUrl, payload, attachments, threadId);
    // ... build row, ref.applyTo(row), save, return
}

@Transactional
public DiscordPost postOrEdit(
        String channelId, String webhookUrl, DiscordPostType type,
        WebhookPayload payload, List<NamedAttachment> attachments,
        DiscordPostRef ref) throws DiscordApiException {
    return postOrEdit(channelId, webhookUrl, type, payload, attachments, ref, null);
}

private void unarchiveIfArchived(String threadId) throws DiscordApiException {
    Channel thread = discordRestClient.fetchChannel(threadId);
    ThreadMetadata md = thread.threadMetadata();   // verify Channel has thread_metadata accessor
    if (md != null && md.archived()) {
        log.info("Unarchiving forum thread {} before post", threadId);
        discordRestClient.modifyChannel(threadId, ChannelModifyRequest.unarchive());
    }
}
```

**Diff scope hint:**
- `instanceof`-guard at lines 286-290 is REPLACED with sealed-switch (Java 25 exhaustive switch); the `UnsupportedOperationException` line is removed.
- All 5 message-method callsites (lines 299, 301, 310, 311) gain a `threadId` parameter pass-through.
- New `discordRestClient` field + constructor injection (current constructor lines 79-106 has 13 dependencies; adding `DiscordRestClient` makes it 14 — update `@SuppressFBWarnings` justification-list lines 73-78).
- New `Channel.threadMetadata()` accessor — verify `Channel.java` has it; if not, extend `Channel` similarly to `Thread`. Plan 96-03 audit task.

---

### `DiscordWebhookClient` — ?thread_id= overloads (EXTEND in-file)

**Analog (same file):** `executeMultipart` lines 63-105.

```java
public WebhookMessage executeMultipart(
        String webhookUrl, WebhookPayload payload, List<NamedAttachment> attachments)
        throws DiscordApiException {
    if (attachments.size() > MAX_ATTACHMENTS) {
        throw new IllegalArgumentException("...");
    }
    if (attachments.isEmpty()) return execute(webhookUrl, payload);
    hostValidator.requireAllowed(webhookUrl);
    String payloadJson = ...;
    MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
    parts.add("payload_json", new HttpEntity<>(payloadJson, ...));
    for (int i = 0; i < attachments.size(); i++) { ... parts.add("files[" + i + "]", ...); }
    return execute(() -> forWebhookUrl(webhookUrl)
            .post()
            .uri(uriBuilder -> uriBuilder.path("").queryParam("wait", "true").build())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(WebhookMessage.class));
}
```

**Apply (RESEARCH Pattern 4):**
```java
public WebhookMessage executeMultipart(String webhookUrl, WebhookPayload payload,
        List<NamedAttachment> attachments, @Nullable String threadId) throws DiscordApiException {
    // ... existing validation + parts assembly ...
    return execute(() -> forWebhookUrl(webhookUrl)
        .post()
        .uri(uriBuilder -> {
            uriBuilder.path("").queryParam("wait", "true");
            if (threadId != null) uriBuilder.queryParam("thread_id", threadId);
            return uriBuilder.build();
        })
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(WebhookMessage.class));
}

public WebhookMessage executeMultipart(String webhookUrl, WebhookPayload payload,
        List<NamedAttachment> attachments) throws DiscordApiException {
    return executeMultipart(webhookUrl, payload, attachments, null);  // 3-arg delegates
}
```

**Diff scope hint:** Variant A (D-96-FOR-3a — overload, NOT WebhookTarget-wrapper). All 4 methods get the same treatment: `execute`, `executeMultipart`, `editMessage`, `editMessageWithAttachments`. The 3-arg/4-arg variants delegate to the new 4-arg/5-arg with `threadId=null`. Phase-95 callsites unchanged. Pitfall 6 (RESEARCH) — PATCH-multipart `attachments`-JSON descriptor stays in `payload_json`, ?thread_id= only changes the URL.

---

### `DiscordPostRef.java` — confirm permits (NO CHANGE NEEDED)

**Status:** All 4 permits (`MatchRef`, `MatchdayRef`, `RaceRef`, `SeasonRef`) are already declared and fully implemented (lines 42-148). Phase 95 D-95-12 staged them; Plan 96-03 only **uses** them.

**Diff scope hint:** the only change in this file would be **none** — the permits already have correct `applyTo` setters (`row.setRaceId(id)` line 99, `row.setSeasonId(id)` line 126). RESEARCH Pitfall 1: prefer `r.id()` style in the sealed-switch (cleaner than the `r.raceId()` interface-method) — RESEARCH recommendation locked.

---

### `ChannelModifyRequest.java` — +archived field + unarchive() factory (EXTEND)

**Analog (same file):** current 10-LOC 2-field record.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelModifyRequest(
        String name,
        @JsonProperty("parent_id") String parentId) {
}
```

**Apply (RESEARCH Example 3):**
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelModifyRequest(
        String name,
        @JsonProperty("parent_id") String parentId,
        Boolean archived) {
    public ChannelModifyRequest(String name, String parentId) { this(name, parentId, null); }
    public static ChannelModifyRequest unarchive() { return new ChannelModifyRequest(null, null, Boolean.FALSE); }
}
```

**Diff scope hint:** preserve existing 2-arg constructor — `MatchController.moveToArchive` line 290-291 calls `new ChannelModifyRequest(null, categoryId)`. Adding the canonical 3-arg constructor as a record breaks call-sites — keep the explicit 2-arg compact constructor delegating to the 3-arg. `Boolean` not `boolean` so `@JsonInclude(NON_NULL)` omits the field for non-unarchive PATCHes.

---

### `DiscordPostRepository.java` — 3 new derived queries (EXTEND)

**Analog (same file):** line 13.

```java
public interface DiscordPostRepository
        extends JpaRepository<DiscordPost, Long>, JpaSpecificationExecutor<DiscordPost> {
    Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchId(
            String channelId, DiscordPostType postType, UUID matchId);
}
```

**Apply:**
```java
Optional<DiscordPost> findByChannelIdAndPostTypeAndRaceId(
        String channelId, DiscordPostType postType, UUID raceId);

Optional<DiscordPost> findByChannelIdAndPostTypeAndSeasonId(
        String channelId, DiscordPostType postType, UUID seasonId);

Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchdayId(
        String channelId, DiscordPostType postType, UUID matchdayId);
```

**Diff scope hint:** Spring-Data derived queries — names must match `DiscordPost` field names verbatim (`raceId`, `seasonId`, `matchdayId` — all lines 51/54/57 of `DiscordPost.java` verified). MatchdayRef is Phase 97 but the derived-query is a no-op until then (deriving the method costs nothing — declare all 3 at once for sealed-switch exhaustiveness).

---

### `ResultsGraphicService.generateResultsBytes(Race) → byte[]` (EXTEND in-file)

**Analog A (same file existing):** `ResultsGraphicService.generateResults(Race)` lines 36-90 (returns String uploads-URL after writing to disk).
**Analog B (cross-file pattern):** `MatchResultsGraphicService.generateMatchResults` lines 80-88 (createTempFile → renderScreenshot → readAllBytes → deleteIfExists).

```java
// MatchResultsGraphicService lines 80-88
Path tempFile = Files.createTempFile("match-results-", ".png");
try {
    renderScreenshot(html, tempFile);
    log.info("Generated match results graphic for match {}", match.getId());
    return Files.readAllBytes(tempFile);
} finally {
    Files.deleteIfExists(tempFile);
}
```

**Apply (RESEARCH Claude's-Discretion: shared-private-helper variant):** extract a private `String buildHtml(Race race)` helper from existing `generateResults`. Keep `generateResults` writing to disk + returning uploads-URL (unchanged contract per CONTEXT D-96-FOR-3d). Add a sibling:

```java
public byte[] generateResultsBytes(Race race) throws IOException {
    String html = buildHtml(race);
    Path tempFile = Files.createTempFile("race-result-", ".png");
    try {
        renderScreenshot(html, tempFile);
        return Files.readAllBytes(tempFile);
    } finally {
        Files.deleteIfExists(tempFile);
    }
}

private String buildHtml(Race race) throws IOException {
    // existing validation + context-building logic from generateResults lines 37-80
    return renderTemplate(ctx);
}
```

**Diff scope hint:** existing `generateResults` lines 36-90 refactor: extract lines 37-80 into `buildHtml`; keep lines 82-89 (raceDir / outputFile / renderScreenshot / return-uploads-URL) on `generateResults`. Add new `generateResultsBytes` (≤ 12 LOC). Coverage-Sweep on `ResultsGraphicService` stays green (existing tests cover `generateResults`; add 1 new test for `generateResultsBytes`).

---

### `RaceController.postRaceResultToForum` (EXTEND in-file)

**Analog (cross-file):** `MatchController.postMatchResults` lines 230-241.

**Apply:**
```java
@PostMapping("/{id}/post-race-result-to-forum")
public String postRaceResultToForum(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postRaceResultToForumThread(raceService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Race result posted to forum-thread.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post race result to forum");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post race result to forum");
    }
    return "redirect:/admin/races/" + id;
}
```

**Diff scope hint:** `RaceController` does NOT currently inject `DiscordPostService` or have `applyErrorFlash` helpers — must add both (copy `applyErrorFlash` 2 overloads verbatim from `MatchController` lines 334-354). New constructor-arg `DiscordPostService discordPostService`. Pre-flight predicates (D-96-FOR-3c) calculated in `RaceController.detail` model:
- `canPostRaceResultToForum` = race-results-not-empty AND season.discordRaceResultsThreadId != null AND globalConfig.raceResultsForumWebhookUrl != null
- Tooltip-message string per failing predicate (3 distinct)

---

### `race-detail.html` — NEW Discord-Actions cluster (EXTEND in-file)

**Analog A (cross-file):** match-detail.html lines 55-153 (entire `.discord-actions--posts` cluster).
**Analog B (in-file):** race-detail.html lines 28-29 (Generate-Lineup-Graphic form + button — shows the form-action + btn-primary pattern already in race-detail).

```html
<!-- match-detail.html lines 55, 78-95 (Settings cluster) -->
<div class="discord-actions discord-actions--posts" th:if="${match.discordChannelId != null}">
    <form th:if="${settingsPost == null and matchHasCompleteSettings}"
          th:action="@{/admin/matches/{id}/post-settings(id=${match.id})}"
          method="post" class="form-inline">
        <button type="submit" class="btn btn-primary btn-sm" data-testid="post-settings">
            Post Settings
        </button>
    </form>
    <span th:if="${settingsPost == null and not matchHasCompleteSettings}"
          class="btn btn-secondary btn-sm disabled"
          data-testid="post-settings-disabled"
          title="Configure settings for all races first">Post Settings</span>
    <form th:if="${settingsPost != null}" ...>
        <button ...>Re-Post Settings</button>
    </form>
</div>
```

**Apply:** new `<div class="card"><h2>Discord Actions</h2><div class="discord-actions discord-actions--posts">…</div></div>` block at top of race-detail.html (after toolbar). Single triplet of (Post / Disabled / Re-Post) for "Race Result to Forum-Thread". Model attribute names: `raceResultsForumPost`, `canPostRaceResultToForum`, `forumPostDisabledReason` (3 distinct tooltip-strings per failing pre-flight).

**Diff scope hint:** Phase 95 introduced `.discord-actions--posts` cluster on Match-Detail; Phase 96 introduces it on Race-Detail for the first time. CSS class is already available (admin.css line 214-227 covers `.discord-actions` + responsive-wrap). No new CSS needed for the cluster itself. The new button label "Post Race Result" (active state) and "Re-Post Race Result" (after row exists) follow the same label-convention as Match-Results / Settings / Lineups.

---

### `admin.css` — additive in-milestone polish (EXTEND in-file)

**Analog (same file):** lines 211-227 (`.discord-actions` flex+responsive-wrap), lines 1124-1141 (`.modal-overlay` + `.modal-body`).

**Apply (per [[feedback-in-milestone-polish]]):** if a new CSS class is needed for the Season-Edit Discord-Integration section (e.g. `.thread-linker-widget` flex-row of badge + buttons), add it inline in Plan 96-02; if a `.btn-provisional` variant emerges from playwright-cli iteration, add it inline in Plan 96-01. **Do NOT defer CSS adds to Phase 98 polish** — UI/UX-Schulden im selben Milestone schließen.

**Diff scope hint:** the Plan-96-02 `linkThreadModal` reuses the **existing** `.modal-overlay` / `.modal-body` / `.modal-body--md` / `.modal-title` classes verbatim — zero CSS additions for the modal itself. Style only what the Discord-Integration section needs that's not already covered.

---

## Shared Patterns

### Pattern S1: Sealed-`DiscordApiException` Error Flash Handler

**Source:** `MatchController.applyErrorFlash(RedirectAttributes, DiscordApiException, String)` lines 334-354.

```java
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    log.warn("{} failed: category={}, exception={}, cause={}", action, category, ...);
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}

private void applyErrorFlash(RedirectAttributes ra, BusinessRuleException e, String action) {
    log.warn("{} failed: category=data-incomplete, message={}", action, e.getMessage());
    ra.addFlashAttribute("errorMessage", e.getMessage());
    ra.addFlashAttribute("errorCategory", "data-incomplete");
}
```

**Apply to:** All controller endpoints in `MatchController` (existing — Plan 96-01 adds `postProvisional`), `SeasonController` (Plan 96-02 — new methods; if no DiscordApiException possible because Link-Thread is pure DB, only the BusinessRuleException overload is needed), `RaceController` (Plan 96-03 — copy both overloads from MatchController lines 334-354). Per RESEARCH Anti-Pattern: NEVER echo `e.getMessage()` directly — use the whitelisted enum-mapped messages.

---

### Pattern S2: WireMock IT Class Setup (Plan 96-01/02/03 ITs)

**Source:** `DiscordPostServiceMatchResultsIT.java` lines 44-66 (verified — RESEARCH section identifies this as the canonical template).

```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostServiceMatchResultsIT {
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
    // ...
}
```

**Apply to:** All new `*IT.java` test-classes from D-96-09:
- `DiscordPostServiceProvisionalScoresIT` (Plan 96-01)
- `MatchControllerProvisionalPostIT` (Plan 96-01)
- `DiscordForumServiceIT` (Plan 96-02)
- `SeasonControllerLinkThreadIT` (Plan 96-02)
- `DiscordPostServiceForumThreadIT` (Plan 96-03)
- `DiscordPostServiceRefBranchesIT` (Plan 96-03)
- `DiscordWebhookClientThreadIdIT` (Plan 96-03)
- `RaceControllerPostRaceResultToForumIT` (Plan 96-03)

**Tag invariant:** every `*IT.java` MUST carry `@Tag("integration")` per CLAUDE.md § Test Categorization — without it the file runs under Surefire's wrong fork config and races on shared state.

---

### Pattern S3: Mockito-Only Unit Test Setup

**Source:** `MatchResultsGraphicServiceTest.java` lines 1-28.

```java
class MatchResultsGraphicServiceTest {
    private final ScoringService scoringService = mock(ScoringService.class);
    @TempDir Path tempDir;
    private MatchResultsGraphicService createService() {
        return new MatchResultsGraphicService(null, scoringService, tempDir.toString());
    }
    // helper builders + tests using @Test
}
```

**Apply to:**
- `ProvisionalScoresGraphicServiceTest` (Plan 96-01)
- `DiscordForumServiceTest` (Plan 96-02)
- `DiscordPostServiceRefBranchesTest` (Plan 96-03)

**Tag invariant:** untagged (Mockito-only unit tests run under Surefire by default). `@Tag("integration")` is explicitly NOT applied here.

**Test-naming convention:** `givenContext_whenAction_thenExpectedResult()` BDD-style per CLAUDE.md § Development Approach. Body uses `// given` / `// when` / `// then` block comments.

---

### Pattern S4: E2E Playwright Test Class (Mobile-Sweep variants)

**Source:** `MatchDetailMatchResultsButtonE2ETest.java` lines 1-40 (verified).

```java
@Tag("e2e")
class MatchDetailMatchResultsButtonE2ETest extends PlaywrightConfig {
    @Autowired DiscordPostRepository discordPostRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired TestHelper helper;

    @BeforeEach void setUp() {
        setupPage();
        discordPostRepository.deleteAll();
    }
    @AfterEach void tearDown() {
        teardownPage();
        discordPostRepository.deleteAll();
    }
    // tests using Playwright's Page API
}
```

**Apply to:**
- `MatchDetailProvisionalButtonsE2ETest` → package `org.ctc.e2e.discord.posts` (Plan 96-01)
- `SeasonEditDiscordSectionE2ETest` → package `org.ctc.e2e.discord.forum` (Plan 96-02)
- `RaceDetailForumPostButtonE2ETest` → package `org.ctc.e2e.discord.forum` (Plan 96-03)

**Tag invariant:** `@Tag("e2e")` REQUIRED — Failsafe-E2E runs under `-Pe2e` profile only.

**Test-data isolation invariant (CLAUDE.md):** prefix all test entities (`Test_Provisional_*`, `T-FORUM`, etc.) — never use real seasons/teams for E2E.

---

### Pattern S5: Modal-Show/Hide via Inline JS (Phase-94 D-94-06 pattern)

**Source:** `match-detail.html` lines 46-52 (open trigger) + 191-221 (modal-body) + 217 (cancel).

```html
<!-- Open trigger -->
<button onclick="document.getElementById('archiveModal').style.display='flex'">
    Move to Archive
</button>

<!-- Modal-body -->
<div id="archiveModal" class="modal-overlay">
    <div class="modal-body modal-body--md">
        <h3 class="modal-title">…</h3>
        <form th:action="@{…}" method="post">
            <!-- radio-set + Confirm/Cancel actions -->
            <button type="button" class="btn btn-secondary"
                    onclick="document.getElementById('archiveModal').style.display='none'">Cancel</button>
        </form>
    </div>
</div>
```

**Apply to:** Plan 96-02 `linkThreadModal` on `season-form.html`. Two modal-buttons on the Season-Edit page (1 per thread-type) trigger the same modal-id with different hidden-input `type` ("race-results" / "standings"); Confirm POSTs to `/admin/seasons/{id}/link-thread`. **No client-side JS framework** — inline `onclick="…style.display='flex'"` is the canonical SSR-modal pattern per Phase 94.

---

### Pattern S6: V13 Flyway File-Header Comment Convention

**Source:** `V8__discord_global_config.sql` lines 1-4.

```sql
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm Jakarta-Validation owns the
-- snowflake/webhook regex contract instead of the DB schema.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").
```

**Apply to:** V13 file's header. **Anti-Pattern (V10 header lines 1-2):** "`Phase 94 V10 D-13: extend matches table…`" — phase/plan/task references are banned by CLAUDE.md § No Comment Pollution; rotate badly with phase renumbers. V13's header must follow V8's wording exactly (compatibility-note + no-mutate-note), not V10's wording. RESEARCH ratifies this.

---

## Cross-Cutting CSS / Inline-Style Discipline

Per CLAUDE.md § No Inline Styles and [[feedback-no-inline-styles]] + [[feedback-in-milestone-polish]]:

- **NEVER** add `style="…"` on `.btn` elements; always use CSS classes from `admin.css` (`btn-xs`, `btn-sm`, `btn-lg`, `btn-tab`). Re-use existing `.discord-actions` / `.discord-actions--posts` / `.modal-overlay` / `.modal-body` / `.modal-body--md` / `.modal-title` classes verbatim.
- **DO** add new CSS classes inline in the relevant plan (`.btn-provisional` variant if needed in Plan 96-01; `.thread-linker-widget` / `.discord-integration-section` if needed in Plan 96-02). Do **NOT** defer to a Phase 98 polish sweep.
- When refactoring template inline-styles to CSS classes, always check `<script>`-blocks for `element.className = '…'` assignments — JavaScript-set class strings must be updated in lockstep.
- Mobile-Sweep per Plan-close: `playwright-cli` capture at 375 px viewport (`.screenshots/96-NN/…-mobile.png`); RESEARCH Open-Question 2 surfaces the Season-Edit `.card`-overflow risk (out-of-scope-fix per CONTEXT — surface only, fix lands in Phase 98).

---

## Test-Class Analogs (consolidated)

| New Test Class | Plan | Tag | Analog | Where (file:line) |
|---|---|---|---|---|
| `ProvisionalScoresGraphicServiceTest` | 96-01 | untagged | `MatchResultsGraphicServiceTest` | `src/test/java/org/ctc/admin/service/MatchResultsGraphicServiceTest.java` lines 1-28 |
| `DiscordPostServiceProvisionalScoresIT` | 96-01 | integration | `DiscordPostServiceMatchResultsIT` | `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java` lines 44-80 |
| `MatchControllerProvisionalPostIT` | 96-01 | integration | (Phase 95 Match-Controller IT precedent) | `MatchControllerPost*IT.java` (Phase-95 sibling files) |
| `MatchDetailProvisionalButtonsE2ETest` | 96-01 | e2e | `MatchDetailMatchResultsButtonE2ETest` | `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java` |
| `DiscordForumServiceTest` | 96-02 | untagged | Mockito-pattern from `MatchResultsGraphicServiceTest` | n/a (new shape) |
| `DiscordForumServiceIT` | 96-02 | integration | `DiscordPostServiceMatchResultsIT` | shared `WireMockExtension` setup pattern |
| `SeasonControllerLinkThreadIT` | 96-02 | integration | Phase 95 controller-IT precedent | n/a — new endpoint |
| `SeasonEditDiscordSectionE2ETest` | 96-02 | e2e | `ArchiveModalE2ETest` | `src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java` (modal-pattern) |
| `DiscordPostServiceRefBranchesTest` | 96-03 | untagged | Mockito-only sealed-switch coverage | n/a (new shape) |
| `DiscordPostServiceForumThreadIT` | 96-03 | integration | `DiscordPostServiceMatchResultsIT` | shared `WireMockExtension` setup pattern |
| `DiscordPostServiceRefBranchesIT` | 96-03 | integration | `DiscordPostServiceMatchResultsIT` | shared |
| `DiscordWebhookClientThreadIdIT` | 96-03 | integration | Phase 93 `DiscordWebhookClientIT` precedent | (verify file exists) |
| `RaceControllerPostRaceResultToForumIT` | 96-03 | integration | `MatchControllerPost*IT.java` (Phase-95) | role-match |
| `RaceDetailForumPostButtonE2ETest` | 96-03 | e2e | `MatchDetailMatchResultsButtonE2ETest` | shared E2E pattern |

---

## No Analog Found

| File | Role | Data Flow | Reason | Fallback |
|---|---|---|---|---|
| (none) | — | — | All 22 production files have an in-repo analog with ≥ role-match quality | RESEARCH section "Key insight: Phase 96 should be ~80% reuse and ~20% additive code" — confirmed by this mapping |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/admin/service/` (graphic services)
- `src/main/java/org/ctc/discord/` (Phase 93-95 Discord package, all sub-packages)
- `src/main/java/org/ctc/domain/model/` (Season entity)
- `src/main/java/org/ctc/admin/controller/` (Match/Race/Season controllers)
- `src/main/java/org/ctc/admin/dto/` (form-DTOs)
- `src/main/java/org/ctc/backup/serialization/` (MixIns)
- `src/main/resources/db/migration/` (V8/V10/V11/V12 precedents)
- `src/main/resources/templates/admin/` (match-detail / match-results-render / season-form / discord-config / race-detail)
- `src/main/resources/static/admin/css/admin.css` (discord-actions + modal styles)
- `src/test/java/org/ctc/admin/service/` (graphic-service unit tests)
- `src/test/java/org/ctc/discord/service/` (WireMock-IT precedents)
- `src/test/java/org/ctc/e2e/discord/` + `org/ctc/e2e/discord/posts/` (E2E precedents)

**Files scanned:** 26 main + 6 test analog files
**Pattern extraction date:** 2026-05-23
