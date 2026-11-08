package org.ctc.domain.model;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class SwissRoundRulesTest {
    static final UUID A=id(1), B=id(2), C=id(3), D=id(4), E=id(5), OLD_A=id(6), FOREIGN=id(99);
    static UUID id(int value) { return new UUID(0, value); }
    static Map<UUID,UUID> places() { return Map.of(A,A,B,B,C,C,D,D,OLD_A,A); }
    static SwissRoundRules.Row row(int index, UUID home, UUID away, boolean bye) { return new SwissRoundRules.Row(index,home,away,bye); }

    @Test void givenEvenRoundWithHistoricalTeamIdentity_whenValidating_thenKeepActualIdentityAndAcceptDistinctPlaces() {
        var rows = List.of(row(0,OLD_A,B,false),row(1,C,D,false));
        assertThat(SwissRoundRules.validate(places(), rows)).isEmpty();
        assertThat(rows.getFirst().homeTeamId()).isEqualTo(OLD_A);
    }
    @Test void givenOddRosterWithOneBye_whenValidating_thenAcceptAndKeepMissingParticipantsForTheEditor() {
        var places = new HashMap<>(places()); places.put(E,E);
        assertThat(SwissRoundRules.validate(places,List.of(row(0,A,B,false),row(1,C,D,false),row(2,E,null,true)))).isEmpty();
        assertThat(SwissRoundRules.validate(places,List.of(row(0,A,B,false)))).isEmpty();
    }
    static Stream<Arguments> invalidRounds() {
        return Stream.of(
                Arguments.of(List.of(row(7,A,A,false)),"awayTeamId"),
                Arguments.of(List.of(row(0,A,B,false),row(8,A,C,false)),"homeTeamId"),
                Arguments.of(List.of(row(0,A,B,false),row(8,C,B,false)),"awayTeamId"),
                Arguments.of(List.of(row(0,A,B,false),row(8,B,A,false)),"homeTeamId"),
                Arguments.of(List.of(row(8,FOREIGN,B,false)),"homeTeamId"),
                Arguments.of(List.of(row(8,A,FOREIGN,false)),"awayTeamId"),
                Arguments.of(List.of(row(8,null,B,false)),"homeTeamId"),
                Arguments.of(List.of(row(8,A,null,false)),"awayTeamId"),
                Arguments.of(List.of(row(8,A,B,true)),"awayTeamId"),
                Arguments.of(List.of(row(8,A,null,true)),"bye"),
                Arguments.of(List.of(row(0,OLD_A,B,false),row(8,A,C,false)),"homeTeamId"),
                Arguments.of(List.of(row(8,OLD_A,A,false)),"awayTeamId"));
    }
    @ParameterizedTest @MethodSource("invalidRounds")
    void givenInvalidRound_whenValidating_thenReportTheSubmittedRowAndField(List<SwissRoundRules.Row> rows, String field) {
        assertThat(SwissRoundRules.validate(places(),rows)).anySatisfy(issue -> {
            assertThat(issue.index()).isEqualTo(rows.getLast().index());
            assertThat(issue.field()).isEqualTo(field); assertThat(issue.message()).isNotBlank();
        });
    }
    @Test void givenTwoByesOrEmptyOrExcessiveRound_whenValidating_thenRejectWithoutAcceptingAPartialWrite() {
        var places = new HashMap<>(places()); places.put(E,E);
        assertThat(SwissRoundRules.validate(places,List.of(row(0,A,null,true),row(1,B,null,true))))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("bye"));
        assertThat(SwissRoundRules.validate(places,List.of())).anySatisfy(issue -> assertThat(issue.field()).isEqualTo("rows"));
        assertThat(SwissRoundRules.validate(places,Collections.nCopies(257,row(0,A,B,false))))
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("rows"));
    }
}
