package org.ctc.admin.controller;

import static org.springframework.util.StringUtils.hasText;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGeneratorForm;
import org.ctc.admin.dto.PostStandingsForm;
import org.ctc.admin.dto.SeasonForm;
import org.ctc.admin.dto.SeasonPoolEntryForm;
import org.ctc.admin.dto.SeasonPoolModeForm;
import org.ctc.admin.service.DiscordSeasonViewService;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.domain.service.MatchdayGeneratorService;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.SwissPairingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/seasons")
@RequiredArgsConstructor
public class SeasonController {

	private static final String THREAD_TYPE_RACE_RESULTS = "race-results";
	private static final String THREAD_TYPE_STANDINGS = "standings";

	private final SeasonManagementService seasonManagementService;
	private final SeasonPhaseService seasonPhaseService;
	private final SwissPairingService swissPairingService;
	private final MatchdayGeneratorService matchdayGeneratorService;
	private final DiscordPostService discordPostService;
	private final DiscordSeasonViewService discordSeasonViewService;
    private final SeasonCarPoolService carPoolService;
    private final jakarta.validation.Validator validator;

	@GetMapping("/{id}")
	public String detail(@PathVariable UUID id, Model model) {
		var season = seasonManagementService.findById(id);
		var regular = seasonPhaseService.findByType(id, PhaseType.REGULAR);

		if (regular.isEmpty()) {
			// Render Empty-State card instead of redirecting when no REGULAR phase exists.
			var allPhases = seasonPhaseService.findAllPhases(id);
			model.addAttribute("season", season);
			model.addAttribute("phase", null);
			model.addAttribute("allPhases", allPhases);
			model.addAttribute("groups", List.of());
			model.addAttribute("phaseTeams", List.of());
			model.addAttribute("matchdays", List.of());
			model.addAttribute("selectedGroupId", null);
			model.addAttribute("hasRegularPhase", false);
			model.addAttribute("effectivePhaseLabel", "");
			model.addAttribute("combinedView", false);
			model.addAttribute("showGroupColumn", false);
			model.addAttribute("availableTeams", seasonManagementService.getAvailableTeamsForReplacement(id));
			model.addAttribute("pageTitle", "Season: " + season.getName());
			return "admin/season-detail";
		}
		// Auto-redirect to REGULAR phase tab
		return "redirect:/admin/seasons/" + id + "/phases/" + regular.get().getId();
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("seasons", seasonManagementService.findAll());
		return "admin/seasons";
	}

	@GetMapping("/new")
	public String create(Model model) {
		model.addAttribute("pageTitle", "New Season");
		model.addAttribute("seasonForm", new SeasonForm());
		return "admin/season-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		model.addAttribute("pageTitle", "Edit Season");
		var data = seasonManagementService.getEditFormData(id);
		var season = data.season();
		var form = new SeasonForm();
		form.setId(season.getId());
		form.setName(season.getName());
		form.setYear(season.getYear());
		form.setNumber(season.getNumber());
		form.setDescription(season.getDescription());
		form.setActive(season.isActive());
        form.setCarPoolMode(season.getCarPoolMode());
		model.addAttribute("seasonForm", form);
		model.addAttribute("season", season);
		model.addAttribute("allTeams", data.allTeams());
		model.addAttribute("allCars", data.allCars());
		model.addAttribute("allTracks", data.allTracks());
		model.addAllAttributes(discordSeasonViewService.buildDiscordIntegrationModel(season.getId()));
        preparePoolModels(season.getId(), model);
		return "admin/season-form";
	}

