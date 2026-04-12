package org.ctc.admin.dto;

import java.util.UUID;

public record RankedTeamData(
		UUID teamId,
		String teamName,
		String teamShortName,
		String logoUrl,
		String primaryColor,
		Integer rating
) {
}
