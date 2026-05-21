package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbedField(String name, String value, boolean inline) {
}
