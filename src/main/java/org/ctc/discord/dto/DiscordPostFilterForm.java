package org.ctc.discord.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ctc.discord.model.DiscordPostType;

@Getter
@NoArgsConstructor
@Setter
public class DiscordPostFilterForm {

	private UUID seasonId;

	private UUID matchId;

	private DiscordPostType postType;
}
