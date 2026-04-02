package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class MatchdayGeneratorServiceTest {

    @Autowired private MatchdayGeneratorService matchdayGeneratorService;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private MatchdayRepository matchdayRepository;
    @Autowired private RaceRepository raceRepository;
    @Autowired private RaceScoringRepository raceScoringRepository;
    @Autowired private MatchScoringRepository matchScoringRepository;

    private Season season;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 4);
        var raceScoring = raceScoringRepository.save(
                new RaceScoring("Gen RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("Gen MS " + suffix, 3, 1, 0));

        season = new Season("Generator Test " + suffix, 2026, 1);
        season.setFormat(SeasonFormat.LEAGUE);
        season.setRaceScoring(raceScoring);
        season.setMatchScoring(matchScoring);
        season = seasonRepository.save(season);
    }

    @Test
    void givenLeagueWith6Teams_whenGenerate_thenCreates5MatchdaysAllTeamsPlayOncePerRound() {
        // given
        addTeams(6);

        // when
        matchdayGeneratorService.generate(season.getId(), 5, false);

        // then
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
        assertThat(matchdays).hasSize(5);
        assertThat(matchdays.get(0).getLabel()).isEqualTo("MD 1");
        assertThat(matchdays.get(4).getLabel()).isEqualTo("MD 5");

        // Each matchday has 3 matches (6 teams / 2)
        for (var md : matchdays) {
            var matches = matchRepository.findByMatchdayId(md.getId());
            assertThat(matches).hasSize(3);
        }

        // Each team plays exactly once per matchday
        for (var md : matchdays) {
            var matches = matchRepository.findByMatchdayId(md.getId());
            var teamIds = new HashSet<UUID>();
            for (var match : matches) {
                assertThat(teamIds.add(match.getHomeTeam().getId()))
                        .as("Team %s appears twice in %s", match.getHomeTeam().getShortName(), md.getLabel())
                        .isTrue();
                assertThat(teamIds.add(match.getAwayTeam().getId()))
                        .as("Team %s appears twice in %s", match.getAwayTeam().getShortName(), md.getLabel())
                        .isTrue();
            }
        }
    }

    @Test
    void givenRoundRobinWith4Teams_whenGenerate3Rounds_thenEachPairPlaysOnce() {
        // given
        season.setFormat(SeasonFormat.ROUND_ROBIN);
        seasonRepository.save(season);
        var teams = addTeams(4);

        // when
        matchdayGeneratorService.generate(season.getId(), 3, false);

        // then
        var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
        assertThat(allMatches).hasSize(6); // C(4,2) = 6 pairings

        // Every pair plays exactly once
        var pairings = new HashSet<String>();
        for (var match : allMatches) {
            var pair = pairKey(match.getHomeTeam().getId(), match.getAwayTeam().getId());
            assertThat(pairings.add(pair)).as("Duplicate pairing found").isTrue();
        }
    }

    @Test
    void givenHomeAndAway_whenGenerate_thenDoubleMatchdaysReversedPairings() {
        // given
        addTeams(4);

        // when
        matchdayGeneratorService.generate(season.getId(), 3, true);

        // then
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
        assertThat(matchdays).hasSize(6); // 3 rounds * 2

        assertThat(matchdays.get(0).getLabel()).isEqualTo("MD 1");
        assertThat(matchdays.get(5).getLabel()).isEqualTo("MD 6");

        // Collect first-half and second-half pairings
        var firstHalf = new ArrayList<String>();
        var secondHalf = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            for (var match : matchRepository.findByMatchdayId(matchdays.get(i).getId())) {
                firstHalf.add(match.getHomeTeam().getId() + "→" + match.getAwayTeam().getId());
            }
        }
        for (int i = 3; i < 6; i++) {
            for (var match : matchRepository.findByMatchdayId(matchdays.get(i).getId())) {
                secondHalf.add(match.getHomeTeam().getId() + "→" + match.getAwayTeam().getId());
            }
        }

        // For each first-half match H→A, second-half should have A→H
        for (var pair : firstHalf) {
            var parts = pair.split("→");
            var reversed = parts[1] + "→" + parts[0];
            assertThat(secondHalf).contains(reversed);
        }
    }

    @Test
    void givenOddTeamCount_whenGenerate_thenByesCreated() {
        // given
        addTeams(5);

        // when
        matchdayGeneratorService.generate(season.getId(), 5, false);

        // then
        var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
        assertThat(matchdays).hasSize(5);

        // Each matchday has 3 matches: 2 regular + 1 bye
        for (var md : matchdays) {
            var matches = matchRepository.findByMatchdayId(md.getId());
            assertThat(matches).hasSize(3);

            long byeCount = matches.stream().filter(Match::isBye).count();
            assertThat(byeCount).isEqualTo(1);

            var byeMatch = matches.stream().filter(Match::isBye).findFirst().orElseThrow();
            assertThat(byeMatch.getHomeTeam()).isNotNull();
            assertThat(byeMatch.getAwayTeam()).isNull();
        }
    }

    @Test
    void givenExistingMatchdays_whenGenerate_thenThrowsException() {
        // given
        addTeams(4);
        matchdayGeneratorService.generate(season.getId(), 3, false);

        // when / then
        assertThatThrownBy(() -> matchdayGeneratorService.generate(season.getId(), 3, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenFewerThan2Teams_whenGenerate_thenThrowsException() {
        // given
        addTeams(1);

        // when / then
        assertThatThrownBy(() -> matchdayGeneratorService.generate(season.getId(), 1, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenSwissSeason_whenGenerate_thenThrowsException() {
        // given
        season.setFormat(SeasonFormat.SWISS);
        seasonRepository.save(season);
        addTeams(4);

        // when / then
        assertThatThrownBy(() -> matchdayGeneratorService.generate(season.getId(), 3, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenParentWithSubTeams_whenGenerate_thenOnlySubTeamsUsed() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 4);
        var parent = teamRepository.save(new Team("Parent " + suffix, "PAR" + suffix));
        var sub1 = teamRepository.save(new Team("Sub1 " + suffix, "SB1" + suffix, parent));
        var sub2 = teamRepository.save(new Team("Sub2 " + suffix, "SB2" + suffix, parent));
        var other = teamRepository.save(new Team("Other " + suffix, "OTH" + suffix));
        season.addTeam(parent);
        season.addTeam(sub1);
        season.addTeam(sub2);
        season.addTeam(other);
        seasonRepository.save(season);

        // when
        matchdayGeneratorService.generate(season.getId(), 2, false);

        // then — 3 eligible teams (sub1, sub2, other), parent excluded
        var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
        for (var match : allMatches) {
            assertThat(match.getHomeTeam().getId()).isNotEqualTo(parent.getId());
            if (match.getAwayTeam() != null) {
                assertThat(match.getAwayTeam().getId()).isNotEqualTo(parent.getId());
            }
        }
    }

    @Test
    void givenGenerate_thenHomeAwayDistributionBalanced() {
        // given
        addTeams(6);

        // when
        matchdayGeneratorService.generate(season.getId(), 5, false);

        // then — each team should have 2-3 home and 2-3 away games (balanced for 5 rounds)
        var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
        var homeCounts = new HashMap<UUID, Integer>();
        var awayCounts = new HashMap<UUID, Integer>();
        for (var match : allMatches) {
            if (match.isBye()) continue;
            homeCounts.merge(match.getHomeTeam().getId(), 1, Integer::sum);
            awayCounts.merge(match.getAwayTeam().getId(), 1, Integer::sum);
        }

        // With 6 teams and 5 rounds, each team plays 5 matches
        // Balanced means max difference of 1 between home and away
        for (var teamId : homeCounts.keySet()) {
            int home = homeCounts.getOrDefault(teamId, 0);
            int away = awayCounts.getOrDefault(teamId, 0);
            assertThat(Math.abs(home - away))
                    .as("Team %s has %d home and %d away — not balanced", teamId, home, away)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void givenGenerate_thenEachMatchHasOneRace() {
        // given
        addTeams(4);

        // when
        matchdayGeneratorService.generate(season.getId(), 3, false);

        // then
        var allRaces = raceRepository.findByMatchdaySeasonId(season.getId());
        var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
        assertThat(allRaces).hasSameSizeAs(allMatches);

        // Each race is linked to a match
        for (var race : allRaces) {
            assertThat(race.getMatch()).isNotNull();
        }
    }

    private List<Team> addTeams(int count) {
        var teams = new ArrayList<Team>();
        var suffix = UUID.randomUUID().toString().substring(0, 4);
        for (int i = 0; i < count; i++) {
            var team = teamRepository.save(new Team("GenT " + i + "_" + suffix,
                    "GT" + i + suffix));
            season.addTeam(team);
            teams.add(team);
        }
        seasonRepository.save(season);
        return teams;
    }

    private String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
