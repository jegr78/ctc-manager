package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchForm;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordCategoryFullException;
import org.ctc.discord.service.DiscordChannelService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.service.MatchService;
import org.ctc.domain.service.MatchService.MatchDetailData;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/matches")
@RequiredArgsConstructor
public class MatchController {

	private final MatchService matchService;
	private final DiscordChannelService discordChannelService;
	private final DiscordRestClient discordRestClient;

	@GetMapping("/new")
	public String create(@RequestParam UUID matchdayId, Model model) {
		var formData = matchService.getCreateFormData(matchdayId);
		model.addAttribute("matchday", formData.matchday());
		model.addAttribute("teams", formData.teams());
		return "admin/match-form";
	}

	@PostMapping("/save")
	public String save(@RequestParam UUID matchdayId,
	                   @RequestParam UUID homeTeamId,
	                   @RequestParam(required = false) UUID awayTeamId,
	                   @RequestParam(defaultValue = "false") boolean bye,
	                   RedirectAttributes redirectAttributes) {
		try {
			var match = matchService.createMatch(matchdayId, homeTeamId, awayTeamId, bye);
			var homeShort = match.getHomeTeam().getShortName();
			var awayShort = match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "?";
			redirectAttributes.addFlashAttribute("successMessage",
					"Match created: " + homeShort + (bye ? " (Bye)" : " vs " + awayShort));
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/matchdays/" + matchdayId;
	}

	@PostMapping("/{id}/add-leg")
	public String addLeg(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			var match = matchService.addLeg(id);
			redirectAttributes.addFlashAttribute("successMessage", "Leg added");
			return "redirect:/admin/matchdays/" + match.getMatchday().getId();
		} catch (IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/matchdays/" + matchService.getMatchdayId(id);
		}
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		var matchdayId = matchService.deleteMatch(id);
		redirectAttributes.addFlashAttribute("successMessage", "Match deleted");
		return "redirect:/admin/matchdays/" + matchdayId;
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable UUID id, Model model) {
		MatchDetailData data = matchService.getDetailData(id);
		Match match = data.match();
		String awayShort = match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "Bye";
		model.addAttribute("match", match);
		model.addAttribute("archiveCategories", data.archiveCategories());
		model.addAttribute("defaultSelectionId", data.defaultSelectionId());
		model.addAttribute("pageTitle",
				"Match: " + match.getHomeTeam().getShortName() + " vs " + awayShort);
		return "admin/match-detail";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		Match match = matchService.findById(id);
		MatchForm form = new MatchForm();
		form.setId(match.getId());
		form.setDiscordTeaser(match.getDiscordTeaser());
		form.setStreamLink(match.getStreamLink());
		form.setLobbyHost(match.getLobbyHost());
		form.setRaceDirector(match.getRaceDirector());
		form.setStreamer(match.getStreamer());
		model.addAttribute("matchForm", form);
		model.addAttribute("match", match);
		return "admin/match-form-edit";
	}

	@PostMapping("/{id}/save-edit")
	public String saveEdit(@PathVariable UUID id,
	                       @Valid @ModelAttribute("matchForm") MatchForm form,
	                       BindingResult result,
	                       Model model,
	                       RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			model.addAttribute("match", matchService.findById(id));
			return "admin/match-form-edit";
		}
		matchService.updateDiscordFields(id, form);
		redirectAttributes.addFlashAttribute("successMessage", "Match details updated.");
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/create-discord-channel")
	public String createDiscordChannel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordChannelService.createMatchChannel(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Discord channel created.");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Create Discord Channel");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/move-to-archive")
	public String moveToArchive(@PathVariable UUID id,
	                            @RequestParam(required = false) String categoryId,
	                            RedirectAttributes redirectAttributes) {
		try {
			Match match = matchService.findById(id);
			if (match.getDiscordChannelId() == null) {
				throw new BusinessRuleException("Match has no Discord channel to archive.");
			}
			if (categoryId == null || categoryId.isBlank()) {
				throw new DiscordCategoryFullException(
						DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE, null);
			}
			discordRestClient.modifyChannel(match.getDiscordChannelId(),
					new ChannelModifyRequest(null, categoryId));
			redirectAttributes.addFlashAttribute("successMessage", "Channel moved to archive.");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Move to Archive");
		}
		return "redirect:/admin/matches/" + id;
	}

	private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
		String message = switch (e.category()) {
			case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
			case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
			case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
			case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
		};
		String category = e.category().name().toLowerCase().replace('_', '-');
		Throwable cause = e.getCause();
		log.warn("{} failed: category={}, exception={}, cause={}",
				action, category, e.getClass().getSimpleName(),
				cause != null ? cause.toString() : "none");
		ra.addFlashAttribute("errorMessage", message);
		ra.addFlashAttribute("errorCategory", category);
	}
}
