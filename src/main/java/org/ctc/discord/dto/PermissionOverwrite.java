package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionOverwrite(
		String id,
		int type,
		String allow,
		String deny) {
}
