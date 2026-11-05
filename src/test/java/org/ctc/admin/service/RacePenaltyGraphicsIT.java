package org.ctc.admin.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.service.RacePenaltyService;
import org.ctc.domain.service.ScoringService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class RacePenaltyGraphicsIT extends RacePenaltyFixture {
    @Autowired TemplateEngine engine;
    @Autowired ScoringService scoring;
    @Autowired RaceRepository raceRows;
    @TempDir Path cards;

    private final java.util.List<java.util.UUID> extraDrivers = new java.util.ArrayList<>();
    @Override
    @org.junit.jupiter.api.AfterEach
    protected void cleanUp() {
        super.cleanUp();
        drivers.deleteAllById(extraDrivers);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void givenNettedPenaltyWithPrivateReason_whenRenderingAllScoreGraphics_thenShowOfficialScoresAndOnlyPublicBreakdown(int extra) throws Exception {
        // given
        if (extra > 0) tx.executeWithoutResult(status -> {
            var race = raceRows.findById(raceId).orElseThrow();
            race.getMatchday().getPhase().getRaceScoring().setRacePoints("80,70,1,1,1,1,1,1");
            var replacements = new java.util.ArrayList<>(results(false));
            for (int i = 0; i < extra; i++) {
                var driver = helper.createDriver("TestGraphicPenalty" + java.util.UUID.randomUUID().toString().substring(0, 8), "Test extra driver");
                extraDrivers.add(driver.getId());
                lineups.save(new org.ctc.domain.model.RaceLineup(race, driver, race.getHomeTeam()));
                replacements.add(new org.ctc.domain.service.RaceService.RaceResultData(driver.getId(), null, null, i + 3, i + 3, false));
            }
            races.saveResults(raceId, replacements);
        });
        var view = penalties.view(raceId);
        penalties.save(raceId, new RacePenaltyService.Correction(homeId, awayId, 20, 10, view.revision(), view.fingerprint(), view.generation(),
                "DistinctivePrivatePenaltyReason"), "DistinctivePrivateManager");
        var directory = cards.resolve("team-cards").resolve(seasonId.toString()); Files.createDirectories(directory);
        var pixel = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3N8AAAAASUVORK5CYII=");
        for (var id : java.util.List.of(homeId, awayId)) {
            String name = teams.findById(id).orElseThrow().getShortName().replaceAll("[^a-zA-Z0-9._-]", "_");
            Files.write(directory.resolve(name + ".png"), pixel);
        }
        // when / then
        tx.executeWithoutResult(status -> {
            var race = raceRows.findById(raceId).orElseThrow();
            var results = new ResultsGraphicService(engine, scoring, lineups, cards.toString());
            var provisional = new ProvisionalScoresGraphicService(engine, scoring, lineups, cards.toString(), null);
            var matchResults = new MatchResultsGraphicService(engine, scoring, cards.toString());
            try {
                var html = results.buildHtml(race);
                var document = Jsoup.parse(html);
                assertThat(document.select(".team-total")).extracting(element -> element.text()).containsExactly(String.valueOf(70 + extra), "70");
                assertThat(document.select(".penalty-summary").text()).contains("Gross " + (80 + extra) + "; penalty total 20; net deduction 10; official " + (70 + extra), "Gross 70; penalty total 10; net deduction 0; official 70");
                assertThat(html).doesNotContain("DistinctivePrivatePenaltyReason", "DistinctivePrivateManager");
                Files.createDirectories(Path.of(".screenshots"));
                if (extra == 0) SandboxedHtmlRenderer.screenshot(html, 1920, 1080, false, Path.of(".screenshots/team-penalties-results.png"));
                var context = provisional.buildContext(race, 1, race.getHomeTeam(), race.getAwayTeam());
                assertThat(context.getVariable("homeOverallTotal")).isEqualTo(70 + extra); assertThat(context.getVariable("awayOverallTotal")).isEqualTo(70);
                assertThat(context.getVariableNames()).doesNotContain("race", "history", "reason", "actorId");
                var provisionalHtml = engine.process("admin/provisional-scores-render", context);
                assertThat(provisionalHtml).doesNotContain("DistinctivePrivatePenaltyReason", "DistinctivePrivateManager").contains("net deduction 10; official " + (70 + extra));
                if (extra == 0) SandboxedHtmlRenderer.screenshot(provisionalHtml, 1920, 1080, false, Path.of(".screenshots/team-penalties-provisional.png"));
                var rows = matchResults.buildRaceRows(matches.findById(matchId).orElseThrow());
                assertThat(rows).hasSize(1); assertThat(rows.getFirst().homePoints()).isEqualTo(70 + extra); assertThat(rows.getFirst().awayPoints()).isEqualTo(70);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }
}
