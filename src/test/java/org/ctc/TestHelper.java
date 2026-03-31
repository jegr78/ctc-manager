package org.ctc;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestHelper {

    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final SeasonRepository seasonRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final RaceRepository raceRepository;
    private final DriverRepository driverRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    public Season createSeason(String name) {
        var suffix = UUID.randomUUID().toString().substring(0, 4);
        var rs = raceScoringRepository.save(
                new RaceScoring("RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var ms = matchScoringRepository.save(
                new MatchScoring("MS " + suffix, 3, 1, 0));
        var season = new Season(name);
        season.setRaceScoring(rs);
        season.setMatchScoring(ms);
        return seasonRepository.save(season);
    }

    public Matchday createMatchday(Season season, String label, int sortIndex) {
        return matchdayRepository.save(new Matchday(season, label, sortIndex));
    }

    public Team createTeam(String name, String shortName) {
        return teamRepository.save(new Team(name, shortName));
    }

    public Match createMatch(Matchday matchday, Team homeTeam, Team awayTeam) {
        return matchRepository.save(new Match(matchday, homeTeam, awayTeam));
    }

    public Race createRace(Matchday matchday, Match match) {
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        return raceRepository.save(race);
    }

    public Driver createDriver(String psnId, String nickname) {
        return driverRepository.save(new Driver(psnId, nickname));
    }

    public SeasonDriver createSeasonDriver(Season season, Driver driver, Team team) {
        return seasonDriverRepository.save(new SeasonDriver(season, driver, team));
    }

    /**
     * Creates a full setup: Season with two teams added, a matchday, a match, and a race.
     * Returns the race for further use.
     */
    public SeasonFixture createFullSeasonFixture(String prefix) {
        var season = createSeason(prefix + "_Season");
        var homeTeam = createTeam(prefix + " Home", prefix + "_HOM");
        var awayTeam = createTeam(prefix + " Away", prefix + "_AWY");
        season.addTeam(homeTeam);
        season.addTeam(awayTeam);
        season = seasonRepository.save(season);
        var matchday = createMatchday(season, prefix + " MD1", 1);
        var match = createMatch(matchday, homeTeam, awayTeam);
        var race = createRace(matchday, match);
        return new SeasonFixture(season, matchday, match, race, homeTeam, awayTeam);
    }

    public record SeasonFixture(Season season, Matchday matchday, Match match, Race race,
                                Team homeTeam, Team awayTeam) {}
}
