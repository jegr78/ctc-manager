package de.ctc.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminRedirectController {

    @GetMapping({"/", "/admin"})
    public String redirectToSeasons() {
        return "redirect:/admin/seasons";
    }
}
