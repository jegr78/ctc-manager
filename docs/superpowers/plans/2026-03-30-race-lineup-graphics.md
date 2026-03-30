# Race Lineup Graphics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate 1920×1080 lineup graphics per Race via Playwright, store as RaceAttachment, with a new Template Editors page consolidating Team Card and Lineup template editors.

**Architecture:** New `LineupGraphicService` (analog to `TeamCardService`) renders a Thymeleaf HTML template with embedded Team Card PNGs, driver pairings, standings positions, and season info. A new `TemplateEditorController` provides a tabbed editor page. The generate button lives on the Race detail page.

**Tech Stack:** Java 25, Spring Boot 4.x, Thymeleaf, Playwright (Chromium), JUnit 5, AssertJ

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `src/main/java/de/ctc/admin/service/LineupGraphicService.java` | Lineup PNG generation via Playwright, template management |
| `src/test/java/de/ctc/admin/service/LineupGraphicServiceTest.java` | Unit tests for service |
| `src/main/java/de/ctc/admin/controller/TemplateEditorController.java` | Template editors page with tabs |
| `src/main/resources/templates/admin/lineup-render.html` | Default lineup Thymeleaf template (1920×1080) |
| `src/main/resources/templates/admin/template-editors.html` | Template editors page (tabs: Team Cards, Lineup) |

### Modified Files
| File | Changes |
|------|---------|
| `src/main/resources/templates/admin/layout.html` | Add "Template Editors" menu item under Tools |
| `src/main/resources/templates/admin/race-detail.html` | Add Generate Lineup button, Download button per attachment |
| `src/main/java/de/ctc/admin/controller/RaceController.java` | Add generate-lineup and attachment-download endpoints, pass lineup availability to model |
| `src/main/java/de/ctc/admin/controller/TeamCardController.java` | Remove template endpoints (migrated to TemplateEditorController) |

---

## Task 1: LineupGraphicService — Unit Tests + Core Logic

**Files:**
- Create: `src/test/java/de/ctc/admin/service/LineupGraphicServiceTest.java`
- Create: `src/main/java/de/ctc/admin/service/LineupGraphicService.java`

- [ ] **Step 1: Write failing tests for buildPairings and encodeCardBase64**

```java
package de.ctc.admin.service;

import de.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineupGraphicServiceTest {

    @TempDir
    Path tempDir;

    private LineupGraphicService createService() {
        return new LineupGraphicService(null, null, null, tempDir.toString());
    }

    @Test
    void buildPairings_matchesHomeAndAwayByIndex() {
        var service = createService();
        var homeTeam = new Team("Home", "HOM");
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        var driverA = new Driver(); driverA.setPsnId("HomeDriver1");
        var driverB = new Driver(); driverB.setPsnId("HomeDriver2");
        var driverC = new Driver(); driverC.setPsnId("AwayDriver1");
        var driverD = new Driver(); driverD.setPsnId("AwayDriver2");

        var lineups = List.of(
                new RaceLineup(race, driverA, homeTeam),
                new RaceLineup(race, driverB, homeTeam),
                new RaceLineup(race, driverC, awayTeam),
                new RaceLineup(race, driverD, awayTeam)
        );

        var pairings = service.buildPairings(lineups, homeTeam, awayTeam);

        assertThat(pairings).hasSize(2);
        assertThat(pairings.get(0).homeDriver()).isEqualTo("HomeDriver1");
        assertThat(pairings.get(0).awayDriver()).isEqualTo("AwayDriver1");
        assertThat(pairings.get(1).homeDriver()).isEqualTo("HomeDriver2");
        assertThat(pairings.get(1).awayDriver()).isEqualTo("AwayDriver2");
    }

    @Test
    void buildPairings_handlesSubTeams() {
        var service = createService();
        var parentHome = new Team("Parent Home", "PH");
        parentHome.setId(UUID.randomUUID());
        var subHome = new Team("Sub Home", "PH 1", parentHome);
        subHome.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        var driverA = new Driver(); driverA.setPsnId("SubDriver1");
        var driverB = new Driver(); driverB.setPsnId("AwayDriver1");

        var lineups = List.of(
                new RaceLineup(race, driverA, subHome),
                new RaceLineup(race, driverB, awayTeam)
        );

        var pairings = service.buildPairings(lineups, parentHome, awayTeam);

        assertThat(pairings).hasSize(1);
        assertThat(pairings.get(0).homeDriver()).isEqualTo("SubDriver1");
        assertThat(pairings.get(0).awayDriver()).isEqualTo("AwayDriver1");
    }

    @Test
    void buildPairings_unevenTeams_pairsUpToMinimum() {
        var service = createService();
        var homeTeam = new Team("Home", "HOM");
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        var d1 = new Driver(); d1.setPsnId("H1");
        var d2 = new Driver(); d2.setPsnId("H2");
        var d3 = new Driver(); d3.setPsnId("H3");
        var d4 = new Driver(); d4.setPsnId("A1");
        var d5 = new Driver(); d5.setPsnId("A2");

        var lineups = List.of(
                new RaceLineup(race, d1, homeTeam),
                new RaceLineup(race, d2, homeTeam),
                new RaceLineup(race, d3, homeTeam),
                new RaceLineup(race, d4, awayTeam),
                new RaceLineup(race, d5, awayTeam)
        );

        var pairings = service.buildPairings(lineups, homeTeam, awayTeam);

        assertThat(pairings).hasSize(3);
        assertThat(pairings.get(2).homeDriver()).isEqualTo("H3");
        assertThat(pairings.get(2).awayDriver()).isEmpty();
    }

    @Test
    void encodeCardBase64_returnsDataUri() throws IOException {
        var service = createService();
        // Create a tiny PNG in tempDir
        Path cardDir = tempDir.resolve("team-cards").resolve("season1");
        Files.createDirectories(cardDir);
        Path cardFile = cardDir.resolve("TST.png");
        var img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(img, "png", cardFile.toFile());

        String result = service.encodeCardBase64("/uploads/team-cards/season1/TST.png");

        assertThat(result).startsWith("data:image/png;base64,");
    }

    @Test
    void encodeCardBase64_returnsNullForMissingFile() {
        var service = createService();

        String result = service.encodeCardBase64("/uploads/team-cards/season1/MISSING.png");

        assertThat(result).isNull();
    }

    @Test
    void extractYear_fromSeasonName() {
        var service = createService();
        assertThat(service.extractYear("Season 4 - 2026")).isEqualTo("2026");
        assertThat(service.extractYear("2025")).isEqualTo("2025");
        assertThat(service.extractYear("No year here")).isEqualTo("");
    }

    @Test
    void templateManagement_defaultAndCustom() throws IOException {
        var service = createService();
        assertThat(service.hasCustomTemplate()).isFalse();

        service.saveTemplate("<html>custom</html>");
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom</html>");

        service.resetTemplate();
        assertThat(service.hasCustomTemplate()).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=LineupGraphicServiceTest -Dspring-boot.run.profiles=dev`
