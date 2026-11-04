package org.ctc.sitegen;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwissStandingsPageGeneratorTest {
    @Mock TemplateWriter writer;
    @Mock SiteSlugger slugger;
    @Mock StandingsService standings;
    @Mock SeasonPhaseService phases;
    @Mock SeasonPhaseGroupRepository groups;
    @Mock PhaseTeamRepository roster;
    @InjectMocks StandingsPageGenerator generator;
    @TempDir Path output;

    @Test
    void givenSwissLeague_whenGeneratingStandings_thenKeepBuchholzOrderAndDisplay() throws Exception {
        // given
        var season = new Season("Test Swiss league", 2031, 1);
        season.setId(UUID.randomUUID());
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());
        phase.setFormat(SeasonFormat.SWISS);
        when(phases.findAllPhases(season.getId())).thenReturn(List.of(phase));
        when(slugger.slugify(season.getDisplayLabel())).thenReturn("test-swiss");
        var team = new Team("Test Swiss team", "TSW");
        team.setId(UUID.randomUUID());
        var expected = List.of(new StandingsService.TeamStanding(team));
        when(standings.calculateStandingsWithBuchholz(phase.getId(), null)).thenReturn(expected);
        var context = new GenerationContext(output, season, "", "", false, null,
                new SiteSlugs(Map.of(team.getId(), "test-swiss-team"), Map.of(), List.of()));
        // when
        generator.generate(context, new SiteGeneratorService.GenerationResult());
        // then
        var captured = ArgumentCaptor.forClass(Context.class);
        verify(writer, times(2)).write(eq("site/standings"), captured.capture(), any(), eq(output), eq(""), eq(""));
        assertThat(captured.getAllValues()).allSatisfy(page -> {
            assertThat(page.getVariable("showBuchholz")).isEqualTo(true);
            assertThat(page.getVariable("standings")).isSameAs(expected);
        });
        verify(standings, never()).calculateStandings(any(), any());
    }
}
