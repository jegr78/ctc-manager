package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.PairingPickWindowForm;
import org.ctc.admin.service.PickWindowAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/pick-windows")
public class PairingPickWindowController {
    private final PickWindowAdminService windows;

    @GetMapping("/matches/{id}")
    public String match(@PathVariable UUID id, Model model) { return form(id, false, model); }
    @GetMapping("/playoffs/{id}")
    public String playoff(@PathVariable UUID id, Model model) { return form(id, true, model); }
    @PostMapping("/matches/{id}")
    public String saveMatch(@PathVariable UUID id, @Valid @ModelAttribute("form") PairingPickWindowForm form, BindingResult errors,
                            Principal principal, Model model, RedirectAttributes flash) {
        return save(id, false, form, errors, principal, model, flash);
    }
    @PostMapping("/playoffs/{id}")
    public String savePlayoff(@PathVariable UUID id, @Valid @ModelAttribute("form") PairingPickWindowForm form, BindingResult errors,
                              Principal principal, Model model, RedirectAttributes flash) {
        return save(id, true, form, errors, principal, model, flash);
    }
    private String form(UUID id, boolean playoff, Model model) {
        var view = windows.pairing(id, playoff);
        model.addAttribute("form", windows.pairingForm(view));
        model.addAttribute("window", view);
        return "admin/pairing-pick-window-form";
    }
    private String save(UUID id, boolean playoff, PairingPickWindowForm form, BindingResult errors, Principal principal, Model model, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { windows.savePairing(id, playoff, form, principal); }
            catch (BusinessRuleException rejected) { errors.reject("pickWindow", rejected.getMessage()); }
        }
        if (errors.hasErrors()) {
            model.addAttribute("window", windows.pairing(id, playoff));
            return "admin/pairing-pick-window-form";
        }
        flash.addFlashAttribute("successMessage", "Match picks deadline saved for both legs.");
        return playoff ? "redirect:/admin/playoffs/matchup/" + id : "redirect:/admin/matches/" + id;
    }
}
