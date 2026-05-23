package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ctc.discord.dto.DiscordSnowflake;

@Getter
@Setter
@NoArgsConstructor
public class SeasonForm {

	private UUID id;

	@NotBlank
	private String name;

	private int year;

	private int number;

	private String description;

	private boolean active;

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String discordRaceResultsThreadId;

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String discordStandingsThreadId;
}
