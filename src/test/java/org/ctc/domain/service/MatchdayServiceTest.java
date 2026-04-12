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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchdayServiceTest {

	@Mock
	private MatchdayRepository matchdayRepository;
	@Mock
	private SeasonRepository seasonRepository;
	@Mock
	private RaceLineupRepository raceLineupRepository;

	@InjectMocks
	private MatchdayService service;

	// --- getMatchdayList ---

	@Test
	void givenSeasonId_whenGetMatchdayList_thenReturnsFilteredMatchdays() {
		// given
		var seasonId = UUID.randomUUID();
		var matchday = new Matchday();
		matchday.setLabel("MD1");
		var season = new Season();
		season.setId(seasonId);

		when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId)).thenReturn(List.of(matchday));
		when(seasonRepository.findAll()).thenReturn(List.of(season));

		// when
		var result = service.getMatchdayList(seasonId);

		// then
		assertThat(result.matchdays()).containsExactly(matchday);
		assertThat(result.selectedSeasonId()).isEqualTo(seasonId);
		assertThat(result.seasons()).containsExactly(season);
	}

	@Test
	void givenNoSeasonIdAndActiveSeason_whenGetMatchdayList_thenUsesActiveSeason() {
		// given
		var season = new Season();
		season.setId(UUID.randomUUID());
		season.setActive(true);
		var matchday = new Matchday();

		when(seasonRepository.findByActiveTrue()).thenReturn(Optional.of(season));
		when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(matchday));
		when(seasonRepository.findAll()).thenReturn(List.of(season));

		// when
		var result = service.getMatchdayList(null);

		// then
		assertThat(result.matchdays()).containsExactly(matchday);
		assertThat(result.selectedSeasonId()).isEqualTo(season.getId());
	}

	@Test
	void givenNoSeasonIdAndNoActiveSeason_whenGetMatchdayList_thenReturnsAll() {
		// given
		var matchday = new Matchday();

		when(seasonRepository.findByActiveTrue()).thenReturn(Optional.empty());
		when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
		when(seasonRepository.findAll()).thenReturn(List.of());

		// when
		var result = service.getMatchdayList(null);

		// then
		assertThat(result.matchdays()).containsExactly(matchday);
		assertThat(result.selectedSeasonId()).isNull();
	}

	// --- saveMatchday ---

	@Test
	void givenNewMatchday_whenSaveMatchday_thenCreatesMatchday() {
		// given
		var seasonId = UUID.randomUUID();
		var season = new Season();
		season.setId(seasonId);

		when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));
		when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		var result = service.saveMatchday("New MD", 1, seasonId, null);

		// then
		assertThat(result.getLabel()).isEqualTo("New MD");
		assertThat(result.getSortIndex()).isEqualTo(1);
		assertThat(result.getSeason()).isEqualTo(season);
		verify(matchdayRepository).save(any(Matchday.class));
	}

	@Test
	void givenExistingMatchday_whenSaveMatchday_thenUpdatesMatchday() {
		// given
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

		// when
		var result = service.saveMatchday("Updated", 5, seasonId, matchdayId);

		// then
		assertThat(result.getLabel()).isEqualTo("Updated");
		assertThat(result.getSortIndex()).isEqualTo(5);
		assertThat(result.getSeason()).isEqualTo(season);
	}

	// --- deleteMatchday ---

	@Test
	void givenExistingMatchday_whenDeleteMatchday_thenReturnsSeasonId() {
		// given
		var seasonId = UUID.randomUUID();
		var matchdayId = UUID.randomUUID();
		var season = new Season();
		season.setId(seasonId);
		var matchday = new Matchday();
		matchday.setId(matchdayId);
		matchday.setSeason(season);

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));

		// when
		var result = service.deleteMatchday(matchdayId);

		// then
		assertThat(result).isEqualTo(seasonId);
		verify(matchdayRepository).delete(matchday);
	}

	// --- getMatchdaysBySeason ---

	@Test
	void givenSeasonWithMatchdays_whenGetMatchdaysBySeason_thenReturnsMatchdayDataList() {
		// given
		var seasonId = UUID.randomUUID();
		var md1 = new Matchday();
		md1.setId(UUID.randomUUID());
		md1.setLabel("Round 1");
		md1.setSortIndex(1);
		var md2 = new Matchday();
		md2.setId(UUID.randomUUID());
		md2.setLabel("Round 2");
		md2.setSortIndex(2);

		when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId)).thenReturn(List.of(md1, md2));

		// when
		var result = service.getMatchdaysBySeason(seasonId);

		// then
		assertThat(result).hasSize(2);
		assertThat(result.get(0)).isInstanceOf(MatchdayService.MatchdayData.class);
		assertThat(result.get(0).id()).isEqualTo(md1.getId());
		assertThat(result.get(0).label()).isEqualTo("Round 1");
		assertThat(result.get(0).sortIndex()).isEqualTo(1);
		assertThat(result.get(1).label()).isEqualTo("Round 2");
	}

	// --- createInline ---

	@Test
	void givenExistingMatchdays_whenCreateInline_thenCalculatesNextSortIndex() {
		// given
		var season = new Season();
		season.setId(UUID.randomUUID());
		season.setName("Test Season");
		var existing = new Matchday(season, "MD1", 3);

		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(existing));
		when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> {
			Matchday md = inv.getArgument(0);
			md.setId(UUID.randomUUID());
			return md;
		});

		// when
		var result = service.createInline(season.getId(), "MD2");

		// then
		assertThat(result.label()).isEqualTo("MD2");
		assertThat(result.sortIndex()).isEqualTo(4);
	}

	@Test
	void givenDuplicateLabel_whenCreateInline_thenThrowsException() {
		// given
		var season = new Season();
		season.setId(UUID.randomUUID());
		season.setName("Test Season");
		var existing = new Matchday(season, "Existing", 1);

		when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
		when(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).thenReturn(List.of(existing));

		// when / then
		assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("already exists");
	}
}
