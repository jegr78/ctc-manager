package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookPayload(String content, List<Embed> embeds) {
}
