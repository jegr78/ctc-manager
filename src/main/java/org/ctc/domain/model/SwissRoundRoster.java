package org.ctc.domain.model;

import java.util.*;

/** Concrete team identities sharing one owned phase/group competition place. */
public record SwissRoundRoster(Map<UUID, UUID> places, List<Team> teams) {
    public SwissRoundRoster { places=Map.copyOf(places); teams=List.copyOf(teams); }
    public static SwissRoundRoster from(Season season, List<PhaseTeam> rows) {
        var places=new LinkedHashMap<UUID,UUID>(); var teams=new LinkedHashMap<UUID,Team>();
        for (var row:rows) {
            var team=row.getTeam();
            if (team!=null && team.getId()!=null) { places.put(team.getId(),team.getId()); teams.put(team.getId(),team); }
        }
        for (var row:season.getSeasonTeams()) {
            Team active=activeOwnedTeam(season,row);
            if (active!=null && places.containsKey(active.getId()) && row.getTeam()!=null && row.getTeam().getId()!=null) {
                places.put(row.getTeam().getId(),active.getId()); teams.put(row.getTeam().getId(),row.getTeam());
            }
        }
        return new SwissRoundRoster(places,new ArrayList<>(teams.values()));
    }
    private static Team activeOwnedTeam(Season season, SeasonTeam start) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<SeasonTeam,Boolean>());
        var current=start;
        for (int depth=0; depth<=10; depth++) {
            if (current.getSeason()==null || !Objects.equals(season.getId(),current.getSeason().getId()) || !seen.add(current)) return null;
            if (current.getSuccessor()==null) return current.getTeam();
            current=current.getSuccessor();
        }
        return null;
    }
}
