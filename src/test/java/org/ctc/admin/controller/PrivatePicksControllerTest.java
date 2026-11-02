package org.ctc.admin.controller;

import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.PrivatePickBindingForm;
import org.ctc.admin.service.PrivatePicksAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivatePicksControllerTest {
    final PrivatePicksAdminService service = mock(PrivatePicksAdminService.class);
    final PrivatePicksController controller = new PrivatePicksController(service);

    @Test
    void givenSetupPage_whenLoading_thenProvideHealthAndNativeBindingFormWithoutRegistration() {
        // given
        var model = new ConcurrentModel();
        var overview = new PrivatePicksAdminService.Overview("100", "Disabled", false, List.of(), List.of(), List.of());
        when(service.overview()).thenReturn(overview);
        // when / then
        assertThat(controller.view(model)).isEqualTo("admin/private-picks");
        assertThat(model.getAttribute("setup")).isSameAs(overview);
        assertThat(model.getAttribute("form")).isInstanceOf(PrivatePickBindingForm.class);
        verify(service, never()).registerCommand();
    }

    @Test
    void givenValidBinding_whenSaving_thenDelegateAndRedirectWithSuccess() {
        // given
        var form = form(); var result = new BeanPropertyBindingResult(form, "form");
        var redirect = new RedirectAttributesModelMap();
        // when / then
        assertThat(controller.save(form, result, new ConcurrentModel(), redirect)).isEqualTo("redirect:/admin/discord-config/picks");
        verify(service).save(form);
        assertThat(redirect.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void givenValidationError_whenSaving_thenRetainInputAndShowFieldErrorWithoutServiceWrite() {
        // given
        var form = form(); form.setChannelId("invalid");
        var result = new BeanPropertyBindingResult(form, "form"); result.rejectValue("channelId", "invalid");
        var model = new ConcurrentModel(); model.addAttribute("form", form);
        // when / then
        assertThat(controller.save(form, result, model, new RedirectAttributesModelMap())).isEqualTo("admin/private-picks");
        assertThat(model.getAttribute("form")).isSameAs(form);
        assertThat(form.getChannelId()).isEqualTo("invalid");
        verify(service, never()).save(any());
    }

    @Test
    void givenDomainRefusal_whenSaving_thenKeepFormAndSurfaceBusinessError() {
        // given
        var form = form(); var result = new BeanPropertyBindingResult(form, "form");
        var model = new ConcurrentModel(); model.addAttribute("form", form);
        doThrow(new BusinessRuleException("This channel is already bound.")).when(service).save(form);
        // when / then
        assertThat(controller.save(form, result, model, new RedirectAttributesModelMap())).isEqualTo("admin/private-picks");
        assertThat(result.getGlobalErrors()).anySatisfy(error -> assertThat(error.getDefaultMessage()).contains("already bound"));
        assertThat(model.getAttribute("form")).isSameAs(form);
    }

    @Test
    void givenExistingBinding_whenRemoving_thenDelegateExactIdAndConfirm() {
        // given
        var id = UUID.randomUUID(); var redirect = new RedirectAttributesModelMap();
        // when / then
        assertThat(controller.delete(id, redirect)).isEqualTo("redirect:/admin/discord-config/picks");
        verify(service).delete(id);
        assertThat(redirect.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void givenChangedBinding_whenRemoving_thenReturnReloadableError() {
        // given
        var id = UUID.randomUUID(); var redirect = new RedirectAttributesModelMap();
        doThrow(new BusinessRuleException("This binding changed. Reload before removing it.")).when(service).delete(id);
        // when / then
        assertThat(controller.delete(id, redirect)).isEqualTo("redirect:/admin/discord-config/picks");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("This binding changed. Reload before removing it.");
    }

    @Test
    void givenExplicitCommandRegistration_whenRequested_thenDelegateOnceAndConfirm() {
        // given
        var redirect = new RedirectAttributesModelMap();
        // when / then
        assertThat(controller.register(redirect)).isEqualTo("redirect:/admin/discord-config/picks");
        verify(service).registerCommand();
        assertThat(redirect.getFlashAttributes()).containsKey("successMessage");
    }

    @Test
    void givenUnavailableRegistration_whenRequested_thenShowGenericBusinessError() {
        // given
        var redirect = new RedirectAttributesModelMap();
        doThrow(new BusinessRuleException("Command registration is unavailable.")).when(service).registerCommand();
        // when / then
        assertThat(controller.register(redirect)).isEqualTo("redirect:/admin/discord-config/picks");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Command registration is unavailable.");
    }

    PrivatePickBindingForm form() {
        var form = new PrivatePickBindingForm(); form.setSeasonId(UUID.randomUUID());
        form.setTeamId(UUID.randomUUID()); form.setChannelId("200"); return form;
    }
}
