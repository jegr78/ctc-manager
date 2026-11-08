package org.ctc.gt7sync.environment;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.TrackEnvironmentImportForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackEnvironmentImportControllerTest {
    final TrackEnvironmentImportService service=mock(TrackEnvironmentImportService.class);
    final TrackEnvironmentImportController controller=new TrackEnvironmentImportController(service);
    final Principal actor=()->"test-manager";
    @Test void givenBundledAndLivePreviews_whenLoading_thenUseTrustedActorAndSharedNativeView() throws Exception {
        var preview=new TrackEnvironmentImportService.Preview(UUID.randomUUID(),"Test",GtPlanetEnvironmentSource.bundled(),List.of());
        when(service.bundled(actor)).thenReturn(preview);when(service.refresh(actor)).thenReturn(preview);
        var model=new ExtendedModelMap();var flash=new RedirectAttributesModelMap();
        assertThat(controller.preview(actor,model,flash)).isEqualTo("admin/track-environment-preview");
        assertThat(model.get("preview")).isSameAs(preview);assertThat(model.get("sourceUrl")).isEqualTo(GtPlanetEnvironmentSource.URL);
        assertThat(controller.refresh(actor,model,flash)).isEqualTo("admin/track-environment-preview");
    }
    @Test void givenUnavailableOrInvalidSource_whenLoading_thenExplainWithoutExposingRemotePayload() throws Exception {
        when(service.bundled(actor)).thenThrow(new IOException("private transport detail"));
        when(service.refresh(actor)).thenThrow(new IOException("remote payload"));
        for(boolean live:List.of(false,true)) {
            var flash=new RedirectAttributesModelMap();var model=new ExtendedModelMap();
            assertThat(live?controller.refresh(actor,model,flash):controller.preview(actor,model,flash)).isEqualTo("redirect:/admin/gt7-sync");
            assertThat(flash.getFlashAttributes().get("errorMessage").toString()).contains("Saved settings are unchanged").doesNotContain("private transport detail","remote payload");
        }
    }
    @Test void givenValidSelection_whenApplying_thenPassOnlyOfferedIdentifiersAndTrustedPrincipal() {
        var form=form();var errors=new BeanPropertyBindingResult(form,"selection");var flash=new RedirectAttributesModelMap();
        when(service.apply(form.getPreviewId(),form.getSelectedTracks(),actor)).thenReturn(1);
        assertThat(controller.apply(form,errors,actor,flash)).isEqualTo("redirect:/admin/gt7-sync/environment");
        assertThat(flash.getFlashAttributes().get("successMessage")).isEqualTo("1 layout environments imported");
        when(service.apply(form.getPreviewId(),form.getSelectedTracks(),actor)).thenThrow(new BusinessRuleException("Reload the preview"));
        controller.apply(form,errors,actor,flash);assertThat(flash.getFlashAttributes().get("errorMessage")).isEqualTo("Reload the preview");
    }
    @Test void givenForgedPayloadAndCsrf_whenBinding_thenRejectExtraFieldsAndNeverBindEnvironmentData() {
        var form=form();var binder=new WebDataBinder(form,"selection");controller.fields(binder);
        binder.bind(new MutablePropertyValues(java.util.Map.of("environmentSource","forged","actor","intruder","_csrf","valid-token")));
        assertThat(binder.getBindingResult().getSuppressedFields()).containsExactlyInAnyOrder("environmentSource","actor");
        var flash=new RedirectAttributesModelMap();controller.apply(form,binder.getBindingResult(),actor,flash);
        verifyNoInteractions(service);assertThat(flash.getFlashAttributes()).containsKey("errorMessage");
    }
    @Test void givenInvalidTypedSelection_whenApplying_thenRejectWithoutCallingWriter() {
        var form=form();var errors=new BeanPropertyBindingResult(form,"selection");errors.rejectValue("previewId","invalid");
        controller.apply(form,errors,actor,new RedirectAttributesModelMap());verifyNoInteractions(service);
    }
    @Test void givenRealBrowserHeaders_whenBindingSelection_thenIgnoreHeadersAndStillRejectForgedParameters() {
        var form=form();var binder=new org.springframework.web.servlet.support.ExtendedServletRequestDataBinder(form,"selection");controller.fields(binder);
        var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("Sec-Fetch-Site","same-origin");request.addHeader("Accept-Encoding","gzip");
        request.addHeader("previewId",UUID.randomUUID().toString());
        request.addParameter("previewId",form.getPreviewId().toString());request.addParameter("selectedTracks",form.getSelectedTracks().getFirst().toString());
        binder.bind(request);assertThat(binder.getBindingResult().getSuppressedFields()).isEmpty();assertThat(binder.getBindingResult().hasErrors()).isFalse();
        request.addParameter("environmentOverride","false");binder.bind(request);
        assertThat(binder.getBindingResult().getSuppressedFields()).contains("environmentOverride");
    }
    private TrackEnvironmentImportForm form() {
        var form=new TrackEnvironmentImportForm();form.setPreviewId(UUID.randomUUID());form.setSelectedTracks(List.of(UUID.randomUUID()));return form;
    }
}
