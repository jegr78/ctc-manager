# Phase 110: Lobby Settings Graphic - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** 12 new/modified files
**Analogs found:** 12 / 12

---

## Drift Report vs. CONTEXT.md Claims

| Claim | Verified Status |
|-------|----------------|
| `TemplateEditorController` 3× `Map.of(...)` each with exactly 10 entries (L22-59) | **CONFIRMED.** `TEMPLATE_TYPE_TO_BEAN` L22-33, `TEMPLATE_TYPE_TO_ATTR` L35-46, `TEMPLATE_TYPE_TO_LABEL` L48-59 — all exactly 10 entries. Build-breaker on 11th entry is real. |
| `RaceGraphicService.generateSettings` ~L38-40 | **CONFIRMED.** L38-40 exactly. |
| `RaceController.generateSettings` ~L318-327 | **CONFIRMED.** L318-327 exactly. |
| `RaceService.canGenerateSettings` ~L143 | **CONFIRMED.** L143 (return in `new RaceDetailData(...)` call at L138-146). |
| `race-detail.html` disabled-with-hint button block ~L52-61 | **CONFIRMED.** L51-61 (Settings Button comment on L51). |
| `DiscordPostService.postSettings/postRaceBundle` ~L730 | **CONFIRMED.** `postSettings` at L730, `postRaceBundle` at L747. |
| `pom.xml` JaCoCo `<exclude>` block ~L374-388 | **CONFIRMED.** Exclude block L369-389; last entry at L388 is `MatchdayPairingsGraphicService.class`. No `LobbySettingsGraphicService` entry yet. Block is NOT `MatchdayScheduleGraphicService.class` at L388 — confirmed last entry is L388. |
| `postSettings` is in `MatchController`, not `RaceController` | **NOTE FOR PLANNER.** The new `postLobbySettings` endpoint follows `MatchController` (L170-181), not `RaceController`. |
| `RaceDetailData` record definition | **CONFIRMED.** L343-351. New fields `canGenerateLobbySettings`, `lobbySettingsExist` must be appended to this record and to the constructor call at L138-146. |

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/org/ctc/admin/service/LobbySettingsGraphicService.java` | service | file-I/O | `SettingsGraphicService.java` | exact |
| `src/main/resources/templates/admin/lobby-settings-render.html` | template | request-response | `settings-render.html` | exact |
| `src/main/java/org/ctc/admin/service/RaceGraphicService.java` | service | CRUD | itself (existing methods) | exact |
| `src/main/java/org/ctc/admin/controller/RaceController.java` | controller | request-response | itself (`generateSettings` L318-327) | exact |
| `src/main/java/org/ctc/domain/service/RaceService.java` | service | CRUD | itself (`canGenerateSettings` pattern L123-146) | exact |
| `src/main/resources/templates/admin/race-detail.html` | template | request-response | itself (Settings button L51-61) | exact |
| `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` | controller | CRUD | itself (3× `Map.of` L22-59) | exact |
| `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` | service | request-response | itself (`renderPreview` switch L46-59, `buildSettingsContext` L94-110) | exact |
| `src/main/resources/templates/admin/template-editors.html` | template | request-response | itself (power-rankings tab L39-42 + L654-685) | exact |
| `src/main/java/org/ctc/discord/model/DiscordPostType.java` | model | - | itself (enum entries L3-17) | exact |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | service | event-driven | itself (`postSettings` L730-736) | exact |
| `pom.xml` | config | - | itself (JaCoCo excludes L369-389) | exact |

---

## Pattern Assignments

### `LobbySettingsGraphicService.java` (new service, file-I/O)

**Analog:** `src/main/java/org/ctc/admin/service/SettingsGraphicService.java`

**Imports pattern** (SettingsGraphicService.java L1-16):
```java
package org.ctc.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Race;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
```
Drop `PlayoffSeed`, `PlayoffSeedRepository`, `StandingsService` — `LobbySettingsGraphicService` does not need them. Add `java.util.HashMap`, `java.util.Map`.

**Constructor shape** (SettingsGraphicService.java L18-35):
```java
@Slf4j
@Service
public class SettingsGraphicService extends AbstractGraphicService implements TemplateManageable {

