package org.ctc.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.service.SeasonWorkspaceService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
public class SeasonWorkspaceInterceptor implements HandlerInterceptor {
    private final SeasonWorkspaceService workspaceService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null) return;
        var viewName = modelAndView.getViewName();
        if (viewName == null || !viewName.startsWith("admin/")) return;
        var workspace = workspaceService.build(modelAndView.getModel());
        if (workspace != null) modelAndView.addObject("seasonWorkspace", workspace);
    }
}
