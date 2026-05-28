package org.ctc.admin.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchForm {

	private UUID id;

	@Size(max = 2000)
	private String discordTeaser;

	@Size(max = 500)
	private String streamLink;

	@Size(max = 100)
	private String lobbyHost;

	@Size(max = 100)
	private String raceDirector;

	@Size(max = 100)
	private String streamer;
}
