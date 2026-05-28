package org.ctc.discord.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordRestClient.BotUser;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordCategoryFullException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class DiscordConfigControllerTest {

	private DiscordGlobalConfigService configService;
	private DiscordRestClient discordRestClient;
	private DiscordWebhookClient webhookClient;
	private DiscordEmojiCache emojiCache;
	private org.ctc.discord.DiscordRoleCache roleCache;
	private org.ctc.discord.DiscordBotIdentityCache botIdentityCache;
	private DiscordConfigController controller;

	@BeforeEach
	void setUp() {
		configService = mock(DiscordGlobalConfigService.class);
		discordRestClient = mock(DiscordRestClient.class);
		webhookClient = mock(DiscordWebhookClient.class);
		emojiCache = mock(DiscordEmojiCache.class);
		roleCache = mock(org.ctc.discord.DiscordRoleCache.class);
		botIdentityCache = mock(org.ctc.discord.DiscordBotIdentityCache.class);
		controller = new DiscordConfigController(
				botIdentityCache, emojiCache, configService, discordRestClient, roleCache, webhookClient);
		given(configService.getOrInitialize()).willReturn(new DiscordGlobalConfig());
	}

	@Test
	void givenValidForm_whenSave_thenRedirectsWithSuccessFlash() {
		DiscordConfigForm form = new DiscordConfigForm();
		BindingResult br = new BeanPropertyBindingResult(form, "form");
		RedirectAttributes ra = new RedirectAttributesModelMap();

		String view = controller.save(form, br, new ConcurrentModel(), ra);

		assertThat(view).isEqualTo("redirect:/admin/discord-config");
		assertThat(flash(ra, "successMessage")).isEqualTo("Configuration saved.");
		verify(configService).save(form);
	}

	@Test
	void givenBindingErrors_whenSave_thenRendersFormView() {
		DiscordConfigForm form = new DiscordConfigForm();
		BindingResult br = new BeanPropertyBindingResult(form, "form");
		br.reject("guildId", "Must be a snowflake");
		RedirectAttributes ra = new RedirectAttributesModelMap();

		String view = controller.save(form, br, new ConcurrentModel(), ra);

		assertThat(view).isEqualTo("admin/discord-config");
		verify(configService, never()).save(any());
	}

	@Test
	void givenDiscordAuthException_whenTestConnection_thenFlashesAuthCategory() throws Exception {
		when(discordRestClient.fetchBotUser()).thenThrow(new DiscordAuthException("auth", null));
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testConnection(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("auth");
	}

	@Test
	void givenDiscordTransientException_whenTestConnection_thenFlashesTransientCategory() throws Exception {
		when(discordRestClient.fetchBotUser()).thenThrow(new DiscordTransientException("t", null));
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testConnection(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("transient");
	}

	@Test
	void givenDiscordNotFoundException_whenTestConnection_thenFlashesNotFoundCategory() throws Exception {
		when(discordRestClient.fetchBotUser()).thenThrow(new DiscordNotFoundException("nf", null));
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testConnection(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("not-found");
	}

	@Test
	void givenDiscordCategoryFullException_whenTestConnection_thenFlashesCategoryFullCategory() throws Exception {
		when(discordRestClient.fetchBotUser()).thenThrow(new DiscordCategoryFullException("cf", null));
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testConnection(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("category-full");
	}

	@Test
	void givenSuccessfulFetch_whenTestConnection_thenFlashesUsernameSuccess() throws Exception {
		BotUser bot = new BotUser("42", "CTC-Bot", "0001");
		when(discordRestClient.fetchBotUser()).thenReturn(bot);
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testConnection(ra);

		assertThat(flash(ra, "successMessage")).isEqualTo("Connected as CTC-Bot");
	}

	@Test
	void givenEmptyGuildId_whenRefreshEmojiCache_thenFlashesNotConfiguredError() {
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.refreshEmojiCache(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("not-found");
		assertThat(flash(ra, "errorMessage")).isEqualTo("Guild ID is not configured.");
		verify(emojiCache, never()).refresh(any());
	}

	@Test
	void givenConfiguredGuildId_whenRefreshEmojiCache_thenFetchesEmojisAndRefreshesCache() throws Exception {
		DiscordGlobalConfig configured = new DiscordGlobalConfig();
		configured.setGuildId("123456789012345678");
		given(configService.getOrInitialize()).willReturn(configured);
		when(discordRestClient.fetchGuildEmojis(anyString()))
				.thenReturn(Map.of("flag_de", "<:flag_de:100>"));
		when(emojiCache.refresh(any())).thenReturn(1);
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.refreshEmojiCache(ra);

		verify(discordRestClient).fetchGuildEmojis("123456789012345678");
		verify(emojiCache).refresh(Map.of("flag_de", "<:flag_de:100>"));
		assertThat(flash(ra, "successMessage")).isEqualTo("Emoji cache refreshed (1 entries).");
	}

	@Test
	void givenEmptyWebhookUrl_whenTestWebhook_thenFlashesNotConfiguredError() {
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.testWebhook(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("not-found");
		assertThat(flash(ra, "errorMessage")).isEqualTo("Announcement webhook URL is not configured.");
	}

	@Test
	void givenView_whenInvoked_thenAddsConfigAndForm() {
		Model model = new ConcurrentModel();

		String view = controller.view(model);

		assertThat(view).isEqualTo("admin/discord-config");
		assertThat(model.getAttribute("config")).isInstanceOf(DiscordGlobalConfig.class);
		assertThat(model.getAttribute("form")).isInstanceOf(DiscordConfigForm.class);
	}

	@Test
	void givenSuccessfulRefreshRolesCache_whenInvoked_thenFlashesCountAndRefreshesBotIdentity() throws Exception {
		DiscordGlobalConfig configured = new DiscordGlobalConfig();
		configured.setGuildId("123456789012345678");
		given(configService.getOrInitialize()).willReturn(configured);
		when(discordRestClient.fetchGuildRoles(anyString())).thenReturn(List.of(
				new org.ctc.discord.dto.Role("r1", "A", 1),
				new org.ctc.discord.dto.Role("r2", "B", 2)));
		when(roleCache.refresh(anyList())).thenReturn(2);
		when(botIdentityCache.refresh()).thenReturn("bot-id-42");
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.refreshRolesCache(ra);

		assertThat(flash(ra, "successMessage")).isEqualTo("Server roles refreshed (2 entries).");
		verify(botIdentityCache).refresh();
	}

	@Test
	void givenBotIdentityRefreshThrowsAuthException_whenRefreshRolesCache_thenFlashesAuthCategory() throws Exception {
		DiscordGlobalConfig configured = new DiscordGlobalConfig();
		configured.setGuildId("123456789012345678");
		given(configService.getOrInitialize()).willReturn(configured);
		when(discordRestClient.fetchGuildRoles(anyString())).thenReturn(List.of());
		when(roleCache.refresh(anyList())).thenReturn(0);
		when(botIdentityCache.refresh()).thenThrow(new DiscordAuthException("auth", null));
		RedirectAttributes ra = new RedirectAttributesModelMap();

		controller.refreshRolesCache(ra);

		assertThat(flash(ra, "errorCategory")).isEqualTo("auth");
	}

	private static String flash(RedirectAttributes ra, String key) {
		Object value = ra.getFlashAttributes().get(key);
		return value == null ? null : value.toString();
	}
}
