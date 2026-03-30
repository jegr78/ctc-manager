package de.ctc.admin.service;

import com.microsoft.playwright.*;
import de.ctc.domain.model.Season;
import de.ctc.domain.model.SeasonTeam;
import de.ctc.domain.service.StandingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

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
        ctx.setVariable("primaryColor", seasonTeam.getEffectivePrimaryColor());
        ctx.setVariable("secondaryColor", seasonTeam.getEffectiveSecondaryColor());
        ctx.setVariable("accentColor", seasonTeam.getEffectiveAccentColor());
        ctx.setVariable("logoBase64", logoBase64);
        ctx.setVariable("fontBase64", fontBase64);

        String html = templateEngine.process("admin/team-card-render", ctx);

        Path tempFile = Files.createTempFile("team-card-", ".html");
        Files.writeString(tempFile, html);

        String storagePath = getCardStoragePath(seasonTeam);
        Path outputFile = uploadDir.resolve(storagePath);
        Files.createDirectories(outputFile.getParent());

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage(new Browser.NewPageOptions()
                    .setViewportSize(1080, 1920));
            page.navigate("file://" + tempFile.toAbsolutePath());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputFile)
                    .setFullPage(false));
            browser.close();
        } finally {
            Files.deleteIfExists(tempFile);
        }

        log.info("Generated team card: {}", outputFile);
        return getCardPath(seasonTeam);
    }

    public List<String> generateAllCards(Season season) throws IOException {
        var paths = new ArrayList<String>();
        for (var seasonTeam : season.getSeasonTeams()) {
            var team = seasonTeam.getTeam();
            if (!team.hasSubTeams()) {
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
                byte[] bytes = resource.getInputStream().readAllBytes();
                return "data:font/woff2;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.warn("Failed to encode font", e);
        }
        return null;
    }
}
