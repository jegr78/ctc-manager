package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.model.*;

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
     * <p>
     * scoring lives on the SeasonPhase. The {@code rs}/{@code ms} parameters
     * are now wired onto the phase so tests building phases via this helper see the scoring
     * propagated. Pass {@code null} when scoring is not relevant for the test.
     */
    public static SeasonPhase regularPhase(Season season, RaceScoring rs, MatchScoring ms) {
        // format/legs/totalRounds live exclusively on the SeasonPhase.
        // Tests must explicitly set these via the returned phase if non-default values are required.
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());
		if (rs != null) {
			phase.setRaceScoring(rs);
		}
		if (ms != null) {
			phase.setMatchScoring(ms);
		}
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
     * Format is set to LEAGUE as a DB-default workaround.
     */
    public static SeasonPhase playoffPhase(Season season, String label, RaceScoring rs, MatchScoring ms) {
        var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10);
        phase.setId(UUID.randomUUID());
        phase.setLabel(label);
        phase.setFormat(SeasonFormat.LEAGUE); // DB-default workaround per         // wire scoring onto the phase so callers can read it back.
		if (rs != null) {
			phase.setRaceScoring(rs);
		}
		if (ms != null) {
			phase.setMatchScoring(ms);
		}
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
     * helper: creates a Matchday bound to a REGULAR phase for the given Season.
     * <p>
     * If the {@code season} has an existing REGULAR phase in {@code season.getPhases()} (typical
     * for IT tests where {@code TestHelper.createSeason} bootstrapped one), reuses that persisted
     * phase so the resulting Matchday's FK to {@code season_phases.id} resolves correctly.
     * Otherwise falls back to a synthetic transient phase (pure-Java unit tests without a DB).
     */
    public static Matchday matchdayInRegularPhase(Season season, String label, int sortIndex) {
        SeasonPhase phase;
        try {
            phase = season.getPhases().stream()
                    .filter(p -> p.getPhaseType() == PhaseType.REGULAR)
                    .findFirst()
                    .orElseGet(() -> regularPhase(season, null, null));
        } catch (org.hibernate.LazyInitializationException e) {
            // when called outside an OSIV session (e.g. an IT test that
            // re-fetches a Season via repo without an open transaction), fall back to a
            // synthetic transient phase. Callers needing a persisted phase should pass it
            // explicitly via new Matchday(phase, ...).
            phase = regularPhase(season, null, null);
        }
        return new Matchday(phase, label, sortIndex);
    }

    /**
     * helper: creates a Playoff bound to a PLAYOFF phase for the given Season.
     * <p>
     * If the {@code season} has an existing PLAYOFF phase in {@code season.getPhases()},
     * reuses that persisted phase. Otherwise creates a synthetic transient PLAYOFF phase
     * (pure-Java unit tests without a DB).
     */
    public static Playoff playoffForSeason(Season season, String name) {
        SeasonPhase phase = season.getPhases().stream()
                .filter(p -> p.getPhaseType() == PhaseType.PLAYOFF)
                .findFirst()
                .orElseGet(() -> playoffPhase(season, name, null, null));
        return new Playoff(phase, name);
    }
}
