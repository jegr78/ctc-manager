package org.ctc.discord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class DiscordWebhookClient {

	private static final String USER_AGENT_VALUE =
			"CTC-Manager (https://github.com/jegr78/ctc-manager, 1.13)";
	private static final int MAX_ATTACHMENTS = 10;

	private final DiscordRateLimitInterceptor rateLimitInterceptor;
	private final ObjectMapper objectMapper;
	private final DiscordHostValidator hostValidator;

	public DiscordWebhookClient(
			DiscordRateLimitInterceptor rateLimitInterceptor,
			ObjectMapper objectMapper,
			DiscordHostValidator hostValidator) {
		this.rateLimitInterceptor = rateLimitInterceptor;
		this.objectMapper = objectMapper;
		this.hostValidator = hostValidator;
	}

	public WebhookMessage execute(String webhookUrl, WebhookPayload payload) throws DiscordApiException {
		hostValidator.requireAllowed(webhookUrl);
		try {
			return execute(() -> forWebhookUrl(webhookUrl)
					.post()
					.uri("")
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(WebhookMessage.class));
		} catch (DiscordApiException e) {
			log.warn("Discord webhook execute failed for {}: {}", webhookUrl, e.category());
			throw e;
		}
	}

	public WebhookMessage executeMultipart(
			String webhookUrl, WebhookPayload payload, List<NamedAttachment> attachments)
			throws DiscordApiException {
		if (attachments.size() > MAX_ATTACHMENTS) {
			throw new IllegalArgumentException(
					"Discord allows at most " + MAX_ATTACHMENTS + " attachments per webhook (got "
							+ attachments.size() + ")");
		}
		if (attachments.isEmpty()) {
			return execute(webhookUrl, payload);
		}
		hostValidator.requireAllowed(webhookUrl);
		String payloadJson;
		try {
			payloadJson = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
		HttpHeaders payloadHeaders = new HttpHeaders();
		payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
		parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
		for (int i = 0; i < attachments.size(); i++) {
			NamedAttachment att = attachments.get(i);
			final String filename = att.filename();
			HttpHeaders fileHeaders = new HttpHeaders();
			fileHeaders.setContentType(MediaType.IMAGE_PNG);
			ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
				@Override
				public String getFilename() {
					return filename;
				}
			};
			parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
		}
		return execute(() -> forWebhookUrl(webhookUrl)
				.post()
				.uri("")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts)
				.retrieve()
				.body(WebhookMessage.class));
	}

	public WebhookMessage editMessage(String webhookUrl, String messageId, WebhookPayload payload)
			throws DiscordApiException {
		hostValidator.requireAllowed(webhookUrl);
		return execute(() -> forWebhookUrl(webhookUrl)
				.patch()
				.uri("/messages/{messageId}", messageId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.body(WebhookMessage.class));
	}

	public WebhookMessage editMessageWithAttachments(
			String webhookUrl,
			String messageId,
			WebhookPayload payload,
			List<NamedAttachment> attachments)
			throws DiscordApiException {
		hostValidator.requireAllowed(webhookUrl);
		if (attachments.size() > MAX_ATTACHMENTS) {
			throw new IllegalArgumentException(
					"Discord allows at most " + MAX_ATTACHMENTS + " attachments per webhook (got "
							+ attachments.size() + ")");
		}
		if (attachments.isEmpty()) {
			return editMessage(webhookUrl, messageId, payload);
		}
		String payloadJson;
		try {
			payloadJson = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
		HttpHeaders payloadHeaders = new HttpHeaders();
		payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
		parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
		for (int i = 0; i < attachments.size(); i++) {
			NamedAttachment att = attachments.get(i);
			final String filename = att.filename();
			HttpHeaders fileHeaders = new HttpHeaders();
			fileHeaders.setContentType(MediaType.IMAGE_PNG);
			ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
				@Override
				public String getFilename() {
					return filename;
				}
			};
			parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
		}
		return execute(() -> forWebhookUrl(webhookUrl)
				.patch()
				.uri("/messages/{messageId}", messageId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts)
				.retrieve()
				.body(WebhookMessage.class));
	}

	private RestClient forWebhookUrl(String webhookUrl) {
		return RestClient.builder()
				.baseUrl(webhookUrl)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
				.requestInterceptor(rateLimitInterceptor)
				.build();
	}

	private static <T> T execute(RestCall<T> call) throws DiscordApiException {
		try {
			return call.run();
		} catch (RestClientResponseException e) {
			throw DiscordApiExceptionMapper.from(e);
		} catch (ResourceAccessException e) {
			throw unwrapInterceptorException(e);
		}
	}

	private static DiscordApiException unwrapInterceptorException(ResourceAccessException e) {
		Throwable cause = e.getCause();
		if (cause instanceof DiscordApiException dae) {
			return dae;
		}
		return new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
	}

	@FunctionalInterface
	private interface RestCall<T> {
		T run();
	}
}
