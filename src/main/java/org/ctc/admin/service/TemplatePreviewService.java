package org.ctc.admin.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.dto.MatchdayGraphicData.MatchGraphicRow;
import org.ctc.admin.dto.PowerRankingsGraphicData;
import org.ctc.admin.dto.PowerRankingsGraphicData.PowerRankingEntry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@Slf4j
@Service
public class TemplatePreviewService {

    private static final String FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2";
    private static final String CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png";
    private static final String COMMENTATOR_CLASSPATH = "static/admin/img/commentator.png";
    private static final String VS_BADGE_CLASSPATH = "static/admin/img/vs-badge.svg";

    private static final List<String> BLOCKED_TOKENS = List.of(
            "Runtime", "ProcessBuilder", "getClass(", "Class.forName",
            ".exec(", ".invoke(", "newInstance(", "getMethod(",
            "getDeclaredMethod(", "getField(", "getDeclaredField(",
            "ClassLoader", "URLClassLoader", "ScriptEngine",
            "javax.script", "java.lang.reflect"
    );

    private volatile String cachedFontBase64;
    private volatile String cachedLogoBase64;
    private volatile String cachedCommentatorBase64;
    private volatile String cachedVsBadgeBase64;

    public String renderPreview(String templateType, String templateContent) {
        var ctx = switch (templateType) {
            case "team-cards" -> buildTeamCardContext();
            case "lineup" -> buildLineupContext();
            case "settings" -> buildSettingsContext();
            case "race-results" -> buildRaceResultsContext();
            case "match-results" -> buildMatchResultsContext();
            case "matchday-overview" -> buildMatchdayOverviewContext();
            case "matchday-schedule" -> buildMatchdayScheduleContext();
            case "matchday-results" -> buildMatchdayResultsContext();
            case "overlay" -> buildOverlayContext();
            case "power-rankings" -> buildPowerRankingsContext();
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
                new LineupPairing("Player_One", "P1", "Player_Seven", "P7"),
                new LineupPairing("Player_Two", "P2", "Player_Eight", "P8"),
                new LineupPairing("Player_Three", "P3", "Player_Nine", "P9"),
                new LineupPairing("Player_Four", "P4", "Player_Ten", "P10"),
                new LineupPairing("Player_Five", "P5", "Player_Eleven", "P11"),
                new LineupPairing("Player_Six", "P6", "Player_Twelve", "P12")
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
                new ResultRow("Player_One", "P1", 25, 22, "Player_Seven", "P7"),
                new ResultRow("Player_Two", "P2", 20, 18, "Player_Eight", "P8"),
                new ResultRow("Player_Three", "P3", 17, 15, "Player_Nine", "P9"),
                new ResultRow("Player_Four", "P4", 14, 12, "Player_Ten", "P10"),
                new ResultRow("Player_Five", "P5", 11, 9, "Player_Eleven", "P11"),
                new ResultRow("Player_Six", "P6", 8, 6, "Player_Twelve", "P12")
        );
        ctx.setVariable("resultRows", rows);
        ctx.setVariable("homeTotal", 95);
        ctx.setVariable("awayTotal", 82);
        ctx.setVariable("homeIsWinner", true);
        ctx.setVariable("awayIsWinner", false);
        return ctx;
    }

    private Context buildMatchResultsContext() {
        var ctx = buildRaceHeaderContext();
        var rows = List.of(
                new RaceRow("Race 1", 95, 82),
                new RaceRow("Race 2", 78, 91)
        );
        ctx.setVariable("raceRows", rows);
        ctx.setVariable("homeTotal", 173);
        ctx.setVariable("awayTotal", 173);
        ctx.setVariable("homeIsWinner", false);
        ctx.setVariable("awayIsWinner", false);
        return ctx;
    }

