package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import org.ctc.domain.model.MatchWalkoverRules;

public final class MatchWalkoverBackupValidator {
    private static final int MAX_VALUES = 100_000;
    private final HashSet<UUID> teams = new HashSet<>();
    private final ArrayList<Walkover> walkovers = new ArrayList<>();
    private int retained;

    private record Walkover(UUID id, UUID home, UUID away, UUID forfeiting, boolean bye) {}

    public void accept(String table, JsonNode row) {
        if (table.equals("teams")) {
            teams.add(MatchWalkoverReference.requiredId(row.get("id")));
            retained++;
        } else if (table.equals("matches")) {
            UUID forfeiting = MatchWalkoverReference.read(row.get("walkoverTeam"));
            if (forfeiting == null) return;
            if (!row.path("bye").isBoolean()) throw new IllegalArgumentException("Invalid match walkover bye flag");
            walkovers.add(new Walkover(MatchWalkoverReference.requiredId(row.get("id")),
                    MatchWalkoverReference.requiredId(row.get("homeTeam")),
                    MatchWalkoverReference.requiredId(row.get("awayTeam")), forfeiting, row.path("bye").booleanValue()));
            retained += 4;
        }
        if (retained > MAX_VALUES) throw new IllegalArgumentException("Match walkover validation exceeds the 100000-reference budget");
    }

    public void validate() {
        for (var walkover : walkovers) {
            if (!teams.contains(walkover.home()) || !teams.contains(walkover.away()) || !teams.contains(walkover.forfeiting())
                    || !MatchWalkoverRules.valid(walkover.forfeiting(), walkover.home(), walkover.away(), walkover.bye())) {
                throw new IllegalArgumentException("Invalid match walkover participants or team references");
            }
        }
    }
}
