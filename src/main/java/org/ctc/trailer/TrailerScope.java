package org.ctc.trailer;

import java.util.UUID;

/** What a trailer covers: one regular matchday or one playoff round. */
public sealed interface TrailerScope {

    String key();

    record MatchdayScope(UUID matchdayId) implements TrailerScope {
        @Override
        public String key() {
            return "matchday:" + matchdayId;
        }
    }

    record PlayoffRoundScope(UUID roundId) implements TrailerScope {
        @Override
        public String key() {
            return "round:" + roundId;
        }
    }
}
