package org.ctc.domain.service;

import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private RaceRepository raceRepository;

    @InjectMocks
    private MatchService service;

    // --- createMatch ---

    @Test
    void createMatch_createsMatchAndFirstRace() {
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();

        var season = new Season("Test Season");
        var matchday = new Matchday(season, "MD1", 1);
        matchday.setId(matchdayId);

        var homeTeam = new Team();
        homeTeam.setId(homeTeamId);
        homeTeam.setShortName("HOM");

        var awayTeam = new Team();
        awayTeam.setId(awayTeamId);
        awayTeam.setShortName("AWY");

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchdayId, homeTeamId, awayTeamId))
                .thenReturn(false);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.createMatch(matchdayId, homeTeamId, awayTeamId, false);

        assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
        assertThat(result.getAwayTeam()).isEqualTo(awayTeam);
        assertThat(result.getMatchday()).isEqualTo(matchday);
        assertThat(result.isBye()).isFalse();
        verify(matchRepository).save(any(Match.class));
        verify(raceRepository).save(any(Race.class));
    }

    @Test
    void createMatch_byeMatch_setsAwayTeamNull() {
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();

        var season = new Season("Test Season");
        var matchday = new Matchday(season, "MD1", 1);
        matchday.setId(matchdayId);

        var homeTeam = new Team();
        homeTeam.setId(homeTeamId);
        homeTeam.setShortName("HOM");

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.createMatch(matchdayId, homeTeamId, null, true);

        assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
        assertThat(result.getAwayTeam()).isNull();
        assertThat(result.isBye()).isTrue();
        verify(matchRepository).save(any(Match.class));
        verify(raceRepository).save(any(Race.class));
    }

    @Test
    void createMatch_duplicateMatch_throwsException() {
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();

        var season = new Season("Test Season");
        var matchday = new Matchday(season, "MD1", 1);
        matchday.setId(matchdayId);

        var homeTeam = new Team();
        homeTeam.setId(homeTeamId);
        homeTeam.setShortName("HOM");

        var awayTeam = new Team();
        awayTeam.setId(awayTeamId);
        awayTeam.setShortName("AWY");

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchdayId, homeTeamId, awayTeamId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createMatch(matchdayId, homeTeamId, awayTeamId, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Match already exists");
    }

    // --- addLeg ---

    @Test
    void addLeg_createsAdditionalRace() {
        var matchId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();

        var season = new Season("Test Season");
        season.setLegs(3);
        var matchday = new Matchday(season, "MD1", 1);
        matchday.setId(matchdayId);

        var homeTeam = new Team();
        homeTeam.setShortName("HOM");
        var awayTeam = new Team();
        awayTeam.setShortName("AWY");

        var match = new Match(matchday, homeTeam, awayTeam);
        match.setId(matchId);
        // One existing race (first leg)
        var existingRace = new Race();
        existingRace.setMatchday(matchday);
        existingRace.setMatch(match);
        match.setRaces(new ArrayList<>());
        match.getRaces().add(existingRace);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.addLeg(matchId);

        assertThat(result.getRaces()).hasSize(2);
        verify(raceRepository).save(any(Race.class));
    }

    // --- deleteMatch ---

    @Test
    void deleteMatch_deletesAndReturnsMatchdayId() {
        var matchId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();

        var season = new Season("Test Season");
        var matchday = new Matchday(season, "MD1", 1);
        matchday.setId(matchdayId);

        var homeTeam = new Team();
        homeTeam.setShortName("HOM");

        var match = new Match(matchday, homeTeam, null);
        match.setId(matchId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        var result = service.deleteMatch(matchId);

        assertThat(result).isEqualTo(matchdayId);
        verify(matchRepository).delete(match);
    }
}
