package org.ctc.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.ctc.admin.service.SeasonWorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.ModelAndView;

@ExtendWith(MockitoExtension.class)
class SeasonWorkspaceInterceptorTest {
    @Mock SeasonWorkspaceService service;
    @InjectMocks SeasonWorkspaceInterceptor interceptor;

    @Test
    void givenRedirectOrResponseBody_whenPostHandle_thenNoWorkspaceQueriesAreMade() {
        // when
        interceptor.postHandle(null, null, null, null);
        interceptor.postHandle(null, null, null, new ModelAndView());
        interceptor.postHandle(null, null, null, new ModelAndView("redirect:/admin/races"));
        interceptor.postHandle(null, null, null, new ModelAndView("site/index"));

        // then
        verifyNoInteractions(service);
    }

    @Test
    void givenAdminView_whenPostHandle_thenResolvedContextIsAddedToTheView() {
        // given
        var view = new ModelAndView("admin/races");
        var workspace = new SeasonWorkspaceService.Workspace(null, List.of(), "races",
                "/admin/seasons", "/admin/matchdays", "/admin/races", "/admin/playoffs", "/admin/standings", null);
        when(service.build(view.getModel())).thenReturn(workspace);

        // when
        interceptor.postHandle(null, null, null, view);

        // then
        assertThat(view.getModel()).containsEntry("seasonWorkspace", workspace);
    }
}
