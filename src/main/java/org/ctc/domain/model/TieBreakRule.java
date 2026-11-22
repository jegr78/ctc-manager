package org.ctc.domain.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Awards a tied pairing to the side whose driver wins the first leg that has driver results. */
public final class TieBreakRule {
    private TieBreakRule() { }

    public enum Side { HOME, AWAY }

    public record Decision(Side side, Race race) { }

    public static final Comparator<Race> LEG_ORDER = Comparator
            .comparing(Race::getDateTime, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(Race::getId, Comparator.nullsLast(Comparator.<UUID>naturalOrder()));

    public static Optional<Decision> decide(List<Race> legs, Predicate<RaceResult> scoresForHome) {
        return legs.stream().sorted(LEG_ORDER)
                .filter(leg -> !leg.getResults().isEmpty())
                .findFirst()
                .flatMap(leg -> leg.getResults().stream()
                        .filter(result -> !result.isDisqualified())
                        .min(Comparator.comparingInt(result -> RaceClassification.position(result, leg.getResults())))
                        .map(winner -> new Decision(scoresForHome.test(winner) ? Side.HOME : Side.AWAY, leg)));
    }

    /** 1-based position of {@code leg} in leg order. */
    public static int legNumber(List<Race> legs, Race leg) {
        return legs.stream().sorted(LEG_ORDER).toList().indexOf(leg) + 1;
    }
}
