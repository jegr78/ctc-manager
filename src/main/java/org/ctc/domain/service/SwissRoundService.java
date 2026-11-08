package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.SwissRoundForm;
import org.ctc.domain.exception.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.util.StringUtils.hasText;

@Service @RequiredArgsConstructor
public class SwissRoundService {
    private final SeasonPhaseRepository phases;
    private final SeasonPhaseGroupRepository groups;
    private final PhaseTeamRepository roster;
    private final MatchdayRepository days;
    private final MatchRepository matches;
    private final RaceRepository races;
    private final TeamRepository teams;
    private final EntityManager entities;
    private final SwissRoundProtection protection;
    private final ScoreWriteLockService locks;
    private final PickWriteExecutor writes;
    private final PickSessionEpoch epoch;
    private final RaceCarSelectionService selections;
    private final SwissPairingService automatic;

    public record Choice(UUID id, String label, UUID placeId) { }
    public record PairingSummary(UUID id, String home, String away, String lockedReason) { }
    public record RoundSummary(UUID id, String label, int number, List<PairingSummary> pairings) { }
    public record Workspace(UUID phaseId, UUID seasonId, String seasonLabel, String phaseLabel, UUID groupId,
                            List<Choice> phases, List<Choice> groups, List<Choice> teams, List<RoundSummary> rounds,
                            boolean chooseGroup, boolean canCreate, boolean canGenerate, String scope, String generation) { }
    public record Editor(Workspace workspace, SwissRoundForm form, List<Choice> choices, Map<UUID,String> lockedRows,
                         List<Choice> missing, String missingLabel, List<String> warnings, String readOnlyReason, Set<String> previousPairs, Set<UUID> previousByes) { }
    public record SavedRound(UUID id, List<String> warnings) { }
    private record History(Set<String> pairs, Set<UUID> byes) { }
    private record Scope(SeasonPhase phase, SeasonPhaseGroup group, List<Matchday> days, List<Match> matches,
                         Map<UUID,List<Race>> legs, SwissRoundRoster roster, Map<UUID,String> reasons, UUID targetDayId, List<Race> targetRaces) { }
    private record PlannedRow(int index, SwissRoundForm.Pairing input, Match existing, boolean changed, List<Leg> legs) { }
    private record Leg(Race existing, boolean reversed) { }

    @Transactional(readOnly=true)
    public Workspace workspace(UUID phaseId, UUID groupId) {
        String generation=epoch.managementGeneration(); var scope=load(phaseId,groupId,false,null);
        return workspace(scope,generation);
    }

    @Transactional(readOnly=true)
    public Editor editor(UUID phaseId, UUID groupId, UUID dayId, SwissRoundForm rejected) {
        String generation=epoch.managementGeneration(); var scope=load(phaseId,groupId,true,dayId);
        Matchday day=day(scope,dayId);
        if (day==null) requireCreation(scope);
        var form=rejected==null ? form(scope,day,generation) : rejected;
        var choices=new ArrayList<>(choices(scope));
        var known=choices.stream().map(Choice::id).collect(Collectors.toSet());
        var retained=new HashSet<UUID>();
        for (var row:form.getRows()) if (row!=null) { if (row.getHomeTeamId()!=null) retained.add(row.getHomeTeamId()); if (row.getAwayTeamId()!=null) retained.add(row.getAwayTeamId()); }
        retained.removeAll(known);
        for (var team:teams.findAllById(retained)) choices.add(new Choice(team.getId(),label(team)+" (outside this phase/group)",null));
        var found=choices.stream().map(Choice::id).collect(Collectors.toSet());
        for (var id:retained) if (!found.contains(id)) choices.add(new Choice(id,"Previously selected team is unavailable",null));
        var used=new HashSet<UUID>();
        for (var row:form.getRows()) if (row!=null && !row.isRemove()) {
            if (row.getHomeTeamId()!=null) used.add(scope.roster().places().get(row.getHomeTeamId()));
            if (!row.isBye() && row.getAwayTeamId()!=null) used.add(place(scope,row.getAwayTeamId()));
        }
        var missing=choices(scope).stream().filter(c -> c.id().equals(c.placeId()) && !used.contains(c.id())).toList();
        return new Editor(workspace(scope,generation),form,List.copyOf(choices),scope.reasons(),missing,missing.isEmpty()?"All places are assigned.":missing.stream().map(Choice::label).collect(Collectors.joining(", ")),
                warnings(scope,day,form),readOnlyReason(scope,day),history(scope,day).pairs(),history(scope,day).byes());
    }

