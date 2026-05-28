package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class MatchUpdatedAtNoopSaveIT {

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TestHelper helper;

	@Test
	void givenPersistedMatch_whenNoopSaveCalled_thenUpdatedAtUnchanged() throws InterruptedException {
		// given — persist a Match
		Season season = helper.createSeason("NoopSave Season");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-NS-1", 0);
		Team home = helper.createTeam("NS Home", "ns-h");
		Team away = helper.createTeam("NS Away", "ns-a");
		Match match = helper.createMatch(md, home, away);
		matchRepository.flush();
		java.time.LocalDateTime original = match.getUpdatedAt();
		assertThat(original).as("Sanity: match.updatedAt is populated after create").isNotNull();

		// when — wait a tick then re-load + save without any setter call
		Thread.sleep(10);
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		Match saved = matchRepository.save(reloaded);
		matchRepository.flush();

		// then — RESEARCH Pitfall 4 / Assumption A1 EMPIRICALLY FALSIFIED:
		// matchRepository.save(match) DOES advance updatedAt even on a no-op save
		// (Spring Data JPA's @LastModifiedDate fires on every merge, not only on
		// dirty fields). The MATCH_RESULTS stale-detection therefore CANNOT use
		// match.updatedAt vs post.updatedAt — switched to comparing against the
		// max RaceResult.updatedAt per Pitfall 4 fallback. This test pins the
		// empirical behavior so future migrations know not to revisit Assumption A1.
		assertThat(saved.getUpdatedAt())
				.as("Pitfall 4 verdict: no-op save DOES advance updatedAt (Assumption A1 falsified)")
				.isAfter(original);
	}
}
