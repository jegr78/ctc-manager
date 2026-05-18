package org.ctc.admin.dto;

import java.util.List;
import java.util.Map;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;

public record SeasonDriverGroupDto(
		Season season,
		Map<Team, List<Driver>> driversByTeam
) {
	public int totalDriverCount() {
		return driversByTeam.values().stream().mapToInt(List::size).sum();
	}
}
