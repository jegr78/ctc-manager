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

        var lineups = raceLineupRepository.findByRaceId(race.getId());
        if (lineups.isEmpty()) throw new IllegalStateException("No lineup entries for this race");
        var pairings = buildPairings(lineups, homeTeam, awayTeam);

        String homeCardPath = "/uploads/team-cards/" + season.getId() + "/"
                + sanitizeFilename(homeTeam.getShortName()) + ".png";
        String awayCardPath = "/uploads/team-cards/" + season.getId() + "/"
                + sanitizeFilename(awayTeam.getShortName()) + ".png";

        String homeCardBase64 = encodeCardBase64(homeCardPath);
        String awayCardBase64 = encodeCardBase64(awayCardPath);
        if (homeCardBase64 == null) throw new IllegalStateException("Team card not found for " + homeTeam.getShortName());
        if (awayCardBase64 == null) throw new IllegalStateException("Team card not found for " + awayTeam.getShortName());

        var standings = standingsService.calculateStandings(season.getId());
        int homePosition = 0;
        int awayPosition = 0;
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getTeam().getId().equals(homeTeam.getId())) homePosition = i + 1;
            if (standings.get(i).getTeam().getId().equals(awayTeam.getId())) awayPosition = i + 1;
        }

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
