package org.ctc.domain.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DisqualificationScoringTest {
    private final ScoringService scoring = new ScoringService(mock(MatchRepository.class), mock(PickWindowService.class),
            mock(PlayoffMatchupRepository.class), mock(RaceLineupRepository.class), mock(RaceRepository.class));
    private final RaceScoring scale = new RaceScoring("Test DSQ", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);

    @Test
    void givenThirdDriverDisqualified_whenScoring_thenAdvanceFollowingDriversAndRetainRecordedPositions() {
        // given
        var race = race(Set.of(3));
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertRacePoints(race, 20, 17, 0, 14, 12, 10, 8, 7, 6, 5, 4, 3);
        assertThat(race.getResults()).extracting(RaceResult::getPosition).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void givenThirdAndSeventhDriversDisqualified_whenScoring_thenRemoveBothFromRacePoints() {
        // given
        var race = race(Set.of(3, 7));
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertRacePoints(race, 20, 17, 0, 14, 12, 10, 0, 8, 7, 6, 5, 4);
    }

    @Test
    void givenDisqualifiedLeaderWithQualifyingAndFastestLap_whenScoring_thenKeepBothBonuses() {
        // given
        var race = race(Set.of(1));
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        var leader = result(race, 1);
        assertThat(leader.getPointsRace()).isZero();
        assertThat(leader.getPointsQuali()).isEqualTo(3);
        assertThat(leader.getPointsFl()).isEqualTo(2);
        assertThat(leader.getPointsTotal()).isEqualTo(5);
        assertThat(result(race, 2).getPointsRace()).isEqualTo(20);
    }

    @Test
    void givenEveryDriverDisqualified_whenScoring_thenKeepBonusesWithoutRacePoints() {
        // given
        var race = race(Set.copyOf(IntStream.rangeClosed(1, 12).boxed().toList()));
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertThat(race.getResults()).allSatisfy(row -> assertThat(row.getPointsRace()).isZero());
        assertThat(result(race, 1).getPointsTotal()).isEqualTo(5);
        assertThat(result(race, 2).getPointsTotal()).isEqualTo(2);
        assertThat(result(race, 3).getPointsTotal()).isEqualTo(1);
    }

    @Test
    void givenDisqualificationsCleared_whenRecalculating_thenRestoreTheOriginalPoints() {
        // given
        var race = race(Set.of(3, 7));
        scoring.calculatePoints(race.getResults(), scale);
        race.getResults().forEach(row -> row.setDisqualified(false));
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertRacePoints(race, 20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2);
    }

    @Test
    void givenRecordedPositionGapsWithoutDisqualification_whenScoring_thenPreserveHistoricalPoints() {
        // given
        var race = race(Set.of(), 1, 4, 9);
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertThat(result(race, 4).getPointsRace()).isEqualTo(12);
        assertThat(result(race, 9).getPointsRace()).isEqualTo(5);
    }

    @Test
    void givenRecordedGapsAndOneDisqualification_whenScoring_thenShiftOnlyForTheRemovedDriver() {
        // given
        var race = race(Set.of(1), 1, 4, 9);
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertThat(result(race, 1).getPointsRace()).isZero();
        assertThat(result(race, 4).getPointsRace()).isEqualTo(14);
        assertThat(result(race, 9).getPointsRace()).isEqualTo(6);
    }

    @Test
    void givenACompleteRaceWithDisqualification_whenCalculatingOneResult_thenUseTheSameClassification() {
        // given
        var race = race(Set.of(3));
        // when
        scoring.calculatePoints(result(race, 5), scale);
        // then
        assertThat(result(race, 5).getPointsRace()).isEqualTo(12);
    }

    @Test
    void givenAnotherRaceWithDisqualifiedDrivers_whenScoring_thenKeepTheCurrentRaceIndependent() {
        // given
        race(Set.of(1, 2, 3));
        var race = race(Set.of());
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertRacePoints(race, 20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2);
    }

    @Test
    void givenResultsInReverseInputOrder_whenScoring_thenUseTheRecordedFinishingOrder() {
        // given
        var race = race(Set.of(3));
        Collections.reverse(race.getResults());
        List<Integer> recordedOrder = race.getResults().stream().map(RaceResult::getPosition).toList();
        // when
        scoring.calculatePoints(race.getResults(), scale);
        // then
        assertThat(result(race, 3).getPointsRace()).isZero();
        assertThat(result(race, 4).getPointsRace()).isEqualTo(14);
        assertThat(race.getResults()).extracting(RaceResult::getPosition).containsExactlyElementsOf(recordedOrder);
    }

    private Race race(Set<Integer> disqualified, int... positions) {
        var race = new Race(); race.setId(UUID.randomUUID());
        int[] ordered = positions.length == 0 ? IntStream.rangeClosed(1, 12).toArray() : positions;
        for (int position : ordered) {
            var result = new RaceResult(race, new Driver("test-p" + position, "Test driver " + position),
                    position, position, position == 1);
            result.setDisqualified(disqualified.contains(position));
            race.getResults().add(result);
        }
        return race;
    }

    private static RaceResult result(Race race, int position) {
        return race.getResults().stream().filter(row -> row.getPosition() == position).findFirst().orElseThrow();
    }

    private static void assertRacePoints(Race race, int... expected) {
        for (int i = 0; i < expected.length; i++) assertThat(result(race, i + 1).getPointsRace()).as("recorded P%s", i + 1).isEqualTo(expected[i]);
    }

}
