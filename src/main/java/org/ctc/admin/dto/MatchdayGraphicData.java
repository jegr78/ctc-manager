package org.ctc.admin.dto;

import java.util.List;

public record MatchdayGraphicData(
		String matchdayLabel,
		String seasonName,
		String seasonYear,
		String ctcLogoBase64,
		String fontBase64,
		List<MatchGraphicRow> matches
) {

	public record MatchGraphicRow(
			String homeTeamName,
			String homeTeamShortName,
			String homeLogoBase64,
			String homePrimaryColor,
			String homeSecondaryColor,
			String homeAccentColor,
			int homeSeed,
			String homeRecord,
			String awayTeamName,
			String awayTeamShortName,
			String awayLogoBase64,
			String awayPrimaryColor,
			String awaySecondaryColor,
			String awayAccentColor,
			int awaySeed,
			String awayRecord,
			String scheduledDateTime,
			Integer homeScore,
			Integer awayScore
	) {
	}
}
