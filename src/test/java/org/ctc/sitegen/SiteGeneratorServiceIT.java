package org.ctc.sitegen;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Mockito-style contract test for {@link SiteGeneratorService} D-23 caller-side update.
 *
 * <p>Phase-62 Plan-0 Task-2 update: per-page generation methods have been extracted into
 * 5 helper Spring beans ({@code StandingsPageGenerator}, {@code DriverRankingPageGenerator},
 * {@code MatchdaysPageGenerator}, {@code TeamProfilePageGenerator},
 * {@code DriverProfilePageGenerator}). The orchestrator now delegates per-season work to
 * these helpers via mock invocations. The remaining direct calls into
 * {@code StandingsService}/{@code DriverRankingService} live in the orchestrator's own
 * {@code generateTeamsOverview} / {@code generateAlltimeStandings} /
 * {@code generateAlltimeDriverRanking} paths.
 *
 * <p>NOTE: This test does NOT use Spring context — that ensures we are testing
 * the caller-side wiring (D-23 contract), not the runtime bean-graph.
 */
@ExtendWith(MockitoExtension.class)
@Tag("integration")
class SiteGeneratorServiceIT {

    @Mock private SeasonRepository seasonRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private StandingsService standingsService;
    @Mock private DriverRankingService driverRankingService;
    @Mock private PlayoffBracketViewService playoffBracketViewService;
    @Mock private PlayoffRepository playoffRepository;
    @Mock private SeasonTeamRepository seasonTeamRepository;
    @Mock private SiteProperties siteProperties;
    @Mock private YouTubeScraperService youTubeScraperService;
    @Mock private SeasonPhaseService seasonPhaseService;
    @Mock private SiteSlugger siteSlugger;
    @Mock private TemplateWriter templateWriter;
    @Mock private StandingsPageGenerator standingsPageGenerator;
    @Mock private DriverRankingPageGenerator driverRankingPageGenerator;
    @Mock private MatchdaysPageGenerator matchdaysPageGenerator;
    @Mock private TeamProfilePageGenerator teamProfilePageGenerator;
    @Mock private DriverProfilePageGenerator driverProfilePageGenerator;

    private SiteGeneratorService buildSut() {
        // Lombok @RequiredArgsConstructor field order:
        // SeasonRepository, SeasonDriverRepository, StandingsService, DriverRankingService,
        // PlayoffBracketViewService, PlayoffRepository, SeasonTeamRepository, SiteProperties,
        // YouTubeScraperService, SeasonPhaseService, SiteSlugger, TemplateWriter,
        // StandingsPageGenerator, DriverRankingPageGenerator, MatchdaysPageGenerator,
        // TeamProfilePageGenerator, DriverProfilePageGenerator
        return new SiteGeneratorService(
                seasonRepository,
                seasonDriverRepository,
                standingsService,
                driverRankingService,
                playoffBracketViewService,
                playoffRepository,
                seasonTeamRepository,
                siteProperties,
                youTubeScraperService,
                seasonPhaseService,
                siteSlugger,
                templateWriter,
                standingsPageGenerator,
                driverRankingPageGenerator,
                matchdaysPageGenerator,
                teamProfilePageGenerator,
                driverProfilePageGenerator);
    }

    @Test
    void givenSeasonWithRegularPhase_whenGenerate_thenDelegatesToHelpersAndPhaseAwareApi() throws Exception {
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

        // generate() short-circuits seasons whose REGULAR-phase findByType is empty (productionSeasons
        // skip). Stub findByType so the fixture season survives into the per-season helper loop.
        when(seasonPhaseService.findByType(seasonId, org.ctc.domain.model.PhaseType.REGULAR))
                .thenReturn(java.util.Optional.of(regular));
        when(seasonRepository.findByActiveTrue()).thenReturn(java.util.Optional.empty());
        when(seasonRepository.findAll()).thenReturn(List.of(season));
        when(playoffRepository.findBySeasonId(seasonId)).thenReturn(java.util.Optional.empty());
        when(seasonDriverRepository.findBySeasonId(seasonId)).thenReturn(List.of());
        when(seasonTeamRepository.findBySeasonId(seasonId)).thenReturn(List.of());
        // SiteSlugger is a collaborator; default Mockito returns null for slugify(...), which would
        // NPE in path-resolve calls inside the orchestrator. Stub deterministic slug.
        when(siteSlugger.slugify(any(String.class))).thenReturn("slug");

        // siteProperties returns a fresh tempDir-style path so generate() doesn't fail on cleanOutputDirectory
        var tempDir = java.nio.file.Files.createTempDirectory("sitegen-it-").toString();
        when(siteProperties.getOutputDir()).thenReturn(tempDir);
        when(siteProperties.getLinks()).thenReturn(List.of());
        when(siteProperties.getYoutubeChannelUrl()).thenReturn("https://example.com");
        when(siteProperties.getYoutubeVideoId()).thenReturn("abcDEF12345");

        // standings/alltime calls used by overview + alltime paths still inside the orchestrator
        when(standingsService.calculateAlltimeStandings(anyList())).thenReturn(List.of());
        when(driverRankingService.calculateAlltimeRanking(anyList())).thenReturn(List.of());

        var sut = buildSut();

        // when — full generate() exercises every refactored call site at least once
        sut.generate();

        // then — Phase-62 Plan-0 contract: orchestrator delegates per-page work to the 5 helpers.
        verify(standingsPageGenerator, atLeastOnce()).generate(any(), any());
        verify(driverRankingPageGenerator, atLeastOnce()).generate(any(), any());
        verify(matchdaysPageGenerator, atLeastOnce()).generateIndex(any(), any());
        verify(matchdaysPageGenerator, atLeastOnce()).generateDetails(any(), any());
        verify(teamProfilePageGenerator, atLeastOnce()).generate(any(), any());
        verify(driverProfilePageGenerator, atLeastOnce()).generate(any(), any());

        // Caller-side contract preserved on the still-inline alltime/overview paths:
        // alltime aggregation uses calculateAlltimeStandings (NOT the legacy seasonId overload).
        verify(standingsService, atLeastOnce()).calculateAlltimeStandings(anyList());
        verify(driverRankingService, atLeastOnce()).calculateAlltimeRanking(anyList());
    }
}
