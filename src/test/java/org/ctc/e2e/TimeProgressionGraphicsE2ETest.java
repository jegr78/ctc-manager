package org.ctc.e2e;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.TestHelper;
import org.ctc.admin.service.LobbySettingsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
class TimeProgressionGraphicsE2ETest extends PlaywrightConfig {
    @Autowired TemplateEngine engine;
    @Autowired TestHelper helper;

    @Test
    void givenBothModes_whenGeneratingBothGraphics_thenRealTemplatesRenderPngs() throws Exception {
        // given
        Path output = Path.of(".screenshots/time-progression-graphics");
        var standings = mock(StandingsService.class);
        when(standings.calculateStandings(any(), any())).thenReturn(List.of());
        var settingsService = new SettingsGraphicService(engine, standings, mock(PlayoffSeedRepository.class), output.toString());
        var lobbyService = new LobbySettingsGraphicService(engine, output.toString());
        var season = new Season("Test Time", 2026, 1);
        season.setId(UUID.randomUUID());
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(PhaseTestFixtures.matchdayInRegularPhase(season, "Test MD1", 1));
        race.setHomeTeamOverride(new Team("Test Home", "TH"));
        race.setAwayTeamOverride(new Team("Test Away", "TA"));
        race.setTrack(new Track("Test Track"));
        race.setCar(new Car("Test", "Car"));
        race.setSettings(helper.completeRaceSettings(race));
        Path cards = output.resolve("team-cards").resolve(season.getId().toString());
        Files.createDirectories(cards);
        for (String team : List.of("TH", "TA")) {
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", cards.resolve(team + ".png").toFile());
        }
        // when / then
        for (TimeProgressionMode mode : TimeProgressionMode.values()) {
            race.getSettings().setTimeProgressionMode(mode);
            race.getSettings().setTimeProgressionMultiplier(mode == TimeProgressionMode.MULTIPLIER ? 0 : null);
            settingsService.generateSettings(race);
            lobbyService.generateLobbySettings(race);
            Path generated = output.resolve("races").resolve(race.getId().toString());
            for (String name : List.of("settings", "lobby-settings")) {
                Path png = generated.resolve(name + ".png");
                assertThat(ImageIO.read(png.toFile()).getWidth()).isEqualTo(1920);
                Files.copy(png, output.resolve(name + "-" + mode + ".png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
