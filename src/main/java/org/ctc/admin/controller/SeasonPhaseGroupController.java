package org.ctc.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.PhaseTeamForm;
import org.ctc.admin.dto.SeasonPhaseGroupForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thin controller for Group CRUD and bulk roster save under
 * {@code /admin/seasons/{seasonId}/phases/{phaseId}/groups}.
 *
 * <p>Implements D-19 (Group Step-1 form), D-20 (bulk roster diff via PhaseTeamForm),
 * D-24 (auto-increment sortIndex), D-28 (strict delete guard), and D-09 IDOR validation.
 *
 * <p>All business logic is delegated to {@link SeasonPhaseService}. Controllers are thin
 * per CLAUDE.md "Keep Controllers Thin".
 */
@Slf4j
@Controller
@RequestMapping("/admin/seasons/{seasonId}/phases/{phaseId}/groups")
@RequiredArgsConstructor
public class SeasonPhaseGroupController {

    private final SeasonPhaseService seasonPhaseService;
    private final SeasonManagementService seasonManagementService;

    /** GET /admin/seasons/{seasonId}/phases/{phaseId}/groups/new — D-19 Step 1 form. */
    @GetMapping("/new")
    public String create(@PathVariable UUID seasonId,
                         @PathVariable UUID phaseId,
                         Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);  // D-09 IDOR
        var form = new SeasonPhaseGroupForm();
        form.setPhaseId(phaseId);
        model.addAttribute("season", seasonManagementService.findById(seasonId));
        model.addAttribute("phase", phase);
        model.addAttribute("form", form);
        return "admin/season-phase-group-form";
    }

    /** GET /admin/seasons/{seasonId}/phases/{phaseId}/groups/{groupId}/edit. */
    @GetMapping("/{groupId}/edit")
    public String edit(@PathVariable UUID seasonId,
                       @PathVariable UUID phaseId,
                       @PathVariable UUID groupId,
                       Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);  // D-09 IDOR
        var group = phase.getGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup", groupId));

        var form = new SeasonPhaseGroupForm();
        form.setId(group.getId());
        form.setName(group.getName());
        form.setSortIndex(group.getSortIndex());
        form.setPhaseId(phaseId);

        model.addAttribute("season", seasonManagementService.findById(seasonId));
        model.addAttribute("phase", phase);
        model.addAttribute("form", form);
        return "admin/season-phase-group-form";
    }

    /** POST /admin/seasons/{seasonId}/phases/{phaseId}/groups/save — D-19 Step 1 save. */
    @PostMapping("/save")
    public String save(@PathVariable UUID seasonId,
                       @PathVariable UUID phaseId,
                       @Valid @ModelAttribute("form") SeasonPhaseGroupForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);  // D-09 IDOR

        if (result.hasErrors()) {
            model.addAttribute("season", seasonManagementService.findById(seasonId));
            model.addAttribute("phase", phase);
            return "admin/season-phase-group-form";
        }
        try {
            if (form.getId() != null) {
                seasonPhaseService.updateGroup(form.getId(), form.getName(), form.getSortIndex());
                redirectAttributes.addFlashAttribute("successMessage", "Group updated: " + form.getName());
            } else {
                // D-24: auto-increment sortIndex = max+1 when not specified
                Integer sortIndex = form.getSortIndex();
                if (sortIndex == null) {
                    sortIndex = phase.getGroups().stream()
                            .mapToInt(g -> g.getSortIndex() + 1)
                            .max()
                            .orElse(0);
                }
                var created = seasonPhaseService.createGroup(phaseId, form.getName(), sortIndex);
                redirectAttributes.addFlashAttribute("successMessage", "Group created: " + created.getName());
            }
            return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
        } catch (BusinessRuleException | IllegalArgumentException e) {
            log.warn("Cannot save group: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId
                    + "/groups" + (form.getId() != null ? "/" + form.getId() + "/edit" : "/new");
        }
    }

    /** POST /admin/seasons/{seasonId}/phases/{phaseId}/groups/{groupId}/delete — D-28 strict guard. */
    @PostMapping("/{groupId}/delete")
    public String delete(@PathVariable UUID seasonId,
                         @PathVariable UUID phaseId,
                         @PathVariable UUID groupId,
                         RedirectAttributes redirectAttributes) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);  // D-09 IDOR
        try {
            seasonPhaseService.deleteGroup(groupId);
            redirectAttributes.addFlashAttribute("successMessage", "Group deleted");
        } catch (BusinessRuleException e) {
            log.warn("Cannot delete group {}: {}", groupId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
    }

    /**
     * POST /admin/seasons/{seasonId}/phases/{phaseId}/groups/roster — D-20 bulk save.
     *
     * <p>Receives a {@link PhaseTeamForm} with indexed-property binding, converts each
     * {@link PhaseTeamForm.Assignment} to a {@link SeasonPhaseService.Assignment} record,
     * and delegates diff-logic to {@link SeasonPhaseService#assignTeamsToPhase}.
     *
     * <p>T-60-02-02 DoS guard: caps assignment list to 100 entries.
     */
    @PostMapping("/roster")
    public String saveRoster(@PathVariable UUID seasonId,
                             @PathVariable UUID phaseId,
                             @ModelAttribute("phaseTeamForm") PhaseTeamForm form,
                             RedirectAttributes redirectAttributes) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);  // D-09 IDOR

        // T-60-02-02 DoS guard: cap assignment list to 100 entries
        if (form.getAssignments() != null && form.getAssignments().size() > 100) {
            redirectAttributes.addFlashAttribute("errorMessage", "Too many roster entries (max 100)");
            return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
        }

        List<SeasonPhaseService.Assignment> assignments = form.getAssignments() == null ? List.of()
                : form.getAssignments().stream()
                        .filter(a -> a != null && a.getTeamId() != null)
                        .map(a -> new SeasonPhaseService.Assignment(a.getTeamId(), a.isIncluded(), a.getGroupId()))
                        .collect(Collectors.toList());
        try {
            seasonPhaseService.assignTeamsToPhase(phaseId, assignments);
            redirectAttributes.addFlashAttribute("successMessage", "Roster updated");
        } catch (BusinessRuleException | EntityNotFoundException e) {
            log.warn("Cannot update roster for phase {}: {}", phaseId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
    }

    /** D-09 IDOR ownership validation — throws EntityNotFoundException → 404 (not 403). */
    private void validateOwnership(UUID actualSeasonId, UUID expectedSeasonId) {
        if (!actualSeasonId.equals(expectedSeasonId)) {
            throw new EntityNotFoundException("Group not found for season", expectedSeasonId);
        }
    }
}
