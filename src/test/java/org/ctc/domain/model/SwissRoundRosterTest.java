package org.ctc.domain.model;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SwissRoundRosterTest {
    private static Team team(String name) { var team=new Team(name,name); team.setId(UUID.randomUUID()); return team; }
    @Test void givenOwnedPredecessorAndCurrentGroupPlace_whenResolvingRoster_thenSharePlaceButRetainActualHistoricalTeam() {
        var season=new Season("Test Swiss history"); season.setId(UUID.randomUUID());
        var phase=new SeasonPhase(season,PhaseType.REGULAR,PhaseLayout.GROUPS,0);
        var current=team("Test current"); var old=team("Test predecessor"); var otherGroup=team("Test other group");
        season.addTeam(current); season.addTeam(old); season.addTeam(otherGroup);
        season.findSeasonTeam(old).orElseThrow().setSuccessor(season.findSeasonTeam(current).orElseThrow());
        var roster=SwissRoundRoster.from(season,List.of(new PhaseTeam(phase,current)));
        assertThat(roster.places()).containsEntry(old.getId(),current.getId()).containsEntry(current.getId(),current.getId());
        assertThat(roster.places()).doesNotContainKey(otherGroup.getId());
        assertThat(roster.teams()).containsExactlyInAnyOrder(current,old);
    }
    @Test void givenForeignOrCyclicSuccession_whenResolvingRoster_thenNeverExtendPhaseMembershipThroughIt() {
        var season=new Season("Test Swiss history"); season.setId(UUID.randomUUID());
        var foreign=new Season("Test foreign"); foreign.setId(UUID.randomUUID());
        var phase=new SeasonPhase(season,PhaseType.REGULAR,PhaseLayout.LEAGUE,0);
        var current=team("Test current"); var old=team("Test predecessor"); var cyclic=team("Test cycle");
        season.addTeam(current); season.addTeam(old); season.addTeam(cyclic);
        var foreignRow=new SeasonTeam(foreign,current);
        season.findSeasonTeam(old).orElseThrow().setSuccessor(foreignRow);
        var cycle=season.findSeasonTeam(cyclic).orElseThrow(); cycle.setSuccessor(cycle);
        var roster=SwissRoundRoster.from(season,List.of(new PhaseTeam(phase,current)));
        assertThat(roster.places()).containsOnlyKeys(current.getId());
    }
}
