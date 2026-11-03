package org.ctc.domain.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.*;
import org.springframework.test.util.ReflectionTestUtils;

final class IncidentUnitFixture {
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    final Season season = new Season("Test Incident season");
    final Team home = new Team("Test Incident home", "TIH"), away = new Team("Test Incident away", "TIA");
    final SeasonPhase phase = new SeasonPhase();
    final Matchday day;
    final Match match;
    final Race race = new Race();
    final IncidentMatchContext context;
    final IncidentReportWindow window;

    IncidentUnitFixture() {
        season.setId(UUID.randomUUID()); home.setId(UUID.randomUUID()); away.setId(UUID.randomUUID());
        home.setDiscordRoleId("300000000000000003"); away.setDiscordRoleId("300000000000000004");
        phase.setId(UUID.randomUUID()); phase.setSeason(season);
        day = new Matchday(phase, "Test Incident round", 1); day.setId(UUID.randomUUID());
        match = new Match(day, home, away); match.setId(UUID.randomUUID()); match.setDiscordChannelId(CHANNEL);
        race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(match);
        race.setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); match.getRaces().add(race);
        context = id(new IncidentMatchContext(match, null, season, home, away, GUILD, CHANNEL, "test-manager", NOW));
        window = id(new IncidentReportWindow(context));
    }

    IncidentMatchRef reference() { return IncidentMatchRef.match(match.getId()); }
    IncidentMatchTarget target() { return new IncidentMatchTarget(reference(), match, null, season, home, away, List.copyOf(match.getRaces())); }
    IncidentParty party() { return new IncidentParty(IncidentPartyType.TEAM, home.getId(), home.getName(), home.getDiscordRoleId()); }
    void openWindow() {
        ReflectionTestUtils.setField(window, "state", IncidentWindowState.OPEN);
        ReflectionTestUtils.setField(window, "version", 1L);
        ReflectionTestUtils.setField(window, "deadline", NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(window, "openedAt", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(window, "openedBy", "test-manager");
    }
    static <T> T id(T entity) { ReflectionTestUtils.setField(entity, "id", UUID.randomUUID()); return entity; }
}
