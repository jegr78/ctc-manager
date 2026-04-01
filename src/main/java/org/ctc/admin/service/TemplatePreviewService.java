package org.ctc.admin.service;

import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.dto.MatchdayGraphicData.MatchGraphicRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class TemplatePreviewService {

    private static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
    private static final String CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png";
    private static final String COMMENTATOR_CLASSPATH = "static/admin/img/commentator.png";

    private String cachedFontBase64;
    private String cachedLogoBase64;
    private String cachedCommentatorBase64;

    public String renderPreview(String templateType, String templateContent) {
        var ctx = switch (templateType) {
            case "team-cards" -> buildTeamCardContext();
            case "lineup" -> buildLineupContext();
            case "settings" -> buildSettingsContext();
            case "race-results" -> buildRaceResultsContext();
            case "matchday-overview" -> buildMatchdayOverviewContext();
            case "matchday-schedule" -> buildMatchdayScheduleContext();
            case "matchday-results" -> buildMatchdayResultsContext();
            case "overlay" -> buildOverlayContext();
            default -> throw new IllegalArgumentException("Unknown template type: " + templateType);
        };
        return processTemplate(templateContent, ctx);
    }

    private Context buildTeamCardContext() {
        var ctx = new Context();
        ctx.setVariable("teamName", "Team Alpha");
        ctx.setVariable("subTeamLabel", null);
        ctx.setVariable("rating", 85);
        ctx.setVariable("points", 42);
        ctx.setVariable("record", "3 - 1 - 0");
        ctx.setVariable("primaryColor", "#E63946");
        ctx.setVariable("secondaryColor", "#1D3557");
        ctx.setVariable("accentColor", "#457B9D");
        ctx.setVariable("gradientColor", "#1D3557");
        ctx.setVariable("logoBase64", getLogoBase64());
        ctx.setVariable("fontBase64", getFontBase64());
        return ctx;
    }

    private Context buildLineupContext() {
        var ctx = buildRaceHeaderContext();
        var pairings = List.of(
                new LineupPairing("Player_One", "Player_Seven"),
                new LineupPairing("Player_Two", "Player_Eight"),
                new LineupPairing("Player_Three", "Player_Nine"),
                new LineupPairing("Player_Four", "Player_Ten"),
                new LineupPairing("Player_Five", "Player_Eleven"),
                new LineupPairing("Player_Six", "Player_Twelve")
        );
        ctx.setVariable("pairings", pairings);
        return ctx;
    }

    private Context buildSettingsContext() {
        var ctx = buildRaceHeaderContext();
        ctx.setVariable("carName", "Mazda RX-Vision GT3");
        ctx.setVariable("trackName", "Nürburgring 24h");
        ctx.setVariable("numberOfLaps", 5);
        ctx.setVariable("tyreWearMultiplier", "10x");
        ctx.setVariable("fuelConsumptionMultiplier", "3x");
        ctx.setVariable("refuelingSpeed", "Fast");
        ctx.setVariable("initialFuel", "Max");
        ctx.setVariable("numberOfRequiredPitStops", 1);
        ctx.setVariable("timeProgressionMultiplier", "60x");
        ctx.setVariable("weather", "Clear");
        ctx.setVariable("timeOfDay", "14:00");
        ctx.setVariable("availableTyres", "SS, SM, SH, RH, IM, HW");
        ctx.setVariable("mandatoryTyres", "SH");
        return ctx;
    }

    private Context buildRaceResultsContext() {
        var ctx = buildRaceHeaderContext();
        var rows = List.of(
                new ResultRow("Player_One", 25, 22, "Player_Seven"),
                new ResultRow("Player_Two", 20, 18, "Player_Eight"),
                new ResultRow("Player_Three", 17, 15, "Player_Nine"),
                new ResultRow("Player_Four", 14, 12, "Player_Ten"),
                new ResultRow("Player_Five", 11, 9, "Player_Eleven"),
                new ResultRow("Player_Six", 8, 6, "Player_Twelve")
        );
        ctx.setVariable("resultRows", rows);
        ctx.setVariable("homeTotal", 95);
        ctx.setVariable("awayTotal", 82);
        ctx.setVariable("homeIsWinner", true);
        ctx.setVariable("awayIsWinner", false);
        return ctx;
    }

    private Context buildOverlayContext() {
        var ctx = new Context();
        var data = buildMatchdayData(false, true);
        var match = data.matches().getFirst();

        ctx.setVariable("homeTeamName", match.homeTeamName());
        ctx.setVariable("homeTeamShortName", match.homeTeamShortName());
        ctx.setVariable("homeLogoBase64", match.homeLogoBase64());
        ctx.setVariable("homePrimaryColor", match.homePrimaryColor());
        ctx.setVariable("homeSecondaryColor", match.homeSecondaryColor());
        ctx.setVariable("homeRecord", match.homeRecord());
        ctx.setVariable("awayTeamName", match.awayTeamName());
        ctx.setVariable("awayTeamShortName", match.awayTeamShortName());
        ctx.setVariable("awayLogoBase64", match.awayLogoBase64());
        ctx.setVariable("awayPrimaryColor", match.awayPrimaryColor());
        ctx.setVariable("awaySecondaryColor", match.awaySecondaryColor());
        ctx.setVariable("awayRecord", match.awayRecord());
        ctx.setVariable("seasonYear", data.seasonYear());
        ctx.setVariable("matchdayName", data.matchdayLabel());
        ctx.setVariable("ctcLogoBase64", getLogoBase64());
        ctx.setVariable("commentatorBase64", getCommentatorBase64());
        ctx.setVariable("fontBase64", getFontBase64());
        return ctx;
    }

    private Context buildMatchdayOverviewContext() {
        var ctx = new Context();
        ctx.setVariable("data", buildMatchdayData(false, false));
        return ctx;
    }

    private Context buildMatchdayScheduleContext() {
        var ctx = new Context();
        ctx.setVariable("data", buildMatchdayData(true, false));
        return ctx;
    }

    private Context buildMatchdayResultsContext() {
        var ctx = new Context();
        ctx.setVariable("data", buildMatchdayData(false, true));
        return ctx;
    }

    private Context buildRaceHeaderContext() {
        var ctx = new Context();
        ctx.setVariable("seasonYear", "2026");
        ctx.setVariable("matchdayName", "MD 1");
        ctx.setVariable("seasonName", "Season 2026");
        ctx.setVariable("homeCardBase64", buildPlaceholderCard("#E63946", "ALF"));
        ctx.setVariable("awayCardBase64", buildPlaceholderCard("#1D3557", "BRV"));
        ctx.setVariable("homePosition", 1);
        ctx.setVariable("awayPosition", 2);
        ctx.setVariable("ctcLogoBase64", getLogoBase64());
        ctx.setVariable("fontBase64", getFontBase64());
        return ctx;
    }

    private MatchdayGraphicData buildMatchdayData(boolean withSchedule, boolean withScores) {
        String logo = getLogoBase64();
        String font = getFontBase64();

        var matches = List.of(
                new MatchGraphicRow(
                        "Team Alpha", "ALF", logo,
                        "#E63946", "#1D3557", "#457B9D", 1, "3 - 1 - 0",
                        "Team Bravo", "BRV", logo,
                        "#2A9D8F", "#264653", "#E9C46A", 2, "2 - 1 - 1",
                        withSchedule ? "Fri, 20 Mar. 19:30 GMT" : null,
                        withScores ? 54 : null, withScores ? 42 : null
                ),
                new MatchGraphicRow(
                        "Team Charlie", "CHL", logo,
                        "#F4A261", "#E76F51", "#2A9D8F", 3, "2 - 2 - 0",
                        "Team Delta", "DLT", logo,
                        "#6A0572", "#AB83A1", "#E8D5B7", 4, "1 - 2 - 1",
                        withSchedule ? "Sat, 21 Mar. 20:00 GMT" : null,
                        withScores ? 48 : null, withScores ? 50 : null
                ),
                new MatchGraphicRow(
                        "Team Echo", "ECH", logo,
                        "#0077B6", "#00B4D8", "#90E0EF", 5, "1 - 3 - 0",
                        "Team Foxtrot", "FXT", logo,
                        "#606C38", "#283618", "#DDA15E", 6, "0 - 3 - 1",
                        withSchedule ? "Sun, 22 Mar. 18:00 GMT" : null,
                        withScores ? 51 : null, withScores ? 45 : null
                )
        );

        return new MatchdayGraphicData("Match Day 3", "Season 2026", "2026", logo, font, matches);
    }

    private String buildPlaceholderCard(String color, String label) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1920" viewBox="0 0 1080 1920">
                    <rect width="1080" height="1920" fill="%s"/>
                    <text x="540" y="960" text-anchor="middle" dominant-baseline="central"
                          font-family="Arial,sans-serif" font-size="120" font-weight="bold" fill="white">%s</text>
                </svg>
                """.formatted(color, label);
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes());
    }

    private String getFontBase64() {
        if (cachedFontBase64 == null) {
            cachedFontBase64 = encodeClasspathResource(FONT_CLASSPATH, "font/woff2");
        }
        return cachedFontBase64;
    }

    private String getLogoBase64() {
        if (cachedLogoBase64 == null) {
            cachedLogoBase64 = encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png");
        }
        return cachedLogoBase64;
    }

    private String getCommentatorBase64() {
        if (cachedCommentatorBase64 == null) {
            cachedCommentatorBase64 = encodeClasspathResource(COMMENTATOR_CLASSPATH, "image/png");
        }
        return cachedCommentatorBase64;
    }

    private String encodeClasspathResource(String classpathLocation, String mimeType) {
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

    private String processTemplate(String template, Context ctx) {
        var engine = new SpringTemplateEngine();
        var resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        engine.setTemplateResolver(resolver);
        return engine.process(template, ctx);
    }

    // Records for template preview data
    public record LineupPairing(String homeDriver, String awayDriver) {}
    public record ResultRow(String homeDriver, int homePoints, int awayPoints, String awayDriver) {}
}
