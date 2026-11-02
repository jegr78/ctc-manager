package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickReservationServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"submitted", "returned", "closed"})
    void givenConfirmedPendingPick_whenReadingReservations_thenReserveLatestPayloadRegardlessOfEditability(String state) {
        // given
        var picks = mock(MatchdayPickRepository.class);
        var revisions = mock(MatchdayPickRevisionRepository.class);
        var manager = mock(EntityManager.class);
        var service = new PickReservationService(picks, revisions, manager);
        var season = new Season("Test_PickReservation");
        season.setId(UUID.randomUUID());
        var race = new Race();
        race.setId(UUID.randomUUID());
        var team = new Team();
        team.setId(UUID.randomUUID());
        var pick = new MatchdayPick(race, season, team);
        pick.submit();
        pick.submit();
        if (state.equals("returned")) pick.returnForCorrection();
        if (state.equals("closed")) pick.close();
        var payload = PickSubmissionServiceTest.payload();
        var revision = new MatchdayPickRevision(pick, 2, payload, Instant.now(), "12345", UUID.randomUUID(), pick.getVersion());
        when(picks.findBySeasonIdForUpdate(season.getId())).thenReturn(List.of(pick));
        when(revisions.findByPickIdAndRevisionNumber(pick.getId(), 2)).thenReturn(Optional.of(revision));
        // when / then
        assertThat(service.pending(season.getId(), true)).containsExactly(new PickReservationService.Reservation(
                race.getId(), team.getId(), payload.carId(), payload.carPoolEntryId(), payload.trackId()));
        verify(manager).refresh(pick, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "approved"})
    void givenNoPendingRevision_whenReadingReservations_thenDoNotReserveDraftsOrDoubleCountApproval(String state) {
        // given
        var picks = mock(MatchdayPickRepository.class);
        var revisions = mock(MatchdayPickRevisionRepository.class);
        var service = new PickReservationService(picks, revisions, mock(EntityManager.class));
        var season = new Season("Test_NoReservation");
        season.setId(UUID.randomUUID());
        var pick = new MatchdayPick(new Race(), season, new Team());
        if (state.equals("approved")) { pick.submit(); pick.approve(1); }
        when(picks.findBySeasonIdForUpdate(season.getId())).thenReturn(List.of(pick));
        // when / then
        assertThat(service.pending(season.getId(), true)).isEmpty();
        verify(picks).findBySeasonIdForUpdate(season.getId());
        verifyNoInteractions(revisions);
    }
}
