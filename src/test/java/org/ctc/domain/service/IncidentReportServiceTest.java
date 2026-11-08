package org.ctc.domain.service;

import java.time.Clock;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentReportServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentReportRepository reports = mock(IncidentReportRepository.class);
    final IncidentReportRevisionRepository revisions = mock(IncidentReportRevisionRepository.class);
    final IncidentReportEventRepository events = mock(IncidentReportEventRepository.class);
    final IncidentWindowService windows = mock(IncidentWindowService.class);
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final IncidentReportAccess access = mock(IncidentReportAccess.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final Clock clock = mock(Clock.class);
    final EntityManager em = mock(EntityManager.class);
    final Map<UUID, IncidentReport> saved = new HashMap<>();
    final Map<UUID, IncidentReportEvent> requests = new HashMap<>();
    final ArrayList<IncidentReportRevision> history = new ArrayList<>();
    final IncidentPayload payload = new IncidentPayload("Test driver and guest", "00:00", "Test <private> incident facts", "https://example.invalid/footage");
    AuthorizedIncidentActor actor;
    IncidentReportService service;

    @BeforeEach
    void setUp() {
        f.openWindow(); actor = actor("400000000000000004", f.party());
        when(clock.instant()).thenReturn(IncidentUnitFixture.NOW);
        when(windows.lock(f.window.getId())).thenReturn(f.window);
        when(contexts.resolve(f.reference())).thenAnswer(inv -> f.target());
        when(contexts.get(f.context.getId())).thenReturn(f.context);
        when(writes.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(reports.save(any())).thenAnswer(inv -> { IncidentReport row = inv.getArgument(0); if (row.getId() == null) IncidentUnitFixture.id(row); saved.put(row.getId(), row); return row; });
        when(reports.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0))));
        when(reports.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0))));
        when(revisions.save(any())).thenAnswer(inv -> { IncidentReportRevision row = IncidentUnitFixture.id(inv.getArgument(0)); history.add(row); return row; });
        when(events.save(any())).thenAnswer(inv -> { IncidentReportEvent row = IncidentUnitFixture.id(inv.getArgument(0)); if (row.getRequestId() != null) requests.put(row.getRequestId(), row); return row; });
        when(events.findByRequestId(any())).thenAnswer(inv -> Optional.ofNullable(requests.get(inv.getArgument(0))));
        service = service(Optional.of(access));
    }

    @Test
    void givenCompleteConfirmation_whenSubmitting_thenKeepPermanentCaseRevisionAndOriginalParty() {
        UUID request = UUID.randomUUID(); var receipt = submit(request);
        assertThat(receipt.reportId()).isNotNull(); assertThat(receipt.caseReference()).startsWith("INC-");
        assertThat(receipt.version()).isEqualTo(1); assertThat(receipt.revisionNumber()).isEqualTo(1);
        assertThat(receipt.acceptedAt()).isEqualTo(IncidentUnitFixture.NOW);
        assertThat(history).hasSize(1); assertThat(history.getFirst().getPayload()).isEqualTo(payload);
        assertThat(history.getFirst().getPayload().getReplayTimestamp()).isEqualTo("00:00");
        var report = saved.get(receipt.reportId()); assertThat(report.getParty()).isEqualTo(f.party());
        assertThat(report.getAuthorId()).isEqualTo(actor.userId()); assertThat(report.getRace()).isSameAs(f.race);
        assertThat(report.getFrozenRevisionNumber()).isNull();
    }

    @Test
    void givenSameAuthorWithDistinctRequests_whenSubmitting_thenRetainTwoDistinctCases() {
        var one = submit(UUID.randomUUID()); var two = submit(UUID.randomUUID());
        assertThat(one.reportId()).isNotEqualTo(two.reportId()); assertThat(one.caseReference()).isNotEqualTo(two.caseReference());
        assertThat(saved).hasSize(2); assertThat(history).hasSize(2);
    }

    @Test
    void givenLostReplyAfterDeadline_whenReplaying_thenRecoverOriginalReceiptWithoutAnotherRevision() {
        UUID request = UUID.randomUUID(); var receipt = submit(request);
        when(clock.instant()).thenReturn(f.window.getDeadline().plusSeconds(60));
        var replay = assertDoesNotThrow(() -> service.submit(f.window.getId(), f.race.getId(), 0, request, actor, null));
        assertThat(replay).isEqualTo(receipt); assertThat(history).hasSize(1); assertThat(saved).hasSize(1);
    }

    @Test
    void givenDifferentCreator_whenEditingOrWithdrawing_thenKeepOriginalHistory() {
        var receipt = submit(UUID.randomUUID()); var teammate = actor("400000000000000005", f.party());
        assertThatThrownBy(() -> service.edit(receipt.reportId(), 1, 1, UUID.randomUUID(), teammate, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("creator");
        assertThatThrownBy(() -> service.withdraw(receipt.reportId(), 1, 1, UUID.randomUUID(), teammate))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("creator");
        assertThat(history).hasSize(1); assertThat(saved.get(receipt.reportId()).getVersion()).isEqualTo(1);
    }

    @Test
    void givenCurrentCreator_whenEditingAndWithdrawing_thenRetainSequentialHistoryAndPartySnapshot() {
        var receipt = submit(UUID.randomUUID()); var original = saved.get(receipt.reportId()).getParty();
        var changedLabel = new IncidentParty(original.getType(), original.getId(), "Test renamed team", "300000000000000099");
        var currentActor = actor(actor.userId(), changedLabel);
        var replacement = new IncidentPayload("Test other driver", "Lap 2 at 01:00", "Test correction", "");
        var edited = assertDoesNotThrow(() -> service.edit(receipt.reportId(), 1, 1, UUID.randomUUID(), currentActor, replacement));
        assertThat(edited.revisionNumber()).isEqualTo(2); assertThat(edited.version()).isEqualTo(2);
        var withdrawn = assertDoesNotThrow(() -> service.withdraw(receipt.reportId(), 1, 2, UUID.randomUUID(), currentActor));
        assertThat(withdrawn.version()).isEqualTo(3); assertThat(history).hasSize(2);
        assertThat(history.getFirst().getPayload()).isEqualTo(payload);
        var report = saved.get(receipt.reportId()); assertThat(report.getState()).isEqualTo(IncidentReportState.WITHDRAWN);
        assertThat(report.getParty()).isEqualTo(original); assertThat(report.getLatestRevisionNumber()).isEqualTo(2);
        assertThat(requests).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"submit", "edit", "withdraw"})
    void givenExactCutoff_whenMutating_thenRejectEvenWithOpenWindowState(String action) {
        var existing = action.equals("submit") ? null : submit(UUID.randomUUID());
        when(clock.instant()).thenReturn(f.window.getDeadline());
        assertThatThrownBy(() -> {
            if (action.equals("submit")) service.submit(f.window.getId(), f.race.getId(), 1, UUID.randomUUID(), actor, payload);
            else if (action.equals("edit")) service.edit(existing.reportId(), 1, 1, UUID.randomUUID(), actor, payload);
            else service.withdraw(existing.reportId(), 1, 1, UUID.randomUUID(), actor);
        }).isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
        assertThat(f.window.getState()).isEqualTo(IncidentWindowState.OPEN);
        assertThat(history).hasSize(action.equals("submit") ? 0 : 1);
    }

    @Test
    void givenChangedWindowOrReportVersion_whenEditing_thenRejectWithoutMutation() {
        var receipt = submit(UUID.randomUUID()); ReflectionTestUtils.setField(f.window, "version", 2L);
        assertThatThrownBy(() -> service.edit(receipt.reportId(), 1, 1, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.edit(receipt.reportId(), 2, 0, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(history).hasSize(1);
    }

    @Test
    void givenRequestOwnedByAnotherCreator_whenReplaying_thenDoNotDiscloseReceipt() {
        UUID request = UUID.randomUUID(); submit(request);
        assertThatThrownBy(() -> service.submit(f.window.getId(), f.race.getId(), 1, request, actor("400000000000000005", f.party()), payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("another");
        assertThat(history).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"drivers", "timestamp", "description", "race"})
    void givenInvalidCaseInput_whenSubmitting_thenKeepReportAndHistoryAbsent(String field) {
        var invalid = new IncidentPayload(field.equals("drivers") ? " " : "Test guest", field.equals("timestamp") ? " " : "00:00",
                field.equals("description") ? " " : "Test description", "");
        UUID race = field.equals("race") ? UUID.randomUUID() : f.race.getId();
        assertThatThrownBy(() -> service.submit(f.window.getId(), race, 1, UUID.randomUUID(), actor, invalid))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(saved).isEmpty(); assertThat(history).isEmpty();
    }

    @Test
    void givenNoConfiguredReporterAuthorization_whenSubmitting_thenDenyInsteadOfTrustingAnActorRecord() {
        service = service(Optional.empty());
        assertThatThrownBy(() -> service.submit(f.window.getId(), f.race.getId(), 1, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        assertThat(saved).isEmpty(); verifyNoInteractions(revisions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "type"})
    void givenCreatorRepresentingAnotherParty_whenEditingOrWithdrawing_thenPreserveTheOriginalParty(String change) {
        var receipt = submit(UUID.randomUUID());
        var changed = new org.ctc.domain.model.IncidentParty(change.equals("type") ? org.ctc.domain.model.IncidentPartyType.ORGANIZATION : org.ctc.domain.model.IncidentPartyType.TEAM,
                change.equals("id") ? f.away.getId() : f.home.getId(), "Test changed party", "300000000000000003");
        var current = actor(actor.userId(), changed);
        assertThatThrownBy(() -> service.edit(receipt.reportId(), 1, 1, UUID.randomUUID(), current, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("original reporting party");
        assertThatThrownBy(() -> service.withdraw(receipt.reportId(), 1, 1, UUID.randomUUID(), current))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("original reporting party");
        assertThat(history).hasSize(1); assertThat(saved.get(receipt.reportId()).getParty()).isEqualTo(f.party());
    }

    private IncidentReportReceipt submit(UUID request) {
        return assertDoesNotThrow(() -> service.submit(f.window.getId(), f.race.getId(), 1, request, actor, payload));
    }
    private AuthorizedIncidentActor actor(String user, IncidentParty party) {
        return new AuthorizedIncidentActor(f.context.getId(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, user, party);
    }
    private IncidentReportService service(Optional<IncidentReportAccess> authorization) {
        return new IncidentReportService(reports, revisions, events, windows, contexts, authorization, writes, clock, em, mock(org.springframework.context.ApplicationEventPublisher.class), mock(IncidentLineupService.class));
    }
}
