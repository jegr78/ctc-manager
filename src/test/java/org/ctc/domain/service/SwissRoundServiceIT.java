package org.ctc.domain.service;

import java.util.*;
import org.ctc.admin.dto.SwissRoundForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SwissRoundFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext @Tag("integration")
class SwissRoundServiceIT extends SwissRoundFixture {
    @Autowired SwissRoundService rounds;
    @Autowired RaceService raceService;
    @Autowired RaceLineupRepository lineups;

    SwissRoundForm newRound() { return rounds.editor(phaseId,null,null,null).form(); }
    void pairing(SwissRoundForm form,int row,int home,int away) {
        var value=form.getRows().get(row); value.setHomeTeamId(teamIds.get(home)); value.setAwayTeamId(away<0?null:teamIds.get(away)); value.setBye(away<0);
    }
    UUID firstRound() { var form=newRound(); pairing(form,0,0,1); pairing(form,1,2,3); return rounds.save(form); }
    Set<String> pairs(UUID dayId) { return tx.execute(status -> matches.findByMatchdayId(dayId).stream()
            .map(m -> m.getHomeTeam().getId()+":"+(m.getAwayTeam()==null?"bye":m.getAwayTeam().getId())).collect(java.util.stream.Collectors.toSet())); }
    Set<UUID> raceIds(UUID dayId) { return tx.execute(status -> races.findByMatchdayId(dayId).stream().map(Race::getId).collect(java.util.stream.Collectors.toSet())); }
    Set<UUID> matchIds(UUID dayId) { return tx.execute(status -> matches.findByMatchdayId(dayId).stream().map(Match::getId).collect(java.util.stream.Collectors.toSet())); }

