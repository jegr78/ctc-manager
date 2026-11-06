package org.ctc.admin.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.service.RaceDisqualificationService;
import org.ctc.domain.service.ScoringService;
import org.ctc.sitegen.MatchdaysPageGenerator;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteSlugger;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.thymeleaf.TemplateEngine;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class DisqualificationPresentationIT extends RacePenaltyFixture {
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired RaceRepository raceRows;
    @Autowired ScoringService scoring;
    @Autowired TemplateEngine engine;
    @Autowired MatchdaysPageGenerator pages;
    @Autowired SiteSlugger slugger;
    @Autowired DataAuditService audit;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path output;

    UUID dsq() {
        var id = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
                .filter(r -> r.getDriver().getId().equals(homeDriver)).findFirst().orElseThrow().getId());
        var view = disqualifications.view(raceId, id);
        disqualifications.save(raceId, id, new RaceDisqualificationService.Correction(true, view.revision(),
                view.fingerprint(), view.generation(), "DistinctivePrivateDsqReason"), "DistinctivePrivateDsqManager"); return id;
    }

    @Test
    void givenSavedDsq_whenGeneratingPublicResults_thenShowDsqAndAdvancedPositionWithoutPrivateEvidence() throws Exception {
        // given
        dsq();
        // when
        var file = tx.execute(status -> {
            var season = seasons.findById(seasonId).orElseThrow();
            var slugs = new SiteSlugs(Map.of(homeId, "test-home", awayId, "test-away"),
                    Map.of(homeDriver, "test-home-driver", awayDriver, "test-away-driver"), List.of());
            try { pages.generateDetails(new GenerationContext(output, season, "test", "Test", false, null, slugs), new SiteGeneratorService.GenerationResult()); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            var day = raceRows.findById(raceId).orElseThrow().getMatchday();
            return output.resolve("season").resolve(slugger.slugify(season.getDisplayLabel())).resolve("matchday").resolve(slugger.slugify(day.getLabel()) + ".html");
        });
        // then
        var html = Files.readString(file); var document = Jsoup.parse(html);
        assertThat(document.select(".result-position").eachText()).contains("Position DSQ", "Position 1");
        assertThat(html).doesNotContain("DistinctivePrivateDsqReason", "DistinctivePrivateDsqManager", "resultFingerprint");
        assertScores(0, 80);
    }

    @Test
    void givenSavedDsq_whenRenderingDefaultGraphics_thenShowDsqWithRetainedPointsAndOnlyPublicData() {
        // given
        dsq();
        // when / then
        tx.executeWithoutResult(status -> {
            var race = raceRows.findById(raceId).orElseThrow();
            var results = new ResultsGraphicService(engine, scoring, lineups, output.toString());
            var provisional = new ProvisionalScoresGraphicService(engine, scoring, lineups, output.toString(), null);
            try {
                var directory = output.resolve("team-cards").resolve(seasonId.toString()); Files.createDirectories(directory);
                var pixel = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3N8AAAAASUVORK5CYII=");
                for (var id : List.of(homeId, awayId)) Files.write(directory.resolve(teams.findById(id).orElseThrow().getShortName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".png"), pixel);
                var html = results.buildHtml(race);
                assertThat(Jsoup.parse(html).select(".disqualification-marker").eachText()).containsExactly("DSQ");
                assertThat(html).doesNotContain("DistinctivePrivateDsqReason", "DistinctivePrivateDsqManager");
                var context = provisional.buildContext(race, 1, race.getHomeTeam(), race.getAwayTeam());
                var preview = engine.process("admin/provisional-scores-render", context);
                assertThat(Jsoup.parse(preview).select("tbody td").eachText()).contains("DSQ");
                assertThat(preview).doesNotContain("DistinctivePrivateDsqReason", "DistinctivePrivateDsqManager");
                Files.createDirectories(Path.of(".screenshots"));
                SandboxedHtmlRenderer.screenshot(html, 1920, 1080, false, Path.of(".screenshots/disqualification-results.png"));
                SandboxedHtmlRenderer.screenshot(preview, 1920, 1080, false, Path.of(".screenshots/disqualification-provisional.png"));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }

    @Test
    void givenValidDsq_whenAuditing_thenDoNotInventPointOrAggregateMismatches() {
        // given
        dsq();
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream())
                .filter(f -> f.subject().contains(raceId.toString()) || f.subject().contains(matchId.toString())))
                .noneMatch(f -> List.of("DRIVER_DISQUALIFICATIONS", "RESULT_POINTS", "STALE_AGGREGATE").contains(f.category().name()));
        assertScores(0, 80);
    }

    @Test
    void givenInvalidHistoricalDsqFingerprint_whenAuditing_thenReportSafeIdsWithoutRepairOrLaterSectionFailure() {
        // given
        dsq(); jdbc.update("update race_disqualification_revisions set result_fingerprint=? where race_id=?", "z".repeat(64), raceId);
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream()).filter(f -> f.subject().contains(raceId.toString())))
                .anyMatch(f -> f.category().name().equals("DRIVER_DISQUALIFICATIONS") && f.evidence().contains("fingerprint"));
        assertThat(report.toString()).doesNotContain("DistinctivePrivateDsqReason", "DistinctivePrivateDsqManager");
        assertThat(jdbc.queryForObject("select result_fingerprint from race_disqualification_revisions where race_id=?", String.class, raceId)).isEqualTo("z".repeat(64));
        assertScores(0, 80);
    }
}
