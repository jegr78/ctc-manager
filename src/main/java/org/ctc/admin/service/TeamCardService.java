package org.ctc.admin.service;

import com.microsoft.playwright.*;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.service.StandingsService;
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

@Slf4j
@Service
public class TeamCardService {

    private static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
    private static final String DEFAULT_TEMPLATE = "templates/admin/team-card-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "team-card-template.html";

    private final TemplateEngine templateEngine;
    private final StandingsService standingsService;
    private final Path uploadDir;

    public TeamCardService(TemplateEngine templateEngine,
                           StandingsService standingsService,
                           @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.templateEngine = templateEngine;
        this.standingsService = standingsService;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String generateCard(SeasonTeam seasonTeam) throws IOException {
        var team = seasonTeam.getTeam();
        var season = seasonTeam.getSeason();

        var standings = standingsService.calculateStandings(season.getId());
        var teamStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(team.getId()))
                .findFirst()
                .orElse(null);

        int points = teamStanding != null ? teamStanding.getPoints() : 0;
        String record = teamStanding != null ? teamStanding.getMatchRecord() : "0 - 0 - 0";

        String logoBase64 = encodeLogoBase64(seasonTeam.getEffectiveLogoUrl());
        String fontBase64 = encodeFontBase64();

        String subTeamLabel = null;
        if (team.isSubTeam()) {
            String parentShort = team.getParentTeam().getShortName();
            String teamShort = team.getShortName();
            subTeamLabel = teamShort.replace(parentShort, "").trim();
            if (subTeamLabel.isEmpty()) subTeamLabel = null;
        }

        var ctx = new Context();
        ctx.setVariable("teamName", team.isSubTeam() ? team.getParentTeam().getName() : team.getName());
        ctx.setVariable("subTeamLabel", subTeamLabel);
        ctx.setVariable("rating", seasonTeam.getRating());
        ctx.setVariable("points", points);
        ctx.setVariable("record", record);
        String primaryColor = seasonTeam.getEffectivePrimaryColor();
        String secondaryColor = seasonTeam.getEffectiveSecondaryColor();
        String accentColor = seasonTeam.getEffectiveAccentColor();

        ctx.setVariable("primaryColor", primaryColor);
        ctx.setVariable("secondaryColor", secondaryColor);
        ctx.setVariable("accentColor", accentColor);
        ctx.setVariable("gradientColor", computeGradientColor(primaryColor, secondaryColor, accentColor));
        ctx.setVariable("logoBase64", logoBase64);
        ctx.setVariable("fontBase64", fontBase64);

        String html = renderTemplate(ctx);

        Path tempFile = Files.createTempFile("team-card-", ".html");
        Files.writeString(tempFile, html);

        String storagePath = getCardStoragePath(seasonTeam);
        Path outputFile = uploadDir.resolve(storagePath);
        Files.createDirectories(outputFile.getParent());

        try (Playwright pw = Playwright.create();
             Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             Page page = browser.newPage(new Browser.NewPageOptions()
                     .setViewportSize(1080, 1920))) {
            page.navigate("file://" + tempFile.toAbsolutePath());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputFile)
                    .setFullPage(false));
        } finally {
            Files.deleteIfExists(tempFile);
        }

        log.info("Generated team card: {}", outputFile);
        return getCardPath(seasonTeam);
    }

    public List<String> generateAllCards(Season season) throws IOException {
        var seasonTeamIds = season.getSeasonTeams().stream()
                .map(st -> st.getTeam().getId())
                .collect(java.util.stream.Collectors.toSet());

        var paths = new ArrayList<String>();
        for (var seasonTeam : season.getSeasonTeams()) {
            var team = seasonTeam.getTeam();
            // Skip parent teams that have sub-teams in THIS season
            boolean hasSubTeamsInSeason = team.getSubTeams().stream()
                    .anyMatch(sub -> seasonTeamIds.contains(sub.getId()));
            if (!hasSubTeamsInSeason) {
                paths.add(generateCard(seasonTeam));
            }
        }
        return paths;
    }

    public String getCardPath(SeasonTeam seasonTeam) {
        return "/uploads/team-cards/" + seasonTeam.getSeason().getId() + "/"
                + sanitizeFilename(seasonTeam.getTeam().getShortName()) + ".png";
    }

    public boolean cardExists(SeasonTeam seasonTeam) {
        return Files.exists(uploadDir.resolve(getCardStoragePath(seasonTeam)));
    }

    private String getCardStoragePath(SeasonTeam seasonTeam) {
        return "team-cards/" + seasonTeam.getSeason().getId() + "/"
                + sanitizeFilename(seasonTeam.getTeam().getShortName()) + ".png";
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    String computeGradientColor(String primary, String secondary, String accent) {
        String darkest = primary;
        double darkestLuminance = relativeLuminance(primary);
        for (String color : new String[]{secondary, accent}) {
            if (color != null) {
                double lum = relativeLuminance(color);
                if (lum < darkestLuminance) {
                    darkestLuminance = lum;
                    darkest = color;
                }
            }
        }
        return darkest != null ? darkest : "#111111";
    }

    private double relativeLuminance(String hex) {
        if (hex == null || hex.length() < 7) return 1.0;
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private String encodeLogoBase64(String logoUrl) {
        if (logoUrl == null || !logoUrl.startsWith("/uploads/")) return null;
        try {
            Path logoFile = uploadDir.resolve(logoUrl.substring("/uploads/".length())).normalize();
            if (!logoFile.startsWith(uploadDir)) {
                log.warn("Path traversal attempt in logo URL: {}", logoUrl);
                return null;
            }
            if (Files.exists(logoFile)) {
                byte[] bytes = Files.readAllBytes(logoFile);
                String mimeType = Files.probeContentType(logoFile);
                if (mimeType == null) mimeType = "image/png";
                return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.warn("Failed to encode logo: {}", logoUrl, e);
        }
        return null;
    }

    private String encodeFontBase64() {
        try {
            var resource = new ClassPathResource(FONT_CLASSPATH);
            if (resource.exists()) {
                try (var is = resource.getInputStream()) {
                    byte[] bytes = is.readAllBytes();
                    return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to encode font", e);
        }
        return null;
    }

    private String renderTemplate(Context ctx) throws IOException {
        Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
        if (Files.exists(customTemplate)) {
            String template = Files.readString(customTemplate);
            return processStringTemplate(template, ctx);
        }
        return templateEngine.process("admin/team-card-render", ctx);
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
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    public void saveTemplate(String content) throws IOException {
        Files.createDirectories(uploadDir);
        Files.writeString(uploadDir.resolve(CUSTOM_TEMPLATE_FILE), content);
        log.info("Saved custom team card template");
    }

    public void resetTemplate() throws IOException {
        Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
        Files.deleteIfExists(customTemplate);
        log.info("Reset team card template to default");
    }

    public boolean hasCustomTemplate() {
        return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
    }
}
