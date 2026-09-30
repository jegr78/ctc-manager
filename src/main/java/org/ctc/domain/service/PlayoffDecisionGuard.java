package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;

/** Rejects result and lineup changes on races whose playoff matchup already has a winner. */
public final class PlayoffDecisionGuard {

	public static final String DECIDED =
			"The playoff matchup is decided. Reopen it before changing its results or lineups";

	private PlayoffDecisionGuard() {
	}

	public static void requireOpen(Race race) {
		requireOpen(race.getPlayoffMatchup());
	}

	public static void requireOpen(PlayoffMatchup matchup) {
		if (matchup != null && matchup.isComplete()) {
			throw new BusinessRuleException(DECIDED);
		}
	}
}
