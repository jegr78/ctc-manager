package org.ctc.domain.service;

import org.ctc.domain.repository.RaceRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class DisqualificationResultRetentionIT extends RacePenaltyFixture {
    @Autowired RaceRepository raceRows;

    @Test
    void givenSavedDriverResults_whenCorrectingPositions_thenRetainConcreteResultIdentities() {
        // given
        var before = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getDriver().getId(), r -> r.getId())));
        // when
        races.saveResults(raceId, results(true));
        // then
        tx.executeWithoutResult(status -> {
            var after = raceRows.findById(raceId).orElseThrow().getResults();
            assertThat(after).hasSize(2).allSatisfy(r -> assertThat(r.getId()).isEqualTo(before.get(r.getDriver().getId())));
            assertThat(after).filteredOn(r -> r.getDriver().getId().equals(homeDriver))
                    .singleElement().satisfies(r -> { assertThat(r.getPosition()).isEqualTo(2); assertThat(r.getPointsRace()).isEqualTo(70); });
        });
        assertScores(70, 80);
    }
}