    private Context buildOverlayContext() {
        var ctx = new Context();
        var data = buildMatchdayData(false, true);
        var match = data.matches().getFirst();

        ctx.setVariable("homeTeamName", match.homeTeamName());
        ctx.setVariable("homeTeamNameHtml", formatTeamNameHtml(match.homeTeamName()));
        ctx.setVariable("homeTeamShortName", match.homeTeamShortName());
        ctx.setVariable("homeLogoBase64", match.homeLogoBase64());
        ctx.setVariable("homePrimaryColor", match.homePrimaryColor());
        ctx.setVariable("homeSecondaryColor", match.homeSecondaryColor());
        ctx.setVariable("homeRecord", match.homeRecord());
        ctx.setVariable("awayTeamName", match.awayTeamName());
        ctx.setVariable("awayTeamNameHtml", formatTeamNameHtml(match.awayTeamName()));
        ctx.setVariable("awayTeamShortName", match.awayTeamShortName());
        ctx.setVariable("awayLogoBase64", match.awayLogoBase64());
        ctx.setVariable("awayPrimaryColor", match.awayPrimaryColor());
        ctx.setVariable("awaySecondaryColor", match.awaySecondaryColor());
        ctx.setVariable("awayRecord", match.awayRecord());
        ctx.setVariable("seasonYear", data.seasonYear());
        ctx.setVariable("matchdayName", data.matchdayLabel());
        ctx.setVariable("ctcLogoBase64", getLogoBase64());
        ctx.setVariable("vsBadgeBase64", getVsBadgeBase64());
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

    private Context buildPowerRankingsContext() {
        String logo = getLogoBase64();
        String font = getFontBase64();

        var teams = List.of(
                new PowerRankingEntry(1, "Team Alpha", "ALF", logo, "#E63946", "#1D3557", "#457B9D"),
                new PowerRankingEntry(2, "Team Bravo", "BRV", logo, "#2A9D8F", "#264653", "#E9C46A"),
                new PowerRankingEntry(3, "Team Charlie", "CHL", logo, "#F4A261", "#E76F51", "#2A9D8F"),
                new PowerRankingEntry(4, "Team Delta", "DLT", logo, "#6A0572", "#AB83A1", "#E8D5B7"),
                new PowerRankingEntry(5, "Team Echo", "ECH", logo, "#0077B6", "#00B4D8", "#90E0EF"),
                new PowerRankingEntry(6, "Team Foxtrot", "FXT", logo, "#606C38", "#283618", "#DDA15E")
        );

        int mid = (teams.size() + 1) / 2;
        var data = new PowerRankingsGraphicData(
                "Power Rankings 2026", "Match Day 3",
                logo, font, teams,
                teams.subList(0, mid), teams.subList(mid, teams.size())
        );

        var ctx = new Context();
        ctx.setVariable("data", data);
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
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
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

    private String getVsBadgeBase64() {
        if (cachedVsBadgeBase64 == null) {
            cachedVsBadgeBase64 = encodeClasspathResource(VS_BADGE_CLASSPATH, "image/svg+xml");
        }
        return cachedVsBadgeBase64;
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

    private String formatTeamNameHtml(String name) {
		if (name == null) {
			return "";
		}
        String[] words = name.split("\\s+");
        if (words.length <= 3) {
            return String.join("<br>", words);
        }
        return words[0] + "<br>" + words[1] + "<br>" + String.join(" ", java.util.Arrays.copyOfRange(words, 2, words.length));
    }

    private String processTemplate(String templateContent, Context ctx) {
        validateTemplateContent(templateContent);
        var engine = new SpringTemplateEngine();
        var resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        engine.setTemplateResolver(resolver);
        return engine.process(templateContent, ctx);
    }

    public void validateTemplateContent(String templateContent) {
        if (templateContent == null) {
            return;
        }
        for (String token : BLOCKED_TOKENS) {
            if (templateContent.contains(token)) {
                throw new TemplateSecurityException("Template contains blocked expression: " + token);
            }
        }
        if (containsSpringElTypeAccess(templateContent)) {
            throw new TemplateSecurityException("Template contains blocked expression pattern");
        }
        if (templateContent.contains("__${")) {
            throw new TemplateSecurityException("Template contains blocked expression pattern");
        }
        if (containsOgnlStaticAccess(templateContent)) {
            throw new TemplateSecurityException("Template contains blocked expression pattern");
        }
    }

    private boolean containsSpringElTypeAccess(String content) {
        int idx = 0;
        while ((idx = content.indexOf("${", idx)) != -1) {
            int end = content.indexOf('}', idx + 2);
			if (end == -1) {
				break;
			}
            String expr = content.substring(idx + 2, end);
            int tIdx = 0;
            while ((tIdx = expr.indexOf('T', tIdx)) != -1) {
                int next = tIdx + 1;
                while (next < expr.length() && expr.charAt(next) == ' ') {
                    next++;
                }
                if (next < expr.length() && expr.charAt(next) == '(') {
                    return true;
                }
                tIdx++;
            }
            idx = end + 1;
        }
        return false;
    }

    private boolean containsOgnlStaticAccess(String content) {
        int idx = 0;
        while ((idx = content.indexOf("${", idx)) != -1) {
            int end = content.indexOf('}', idx + 2);
			if (end == -1) {
				break;
			}
            String expr = content.substring(idx + 2, end);
            for (int i = 0; i < expr.length() - 1; i++) {
                if (expr.charAt(i) == '@' && Character.isLetterOrDigit(expr.charAt(i + 1))) {
                    return true;
                }
            }
            idx = end + 1;
        }
        return false;
    }

    public static class TemplateSecurityException extends RuntimeException {
        public TemplateSecurityException(String message) {
            super(message);
        }
    }

    public record LineupPairing(String homeDriver, String homeNickname, String awayDriver, String awayNickname) {}

    public record ResultRow(String homeDriver, String homeNickname, int homePoints, int awayPoints, String awayDriver, String awayNickname) {}

    public record RaceRow(String label, int homePoints, int awayPoints) {}
}
