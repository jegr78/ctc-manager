package org.ctc.discord.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DiscordConfigFormTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	@Test
	void givenAllEmptyAndCtcEmoji_whenValidate_thenNoViolations() {
		// given — bootstrap state (every field still empty, vsEmojiName = "CTC")
		DiscordConfigForm form = new DiscordConfigForm();

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	void givenValidSnowflakeGuildId_whenValidate_thenNoViolations() {
		// given
		DiscordConfigForm form = new DiscordConfigForm();
		form.setGuildId("12345678901234567");

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	void givenInvalidGuildId_whenValidate_thenSnowflakeViolation() {
		// given
		DiscordConfigForm form = new DiscordConfigForm();
		form.setGuildId("abc");

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("guildId"));
	}

	@Test
	void givenBlankVsEmojiName_whenValidate_thenNotBlankViolation() {
		// given
		DiscordConfigForm form = new DiscordConfigForm();
		form.setVsEmojiName(" ");

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("vsEmojiName"));
	}

	@Test
	void givenNonDiscordWebhookUrl_whenValidate_thenPatternViolation() {
		// given — SSRF defence at the form layer: webhook URL must be on discord.com
		DiscordConfigForm form = new DiscordConfigForm();
		form.setAnnouncementWebhookUrl("https://evil.com/api/webhooks/1/abc");

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("announcementWebhookUrl"));
	}

	@Test
	void givenValidDiscordWebhookUrl_whenValidate_thenNoViolation() {
		// given
		DiscordConfigForm form = new DiscordConfigForm();
		form.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/100/abc-token_XYZ");

		// when
		Set<ConstraintViolation<DiscordConfigForm>> violations = validator.validate(form);

		// then
		assertThat(violations).isEmpty();
	}
}
