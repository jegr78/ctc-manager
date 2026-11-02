package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class TeamPickChannelBindingIT {
    @Autowired TestHelper helper;
    @Autowired JdbcTemplate jdbc;
    TestHelper.SeasonFixture fixture;

    @BeforeEach
    void setUp() {
        // given
        fixture = helper.createFullSeasonFixture("TGB" + UUID.randomUUID().toString().substring(0, 7));
    }

    @Test
    void givenExactScope_whenPersisting_thenAllowOneTeamPerSeasonChannelAndIndependentGuilds() {
        // when / then
        assertThatCode(() -> insert(fixture.season().getId(), fixture.homeTeam().getId(), "100", "200"))
                .doesNotThrowAnyException();
        assertThatCode(() -> insert(fixture.season().getId(), fixture.homeTeam().getId(), "101", "200"))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_pick_channel_bindings WHERE season_id=?", Long.class,
                fixture.season().getId())).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-team", "duplicate-channel", "foreign-season", "foreign-team", "invalid-guild", "invalid-channel"})
    void givenConflictingOrMalformedScope_whenPersisting_thenDatabaseRejectsIt(String invalid) {
        // given
        assertThatCode(() -> insert(fixture.season().getId(), fixture.homeTeam().getId(), "100", "200"))
                .doesNotThrowAnyException();
        var season = invalid.equals("foreign-season") ? UUID.randomUUID() : fixture.season().getId();
        var team = invalid.equals("foreign-team") ? UUID.randomUUID()
                : invalid.equals("duplicate-channel") ? fixture.awayTeam().getId() : fixture.homeTeam().getId();
        var guild = invalid.equals("invalid-guild") ? "malformed" : "100";
        var channel = invalid.equals("duplicate-team") ? "201" : invalid.equals("invalid-channel") ? "invalid" : "200";
        // when / then
        assertThatThrownBy(() -> insert(season, team, guild, channel)).isInstanceOf(DataIntegrityViolationException.class);
    }

    void insert(UUID season, UUID team, String guild, String channel) {
        jdbc.update("INSERT INTO team_pick_channel_bindings (id,season_id,team_id,guild_id,channel_id,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), season, team, guild, channel);
    }
}
