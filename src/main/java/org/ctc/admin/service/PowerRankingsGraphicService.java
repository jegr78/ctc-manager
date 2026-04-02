package org.ctc.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.PowerRankingsGraphicData;
import org.ctc.admin.dto.PowerRankingsGraphicData.PowerRankingEntry;
import org.ctc.admin.dto.RankedTeamData;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PowerRankingsGraphicService extends AbstractGraphicService {

    private static final String DEFAULT_TEMPLATE = "templates/admin/power-rankings-render.html";
    private static final String DEFAULT_TEMPLATE_PATH = "admin/power-rankings-render";
    private static final String CUSTOM_TEMPLATE_FILE = "power-rankings-template.html";

    private final SeasonRepository seasonRepository;
    private final SeasonTeamRepository seasonTeamRepository;

    public PowerRankingsGraphicService(TemplateEngine templateEngine,
                                       SeasonRepository seasonRepository,
                                       SeasonTeamRepository seasonTeamRepository,
                                       @Value("${app.upload-dir:uploads}") String uploadDir) {
        super(templateEngine, uploadDir);
        this.seasonRepository = seasonRepository;
        this.seasonTeamRepository = seasonTeamRepository;
    }

    public List<RankedTeamData> loadTeamsForSeasonGroup(int year, int number) {
        List<Season> seasons = seasonRepository.findByYearAndNumber(year, number);
        if (seasons.isEmpty()) {
            return List.of();
        }

        // Collect all SeasonTeams across all seasons with this (year, number)
        Map<UUID, SeasonTeam> seasonTeamMap = new LinkedHashMap<>();
        for (Season season : seasons) {
            for (SeasonTeam st : seasonTeamRepository.findBySeasonId(season.getId())) {
                seasonTeamMap.putIfAbsent(st.getTeam().getId(), st);
            }
        }

        // Collect all team IDs in the group
        Set<UUID> allTeamIds = seasonTeamMap.keySet();

        // Filter: exclude parent teams whose sub-teams are also in the season group
        List<SeasonTeam> filtered = seasonTeamMap.values().stream()
                .filter(st -> {
                    Team team = st.getTeam();
                    if (!team.hasSubTeams()) {
                        return true; // standalone or sub-team without own sub-teams
                    }
                    // Parent with sub-teams: include only if none of its sub-teams are in the group
                    return team.getSubTeams().stream()
                            .noneMatch(sub -> allTeamIds.contains(sub.getId()));
                })
                .toList();

        // Sort by rating DESC (nulls last), then shortName ASC
        return filtered.stream()
                .sorted(Comparator
                        .<SeasonTeam, Integer>comparing(
                                st -> st.getRating() != null ? st.getRating() : Integer.MIN_VALUE,
                                Comparator.reverseOrder())
                        .thenComparing(st -> st.getTeam().getShortName()))
                .map(st -> new RankedTeamData(
                        st.getTeam().getId(),
                        st.getTeam().getName(),
                        st.getTeam().getShortName(),
                        st.getEffectiveLogoUrl(),
                        st.getEffectivePrimaryColor(),
                        st.getRating()))
                .toList();
    }

    public byte[] generateRankings(int year, int number, String subtitle, List<UUID> teamIds) throws IOException {
        List<Season> seasons = seasonRepository.findByYearAndNumber(year, number);
        if (seasons.isEmpty()) {
            throw new IllegalStateException("No seasons found for year=" + year + " number=" + number);
        }

        // Build SeasonTeam lookup across all seasons
        Map<UUID, SeasonTeam> seasonTeamMap = new HashMap<>();
        for (Season season : seasons) {
            for (SeasonTeam st : seasonTeamRepository.findBySeasonId(season.getId())) {
                seasonTeamMap.putIfAbsent(st.getTeam().getId(), st);
            }
        }

        // Build ranking entries in the order of teamIds
        List<PowerRankingEntry> entries = new ArrayList<>();
        for (int i = 0; i < teamIds.size(); i++) {
            UUID teamId = teamIds.get(i);
            SeasonTeam st = seasonTeamMap.get(teamId);
            if (st == null) continue;

            Team team = st.getTeam();
            entries.add(new PowerRankingEntry(
                    i + 1,
                    team.getName(),
                    team.getShortName(),
                    encodeLogoBase64(st),
                    st.getEffectivePrimaryColor(),
                    st.getEffectiveSecondaryColor(),
                    st.getEffectiveAccentColor()
            ));
        }

        // Split into two columns
        int mid = (entries.size() + 1) / 2;
        List<PowerRankingEntry> leftColumn = entries.subList(0, Math.min(mid, entries.size()));
        List<PowerRankingEntry> rightColumn = mid < entries.size() ? entries.subList(mid, entries.size()) : List.of();

        String title = "Power Rankings " + year;
        var data = new PowerRankingsGraphicData(
                title, subtitle,
                encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"),
                encodeClasspathResource(FONT_CLASSPATH, "font/woff2"),
                entries, leftColumn, rightColumn
        );

        var ctx = new Context();
        ctx.setVariable("data", data);

        String html = renderTemplate(ctx);
        return renderToBytes(html);
    }

    private String encodeLogoBase64(SeasonTeam seasonTeam) {
        String logoUrl = seasonTeam.getEffectiveLogoUrl();
        if (logoUrl == null) return null;
        return encodeCardBase64(logoUrl);
    }

    private String renderTemplate(Context ctx) throws IOException {
        Path customTemplate = uploadDir.resolve(CUSTOM_TEMPLATE_FILE);
        if (Files.exists(customTemplate)) {
            String template = Files.readString(customTemplate);
            return processStringTemplate(template, ctx);
        }
        return templateEngine.process(DEFAULT_TEMPLATE_PATH, ctx);
    }

    private byte[] renderToBytes(String html) throws IOException {
        Path tempFile = Files.createTempFile("power-rankings-", ".png");
        try {
            renderScreenshot(html, tempFile);
            return Files.readAllBytes(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // Template management

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
        log.info("Saved custom power rankings template");
    }

    public void resetTemplate() throws IOException {
        Files.deleteIfExists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
        log.info("Reset power rankings template to default");
    }

    public boolean hasCustomTemplate() {
        return Files.exists(uploadDir.resolve(CUSTOM_TEMPLATE_FILE));
    }
}