Expected: Compilation failure — `LineupGraphicService` does not exist yet.

- [ ] **Step 3: Implement LineupGraphicService**

```java
package de.ctc.admin.service;

import com.microsoft.playwright.*;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceLineup;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.RaceLineupRepository;
import de.ctc.domain.service.StandingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LineupGraphicService {

    private static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
    private static final String CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png";
    private static final String DEFAULT_TEMPLATE = "templates/admin/lineup-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "lineup-template.html";
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");

    private final TemplateEngine templateEngine;
    private final StandingsService standingsService;
    private final RaceLineupRepository raceLineupRepository;
    private final Path uploadDir;

    public LineupGraphicService(TemplateEngine templateEngine,
                                StandingsService standingsService,
                                RaceLineupRepository raceLineupRepository,
                                @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.templateEngine = templateEngine;
        this.standingsService = standingsService;
        this.raceLineupRepository = raceLineupRepository;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public record DriverPairing(String homeDriver, String awayDriver) {}

    public String generateLineup(Race race) throws IOException {
        var match = race.getMatch();
        if (match == null) throw new IllegalStateException("Race has no match");

        var homeTeam = match.getHomeTeam();
        var awayTeam = match.getAwayTeam();
        var season = race.getMatchday().getSeason();

        // Load lineups and build pairings
        var lineups = raceLineupRepository.findByRaceId(race.getId());
        if (lineups.isEmpty()) throw new IllegalStateException("No lineup entries for this race");
        var pairings = buildPairings(lineups, homeTeam, awayTeam);

        // Load team card PNGs as Base64
        String homeCardPath = "/uploads/team-cards/" + season.getId() + "/"
                + sanitizeFilename(homeTeam.getShortName()) + ".png";
        String awayCardPath = "/uploads/team-cards/" + season.getId() + "/"
                + sanitizeFilename(awayTeam.getShortName()) + ".png";

        String homeCardBase64 = encodeCardBase64(homeCardPath);
        String awayCardBase64 = encodeCardBase64(awayCardPath);
        if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
        if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

        // Calculate standings positions
        var standings = standingsService.calculateStandings(season.getId());
        int homePosition = 0;
        int awayPosition = 0;
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
            if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
        }

        // Build template context
        var ctx = new Context();
        ctx.setVariable("seasonYear", extractYear(season.getName()));
        ctx.setVariable("matchdayName", race.getMatchday().getLabel());
        ctx.setVariable("seasonName", season.getName());
        ctx.setVariable("homeCardBase64", homeCardBase64);
        ctx.setVariable("awayCardBase64", awayCardBase64);
        ctx.setVariable("homePosition", homePosition);
        ctx.setVariable("awayPosition", awayPosition);
        ctx.setVariable("pairings", pairings);
        ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
        ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));

        String html = renderTemplate(ctx);

        // Render with Playwright
        Path tempFile = Files.createTempFile("lineup-", ".html");
        Files.writeString(tempFile, html);

        Path raceDir = uploadDir.resolve("races").resolve(race.getId().toString());
        Files.createDirectories(raceDir);
        Path outputFile = raceDir.resolve("lineup.png");

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage(new Browser.NewPageOptions()
                    .setViewportSize(1920, 1080));
            page.navigate("file://" + tempFile.toAbsolutePath());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputFile)
                    .setFullPage(false));
            browser.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }

        log.info("Generated lineup graphic: {}", outputFile);
        return "/uploads/races/" + race.getId() + "/lineup.png";
    }

    List<DriverPairing> buildPairings(List<RaceLineup> lineups, Team homeTeam, Team awayTeam) {
        var homeDrivers = lineups.stream()
                .filter(lu -> isTeamOrSubTeam(lu.getTeam(), homeTeam))
                .map(lu -> lu.getDriver().getPsnId())
                .toList();
        var awayDrivers = lineups.stream()
                .filter(lu -> isTeamOrSubTeam(lu.getTeam(), awayTeam))
                .map(lu -> lu.getDriver().getPsnId())
                .toList();

        int maxSize = Math.max(homeDrivers.size(), awayDrivers.size());
        var pairings = new ArrayList<DriverPairing>();
        for (int i = 0; i < maxSize; i++) {
            String home = i < homeDrivers.size() ? homeDrivers.get(i) : "";
            String away = i < awayDrivers.size() ? awayDrivers.get(i) : "";
            pairings.add(new DriverPairing(home, away));
        }
        return pairings;
    }

    private boolean isTeamOrSubTeam(Team team, Team parentOrSelf) {
        if (team.getId().equals(parentOrSelf.getId())) return true;
        return team.getParentTeam() != null && team.getParentTeam().getId().equals(parentOrSelf.getId());
    }

    String encodeCardBase64(String cardUrl) {
        if (cardUrl == null || !cardUrl.startsWith("/uploads/")) return null;
        try {
            Path cardFile = uploadDir.resolve(cardUrl.substring("/uploads/".length())).normalize();
            if (!cardFile.startsWith(uploadDir)) return null;
            if (Files.exists(cardFile)) {
                byte[] bytes = Files.readAllBytes(cardFile);
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.warn("Failed to encode card: {}", cardUrl, e);
        }
        return null;
    }

    String extractYear(String seasonName) {
        if (seasonName == null) return "";
        Matcher m = YEAR_PATTERN.matcher(seasonName);
        return m.find() ? m.group(1) : "";
    }

    private String encodeClasspathResource(String classpathLocation, String mimeType) {
        try {
            var resource = new ClassPathResource(classpathLocation);
            if (resource.exists()) {
                byte[] bytes = resource.getInputStream().readAllBytes();
                return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.warn("Failed to encode classpath resource: {}", classpathLocation, e);
        }
        return null;
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // Template management

    private String renderTemplate(Context ctx) throws IOException {
        Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
        if (Files.exists(customTemplate)) {
            String template = Files.readString(customTemplate);
            return processStringTemplate(template, ctx);
        }
        return templateEngine.process("admin/lineup-render", ctx);
    }

    private String processStringTemplate(String template, Context ctx) {
        var engine = new SpringTemplateEngine();
        var resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        engine.setTemplateResolver(resolver);
        return engine.process(template, ctx);
    }

    public String loadTemplate() throws IOException {
        Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
        if (Files.exists(customTemplate)) {
            return Files.readString(customTemplate);
        }
        return loadDefaultTemplate();
    }

    public String loadDefaultTemplate() throws IOException {
        var resource = new ClassPathResource(DEFAULT_TEMPLATE);
        return new String(resource.getInputStream().readAllBytes());
    }

    public void saveTemplate(String content) throws IOException {
        Files.createDirectories(uploadDir);
        Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
        log.info("Saved custom lineup template");
    }

    public void resetTemplate() throws IOException {
        Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
        log.info("Reset lineup template to default");
    }

    public boolean hasCustomTemplate() {
        return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=LineupGraphicServiceTest`
