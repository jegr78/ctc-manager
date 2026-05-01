package org.ctc.domain.service;

import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.Team;

import java.util.UUID;

/**
 * Pure-Java entity builder utility for Phase 58 tests.
 *
 * All factory methods assign a random {@code id} so Mockito-mode tests work
 * without persistence. Repository IT-tests may override the id after calling
 * the factory, or simply use the assigned random id as a correlation key.
 *
 * Test data prefix convention: callers must use a {@code Phase58-Test-} prefix
 * on entity names to comply with CLAUDE.md "Isolate Test Data Completely".
 *
 * @see org.ctc.domain.repository.SeasonPhaseRepositoryIT
 * @see SeasonPhaseServiceTest
 */
// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1
public final class PhaseTestFixtures {

    private PhaseTestFixtures() {
        // utility class
    }

    /**
     * Creates a REGULAR phase with LEAGUE layout at sortIndex=0.
     * The returned entity has a random id assigned (Mockito tests require non-null ids).
     */
    public static SeasonPhase regularPhase(Season season, RaceScoring rs, MatchScoring ms) {
        // Phase 61 MIGR-06: format/legs/totalRounds live exclusively on the SeasonPhase.
        // Tests must explicitly set these via the returned phase if non-default values are required.
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());
        return phase;
    }

    /**
     * Creates a REGULAR phase with GROUPS layout and the given group names.
     * Each group gets a random id. Groups are added to the returned phase's
     * {@code groups} collection in the order provided.
     */
    public static SeasonPhase groupsRegularPhase(Season season, RaceScoring rs, MatchScoring ms,
                                                  String... groupNames) {
        var phase = regularPhase(season, rs, ms);
        phase.setLayout(PhaseLayout.GROUPS);
        int sortIdx = 0;
        for (String name : groupNames) {
            var group = new SeasonPhaseGroup(phase, name, sortIdx++);
            group.setId(UUID.randomUUID());
            phase.getGroups().add(group);
        }
        return phase;
    }

    /**
     * Creates a PLAYOFF phase with BRACKET layout at sortIndex=10.
     * Format is set to LEAGUE as a DB-default workaround (Phase 57 D-08).
     */
    public static SeasonPhase playoffPhase(Season season, String label, RaceScoring rs, MatchScoring ms) {
        var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10);
        phase.setId(UUID.randomUUID());
        phase.setLabel(label);
        phase.setFormat(SeasonFormat.LEAGUE); // DB-default workaround per Phase 57 D-08
        return phase;
    }

    /**
     * Creates a PhaseTeam associating the given team with the given phase (and optional group).
     * The returned entity has a random id.
     *
     * @param group may be {@code null} for LEAGUE-layout phases
     */
    public static PhaseTeam assignTeam(SeasonPhase phase, Team team, SeasonPhaseGroup group) {
        var pt = new PhaseTeam(phase, team);
        pt.setId(UUID.randomUUID());
        pt.setGroup(group);
        return pt;
    }

    /**
     * Phase 61 MIGR-06 helper: creates a Matchday bound to a synthetic REGULAR phase
     * for the given Season. Convenience for pure-Java unit tests that do not have a
     * persisted phase available.
     */
    public static Matchday matchdayInRegularPhase(Season season, String label, int sortIndex) {
        var phase = regularPhase(season, null, null);
        return new Matchday(phase, label, sortIndex);
    }

    /**
     * Phase 61 MIGR-06 helper: creates a Playoff bound to a synthetic PLAYOFF phase
     * for the given Season. Convenience for pure-Java unit tests.
     */
    public static Playoff playoffForSeason(Season season, String name) {
        var phase = playoffPhase(season, name, null, null);
        return new Playoff(phase, name);
    }
}
