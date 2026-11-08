package org.ctc.domain.model;

import java.util.*;

/** Whole-round rules shared by manual maintenance and read-only diagnostics. */
public final class SwissRoundRules {
    private SwissRoundRules() { }
    public record Row(int index, UUID homeTeamId, UUID awayTeamId, boolean bye) { }
    public record Issue(int index, String field, String message) { }
    public static List<Issue> validate(Map<UUID, UUID> places, List<Row> rows) {
        var errors = new ArrayList<Issue>();
        if (rows.isEmpty() || rows.size() > 256) {
            return List.of(new Issue(-1,"rows","Choose between 1 and 256 pairings for this round."));
        }
        var used = new HashSet<UUID>();
        var byes = rows.stream().filter(Row::bye).toList();
        boolean odd = new HashSet<>(places.values()).size() % 2 == 1;
        for (var row : rows) {
            UUID home = participant(places,row.homeTeamId(),row.index(),"homeTeamId",errors);
            UUID away = row.bye() && row.awayTeamId() == null ? null
                    : participant(places,row.awayTeamId(),row.index(),"awayTeamId",errors);
            if (row.bye()) {
                if (row.awayTeamId() != null) errors.add(new Issue(row.index(),"awayTeamId","A bye must have no away team."));
                if (!odd || byes.size() > 1) errors.add(new Issue(row.index(),"bye","Use at most one bye, only with an odd number of phase/group places."));
            }
            if (home != null && home.equals(away)) errors.add(new Issue(row.index(),"awayTeamId","A team cannot play itself or its replacement."));
            if (home != null && !used.add(home)) errors.add(new Issue(row.index(),"homeTeamId","Each team place can appear only once in a round."));
            if (away != null && !used.add(away)) errors.add(new Issue(row.index(),"awayTeamId","Each team place can appear only once in a round."));
        }
        return List.copyOf(errors);
    }
    private static UUID participant(Map<UUID,UUID> places, UUID id, int row, String field, List<Issue> errors) {
        if (id == null) { errors.add(new Issue(row,field,"Choose a team.")); return null; }
        UUID place = places.get(id);
        if (place == null) errors.add(new Issue(row,field,"Choose a team from this phase/group."));
        return place;
    }
}