Expected: All 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/ctc/admin/service/LineupGraphicService.java \
       src/test/java/de/ctc/admin/service/LineupGraphicServiceTest.java
git commit -m "feat: LineupGraphicService mit Unit Tests"
```

---

## Task 2: Lineup Render Template

**Files:**
- Create: `src/main/resources/templates/admin/lineup-render.html`

- [ ] **Step 1: Create the default lineup HTML template**

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
        body {
            width: 1920px;
            height: 1080px;
            overflow: hidden;
            font-family: 'Conthrax', -apple-system, sans-serif;
            display: flex;
            flex-direction: column;
        }

        /* Header */
        .header {
            background: linear-gradient(180deg, #c0c0c0 0%, #a0a0a0 100%);
            padding: 24px 48px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            min-height: 130px;
        }

        .header-left {
            text-align: center;
        }

        .header-left .title {
            font-size: 28px;
            font-weight: 900;
            color: #1a1a1a;
            text-transform: uppercase;
            letter-spacing: 3px;
            line-height: 1.1;
        }

        .header-left .year {
            font-size: 32px;
            font-weight: 700;
            color: #1a1a1a;
            margin-top: 4px;
        }

        .header-center {
            text-align: center;
        }

        .header-center .lineups-title {
            font-size: 56px;
            font-weight: 900;
            font-style: italic;
            color: #1a1a1a;
            letter-spacing: 2px;
        }

        .header-center .matchday {
            font-size: 36px;
            font-weight: 700;
            color: #1a1a1a;
        }

        .header-right {
            width: 80px;
            height: 80px;
        }

        .header-right img {
            width: 100%;
            height: 100%;
            object-fit: contain;
        }

        /* Main content */
        .main {
            flex: 1;
            background: #111111;
            display: flex;
            align-items: stretch;
            padding: 20px 32px;
            gap: 24px;
        }

        .team-card-container {
            width: 18%;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .team-card-container img {
            max-height: 100%;
            max-width: 100%;
            object-fit: contain;
            border: 3px solid white;
            border-radius: 6px;
        }

        .pairings {
            flex: 1;
            display: flex;
            flex-direction: column;
            justify-content: space-around;
            padding: 8px 0;
        }

        .pairing {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px 32px;
        }

        .driver-name {
            font-size: 28px;
            font-weight: 700;
            color: white;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 45%;
        }

        .driver-name.home {
            text-align: left;
        }

        .driver-name.away {
            text-align: right;
        }

        /* Footer */
        .footer {
            background: linear-gradient(180deg, #d0d0d0 0%, #b0b0b0 100%);
            padding: 24px 48px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            min-height: 130px;
        }

        .position {
            font-size: 56px;
            font-weight: 900;
            color: #1a1a1a;
        }

        .season-name {
            font-size: 42px;
            font-weight: 700;
            font-style: italic;
            color: #1a1a1a;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="header-left">
            <div class="title">Community<br>Team Cup</div>
            <div class="year" th:text="${seasonYear}"></div>
        </div>
        <div class="header-center">
            <div class="lineups-title">Lineups</div>
            <div class="matchday" th:text="${matchdayName}"></div>
        </div>
        <div class="header-right">
            <img th:if="${ctcLogoBase64 != null}" th:src="${ctcLogoBase64}" alt="CTC">
        </div>
    </div>

    <div class="main">
        <div class="team-card-container">
            <img th:src="${homeCardBase64}" alt="Home Team Card">
        </div>

        <div class="pairings">
            <div class="pairing" th:each="p : ${pairings}">
                <span class="driver-name home" th:text="${p.homeDriver}"></span>
                <span class="driver-name away" th:text="${p.awayDriver}"></span>
            </div>
        </div>

        <div class="team-card-container">
            <img th:src="${awayCardBase64}" alt="Away Team Card">
        </div>
    </div>

    <div class="footer">
        <div class="position" th:text="'P' + ${homePosition}"></div>
        <div class="season-name" th:text="${seasonName}"></div>
        <div class="position" th:text="'P' + ${awayPosition}"></div>
    </div>
</body>
</html>
```

