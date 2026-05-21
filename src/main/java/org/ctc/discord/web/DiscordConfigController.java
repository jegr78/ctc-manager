package org.ctc.discord.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.BotUser;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/discord-config")
@RequiredArgsConstructor
@Slf4j
public class DiscordConfigController {

	private static final String REDIRECT = "redirect:/admin/discord-config";
	private static final String VIEW = "admin/discord-config";

	private final DiscordEmojiCache emojiCache;
	private final DiscordGlobalConfigService configService;
	private final DiscordRestClient discordRestClient;
	private final DiscordWebhookClient webhookClient;

	@GetMapping
	public String view(Model model) {
		DiscordGlobalConfig current = configService.getOrInitialize();
		model.addAttribute("config", current);
		if (!model.containsAttribute("form")) {
			model.addAttribute("form", toForm(current));
		}
		model.addAttribute("activeRoute", "discord-config");
		return VIEW;
	}

	@PostMapping("/save")
	public String save(
			@Valid @ModelAttribute("form") DiscordConfigForm form,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("config", configService.getOrInitialize());
			model.addAttribute("activeRoute", "discord-config");
			return VIEW;
		}
		configService.save(form);
		redirectAttributes.addFlashAttribute("successMessage", "Configuration saved.");
		return REDIRECT;
	}

	@PostMapping("/test-connection")
	public String testConnection(RedirectAttributes redirectAttributes) {
		try {
			BotUser user = discordRestClient.fetchBotUser();
			redirectAttributes.addFlashAttribute("successMessage", "Connected as " + user.username());
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Test Connection");
		}
		return REDIRECT;
	}

	@PostMapping("/test-webhook")
	public String testWebhook(RedirectAttributes redirectAttributes) {
		DiscordGlobalConfig current = configService.getOrInitialize();
		String webhookUrl = current.getAnnouncementWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Announcement webhook URL is not configured.");
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
			return REDIRECT;
		}
		try {
			webhookClient.execute(webhookUrl, new WebhookPayload("Test from CTC-Manager", List.of()));
			redirectAttributes.addFlashAttribute("successMessage", "Webhook test message delivered.");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Test Announcement Webhook");
		}
		return REDIRECT;
	}

	@PostMapping("/refresh-roles-cache")
	public String refreshRolesCache(RedirectAttributes redirectAttributes) {
		DiscordGlobalConfig current = configService.getOrInitialize();
		String guildId = current.getGuildId();
		if (guildId == null || guildId.isBlank()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Guild ID is not configured.");
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
			return REDIRECT;
		}
		try {
			int count = discordRestClient.fetchGuildRoles(guildId).size();
			redirectAttributes.addFlashAttribute(
					"successMessage", "Server roles refreshed (" + count + " entries).");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Refresh Server Roles");
		}
		return REDIRECT;
	}

	@PostMapping("/refresh-emoji-cache")
	public String refreshEmojiCache(RedirectAttributes redirectAttributes) {
		DiscordGlobalConfig current = configService.getOrInitialize();
		String guildId = current.getGuildId();
		if (guildId == null || guildId.isBlank()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Guild ID is not configured.");
			redirectAttributes.addFlashAttribute("errorCategory", "not-found");
			return REDIRECT;
		}
		try {
			Map<String, String> emojis = discordRestClient.fetchGuildEmojis(guildId);
			int refreshed = emojiCache.refresh(emojis);
			redirectAttributes.addFlashAttribute(
					"successMessage", "Emoji cache refreshed (" + refreshed + " entries).");
		} catch (DiscordApiException e) {
			applyErrorFlash(redirectAttributes, e, "Refresh Emoji Cache");
		}
		return REDIRECT;
	}

	private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
		String message = switch (e.category()) {
			case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
			case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
			case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
			case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
		};
		String category = e.category().name().toLowerCase().replace('_', '-');
		log.warn("{} failed: category={}", action, category);
		ra.addFlashAttribute("errorMessage", message);
		ra.addFlashAttribute("errorCategory", category);
	}

	private static DiscordConfigForm toForm(DiscordGlobalConfig config) {
		DiscordConfigForm form = new DiscordConfigForm();
		form.setGuildId(nullSafe(config.getGuildId()));
		form.setAnnouncementWebhookUrl(nullSafe(config.getAnnouncementWebhookUrl()));
		form.setRaceResultsForumChannelId(nullSafe(config.getRaceResultsForumChannelId()));
		form.setStandingsForumChannelId(nullSafe(config.getStandingsForumChannelId()));
		form.setVsEmojiName(nullSafe(config.getVsEmojiName()));
		form.setBotApplicationId(config.getBotApplicationId());
		return form;
	}

	private static String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
