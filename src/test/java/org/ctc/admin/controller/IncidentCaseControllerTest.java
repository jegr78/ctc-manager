package org.ctc.admin.controller;

import java.security.Principal;
import java.util.UUID;
import org.ctc.admin.dto.IncidentRulingDraftForm;
import org.ctc.admin.dto.IncidentRulingPublishForm;
import org.ctc.admin.dto.IncidentRulingReviewForm;
import org.ctc.admin.service.IncidentCaseAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentCaseControllerTest {
    private final UUID reportId = UUID.randomUUID(), seasonId = UUID.randomUUID();
    private final IncidentCaseAdminService service = mock(IncidentCaseAdminService.class);
    private final IncidentCaseController controller = new IncidentCaseController(service);
    private final Principal principal = () -> "test-case-manager";

    private IncidentCaseAdminService.Page page() {
        var page = mock(IncidentCaseAdminService.Page.class);
        when(page.seasonId()).thenReturn(seasonId);
        when(service.view(reportId)).thenReturn(page);
        return page;
    }

    @Test void givenAPermanentCase_whenOpening_thenRenderItsNativePageAndSeasonContext() {
        // given
        var expected = page(); var model = new ExtendedModelMap();
        // when
        var view = controller.view(reportId, model);
        // then
        assertThat(view).isEqualTo("admin/incident-case");
        assertThat(model.getAttribute("page")).isSameAs(expected);
        assertThat(model.getAttribute("selectedSeasonId")).isEqualTo(seasonId);
        verify(service, never()).publish(any(), any(), any());
    }

    @Test void givenAnInvalidDraft_whenSaving_thenRetainItsInputWithoutCallingTheWriter() {
        // given
        page(); var form = new IncidentRulingDraftForm(); form.setText("Test entered draft");
        var errors = new BeanPropertyBindingResult(form, "rulingDraftForm"); errors.rejectValue("text", "test.invalid");
        var model = new ExtendedModelMap(); model.addAttribute("rulingDraftForm", form);
        // when
        var view = controller.save(reportId, form, errors, principal, model, new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("admin/incident-case");
        assertThat(model.getAttribute("rulingDraftForm")).isSameAs(form);
        verify(service, never()).saveDraft(any(), any(), any());
    }

    @Test void givenAnAcceptedDraft_whenSaving_thenDelegateTheTrustedPrincipalWithoutPublishing() {
        // given
        var form = new IncidentRulingDraftForm(); form.setText("Test exact draft");
        var errors = new BeanPropertyBindingResult(form, "rulingDraftForm");
        // when
        var view = controller.save(reportId, form, errors, principal, new ExtendedModelMap(), new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("redirect:/admin/incidents/" + reportId);
        verify(service).saveDraft(reportId, form, principal);
        verify(service, never()).publish(any(), any(), any());
    }

    @Test void givenAStaleDraft_whenSaving_thenRenderTheEnteredVersionAndDomainError() {
        // given
        page(); var form = new IncidentRulingDraftForm(); form.setExpectedVersion(4L); form.setText("Test stale draft");
        doThrow(new BusinessRuleException("Ruling changed; reload the current case")).when(service).saveDraft(reportId, form, principal);
        var errors = new BeanPropertyBindingResult(form, "rulingDraftForm");
        var model = new ExtendedModelMap(); model.addAttribute("rulingDraftForm", form);
        // when
        var view = controller.save(reportId, form, errors, principal, model, new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("admin/incident-case");
        assertThat(errors.getGlobalError()).isNotNull();
        assertThat(errors.getGlobalError().getDefaultMessage()).contains("changed");
        assertThat(form.getExpectedVersion()).isEqualTo(4L);
        assertThat(model.getAttribute("rulingDraftForm")).isSameAs(form);
    }

    @Test void givenAnExactPublicationRequest_whenPublishing_thenDelegateTheCapturedFormAndPrincipal() {
        // given
        var form = new IncidentRulingPublishForm(); form.setExpectedVersion(5L); form.setRulingVersion(2); form.setGeneration("test-generation");
        var errors = new BeanPropertyBindingResult(form, "rulingPublishForm");
        // when
        var view = controller.publish(reportId, form, errors, principal, new ExtendedModelMap(), new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("redirect:/admin/incidents/" + reportId);
        verify(service).publish(reportId, form, principal);
        verify(service, never()).saveDraft(any(), any(), any());
    }

    @Test void givenARejectedPublication_whenPublishing_thenKeepItsConsentInputsForExplicitReload() {
        // given
        page(); var form = new IncidentRulingPublishForm(); form.setExpectedVersion(5L); form.setRulingVersion(2);
        doThrow(new BusinessRuleException("Ruling changed; reload the current case")).when(service).publish(reportId, form, principal);
        var errors = new BeanPropertyBindingResult(form, "rulingPublishForm");
        var model = new ExtendedModelMap(); model.addAttribute("rulingPublishForm", form);
        // when
        var view = controller.publish(reportId, form, errors, principal, model, new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("admin/incident-case");
        assertThat(errors.hasGlobalErrors()).isTrue();
        assertThat(form.getRulingVersion()).isEqualTo(2);
        assertThat(model.getAttribute("rulingPublishForm")).isSameAs(form);
    }

    @Test void givenInvalidRecoveryInputs_whenReviewing_thenDoNotAuthorizeTheRemoteDecision() {
        // given
        page(); var form = new IncidentRulingReviewForm();
        var errors = new BeanPropertyBindingResult(form, "rulingReviewForm"); errors.rejectValue("reviewed", "test.required");
        var model = new ExtendedModelMap(); model.addAttribute("rulingReviewForm", form);
        // when
        var view = controller.reconcile(reportId, form, errors, principal, model, new RedirectAttributesModelMap());
        // then
        assertThat(view).isEqualTo("admin/incident-case");
        assertThat(model.getAttribute("rulingReviewForm")).isSameAs(form);
        verify(service, never()).reconcile(any(), any(), any());
    }
}
