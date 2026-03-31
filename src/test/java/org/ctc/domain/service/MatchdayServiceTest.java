package org.ctc.domain.service;

import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchdayServiceTest {

    @Mock private MatchdayRepository matchdayRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private RaceLineupRepository raceLineupRepository;

    @InjectMocks
    private MatchdayService service;

    // --- getMatchdayList ---

    @Test
    void getMatchdayList_withSeasonId_returnsFilteredMatchdays() {
        var seasonId = UUID.randomUUID();
        var matchday = new Matchday();
        matchday.setLabel("MD1");
        var season = new Season();
        season.setId(seasonId);

        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId)).thenReturn(List.of(matchday));
        when(seasonRepository.findAll()).thenReturn(List.of(season));

        var result = service.getMatchdayList(seasonId);

        assertThat(result.matchdays()).containsExactly(matchday);
        assertThat(result.selectedSeasonId()).isEqualTo(seasonId);
        assertThat(result.seasons()).containsExactly(season);
    }

    @Test
    void getMatchdayList_withoutSeasonId_usesActiveSeason() {
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setActive(true);
        var matchday = new Matchday();

        when(seasonRepository.findByActiveTrue()).thenReturn(Optional.of(season));
        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(matchday));
        when(seasonRepository.findAll()).thenReturn(List.of(season));

        var result = service.getMatchdayList(null);

        assertThat(result.matchdays()).containsExactly(matchday);
        assertThat(result.selectedSeasonId()).isEqualTo(season.getId());
    }

    @Test
    void getMatchdayList_noActiveSeason_returnsAll() {
        var matchday = new Matchday();

        when(seasonRepository.findByActiveTrue()).thenReturn(Optional.empty());
        when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
        when(seasonRepository.findAll()).thenReturn(List.of());

        var result = service.getMatchdayList(null);

        assertThat(result.matchdays()).containsExactly(matchday);
        assertThat(result.selectedSeasonId()).isNull();
    }

    // --- saveMatchday ---

    @Test
    void saveMatchday_new_createsMatchday() {
        var seasonId = UUID.randomUUID();
        var season = new Season();
        season.setId(seasonId);

        when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));
        when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.saveMatchday("New MD", 1, seasonId, null);

        assertThat(result.getLabel()).isEqualTo("New MD");
        assertThat(result.getSortIndex()).isEqualTo(1);
        assertThat(result.getSeason()).isEqualTo(season);
        verify(matchdayRepository).save(any(Matchday.class));
    }

    @Test
    void saveMatchday_existing_updatesMatchday() {
        var seasonId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();
        var season = new Season();
        season.setId(seasonId);
        var existing = new Matchday();
        existing.setId(matchdayId);
        existing.setLabel("Old");

        when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));
        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(existing));
        when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.saveMatchday("Updated", 5, seasonId, matchdayId);

        assertThat(result.getLabel()).isEqualTo("Updated");
        assertThat(result.getSortIndex()).isEqualTo(5);
        assertThat(result.getSeason()).isEqualTo(season);
    }

    // --- deleteMatchday ---

    @Test
    void deleteMatchday_returnsSeasonId() {
        var seasonId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();
        var season = new Season();
        season.setId(seasonId);
        var matchday = new Matchday();
        matchday.setId(matchdayId);
        matchday.setSeason(season);

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));

        var result = service.deleteMatchday(matchdayId);

        assertThat(result).isEqualTo(seasonId);
        verify(matchdayRepository).delete(matchday);
    }

    // --- createInline ---

    @Test
    void createInline_calculatesNextSortIndex() {
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var existing = new Matchday(season, "MD1", 3);

        when(seasonRepository.findByName("Test Season")).thenReturn(Optional.of(season));
        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(existing));
        when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> {
            Matchday md = inv.getArgument(0);
            md.setId(UUID.randomUUID());
            return md;
        });

        var result = service.createInline("Test Season", "MD2");

        assertThat(result.label()).isEqualTo("MD2");
        assertThat(result.sortIndex()).isEqualTo(4);
    }

    @Test
    void createInline_rejectsDuplicateLabel() {
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var existing = new Matchday(season, "Existing", 1);

        when(seasonRepository.findByName("Test Season")).thenReturn(Optional.of(season));
        when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createInline("Test Season", "Existing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }
}
