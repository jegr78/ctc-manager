package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PrivatePickBindingForm;
import org.ctc.admin.service.PrivatePicksAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/discord-config/picks")
@RequiredArgsConstructor
public class PrivatePicksController {
    private static final String VIEW = "admin/private-picks";
    private static final String REDIRECT = "redirect:/admin/discord-config/picks";
    private final PrivatePicksAdminService service;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("setup", service.overview());
        if (!model.containsAttribute("form")) model.addAttribute("form", new PrivatePickBindingForm());
        return VIEW;
    }
    @PostMapping("/bindings/save")
    public String save(@Valid @ModelAttribute("form") PrivatePickBindingForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return view(model);
        try {
            service.save(form);
            redirect.addFlashAttribute("successMessage", "Private team channel binding saved.");
            return REDIRECT;
        } catch (BusinessRuleException failure) {
            result.reject("binding", failure.getMessage());
            return view(model);
        }
    }
    @PostMapping("/bindings/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("successMessage", "Private team channel binding removed.");
        } catch (BusinessRuleException failure) {
            redirect.addFlashAttribute("errorMessage", failure.getMessage());
        }
        return REDIRECT;
    }
    @PostMapping("/register-command")
    public String register(RedirectAttributes redirect) {
        try {
            service.registerCommand();
            redirect.addFlashAttribute("successMessage", "Private picks command registered for the configured server.");
        } catch (BusinessRuleException failure) {
            redirect.addFlashAttribute("errorMessage", failure.getMessage());
        }
        return REDIRECT;
    }
}
