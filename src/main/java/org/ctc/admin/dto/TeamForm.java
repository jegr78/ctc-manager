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
public class TeamForm {

	private UUID id;

	@NotBlank
	private String name;

	@NotBlank
	private String shortName;

	private String primaryColor;

	private String secondaryColor;

	private String accentColor;

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String discordRoleId;
}
