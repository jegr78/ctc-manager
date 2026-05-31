package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchdayServiceTest {

    @Mock private MatchdayRepository matchdayRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private SeasonPhaseService seasonPhaseService;

    @InjectMocks
    private MatchdayService service;


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


    @Test
    void givenNewMatchday_whenSaveMatchday_thenCreatesMatchday() {
        // given
        var seasonId = UUID.randomUUID();
        var season = new Season();
        season.setId(seasonId);
        // saveMatchday delegates to seasonPhaseService.findRegularPhase to bind phase.
        var regular = PhaseTestFixtures.regularPhase(season, null, null);
        when(seasonPhaseService.findRegularPhase(seasonId)).thenReturn(regular);

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
        // saveMatchday delegates to seasonPhaseService.findRegularPhase to bind phase.
        var regular = PhaseTestFixtures.regularPhase(season, null, null);
        when(seasonPhaseService.findRegularPhase(seasonId)).thenReturn(regular);
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


    @Test
    void givenExistingMatchday_whenDeleteMatchday_thenReturnsSeasonId() {
        // given
        var seasonId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();
        var season = new Season();
        season.setId(seasonId);
        var matchday = new Matchday();
        matchday.setId(matchdayId);
        // matchday.getSeason() derives from phase; wire a phase so deleteMatchday can read seasonId.
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));

        // when
        var result = service.deleteMatchday(matchdayId);

        // then
        assertThat(result).isEqualTo(seasonId);
        verify(matchdayRepository).delete(matchday);
    }


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


    @Test
    void givenExistingMatchdays_whenCreateInline_thenCalculatesNextSortIndex() {
        // given
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var regular = PhaseTestFixtures.regularPhase(season, null, null);
        var existing = new Matchday(regular, "MD1", 3);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(regular);
        when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId())).thenReturn(List.of(existing));
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
    void givenMissingSeason_whenCreateInline_thenThrowsEntityNotFoundException() {
        // given
        var seasonId = UUID.randomUUID();
        when(seasonRepository.findById(seasonId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.createInline(seasonId, "Test"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Season");
    }

    @Test
    void givenDuplicateLabel_whenCreateInline_thenThrowsBusinessRuleException() {
        // given
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var regular = PhaseTestFixtures.regularPhase(season, null, null);
        var existing = new Matchday(regular, "Existing", 1);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(regular);
        when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId())).thenReturn(List.of(existing));

        // when / then
        assertThatThrownBy(() -> service.createInline(season.getId(), "Existing"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    /**
     * REGULAR matchday creation must not be poisoned by PLAYOFF
     * matchdays. The pre-fix bug used {@code findBySeasonIdOrderBySortIndexAsc}, which
     * returns matchdays from BOTH the REGULAR and PLAYOFF phases. Playoff matchdays carry
     * sortIndex >= 100 (see {@code PlayoffService.addRaceToMatchup}), so the next REGULAR
     * matchday inherited the playoff's high sortIndex (e.g., 101) instead of the expected
     * {@code lastRegularSortIndex + 1}.
     */
    @Test
    void givenSeasonWithPlayoffMatchdays_whenCreateInline_thenSortIndexScopedToRegularPhase() {
        // given — REGULAR phase has matchdays with sortIndex 1..3; a PLAYOFF phase exists with
        // matchdays at sortIndex 100+. The mock simulates the phase-scoped finder; the fix MUST
        // call findByPhaseIdOrderBySortIndexAsc(regular) and ignore the playoff matchdays.
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var regular = PhaseTestFixtures.regularPhase(season, null, null);
        var existingRegular = new Matchday(regular, "Round 3", 3);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(regular);
        when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId()))
                .thenReturn(List.of(existingRegular));
        when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> {
            Matchday md = inv.getArgument(0);
            md.setId(UUID.randomUUID());
            return md;
        });

        // when
        var result = service.createInline(season.getId(), "Round 4");

        // then — sortIndex must be 4 (lastRegular + 1), NOT 101 (max across phases + 1)
        assertThat(result.sortIndex()).isEqualTo(4);
    }

    /**
     * a duplicate-label check must be scoped to the REGULAR phase.
     * Pre-fix, label "Round 1" in REGULAR was rejected when "Round 1" already existed in
     * PLAYOFF — a cross-phase false positive.
     */
    @Test
    void givenPlayoffMatchdayWithSameLabel_whenCreateInlineForRegular_thenAllowsCreation() {
        // given — REGULAR phase is empty; PLAYOFF "Round 1" exists but is NOT in scope of
        // findByPhaseIdOrderBySortIndexAsc(regular). The fix must accept "Round 1".
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        var regular = PhaseTestFixtures.regularPhase(season, null, null);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(seasonPhaseService.findRegularPhase(season.getId())).thenReturn(regular);
        when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regular.getId())).thenReturn(List.of());
        when(matchdayRepository.save(any(Matchday.class))).thenAnswer(inv -> {
            Matchday md = inv.getArgument(0);
            md.setId(UUID.randomUUID());
            return md;
        });

        // when
        var result = service.createInline(season.getId(), "Round 1");

        // then — duplicate check must NOT cross phase boundaries
        assertThat(result.label()).isEqualTo("Round 1");
        assertThat(result.sortIndex()).isEqualTo(1);
    }


    @Nested
    class GetMatchdayDetailGraphicStatus {

        @Test
        void givenMatchdayWithNonByeMatchesAndSchedule_whenGetMatchdayDetail_thenHasMatchesTrueAndHasScheduleTrue() {
            // given
            var matchdayId = UUID.randomUUID();
            var matchday = new Matchday();
            matchday.setId(matchdayId);

            var homeTeam = new Team("Home", "HOM");
            homeTeam.setId(UUID.randomUUID());
            var awayTeam = new Team("Away", "AWY");
            awayTeam.setId(UUID.randomUUID());

            var match = new Match(matchday, homeTeam, awayTeam);
            match.setId(UUID.randomUUID());
            match.setBye(false);

            var race = new Race();
            race.setId(UUID.randomUUID());
            race.setMatchday(matchday);
            race.setMatch(match);
            race.setDateTime(LocalDateTime.now());
            match.getRaces().add(race);
            matchday.getMatches().add(match);

            when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
            when(raceLineupRepository.findByRaceMatchdayId(matchdayId)).thenReturn(List.of());

            // when
            var data = service.getMatchdayDetail(matchdayId);

            // then
            assertThat(data.hasMatches()).isTrue();
            assertThat(data.hasSchedule()).isTrue();
        }

        @Test
        void givenMatchdayWithAllByeMatches_whenGetMatchdayDetail_thenHasMatchesFalse() {
            // given
            var matchdayId = UUID.randomUUID();
            var matchday = new Matchday();
            matchday.setId(matchdayId);

            var homeTeam = new Team("Home", "HOM");
            homeTeam.setId(UUID.randomUUID());

            var match = new Match(matchday, homeTeam, null);
            match.setId(UUID.randomUUID());
            match.setBye(true);
            matchday.getMatches().add(match);

            when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
            when(raceLineupRepository.findByRaceMatchdayId(matchdayId)).thenReturn(List.of());

            // when
            var data = service.getMatchdayDetail(matchdayId);

            // then
            assertThat(data.hasMatches()).isFalse();
        }

        @Test
        void givenMatchdayWithPartialSchedule_whenGetMatchdayDetail_thenScheduleMissingCountCorrect() {
            // given
            var matchdayId = UUID.randomUUID();
            var matchday = new Matchday();
            matchday.setId(matchdayId);

            var homeTeam = new Team("Home", "HOM");
            homeTeam.setId(UUID.randomUUID());
            var awayTeam = new Team("Away", "AWY");
            awayTeam.setId(UUID.randomUUID());

            // Match 1: has schedule
            var match1 = new Match(matchday, homeTeam, awayTeam);
            match1.setId(UUID.randomUUID());
            match1.setBye(false);
            var race1 = new Race();
            race1.setId(UUID.randomUUID());
            race1.setMatchday(matchday);
            race1.setMatch(match1);
            race1.setDateTime(LocalDateTime.now());
            match1.getRaces().add(race1);

            // Match 2: no schedule
            var match2 = new Match(matchday, homeTeam, awayTeam);
            match2.setId(UUID.randomUUID());
            match2.setBye(false);
            var race2 = new Race();
            race2.setId(UUID.randomUUID());
            race2.setMatchday(matchday);
            race2.setMatch(match2);
            // no dateTime set
            match2.getRaces().add(race2);

            // Match 3: no schedule
            var match3 = new Match(matchday, homeTeam, awayTeam);
            match3.setId(UUID.randomUUID());
            match3.setBye(false);
            var race3 = new Race();
            race3.setId(UUID.randomUUID());
            race3.setMatchday(matchday);
            race3.setMatch(match3);
            // no dateTime set
            match3.getRaces().add(race3);

            matchday.getMatches().add(match1);
            matchday.getMatches().add(match2);
            matchday.getMatches().add(match3);

            when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
            when(raceLineupRepository.findByRaceMatchdayId(matchdayId)).thenReturn(List.of());

            // when
            var data = service.getMatchdayDetail(matchdayId);

            // then
            assertThat(data.scheduleMissingCount()).isEqualTo(2);
        }

        @Test
        void givenMatchdayWithResults_whenGetMatchdayDetail_thenHasResultsTrue() {
            // given
            var matchdayId = UUID.randomUUID();
            var matchday = new Matchday();
            matchday.setId(matchdayId);

            var homeTeam = new Team("Home", "HOM");
            homeTeam.setId(UUID.randomUUID());
            var awayTeam = new Team("Away", "AWY");
            awayTeam.setId(UUID.randomUUID());

            var match = new Match(matchday, homeTeam, awayTeam);
            match.setId(UUID.randomUUID());
            match.setBye(false);
            match.setHomeScore(3);
            match.setAwayScore(1);
            matchday.getMatches().add(match);

            when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
            when(raceLineupRepository.findByRaceMatchdayId(matchdayId)).thenReturn(List.of());

            // when
            var data = service.getMatchdayDetail(matchdayId);

            // then
            assertThat(data.hasResults()).isTrue();
        }
    }


    @Test
    void givenRegularAndPlayoffMatchdays_whenFindByPhaseId_thenSegmentedCorrectly() {
        // given
        var phaseId = UUID.randomUUID();
        var md1 = new Matchday();
        md1.setId(UUID.randomUUID());
        md1.setLabel("MD-1");
        var md2 = new Matchday();
        md2.setId(UUID.randomUUID());
        md2.setLabel("MD-2");
        when(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phaseId))
                .thenReturn(List.of(md1, md2));

        // when
        var result = service.findByPhaseId(phaseId);

        // then
        assertThat(result).containsExactly(md1, md2);
        verify(matchdayRepository).findByPhaseIdOrderBySortIndexAsc(phaseId);
    }

    @Test
    void givenPhaseAndGroupId_whenFindByPhaseIdAndGroupId_thenReturnsGroupMatchdaysOnly() {
        // given
        var phaseId = UUID.randomUUID();
        var groupId = UUID.randomUUID();
        var groupMd = new Matchday();
        groupMd.setId(UUID.randomUUID());
        groupMd.setLabel("Group A MD-1");
        when(matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId))
                .thenReturn(List.of(groupMd));

        // when
        var result = service.findByPhaseIdAndGroupId(phaseId, groupId);

        // then
        assertThat(result).containsExactly(groupMd);
        verify(matchdayRepository).findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId, groupId);
    }

}
