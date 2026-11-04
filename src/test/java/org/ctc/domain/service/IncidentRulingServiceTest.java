package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.model.IncidentReport;
import org.ctc.domain.model.IncidentReportWindow;
import org.ctc.domain.model.IncidentRuling;
import org.ctc.domain.repository.IncidentReportRepository;
import org.ctc.domain.repository.IncidentRulingRepository;
import org.ctc.domain.repository.IncidentRulingRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class IncidentRulingServiceTest {
    private IncidentRulingService service;
    private IncidentReportRepository privateReports;
    private ImportLockService importAdmission;
    private org.ctc.domain.repository.IncidentRulingPublicationRepository privateWork;
    private final UUID reportId = UUID.randomUUID();
    private final IncidentManagementActor manager = new IncidentManagementActor("test-private-manager");
    private static final String GENERATION = "test-ruling-generation";

    @BeforeEach void storage() {
        var reports = mock(IncidentReportRepository.class); var rulings = mock(IncidentRulingRepository.class);
        var revisions = mock(IncidentRulingRevisionRepository.class); var windows = mock(IncidentWindowService.class);
        var imports = mock(ImportLockService.class); var epoch = mock(PickSessionEpoch.class);
        privateReports = reports; importAdmission = imports;
        privateWork = mock(org.ctc.domain.repository.IncidentRulingPublicationRepository.class);
        var manager = mock(PlatformTransactionManager.class); var em = mock(EntityManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(imports.tryEnterWriter()).thenReturn(true); when(epoch.managementGeneration()).thenReturn(GENERATION);
        var report = mock(IncidentReport.class); var window = mock(IncidentReportWindow.class); UUID windowId = UUID.randomUUID();
        when(report.getId()).thenReturn(reportId); when(report.getCaseReference()).thenReturn("INC-CASE-TEST");
        when(report.getWindow()).thenReturn(window); when(window.getId()).thenReturn(windowId);
        when(reports.findById(reportId)).thenReturn(Optional.of(report));
        when(reports.findByIdForUpdate(reportId)).thenReturn(Optional.of(report)); when(windows.lock(windowId)).thenReturn(window);
        var stored = new AtomicReference<IncidentRuling>();
        when(rulings.findByReportIdForUpdate(reportId)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(rulings.save(any(IncidentRuling.class))).thenAnswer(call -> {
            var row = call.getArgument(0, IncidentRuling.class); if (row.getId() == null) ReflectionTestUtils.setField(row, "id", UUID.randomUUID()); stored.set(row); return row;
        });
        when(revisions.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new IncidentRulingService(reports, rulings, revisions, windows, imports, epoch,
                Clock.fixed(Instant.parse("2035-01-02T20:00:00Z"), ZoneOffset.UTC), em, manager,
                privateWork, mock(org.ctc.domain.repository.IncidentDeliveryRepository.class),
                mock(org.ctc.domain.repository.IncidentThreadPublicationRepository.class), mock(org.ctc.domain.incidents.IncidentRulingSender.class),
                mock(IncidentThreadPublicationService.class));
    }

    @Test void givenAManagementDraft_whenSaving_thenRetainExactTextAndAuthorWithoutAddingThemToItsPublicPreview() {
        // given
        String text = "Test final judgment\nNo scoring change. <@123456789012345678>";
        // when
        var saved = service.saveDraft(reportId, 0, text, "Test initial decision", manager, GENERATION);
        // then
        assertThat(saved).isNotNull();
        assertThat(saved.getRevisionNumber()).isEqualTo(1);
        assertThat(saved.getText()).isEqualTo(text);
        assertThat(saved.getActorId()).isEqualTo(manager.id());
        assertThat(saved.getReason()).isEqualTo("Test initial decision");
        assertThat(service.preview(saved)).contains(text).doesNotContain(manager.id());
    }

    @Test void givenAnExistingDraft_whenSavingAnother_thenPreserveTheOriginalImmutableVersion() {
        // given
        var original = service.saveDraft(reportId, 0, "Test original ruling", "", manager, GENERATION);
        // when
        var changed = service.saveDraft(reportId, 1, "Test changed ruling", "Test revised draft", manager, GENERATION);
        // then
        assertThat(original).isNotNull(); assertThat(changed).isNotNull();
        assertThat(original.getText()).isEqualTo("Test original ruling");
        assertThat(original.getRevisionNumber()).isEqualTo(1);
        assertThat(changed.getText()).isEqualTo("Test changed ruling");
        assertThat(changed.getRevisionNumber()).isEqualTo(2);
    }

    @Test void givenAStaleDraftVersion_whenSaving_thenRejectInsteadOfReplacingTheCurrentText() {
        // given
        service.saveDraft(reportId, 0, "Test current ruling", "", manager, GENERATION);
        // when / then
        assertThatThrownBy(() -> service.saveDraft(reportId, 0, "Test stale replacement", "", manager, GENERATION))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
    }

    @Test void givenAChangedDraft_whenPublishingThePreviouslyReviewedVersion_thenRejectStaleConsent() {
        // given
        service.saveDraft(reportId, 0, "Test first ruling", "", manager, GENERATION);
        service.saveDraft(reportId, 1, "Test second ruling", "Test revised draft", manager, GENERATION);
        // when / then
        assertThatThrownBy(() -> service.publish(reportId, 1, 1, manager, GENERATION))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "generation"})
    void missingAuthorityCannotReadPrivateCasesOrEnterWriterAdmission(String cause) {
        assertThatThrownBy(() -> service.saveDraft(reportId, 0, "Test valid draft", "", cause.equals("actor") ? null : manager,
                cause.equals("generation") ? "old-runtime" : GENERATION)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(privateReports, importAdmission);
    }
    @ParameterizedTest @ValueSource(strings = {"draft", "dispatch", "review"})
    void restoringStateDeniesEveryRulingWriterBeforeReadingPrivateData(String action) {
        when(importAdmission.tryEnterWriter()).thenReturn(false);
        if (action.equals("draft")) assertThatThrownBy(() -> service.saveDraft(reportId, 0, "Test valid draft", "", manager, GENERATION)).isInstanceOf(BusinessRuleException.class);
        else if (action.equals("dispatch")) assertThat(service.dispatch(UUID.randomUUID())).isFalse();
        else assertThatThrownBy(() -> service.reconcile(reportId, UUID.randomUUID(), 0, 0, "RETRY_MISSING", "", manager, "Test reviewed decision", true, GENERATION)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(privateReports, privateWork); verify(importAdmission, never()).exitWriter();
    }

    @ParameterizedTest @ValueSource(strings = {"high", "low"})
    void malformedUnicodeCannotBeAcceptedAsAnExactPublicRuling(String cause) {
        String text = cause.equals("high") ? "\uD800" : "\uDC00";
        assertThatThrownBy(() -> service.saveDraft(reportId, 0, text, "", manager, GENERATION)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "empty", "long", "generation"})
    void givenUntrustedOrInvalidDraftInput_whenSaving_thenRejectBeforeAcceptingARulingVersion(String cause) {
        // given
        String text = cause.equals("empty") ? " \n" : cause.equals("long") ? "x".repeat(1801) : "Test valid draft";
        // when / then
        assertThatThrownBy(() -> service.saveDraft(reportId, 0, text, "", cause.equals("actor") ? null : manager,
                cause.equals("generation") ? "stale-process" : GENERATION)).isInstanceOf(BusinessRuleException.class);
    }
}