    public UUID save(SwissRoundForm submitted) { return saveWithWarnings(submitted).id(); }

    public SavedRound saveWithWarnings(SwissRoundForm submitted) {
        var form=copy(submitted); validateHeader(form);
        return writes.executeReadCommitted(() -> {
            requireGeneration(form.getGeneration());
            locks.lockPhase(form.getPhaseId()); entities.clear();
            var scope=load(form.getPhaseId(),form.getGroupId(),true,form.getMatchdayId());
            requireSnapshot(scope,form.getScope());
            Matchday day=day(scope,form.getMatchdayId());
            int ordinal=day==null ? scope.days().size()+1 : scope.days().indexOf(day)+1;
            if (form.getRoundNumber()!=ordinal) throw new BusinessRuleException("The round order changed. Reload before saving.");
            if (day==null) requireCreation(scope);
            String readOnly=readOnlyReason(scope,day);
            if (readOnly!=null) throw new BusinessRuleException(readOnly);
            var planned=plan(scope,day,form);
            var retained=planned.stream().map(PlannedRow::existing).filter(Objects::nonNull).map(Match::getId).collect(Collectors.toSet());
            UUID targetId=day==null?null:day.getId();
            var removed=scope.matches().stream().filter(m -> targetId!=null && m.getMatchday().getId().equals(targetId) && !retained.contains(m.getId())).toList();
            for (var old:removed) if (scope.reasons().containsKey(old.getId()))
                throw new BusinessRuleException("Keep the locked pairing "+label(old.getHomeTeam())+". "+scope.reasons().get(old.getId())+" must retain its identity.");
            var assignments=new ArrayList<RaceCarSelectionService.Assignment>();
            for (var row:planned) if (row.changed()) {
                var kept=new HashSet<UUID>();
                for (var leg:row.legs()) if (leg.existing()!=null) {
                    var race=leg.existing(); kept.add(race.getId());
                    assignments.add(new RaceCarSelectionService.Assignment(race.getId(),leg.reversed()?row.input().getAwayTeamId():row.input().getHomeTeamId(),
                            row.input().isBye(),race.getCarPoolEntry()==null?null:race.getCarPoolEntry().getId(),race.getCar()==null?null:race.getCar().getId()));
                }
                if (row.existing()!=null) for (var race:scope.legs().get(row.existing().getId())) if (!kept.contains(race.getId())) assignments.add(cleared(race));
            }
            for (var old:removed) for (var race:scope.legs().get(old.getId())) assignments.add(cleared(race));
            var validation=selections.validateAssignments(scope.phase().getSeason().getId(),assignments);
            if (!validation.valid()) throw new BusinessRuleException(validation.message());
            if (day==null) {
                day=days.save(new Matchday(scope.phase(),form.getLabel().strip(),appendIndex(scope.days())));
                day.setGroup(scope.group());
            } else day.setLabel(form.getLabel().strip());
            for (var old:removed) remove(day,old);
            for (var row:planned) apply(scope,day,row);
            entities.flush(); return new SavedRound(day.getId(),warnings(scope,day,form));
        });
    }