- [ ] **Step 2: Verify template is loadable**

Run: `./mvnw test -Dtest=LineupGraphicServiceTest`
Expected: All tests still pass (template file now on classpath for `loadDefaultTemplate`).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/admin/lineup-render.html
git commit -m "feat: Lineup-Grafik Thymeleaf-Template (1920x1080)"
```

---

## Task 3: Template Editors Page + Controller

**Files:**
- Create: `src/main/java/de/ctc/admin/controller/TemplateEditorController.java`
- Create: `src/main/resources/templates/admin/template-editors.html`
- Modify: `src/main/resources/templates/admin/layout.html`
- Modify: `src/main/java/de/ctc/admin/controller/TeamCardController.java`

- [ ] **Step 1: Create TemplateEditorController**

```java
package de.ctc.admin.controller;

import de.ctc.admin.service.LineupGraphicService;
import de.ctc.admin.service.TeamCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/tools/template-editors")
@RequiredArgsConstructor
public class TemplateEditorController {

    private final TeamCardService teamCardService;
    private final LineupGraphicService lineupGraphicService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "team-cards") String tab, Model model) {
        try {
            model.addAttribute("teamCardTemplate", teamCardService.loadTemplate());
            model.addAttribute("teamCardIsCustom", teamCardService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("teamCardTemplate", "");
            model.addAttribute("errorMessage", "Failed to load team card template: " + e.getMessage());
        }
        try {
            model.addAttribute("lineupTemplate", lineupGraphicService.loadTemplate());
            model.addAttribute("lineupIsCustom", lineupGraphicService.hasCustomTemplate());
        } catch (Exception e) {
            model.addAttribute("lineupTemplate", "");
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", "Failed to load lineup template: " + e.getMessage());
            }
        }
        model.addAttribute("activeTab", tab);
        return "admin/template-editors";
    }

    @PostMapping("/team-cards/save")
    public String saveTeamCardTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            teamCardService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Team card template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=team-cards";
    }

    @PostMapping("/team-cards/reset")
    public String resetTeamCardTemplate(RedirectAttributes redirectAttributes) {
        try {
            teamCardService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Team card template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=team-cards";
    }

    @PostMapping("/lineup/save")
    public String saveLineupTemplate(@RequestParam String template, RedirectAttributes redirectAttributes) {
        try {
            lineupGraphicService.saveTemplate(template);
            redirectAttributes.addFlashAttribute("successMessage", "Lineup template saved");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Save failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=lineup";
    }

    @PostMapping("/lineup/reset")
    public String resetLineupTemplate(RedirectAttributes redirectAttributes) {
        try {
            lineupGraphicService.resetTemplate();
            redirectAttributes.addFlashAttribute("successMessage", "Lineup template reset to default");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Reset failed: " + e.getMessage());
        }
        return "redirect:/admin/tools/template-editors?tab=lineup";
    }
}
```

- [ ] **Step 2: Create template-editors.html**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Template Editors', ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div><h1>Template Editors</h1></div>
    </div>

    <!-- Tabs -->
    <div style="display:flex;gap:0;margin-top:16px;border-bottom:2px solid var(--border);">
        <a th:href="@{/admin/tools/template-editors(tab='team-cards')}"
           th:classappend="${activeTab == 'team-cards' ? 'tab-active' : ''}"
           class="tab-btn">Team Cards</a>
        <a th:href="@{/admin/tools/template-editors(tab='lineup')}"
           th:classappend="${activeTab == 'lineup' ? 'tab-active' : ''}"
           class="tab-btn">Lineup</a>
    </div>

    <!-- Team Cards Tab -->
    <div th:if="${activeTab == 'team-cards'}" style="margin-top:16px;">
        <div style="display:flex;justify-content:flex-end;margin-bottom:8px;">
            <span th:if="${teamCardIsCustom}" class="badge badge-active" style="font-size:12px;">Custom</span>
            <span th:unless="${teamCardIsCustom}" class="badge badge-inactive" style="font-size:12px;">Default</span>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
            <div class="card" style="padding:0;display:flex;flex-direction:column;">
                <div style="padding:12px 16px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;">
                    <strong>Template (HTML + Thymeleaf)</strong>
                    <form th:action="@{/admin/tools/template-editors/team-cards/reset}" method="post"
                          onsubmit="return confirm('Reset to default template?')">
                        <button type="submit" class="btn btn-danger btn-sm">Reset</button>
                    </form>
                </div>
                <form th:action="@{/admin/tools/template-editors/team-cards/save}" method="post" style="display:flex;flex-direction:column;flex:1;">
                    <textarea name="template" class="template-textarea" th:text="${teamCardTemplate}"></textarea>
                    <div style="padding:12px 16px;border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end;">
                        <button type="submit" class="btn btn-primary">Save Template</button>
                    </div>
                </form>
            </div>
            <div class="card" style="padding:16px;">
                <strong>Available Variables</strong>
                <table style="min-width:auto;margin-top:12px;font-size:13px;">
                    <thead><tr><th>Variable</th><th>Description</th></tr></thead>
                    <tbody>
                        <tr><td><code>rating</code></td><td>Team rating (Integer or null)</td></tr>
                        <tr><td><code>teamName</code></td><td>Team display name</td></tr>
                        <tr><td><code>subTeamLabel</code></td><td>Sub-team suffix (e.g. "A", "1") or null</td></tr>
                        <tr><td><code>points</code></td><td>Current season points</td></tr>
                        <tr><td><code>record</code></td><td>W - L - D record</td></tr>
                        <tr><td><code>primaryColor</code></td><td>Primary color hex</td></tr>
                        <tr><td><code>secondaryColor</code></td><td>Secondary color hex</td></tr>
                        <tr><td><code>accentColor</code></td><td>Accent color hex</td></tr>
                        <tr><td><code>gradientColor</code></td><td>Darkest of the 3 colors</td></tr>
                        <tr><td><code>logoBase64</code></td><td>Logo as data URI (or null)</td></tr>
                        <tr><td><code>fontBase64</code></td><td>Conthrax font as data URI</td></tr>
                    </tbody>
                </table>
                <div style="margin-top:20px;">
                    <strong>Tips</strong>
                    <ul style="margin-top:8px;padding-left:20px;font-size:13px;color:var(--text-dim);line-height:1.8;">
                        <li>Card size is 1080x1920px (Story format)</li>
                        <li>Use <code>th:inline="text"</code> in <code>&lt;style&gt;</code> for CSS variables</li>
                        <li>Use <code>[[...]]</code> syntax for inline text in CSS</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <!-- Lineup Tab -->
    <div th:if="${activeTab == 'lineup'}" style="margin-top:16px;">
        <div style="display:flex;justify-content:flex-end;margin-bottom:8px;">
            <span th:if="${lineupIsCustom}" class="badge badge-active" style="font-size:12px;">Custom</span>
            <span th:unless="${lineupIsCustom}" class="badge badge-inactive" style="font-size:12px;">Default</span>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
            <div class="card" style="padding:0;display:flex;flex-direction:column;">
                <div style="padding:12px 16px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;">
                    <strong>Template (HTML + Thymeleaf)</strong>
                    <form th:action="@{/admin/tools/template-editors/lineup/reset}" method="post"
                          onsubmit="return confirm('Reset to default template?')">
                        <button type="submit" class="btn btn-danger btn-sm">Reset</button>
                    </form>
                </div>
                <form th:action="@{/admin/tools/template-editors/lineup/save}" method="post" style="display:flex;flex-direction:column;flex:1;">
                    <textarea name="template" class="template-textarea" th:text="${lineupTemplate}"></textarea>
                    <div style="padding:12px 16px;border-top:1px solid var(--border);display:flex;gap:8px;justify-content:flex-end;">
                        <button type="submit" class="btn btn-primary">Save Template</button>
                    </div>
                </form>
            </div>
            <div class="card" style="padding:16px;">
                <strong>Available Variables</strong>
                <table style="min-width:auto;margin-top:12px;font-size:13px;">
                    <thead><tr><th>Variable</th><th>Description</th></tr></thead>
                    <tbody>
                        <tr><td><code>seasonYear</code></td><td>Year from season name</td></tr>
                        <tr><td><code>matchdayName</code></td><td>Matchday label (e.g. "MD 1")</td></tr>
                        <tr><td><code>seasonName</code></td><td>Season name for footer</td></tr>
                        <tr><td><code>homeCardBase64</code></td><td>Home team card PNG as data URI</td></tr>
                        <tr><td><code>awayCardBase64</code></td><td>Away team card PNG as data URI</td></tr>
                        <tr><td><code>homePosition</code></td><td>Home team standings position</td></tr>
                        <tr><td><code>awayPosition</code></td><td>Away team standings position</td></tr>
                        <tr><td><code>pairings</code></td><td>List of {homeDriver, awayDriver}</td></tr>
                        <tr><td><code>ctcLogoBase64</code></td><td>CTC logo as data URI</td></tr>
                        <tr><td><code>fontBase64</code></td><td>Conthrax font as data URI</td></tr>
                    </tbody>
                </table>
                <div style="margin-top:20px;">
                    <strong>Tips</strong>
                    <ul style="margin-top:8px;padding-left:20px;font-size:13px;color:var(--text-dim);line-height:1.8;">
                        <li>Graphic size is 1920x1080px (Full HD 16:9)</li>
                        <li>Use <code>th:each="p : ${pairings}"</code> to iterate pairings</li>
                        <li>Access driver names: <code>${p.homeDriver}</code>, <code>${p.awayDriver}</code></li>
                        <li>Use <code>text-overflow: ellipsis</code> for long driver names</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <script>
        document.querySelectorAll('.template-textarea').forEach(function(el) {
            el.addEventListener('keydown', function(e) {
                if (e.key === 'Tab') {
                    e.preventDefault();
                    var start = this.selectionStart;
                    var end = this.selectionEnd;
                    this.value = this.value.substring(0, start) + '    ' + this.value.substring(end);
                    this.selectionStart = this.selectionEnd = start + 4;
                }
            });
        });
    </script>
</section>
</body>
</html>
```

- [ ] **Step 3: Add CSS for tabs and template textarea to admin.css**

Add the following at the end of `src/main/resources/static/admin/css/admin.css`:

```css
/* Template Editor */
.tab-btn {
    padding: 10px 24px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-dim);
    text-decoration: none;
    border-bottom: 2px solid transparent;
    margin-bottom: -2px;
    transition: color 0.2s, border-color 0.2s;
}

.tab-btn:hover {
    color: var(--white);
}

.tab-btn.tab-active {
    color: var(--accent);
    border-bottom-color: var(--accent);
}

.template-textarea {
    flex: 1;
    min-height: 600px;
    padding: 12px;
    border: none;
    background: var(--bg);
    color: var(--white);
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    line-height: 1.5;
    resize: none;
    tab-size: 4;
    white-space: pre;
    overflow: auto;
}
```

- [ ] **Step 4: Add "Template Editors" to sidebar in layout.html**

In `src/main/resources/templates/admin/layout.html`, add this line after the Team Cards link (line 49):

```html
                    <a th:href="@{/admin/tools/template-editors}" th:classappend="${title.contains('Template') ? 'active' : ''}">Template Editors</a>
```

- [ ] **Step 5: Remove template endpoints from TeamCardController**

In `src/main/java/de/ctc/admin/controller/TeamCardController.java`, delete the following methods (lines 139-171):
- `templateEditor()` (GET `/template`)
- `saveTemplate()` (POST `/template/save`)
- `resetTemplate()` (POST `/template/reset`)

- [ ] **Step 6: Run full test suite**

Run: `./mvnw verify`
Expected: All tests pass. No compilation errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/TemplateEditorController.java \
       src/main/resources/templates/admin/template-editors.html \
       src/main/resources/templates/admin/layout.html \
       src/main/resources/static/admin/css/admin.css \
       src/main/java/de/ctc/admin/controller/TeamCardController.java
git commit -m "feat: Template Editors Seite mit Tabs (Team Cards + Lineup)"
```

---

## Task 4: Generate Lineup Button + Attachment Download on Race Detail

**Files:**
- Modify: `src/main/java/de/ctc/admin/controller/RaceController.java`
- Modify: `src/main/resources/templates/admin/race-detail.html`

- [ ] **Step 1: Add LineupGraphicService dependency and endpoints to RaceController**

Add to `RaceController.java`:

1. Add fields:

```java
    private final LineupGraphicService lineupGraphicService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;
```

2. In the `detail()` method, add this block **after** the closing brace of the `if (!race.getResults().isEmpty())` block (after line 103), so it runs regardless of whether results exist:

```java
        // Check if lineup graphic can be generated
        var lineups = raceLineupRepository.findByRaceId(race.getId());
        boolean hasLineup = !lineups.isEmpty();
        boolean hasHomeCard = false;
        boolean hasAwayCard = false;
        if (race.getMatch() != null && race.getHomeTeam() != null && race.getAwayTeam() != null) {
            var season = race.getMatchday().getSeason();
            hasHomeCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getHomeTeam().getId())
                    .map(st -> teamCardService.cardExists(st)).orElse(false);
            hasAwayCard = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), race.getAwayTeam().getId())
                    .map(st -> teamCardService.cardExists(st)).orElse(false);
        }
        model.addAttribute("canGenerateLineup", hasLineup && hasHomeCard && hasAwayCard);
        model.addAttribute("lineupMissing", !hasLineup);
        model.addAttribute("cardsMissing", !hasHomeCard || !hasAwayCard);
