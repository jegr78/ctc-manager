package org.ctc.admin.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.service.SeasonWorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class SeasonWorkspaceController {
    private final SeasonWorkspaceService workspaceService;

    @GetMapping("/admin/workspace")
    public String switchSeason(@RequestParam UUID seasonId,
                               @RequestParam(defaultValue = "seasons") String section) {
        return "redirect:" + workspaceService.destination(seasonId, section);
    }
}