    @Autowired org.ctc.domain.repository.SeasonPhaseGroupRepository groups;
    @Test void givenGroupedSwissPhase_whenCreatingRound_thenUseOnlyConcreteGroupRosterAndRejectForeignGroupOrRound() {
        var groupIds=tx.execute(status -> {
            var phase=phases.findById(phaseId).orElseThrow(); phase.setLayout(PhaseLayout.GROUPS);
            var first=groups.save(new SeasonPhaseGroup(phase,"Test group A",0)); var second=groups.save(new SeasonPhaseGroup(phase,"Test group B",1));
            for(var row:roster.findByPhaseId(phaseId)) row.setGroup(teamIds.indexOf(row.getTeam().getId())<2?first:second);
            return List.of(first.getId(),second.getId());
        });
        assertThat(rounds.workspace(phaseId,null).chooseGroup()).isTrue();
        assertThatThrownBy(() -> rounds.editor(phaseId,null,null,null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("group");
        var form=rounds.editor(phaseId,groupIds.getFirst(),null,null).form();
        form.getRows().getFirst().setHomeTeamId(teamIds.get(0)); form.getRows().getFirst().setAwayTeamId(teamIds.get(2));
        assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(SwissRoundValidationException.class);
        form.getRows().getFirst().setAwayTeamId(teamIds.get(1)); UUID day=rounds.save(form);
        assertThat(rounds.workspace(phaseId,groupIds.getLast()).rounds()).isEmpty();
        assertThatThrownBy(() -> rounds.editor(phaseId,groupIds.getLast(),day,null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> rounds.editor(phaseId,UUID.randomUUID(),null,null)).isInstanceOf(org.ctc.domain.exception.EntityNotFoundException.class);
        assertThat(pairs(day)).containsExactly(teamIds.get(0)+":"+teamIds.get(1));
    }
    @Test void givenForeignPhaseGroupOrStandaloneRace_whenEditing_thenRequireCorrectOwnedRoundContext() {
        UUID foreignGroup=tx.execute(status -> {
            var other=new SeasonPhase(seasons.findById(seasonId).orElseThrow(),PhaseType.PLACEMENT,PhaseLayout.GROUPS,10);
            other.setFormat(SeasonFormat.SWISS); phases.save(other);
            return groups.saveAndFlush(new SeasonPhaseGroup(other,"Test foreign group",0)).getId();
        });
        tx.executeWithoutResult(status -> phases.findById(phaseId).orElseThrow().setLayout(PhaseLayout.GROUPS));
        assertThatThrownBy(() -> rounds.editor(phaseId,foreignGroup,null,null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("another phase");
        tx.executeWithoutResult(status -> phases.findById(phaseId).orElseThrow().setLayout(PhaseLayout.LEAGUE));
        UUID day=firstRound(); tx.executeWithoutResult(status -> {var race=new Race(); race.setMatchday(days.findById(day).orElseThrow()); races.save(race);});
        var view=rounds.editor(phaseId,null,day,null); assertThat(view.readOnlyReason()).contains("standalone");
        assertThatThrownBy(() -> rounds.save(view.form())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("standalone");
        assertThat(matches.findByMatchdayId(day)).hasSize(2); assertThat(races.findByMatchdayId(day)).hasSize(5);
    }
    @Test void givenConfiguredRoundLimitOrEmptyRoster_whenCreatingManually_thenRequireCapacityAndAssignedPlaces() {
        var form=newRound(); pairing(form,0,0,1);
        tx.executeWithoutResult(status -> phases.findById(phaseId).orElseThrow().setTotalRounds(0));
        form.setScope(rounds.workspace(phaseId,null).scope());
        assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("All configured rounds");
        tx.executeWithoutResult(status -> { phases.findById(phaseId).orElseThrow().setTotalRounds(8); roster.deleteAll(roster.findByPhaseId(phaseId)); });
        form.setScope(rounds.workspace(phaseId,null).scope());
        assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Assign teams");
        assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).isEmpty();
    }

    @Test void givenWrongFormatOrChangedScopeOrOrdinal_whenSaving_thenRejectBeforeRoundCreation() {
        var stale=newRound(); pairing(stale,0,0,1); stale.setScope("stale-context");
        assertThatThrownBy(() -> rounds.save(stale)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("context changed");
        var form=newRound(); pairing(form,0,0,1); form.setRoundNumber(99); var wrongNumber=form;
        assertThatThrownBy(() -> rounds.save(wrongNumber)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("round order");
        tx.executeWithoutResult(status -> phases.findById(phaseId).orElseThrow().setFormat(SeasonFormat.LEAGUE));
        assertThatThrownBy(() -> rounds.editor(phaseId,null,null,null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Swiss");
        assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).isEmpty();
    }

    @Test void givenUnprotectedPairingRemovedAndReplaced_whenSaving_thenKeepOtherIdsAndRemoveOnlyTheReplacedRaces() {
        UUID day=firstRound(); var form=rounds.editor(phaseId,null,day,null).form();
        var original=form.getRows().stream().filter(r -> r.getMatchId()!=null).toList();
        var removed=original.getFirst(); var kept=original.getLast(); UUID removedId=removed.getMatchId(), keptId=kept.getMatchId();
        UUID home=removed.getHomeTeamId(),away=removed.getAwayTeamId(); removed.setRemove(true);
        var added=form.getRows().stream().filter(r -> r.getMatchId()==null).findFirst().orElseThrow(); added.setHomeTeamId(away); added.setAwayTeamId(home);
        var keptRaces=tx.execute(status -> races.findByMatchId(keptId).stream().map(Race::getId).toList());
        rounds.save(form);
        assertThat(matchIds(day)).contains(keptId).doesNotContain(removedId).hasSize(2);
        assertThat(raceIds(day)).containsAll(keptRaces).hasSize(4);
    }
    @Test void givenOddRosterAndExplicitBye_whenSavingAndReopening_thenUseOneByeLegAndPrefillIt() {
        tx.executeWithoutResult(status -> {
            var team=helper.createTeam("Test Swiss fifth "+phaseId,"Test_SWE_"+phaseId.toString().substring(0,8)); teamIds.add(team.getId());
            var season=seasons.findById(seasonId).orElseThrow(); season.addTeam(team); roster.save(new PhaseTeam(phases.findById(phaseId).orElseThrow(),team));
        });
        var form=newRound(); pairing(form,0,0,1); pairing(form,1,2,3); pairing(form,2,4,-1); UUID day=rounds.save(form);
        tx.executeWithoutResult(status -> {
            var bye=matches.findByMatchdayId(day).stream().filter(Match::isBye).findFirst().orElseThrow();
            assertThat(bye.getAwayTeam()).isNull(); assertThat(races.findByMatchId(bye.getId())).hasSize(1);
            assertThat(races.findByMatchdayId(day)).hasSize(5);
        });
        assertThat(rounds.editor(phaseId,null,day,null).form().getRows()).anySatisfy(row -> { assertThat(row.isBye()).isTrue(); assertThat(row.getHomeTeamId()).isEqualTo(teamIds.get(4)); });
    }
    @Test void givenUnscoredLineup_whenChangingPairing_thenProtectActualDriverAttribution() {
        UUID day=firstRound();
        UUID locked=tx.execute(status -> {
            var match=matches.findByMatchdayId(day).getFirst(); var race=races.findByMatchId(match.getId()).getFirst();
            var driver=helper.createDriver("Test_SW_driver_"+phaseId,"Test Swiss driver"); driverIds.add(driver.getId());
            lineups.save(new RaceLineup(race,driver,match.getHomeTeam())); return match.getId();
        });
        var view=rounds.editor(phaseId,null,day,null); assertThat(view.lockedRows().get(locked)).contains("lineup");
        var row=view.form().getRows().stream().filter(r -> locked.equals(r.getMatchId())).findFirst().orElseThrow();
        UUID oldHome=row.getHomeTeamId(); row.setHomeTeamId(row.getAwayTeamId()); row.setAwayTeamId(oldHome);
        assertThatThrownBy(() -> rounds.save(view.form())).isInstanceOf(SwissRoundValidationException.class);
        tx.executeWithoutResult(status -> assertThat(lineups.findByRaceMatchdayId(day)).singleElement().satisfies(lineup -> assertThat(lineup.getDriver().getId()).isEqualTo(driverIds.getFirst())));
    }

    @Test void givenUnplayedRounds_whenEnteringHistoricalPairings_thenCreateOnlyRequestedRoundsAndConfiguredAlternatingLegs() {
        UUID first=firstRound(); var second=newRound(); pairing(second,0,0,2); pairing(second,1,1,3); UUID next=rounds.save(second);
        assertThat(pairs(next)).containsExactlyInAnyOrder(teamIds.get(0)+":"+teamIds.get(2),teamIds.get(1)+":"+teamIds.get(3));
        tx.executeWithoutResult(status -> {
            assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).hasSize(2);
            assertThat(phases.findById(phaseId).orElseThrow().getFormat()).isEqualTo(SeasonFormat.SWISS);
            assertThat(seasons.findById(seasonId).orElseThrow().isActive()).isFalse();
            for (UUID id:List.of(first,next)) {
                var day=days.findById(id).orElseThrow(); assertThat(day.isPicksOpen()).isFalse(); assertThat(day.getPickDeadline()).isNull();
                assertThat(matches.findByMatchdayId(id)).hasSize(2).allSatisfy(match -> {
                    var legs=races.findByMatchId(match.getId()); assertThat(legs).hasSize(2);
                    assertThat(legs.stream().filter(Race::hasTeamOverrides)).hasSize(1);
                    assertThat(legs).allSatisfy(race -> {
                        assertThat(race.getResults()).isEmpty(); assertThat(race.getPlayoffMatchup()).isNull();
                        assertThat(race.getHomeTeam().getId()).isEqualTo(race.hasTeamOverrides()?match.getAwayTeam().getId():match.getHomeTeam().getId());
                    });
                });
            }
        });
    }
    @Test void givenUnprotectedRound_whenSwappingTeamsAcrossPairings_thenSaveAtomicallyWithStableMatchAndRaceIdsAndRejectStaleEdit() {
        UUID day=firstRound(); var oldMatches=matchIds(day); var oldRaces=raceIds(day);
        var form=rounds.editor(phaseId,null,day,null).form();
        var active=form.getRows().stream().filter(r -> r.getMatchId()!=null).toList();
        active.get(0).setHomeTeamId(teamIds.get(0)); active.get(0).setAwayTeamId(teamIds.get(2));
        active.get(1).setHomeTeamId(teamIds.get(1)); active.get(1).setAwayTeamId(teamIds.get(3));
        rounds.save(form);
        assertThat(matchIds(day)).isEqualTo(oldMatches); assertThat(raceIds(day)).isEqualTo(oldRaces);
        assertThat(pairs(day)).containsExactlyInAnyOrder(teamIds.get(0)+":"+teamIds.get(2),teamIds.get(1)+":"+teamIds.get(3));
        assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Reload");
    }
    @Test void givenIdenticalPairingsReenteredWithoutOpaqueKeys_whenSaving_thenRetainTheExistingIds() {
        UUID day=firstRound(); var oldMatches=matchIds(day); var oldRaces=raceIds(day);
        var form=rounds.editor(phaseId,null,day,null).form(); form.getRows().forEach(r -> r.setMatchId(null));
        rounds.save(form); assertThat(matchIds(day)).isEqualTo(oldMatches); assertThat(raceIds(day)).isEqualTo(oldRaces);
    }
    @Test void givenDuplicateTeamOrForeignPairingKeyOrStaleGeneration_whenSaving_thenKeepEveryStoredPairingUnchanged() {
        UUID day=firstRound(); var original=pairs(day); var ids=raceIds(day);
        var duplicate=rounds.editor(phaseId,null,day,null).form(); var active=duplicate.getRows().stream().filter(r -> r.getMatchId()!=null).toList();
        active.get(1).setHomeTeamId(active.get(0).getHomeTeamId());
        assertThatThrownBy(() -> rounds.save(duplicate)).isInstanceOf(SwissRoundValidationException.class);
        var foreign=rounds.editor(phaseId,null,day,null).form(); foreign.getRows().getFirst().setMatchId(UUID.randomUUID());
        assertThatThrownBy(() -> rounds.save(foreign)).isInstanceOf(SwissRoundValidationException.class);
        var stale=rounds.editor(phaseId,null,day,null).form(); stale.setGeneration("old-instance:0");
        assertThatThrownBy(() -> rounds.save(stale)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
        assertThat(pairs(day)).isEqualTo(original); assertThat(raceIds(day)).isEqualTo(ids);
    }
    @Test void givenQuickScore_whenEditingOtherPairings_thenRetainProtectedRowsAndRejectTheirRemovalOrRewrite() {
        UUID day=firstRound();
        UUID scored=tx.execute(status -> matches.findByMatchdayId(day).getFirst().getId());
        UUID leg=tx.execute(status -> races.findByMatchId(scored).stream().filter(r -> !r.hasTeamOverrides()).findFirst().orElseThrow().getId());
        raceService.quickScore(leg,30,10);
        var protectedForm=rounds.editor(phaseId,null,day,null); assertThat(protectedForm.lockedRows()).containsKey(scored);
        var locked=protectedForm.form().getRows().stream().filter(r -> scored.equals(r.getMatchId())).findFirst().orElseThrow(); locked.setRemove(true);
        assertThatThrownBy(() -> rounds.save(protectedForm.form())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("locked pairing");
        var rewrite=rounds.editor(phaseId,null,day,null).form(); var row=rewrite.getRows().stream().filter(r -> scored.equals(r.getMatchId())).findFirst().orElseThrow();
        UUID previous=row.getHomeTeamId(); row.setHomeTeamId(row.getAwayTeamId()); row.setAwayTeamId(previous);
        assertThatThrownBy(() -> rounds.save(rewrite)).isInstanceOf(SwissRoundValidationException.class);
        var unchanged=rounds.editor(phaseId,null,day,null).form(); unchanged.setLabel("Test renamed historical round"); rounds.save(unchanged);
        tx.executeWithoutResult(status -> { var match=matches.findById(scored).orElseThrow(); assertThat(match.getHomeScore()).isEqualTo(30); assertThat(match.getAwayScore()).isEqualTo(10); });
    }
    @Test void givenHistoricalRematch_whenMaintainingRound_thenWarnButPreserveActualPairing() {
        firstRound(); var next=newRound(); pairing(next,0,0,1); pairing(next,1,2,3);
        var preview=rounds.editor(phaseId,null,null,next); assertThat(preview.warnings()).hasSize(2);
        UUID day=rounds.save(next); assertThat(pairs(day)).contains(teamIds.get(0)+":"+teamIds.get(1));
    }
    @Test void givenTwoManualRoundsWithOnlyLatestDecided_whenGeneratingNext_thenUseStoredOpponentsAndKeepNormalAdmission() {
        firstRound(); var second=newRound(); pairing(second,0,0,2); pairing(second,1,1,3); UUID day=rounds.save(second);
        var before=rounds.workspace(phaseId,null); assertThat(before.canGenerate()).isFalse();
        assertThatThrownBy(() -> rounds.generate(phaseId,null,before.generation(),before.scope())).isInstanceOf(IllegalStateException.class).hasMessageContaining("incomplete");
        for (var id:tx.execute(status -> matches.findByMatchdayId(day).stream().map(m -> races.findByMatchId(m.getId()).getFirst().getId()).toList())) raceService.quickScore(id,20,10);
        var current=rounds.workspace(phaseId,null); UUID third=rounds.generate(phaseId,null,current.generation(),current.scope());
        var actual=pairs(third); assertThat(actual).hasSize(2);
        assertThat(actual).allSatisfy(pair -> {
            var ids=pair.split(":"); var set=Set.of(UUID.fromString(ids[0]),UUID.fromString(ids[1]));
            assertThat(set).isIn(Set.of(teamIds.get(0),teamIds.get(3)),Set.of(teamIds.get(1),teamIds.get(2)));
        });
    }
}
