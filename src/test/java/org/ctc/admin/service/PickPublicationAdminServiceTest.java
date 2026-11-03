package org.ctc.admin.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.discord.service.DiscordPickReconciliationService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.ctc.domain.repository.PickPublicationSnapshotRepository;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.PickPublicationService;
import org.ctc.domain.service.PickSessionEpoch;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickPublicationAdminServiceTest {
    @Test
    void givenRestoreDuringPublicationReviewRead_whenPreparingTheForm_thenRetainItsOriginalGeneration() {
        // given
        var attempts = mock(PickPublicationAttemptRepository.class); var publications = mock(PickPublicationService.class); var epoch = new PickSessionEpoch();
        var service = new PickPublicationAdminService(attempts, mock(PickPublicationSnapshotRepository.class), publications,
                mock(DiscordPickReconciliationService.class), epoch, mock(Environment.class));
        var season = new Season("Test review restore", 2035, 1); season.setId(UUID.randomUUID());
        var day = PhaseTestFixtures.matchdayInRegularPhase(season, "Test round", 1); day.setId(UUID.randomUUID());
        var home = new Team("Test review home", "T-RH"); home.setId(UUID.randomUUID());
        var away = new Team("Test review away", "T-RA"); away.setId(UUID.randomUUID());
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day); race.setHomeTeamOverride(home); race.setAwayTeamOverride(away);
        UUID batch = UUID.randomUUID(); String before = epoch.managementGeneration();
        var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(batch, race, 1, 0, PickPublicationOrigin.LEGACY, PickPublicationOutput.SETTINGS,
                PickPublicationContent.from(race), Set.of(PickDisclosureField.TRACK), "test-admin", "100", "200", null, "a".repeat(64), "{}", before, Instant.now()));
        when(attempts.findByBatchId(batch)).thenAnswer(call -> { epoch.afterRestore(null); return List.of(attempt); });
        when(publications.reconciliationFingerprint(batch)).thenReturn("b".repeat(64));
        // when
        var detail = service.detail(batch); var form = service.form(detail);
        // then
        assertThat(detail.generation()).isEqualTo(before).isNotEqualTo(epoch.managementGeneration());
        assertThat(form.getExpectedGeneration()).isEqualTo(before);
    }
}
