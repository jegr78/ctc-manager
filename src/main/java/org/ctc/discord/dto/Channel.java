package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
		String id,
		String name,
		int type,
		@JsonProperty("parent_id") String parentId,
		@JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) {

	public Channel(String id, String name, int type, String parentId) {
		this(id, name, type, parentId, null);
	}
}
