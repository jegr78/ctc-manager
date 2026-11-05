package org.ctc.domain.model;

public record RacePenaltyTotals(TeamScore home, TeamScore away) {
    public record TeamScore(int gross, int penalty, long deduction, int official) {
        public String summary() {
            return "Gross " + gross + "; penalty total " + penalty + "; net deduction " + deduction + "; official " + official;
        }
    }

    public boolean hasPenalties() { return home.penalty() != 0 || away.penalty() != 0; }

    public static RacePenaltyTotals calculate(int homeGross, int awayGross, int homePenalty, int awayPenalty) {
        if (homePenalty < 0 || awayPenalty < 0) throw new IllegalArgumentException("Team penalty totals must be nonnegative");
        long difference = (long) homePenalty - awayPenalty;
        long homeDeduction = Math.max(difference, 0);
        long awayDeduction = Math.max(-difference, 0);
        return new RacePenaltyTotals(new TeamScore(homeGross, homePenalty, homeDeduction,
                (int) Math.max((long) homeGross - homeDeduction, 0)),
                new TeamScore(awayGross, awayPenalty, awayDeduction,
                        (int) Math.max((long) awayGross - awayDeduction, 0)));
    }
}
