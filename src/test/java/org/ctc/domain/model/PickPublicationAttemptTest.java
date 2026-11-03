package org.ctc.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.PhaseTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PickPublicationAttemptTest {
    static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    @Test
    void givenPreparedExactContent_whenConfirmed_thenCreateSnapshotWithoutChangingCapturedChoices() {
        // given
        var capture = capture("valid"); var attempt = new PickPublicationAttempt(capture);
        // when
        attempt.confirm("test-message", NOW.plusSeconds(1)); var snapshot = new PickPublicationSnapshot(attempt);
        // then
        assertThat(attempt.getState()).isEqualTo(PickPublicationState.SUCCEEDED); assertThat(attempt.getActiveRaceId()).isNull();
        assertThat(snapshot.getAttempt().getContent()).isEqualTo(capture.content());
        assertThat(snapshot.getConfirmedAt()).isEqualTo(NOW.plusSeconds(1)); assertThat(attempt.getContentHash()).isEqualTo(capture.content().fingerprint());
    }

    @Test
    void givenPreparedOrUncertainAttempt_whenCreatingSnapshotWithoutConfirmation_thenReject() {
        // given
        var attempt = new PickPublicationAttempt(capture("valid"));
        // when / then
        assertThatThrownBy(() -> new PickPublicationSnapshot(attempt)).isInstanceOf(BusinessRuleException.class);
        attempt.uncertain("TIMEOUT", "test-possible-remote-message", NOW.plusSeconds(1));
        assertThatThrownBy(() -> new PickPublicationSnapshot(attempt)).isInstanceOf(BusinessRuleException.class);
        assertThat(attempt.getActiveRaceId()).isEqualTo(attempt.getRace().getId());
    }

    @Test
    void givenUncertainDelivery_whenNormalConfirmationArrives_thenRequireExplicitReconciliation() {
        // given
        var attempt = new PickPublicationAttempt(capture("valid")); attempt.uncertain("LOST_RESPONSE", NOW.plusSeconds(1));
        // when / then
        assertThatThrownBy(() -> attempt.confirm("test-late-message", NOW.plusSeconds(2))).isInstanceOf(BusinessRuleException.class);
        assertThat(attempt.getState()).isEqualTo(PickPublicationState.UNCERTAIN);
        attempt.reconcile("test-verified-message", "test-reviewer", "Test verified exact remote output", NOW.plusSeconds(3));
        assertThat(attempt.getState()).isEqualTo(PickPublicationState.SUCCEEDED); assertThat(attempt.getReconciledBy()).isEqualTo("test-reviewer");
        assertThat(new PickPublicationSnapshot(attempt).getConfirmedAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void givenKnownFailureOrConfirmedOutput_whenLateOutcomeArrives_thenKeepTheTerminalOutcome() {
        // given
        var failed = new PickPublicationAttempt(capture("valid")); failed.fail("AUTH", NOW.plusSeconds(1));
        var confirmed = new PickPublicationAttempt(capture("valid")); confirmed.confirm("test-known-message", NOW.plusSeconds(1));
        // when / then
        assertThatThrownBy(() -> failed.confirm("test-late-message", NOW.plusSeconds(2))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> confirmed.uncertain("TIMEOUT", NOW.plusSeconds(2))).isInstanceOf(BusinessRuleException.class);
        assertThat(failed.getState()).isEqualTo(PickPublicationState.FAILED); assertThat(confirmed.getMessageId()).isEqualTo("test-known-message");
    }

    @Test
    void givenUncertainAttempt_whenAbandoning_thenRequireARecordedManagementReasonAndReleaseTheActiveClaim() {
        // given
        var attempt = new PickPublicationAttempt(capture("valid")); attempt.uncertain("TIMEOUT", NOW.plusSeconds(1));
        // when / then
        assertThatThrownBy(() -> attempt.abandon("test-reviewer", " ", NOW.plusSeconds(2))).isInstanceOf(BusinessRuleException.class);
        attempt.abandon("test-reviewer", "Test quarantine ambiguous message before a fresh post", NOW.plusSeconds(3));
        assertThat(attempt.getState()).isEqualTo(PickPublicationState.ABANDONED); assertThat(attempt.getActiveRaceId()).isNull();
        assertThatThrownBy(() -> new PickPublicationSnapshot(attempt)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "sourceVersion", "actor", "channel", "template", "manifest", "lobby"})
    void givenInvalidCapture_whenPreparing_thenRejectBeforeDelivery(String invalid) {
        // given / when / then
        assertThatThrownBy(() -> new PickPublicationAttempt(capture(invalid))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenOutputWithNoProvenFields_whenConfirmed_thenNeverAdvancePublicContent() {
        // given
        var c = capture("valid"); var empty = new PickPublicationAttempt(new PickPublicationAttempt.Capture(c.batchId(), c.race(), c.contentVersion(),
                c.sourceVersion(), c.origin(), c.output(), c.content(), Set.of(), c.actorId(), c.channelId(), c.webhookId(), c.targetMessageId(),
                c.templateHash(), c.requestManifest(), c.runtimeGeneration(), c.capturedAt()));
        empty.confirm("test-empty-message", NOW.plusSeconds(1));
        // when / then
        assertThatThrownBy(() -> new PickPublicationSnapshot(empty)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenEarlierOutcomeClock_whenConfirming_thenKeepThePreparedAttemptUnchanged() {
        // given
        var attempt = new PickPublicationAttempt(capture("valid"));
        // when / then
        assertThatThrownBy(() -> attempt.confirm("test-message", NOW.minusSeconds(1))).isInstanceOf(BusinessRuleException.class);
        assertThat(attempt.getState()).isEqualTo(PickPublicationState.PREPARED);
    }

    PickPublicationAttempt.Capture capture(String invalid) {
        var season = new Season("Test publication"); season.setId(UUID.randomUUID());
        var day = PhaseTestFixtures.matchdayInRegularPhase(season, "Test publication day", 0); day.setId(UUID.randomUUID());
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day);
        var home = new Team("Test publication home", "T-PHOME"); home.setId(UUID.randomUUID()); race.setHomeTeamOverride(home);
        var away = new Team("Test publication away", "T-PAWAY"); away.setId(UUID.randomUUID()); race.setAwayTeamOverride(away);
        var car = new Car("Test", "Test publication car"); car.setId(UUID.randomUUID()); race.setCar(car);
        var track = new Track("Test publication track"); track.setId(UUID.randomUUID()); race.setTrack(track);
        var settings = new RaceSettings(race); race.setSettings(settings);
        new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", "RS", "None").applyTo(settings);
        return new PickPublicationAttempt.Capture(UUID.randomUUID(), race, invalid.equals("version") ? 0 : 1,
                invalid.equals("sourceVersion") ? 0 : 2, PickPublicationOrigin.APPROVAL,
                invalid.equals("lobby") ? PickPublicationOutput.LOBBY_SETTINGS : PickPublicationOutput.SETTINGS,
                PickPublicationContent.from(race), PickDisclosureField.all(), invalid.equals("actor") ? " " : "test-manager",
                invalid.equals("channel") ? " " : "test-channel", "test-webhook", null,
                invalid.equals("template") ? "forged" : "0".repeat(64), invalid.equals("manifest") ? " " : "{}",
                "00000000-0000-0000-0000-000000000000:0", NOW);
    }
}
