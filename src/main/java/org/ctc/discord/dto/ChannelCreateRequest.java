package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelCreateRequest(
		String name,
		int type,
		@JsonProperty("parent_id") String parentId,
		@JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) {

	public ChannelCreateRequest(String name, int type, String parentId) {
		this(name, type, parentId, null);
	}
}
