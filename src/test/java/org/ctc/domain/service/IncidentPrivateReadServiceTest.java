package org.ctc.domain.service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentPrivateReadServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentAccessService access = mock(IncidentAccessService.class);
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final IncidentWindowService windows = mock(IncidentWindowService.class);
    final IncidentReportRepository reports = mock(IncidentReportRepository.class);
    final IncidentReportRevisionRepository revisions = mock(IncidentReportRevisionRepository.class);
    final Clock clock = Clock.fixed(IncidentUnitFixture.NOW, ZoneOffset.UTC);
    final IncidentPrivateReadService reads = new IncidentPrivateReadService(access, contexts, windows, reports, revisions, clock);
    final AuthorizedIncidentActor actor = new AuthorizedIncidentActor(f.context.getId(), IncidentUnitFixture.GUILD, IncidentUnitFixture.CHANNEL, "400", f.party());
    final IncidentReport report = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, actor.userId(), f.party()));
    final IncidentPayload payload = new IncidentPayload("Test involved guest", "00:00", "Test private case", "");

    @BeforeEach
    void setUp() {
        f.openWindow(); report.appendRevision();
        when(contexts.get(f.context.getId())).thenReturn(f.context); when(contexts.resolve(f.reference())).thenReturn(f.target());
        when(windows.getForContext(f.context.getId())).thenReturn(f.window);
        when(reports.findByWindowIdAndAuthorIdOrderByCaseReferenceAsc(f.window.getId(), actor.userId())).thenReturn(List.of(report));
        when(reports.findByIdAndWindowContextIdAndAuthorId(report.getId(), f.context.getId(), actor.userId())).thenReturn(Optional.of(report));
        when(revisions.findByReportIdAndRevisionNumber(report.getId(), 1)).thenReturn(Optional.of(new IncidentReportRevision(report, 1, 1, payload, actor.userId(), IncidentUnitFixture.NOW, UUID.randomUUID())));
    }

    @Test
    void givenCurrentReporter_whenListingMyCases_thenQueryOnlyTheirWindowAndAuthorWithoutLoadingPayloads() {
        var view = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> reads.home(actor));
        assertThat(view.contextId()).isEqualTo(f.context.getId()); assertThat(view.windowId()).isEqualTo(f.window.getId());
        assertThat(view.editable()).isTrue(); assertThat(view.cases()).hasSize(1);
        assertThat(view.races().getFirst().label()).contains("Test Incident round", "TIH", "TIA", "Leg 1");
        assertThat(view.cases().getFirst().reference()).isEqualTo(report.getCaseReference());
        verify(access).requireCurrent(actor, f.context, null); verifyNoInteractions(revisions);
        assertThatThrownBy(() -> view.races().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void givenOwnConfirmedCase_whenReading_thenRecheckOriginalPartyAndReturnTheCompleteSavedRevision() {
        var detail = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> reads.load(actor, report.getId()));
        assertThat(detail.payload()).isSameAs(payload); assertThat(detail.summary().party()).isEqualTo(f.party().getLabel());
        assertThat(detail.summary().revision()).isEqualTo(1); assertThat(detail.summary().version()).isEqualTo(1);
        verify(access).requireCurrent(actor, f.context, report.getParty());
    }

    @Test
    void givenForeignReportId_whenReading_thenDenyWithoutQueryingItsPayload() {
        UUID foreign = UUID.randomUUID();
        assertThatThrownBy(() -> reads.load(actor, foreign)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("unavailable");
        verifyNoInteractions(revisions);
        verify(reports).findByIdAndWindowContextIdAndAuthorId(foreign, f.context.getId(), actor.userId());
        verify(reports, never()).findById(any());
    }

    @Test
    void givenRevokedCurrentAccess_whenListingOrReading_thenDenyBeforeQueryingPrivateCases() {
        doThrow(new BusinessRuleException("unavailable")).when(access).requireCurrent(actor, f.context, null);
        assertThatThrownBy(() -> reads.home(actor)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> reads.load(actor, report.getId())).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(reports, revisions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"draft", "expired", "closed", "withdrawn"})
    void givenNonEditableReportingState_whenReading_thenKeepHistoryWithoutOfferingMutations(String state) {
        switch (state) {
            case "draft" -> org.springframework.test.util.ReflectionTestUtils.setField(f.window, "state", IncidentWindowState.DRAFT);
            case "closed" -> f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
            case "expired" -> org.springframework.test.util.ReflectionTestUtils.setField(f.window, "deadline", IncidentUnitFixture.NOW);
            case "withdrawn" -> report.withdraw();
            default -> throw new AssertionError(state);
        }
        var view = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> reads.home(actor));
        assertThat(view.cases()).hasSize(1); assertThat(view.cases().getFirst().editable()).isFalse();
        if (!state.equals("withdrawn")) assertThat(view.editable()).isFalse();
    }

    @Test
    void givenReporterNowRepresentingAnotherOriginalParty_whenListing_thenDoNotExposeEarlierCaseMetadata() {
        var changed = new AuthorizedIncidentActor(actor.contextId(), actor.guildId(), actor.channelId(), actor.userId(),
                new IncidentParty(IncidentPartyType.TEAM, f.away.getId(), f.away.getName(), f.away.getDiscordRoleId()));
        var view = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> reads.home(changed));
        assertThat(view.cases()).isEmpty(); verifyNoInteractions(revisions);
    }

    @Test
    void givenOriginalPartyEligibilityLostBetweenIndexAndPayload_whenReading_thenDenyBeforeLoadingPrivateText() {
        doThrow(new BusinessRuleException("unavailable")).when(access).requireCurrent(actor, f.context, report.getParty());
        assertThatThrownBy(() -> reads.load(actor, report.getId())).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(revisions);
    }

    @Test
    void givenMissingLatestRevision_whenReading_thenDenyRatherThanInventingAnEmptyCase() {
        when(revisions.findByReportIdAndRevisionNumber(report.getId(), 1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reads.load(actor, report.getId())).isInstanceOf(BusinessRuleException.class).hasMessageNotContaining("Test private case");
    }
}
