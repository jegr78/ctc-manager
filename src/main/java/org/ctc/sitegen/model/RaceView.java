package org.ctc.sitegen.model;

import java.util.List;

public record RaceView(String homeTeamShortName, String awayTeamShortName, String track, String car, int homeTotal,
                       int awayTotal, boolean hasResults,
                       boolean homeTeamWon, boolean awayTeamWon,
                       List<ResultView> results, String poolEntryLabel, org.ctc.domain.model.RacePenaltyTotals penaltyTotals) {

    public boolean isTeamOnlyResult() { return hasResults && results.isEmpty(); }

	public String getScore() {
		return homeTotal + " : " + awayTotal;
	}

	public record ResultView(String driverPsnId, String teamShortName, String scoringTeamShortName,
	                         int position, int qualiPosition,
	                         boolean fastestLap, int pointsTotal, String driverProfileUrl, boolean disqualified, int recordedPosition) {
        public ResultView(String driverPsnId, String teamShortName, String scoringTeamShortName, int position, int qualiPosition,
                          boolean fastestLap, int pointsTotal, String driverProfileUrl) {
            this(driverPsnId, teamShortName, scoringTeamShortName, position, qualiPosition, fastestLap, pointsTotal, driverProfileUrl, false, position);
        }
        public String getClassificationLabel() { return disqualified ? "DSQ" : Integer.toString(position); }
	}
}