```

Also add the required fields `SeasonTeamRepository seasonTeamRepository` and `TeamCardService teamCardService` to the constructor (via `@RequiredArgsConstructor` — just add the fields).

3. Add the generate-lineup endpoint:

```java
    @PostMapping("/{id}/generate-lineup")
    public String generateLineup(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var race = raceRepository.findById(id).orElseThrow();
        try {
            String url = lineupGraphicService.generateLineup(race);
            var attachment = new RaceAttachment(race, AttachmentType.FILE, "Lineup", url);
            raceAttachmentRepository.save(attachment);
            redirectAttributes.addFlashAttribute("successMessage", "Lineup graphic generated");
        } catch (Exception e) {
            log.error("Lineup generation failed for race {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Generation failed: " + e.getMessage());
        }
        return "redirect:/admin/races/" + id;
    }
```

4. Add the attachment download endpoint:

```java
    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAttachment(@PathVariable UUID attachmentId) {
        var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
        if (attachment.getType() != AttachmentType.FILE) {
            return ResponseEntity.badRequest().build();
        }
        String url = attachment.getUrl();
        Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve(url.substring("/uploads/".length()));
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = "application/octet-stream";
        try { contentType = Files.probeContentType(file); } catch (IOException ignored) {}
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getName() + getExtension(file) + "\"")
                .body(new org.springframework.core.io.FileSystemResource(file));
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
```

Add the required imports:

```java
import de.ctc.admin.service.LineupGraphicService;
import de.ctc.admin.service.TeamCardService;
import de.ctc.domain.repository.SeasonTeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
```

- [ ] **Step 2: Update race-detail.html — Generate Lineup button**

In `src/main/resources/templates/admin/race-detail.html`, add the following block after the toolbar `</div>` (line 23) and before the Score Banner:

```html
    <!-- Generate Lineup -->
    <div th:if="${canGenerateLineup}" style="margin-bottom:16px;">
        <form th:action="@{/admin/races/{id}/generate-lineup(id=${race.id})}" method="post" style="display:inline;">
            <button type="submit" class="btn btn-primary" style="width:100%;padding:12px;font-size:15px;">
                Generate Lineup Graphic
            </button>
        </form>
    </div>
    <div th:if="${!canGenerateLineup}" style="margin-bottom:16px;">
        <button class="btn btn-secondary" disabled style="width:100%;padding:12px;font-size:15px;opacity:0.5;">
            Generate Lineup Graphic
        </button>
        <small class="text-dim" style="display:block;margin-top:4px;">
            <span th:if="${lineupMissing}">No lineup assigned for this race.</span>
            <span th:if="${cardsMissing}">Team cards must be generated first.</span>
        </small>
    </div>
