package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.stats.StatsChoice;
import org.ctc.domain.stats.StatsLookupException;
import org.ctc.domain.stats.StatsScope;
import org.junit.jupiter.api.Test;

class StatsSeasonServiceTest {
    final SeasonRepository repository = mock(SeasonRepository.class);
    final SeasonPhaseService phases = mock(SeasonPhaseService.class);
    final StatsSeasonService service = new StatsSeasonService(repository, phases);
    final Season older = season("CTC Spring", 2025, 2, true);
    final Season newer = season("CTC Autumn", 2026, 1, true);
    final Season test = season("Test Stats", 2027, 1, true);
    final Season withoutRegular = season("CTC Draft", 2027, 2, false);

    @Test
    void whenCountingSeasons_thenExcludeTestAndPhaselessSeasonsAndOrderByYearAndNumber() {
        // given
        when(repository.findAll()).thenReturn(List.of(withoutRegular, newer, test, older));
        // when / then
        assertThat(service.counted()).as("Test seasons and seasons without a regular phase are not public").containsExactly(older, newer);
    }

    @Test
    void givenNoActiveCountedSeason_whenAskingForTheCurrentSeason_thenFallBackToAlltime() {
        // given
        test.setActive(true);
        when(repository.findAll()).thenReturn(List.of(older, newer, test));
        // when
        var period = service.period(StatsScope.CURRENT, null);
        // then
        assertThat(period.alltime()).isTrue();
        assertThat(period.fallback()).as("the answer says that no season is active").isTrue();
        assertThat(period.seasons()).containsExactly(older, newer);
    }

    @Test
    void givenActiveSeason_whenAskingForTheCurrentSeason_thenUseIt() {
        // given
        newer.setActive(true);
        when(repository.findAll()).thenReturn(List.of(older, newer));
        // when
        var period = service.period(StatsScope.CURRENT, null);
        // then
        assertThat(period.season()).isEqualTo(newer);
        assertThat(period.fallback()).isFalse();
    }

    @Test
    void givenSeasonScope_whenChoosingByIdOrLabel_thenResolveOnlyCountedSeasons() {
        // given
        when(repository.findAll()).thenReturn(List.of(older, newer, test));
        // when / then
        assertThat(service.period(StatsScope.SEASON, newer.getId().toString()).season()).isEqualTo(newer);
        assertThat(service.period(StatsScope.SEASON, " 2025 | #2 | ctc spring ").season()).isEqualTo(older);
        assertThatThrownBy(() -> service.period(StatsScope.SEASON, test.getId().toString()))
                .isInstanceOf(StatsLookupException.class).hasMessage("No season found");
        assertThatThrownBy(() -> service.period(StatsScope.SEASON, null))
                .isInstanceOf(StatsLookupException.class).hasMessage("Choose a season for the Season scope");
    }

    @Test
    void whenListingSeasonChoices_thenOfferNewestFirstWithTheIdAsValue() {
        // given
        when(repository.findAll()).thenReturn(List.of(older, newer));
        // when / then
        assertThat(service.choices("ctc")).containsExactly(
                new StatsChoice("2026 | #1 | CTC Autumn", newer.getId().toString()),
                new StatsChoice("2025 | #2 | CTC Spring", older.getId().toString()));
        assertThat(service.choices("spring")).extracting(StatsChoice::value).containsExactly(older.getId().toString());
    }

    @Test
    void whenParsingScope_thenDefaultToCurrentAndRejectUnknownValues() {
        // when / then
        assertThat(StatsScope.parse(null)).isEqualTo(StatsScope.CURRENT);
        assertThat(StatsScope.parse("alltime")).isEqualTo(StatsScope.ALLTIME);
        assertThatThrownBy(() -> StatsScope.parse("forever")).isInstanceOf(StatsLookupException.class);
    }

    private Season season(String name, int year, int number, boolean regular) {
        var season = new Season(name, year, number);
        season.setId(UUID.randomUUID());
        when(phases.findByType(eq(season.getId()), eq(PhaseType.REGULAR)))
                .thenReturn(regular ? Optional.of(mock(SeasonPhase.class)) : Optional.empty());
        return season;
    }
}
