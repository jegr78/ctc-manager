package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.dto.RaceForm;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeasonWorkspaceServiceTest {
    @Mock SeasonRepository seasons;
    @Mock MatchdayRepository matchdays;
    @InjectMocks SeasonWorkspaceService service;
    Season season;

    @BeforeEach
    void setUp() {
        season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test workspace");
        season.setYear(2026);
        season.setNumber(1);
    }

    @Test
    void givenRaceAndDifferentFilter_whenBuild_thenResourceSeasonDeterminesNavigation() {
        // given
        var phase = new SeasonPhase();
        phase.setSeason(season);
        var matchday = new Matchday();
        matchday.setPhase(phase);
        var race = new Race();
        race.setMatchday(matchday);
        when(seasons.findAll()).thenReturn(List.of(season));

        // when
        var workspace = service.build(Map.of("activeRoute", "races", "race", race,
                "selectedSeasonId", UUID.randomUUID()));

        // then
        assertThat(workspace.selectedSeason().id()).isEqualTo(season.getId());
        assertThat(workspace.matchdaysUrl()).isEqualTo("/admin/matchdays?seasonId=" + season.getId());
        assertThat(workspace.standingsUrl()).isEqualTo("/admin/standings?seasonId=" + season.getId());
        assertThat(workspace.section()).isEqualTo("races");
    }

    @Test
    void givenUnfilteredRaces_whenBuild_thenAllSeasonsRemainAvailableWithoutDefaultSelection() {
        // given
        when(seasons.findAll()).thenReturn(List.of(season));

        // when
        var workspace = service.build(Map.of("activeRoute", "races"));

        // then
        assertThat(workspace.selectedSeason()).isNull();
        assertThat(workspace.seasons()).hasSize(1);
        assertThat(workspace.racesUrl()).isEqualTo("/admin/races");
    }

    @Test
    void givenRaceForm_whenBuild_thenSeasonIsResolvedFromItsMatchday() {
        // given
        var phase = new SeasonPhase();
        phase.setSeason(season);
        var matchday = new Matchday();
        matchday.setPhase(phase);
        var form = new RaceForm();
        form.setMatchdayId(UUID.randomUUID());
        when(matchdays.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(seasons.findAll()).thenReturn(List.of(season));

        // when
        var workspace = service.build(Map.of("activeRoute", "races", "raceForm", form));

        // then
        assertThat(workspace.selectedSeason().id()).isEqualTo(season.getId());
    }

    @Test
    void givenNumericSeasonNumbers_whenBuild_thenNewestSeasonIsListedFirst() {
        // given
        var newer = new Season();
        newer.setId(UUID.randomUUID());
        newer.setName("Test newest");
        newer.setYear(2026);
        newer.setNumber(10);
        season.setNumber(9);
        when(seasons.findAll()).thenReturn(List.of(season, newer));

        // when
        var workspace = service.build(Map.of("activeRoute", "seasons"));

        // then
        assertThat(workspace.seasons()).extracting(SeasonWorkspaceService.SeasonOption::id)
                .containsExactly(newer.getId(), season.getId());
    }

    @Test
    void givenUnknownSection_whenSwitch_thenRejectInsteadOfRedirecting() {
        // when / then
        assertThatThrownBy(() -> service.destination(season.getId(), "https://example.com"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void givenDeletedSeason_whenSwitch_thenReportMissingSeason() {
        // given
        when(seasons.findById(season.getId())).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.destination(season.getId(), "races"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void givenGlobalPage_whenBuild_thenNoSeasonWorkspaceIsAdded() {
        // when
        var workspace = service.build(Map.of("activeRoute", "teams"));

        // then
        assertThat(workspace).isNull();
    }
}