```

- [ ] **Step 3: Update race-detail.html — Download button per attachment**

In `src/main/resources/templates/admin/race-detail.html`, in the attachment item section (around line 133), add a download button before the delete form. Replace the block from `</div>` (after attachment-info) to the delete form with:

```html
                <div style="display:flex;gap:6px;align-items:center;">
                    <a th:if="${att.type.name() == 'FILE'}"
                       th:href="@{/admin/races/attachments/{aid}/download(aid=${att.id})}"
                       class="btn btn-secondary btn-sm">Download</a>
                    <form th:action="@{/admin/races/attachments/{aid}/delete(aid=${att.id})}" method="post"
                          onsubmit="return confirm('Delete this attachment?')">
                        <button type="submit" class="btn btn-danger btn-sm">Delete</button>
                    </form>
                </div>
```

- [ ] **Step 4: Run full test suite**

Run: `./mvnw verify`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/ctc/admin/controller/RaceController.java \
       src/main/resources/templates/admin/race-detail.html
git commit -m "feat: Generate Lineup Button + Attachment Download auf Race Detail"
```

---

## Task 5: Remove old Team Card Template Editor page

**Files:**
- Delete: `src/main/resources/templates/admin/team-card-template-editor.html`

- [ ] **Step 1: Delete the old template editor page**

