package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.SeasonLobbyRulesForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.SeasonLobbyRulesService;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SeasonLobbyRulesController {
    private final SeasonLobbyRulesService rules;

    @PostMapping("/admin/seasons/{id}/lobby-rules")
    public String save(@PathVariable UUID id, @Valid @ModelAttribute("lobbyRulesForm") SeasonLobbyRulesForm form,
                       BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) binding.reject("lobby.rules", "Choose a supported rules profile and reload the season if its version is missing.");
        else {
            try {
                rules.changeProfile(id, form.getExpectedProfile(), form.getLobbyRulesProfile());
                redirect.addFlashAttribute("successMessage", "Season lobby rules saved.");
            } catch (BusinessRuleException failure) { binding.reject("lobby.rules", failure.getMessage()); }
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("lobbyRulesForm", form);
            redirect.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "lobbyRulesForm", binding);
            redirect.addFlashAttribute("errorMessage", binding.getGlobalErrors().getLast().getDefaultMessage());
        }
        return "redirect:/admin/seasons/" + id + "/edit#season-lobby-rules";
    }
}
