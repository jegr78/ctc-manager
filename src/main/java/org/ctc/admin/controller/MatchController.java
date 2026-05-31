package org.ctc.admin.controller;

import static org.springframework.util.StringUtils.hasText;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchForm;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.service.DiscordChannelService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.MatchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/matches")
@RequiredArgsConstructor
public class MatchController {

	private static final String AUTO_POST_ERROR_ATTRIBUTE = "discord.autoPostError";

	private final MatchService matchService;
	private final DiscordChannelService discordChannelService;
	private final DiscordRestClient discordRestClient;
	private final DiscordPostService discordPostService;
	private final TeamCardService teamCardService;
	private final SeasonTeamRepository seasonTeamRepository;

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
		model.addAllAttributes(matchService.buildMatchDetailModel(id));
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
		form.setWalkoverTeamId(match.getWalkoverTeam() != null ? match.getWalkoverTeam().getId() : null);
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
		try {
			matchService.updateMatchEdit(id, form);
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Walkover update");
			return "redirect:/admin/matches/" + id;
		}
		redirectAttributes.addFlashAttribute("successMessage", "Match details updated.");
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/create-discord-channel")
	public String createDiscordChannel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordChannelService.createMatchChannel(matchService.findById(id));
			String autoPostError = consumeAutoPostError();
			if (autoPostError != null) {
				redirectAttributes.addFlashAttribute("errorMessage",
						"Channel created. Team Cards post failed: " + autoPostError
								+ " — click Re-Post Team Cards to retry.");
				redirectAttributes.addFlashAttribute("errorCategory", autoPostError);
			} else {
				redirectAttributes.addFlashAttribute("successMessage", "Discord channel created.");
			}
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Create Discord Channel");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/link-discord-channel")
	public String linkDiscordChannel(@PathVariable UUID id,
	                                 @RequestParam(required = false) String channelId,
	                                 RedirectAttributes redirectAttributes) {
		if (!hasText(channelId)) {
			redirectAttributes.addFlashAttribute("errorMessage", "Discord channel ID is required.");
			return "redirect:/admin/matches/" + id;
		}
		try {
			discordChannelService.linkExistingChannel(matchService.findById(id), channelId.trim());
			redirectAttributes.addFlashAttribute("successMessage", "Discord channel linked.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Link Discord Channel");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Link Discord Channel");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-team-cards")
	public String postTeamCards(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postTeamCards(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Team cards posted.");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post team cards");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-settings")
	public String postSettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postSettings(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Settings posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post settings");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post settings");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-lobby-settings")
	public String postLobbySettings(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postLobbySettings(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Lobby settings posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post lobby settings");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post lobby settings");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-lineups")
	public String postLineups(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postLineups(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Lineups posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post lineups");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post lineups");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-match-results")
	public String postMatchResults(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postMatchResults(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Match results posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post match results");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post match results");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-provisional")
	public String postProvisional(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postProvisionalScores(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Provisional scores posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post provisional scores");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post provisional scores");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-schedule")
	public String postSchedule(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postSchedule(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Schedule posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post schedule");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post schedule");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/post-match-preview")
	public String postMatchPreview(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			discordPostService.postMatchPreview(matchService.findById(id));
			redirectAttributes.addFlashAttribute("successMessage", "Match preview posted.");
		} catch (BusinessRuleException e) {
			applyErrorFlash(redirectAttributes, e, "Post match preview");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Post match preview");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/refresh-team-cards")
	public String refreshTeamCards(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			Match match = matchService.findById(id);
			SeasonTeam home = resolveSeasonTeam(match, match.getHomeTeam().getId());
			SeasonTeam away = resolveSeasonTeam(match, match.getAwayTeam().getId());
			teamCardService.generateCard(home);
			teamCardService.generateCard(away);
			discordPostService.postTeamCards(match);
			redirectAttributes.addFlashAttribute("successMessage", "Team cards regenerated and re-posted.");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Refresh team cards");
		} catch (java.io.IOException e) {
			log.warn("Refresh team cards failed for match {}: {}", id, e.toString());
			redirectAttributes.addFlashAttribute("errorMessage",
					"Refresh failed: could not regenerate team cards.");
			redirectAttributes.addFlashAttribute("errorCategory", "transient");
		}
		return "redirect:/admin/matches/" + id;
	}

	@PostMapping("/{id}/move-to-archive")
	public String moveToArchive(@PathVariable UUID id,
	                            @RequestParam(required = false) String categoryId,
	                            RedirectAttributes redirectAttributes) {
		try {
			if (!hasText(categoryId)) {
				redirectAttributes.addFlashAttribute("errorMessage",
						"Select an archive category before confirming the move.");
				redirectAttributes.addFlashAttribute("errorCategory", "data-incomplete");
				return "redirect:/admin/matches/" + id;
			}
			Match match = matchService.findById(id);
			if (match.getDiscordChannelId() == null) {
				throw new BusinessRuleException("Match has no Discord channel to archive.");
			}
			discordRestClient.modifyChannel(match.getDiscordChannelId(),
					new ChannelModifyRequest(null, categoryId));
			matchService.markChannelArchived(id);
			redirectAttributes.addFlashAttribute("successMessage", "Channel moved to archive.");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Move to Archive");
		}
		return "redirect:/admin/matches/" + id;
	}

	private SeasonTeam resolveSeasonTeam(Match match, UUID teamId) {
		return seasonTeamRepository
				.findBySeasonIdAndTeamId(match.getMatchday().getSeason().getId(), teamId)
				.orElseThrow(() -> new IllegalStateException(
						"SeasonTeam missing for season " + match.getMatchday().getSeason().getId()
								+ " and team " + teamId));
	}

	private static String consumeAutoPostError() {
		try {
			return (String) RequestContextHolder.currentRequestAttributes()
					.getAttribute(AUTO_POST_ERROR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		} catch (IllegalStateException ignoredNoRequestBound) {
			return null;
		}
	}

	private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
		String message = switch (e.category()) {
			case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
			case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
			case MISSING_PERMISSIONS -> DiscordApiExceptionMapper.MISSING_PERMISSIONS_MESSAGE;
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

	private void applyErrorFlash(RedirectAttributes ra, BusinessRuleException e, String action) {
		log.warn("{} failed: category=data-incomplete, message={}", action, e.getMessage());
		ra.addFlashAttribute("errorMessage", e.getMessage());
		ra.addFlashAttribute("errorCategory", "data-incomplete");
	}
}
