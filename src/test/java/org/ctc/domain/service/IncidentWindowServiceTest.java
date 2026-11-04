package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentWindowServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentReportWindowRepository windows = mock(IncidentReportWindowRepository.class);
    final IncidentReportEventRepository events = mock(IncidentReportEventRepository.class);
    final IncidentReportRepository reports = mock(IncidentReportRepository.class);
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    final EntityManager em = mock(EntityManager.class);
    final Clock clock = mock(Clock.class);
    final IncidentManagementActor manager = new IncidentManagementActor("test-manager");
    IncidentWindowService service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(IncidentUnitFixture.NOW);
        when(contexts.get(f.context.getId())).thenReturn(f.context);
        when(contexts.resolve(f.reference())).thenAnswer(inv -> f.target());
        when(windows.findByContextId(f.context.getId())).thenReturn(Optional.of(f.window));
        when(windows.findById(f.window.getId())).thenReturn(Optional.of(f.window));
        when(windows.findByIdForUpdate(f.window.getId())).thenReturn(Optional.of(f.window));
        when(contexts.lock(f.context.getId())).thenReturn(f.context);
        when(writes.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        service = new IncidentWindowService(windows, events, reports, contexts, writes, em, clock, mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @ParameterizedTest
    @CsvSource({"2026-03-28T20:00:00,2026-03-29T19:00:00Z", "2026-10-24T20:00:00,2026-10-25T18:00:00Z"})
    void givenPlannedStartAcrossDst_whenPreviewing_thenAddTwentyFourElapsedHours(String local, String expected) {
        f.race.setDateTime(LocalDateTime.parse(local));
        var preview = assertDoesNotThrow(() -> service.previewDeadline(f.context.getId()));
        assertThat(preview.defaultDeadline()).isEqualTo(Instant.parse(expected));
        assertThat(preview.plannedStartLocal()).isEqualTo(f.race.getDateTime());
        assertThat(f.window.getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-03-29T02:30:00", "2026-10-25T02:30:00"})
    void givenMissingOrAmbiguousStart_whenPreviewing_thenRequireExplicitDeadlineWithoutNowPlusFallback(String local) {
        f.race.setDateTime(local == null ? null : LocalDateTime.parse(local));
        var preview = assertDoesNotThrow(() -> service.previewDeadline(f.context.getId()));
        assertThat(preview.defaultDeadline()).isNull(); assertThat(preview.problem()).isNotBlank();
        assertThat(f.window.getDeadline()).isNull();
    }

    @Test
    void givenPastDefault_whenPreviewing_thenShowItsActualOriginWithoutOpening() {
        f.race.setDateTime(LocalDateTime.of(2020, 1, 1, 20, 0));
        var preview = assertDoesNotThrow(() -> service.previewDeadline(f.context.getId()));
        assertThat(preview.defaultDeadline()).isEqualTo(Instant.parse("2020-01-02T19:00:00Z"));
        assertThat(preview.expired()).isTrue(); assertThat(f.window.getState()).isEqualTo(IncidentWindowState.DRAFT);
    }

    @Test
    void givenExplicitFutureDeadline_whenOpening_thenRecordManagementAndFreezePlannedReference() {
        Instant chosen = IncidentUnitFixture.NOW.plusSeconds(7200);
        assertDoesNotThrow(() -> service.open(f.context.getId(), chosen, 0, manager));
        assertThat(f.window.getState()).isEqualTo(IncidentWindowState.OPEN);
        assertThat(f.window.getDeadline()).isEqualTo(chosen); assertThat(f.window.getVersion()).isEqualTo(1);
        assertThat(f.window.getOpenedBy()).isEqualTo(manager.id());
        var event = ArgumentCaptor.forClass(IncidentReportEvent.class); verify(events).save(event.capture());
        assertThat(event.getValue().getDeadline()).isEqualTo(chosen);
        var original = f.window.getPlannedStart(); f.race.setDateTime(f.race.getDateTime().plusDays(4));
        assertThat(assertDoesNotThrow(() -> service.previewDeadline(f.context.getId())).plannedStart()).isEqualTo(original);
        assertThat(f.window.getDeadline()).isEqualTo(chosen);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void givenNonFutureDeadline_whenOpening_thenKeepDraftWithoutDecision(long seconds) {
        assertThatThrownBy(() -> service.open(f.context.getId(), IncidentUnitFixture.NOW.plusSeconds(seconds), 0, manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
        assertThat(f.window.getState()).isEqualTo(IncidentWindowState.DRAFT); verify(events, never()).save(any());
    }

    @Test
    void givenOpenWindow_whenChangingDeadline_thenRequireReasonAndCurrentVersion() {
        f.openWindow();
        assertThatThrownBy(() -> service.changeDeadline(f.context.getId(), IncidentUnitFixture.NOW.plusSeconds(7200), 0, "Test change", manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.changeDeadline(f.context.getId(), IncidentUnitFixture.NOW.plusSeconds(7200), 1, " ", manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        assertThat(f.window.getVersion()).isEqualTo(1);
    }

    @Test
    void givenExpiredOrClosedWindow_whenChangingOrReopening_thenDenyWithoutNewDeadline() {
        f.openWindow(); when(clock.instant()).thenReturn(f.window.getDeadline());
        assertThatThrownBy(() -> service.changeDeadline(f.context.getId(), f.window.getDeadline().plusSeconds(3600), 1, "Test late change", manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
        ReflectionTestUtils.setField(f.window, "state", IncidentWindowState.CLOSED);
        assertThatThrownBy(() -> service.open(f.context.getId(), f.window.getDeadline().plusSeconds(3600), 1, manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("open");
        assertThat(f.window.getVersion()).isEqualTo(1);
    }
}