    private static final String DEFAULT_TEMPLATE = "templates/admin/settings-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "settings-template.html";

    // SettingsGraphicService has extra deps (StandingsService, PlayoffSeedRepository)
    // LobbySettingsGraphicService uses the simpler 2-arg form:
    public LobbySettingsGraphicService(TemplateEngine templateEngine,
                                       @Value("${app.upload-dir:uploads}") String uploadDir) {
        super(templateEngine, uploadDir);
    }
```
Change constants to `DEFAULT_TEMPLATE = "templates/admin/lobby-settings-render.html"` and `CUSTOM_TEMPLATE_FILE = "lobby-settings-template.html"`.

**Core generate method** (SettingsGraphicService.java L37-129):
The guard pattern to mirror (adapt for D-03 — no team-card requirement):
```java
public String generateSettings(Race race) throws IOException {
    if (race.getTrack() == null) {
        throw new IllegalStateException("Race has no track");
    }
    if (!race.hasAllSettings()) {
        throw new IllegalStateException("Race settings are incomplete");
    }
    // ... build Context, call renderTemplate(ctx), save PNG under uploadDir/races/{id}/settings.png
    renderScreenshot(html, outputFile);
    return "/uploads/races/" + race.getId() + "/settings.png";
}
```
New method signature: `public String generateLobbySettings(Race race) throws IOException`.
Save file as `lobby-settings.png`. Return `/uploads/races/{id}/lobby-settings.png`.
Context variables come from `v` Map (see handoff INTEGRATION.md).

**TemplateManageable methods** (SettingsGraphicService.java L131-168) — 1:1 mirror:
```java
private String renderTemplate(Context ctx) throws IOException {
    Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
    if (Files.exists(customTemplate)) {
        String template = Files.readString(customTemplate);
        return processStringTemplate(template, ctx);
    }
    return templateEngine.process("admin/lobby-settings-render", ctx);
}

public String loadTemplate() throws IOException { ... }
public String loadDefaultTemplate() throws IOException { ... }
public void saveTemplate(String content) throws IOException { ... }
public void resetTemplate() throws IOException { ... }
public boolean hasCustomTemplate() { ... }
```
Only the template name string and `CUSTOM_TEMPLATE_FILE`/`DEFAULT_TEMPLATE` constants differ from `SettingsGraphicService`.

**AbstractGraphicService constants available** (AbstractGraphicService.java L23-24):
```java
protected static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
protected static final String CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png";
```
Use `encodeClasspathResource(FONT_CLASSPATH, "font/woff2")` and `encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png")` as in existing services.

---

### `lobby-settings-render.html` (new template, file-I/O)

**Analog:** `src/main/resources/templates/admin/settings-render.html` (weather row L109-112)

**Weather row pattern** (settings-render.html L109-112):
```html
<div class="setting-row">
    <div class="setting-label">Weather</div>
    <div class="setting-value" th:text="${weather}"></div>
</div>
```
The lobby template must use the three-row D-07 pattern instead:
```html
<div class="row"><span class="set-label">Weather Selection Method</span>
  <span class="set-value" th:text="${v.weatherMethod} ?: 'Preset Weather'">Preset Weather</span></div>
<div class="row"><span class="set-label">Preset Weather</span>
  <span class="set-value" th:text="${v.weather} ?: '—'">—</span></div>
<div class="row"><span class="set-label">Custom Weather</span>
  <span class="set-value" th:text="${v.customWeather} ?: '—'">—</span></div>
```
Drop-in template is in `design-handoff/templates/lobby-settings-render.html` — apply D-07 weather tweak as the only required deviation from the drop-in.

---

### `RaceGraphicService.java` (modified, CRUD)

**Analog:** itself — `generateSettings` method (RaceGraphicService.java L38-40):
```java
@Transactional
public void generateSettings(UUID raceId) {
    generateAndSaveGraphic(raceId, "Settings", settingsGraphicService::generateSettings);
}
```
Add analogous field + method:
```java
private final LobbySettingsGraphicService lobbySettingsGraphicService;

@Transactional
public void generateLobbySettings(UUID raceId) {
    generateAndSaveGraphic(raceId, "LobbySettings", lobbySettingsGraphicService::generateLobbySettings);
}
```
`@RequiredArgsConstructor` handles the new field automatically — no constructor change needed.

Note: `generateAndSaveGraphic` (L47-59) uses `race.getHomeTeam().getShortName()` + `race.getAwayTeam().getShortName()` for the attachment name. The service guard in `LobbySettingsGraphicService` throws before this point if teams are absent.

---

### `RaceController.java` (modified, request-response)

**Analog:** `generateSettings` endpoint (RaceController.java L318-327):
```java
@PostMapping("/{id}/generate-settings")
public String generateSettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        raceGraphicService.generateSettings(id);
        redirectAttributes.addFlashAttribute("successMessage", "Settings graphic generated");
    } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```
New endpoint:
```java
@PostMapping("/{id}/generate-lobby-settings")
public String generateLobbySettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        raceGraphicService.generateLobbySettings(id);
        redirectAttributes.addFlashAttribute("successMessage", "Lobby settings graphic generated");
    } catch (RuntimeException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/races/" + id;
}
```

Model attribute setup follows the existing `detail()` method pattern (RaceController.java L66-93). Add to the `detail()` method after L82-83:
```java
model.addAttribute("canGenerateLobbySettings", data.canGenerateLobbySettings());
model.addAttribute("lobbySettingsMissing", data.lobbySettingsMissing());
model.addAttribute("lobbySettingsExist", data.lobbySettingsExist());
```

---

### `RaceService.java` (modified, CRUD)

**Analog:** `canGenerateSettings` gate pattern (RaceService.java L123-146):
```java
boolean settingsGraphicExists = race.getAttachments().stream()
        .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/settings.png"));
boolean hasAllSettings = race.hasAllSettings() && race.getCar() != null && race.getTrack() != null;
// ...
return new RaceDetailData(race, homeTotal, awayTotal, driverTeamMap,
        hasAllSettings && hasHomeCard && hasAwayCard && !settingsGraphicExists,
        !hasAllSettings, settingsGraphicExists,
        // ...
```
New gate for D-03 (no team-card requirement):
```java
boolean lobbySettingsGraphicExists = race.getAttachments().stream()
        .anyMatch(a -> a.getType() == AttachmentType.FILE && a.getUrl().endsWith("/lobby-settings.png"));
// canGenerateLobbySettings = hasAllSettings && track != null (already in hasAllSettings) && !lobbySettingsGraphicExists
```
`hasAllSettings` at L125 already includes `race.getTrack() != null`, so:
```java
hasAllSettings && !lobbySettingsGraphicExists,  // canGenerateLobbySettings
!hasAllSettings,                                 // lobbySettingsMissing
lobbySettingsGraphicExists                       // lobbySettingsExist
```

`RaceDetailData` record (L343-351) must be extended with three new fields appended after `canCreateCalendarEvent`:
```java
public record RaceDetailData(Race race, int homeTotal, int awayTotal,
                             Map<UUID, String> driverTeamMap, boolean canGenerateLineup,
                             boolean lineupMissing, boolean cardsMissing, boolean lineupExists,
                             boolean canGenerateResults, boolean resultsMissing, boolean resultsExist,
                             boolean canGenerateSettings, boolean settingsMissing, boolean settingsExist,
                             boolean canGenerateOverlay, boolean overlayExists,
                             boolean calendarAvailable, boolean hasCalendarEvent,
                             boolean canCreateCalendarEvent,
                             boolean canGenerateLobbySettings, boolean lobbySettingsMissing,
                             boolean lobbySettingsExist) {
}
```

---

### `race-detail.html` (modified, request-response)

**Analog:** Settings button block (race-detail.html L51-61):
```html
<!-- Settings Button -->
<form th:if="${canGenerateSettings}" th:action="@{/admin/races/{id}/generate-settings(id=${race.id})}" method="post">
    <button type="submit" class="btn btn-primary">Generate Settings Graphic</button>
</form>
<span th:if="${!canGenerateSettings && !settingsExist}" class="btn-with-hint">
    <button class="btn btn-secondary" disabled>Generate Settings Graphic</button>
    <small class="text-dim">
        <span th:if="${settingsMissing}">Settings incomplete.</span>
        <span th:if="${cardsMissing}">Team cards missing.</span>
    </small>
</span>
```
New block (D-02 disabled-with-hint, D-03 no card requirement):
```html
<!-- Lobby Settings Button -->
<form th:if="${canGenerateLobbySettings}" th:action="@{/admin/races/{id}/generate-lobby-settings(id=${race.id})}" method="post">
    <button type="submit" class="btn btn-primary">Generate Lobby Settings Graphic</button>
</form>
<span th:if="${!canGenerateLobbySettings && !lobbySettingsExist}" class="btn-with-hint">
    <button class="btn btn-secondary" disabled>Generate Lobby Settings Graphic</button>
    <small class="text-dim">
        <span th:if="${lobbySettingsMissing}">Settings incomplete.</span>
    </small>
</span>
```
CSS classes: `btn btn-primary` (active), `btn btn-secondary` + `disabled` attr (inactive). No `style="..."` inline styles. No `cardsMissing` hint — D-03 drops card requirement.

---

### `TemplateEditorController.java` (modified, CRUD)

**Current state** (TemplateEditorController.java L22-59) — all three maps have exactly 10 entries:

`TEMPLATE_TYPE_TO_BEAN` (L22-33):
```java
private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.of(
    "team-cards",         "teamCardService",
    "lineup",             "lineupGraphicService",
    "settings",           "settingsGraphicService",
    "race-results",       "resultsGraphicService",
    "match-results",      "matchResultsGraphicService",
    "matchday-overview",  "matchdayOverviewGraphicService",
    "matchday-schedule",  "matchdayScheduleGraphicService",
    "matchday-results",   "matchdayResultsGraphicService",
    "overlay",            "overlayGraphicService",
    "power-rankings",     "powerRankingsGraphicService"
);
```
`TEMPLATE_TYPE_TO_ATTR` (L35-46): same 10 keys → attribute prefixes.
`TEMPLATE_TYPE_TO_LABEL` (L48-59): same 10 keys → display labels.

Required transformation — convert all three to `Map.ofEntries` and add `"lobby-settings"` entry:
```java
import static java.util.Map.entry;

private static final Map<String, String> TEMPLATE_TYPE_TO_BEAN = Map.ofEntries(
    entry("team-cards",        "teamCardService"),
    // ... existing 10 entries ...
    entry("power-rankings",    "powerRankingsGraphicService"),
    entry("lobby-settings",    "lobbySettingsGraphicService")
);
// Repeat for TEMPLATE_TYPE_TO_ATTR ("lobby-settings" → "lobbySettings")
// Repeat for TEMPLATE_TYPE_TO_LABEL ("lobby-settings" → "Lobby settings")
```

---

### `TemplatePreviewService.java` (modified, request-response)

**Analog:** `renderPreview` switch (TemplatePreviewService.java L46-59):
```java
public String renderPreview(String templateType, String templateContent) {
    var ctx = switch (templateType) {
        case "team-cards" -> buildTeamCardContext();
        case "lineup" -> buildLineupContext();
        case "settings" -> buildSettingsContext();
        // ... 7 more cases ...
        case "power-rankings" -> buildPowerRankingsContext();
        default -> throw new IllegalArgumentException("Unknown template type: " + templateType);
    };
    return processTemplate(templateContent, ctx);
}
```
Add before `default`:
```java
case "lobby-settings" -> buildLobbySettingsContext();
```

**Analog for new context builder:** `buildSettingsContext()` (TemplatePreviewService.java L94-110):
```java
private Context buildSettingsContext() {
    var ctx = buildRaceHeaderContext();
    ctx.setVariable("carName", "Mazda RX-Vision GT3");
    ctx.setVariable("trackName", "Nürburgring 24h");
    // ... sets individual variables
    return ctx;
}
```
New builder uses `v` Map pattern and standalone `Context` (no `buildRaceHeaderContext()` — lobby template does not need team cards):
```java
private Context buildLobbySettingsContext() {
    var ctx = new Context();
    ctx.setVariable("fontBase64", getFontBase64());
    ctx.setVariable("ctcLogoBase64", getLogoBase64());
    ctx.setVariable("seasonYear", "2026");
    ctx.setVariable("seasonName", "Community Team Cup · 2026");
    var v = new java.util.HashMap<String, Object>();
    v.put("roomName", "CTC – 2026 – MD4 – P1R vs. VEZ");
    v.put("track", "Circuit de Sainte-Croix · B Reverse");
    v.put("laps", 19);
    v.put("weatherMethod", "Preset Weather");
    v.put("weather", "S01");
    v.put("customWeather", "—");
    v.put("timeOfDay", "Afternoon");
    v.put("timeSpeed", "3×");
    v.put("tyreWear", "4×");
    v.put("fuelRate", "3×");
    v.put("refuelSpeed", "3 Litre/sec");
    v.put("initialFuel", "Default");
    v.put("minPitStops", 0);
    v.put("usableTyres", "Soft, Medium, Hard, Intermediate, Wet");
    v.put("requiredTyre", "None");
    ctx.setVariable("v", v);
    return ctx;
}
```

---

### `template-editors.html` (modified, request-response)

**Analog:** power-rankings tab button (template-editors.html L39-42):
```html
<a th:href="@{/admin/tools/template-editors(tab='power-rankings')}"
   th:classappend="${activeTab == 'power-rankings' ? 'tab-active' : ''}"
   class="tab-btn">Power Rankings</a>
```
Insert after (inside `.editor-tab-bar`):
```html
<a th:href="@{/admin/tools/template-editors(tab='lobby-settings')}"
   th:classappend="${activeTab == 'lobby-settings' ? 'tab-active' : ''}"
   class="tab-btn">Lobby Settings</a>
```

Tab content block from `design-handoff/snippets/template-editor-tab.html` — insert after the power-rankings tab-content block. Expects model attributes `lobbySettingsIsCustom` and `lobbySettingsTemplate` (provided by `TemplateEditorController` after the map change).

---

### `DiscordPostType.java` (modified, model)

**Current enum** (DiscordPostType.java L3-17):
```java
public enum DiscordPostType {
    TEAM_CARDS, SETTINGS, LINEUPS, SCHEDULE,
    PROVISIONAL_SCORES, MATCH_RESULTS, RACE_RESULTS,
    MATCHDAY_PAIRINGS, MATCHDAY_SCHEDULE, MATCH_PREVIEW,
    MATCHDAY_OVERVIEW, POWER_RANKINGS, STANDINGS
}
```
Add `LOBBY_SETTINGS` (after `SETTINGS` for logical grouping).

---

### `DiscordPostService.java` (modified, event-driven)

**Analog:** `postSettings` (DiscordPostService.java L729-736):
```java
@Transactional
public DiscordPost postSettings(Match match) throws DiscordApiException {
    if (!matchHasCompleteSettings(match)) {
        throw new BusinessRuleException("Configure settings for all races first");
    }
    return postRaceBundle(match, DiscordPostType.SETTINGS, "settings-race-",
            race -> readRaceGraphic(settingsGraphicService.generateSettings(race)));
}
```
New method:
```java
@Transactional
public DiscordPost postLobbySettings(Match match) throws DiscordApiException {
    if (!matchHasCompleteSettings(match)) {
        throw new BusinessRuleException("Configure settings for all races first");
    }
    return postRaceBundle(match, DiscordPostType.LOBBY_SETTINGS, "lobby-settings-race-",
            race -> readRaceGraphic(lobbySettingsGraphicService.generateLobbySettings(race)));
}
```
Add `private final LobbySettingsGraphicService lobbySettingsGraphicService;` field. **CORRECTION:** `DiscordPostService` does NOT use `@RequiredArgsConstructor` — it has an EXPLICIT constructor (DiscordPostService.java ~L119-164). Four edits are required: (1) the `private final` field, (2) the constructor parameter, (3) the assignment in the constructor body, (4) the `@SuppressFBWarnings` EI_EXPOSE_REP2 justification list update per CLAUDE.md SpotBugs discipline.

**Analog for controller endpoint:** `MatchController.postSettings` (MatchController.java L170-181):
```java
@PostMapping("/{id}/post-settings")
public String postSettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postSettings(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Settings posted.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post settings");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post settings");
    }
    return "redirect:/admin/matches/" + id;
}
```
New endpoint in `MatchController`:
```java
@PostMapping("/{id}/post-lobby-settings")
public String postLobbySettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        discordPostService.postLobbySettings(matchService.findById(id));
        redirectAttributes.addFlashAttribute("successMessage", "Lobby settings posted.");
    } catch (BusinessRuleException e) {
        applyErrorFlash(redirectAttributes, e, "Post lobby settings");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Post lobby settings");
    }
    return "redirect:/admin/matches/" + id;
}
```

---

### `pom.xml` (modified, config)

**Analog:** JaCoCo exclude block (pom.xml L369-389):
```xml
<excludes>
    <exclude>org/ctc/CtcManagerApplication.class</exclude>
    <!-- ... -->
    <exclude>org/ctc/admin/service/SettingsGraphicService.class</exclude>
    <exclude>org/ctc/admin/service/OverlayGraphicService.class</exclude>
    <!-- ... -->
    <exclude>org/ctc/admin/service/MatchdayPairingsGraphicService.class</exclude>
    <exclude>org/ctc/admin/service/MatchdayScheduleGraphicService.class</exclude>
</excludes>
```
Append after the last entry (currently `MatchdayScheduleGraphicService.class` at L388):
```xml
<exclude>org/ctc/admin/service/LobbySettingsGraphicService.class</exclude>
```

---

## Shared Patterns

### Graphic Service Constructor
**Source:** `AbstractGraphicService.java` L31-34
```java
protected AbstractGraphicService(TemplateEngine templateEngine, String uploadDir) {
    this.templateEngine = templateEngine;
    this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
}
```
Apply to: `LobbySettingsGraphicService` constructor (2-arg form, no extra deps).

### Classpath Asset Encoding
**Source:** `AbstractGraphicService.java` L90-103
```java
protected String encodeClasspathResource(String classpathLocation, String mimeType) {
    try {
        var resource = new ClassPathResource(classpathLocation);
        if (resource.exists()) {
            try (var is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        }
    } catch (IOException e) {
        log.warn("Failed to encode classpath resource: {}", classpathLocation, e);
    }
    return null;
}
```
Apply to: `LobbySettingsGraphicService.generateLobbySettings` — call `encodeClasspathResource(FONT_CLASSPATH, "font/woff2")` and `encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png")`.

### Flash Attributes
**Source:** `RaceController.java` L319-327
```java
redirectAttributes.addFlashAttribute("successMessage", "Settings graphic generated");
// ...
redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
```
Apply to: all new controller endpoints (generate + post).

### Dependency Injection — two cases (do NOT conflate)
**Source:** `RaceGraphicService.java` L17-25 (`@RequiredArgsConstructor`) vs `DiscordPostService.java` (EXPLICIT constructor, ~L119-164).
- `@RequiredArgsConstructor` classes (e.g. `RaceGraphicService`): a new `private final` field is auto-wired — no constructor change.
- **`DiscordPostService` is NOT `@RequiredArgsConstructor`** — it has an explicit constructor. Adding `lobbySettingsGraphicService` requires four edits: field, constructor param, assignment, and the `@SuppressFBWarnings` EI_EXPOSE_REP2 justification list (CLAUDE.md SpotBugs discipline).

### Lombok Annotation Order
**Source:** `RaceGraphicService.java` L15-18
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceGraphicService {
```
Apply to: `LobbySettingsGraphicService` uses `@Slf4j @Service` (no `@RequiredArgsConstructor` — manual constructor with `@Value`).

---

## No Analog Found

None — all files have direct in-codebase analogs.

---

## Metadata

**Analog search scope:** `src/main/java/org/ctc/admin/`, `src/main/java/org/ctc/discord/`, `src/main/java/org/ctc/domain/service/`, `src/main/resources/templates/admin/`, `pom.xml`
**Files scanned:** 12 source files + 3 handoff files
**Pattern extraction date:** 2026-05-31
