package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentMatchContext;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.repository.IncidentMatchContextRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentLifecycleGuardTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentMatchContextRepository contexts = mock(IncidentMatchContextRepository.class);
    final IncidentReportRepository reports = mock(IncidentReportRepository.class);
    final IncidentLifecycleGuard guard = new IncidentLifecycleGuard(contexts, reports, mock(org.ctc.domain.repository.IncidentReportWindowRepository.class));

    @Test
    void reportedRaceCannotMoveMatchday() {
        when(reports.findFirstByRaceIdOrderByIdAsc(f.race.getId())).thenReturn(Optional.of(mock(org.ctc.domain.model.IncidentReport.class)));
        assertThatThrownBy(() -> guard.requireIdentity(f.race, UUID.randomUUID(), f.home, f.away))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
    }

    @Test
    void reportedRaceCannotSwapItsOriginalParticipants() {
        when(reports.findFirstByRaceIdOrderByIdAsc(f.race.getId())).thenReturn(Optional.of(mock(org.ctc.domain.model.IncidentReport.class)));
        assertThat(guard.identityRejection(f.race, f.day.getId(), f.away, f.home)).contains("incident");
    }

    @Test
    void reportedRaceCannotBeDeletedIncludingAfterWithdrawal() {
        when(reports.findFirstByRaceIdOrderByIdAsc(f.race.getId())).thenReturn(Optional.of(mock(org.ctc.domain.model.IncidentReport.class)));
        assertThatThrownBy(() -> guard.requireNoHistory(List.of(f.race)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
    }

    @Test
    void boundRegularContextProtectsItsRaceIdsBeforeFirstReport() {
        when(contexts.findByMatchIdForUpdate(f.match.getId())).thenReturn(Optional.of(f.context));
        assertThat(guard.protects(f.race)).isTrue();
        assertThat(guard.identityRejection(f.race, f.day.getId(), f.away, f.home)).contains("incident");
        assertThatThrownBy(() -> guard.requireNoHistory(List.of(f.race))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
    }

    @Test
    void boundMatchCannotBeDeletedWithAnEmptyDraftWindow() {
        when(contexts.findByMatchIdForUpdate(f.match.getId())).thenReturn(Optional.of(f.context));
        assertThatThrownBy(() -> guard.requireMatchDeletion(f.match))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
    }

    @Test
    void boundPlayoffContextProtectsAuxiliaryRegularRace() {
        var matchup = new PlayoffMatchup(); matchup.setId(UUID.randomUUID());
        f.race.setPlayoffMatchup(matchup);
        var context = new IncidentMatchContext(null, matchup, f.season, f.home, f.away,
                IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, "test-manager", IncidentUnitFixture.NOW);
        when(contexts.findByPlayoffMatchupIdForUpdate(matchup.getId())).thenReturn(Optional.of(context));
        assertThat(guard.protects(f.race)).isTrue();
        assertThatThrownBy(() -> guard.requirePlayoffIdentity(matchup, f.away, f.home))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
        assertThatCode(() -> guard.requirePlayoffIdentity(matchup, f.home, f.away)).doesNotThrowAnyException();
    }

    @Test
    void originalRaceIdentityRemainsEditableForResultsAndSchedule() {
        when(reports.findFirstByRaceIdOrderByIdAsc(f.race.getId())).thenReturn(Optional.of(mock(org.ctc.domain.model.IncidentReport.class)));
        assertThatCode(() -> guard.requireIdentity(f.race, f.day.getId(), f.home, f.away)).doesNotThrowAnyException();
    }

    @Test
    void unrelatedLegacyRaceRetainsExistingEditingAndDeletionBehavior() {
        assertThat(guard.protects(f.race)).isFalse();
        assertThatCode(() -> guard.requireIdentity(f.race, UUID.randomUUID(), f.away, f.home)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireNoHistory(List.of(f.race))).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireMatchDeletion(f.match)).doesNotThrowAnyException();
    }
}
