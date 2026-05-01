package org.ctc.sitegen;

import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-style contract test for {@link SiteGeneratorService} D-23 caller-side update.
 *
 * <p>This IT proves that {@link SiteGeneratorService#generateStandings} (and friends)
 * route through the new phase-aware service surface introduced by Plans 58-01..58-03,
 * not through the legacy seasonId-overloads.
 *
 * <p>Per-method invocation pattern: instantiate the SUT directly with mocks (no Spring
 * context), call private rendering methods via reflection-free path through the
 * {@link SiteGeneratorService#generate} entry point would also test disk I/O —
 * we want a pure contract test, so we build a minimal scenario that exercises the
 * standings + ranking call sites and asserts the phase-aware API is used.
 *
 * <p>NOTE: This test does NOT use Spring context — that ensures we are testing
 * the caller-side wiring (D-23 contract), not the runtime bean-graph.
 */
@ExtendWith(MockitoExtension.class)
class SiteGeneratorServiceIT {

    @Mock private TemplateEngine templateEngine;
    @Mock private SeasonRepository seasonRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private StandingsService standingsService;
    @Mock private DriverRankingService driverRankingService;
    @Mock private PlayoffService playoffService;
    @Mock private PlayoffBracketViewService playoffBracketViewService;
    @Mock private PlayoffRepository playoffRepository;
    @Mock private SeasonTeamRepository seasonTeamRepository;
    @Mock private SiteProperties siteProperties;
    @Mock private YouTubeScraperService youTubeScraperService;
    @Mock private SeasonPhaseService seasonPhaseService;

    private SiteGeneratorService buildSut() {
        // Use the all-args-style constructor exposed by Lombok @RequiredArgsConstructor.
        // SeasonPhaseService is added in Task 2 — once present, it must be the FINAL
        // constructor argument (after all existing finals).
        return new SiteGeneratorService(
                templateEngine,
                seasonRepository,
                matchdayRepository,
                raceRepository,
                teamRepository,
                seasonDriverRepository,
                raceResultRepository,
                raceLineupRepository,
                standingsService,
                driverRankingService,
                playoffService,
                playoffBracketViewService,
                playoffRepository,
                seasonTeamRepository,
                siteProperties,
                youTubeScraperService,
                seasonPhaseService);
    }

    @Test
    void givenSeasonWithRegularPhase_whenGenerateStandings_thenUsesPhaseAwareApi() throws Exception {
        // given
        UUID seasonId = UUID.randomUUID();
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        rs.setName("RS");
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        ms.setName("MS");

        // Note: name must NOT contain "Test" — generate() filters those out as non-production seasons
        var season = new Season("Phase58 Production Season");
        season.setId(seasonId);
        season.setYear(2026);
        season.setNumber(1);

        var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

        when(seasonPhaseService.findRegularPhase(seasonId)).thenReturn(regular);
        when(seasonPhaseService.findAllPhases(seasonId)).thenReturn(List.of(regular));
        when(standingsService.calculateStandings(regular.getId(), null)).thenReturn(List.of());
        when(driverRankingService.aggregateAcrossPhases(anyList(), eq(seasonId))).thenReturn(List.of());
        when(seasonRepository.findByActiveTrue()).thenReturn(java.util.Optional.empty());
        when(seasonRepository.findAll()).thenReturn(List.of(season));
        when(playoffRepository.findBySeasonId(seasonId)).thenReturn(java.util.Optional.empty());
        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId)).thenReturn(List.of());
        when(raceLineupRepository.findByRaceMatchdaySeasonId(seasonId)).thenReturn(List.of());
        when(seasonDriverRepository.findBySeasonId(seasonId)).thenReturn(List.of());
        when(seasonTeamRepository.findBySeasonId(seasonId)).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of());
        when(templateEngine.process(any(String.class), any())).thenReturn("<html/>");

        // siteProperties returns a fresh tempDir-style path so generate() doesn't fail on cleanOutputDirectory
        var tempDir = java.nio.file.Files.createTempDirectory("sitegen-it-").toString();
        when(siteProperties.getOutputDir()).thenReturn(tempDir);
        when(siteProperties.getLinks()).thenReturn(List.of());
        when(siteProperties.getYoutubeChannelUrl()).thenReturn("https://example.com");
        when(siteProperties.getYoutubeVideoId()).thenReturn("abcDEF12345");

        // standings/alltime calls used by overview pages
        when(standingsService.calculateAlltimeStandings(anyList())).thenReturn(List.of());
        when(driverRankingService.calculateAlltimeRanking(anyList())).thenReturn(List.of());

        var sut = buildSut();

        // when — full generate() exercises every refactored call site at least once
        sut.generate();

        // then — D-23 caller-side contract: phase-aware API used. SiteGenerator routes
        // standings through the REGULAR phase from multiple call sites (generateStandings,
        // generateTeamProfiles, generateTeamsOverview, generateAlltimeStandings inner loop) —
        // we assert it was called AT LEAST ONCE per refactored surface.
        verify(seasonPhaseService, atLeastOnce()).findRegularPhase(seasonId);
        verify(standingsService, atLeastOnce()).calculateStandings(regular.getId(), null);
        verify(driverRankingService, atLeastOnce()).aggregateAcrossPhases(anyList(), eq(seasonId));
        // explicitly verify the legacy bridges are NOT invoked (proves swap happened, not just an additive call)
        verify(standingsService, never()).calculateStandings(seasonId);
        // Phase 61 MIGR-06: DriverRankingService.calculateRanking(seasonId) was removed entirely
        // (legacy season-aware bridge gone); only calculateRankingForPhase + aggregateAcrossPhases remain.
    }
}