    public UUID generate(UUID phaseId, UUID groupId, String generation, String expectedScope) {
        return writes.executeReadCommitted(() -> {
            requireGeneration(generation); locks.lockPhase(phaseId); entities.clear();
            var scope=load(phaseId,groupId,true,null); requireSnapshot(scope,expectedScope); requireCreation(scope);
            return automatic.generateNextRound(phaseId,groupId).getId();
        });
    }

    private Scope load(UUID phaseId, UUID groupId, boolean requireGroup, UUID targetDayId) {
        var phase=phases.findById(phaseId).orElseThrow(() -> new EntityNotFoundException("SeasonPhase",phaseId));
        if (phase.getFormat()!=SeasonFormat.SWISS || (phase.getLayout()!=PhaseLayout.LEAGUE && phase.getLayout()!=PhaseLayout.GROUPS))
            throw new BusinessRuleException("Choose a Swiss league or group phase.");
        if (phase.getLayout()==PhaseLayout.LEAGUE && groupId!=null) throw new BusinessRuleException("A league phase has no group selection.");
        if (phase.getLayout()==PhaseLayout.GROUPS && requireGroup && groupId==null) throw new BusinessRuleException("Choose the concrete group first.");
        SeasonPhaseGroup group=null;
        if (groupId!=null) {
            group=groups.findById(groupId).orElseThrow(() -> new EntityNotFoundException("SeasonPhaseGroup",groupId));
            if (!phaseId.equals(group.getPhase().getId())) throw new BusinessRuleException("The group belongs to another phase.");
        }
        boolean pending=phase.getLayout()==PhaseLayout.GROUPS && group==null;
        var roundDays=pending?List.<Matchday>of():group==null?days.findByPhaseIdOrderBySortIndexAsc(phaseId):days.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phaseId,groupId);
        if (targetDayId!=null && roundDays.stream().noneMatch(d -> targetDayId.equals(d.getId())))
            throw new BusinessRuleException("This round does not belong to the selected phase/group. Reload its workspace.");
        var rows=new ArrayList<Match>();
        for (var day:roundDays) rows.addAll(matches.findByMatchdayId(day.getId()));
        rows.sort(Comparator.comparing(Match::getId));
        var legs=new LinkedHashMap<UUID,List<Race>>(); var reasons=new HashMap<UUID,String>();
        for (var match:rows) {
            if (!match.getMatchday().getId().equals(targetDayId)) continue;
            var children=races.findByMatchId(match.getId()).stream().sorted(Comparator.comparing(Race::getId)).toList();
            legs.put(match.getId(),children); String reason=protection.reason(match,children);
            if (reason!=null) reasons.put(match.getId(),reason);
        }
        var members=pending?List.<PhaseTeam>of():group==null?roster.findByPhaseId(phaseId):roster.findByPhaseIdAndGroupId(phaseId,groupId);
        return new Scope(phase,group,List.copyOf(roundDays),List.copyOf(rows),legs,SwissRoundRoster.from(phase.getSeason(),members),Map.copyOf(reasons),targetDayId,targetDayId==null?List.of():races.findByMatchdayId(targetDayId));
    }

    private Workspace workspace(Scope scope, String generation) {
        var phase=scope.phase(); var season=phase.getSeason();
        boolean pending=phase.getLayout()==PhaseLayout.GROUPS && scope.group()==null;
        boolean create=!pending && !scope.roster().places().isEmpty()
                && (phase.getTotalRounds()==null || scope.days().size()<phase.getTotalRounds());
        var rounds=new ArrayList<RoundSummary>();
        for (int i=0;i<scope.days().size();i++) {
            var day=scope.days().get(i);
            var pairings=scope.matches().stream().filter(m -> m.getMatchday().getId().equals(day.getId()))
                    .map(m -> new PairingSummary(m.getId(),label(m.getHomeTeam()),m.isBye()?"Bye":label(m.getAwayTeam()),scope.reasons().get(m.getId()))).toList();
            rounds.add(new RoundSummary(day.getId(),day.getLabel(),i+1,pairings));
        }
        var phaseChoices=phases.findBySeasonIdOrderBySortIndex(season.getId()).stream()
                .filter(p -> p.getFormat()==SeasonFormat.SWISS && (p.getLayout()==PhaseLayout.LEAGUE || p.getLayout()==PhaseLayout.GROUPS))
                .map(p -> new Choice(p.getId(),phaseLabel(p),null)).toList();
        var groupChoices=phase.getLayout()==PhaseLayout.GROUPS
                ? groups.findByPhaseIdOrderBySortIndex(phase.getId()).stream().map(g -> new Choice(g.getId(),g.getName(),null)).toList() : List.<Choice>of();
        return new Workspace(phase.getId(),season.getId(),season.getDisplayLabel(),phaseLabel(phase),scope.group()==null?null:scope.group().getId(),
                phaseChoices,groupChoices,choices(scope),List.copyOf(rounds),pending,create,
                create && automatic.isCurrentRoundComplete(phase.getId(),scope.group()==null?null:scope.group().getId()),
                snapshot(scope,generation),generation);
    }

    private List<Choice> choices(Scope scope) {
        return scope.roster().teams().stream().sorted(Comparator.comparing(SwissRoundService::label,String.CASE_INSENSITIVE_ORDER).thenComparing(Team::getId))
                .map(t -> new Choice(t.getId(),label(t)+(t.getId().equals(scope.roster().places().get(t.getId()))?"":" (historical predecessor)"),scope.roster().places().get(t.getId()))).toList();
    }
    private SwissRoundForm form(Scope scope, Matchday day, String generation) {
        var form=new SwissRoundForm(); form.setPhaseId(scope.phase().getId()); form.setGroupId(scope.group()==null?null:scope.group().getId());
        form.setMatchdayId(day==null?null:day.getId()); form.setRoundNumber(day==null?scope.days().size()+1:scope.days().indexOf(day)+1);
        form.setLabel(day==null?"Round "+form.getRoundNumber():day.getLabel()); form.setGeneration(generation); form.setScope(snapshot(scope,generation));
        if (day!=null) for (var match:scope.matches()) if (match.getMatchday().getId().equals(day.getId())) {
            var row=new SwissRoundForm.Pairing(); row.setMatchId(match.getId()); row.setHomeTeamId(id(match.getHomeTeam()));
            row.setAwayTeamId(id(match.getAwayTeam())); row.setBye(match.isBye()); form.getRows().add(row);
        }
        int places=new HashSet<>(scope.roster().places().values()).size();
        int capacity=Math.min(256,Math.max(1,form.getRows().size()+(places+1)/2));
        while (form.getRows().size()<capacity) form.getRows().add(new SwissRoundForm.Pairing());
        return form;
    }
    private Matchday day(Scope scope, UUID id) {
        if (id==null) return null;
        return scope.days().stream().filter(d -> id.equals(d.getId())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("This round does not belong to the selected phase/group. Reload its workspace."));
    }
    private List<PlannedRow> plan(Scope scope, Matchday day, SwissRoundForm form) {
        var existing=scope.matches().stream().filter(m -> day!=null && m.getMatchday().getId().equals(day.getId())).toList();
        var byId=existing.stream().collect(Collectors.toMap(Match::getId,m -> m));
        var errors=new ArrayList<SwissRoundValidationException.FieldIssue>(); var seenIds=new HashSet<UUID>();
        var active=new ArrayList<Integer>(); var explicit=new HashSet<UUID>();
        for (int i=0;i<form.getRows().size();i++) {
            var row=form.getRows().get(i);
            if (row==null) { errors.add(issue("rows","A pairing row is missing. Reload the form.")); continue; }
            if (row.getMatchId()!=null && (!byId.containsKey(row.getMatchId()) || !seenIds.add(row.getMatchId())))
                errors.add(issue("rows["+i+"].matchId","Choose each existing pairing only once, from this round."));
            if (row.isRemove() || row.blank()) continue;
            active.add(i); if (row.getMatchId()!=null) explicit.add(row.getMatchId());
        }
        var rows=active.stream().map(i -> {
            var row=form.getRows().get(i); return new SwissRoundRules.Row(i,row.getHomeTeamId(),row.getAwayTeamId(),row.isBye());
        }).toList();
        for (var error:SwissRoundRules.validate(scope.roster().places(),rows))
            errors.add(issue(error.index()<0?error.field():"rows["+error.index()+"]."+error.field(),error.message()));
        if (!errors.isEmpty()) throw new SwissRoundValidationException(errors);
        var claimed=new HashSet<>(explicit); var plans=new ArrayList<PlannedRow>();
        for (int index:active) {
            var row=form.getRows().get(index); Match match=row.getMatchId()==null?null:byId.get(row.getMatchId());
            if (match==null) {
                match=existing.stream().filter(m -> !claimed.contains(m.getId()) && same(m,row)).findFirst().orElse(null);
                if (match!=null) claimed.add(match.getId());
            }
            boolean changed=match==null || !same(match,row);
            if (changed && match!=null && scope.reasons().containsKey(match.getId()))
                errors.add(issue("rows["+index+"].matchId","Keep this locked pairing unchanged. "+scope.reasons().get(match.getId())+" must retain its identity."));
            var legs=changed?legs(match==null?List.of():scope.legs().get(match.getId()),row.isBye()?1:Math.max(1,scope.phase().getLegs())):List.<Leg>of();
            plans.add(new PlannedRow(index,row,match,changed,legs));
        }
        if (!errors.isEmpty()) throw new SwissRoundValidationException(errors);
        return List.copyOf(plans);
    }
    private List<Leg> legs(List<Race> existing, int count) {
        var unused=new ArrayList<>(existing); var plan=new ArrayList<Leg>();
        unused.sort(Comparator.comparing(Race::getCreatedAt,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Race::getId));
        for (int i=0;i<count;i++) {
            boolean reversed=i%2!=0;
            Race retained=unused.stream().filter(r -> r.hasTeamOverrides()==reversed).findFirst().orElse(unused.isEmpty()?null:unused.getFirst());
            if (retained!=null) unused.remove(retained);
            plan.add(new Leg(retained,reversed));
        }
        return List.copyOf(plan);
    }
    private void apply(Scope scope, Matchday day, PlannedRow plan) {
        if (!plan.changed()) return;
        var input=plan.input(); var byId=scope.roster().teams().stream().collect(Collectors.toMap(Team::getId,t -> t));
        var home=byId.get(input.getHomeTeamId()); var away=input.isBye()?null:byId.get(input.getAwayTeamId());
        Match match=plan.existing();
        if (match==null) { match=matches.save(new Match(day,home,away)); day.getMatches().add(match); }
        match.setHomeTeam(home); match.setAwayTeam(away); match.setBye(input.isBye());
        var retained=plan.legs().stream().map(Leg::existing).filter(Objects::nonNull).map(Race::getId).collect(Collectors.toSet());
        for (var old:new ArrayList<>(match.getRaces())) if (!retained.contains(old.getId())) {
            match.getRaces().remove(old); day.getRaces().remove(old); entities.remove(old);
        }
        for (var leg:plan.legs()) {
            var race=leg.existing();
            if (race==null) { race=new Race(); race.setMatchday(day); race.setMatch(match); races.save(race); match.getRaces().add(race); day.getRaces().add(race); }
            race.setHomeTeamOverride(leg.reversed()?away:null); race.setAwayTeamOverride(leg.reversed()?home:null);
            race.enrolForPicksIfEnabled();
        }
    }
    private void remove(Matchday day, Match match) {
        day.getMatches().remove(match); day.getRaces().removeIf(r -> r.getMatch()!=null && match.getId().equals(r.getMatch().getId())); entities.remove(match);
    }
    private History history(Scope scope, Matchday day) {
        int before=day==null?Integer.MAX_VALUE:day.getSortIndex(); var played=new HashSet<String>(); var byes=new HashSet<UUID>();
        for (var match:scope.matches()) if (match.getMatchday().getSortIndex()<before) {
            UUID home=place(scope,id(match.getHomeTeam())), away=place(scope,id(match.getAwayTeam()));
            if (match.isBye() && home!=null) byes.add(home); else if (!match.isBye() && home!=null && away!=null) played.add(pair(home,away));
        }
        return new History(Set.copyOf(played),Set.copyOf(byes));
    }
    private List<String> warnings(Scope scope, Matchday day, SwissRoundForm form) {
        var history=history(scope,day); var played=history.pairs(); var byes=history.byes();
        var warnings=new ArrayList<String>();
        for (int i=0;i<form.getRows().size();i++) {
            var row=form.getRows().get(i); if (row==null || row.isRemove() || row.getHomeTeamId()==null) continue;
            UUID home=scope.roster().places().get(row.getHomeTeamId()), away=place(scope,row.getAwayTeamId());
            if (home==null) continue;
            if (row.isBye() && byes.contains(home)) warnings.add("Pairing "+(i+1)+": this team has received a bye before. Keep it if this is the actual historical pairing.");
            else if (away!=null && played.contains(pair(home,away))) warnings.add("Pairing "+(i+1)+": these teams have played before. Historical rematches are allowed.");
        }
        return List.copyOf(warnings);
    }
    private String readOnlyReason(Scope scope, Matchday day) {
        if (day==null) return null;
        if (scope.matches().stream().filter(m -> m.getMatchday().getId().equals(day.getId())).count()>256)
            return "This round has more than 256 pairings. Use the matchday workspace first.";
        return scope.targetRaces().stream().anyMatch(r -> r.getMatch()==null || !day.getId().equals(r.getMatch().getMatchday().getId()))
                ? "Link this round's standalone or misplaced races to their pairings in the matchday workspace before editing the round." : null;
    }
    private String snapshot(Scope scope, String generation) {
        var text=new StringBuilder(); var phase=scope.phase();
        append(text,generation,phase.getId(),phase.getSeason().getId(),phase.getFormat(),phase.getLayout(),phase.getLegs(),phase.getTotalRounds(),
                scope.group()==null?null:scope.group().getId(),scope.targetDayId());
        new TreeMap<>(scope.roster().places()).forEach((team,place) -> append(text,team,place));
        for (var day:scope.days()) append(text,day.getId(),day.getSortIndex(),day.getLabel(),day.getUpdatedAt(),day.getPickWindowVersion(),day.isPicksOpen());
        for (var match:scope.matches()) {
            append(text,match.getId(),match.getMatchday().getId(),id(match.getHomeTeam()),id(match.getAwayTeam()),match.isBye(),id(match.getWalkoverTeam()),
                    match.getHomeScore(),match.getAwayScore(),match.getUpdatedAt(),scope.reasons().get(match.getId()));
        }
        var children=new TreeMap<UUID,Race>(); scope.legs().values().forEach(rows -> rows.forEach(r -> children.put(r.getId(),r)));
        scope.targetRaces().forEach(r -> children.put(r.getId(),r));
        for (var race:children.values()) append(text,race.getId(),race.getMatchday().getId(),race.getMatch()==null?null:race.getMatch().getId(),
                race.getPlayoffMatchup()==null?null:race.getPlayoffMatchup().getId(),id(race.getHomeTeam()),id(race.getAwayTeam()),race.hasTeamOverrides(),race.getUpdatedAt(),
                race.getCar()==null?null:race.getCar().getId(),race.getCarPoolEntry()==null?null:race.getCarPoolEntry().getId(),race.getTrack()==null?null:race.getTrack().getId(),race.getDateTime());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private void requireSnapshot(Scope scope, String expected) {
        if (!snapshot(scope,epoch.managementGeneration()).equals(expected)) throw new BusinessRuleException("The round context changed. Reload before saving.");
    }
    private void requireGeneration(String generation) {
        if (!epoch.managementGeneration().equals(generation)) throw new BusinessRuleException("Reload this round after a restart or restore.");
    }
    private void requireCreation(Scope scope) {
        if (scope.roster().places().isEmpty()) throw new BusinessRuleException("Assign teams to this phase/group before creating a round.");
        if (scope.phase().getTotalRounds()!=null && scope.days().size()>=scope.phase().getTotalRounds()) throw new BusinessRuleException("All configured rounds already exist.");
    }
    private static void validateHeader(SwissRoundForm form) {
        var errors=new ArrayList<SwissRoundValidationException.FieldIssue>();
        if (form.getPhaseId()==null) errors.add(issue("phaseId","Choose a phase."));
        if (!hasText(form.getLabel()) || form.getLabel().length()>255) errors.add(issue("label","Enter a round label of at most 255 characters."));
        if (form.getRows()==null || form.getRows().size()>256) errors.add(issue("rows","Use at most 256 pairing rows."));
        if (!errors.isEmpty()) throw new SwissRoundValidationException(errors);
    }
    private static SwissRoundForm copy(SwissRoundForm source) {
        var target=new SwissRoundForm(); target.setPhaseId(source.getPhaseId()); target.setGroupId(source.getGroupId()); target.setMatchdayId(source.getMatchdayId());
        target.setLabel(source.getLabel()); target.setRoundNumber(source.getRoundNumber()); target.setScope(source.getScope()); target.setGeneration(source.getGeneration());
        if (source.getRows()==null) { target.setRows(null); return target; }
        for (var row:source.getRows()) {
            if (row==null) { target.getRows().add(null); continue; }
            var next=new SwissRoundForm.Pairing(); next.setMatchId(row.getMatchId()); next.setHomeTeamId(row.getHomeTeamId()); next.setAwayTeamId(row.isBye()?null:row.getAwayTeamId());
            next.setBye(row.isBye()); next.setRemove(row.isRemove()); target.getRows().add(next);
        }
        return target;
    }
    private static boolean same(Match match, SwissRoundForm.Pairing row) {
        return Objects.equals(id(match.getHomeTeam()),row.getHomeTeamId()) && Objects.equals(id(match.getAwayTeam()),row.getAwayTeamId()) && match.isBye()==row.isBye();
    }
    private static RaceCarSelectionService.Assignment cleared(Race race) { return new RaceCarSelectionService.Assignment(race.getId(),id(race.getHomeTeam()),true,null,null); }
    private static UUID id(Team team) { return team==null?null:team.getId(); }
    private static String label(Team team) { return team==null?"Unavailable team":team.getShortName()+" · "+team.getName(); }
    private static String phaseLabel(SeasonPhase phase) { return hasText(phase.getLabel())?phase.getLabel():phase.getPhaseType().name().replace('_',' '); }
    private static UUID place(Scope scope, UUID id) { return id==null?null:scope.roster().places().get(id); }
    private static String pair(UUID first, UUID second) { return first.toString().compareTo(second.toString())<0?first+":"+second:second+":"+first; }
    private static SwissRoundValidationException.FieldIssue issue(String field, String message) { return new SwissRoundValidationException.FieldIssue(field,message); }
    private static int appendIndex(List<Matchday> days) {
        try { return Math.addExact(days.stream().mapToInt(Matchday::getSortIndex).max().orElse(0),1); }
        catch (ArithmeticException failure) { throw new BusinessRuleException("The matchday order is exhausted. Adjust ordering before adding a round."); }
    }
    private static void append(StringBuilder target, Object... fields) {
        for (var field:fields) { if (field==null) target.append("-;"); else { String text=field.toString(); target.append(text.length()).append(':').append(text).append(';'); } }
    }
}
