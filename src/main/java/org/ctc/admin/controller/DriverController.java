package org.ctc.admin.controller;

import org.ctc.admin.dto.DriverForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("drivers", driverService.findAll());
        return "admin/drivers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var driver = driverService.findById(id);
        model.addAttribute("driver", driver);
        return "admin/driver-detail";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("driverForm", new DriverForm());
        return "admin/driver-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var editData = driverService.getEditFormData(id);
        var driver = editData.driver();
        var form = new DriverForm();
        form.setId(driver.getId());
        form.setPsnId(driver.getPsnId());
        form.setNickname(driver.getNickname());
        form.setActive(driver.isActive());
        form.setAliases(driver.getAliases().stream().map(PsnAlias::getAlias).toList());
        model.addAttribute("driverForm", form);
        model.addAttribute("seasonDrivers", editData.seasonDrivers());
        model.addAttribute("seasons", editData.allSeasons());
        model.addAttribute("teams", editData.allTeams());
        return "admin/driver-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("driverForm") DriverForm driverForm, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/driver-form";
        }

        var aliasErrors = driverService.validateAliases(driverForm.getId(), driverForm.getAliases());
        if (!aliasErrors.isEmpty()) {
            aliasErrors.forEach(error -> result.rejectValue("aliases", "alias.conflict", error));
            return "admin/driver-form";
        }

        driverService.save(driverForm.getId(), driverForm.getPsnId(), driverForm.getNickname(),
                driverForm.isActive(), driverForm.getAliases());
        log.info("Saved driver: {}", driverForm.getPsnId());
        redirectAttributes.addFlashAttribute("successMessage", "Driver saved: " + driverForm.getPsnId());
        return "redirect:/admin/drivers";
    }

    @PostMapping("/{id}/assign")
    public String assignToSeason(@PathVariable UUID id,
                                 @RequestParam UUID seasonId,
                                 @RequestParam UUID teamId,
                                 RedirectAttributes redirectAttributes) {
        var message = driverService.assignToSeason(id, seasonId, teamId);
        redirectAttributes.addFlashAttribute("successMessage", message);
        return "redirect:/admin/drivers/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            driverService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Driver deleted");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/drivers";
    }
}
