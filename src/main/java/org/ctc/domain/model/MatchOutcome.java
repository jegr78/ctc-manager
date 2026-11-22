package org.ctc.domain.model;

/** Result of a played pairing; {@code winner} is null for a draw. */
public record MatchOutcome(Team winner, Team loser, boolean tieBreak) {

    public static MatchOutcome draw() {
        return new MatchOutcome(null, null, false);
    }

    public boolean isDraw() {
        return winner == null;
    }

    public boolean isWinner(Team team) {
        return winner != null && team != null && winner.getId().equals(team.getId());
    }

    public boolean isLoser(Team team) {
        return loser != null && team != null && loser.getId().equals(team.getId());
    }
}
