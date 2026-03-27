package de.ctc.sitegen.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class RaceView {

    private final String homeTeamShortName;
    private final String awayTeamShortName;
    private final String track;
    private final String car;
    private final int homeTotal;
    private final int awayTotal;
    private final boolean hasResults;
    private final List<ResultView> results;

    public String getScore() {
        return homeTotal + " : " + awayTotal;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ResultView {
        private final String driverPsnId;
        private final String teamShortName;
        private final int position;
        private final int qualiPosition;
        private final boolean fastestLap;
        private final int pointsTotal;
    }
}
