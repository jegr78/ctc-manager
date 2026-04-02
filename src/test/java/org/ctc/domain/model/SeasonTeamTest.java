package org.ctc.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SeasonTeamTest {

    private Team teamWithColors(String primary, String secondary, String accent, String logoUrl) {
        Team team = new Team("Alpha Team", "ALF");
        team.setPrimaryColor(primary);
        team.setSecondaryColor(secondary);
        team.setAccentColor(accent);
        team.setLogoUrl(logoUrl);
        return team;
    }

    private Team subTeamOf(Team parent) {
        Team sub = new Team("Alpha Sub 1", "ALF 1", parent);
        return sub;
    }

    @Nested
    class GetEffectivePrimaryColorTest {

        @Test
        void givenSeasonOverride_whenGetEffectivePrimaryColor_thenReturnsOverride() {
            // given
            Team team = teamWithColors("#FF0000", null, null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setPrimaryColor("#0000FF");

            // when / then
            assertEquals("#0000FF", seasonTeam.getEffectivePrimaryColor());
        }

        @Test
        void givenNoOverride_whenGetEffectivePrimaryColor_thenFallsBackToTeamColor() {
            // given
            Team team = teamWithColors("#FF0000", null, null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            // when / then
            assertEquals("#FF0000", seasonTeam.getEffectivePrimaryColor());
        }

        @Test
        void givenSubTeamWithNoColor_whenGetEffectivePrimaryColor_thenReturnsNull() {
            // given
            Team parent = teamWithColors("#FF0000", null, null, null);
            Team sub = subTeamOf(parent);
            SeasonTeam seasonTeam = new SeasonTeam(null, sub);

            // Sub-team has no own color — propagation happens at save time, not at read time
            // when / then
            assertNull(seasonTeam.getEffectivePrimaryColor());
        }

        @Test
        void givenSubTeamWithPropagatedColor_whenGetEffectivePrimaryColor_thenReturnsSubTeamColor() {
            // given
            Team parent = teamWithColors("#FF0000", null, null, null);
            Team sub = subTeamOf(parent);
            sub.setPrimaryColor("#FF0000"); // Simulates propagation from parent
            SeasonTeam seasonTeam = new SeasonTeam(null, sub);

            // when / then
            assertEquals("#FF0000", seasonTeam.getEffectivePrimaryColor());
        }
    }

    @Nested
    class GetEffectiveSecondaryColorTest {

        @Test
        void givenSeasonOverride_whenGetEffectiveSecondaryColor_thenReturnsOverride() {
            // given
            Team team = teamWithColors(null, "#FFFFFF", null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setSecondaryColor("#000000");

            // when / then
            assertEquals("#000000", seasonTeam.getEffectiveSecondaryColor());
        }

        @Test
        void givenNoOverride_whenGetEffectiveSecondaryColor_thenFallsBackToTeamColor() {
            // given
            Team team = teamWithColors(null, "#FFFFFF", null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            // when / then
            assertEquals("#FFFFFF", seasonTeam.getEffectiveSecondaryColor());
        }
    }

    @Nested
    class GetEffectiveAccentColorTest {

        @Test
        void givenSeasonOverride_whenGetEffectiveAccentColor_thenReturnsOverride() {
            // given
            Team team = teamWithColors(null, null, "#123456", null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setAccentColor("#654321");

            // when / then
            assertEquals("#654321", seasonTeam.getEffectiveAccentColor());
        }

        @Test
        void givenNoOverride_whenGetEffectiveAccentColor_thenFallsBackToTeamColor() {
            // given
            Team team = teamWithColors(null, null, "#123456", null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            // when / then
            assertEquals("#123456", seasonTeam.getEffectiveAccentColor());
        }
    }

    @Nested
    class GetEffectiveLogoUrlTest {

        @Test
        void givenSeasonOverride_whenGetEffectiveLogoUrl_thenReturnsOverride() {
            // given
            Team team = teamWithColors(null, null, null, "https://example.com/team-logo.png");
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setLogoUrl("https://example.com/season-logo.png");

            // when / then
            assertEquals("https://example.com/season-logo.png", seasonTeam.getEffectiveLogoUrl());
        }

        @Test
        void givenNoOverride_whenGetEffectiveLogoUrl_thenFallsBackToTeamLogoUrl() {
            // given
            Team team = teamWithColors(null, null, null, "https://example.com/team-logo.png");
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            // when / then
            assertEquals("https://example.com/team-logo.png", seasonTeam.getEffectiveLogoUrl());
        }
    }

    @Nested
    class SuccessionTest {

        @Test
        void givenNoSuccessor_whenIsReplaced_thenReturnsFalse() {
            // given
            SeasonTeam seasonTeam = new SeasonTeam(null, new Team("Team A", "A"));

            // when / then
            assertFalse(seasonTeam.isReplaced());
        }

        @Test
        void givenSuccessor_whenIsReplaced_thenReturnsTrue() {
            // given
            SeasonTeam predecessor = new SeasonTeam(null, new Team("Team A", "A"));
            SeasonTeam successor = new SeasonTeam(null, new Team("Team B", "B"));
            predecessor.setSuccessor(successor);
            predecessor.setReplacedAt(LocalDate.of(2026, 3, 15));

            // when / then
            assertTrue(predecessor.isReplaced());
            assertEquals(LocalDate.of(2026, 3, 15), predecessor.getReplacedAt());
        }

        @Test
        void givenNoSuccessor_whenGetActiveSeasonTeam_thenReturnsSelf() {
            // given
            SeasonTeam seasonTeam = new SeasonTeam(null, new Team("Team A", "A"));

            // when / then
            assertSame(seasonTeam, seasonTeam.getActiveSeasonTeam());
        }

        @Test
        void givenDirectSuccessor_whenGetActiveSeasonTeam_thenReturnsSuccessor() {
            // given
            SeasonTeam predecessor = new SeasonTeam(null, new Team("Team A", "A"));
            SeasonTeam successor = new SeasonTeam(null, new Team("Team B", "B"));
            predecessor.setSuccessor(successor);

            // when / then
            assertSame(successor, predecessor.getActiveSeasonTeam());
        }

        @Test
        void givenSuccessionChain_whenGetActiveSeasonTeam_thenReturnsFinalSuccessor() {
            // given
            SeasonTeam teamA = new SeasonTeam(null, new Team("Team A", "A"));
            SeasonTeam teamB = new SeasonTeam(null, new Team("Team B", "B"));
            SeasonTeam teamC = new SeasonTeam(null, new Team("Team C", "C"));
            teamA.setSuccessor(teamB);
            teamB.setSuccessor(teamC);

            // when / then
            assertSame(teamC, teamA.getActiveSeasonTeam());
            assertSame(teamC, teamB.getActiveSeasonTeam());
        }
    }
}
