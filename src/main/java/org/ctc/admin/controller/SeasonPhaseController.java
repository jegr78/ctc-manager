package org.ctc.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.SeasonPhaseForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.service.MatchdayService;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Thin controller for Phase CRUD under {@code /admin/seasons/{seasonId}/phases}.
 *
 * <p>Routes phase form pages, enforces IDOR ownership validation, prefills the create-form
 * defaults from the REGULAR phase, surfaces strict-guard violations as flash redirects, and
 * auto-creates PLAYOFF phases via {@link PlayoffService} when the user submits a PLAYOFF
 * type.
 *
 * <p>All business logic is delegated to {@link SeasonPhaseService} per CLAUDE.md
 * "Keep Controllers Thin".
 */
@Slf4j
@Controller
@RequestMapping("/admin/seasons/{seasonId}/phases")
@RequiredArgsConstructor
public class SeasonPhaseController {

    private final SeasonPhaseService seasonPhaseService;
    private final SeasonManagementService seasonManagementService;
    private final MatchdayService matchdayService;
    private final StandingsService standingsService;
    private final PhaseTeamRepository phaseTeamRepository;
    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final PlayoffService playoffService;

    /** GET /admin/seasons/{seasonId}/phases/{phaseId} — Phase-Tab view. */
    @GetMapping("/{phaseId}")
    public String detail(@PathVariable UUID seasonId,
                         @PathVariable UUID phaseId,
                         Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);
        var season = seasonManagementService.findById(seasonId);
        var allPhases = seasonPhaseService.findAllPhases(seasonId);
        var rosterState = seasonPhaseService.getRosterEditorState(phaseId);

        model.addAttribute("season", season);
        model.addAttribute("phase", phase);
        model.addAttribute("allPhases", allPhases);
        model.addAttribute("groups", phase.getGroups());
        model.addAttribute("phaseTeams", phaseTeamRepository.findByPhaseId(phaseId));
        model.addAttribute("matchdays", matchdayService.findByPhaseId(phaseId));
        model.addAttribute("selectedGroupId", null);
        model.addAttribute("effectivePhaseLabel", effectiveLabel(phase));
        model.addAttribute("hasRegularPhase", true);

        model.addAttribute("assignedTeamIds", rosterState.assignedTeamIds());
        model.addAttribute("currentGroupByTeamId", rosterState.currentGroupByTeamId());

        boolean isGroupsLayout = phase.getLayout() == PhaseLayout.GROUPS;
        boolean isPlayoffLayout = phase.getPhaseType() == PhaseType.PLAYOFF;
        model.addAttribute("isGroupsLayout", isGroupsLayout);
        model.addAttribute("isPlayoffLayout", isPlayoffLayout);

        // Resolve the playoff entity so the template can read seeds without extra queries.
        if (isPlayoffLayout) {
            playoffService.findByPhaseId(phaseId).ifPresent(p -> {
                model.addAttribute("playoff", p);
                model.addAttribute("playoffSeeds", p.getSeeds());
            });
        }

        // Flags driving conditional column rendering on the shared standings template.
        model.addAttribute("combinedView", isGroupsLayout);
        model.addAttribute("showGroupColumn", isGroupsLayout);

