package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchdayGeneratorForm;
import org.ctc.admin.dto.SeasonForm;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.service.DiscordForumService;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PhaseType;
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
	private final DiscordForumService discordForumService;
	private final DiscordGlobalConfigService discordGlobalConfigService;

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
		model.addAttribute("seasonForm", new SeasonForm());
		return "admin/season-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		var data = seasonManagementService.getEditFormData(id);
		var season = data.season();
		var form = new SeasonForm();
		form.setId(season.getId());
		form.setName(season.getName());
		form.setYear(season.getYear());
		form.setNumber(season.getNumber());
		form.setDescription(season.getDescription());
		form.setActive(season.isActive());
		form.setDiscordRaceResultsThreadId(season.getDiscordRaceResultsThreadId());
		form.setDiscordStandingsThreadId(season.getDiscordStandingsThreadId());
		model.addAttribute("seasonForm", form);
		model.addAttribute("season", season);
		model.addAttribute("allTeams", data.allTeams());
		model.addAttribute("allCars", data.allCars());
		model.addAttribute("allTracks", data.allTracks());
		populateDiscordIntegrationModel(model, season);
		return "admin/season-form";
	}

	private void populateDiscordIntegrationModel(Model model, org.ctc.domain.model.Season season) {
		DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
		String raceResultsForumId = config.getRaceResultsForumChannelId();
		String standingsForumId = config.getStandingsForumChannelId();
		boolean integrationActive = isNonBlank(raceResultsForumId) || isNonBlank(standingsForumId);

		List<Thread> raceResultsOptions = loadThreadOptions(raceResultsForumId, model, "race-results");
		List<Thread> standingsOptions = loadThreadOptions(standingsForumId, model, "standings");

		model.addAttribute("discordIntegrationActive", integrationActive);
		model.addAttribute("raceResultsThreadOptions", raceResultsOptions);
		model.addAttribute("standingsThreadOptions", standingsOptions);
		model.addAttribute("linkedRaceResultsThread",
				resolveLinkedThread(season.getDiscordRaceResultsThreadId(), raceResultsOptions));
		model.addAttribute("linkedStandingsThread",
				resolveLinkedThread(season.getDiscordStandingsThreadId(), standingsOptions));
	}

	private List<Thread> loadThreadOptions(String forumChannelId, Model model, String label) {
		if (!isNonBlank(forumChannelId)) {
			return List.of();
		}
		try {
			return discordForumService.listThreads(forumChannelId);
		} catch (DiscordApiException e) {
			log.warn("Could not load {} forum threads (category={}): {}", label, e.category(), e.getMessage());
			model.addAttribute("discordIntegrationWarning",
					"Discord forum threads currently unreachable — list may be empty.");
			return List.of();
		}
	}

	private Thread resolveLinkedThread(String threadId, List<Thread> options) {
		if (!isNonBlank(threadId)) {
			return null;
		}
		return options.stream()
				.filter(t -> threadId.equals(t.id()))
				.findFirst()
				.orElse(new Thread(threadId, "(unknown)", null, 0, null, null));
	}

	private static boolean isNonBlank(String value) {
		return value != null && !value.isBlank();
	}

	@PostMapping("/{id}/link-thread")
	public String linkThread(@PathVariable UUID id,
	                         @RequestParam String threadId,
	                         @RequestParam String type,
	                         RedirectAttributes redirectAttributes) {
		try {
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
	                   RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
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
		model.addAttribute("raceScores", data.raceScores());
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
		var regular = seasonPhaseService.findRegularPhase(id);
		var form = new MatchdayGeneratorForm();
		Integer rounds = regular.getTotalRounds();
		form.setNumberOfRounds(rounds != null ? rounds : formData.optimalRounds());
		model.addAttribute("season", season);
		model.addAttribute("phase", regular);
		model.addAttribute("generatorForm", form);
		model.addAttribute("teamCount", formData.teamCount());
		model.addAttribute("optimalRounds", formData.optimalRounds());
		return "admin/matchday-generator";
	}

	@PostMapping("/{id}/generate")
	public String generate(@PathVariable UUID id,
	                       @Valid @ModelAttribute MatchdayGeneratorForm form,
	                       BindingResult result,
	                       RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Invalid input: number of rounds must be at least 1");
			return "redirect:/admin/seasons/" + id + "/generate";
		}
		var regular = seasonPhaseService.findRegularPhase(id);
		try {
			matchdayGeneratorService.generate(regular.getId(), form.getGroupId(), form.getNumberOfRounds(), form.isHomeAndAway());
			redirectAttributes.addFlashAttribute("successMessage", "Matchdays generated successfully");
		} catch (IllegalStateException | IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/seasons/" + id + "/generate";
		}
		return "redirect:/admin/seasons/" + id;
	}
}