Delete `src/main/resources/templates/admin/team-card-template-editor.html` — it's fully replaced by the Template Editors page.

- [ ] **Step 2: Verify no references remain**

Run: `grep -r "team-card-template-editor" src/` — should return nothing (the TeamCardController template endpoint was already removed in Task 3).

- [ ] **Step 3: Run full test suite**

Run: `./mvnw verify`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git rm src/main/resources/templates/admin/team-card-template-editor.html
git commit -m "cleanup: Alten Team Card Template Editor entfernt (ersetzt durch Template Editors)"
```

---

## Task 6: Visual Verification

**Files:** None (read-only verification)

- [ ] **Step 1: Start dev server with demo data**

Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`

- [ ] **Step 2: Verify Template Editors page**

Navigate to `http://localhost:9090/admin/tools/template-editors`:
- Both tabs (Team Cards, Lineup) work
- Switching tabs preserves content
- Save/Reset buttons function
- Custom/Default badge updates

- [ ] **Step 3: Verify sidebar navigation**

Check that "Template Editors" appears under Tools in the sidebar and highlights correctly.

- [ ] **Step 4: Generate Team Cards first**

Navigate to `http://localhost:9090/admin/tools/team-cards`, select a season, and generate all team cards (prerequisite for lineup generation).

- [ ] **Step 5: Verify Generate Lineup on Race Detail**

Navigate to a Race detail page with lineup data:
- Generate Lineup button is visible and enabled
- Click generates a lineup graphic
- Graphic appears as attachment
- Download button works on the attachment

- [ ] **Step 6: Inspect generated lineup graphic**

Use `playwright-cli` to open the generated PNG and visually verify:
- 1920×1080 dimensions
- Header: "Community Team Cup", year, "Lineups", matchday name, CTC logo
- Main: Team cards with white border/rounded corners, driver pairings, no line breaks on names
- Footer: Positions, season name
- All text in dark tones on header/footer, white on black main area