        return "admin/season-detail";
    }

    /** GET /admin/seasons/{seasonId}/phases/{phaseId}/groups/{groupId} — Group sub-tab. */
    @GetMapping("/{phaseId}/groups/{groupId}")
    public String groupDetail(@PathVariable UUID seasonId,
                              @PathVariable UUID phaseId,
                              @PathVariable UUID groupId,
                              Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);
        var season = seasonManagementService.findById(seasonId);
        var rosterState = seasonPhaseService.getRosterEditorState(phaseId);

        model.addAttribute("season", season);
        model.addAttribute("phase", phase);
        model.addAttribute("allPhases", seasonPhaseService.findAllPhases(seasonId));
        model.addAttribute("groups", phase.getGroups());
        model.addAttribute("phaseTeams", phaseTeamRepository.findByPhaseIdAndGroupId(phaseId, groupId));
        model.addAttribute("matchdays", matchdayService.findByPhaseIdAndGroupId(phaseId, groupId));
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("effectivePhaseLabel", effectiveLabel(phase));
        model.addAttribute("hasRegularPhase", true);

        model.addAttribute("assignedTeamIds", rosterState.assignedTeamIds());
        model.addAttribute("currentGroupByTeamId", rosterState.currentGroupByTeamId());

        boolean isGroupsLayout = phase.getLayout() == PhaseLayout.GROUPS;
        boolean isPlayoffLayout = phase.getPhaseType() == PhaseType.PLAYOFF;
        model.addAttribute("isGroupsLayout", isGroupsLayout);
        model.addAttribute("isPlayoffLayout", isPlayoffLayout);

        if (isPlayoffLayout) {
            playoffService.findByPhaseId(phaseId).ifPresent(p -> {
                model.addAttribute("playoff", p);
                model.addAttribute("playoffSeeds", p.getSeeds());
            });
        }

        model.addAttribute("combinedView", false);
        model.addAttribute("showGroupColumn", false);

        return "admin/season-detail";
    }

    /** GET /admin/seasons/{seasonId}/phases/new — form with REGULAR-phase prefill. */
    @GetMapping("/new")
    public String create(@PathVariable UUID seasonId,
                         @RequestParam(required = false) PhaseType phaseType,
                         Model model) {
        var season = seasonManagementService.findById(seasonId);
        var form = new SeasonPhaseForm();
        if (phaseType != null) form.setPhaseType(phaseType);

        // Defaults: copy non-identity fields from REGULAR phase
        seasonPhaseService.findByType(seasonId, PhaseType.REGULAR).ifPresent(regular -> {
            if (form.getFormat() == SeasonFormat.LEAGUE) form.setFormat(regular.getFormat());
            if (regular.getRaceScoring() != null) form.setRaceScoringId(regular.getRaceScoring().getId());
            if (regular.getMatchScoring() != null) form.setMatchScoringId(regular.getMatchScoring().getId());
            form.setLegs(regular.getLegs());
            form.setEventDurationMinutes(regular.getEventDurationMinutes());
            // dates and totalRounds intentionally NOT copied
        });

        addFormModelAttributes(model, season, form);
        return "admin/season-phase-form";
    }

    /** GET /admin/seasons/{seasonId}/phases/{phaseId}/edit. */
    @GetMapping("/{phaseId}/edit")
    public String edit(@PathVariable UUID seasonId,
                       @PathVariable UUID phaseId,
                       Model model) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);
        var season = seasonManagementService.findById(seasonId);

        var form = new SeasonPhaseForm();
        form.setId(phase.getId());
        form.setPhaseType(phase.getPhaseType());
        form.setLayout(phase.getLayout());
        form.setFormat(phase.getFormat() != null ? phase.getFormat() : SeasonFormat.LEAGUE);
        if (phase.getRaceScoring() != null) form.setRaceScoringId(phase.getRaceScoring().getId());
        if (phase.getMatchScoring() != null) form.setMatchScoringId(phase.getMatchScoring().getId());
        form.setStartDate(phase.getStartDate());
        form.setEndDate(phase.getEndDate());
        form.setTotalRounds(phase.getTotalRounds());
        form.setLegs(phase.getLegs());
        form.setEventDurationMinutes(phase.getEventDurationMinutes());
        form.setLabel(phase.getLabel());
        form.setSortIndex(phase.getSortIndex());

        addFormModelAttributes(model, season, form);
        return "admin/season-phase-form";
    }

    /**
     * POST /admin/seasons/{seasonId}/phases/save.
     *
     * <p>Creates or updates a phase. PLAYOFF creation is auto-routed through
     * {@link PlayoffService#createPlayoff} which atomically creates the SeasonPhase plus
     * the playoff bracket. REGULAR/PLACEMENT creation goes through
     * {@link SeasonPhaseService#create} with auto-derived sortIndex.
     *
     * <p>{@code phaseType} is immutable post-create; the update call ignores it.
     */
    @PostMapping("/save")
    public String save(@PathVariable UUID seasonId,
                       @Valid @ModelAttribute("seasonPhaseForm") SeasonPhaseForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes,
                       Model model) {
        if (result.hasErrors()) {
            var season = seasonManagementService.findById(seasonId);
            addFormModelAttributes(model, season, form);
            return "admin/season-phase-form";
        }

        try {
            if (form.getId() != null) {
                // UPDATE — phaseType is immutable post-create.
                seasonPhaseService.update(form.getId(), form.getLayout(), form.getFormat(),
                        form.getRaceScoringId(), form.getMatchScoringId(),
                        form.getStartDate(), form.getEndDate(),
                        form.getTotalRounds(), form.getLegs(), form.getEventDurationMinutes(),
                        form.getLabel(), form.getSortIndex());
                redirectAttributes.addFlashAttribute("successMessage", "Phase updated");
                return "redirect:/admin/seasons/" + seasonId + "/phases/" + form.getId();
            }

            // CREATE — PLAYOFF auto-routes via PlayoffService (which auto-creates the SeasonPhase).
            if (form.getPhaseType() == PhaseType.PLAYOFF) {
                var playoffName = form.getLabel() != null && !form.getLabel().isBlank()
                        ? form.getLabel() : "Playoff";
                var playoff = playoffService.createPlayoff(seasonId, playoffName,
                        /* numberOfTeams */ 4,
                        form.getStartDate(), form.getEndDate(),
                        form.getEventDurationMinutes());
                redirectAttributes.addFlashAttribute("successMessage", "Playoff phase created: " + playoff.getName());
                return "redirect:/admin/seasons/" + seasonId;
            }

            // Standard CREATE for REGULAR / PLACEMENT.
            int sortIndex = form.getSortIndex() != null ? form.getSortIndex() : autoSortIndex(form.getPhaseType());
            // Resolve scoring entities (nullable in DTO; service allows null for bootstrap phases)
            var raceScoring = form.getRaceScoringId() != null
                    ? raceScoringRepository.findById(form.getRaceScoringId())
                            .orElseThrow(() -> new EntityNotFoundException("RaceScoring", form.getRaceScoringId()))
                    : null;
            var matchScoring = form.getMatchScoringId() != null
                    ? matchScoringRepository.findById(form.getMatchScoringId())
                            .orElseThrow(() -> new EntityNotFoundException("MatchScoring", form.getMatchScoringId()))
                    : null;
            var phase = seasonPhaseService.create(seasonId, form.getPhaseType(), form.getLayout(),
                    sortIndex, form.getLabel(), raceScoring, matchScoring,
                    form.getFormat(), form.getStartDate(), form.getEndDate(),
                    form.getTotalRounds(), form.getLegs(), form.getEventDurationMinutes());
            redirectAttributes.addFlashAttribute("successMessage", "Phase created");
            return "redirect:/admin/seasons/" + seasonId + "/phases/" + phase.getId();

        } catch (BusinessRuleException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Cannot save phase: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/seasons/" + seasonId
                    + (form.getId() != null ? "/phases/" + form.getId() + "/edit" : "/phases/new");
        }
    }

    /** POST /admin/seasons/{seasonId}/phases/{phaseId}/delete. */
    @PostMapping("/{phaseId}/delete")
    public String delete(@PathVariable UUID seasonId,
                         @PathVariable UUID phaseId,
                         RedirectAttributes redirectAttributes) {
        var phase = seasonPhaseService.findById(phaseId);
        validateOwnership(phase.getSeason().getId(), seasonId);
        try {
            seasonPhaseService.delete(phaseId);
            redirectAttributes.addFlashAttribute("successMessage", "Phase deleted");
            return "redirect:/admin/seasons/" + seasonId;
        } catch (BusinessRuleException e) {
            log.warn("Cannot delete phase {}: {}", phaseId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/seasons/" + seasonId + "/phases/" + phaseId;
        }
    }

    // === helpers ===

    /** IDOR ownership validation — throws EntityNotFoundException → 404 (not 403). */
    private void validateOwnership(UUID actualSeasonId, UUID expectedSeasonId) {
        if (!actualSeasonId.equals(expectedSeasonId)) {
            throw new EntityNotFoundException("Phase not found for season", expectedSeasonId);
        }
    }

    /** Effective label fallback: label field → phaseType default name. */
    private static String effectiveLabel(org.ctc.domain.model.SeasonPhase phase) {
        if (phase.getLabel() != null && !phase.getLabel().isBlank()) return phase.getLabel();
        return switch (phase.getPhaseType()) {
            case REGULAR -> "Regular Season";
            case PLAYOFF -> "Playoff";
            case PLACEMENT -> "Placement";
        };
    }

    /** Fixed sort indices: REGULAR=0, PLAYOFF=10, PLACEMENT=20. */
    private static int autoSortIndex(PhaseType type) {
        return switch (type) {
            case REGULAR -> 0;
            case PLAYOFF -> 10;
            case PLACEMENT -> 20;
        };
    }

    /**
     * Populates model attributes shared by create and edit form views. Provides enum lists
     * and display-label maps so the templates avoid SpEL string comparisons. The
     * {@code phaseTypeReadonly} flag is set when editing because {@code phaseType} is
     * immutable post-create.
     */
    private void addFormModelAttributes(Model model, Object season, SeasonPhaseForm form) {
        model.addAttribute("season", season);
        model.addAttribute("seasonPhaseForm", form);
        model.addAttribute("raceScorings", raceScoringRepository.findAll());
        model.addAttribute("matchScorings", matchScoringRepository.findAll());

        model.addAttribute("phaseTypes", PhaseType.values());
        model.addAttribute("phaseLayouts", PhaseLayout.values());
        model.addAttribute("seasonFormats", SeasonFormat.values());
        model.addAttribute("phaseTypeLabels", Map.of(
                PhaseType.REGULAR, "Regular Season",
                PhaseType.PLAYOFF, "Playoff",
                PhaseType.PLACEMENT, "Placement"));
        model.addAttribute("layoutLabels", Map.of(
                PhaseLayout.LEAGUE, "League",
                PhaseLayout.GROUPS, "Groups",
                PhaseLayout.BRACKET, "Bracket"));
        model.addAttribute("formatLabels", Map.of(
                SeasonFormat.LEAGUE, "League",
                SeasonFormat.SWISS, "Swiss",
                SeasonFormat.ROUND_ROBIN, "Round Robin"));

        model.addAttribute("phaseTypeReadonly", form.getId() != null);
    }
}