	@PostMapping("/{id}/post-standings")
	public String postStandings(@PathVariable UUID id,
	                            @Valid @ModelAttribute PostStandingsForm form,
	                            BindingResult bindingResult,
	                            RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Phase is required.");
			redirectAttributes.addFlashAttribute("errorCategory", "data-incomplete");
			return "redirect:/admin/seasons/" + id + "/edit";
		}
		try {
			var season = seasonManagementService.findById(id);
			var phase = seasonPhaseService.findById(form.getPhaseId());
			discordPostService.postStandings(season, phase);
			redirectAttributes.addFlashAttribute("successMessage", "Standings posted.");
		} catch (BusinessRuleException e) {
			applyStandingsErrorFlash(redirectAttributes, e, "Post standings");
		} catch (DiscordApiException e) {
			applyStandingsErrorFlash(redirectAttributes, e, "Post standings");
		}
		return "redirect:/admin/seasons/" + id + "/edit";
	}

	private void applyStandingsErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
		String message = switch (e.category()) {
			case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
			case DISABLED -> DiscordApiExceptionMapper.DISABLED_MESSAGE;
			case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
			case MISSING_PERMISSIONS -> DiscordApiExceptionMapper.MISSING_PERMISSIONS_MESSAGE;
			case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
			case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
		};
		String category = e.category().name().toLowerCase().replace('_', '-');
		log.warn("{} failed: category={}, exception={}", action, category, e.getClass().getSimpleName());
		ra.addFlashAttribute("errorMessage", message);
		ra.addFlashAttribute("errorCategory", category);
	}

	private void applyStandingsErrorFlash(RedirectAttributes ra, BusinessRuleException e, String action) {
		log.warn("{} failed: category=data-incomplete, message={}", action, e.getMessage());
		ra.addFlashAttribute("errorMessage", e.getMessage());
		ra.addFlashAttribute("errorCategory", "data-incomplete");
	}

	@PostMapping("/{id}/link-thread")
	public String linkThread(@PathVariable UUID id,
	                         @RequestParam String threadId,
	                         @RequestParam String type,
	                         RedirectAttributes redirectAttributes) {
		try {
			if (!hasText(threadId)) {
				throw new BusinessRuleException("Thread ID must not be empty.");
			}
			switch (type) {
				case THREAD_TYPE_RACE_RESULTS -> seasonManagementService.linkRaceResultsThread(id, threadId);
				case THREAD_TYPE_STANDINGS -> seasonManagementService.linkStandingsThread(id, threadId);
				default -> throw new BusinessRuleException("Unknown thread type: " + type);
			}
			redirectAttributes.addFlashAttribute("successMessage", "Thread linked.");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/seasons/" + id + "/edit";
	}

	@PostMapping("/{id}/unlink-thread")
	public String unlinkThread(@PathVariable UUID id,
	                           @RequestParam String type,
	                           RedirectAttributes redirectAttributes) {
		try {
			switch (type) {
				case THREAD_TYPE_RACE_RESULTS -> seasonManagementService.unlinkRaceResultsThread(id);
				case THREAD_TYPE_STANDINGS -> seasonManagementService.unlinkStandingsThread(id);
				default -> throw new BusinessRuleException("Unknown thread type: " + type);
			}
			redirectAttributes.addFlashAttribute("successMessage", "Thread unlinked.");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/seasons/" + id + "/edit";
	}

	@PostMapping("/save")
	public String save(@Valid @ModelAttribute("seasonForm") SeasonForm form,
	                   BindingResult result,
	                   RedirectAttributes redirectAttributes, Model model) {
		if (result.hasErrors()) {
            model.addAttribute("pageTitle", form.getId() != null ? "Edit Season" : "New Season");
            if (form.getId() != null) {
                var data = seasonManagementService.getEditFormData(form.getId());
                model.addAttribute("season", data.season());
                model.addAttribute("allTeams", data.allTeams());
                model.addAttribute("allCars", data.allCars());
                model.addAttribute("allTracks", data.allTracks());
                model.addAllAttributes(discordSeasonViewService.buildDiscordIntegrationModel(form.getId()));
                preparePoolModels(form.getId(), model);
            }
			return "admin/season-form";
		}
		var season = seasonManagementService.save(form.getId(), form.getName(),
				form.getYear(), form.getNumber(), form.getDescription(), form.isActive());
		redirectAttributes.addFlashAttribute("successMessage", "Season saved: " + season.getName());
		return "redirect:/admin/seasons";
	}

	@PostMapping("/{id}/add-team")
	public String addTeam(@PathVariable UUID id, @RequestParam UUID teamId,
	                      RedirectAttributes redirectAttributes) {
		String teamName = seasonManagementService.addTeamToSeason(id, teamId);
		redirectAttributes.addFlashAttribute("successMessage", "Team added: " + teamName);
		return "redirect:/admin/seasons/" + id + "/edit";
	}

	@PostMapping("/{id}/remove-team")
	public String removeTeam(@PathVariable UUID id, @RequestParam UUID teamId,
	                         RedirectAttributes redirectAttributes) {
		try {
			seasonManagementService.removeTeamFromSeason(id, teamId);
			redirectAttributes.addFlashAttribute("successMessage", "Team removed");
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/seasons/" + id + "/edit";
	}

	@PostMapping("/{id}/update-season-team")
	public String updateSeasonTeam(@PathVariable UUID id,
	                               @RequestParam UUID seasonTeamId,
	                               @RequestParam(required = false) Integer rating,
	                               @RequestParam(required = false) String primaryColor,
	                               @RequestParam(required = false) String secondaryColor,
	                               @RequestParam(required = false) String accentColor,
	                               @RequestParam(required = false) MultipartFile logoOverride,
	                               RedirectAttributes redirectAttributes) {
		try {
			String teamName = seasonManagementService.updateSeasonTeam(
					seasonTeamId, rating, primaryColor, secondaryColor, accentColor, logoOverride);
			redirectAttributes.addFlashAttribute("successMessage", "Updated: " + teamName);
		} catch (IOException e) {
			redirectAttributes.addFlashAttribute("errorMessage", "Logo upload failed: " + e.getMessage());
		}
		return "redirect:/admin/seasons/" + id;
	}

	@PostMapping("/{id}/replace-team")
	public String replaceTeam(@PathVariable UUID id,
	                          @RequestParam UUID predecessorTeamId,
	                          @RequestParam UUID successorTeamId,
	                          @RequestParam LocalDate replacedAt,
	                          RedirectAttributes redirectAttributes) {
		try {
			String result = seasonManagementService.replaceTeam(id, predecessorTeamId, successorTeamId, replacedAt);
			redirectAttributes.addFlashAttribute("successMessage", "Team replaced: " + result);
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/seasons/" + id;
	}

    private void preparePoolModels(UUID id, Model model) {
        model.addAttribute("poolPreview", carPoolService.preview(id));
        model.addAttribute("poolCategories", CarCategory.values());
        model.addAttribute("poolDrivetrains", Drivetrain.values());
        if (!model.containsAttribute("poolEntryForm")) model.addAttribute("poolEntryForm", new SeasonPoolEntryForm());
        if (!model.containsAttribute("poolModeForm")) {
            var mode = new SeasonPoolModeForm();
            mode.setCarPoolMode(seasonManagementService.findById(id).getCarPoolMode());
            model.addAttribute("poolModeForm", mode);
        }
    }

    @InitBinder("poolEntryForm")
    void bindPoolEntry(org.springframework.web.bind.WebDataBinder binder, jakarta.servlet.http.HttpServletRequest request) {
        for (String field : request.getParameterMap().keySet()) {
            if (!field.startsWith("clauses[")) continue;
            try {
                int end = field.indexOf(']');
                int index = Integer.parseInt(field.substring("clauses[".length(), end));
                if (index < 0 || index >= SeasonCarPoolService.MAX_CLAUSES) throw new NumberFormatException();
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                throw new org.ctc.domain.exception.ValidationException("A pool entry supports at most 32 clauses.");
            }
        }
        binder.setAllowedFields("entryId", "label", "clauses[*].category", "clauses[*].drivetrain", "clauses[*].modelYear");
        binder.setAutoGrowCollectionLimit(32);
    }

    @GetMapping("/{id}/car-pool/entries/{entryId}/edit")
    public String editPoolEntry(@PathVariable UUID id, @PathVariable UUID entryId, Model model) {
        var season = seasonManagementService.findById(id);
        var entry = season.getCarPoolEntries().stream().filter(item -> item.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Pool entry does not belong to this season."));
        model.addAttribute("poolEntryForm", SeasonPoolEntryForm.from(entry));
        return edit(id, model);
    }

    @PostMapping("/{id}/car-pool/entries/save")
    public String savePoolEntry(@PathVariable UUID id, @ModelAttribute("poolEntryForm") SeasonPoolEntryForm form,
                               BindingResult result, @RequestParam(defaultValue = "save") String action,
                               Model model, RedirectAttributes redirect) {
        if (action.equals("add-clause")) {
            if (form.getClauses().size() < SeasonCarPoolService.MAX_CLAUSES) form.getClauses().add(new SeasonPoolEntryForm.ClauseForm());
            else result.reject("pool", "An entry supports at most 32 clauses.");
            return edit(id, model);
        }
        if (action.startsWith("remove-clause:")) {
            try {
                int index = Integer.parseInt(action.substring("remove-clause:".length()));
                if (index < 0 || index >= form.getClauses().size()) throw new NumberFormatException();
                form.getClauses().remove(index);
                reindexClauseErrors(form, result, index, model);
            } catch (NumberFormatException e) { result.reject("pool", "Choose an existing clause to remove."); }
            return edit(id, model);
        }
        if (!action.equals("save")) result.reject("pool", "Unknown pool action.");
        validator.validate(form).forEach(violation -> result.rejectValue(violation.getPropertyPath().toString(), "pool", violation.getMessage()));
        if (!result.hasErrors()) {
            try { carPoolService.saveEntry(id, form.getEntryId(), form.getLabel(), form.toInputs()); }
            catch (BusinessRuleException e) { result.reject("pool", e.getMessage()); }
        }
        if (result.hasErrors()) return edit(id, model);
        redirect.addFlashAttribute("successMessage", "Pool entry saved. Review the preview before freezing.");
        return poolRedirect(id);
    }

    private static void reindexClauseErrors(SeasonPoolEntryForm form, BindingResult original, int removed, Model model) {
        var updated = new org.springframework.validation.BeanPropertyBindingResult(form, "poolEntryForm");
        for (var error : original.getAllErrors()) {
            if (error instanceof org.springframework.validation.FieldError fieldError && fieldError.getField().startsWith("clauses[")) {
                int end = fieldError.getField().indexOf(']');
                int index = Integer.parseInt(fieldError.getField().substring("clauses[".length(), end));
                if (index == removed) continue;
                String field = "clauses[" + (index > removed ? index - 1 : index) + fieldError.getField().substring(end);
                updated.addError(new org.springframework.validation.FieldError("poolEntryForm", field, fieldError.getRejectedValue(),
                        fieldError.isBindingFailure(), fieldError.getCodes(), fieldError.getArguments(), fieldError.getDefaultMessage()));
            } else updated.addError(error);
        }
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "poolEntryForm", updated);
    }

    @PostMapping("/{id}/car-pool/mode")
    public String savePoolMode(@PathVariable UUID id, @Valid @ModelAttribute("poolModeForm") SeasonPoolModeForm form,
                              BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try { carPoolService.changeMode(id, form.getCarPoolMode()); }
            catch (BusinessRuleException e) { result.reject("pool", e.getMessage()); }
        }
        if (result.hasErrors()) return edit(id, model);
        redirect.addFlashAttribute("successMessage", "Car pool mode saved.");
        return poolRedirect(id);
    }

    @PostMapping("/{id}/car-pool/entries/{entryId}/remove")
    public String removePoolEntry(@PathVariable UUID id, @PathVariable UUID entryId, Model model, RedirectAttributes redirect) {
        try { carPoolService.removeEntry(id, entryId); }
        catch (BusinessRuleException e) { model.addAttribute("errorMessage", e.getMessage()); return edit(id, model); }
        redirect.addFlashAttribute("successMessage", "Pool entry removed.");
        return poolRedirect(id);
    }

    @PostMapping("/{id}/car-pool/freeze")
    public String freezePool(@PathVariable UUID id, Model model, RedirectAttributes redirect) {
        try { carPoolService.freeze(id); }
        catch (BusinessRuleException e) { model.addAttribute("errorMessage", e.getMessage()); return edit(id, model); }
        redirect.addFlashAttribute("successMessage", "Pool frozen. Membership and definitions are now fixed.");
        return poolRedirect(id);
    }

    @PostMapping("/{id}/car-pool/reopen")
    public String reopenPool(@PathVariable UUID id, Model model, RedirectAttributes redirect) {
        try { carPoolService.reopen(id); }
        catch (BusinessRuleException e) { model.addAttribute("errorMessage", e.getMessage()); return edit(id, model); }
        redirect.addFlashAttribute("successMessage", "Pool reopened as a draft. Preview before freezing again.");
        return poolRedirect(id);
    }

    private static String poolRedirect(UUID id) { return "redirect:/admin/seasons/" + id + "/edit#carPool"; }

	@PostMapping("/{id}/cars/add")
	public String addCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
	                      RedirectAttributes redirectAttributes) {
		int added = seasonManagementService.addCarsToSeason(id, carIds);
		redirectAttributes.addFlashAttribute("successMessage", added + " car(s) added to pool");
		return "redirect:/admin/seasons/" + id + "/edit#carPool";
	}

	@PostMapping("/{id}/cars/remove")
	public String removeCars(@PathVariable UUID id, @RequestParam List<UUID> carIds,
	                         RedirectAttributes redirectAttributes) {
		int removed = seasonManagementService.removeCarsFromSeason(id, carIds);
		redirectAttributes.addFlashAttribute("successMessage", removed + " car(s) removed from pool");
		return "redirect:/admin/seasons/" + id + "/edit#carPool";
	}

	@PostMapping("/{id}/tracks/add")
	public String addTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
	                        RedirectAttributes redirectAttributes) {
		int added = seasonManagementService.addTracksToSeason(id, trackIds);
		redirectAttributes.addFlashAttribute("successMessage", added + " track(s) added to pool");
		return "redirect:/admin/seasons/" + id + "/edit#trackPool";
	}

	@PostMapping("/{id}/tracks/remove")
	public String removeTracks(@PathVariable UUID id, @RequestParam List<UUID> trackIds,
	                           RedirectAttributes redirectAttributes) {
		int removed = seasonManagementService.removeTracksFromSeason(id, trackIds);
		redirectAttributes.addFlashAttribute("successMessage", removed + " track(s) removed from pool");
		return "redirect:/admin/seasons/" + id + "/edit#trackPool";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		String name = seasonManagementService.delete(id);
		redirectAttributes.addFlashAttribute("successMessage", "Season deleted: " + name);
		return "redirect:/admin/seasons";
	}

	@GetMapping("/{id}/swiss")
	public String swissRounds(@PathVariable UUID id, Model model) {
		var data = seasonManagementService.getSwissRoundData(id);
		var regular = seasonPhaseService.findRegularPhase(id);
		Integer totalRounds = regular.getTotalRounds();
		model.addAttribute("season", data.season());
		model.addAttribute("rounds", data.rounds());
		model.addAttribute("pairings", data.pairings());
		model.addAttribute("totalRounds", totalRounds);
		model.addAttribute("currentRound", swissPairingService.getCurrentRound(regular.getId(), null));
		model.addAttribute("canGenerateNext",
				swissPairingService.isCurrentRoundComplete(regular.getId(), null)
						&& (totalRounds == null || data.season().getMatchdays().size() < totalRounds));
		return "admin/swiss-rounds";
	}

	@PostMapping("/{id}/swiss/generate")
	public String generateSwissRound(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		var regular = seasonPhaseService.findRegularPhase(id);
		try {
			swissPairingService.generateNextRound(regular.getId(), null);
			redirectAttributes.addFlashAttribute("successMessage", "Next round generated successfully");
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/seasons/" + id + "/swiss";
	}

	@GetMapping("/{id}/generate")
	public String generateForm(@PathVariable UUID id, Model model) {
		var formData = matchdayGeneratorService.getFormData(id);
		var season = formData.season();
		var regular = formData.phase();
		var form = new MatchdayGeneratorForm();
		Integer rounds = regular.getTotalRounds();
		form.setNumberOfRounds(rounds != null ? rounds : formData.optimalRounds());
		if (regular.getLayout() == PhaseLayout.GROUPS && !formData.groups().isEmpty()) {
			var group = formData.groups().getFirst();
			form.setGroupId(group.id());
			if (rounds == null) {
				form.setNumberOfRounds(group.optimalRounds());
			}
		}
		model.addAttribute("season", season);
		model.addAttribute("phase", regular);
		model.addAttribute("generatorForm", form);
		model.addAttribute("teamCount", formData.teamCount());
		model.addAttribute("optimalRounds", formData.optimalRounds());
		model.addAttribute("generatorGroups", formData.groups());
		return "admin/matchday-generator";
	}

	@PostMapping("/{id}/generate")
	public String generate(@PathVariable UUID id,
	                       @Valid @ModelAttribute("generatorForm") MatchdayGeneratorForm form,
	                       BindingResult result,
	                       Model model,
	                       RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			return renderGeneratorForm(id, model);
		}
		var regular = seasonPhaseService.findRegularPhase(id);
		try {
			matchdayGeneratorService.generate(regular.getId(), form.getGroupId(), form.getNumberOfRounds(), form.isHomeAndAway());
			redirectAttributes.addFlashAttribute("successMessage", "Matchdays generated successfully");
		} catch (IllegalStateException | IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			return renderGeneratorForm(id, model);
		}
		return "redirect:/admin/seasons/" + id;
	}

	private String renderGeneratorForm(UUID id, Model model) {
		var data = matchdayGeneratorService.getFormData(id);
		model.addAttribute("season", data.season());
		model.addAttribute("phase", data.phase());
		model.addAttribute("teamCount", data.teamCount());
		model.addAttribute("optimalRounds", data.optimalRounds());
		model.addAttribute("generatorGroups", data.groups());
		return "admin/matchday-generator";
	}
}
