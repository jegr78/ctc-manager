package de.ctc.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @Nested
    class GetEffectivePrimaryColorTest {

        @Test
        void shouldReturnOverrideWhenSet() {
            Team team = teamWithColors("#FF0000", null, null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setPrimaryColor("#0000FF");

            assertEquals("#0000FF", seasonTeam.getEffectivePrimaryColor());
        }

        @Test
        void shouldFallbackToTeamColorWhenNoOverride() {
            Team team = teamWithColors("#FF0000", null, null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            assertEquals("#FF0000", seasonTeam.getEffectivePrimaryColor());
        }
    }

    @Nested
    class GetEffectiveSecondaryColorTest {

        @Test
        void shouldReturnOverrideWhenSet() {
            Team team = teamWithColors(null, "#FFFFFF", null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setSecondaryColor("#000000");

            assertEquals("#000000", seasonTeam.getEffectiveSecondaryColor());
        }

        @Test
        void shouldFallbackToTeamColorWhenNoOverride() {
            Team team = teamWithColors(null, "#FFFFFF", null, null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            assertEquals("#FFFFFF", seasonTeam.getEffectiveSecondaryColor());
        }
    }

    @Nested
    class GetEffectiveAccentColorTest {

        @Test
        void shouldReturnOverrideWhenSet() {
            Team team = teamWithColors(null, null, "#123456", null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setAccentColor("#654321");

            assertEquals("#654321", seasonTeam.getEffectiveAccentColor());
        }

        @Test
        void shouldFallbackToTeamColorWhenNoOverride() {
            Team team = teamWithColors(null, null, "#123456", null);
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            assertEquals("#123456", seasonTeam.getEffectiveAccentColor());
        }
    }

    @Nested
    class GetEffectiveLogoUrlTest {

        @Test
        void shouldReturnOverrideWhenSet() {
            Team team = teamWithColors(null, null, null, "https://example.com/team-logo.png");
            SeasonTeam seasonTeam = new SeasonTeam(null, team);
            seasonTeam.setLogoUrl("https://example.com/season-logo.png");

            assertEquals("https://example.com/season-logo.png", seasonTeam.getEffectiveLogoUrl());
        }

        @Test
        void shouldFallbackToTeamLogoUrlWhenNoOverride() {
            Team team = teamWithColors(null, null, null, "https://example.com/team-logo.png");
            SeasonTeam seasonTeam = new SeasonTeam(null, team);

            assertEquals("https://example.com/team-logo.png", seasonTeam.getEffectiveLogoUrl());
        }
    }
}
