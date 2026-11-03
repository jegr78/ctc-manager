package org.ctc.admin.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.MatchdayPickView.*;
import org.ctc.admin.dto.PickReviewForm;
import org.ctc.admin.dto.PickCorrectionForm;
import org.ctc.admin.service.MatchdayPickAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickState;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchdayPickControllerTest {
    final MatchdayPickAdminService service = mock(MatchdayPickAdminService.class);
    final MatchdayPickController controller = new MatchdayPickController(service);
    final UUID race = UUID.randomUUID(), season = UUID.randomUUID(), day = UUID.randomUUID();
    final Principal principal = () -> "test-manager";

    @Test
    void givenOverviewFilters_whenLoading_thenDelegateExactContextWithoutMutation() {
        // given
        var data = new Overview(season, day, PickState.RETURNED, List.of(), List.of(), List.of());
        when(service.overview(season, day, PickState.RETURNED)).thenReturn(data); var model = new ConcurrentModel();
        // when / then
        assertThat(controller.list(season, day, PickState.RETURNED, model)).isEqualTo("admin/matchday-picks");
        assertThat(model.getAttribute("overview")).isSameAs(data);
        verify(service, never()).decide(any(), any(), any());
    }

    @Test
    void givenValidDecision_whenPosting_thenUseServerPrincipalAndConfirmSeparateFromPublication() {
        // given
        var form = form(); var redirect = new RedirectAttributesModelMap();
        // when / then
        assertThat(controller.decide(race, form, new BeanPropertyBindingResult(form, "form"), principal, new ConcurrentModel(), redirect))
                .isEqualTo("redirect:/admin/matchday-picks/" + race);
        verify(service).decide(race, form, principal);
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("Pick review decision saved. Publication remains separate.");
    }

    @Test
    void givenDomainConflict_whenPosting_thenRetainSubmittedReasonVersionAndGlobalError() {
        // given
        var form = form(); var result = new BeanPropertyBindingResult(form, "form"); var model = new ConcurrentModel();
        when(service.detail(race)).thenReturn(detail());
        doThrow(new BusinessRuleException("Pick changed; reload its current version")).when(service).decide(race, form, principal);
        // when / then
        assertThat(controller.decide(race, form, result, principal, model, new RedirectAttributesModelMap())).isEqualTo("admin/matchday-pick-review");
        assertThat(model.getAttribute("form")).isSameAs(form);
        assertThat(form.getReason()).isEqualTo("Test correction reason"); assertThat(form.getExpectedVersion()).isEqualTo(4);
        assertThat(result.getGlobalErrors()).anySatisfy(error -> assertThat(error.getDefaultMessage()).contains("changed"));
        assertThat(model.getAttribute("selectedSeasonId")).isEqualTo(season);
    }

    @Test
    void givenBindingError_whenPosting_thenRenderFieldErrorsWithoutCallingWriteService() {
        // given
        var form = form(); var result = new BeanPropertyBindingResult(form, "form"); result.rejectValue("reason", "required");
        when(service.detail(race)).thenReturn(detail()); var model = new ConcurrentModel();
        // when / then
        assertThat(controller.decide(race, form, result, principal, model, new RedirectAttributesModelMap())).isEqualTo("admin/matchday-pick-review");
        assertThat(model.getAttribute("form")).isSameAs(form); verify(service, never()).decide(any(), any(), any());
    }

    @Test
    void givenNullExistingCorrectionForm_whenOpeningCorrection_thenPrepareFreshTypedForm() {
        // given
        var fresh = new PickCorrectionForm(); var model = new ExtendedModelMap(); model.addAttribute("form", null);
        when(service.correctionForm(race)).thenReturn(fresh); when(service.detail(race)).thenReturn(detail());
        // when / then
        assertThat(controller.correction(race, model)).isEqualTo("admin/matchday-pick-correction");
        assertThat(model.getAttribute("form")).isSameAs(fresh);
        verify(service).correctionChoices(race, fresh);
    }

    PickReviewForm form() {
        var form = new PickReviewForm(); form.setExpectedVersion(4L); form.setExpectedGeneration("00000000-0000-0000-0000-000000000000:0"); form.setAction(PickReviewForm.Action.RETURN); form.setReason("Test correction reason");
        form.setSettingsFingerprint("0".repeat(64)); return form;
    }
    Detail detail() {
        var row = new Row(race, season, day, "Test season | Test matchday | vs T-AWAY | Leg 1", "Test season", "Test matchday",
                PickState.SUBMITTED, 5, 2, null, "Test deadline", "Deadline passed", false, false, "Closed", true,
                List.of(PickReviewForm.Action.RETURN), false);
        return new Detail(row, "1".repeat(64), false, null, List.of(), List.of(), List.of(), "00000000-0000-0000-0000-000000000000:0",
                new Publication("Unpublished", null, List.of(), null, false));
    }
}
