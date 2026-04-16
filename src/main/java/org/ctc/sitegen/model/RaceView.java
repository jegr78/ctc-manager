package org.ctc.sitegen.model;

import java.util.List;

public record RaceView(String homeTeamShortName, String awayTeamShortName, String track, String car, int homeTotal,
                       int awayTotal, boolean hasResults, List<ResultView> results) {

	public String getScore() {
		return homeTotal + " : " + awayTotal;
	}

	public record ResultView(String driverPsnId, String teamShortName, int position, int qualiPosition,
	                         boolean fastestLap, int pointsTotal, String driverProfileUrl) {
	}
}
