package org.ctc.admin.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"dev"})
@Tag("preview")
@Transactional
@Disabled("manual: re-enable + run via ./mvnw -Dtest=StandingsGraphicPreviewTest test to regenerate previews into .screenshots/")
class StandingsGraphicPreviewTest {

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
