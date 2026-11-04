package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.model.IncidentDeliveryState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@AutoConfigureMockMvc
class IncidentDeliveryManagementIT extends IncidentDeliveryDispatchFixture {
    @Autowired MockMvc mvc;
    UUID uncertain() {var id=receipt();tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();var token=UUID.randomUUID();row.claim(token,epoch.managementGeneration(),NOW,NOW.plusSeconds(120));row.fail(token,1,epoch.managementGeneration(),NOW,"SEND_UNCERTAIN",true,NOW.plusSeconds(60));});return id;}
    @Test void givenSavedReportAndUnknownPublicSend_whenOpeningManagement_thenShowSeparateStatusAndNativeReview() throws Exception {
        var id=uncertain();
        mvc.perform(get("/admin/incident-contexts/"+contextId+"/deliveries")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Outcome unknown"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Saved reports remain stored")));
        mvc.perform(get("/admin/incident-deliveries/"+id)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Existing receipt message ID"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm your external review")));
    }
    @Test void givenMissingReviewFields_whenSubmitting_thenKeepUnknownStateAndShowErrorsBesideNativeInputs() throws Exception {
        var id=uncertain();mvc.perform(post("/admin/incident-deliveries/"+id).param("reason","Test entered reason")).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("deliveryReviewForm","expectedVersion","generation","reviewed","decision")).andExpect(content().string(org.hamcrest.Matchers.containsString("Test entered reason")));
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);
    }
    @Test
    @org.springframework.security.test.context.support.WithMockUser("test-authenticated-manager")
    void givenReviewedMissingReceipt_whenPostingForgedActorAndState_thenUseAuthenticatedActorAndOnlyAllowTheReviewedTransition() throws Exception {
        var id=uncertain();mvc.perform(post("/admin/incident-deliveries/"+id).param("expectedVersion","2").param("generation",epoch.managementGeneration()).param("reviewed","true").param("reason","Test checked missing receipt").param("decision","RETRY_MISSING").param("actor","forged-manager").param("state","SENT"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/incident-contexts/"+contextId+"/deliveries"));
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);tx.executeWithoutResult(status -> assertThat(deliveryRows.findById(id).orElseThrow().getReconciledBy()).isEqualTo("test-authenticated-manager"));
    }
    @Test void givenUnknownRestoredWork_whenResumingWithoutIndividualResolution_thenKeepPauseAndShowReason() throws Exception {
        uncertain();tx.executeWithoutResult(status -> org.springframework.test.util.ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));
        mvc.perform(post("/admin/incident-contexts/"+contextId+"/deliveries/resume").param("expectedVersion","1").param("generation",epoch.managementGeneration()).param("reviewed","true").param("reason","Test checked restored scope"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Review every unresolved restored delivery")));
        tx.executeWithoutResult(status -> assertThat(windowRows.findById(windowId).orElseThrow().isDeliveryPaused()).isTrue());
    }
}
