package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.ctc.discord.dto.DiscordConfigForm;
import org.junit.jupiter.api.Test;

class DiscordPostServiceWebhookUrlPatternTest {

	@Test
	void givenRealDiscordWebhookUrl_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/1234567890/abcDEF_123-XYZ--token");

		assertThat(creds.id()).isEqualTo("1234567890");
		assertThat(creds.token()).isEqualTo("abcDEF_123-XYZ--token");
	}

	@Test
	void givenVersionedApiWebhookUrl_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/v10/webhooks/9876/some-token");

		assertThat(creds.id()).isEqualTo("9876");
		assertThat(creds.token()).isEqualTo("some-token");
	}

	@Test
	void givenWireMockStyleUrlWithoutApiSegment_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"http://localhost:12345/webhooks/42/mock-token");

		assertThat(creds.id()).isEqualTo("42");
		assertThat(creds.token()).isEqualTo("mock-token");
	}

	@Test
	void givenDiscordappLegacyHost_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discordapp.com/api/webhooks/555/legacy-token");

		assertThat(creds.id()).isEqualTo("555");
		assertThat(creds.token()).isEqualTo("legacy-token");
	}

	@Test
	void givenUrlWithTrailingQueryString_whenParse_thenIgnoresQuery() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/123/token?wait=true");

		assertThat(creds.id()).isEqualTo("123");
		assertThat(creds.token()).isEqualTo("token");
	}

	@Test
	void givenGarbageUrl_whenParse_thenThrows() {
		assertThatThrownBy(() -> DiscordPostService.parseWebhookUrl(
				"https://example.com/not-a-webhook"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match expected shape");
	}

	@Test
	void givenNonNumericId_whenParse_thenThrows() {
		assertThatThrownBy(() -> DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/abc/token"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void givenFormAcceptedUrls_whenParsed_thenServiceAlsoAccepts() {
		// regex parity: anything DiscordConfigForm validates as a webhook URL must also
		// parse successfully via DiscordPostService.parseWebhookUrl
		List<String> formAcceptedUrls = List.of(
				"https://discord.com/api/webhooks/100/abc-token_XYZ",
				"https://discordapp.com/api/webhooks/555/legacy-token",
				"https://discord.com/api/v10/webhooks/9876/some-token",
				"https://discord.com/api/webhooks/123/token?wait=true");

		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = factory.getValidator();
			for (String url : formAcceptedUrls) {
				DiscordConfigForm form = new DiscordConfigForm();
				form.setAnnouncementWebhookUrl(url);
				assertThat(validator.validate(form))
						.as("Form must accept %s", url)
						.isEmpty();
				DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(url);
				assertThat(creds.id()).as("Parser must extract id from %s", url).isNotBlank();
				assertThat(creds.token()).as("Parser must extract token from %s", url).isNotBlank();
			}
		}
	}
}
