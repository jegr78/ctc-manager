package org.ctc.tools;

import org.ctc.admin.service.StandingsGraphicService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.ctc.admin.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:standings-preview;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ctc.outbound.enabled=false",
        "app.discord.dev-seed.enabled=false",
        "app.discord.picks.enabled=false",
        "app.discord.incidents.delivery-enabled=false",
        "app.seed.generate-team-cards=false",
        "app.upload-dir=target/manual-standings-preview/uploads",
        "ctc.site.output-dir=target/manual-standings-preview/site"
})
@ActiveProfiles({"dev"})
@Tag("preview")
@Transactional
class StandingsGraphicPreview {

    @Autowired private TestDataService testData;

    @BeforeEach void seedPreviewData() { testData.seed(); }

	@Autowired
	StandingsGraphicService standingsGraphicService;

	@Autowired
	SeasonRepository seasonRepository;

	@Test
	void renderLeaguePreview_2026() throws Exception {
		Season season = seasonRepository.findAll().stream()
				.filter(s -> s.getYear() == 2026 && s.getName().equals("Regular Season"))
				.findFirst().orElseThrow();
		SeasonPhase regular = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR)
				.findFirst().orElseThrow();
		writePngs("standings-league-2026", standingsGraphicService.generateStandingsBytes(season, regular));
	}

	@Test
	void renderGroupsPreview_2023() throws Exception {
		Season season = seasonRepository.findAll().stream()
				.filter(s -> s.getYear() == 2023 && s.getName().equals("Season 2023"))
				.findFirst().orElseThrow();
		SeasonPhase regular = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR)
				.findFirst().orElseThrow();
		writePngs("standings-groups-2023", standingsGraphicService.generateStandingsBytes(season, regular));
	}

	private static void writePngs(String prefix, List<byte[]> pngs) throws Exception {
		Path dir = Paths.get(".screenshots");
		Files.createDirectories(dir);
		for (int i = 0; i < pngs.size(); i++) {
			Path out = dir.resolve(prefix + "-" + (i + 1) + ".png");
			Files.write(out, pngs.get(i));
			System.out.println("Wrote preview: " + out.toAbsolutePath());
		}
	}
}
