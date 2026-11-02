package org.ctc.admin.service;

import java.util.UUID;
import org.ctc.admin.dto.PickReviewForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.service.PickReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchdayPickAdminServiceTest {
    @Mock PickReviewService reviews;
    @Mock Environment environment;
    @InjectMocks MatchdayPickAdminService service;
    final UUID race = UUID.randomUUID();

    @Test
    void givenProductionWithoutPrincipal_whenPostingDecision_thenRejectBeforeDomainMutation() {
        // given
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        // when / then
        assertThatThrownBy(() -> service.decide(race, form(), null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("authenticated");
        verifyNoInteractions(reviews);
    }

    @Test
    void givenAuthenticatedPrincipalAndExplicitApproval_whenPosting_thenUseServerIdentityAndExactVersionSnapshot() {
        // given
        var form = form(); form.setAction(PickReviewForm.Action.APPROVE); form.setRevisionNumber(2); form.setReplaceExisting(true);
        // when
        service.decide(race, form, () -> "test-manager");
        // then
        verify(reviews).approve(race, 3, 2, new ManagementPickActor("test-manager", 0L), "Test review reason", true, "0".repeat(64));
        verifyNoInteractions(environment);
    }

    @Test
    void givenOpenDevelopmentProfile_whenPostingWithoutPrincipal_thenRecordExplicitDevelopmentActor() {
        // given
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        // when
        service.decide(race, form(), null);
        // then
        verify(reviews).close(race, 3, new ManagementPickActor("development-manager", 0L), "Test review reason");
    }

    @Test
    void givenMissingOrMalformedVersionSnapshot_whenPosting_thenRejectBeforeResolvingActorOrWriting() {
        // given
        var form = form(); form.setSettingsFingerprint("forged");
        // when / then
        assertThatThrownBy(() -> service.decide(race, form, () -> "test-manager")).hasMessageContaining("Reload");
        form.setSettingsFingerprint("0".repeat(64)); form.setExpectedVersion(null);
        assertThatThrownBy(() -> service.decide(race, form, () -> "test-manager")).hasMessageContaining("Reload");
        form.setExpectedVersion(3L); form.setExpectedGeneration(null);
        assertThatThrownBy(() -> service.decide(race, form, () -> "test-manager")).hasMessageContaining("Reload");
        form.setExpectedGeneration(-1L);
        assertThatThrownBy(() -> service.decide(race, form, () -> "test-manager")).hasMessageContaining("Reload");
        verifyNoInteractions(reviews, environment);
    }

    @Test
    void givenBlankOrOverlongServerPrincipal_whenPosting_thenDenyWithoutInventingDevelopmentIdentity() {
        // given / when / then
        for (String name : new String[] {"", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> service.decide(race, form(), () -> name)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("authenticated");
        }
        verifyNoInteractions(reviews, environment);
    }

    PickReviewForm form() {
        var form = new PickReviewForm(); form.setExpectedVersion(3L); form.setExpectedGeneration(0L); form.setSettingsFingerprint("0".repeat(64));
        form.setAction(PickReviewForm.Action.CLOSE); form.setReason("Test review reason"); return form;
    }
}
