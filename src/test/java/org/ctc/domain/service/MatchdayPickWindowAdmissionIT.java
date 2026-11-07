package org.ctc.domain.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class MatchdayPickWindowAdmissionIT extends PickPersistenceIT {
    @Test
    void givenPreparedClosedRoundWithFutureCutoff_whenConfirming_thenStoreNothing() {
        tx.executeWithoutResult(status -> entityManager.find(Matchday.class, fixture.matchday().getId()).setPicksOpen(false));
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Matchday").hasMessageContaining("closed");
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId())).isEmpty();
    }

    @Test
    void givenOpenedRound_whenConfirming_thenUseTheGlobalFlagWithoutAnIndividualOpening() {
        var receipt = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload));
        assertThat(receipt.revisionNumber()).isEqualTo(1);
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId()).orElseThrow().isManualOpen()).isFalse();
    }

    @Test
    void givenOnePairingDeadlineException_whenConfirming_thenKeepAnotherPairingExpired() {
        UUID otherRace = tx.execute(status -> {
            var day = entityManager.find(Matchday.class, fixture.matchday().getId()); day.setPickDeadline(LocalDateTime.of(2020, 1, 1, 20, 0));
            var first = entityManager.find(Race.class, fixture.race().getId());
            first.getMatch().setPickDeadlineOverride(LocalDateTime.of(2035, 1, 1, 12, 0));
            var other = new Match(day, first.getAwayTeam(), first.getHomeTeam()); entityManager.persist(other);
            var race = new Race(); race.setMatchday(day); race.setMatch(other); race.setPicksManaged(true);
            race.setHomeTeamOverride(first.getHomeTeam()); race.setAwayTeamOverride(first.getAwayTeam()); race.setDateTime(first.getDateTime());
            entityManager.persist(race); entityManager.flush(); return race.getId();
        });
        var otherActor = new AuthorizedPickActor(otherRace, actor.seasonId(), actor.teamId(), actor.userId());
        assertThatThrownBy(() -> submissions.submit(otherRace, 0, UUID.randomUUID(), otherActor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("deadline");
        assertThat(picks.findSnapshotByRaceId(otherRace)).isEmpty();
        assertThatCode(() -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload)).doesNotThrowAnyException();
    }

    @Test
    void givenConfirmedPickThenCentralClosure_whenReplacing_thenRetainTheConfirmedHistoryAndRecoverItsReceipt() {
        UUID request = UUID.randomUUID(); var original = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        tx.executeWithoutResult(status -> entityManager.find(Matchday.class, fixture.matchday().getId()).setPicksOpen(false));
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
        assertThat(submissions.submit(fixture.race().getId(), 0, request, actor, payload)).isEqualTo(original);
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId()).orElseThrow().getLatestRevisionNumber()).isEqualTo(1);
    }

    @Test
    void givenLegacyIndividualOpening_whenTheMatchdayIsClosed_thenDoNotRestoreItsOldBypass() {
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId()); race.getMatchday().setPicksOpen(false);
            var pick = new MatchdayPick(race, race.getMatchday().getSeason(), race.getHomeTeam()); pick.openMissing(); picks.save(pick);
            decisions.save(new MatchdayPickDecision(pick, PickDecisionKind.OPEN, pick.getVersion(), "test-legacy-manager", "Test legacy opening", Instant.now()));
        });
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Matchday").hasMessageContaining("closed");
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId()).orElseThrow().isManualOpen()).isTrue();
    }
}
