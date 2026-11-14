package org.ctc.domain.service;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.PickPublicationSnapshotRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class PickDisclosureServiceTest {
    final RaceRepository races = mock(RaceRepository.class);
    final PickPublicationSnapshotRepository snapshots = mock(PickPublicationSnapshotRepository.class);
    final PickPublicationService publications = new PickPublicationService(mock(org.ctc.backup.lock.ImportLockService.class),
            mock(org.springframework.transaction.PlatformTransactionManager.class), mock(org.ctc.domain.repository.SeasonRepository.class), races,
            mock(org.ctc.domain.repository.PickPublicationAttemptRepository.class), snapshots, new PickSessionEpoch(),
            mock(jakarta.persistence.EntityManager.class), java.time.Clock.systemUTC());
    final PickDisclosureService service = new PickDisclosureService(races, publications);

    @Test
    void givenFinishedRace_whenProjectingPublicly_thenShowItsStoredSetup() {
        // given
        var race = race(true); when(races.findById(race.getId())).thenReturn(Optional.of(race));
        // when
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        // then
        assertThat(projection.carName()).contains("Test public legacy");
        assertThat(projection.trackName()).isEqualTo("Test private track");
        assertThat(projection.settings().weather()).isEqualTo("Test private weather");
    }

    @Test
    void givenUnfinishedUnpublishedRace_whenProjectingPublicly_thenWithholdEveryChoice() {
        // given
        var race = race(false);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        // when
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        // then
        assertThat(projection.carName()).isNull(); assertThat(projection.poolEntryLabel()).isNull();
        assertThat(projection.trackName()).isNull(); assertThat(projection.settings()).isNull();
    }

    @Test
    void givenPlayoffLegWithoutKnownOpponent_whenProjectingPublicly_thenWithholdItsStoredSetup() {
        // given
        var race = race(false); race.setAwayTeamOverride(null); race.getMatch().setAwayTeam(null);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        // when
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        // then
        assertThat(projection.trackName()).as("an undecided pairing is not finished").isNull();
        assertThat(projection.carName()).isNull();
    }

    @Test
    void givenRaceWhosePairingHasAScore_whenProjectingPublicly_thenShowItsStoredSetup() {
        // given
        var race = race(false); race.getMatch().setHomeScore(10); race.getMatch().setAwayScore(8);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        // when
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        // then
        assertThat(projection.trackName()).isEqualTo("Test private track");
    }

    @Test
    void givenMissingRace_whenProjectingPublicly_thenRejectWithoutAnInventedFallback() {
        // given / when / then
        assertThatThrownBy(() -> service.forPublic(UUID.randomUUID())).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void givenConfirmedContentAndLaterPrivateCorrection_whenReadingPublicly_thenKeepOnlyTheOriginalFrozenVersion() {
        // given
        var race = race(false); var original = confirmed(race, 1, PickDisclosureField.all(), 1, 0);
        race.getCar().setName("Test later private car"); race.getTrack().setName("Test later private track");
        race.getSettings().setWeather("Test later private weather");
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(original));
        // when / then
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        assertThat(projection.trackName()).isEqualTo("Test private track");
        assertThat(projection.carName()).contains("Test private car").doesNotContain("later");
        assertThat(projection.settings().weather()).isEqualTo("Test private weather");
    }

    @Test
    void givenOlderFullAndNewerLobbyPublication_whenReading_thenNeverMixContentVersionsEvenIfOlderConfirmationIsLater() {
        // given
        var race = race(false); var old = confirmed(race, 1, PickDisclosureField.all(), 4, 0);
        race.getTrack().setName("Test newer public track"); race.getSettings().setWeather("Test newer public weather");
        var latest = confirmed(race, 2, PickDisclosureField.lobby(), 2, 0);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(old, latest));
        // when / then
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        assertThat(projection.trackName()).isEqualTo("Test newer public track");
        assertThat(projection.settings().weather()).isEqualTo("Test newer public weather");
        assertThat(projection.carName()).isNull(); assertThat(projection.poolEntryLabel()).isNull();
    }

    @Test
    void givenTwoDisclosuresOfOneExactVersion_whenReading_thenUnionOnlyTheirConfirmedFields() {
        // given
        var race = race(false); var lobby = confirmed(race, 1, PickDisclosureField.lobby(), 1, 0);
        var car = confirmed(race, 1, Set.of(PickDisclosureField.CAR), 2, 0);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(car, lobby));
        // when / then
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        assertThat(projection.trackName()).isEqualTo("Test private track");
        assertThat(projection.carName()).contains("Test private car"); assertThat(projection.poolEntryLabel()).isEqualTo("Test private entry");
        assertThat(projection.settings().weather()).isEqualTo("Test private weather");
    }

    @Test
    void givenConflictingHashesWithinOneVersion_whenReading_thenRejectInsteadOfUnioningDifferentChoices() {
        // given
        var race = race(false); var first = confirmed(race, 1, Set.of(PickDisclosureField.TRACK), 1, 0);
        race.getSettings().setWeather("Test conflicting private weather");
        var conflicting = confirmed(race, 1, Set.of(PickDisclosureField.CAR), 2, 0);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(first, conflicting));
        // when / then
        assertThatThrownBy(() -> service.forPublic(race.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenConflictingProvenanceVersionsWithinOneContentVersion_whenReading_thenRejectInsteadOfMixingRevisions() {
        // given
        var race = race(false); var first = confirmed(race, 1, Set.of(PickDisclosureField.TRACK), 1, 2);
        var conflicting = confirmed(race, 1, Set.of(PickDisclosureField.CAR), 2, 3);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(first, conflicting));
        // when / then
        assertThatThrownBy(() -> service.forPublic(race.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenAReassignedTargetWithOldLegacyEvidence_whenReading_thenKeepItsCurrentChoicesWithheld() {
        // given
        var race = race(false); var previous = confirmed(race, 1, PickDisclosureField.all(), 1, 0);
        var away = new Team("Test new opponent", "T-NEW"); away.setId(UUID.randomUUID()); race.setAwayTeamOverride(away);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(previous));
        // when / then
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        assertThat(projection.carName()).isNull(); assertThat(projection.trackName()).isNull(); assertThat(projection.settings()).isNull();
    }

    @Test
    void givenUnconfirmedStoredAttempt_whenReading_thenKeepItWhollyWithheld() {
        // given
        var race = race(false); var attempt = mock(PickPublicationAttempt.class); var row = mock(PickPublicationSnapshot.class);
        when(row.getAttempt()).thenReturn(attempt); when(attempt.getState()).thenReturn(PickPublicationState.UNCERTAIN);
        when(attempt.matchesTarget(race)).thenReturn(true); when(attempt.getContentVersion()).thenReturn(1L);
        when(attempt.getContent()).thenReturn(PickPublicationContent.from(race));
        when(attempt.getContentHash()).thenReturn(PickPublicationContent.from(race).fingerprint());
        when(attempt.getOrigin()).thenReturn(PickPublicationOrigin.LEGACY); when(attempt.fields()).thenReturn(PickDisclosureField.all());
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(row));
        // when / then
        var projection = assertDoesNotThrow(() -> service.forPublic(race.getId()));
        assertThat(projection.carName()).isNull(); assertThat(projection.trackName()).isNull(); assertThat(projection.settings()).isNull();
    }

    @Test
    void givenStoredHashThatDoesNotMatchCapturedContent_whenReading_thenRejectUnverifiedChoices() {
        // given
        var race = race(false); var attempt = mock(PickPublicationAttempt.class); var row = mock(PickPublicationSnapshot.class);
        when(row.getAttempt()).thenReturn(attempt); when(attempt.getState()).thenReturn(PickPublicationState.SUCCEEDED);
        when(attempt.matchesTarget(race)).thenReturn(true); when(attempt.getContentVersion()).thenReturn(1L);
        when(attempt.getContent()).thenReturn(PickPublicationContent.from(race)); when(attempt.getContentHash()).thenReturn("0".repeat(64));
        when(attempt.getOrigin()).thenReturn(PickPublicationOrigin.LEGACY); when(attempt.fields()).thenReturn(PickDisclosureField.all());
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId())).thenReturn(List.of(row));
        // when / then
        assertThatThrownBy(() -> service.forPublic(race.getId())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenMixedApprovalAndCorrectionOriginsAtOneSourceVersion_whenReading_thenRejectTheirUnion() {
        // given
        var race = race(false); var approved = confirmed(race, 1, Set.of(PickDisclosureField.TRACK), 1, 2);
        var source = approved.getAttempt();
        var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), race, 1, 2,
                PickPublicationOrigin.CORRECTION, PickPublicationOutput.SETTINGS, source.getContent(), Set.of(PickDisclosureField.CAR),
                source.getActorId(), source.getChannelId(), source.getWebhookId(), null, source.getTemplateHash(), source.getRequestManifest(),
                source.getRuntimeGeneration(), source.getCapturedAt()));
        attempt.confirm("test-correction-message", source.getCapturedAt().plusSeconds(2));
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId()))
                .thenReturn(List.of(approved, new PickPublicationSnapshot(attempt)));
        // when / then
        assertThatThrownBy(() -> service.forPublic(race.getId())).isInstanceOf(BusinessRuleException.class);
    }

    PickPublicationSnapshot confirmed(Race race, long version, Set<PickDisclosureField> fields, int seconds, long sourceVersion) {
        var at = Instant.parse("2026-11-03T00:00:00Z");
        var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), race, version, sourceVersion,
                sourceVersion == 0 ? PickPublicationOrigin.LEGACY : PickPublicationOrigin.APPROVAL, PickPublicationOutput.SETTINGS,
                PickPublicationContent.from(race), fields, "test-manager", "test-channel", "test-webhook", null,
                "0".repeat(64), "{}", "00000000-0000-0000-0000-000000000000:0", at));
        attempt.confirm("test-message-" + seconds, at.plusSeconds(seconds));
        return new PickPublicationSnapshot(attempt);
    }

    Race race(boolean finished) {
        var season = new Season("Test disclosure"); season.setId(UUID.randomUUID());
        var day = PhaseTestFixtures.matchdayInRegularPhase(season, "Test disclosure day", 0); day.setId(UUID.randomUUID());
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day);
        var home = new Team("Test home", "T-DHOME"); home.setId(UUID.randomUUID()); race.setHomeTeamOverride(home);
        var away = new Team("Test away", "T-DAWAY"); away.setId(UUID.randomUUID()); race.setAwayTeamOverride(away);
        race.setMatch(new Match(day, home, away));
        if (finished) race.getResults().add(new RaceResult());
        race.setCar(new Car("Test public legacy", "Test private car")); race.setTrack(new Track("Test private track"));
        var entry = new SeasonCarPoolEntry(); entry.setLabel("Test private entry"); race.setCarPoolEntry(entry);
        var settings = new RaceSettings(race); race.setSettings(settings);
        new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 0,
                "Test private weather", "Sunset", "RS", "None").applyTo(settings);
        return race;
    }
}
