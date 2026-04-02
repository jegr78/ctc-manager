package org.ctc.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonTest {

    @Test
    void givenSeasonWithoutDescription_whenGetDisplayLabel_thenFormatsCorrectly() {
        // given
        var season = new Season("CTC Season 4", 2026, 4);

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | CTC Season 4");
    }

    @Test
    void givenSeasonWithDescription_whenGetDisplayLabel_thenDescriptionNotIncluded() {
        // given
        var season = new Season("Season 3", 2025, 3);
        season.setDescription("Group A");

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2025 | #3 | Season 3");
    }

    @Test
    void givenSeasonWithDescription_whenGetDisplayLabel_thenReturnsNameNotDescription() {
        // given
        var season = new Season("Regular Season", 2026, 4);
        season.setDescription("Group A");

        // when / then
        assertThat(season.getDisplayLabel()).isEqualTo("2026 | #4 | Regular Season");
    }

    @Test
    void whenConstructed_thenAllFieldsAreSet() {
        // when
        var season = new Season("Test", 2025, 3);

        // then
        assertThat(season.getName()).isEqualTo("Test");
        assertThat(season.getYear()).isEqualTo(2025);
        assertThat(season.getNumber()).isEqualTo(3);
    }

    @Nested
    class EligibleTeamsTest {

        private Team teamWithId(String shortName) {
            var team = new Team("Team " + shortName, shortName);
            team.setId(UUID.randomUUID());
            return team;
        }

        private Team subTeamWithId(String shortName, Team parent) {
            var team = new Team("Team " + shortName, shortName, parent);
            team.setId(UUID.randomUUID());
            return team;
        }

        @Test
        void givenOnlyParentTeams_whenGetEligibleTeams_thenReturnsAll() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            season.addTeam(teamA);
            season.addTeam(teamB);

            // when
            var eligible = season.getEligibleTeams();

            // then
            assertThat(eligible).extracting(Team::getShortName).containsExactlyInAnyOrder("A", "B");
        }

        @Test
        void givenParentWithSubTeams_whenGetEligibleTeams_thenExcludesParent() {
            // given
            var season = new Season("S1", 2026, 1);
            var parent = teamWithId("P1R");
            var sub1 = subTeamWithId("P1R-1", parent);
            var sub2 = subTeamWithId("P1R-2", parent);
            var otherTeam = teamWithId("CLR");
            season.addTeam(parent);
            season.addTeam(sub1);
            season.addTeam(sub2);
            season.addTeam(otherTeam);

            // when
            var eligible = season.getEligibleTeams();

            // then
            assertThat(eligible).extracting(Team::getShortName)
                    .containsExactlyInAnyOrder("P1R-1", "P1R-2", "CLR");
            assertThat(eligible).noneMatch(t -> t.getShortName().equals("P1R"));
        }

        @Test
        void givenReplacedTeam_whenGetEligibleTeams_thenExcludesReplaced() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            var teamC = teamWithId("C");
            season.addTeam(teamA);
            season.addTeam(teamB);
            season.addTeam(teamC);

            var stA = season.findSeasonTeam(teamA).orElseThrow();
            var stC = season.findSeasonTeam(teamC).orElseThrow();
            stA.setSuccessor(stC);

            // when
            var eligible = season.getEligibleTeams();

            // then
            assertThat(eligible).extracting(Team::getShortName)
                    .containsExactlyInAnyOrder("B", "C");
        }
    }

    @Nested
    class SuccessionTest {

        private Team teamWithId(String shortName) {
            var team = new Team("Team " + shortName, shortName);
            team.setId(UUID.randomUUID());
            return team;
        }

        @Test
        void givenNoReplacedTeams_whenGetActiveTeams_thenReturnsAll() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            season.addTeam(teamA);
            season.addTeam(teamB);

            // when
            var activeTeams = season.getActiveTeams();

            // then
            assertThat(activeTeams).extracting(Team::getShortName).containsExactly("A", "B");
        }

        @Test
        void givenReplacedTeam_whenGetActiveTeams_thenExcludesPredecessor() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            var teamC = teamWithId("C");
            season.addTeam(teamA);
            season.addTeam(teamB);
            season.addTeam(teamC);

            // Team A replaced by Team C
            var stA = season.findSeasonTeam(teamA).orElseThrow();
            var stC = season.findSeasonTeam(teamC).orElseThrow();
            stA.setSuccessor(stC);

            // when
            var activeTeams = season.getActiveTeams();

            // then
            assertThat(activeTeams).extracting(Team::getShortName).containsExactly("B", "C");
        }

        @Test
        void givenNoReplacedTeams_whenBuildSuccessionMap_thenReturnsEmptyMap() {
            // given
            var season = new Season("S1", 2026, 1);
            season.addTeam(teamWithId("A"));

            // when
            var map = season.buildSuccessionMap();

            // then
            assertThat(map).isEmpty();
        }

        @Test
        void givenReplacedTeam_whenBuildSuccessionMap_thenMapsPredecessorToSuccessor() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            season.addTeam(teamA);
            season.addTeam(teamB);

            var stA = season.findSeasonTeam(teamA).orElseThrow();
            var stB = season.findSeasonTeam(teamB).orElseThrow();
            stA.setSuccessor(stB);

            // when
            var map = season.buildSuccessionMap();

            // then
            assertThat(map).hasSize(1);
            assertThat(map.get(teamA.getId())).isEqualTo(teamB.getId());
        }

        @Test
        void givenSuccessionChain_whenBuildSuccessionMap_thenAllMapToFinalSuccessor() {
            // given
            var season = new Season("S1", 2026, 1);
            var teamA = teamWithId("A");
            var teamB = teamWithId("B");
            var teamC = teamWithId("C");
            season.addTeam(teamA);
            season.addTeam(teamB);
            season.addTeam(teamC);

            var stA = season.findSeasonTeam(teamA).orElseThrow();
            var stB = season.findSeasonTeam(teamB).orElseThrow();
            var stC = season.findSeasonTeam(teamC).orElseThrow();
            stA.setSuccessor(stB);
            stB.setSuccessor(stC);

            // when
            var map = season.buildSuccessionMap();

            // then
            assertThat(map).hasSize(2);
            assertThat(map.get(teamA.getId())).isEqualTo(teamC.getId());
            assertThat(map.get(teamB.getId())).isEqualTo(teamC.getId());
        }
    }
}
