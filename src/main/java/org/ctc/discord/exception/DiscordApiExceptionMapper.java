package org.ctc.discord.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.web.client.RestClientResponseException;

public final class DiscordApiExceptionMapper {

	public static final String TRANSIENT_MESSAGE = "Discord connection problem — retry";
	public static final String AUTH_MESSAGE = "Discord authentication problem — check bot token";
	public static final String AUDIT_FAIL_MESSAGE =
			"Channel permission audit failed - an unexpected role had View permission."
					+ " Channel was deleted; verify Discord server-role setup and retry.";
	public static final String NOT_FOUND_MESSAGE = "Discord resource not found — verify guild/channel/webhook ID";
	public static final String CATEGORY_FULL_MESSAGE =
			"Discord archive category is full (50 channels). Create a new archive category.";

	private static final int CATEGORY_FULL_CODE = 30013;
	private static final ObjectMapper JSON = new ObjectMapper();

	private DiscordApiExceptionMapper() {
	}

	public static DiscordApiException from(RestClientResponseException e) {
		int status = e.getStatusCode().value();
		return switch (status) {
			case 401, 403 -> new DiscordAuthException(AUTH_MESSAGE, e);
			case 404 -> new DiscordNotFoundException(NOT_FOUND_MESSAGE, e);
			case 400 -> from400(e);
			default -> new DiscordTransientException(TRANSIENT_MESSAGE, e);
		};
	}

	public static DiscordApiException from(IOException e) {
		if (e instanceof DiscordApiException dae) {
			return dae;
		}
		return new DiscordTransientException(TRANSIENT_MESSAGE, e);
	}

	private static DiscordApiException from400(RestClientResponseException e) {
		String body = e.getResponseBodyAsString();
		if (!body.isBlank()) {
			try {
				JsonNode root = JSON.readTree(body);
				JsonNode codeNode = root.get("code");
				if (codeNode != null && codeNode.isInt() && codeNode.asInt() == CATEGORY_FULL_CODE) {
					return new DiscordCategoryFullException(CATEGORY_FULL_MESSAGE, e);
				}
			} catch (IOException _) {
				// fall through to transient default
			}
		}
		return new DiscordTransientException(TRANSIENT_MESSAGE, e);
	}
}
