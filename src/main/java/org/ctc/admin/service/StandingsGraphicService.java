package org.ctc.admin.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.ctc.domain.service.StandingsService.TeamStanding;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
public class StandingsGraphicService extends AbstractGraphicService {

	private static final String TEMPLATE_PATH = "admin/standings-render";

	private final StandingsService standingsService;
	private final SeasonTeamRepository seasonTeamRepository;

	public StandingsGraphicService(TemplateEngine templateEngine,
	                               StandingsService standingsService,
	                               SeasonTeamRepository seasonTeamRepository,
	                               @Value("${app.upload-dir:uploads}") String uploadDir) {
		super(templateEngine, uploadDir);
		this.standingsService = standingsService;
		this.seasonTeamRepository = seasonTeamRepository;
	}

	public List<byte[]> generateStandingsBytes(Season season, SeasonPhase phase) throws IOException {
		if (phase.getLayout() == PhaseLayout.GROUPS) {
			List<SeasonPhaseGroup> groups = phase.getGroups().stream()
					.sorted(Comparator.comparingInt(SeasonPhaseGroup::getSortIndex))
					.toList();
			List<byte[]> out = new ArrayList<>(groups.size());
			for (SeasonPhaseGroup g : groups) {
				out.add(renderSingleStandings(season, phase, g, g.getId()));
			}
			return out;
		}
		return List.of(renderSingleStandings(season, phase, null, null));
	}

	private byte[] renderSingleStandings(Season season, SeasonPhase phase, @Nullable SeasonPhaseGroup group,
	                                     @Nullable UUID groupId) throws IOException {
		List<TeamStanding> standings = standingsService.calculateStandings(phase.getId(), groupId);
		Map<UUID, SeasonTeam> seasonTeamsByTeamId = seasonTeamRepository.findBySeasonId(season.getId()).stream()
				.collect(Collectors.toMap(st -> st.getTeam().getId(), st -> st, (a, b) -> a));

		List<StandingsRow> rows = new ArrayList<>(standings.size());
		for (int i = 0; i < standings.size(); i++) {
			TeamStanding ts = standings.get(i);
			Team team = ts.getTeam();
			SeasonTeam st = seasonTeamsByTeamId.get(team.getId());
			rows.add(new StandingsRow(
					i + 1,
					team.getName(),
					team.getShortName(),
					st != null ? encodeCardBase64(st.getEffectiveLogoUrl()) : null,
					st != null ? Optional.ofNullable(st.getEffectivePrimaryColor()).orElse("#444") : "#444",
					ts.getWins(),
					ts.getDraws(),
					ts.getLosses(),
					ts.getPoints()
			));
		}

		Context ctx = new Context();
		ctx.setVariable("seasonName", season.getName());
		ctx.setVariable("phaseLabel", phase.getPhaseType().name()
				+ (group != null ? " — " + group.getName() : ""));
		ctx.setVariable("standings", rows);
		ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
		ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));

		String html = templateEngine.process(TEMPLATE_PATH, ctx);
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

	public record StandingsRow(
			int position,
			String teamName,
			String teamShortName,
			@Nullable String teamLogoBase64,
			String primaryColor,
			int wins,
			int draws,
			int losses,
			int points
	) {
	}
}
