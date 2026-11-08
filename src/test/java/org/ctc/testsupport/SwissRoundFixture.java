package org.ctc.testsupport;

import java.util.*;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Every committed integration fixture owns its season and all four synthetic teams. */
public abstract class SwissRoundFixture {
    @Autowired protected TestHelper helper;
    @Autowired protected TransactionTemplate tx;
    @Autowired protected SeasonRepository seasons;
    @Autowired protected SeasonPhaseRepository phases;
    @Autowired protected PhaseTeamRepository roster;
    @Autowired protected TeamRepository teams;
    @Autowired protected MatchdayRepository days;
    @Autowired protected MatchRepository matches;
    @Autowired protected RaceRepository races;
    @Autowired protected DriverRepository drivers;
    protected final List<UUID> driverIds = new ArrayList<>();
    protected UUID seasonId, phaseId;
    protected final List<UUID> teamIds = new ArrayList<>();

    @BeforeEach protected void createOwnedSwissFixture() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0,8);
            var season = helper.createSeason("Test Swiss " + suffix); seasonId = season.getId();
            var phase = phases.findBySeasonIdAndPhaseType(seasonId,PhaseType.REGULAR).orElseThrow();
            phaseId = phase.getId(); phase.setFormat(SeasonFormat.SWISS); phase.setTotalRounds(8); phase.setLegs(2);
            for (int i=0; i<4; i++) {
                var team = helper.createTeam("Test Swiss " + (char)('A'+i) + " " + suffix, "Test_SW_"+suffix+"_"+i);
                teamIds.add(team.getId()); season.addTeam(team); roster.save(new PhaseTeam(phase,team));
            }
            seasons.saveAndFlush(season);
        });
    }
    @AfterEach protected void removeOwnedSwissFixture() {
        tx.executeWithoutResult(status -> {
            if (seasonId != null) {
                for (var phase : phases.findBySeasonIdOrderBySortIndex(seasonId)) roster.deleteAll(roster.findByPhaseId(phase.getId()));
                roster.flush(); seasons.findById(seasonId).ifPresent(helper::deleteSeasonCascade);
            }
            seasons.flush(); drivers.deleteAllById(driverIds); drivers.flush(); teams.deleteAllById(teamIds); teams.flush();
        });
    }
}
