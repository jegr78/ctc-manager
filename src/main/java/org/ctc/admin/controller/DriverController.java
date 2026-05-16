package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.DriverForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.service.DriverMergeService;
import org.ctc.domain.service.DriverService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;
    private final DriverMergeService driverMergeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("drivers", driverService.findAll());
        return "admin/drivers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        var driver = driverService.findById(id);
        model.addAttribute("driver", driver);
        model.addAttribute("pageTitle", "Driver: " + driver.getPsnId());
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

    @GetMapping("/{id}/merge")
    public String mergeForm(@PathVariable UUID id, Model model) {
        var source = driverService.findById(id);
        var allDrivers = driverService.getMergeFormDrivers(id);
        model.addAttribute("source", source);
        model.addAttribute("allDrivers", allDrivers);
        model.addAttribute("pageTitle", "Merge Driver: " + source.getPsnId());
        return "admin/driver-merge";
    }

    @PostMapping("/{id}/merge/preview")
    public String previewMerge(@PathVariable UUID id, @RequestParam UUID targetId,
                               RedirectAttributes redirectAttributes, Model model) {
        try {
            var source = driverService.findById(id);
            var target = driverService.findById(targetId);
            var preview = driverMergeService.previewMerge(id, targetId);
            model.addAttribute("source", source);
            model.addAttribute("target", target);
            model.addAttribute("preview", preview);
            model.addAttribute("pageTitle", "Merge Driver: " + source.getPsnId());
            return "admin/driver-merge";
        } catch (EntityNotFoundException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Merge failed: " + e.getMessage());
            return "redirect:/admin/drivers/" + id + "/merge";
        }
    }

    @PostMapping("/{id}/merge")
    public String executeMerge(@PathVariable UUID id, @RequestParam UUID targetId,
                               RedirectAttributes redirectAttributes) {
        try {
            var source = driverService.findById(id);
            var target = driverService.findById(targetId);
            var result = driverMergeService.merge(id, targetId);
            int total = result.seasonDrivers() + result.raceLineups() + result.raceResults() + result.aliasesReassigned();
            int dropped = result.seasonDriversDropped() + result.raceLineupsDropped() + result.raceResultsDropped();
            redirectAttributes.addFlashAttribute("successMessage",
                    "Driver merged: " + source.getPsnId() + " into " + target.getPsnId()
                    + " — " + total + " references reassigned, " + dropped + " duplicates resolved");
            return "redirect:/admin/drivers/" + targetId;
        } catch (EntityNotFoundException | BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Merge failed: " + e.getMessage());
            return "redirect:/admin/drivers";
        }
    }
}
