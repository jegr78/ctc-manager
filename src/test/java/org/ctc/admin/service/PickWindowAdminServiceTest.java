package org.ctc.admin.service;

import java.util.UUID;
import org.ctc.admin.dto.MatchdayPairingsForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.service.PickWindowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PickWindowAdminServiceTest {
    @Mock PickWindowService windows;
    @Mock Environment environment;
    @InjectMocks PickWindowAdminService service;
    final UUID day = UUID.randomUUID();
    final String generation = "00000000-0000-0000-0000-000000000000:0";

    @Test
    void givenAuthenticatedManager_whenSaving_thenUseTheServerPrincipalAndExactFormTokens() {
        var form = form();
        service.saveMatchday(day, form, () -> "test-manager");
        verify(windows).saveMatchday(day, true, null, null, 4, "a".repeat(64), new ManagementPickActor("test-manager", generation));
        verifyNoInteractions(environment);
    }
    @Test
    void givenProductionWithoutPrincipal_whenSaving_thenRejectBeforeAnyWindowWrite() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        assertThatThrownBy(() -> service.saveMatchday(day, form(), null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("authenticated");
        verifyNoInteractions(windows);
    }
    @Test
    void givenDevelopmentWithoutPrincipal_whenSaving_thenUseTheExplicitDevelopmentActor() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        service.saveMatchday(day, form(), null);
        verify(windows).saveMatchday(day, true, null, null, 4, "a".repeat(64), new ManagementPickActor("development-manager", generation));
    }
    @Test
    void givenInvalidPrincipalOrWrongFormIdentity_whenSaving_thenDoNotResolveAnAlternativeActorOrWrite() {
        for (String name : new String[]{"", " ", "x".repeat(129)})
            assertThatThrownBy(() -> service.saveMatchday(day, form(), () -> name)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("authenticated");
        var form = form(); form.setId(UUID.randomUUID());
        assertThatThrownBy(() -> service.saveMatchday(day, form, () -> "test-manager")).hasMessageContaining("Reload");
        form.setId(day); form.setExpectedVersion(null);
        assertThatThrownBy(() -> service.saveMatchday(day, form, () -> "test-manager")).hasMessageContaining("Reload");
        verifyNoInteractions(windows, environment);
    }
    private MatchdayPairingsForm form() {
        var form = new MatchdayPairingsForm(); form.setId(day); form.setPicksOpen(true);
        form.setExpectedVersion(4L); form.setExpectedScope("a".repeat(64)); form.setExpectedGeneration(generation); return form;
    }
}
