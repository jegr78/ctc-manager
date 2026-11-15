package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupChoice;
import org.ctc.domain.lineups.LineupTarget;
import org.ctc.domain.lineups.LineupView;
import org.ctc.domain.model.LineupPayload;
import org.ctc.domain.model.LineupStatus;
import org.ctc.domain.model.LineupSubmission;
import org.ctc.domain.model.LineupTargetRules;
import org.ctc.domain.model.LineupWindowEvent;
import org.ctc.domain.model.LineupWindowKind;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.picks.ChannelPickActor;
import org.ctc.domain.repository.LineupSubmissionRepository;
import org.ctc.domain.repository.LineupSubmissionRevisionRepository;
import org.ctc.domain.repository.LineupWindowEventRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists lineup targets for a private team channel and assesses the intake window of one leg and team. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LineupTargetService {
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PickAccessService access;
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final SeasonDriverRepository seasonDrivers;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final LineupWindowEventRepository windows;
    private final PickPublicationService publications;
    private final LineupWindowPolicy policy;
    private final LineupOverlapService overlaps;

    /** Open targets plus targets that already carry a submission, ordered by start time. */
    public List<LineupTarget> list(ChannelPickActor actor) {
        var current = access.authorizeChannel(actor.guildId(), actor.channelId(), actor.userId());
        var result = new LinkedHashMap<String, LineupTarget>();
        for (var binding : current.bindings()) {
            if (seasons.findById(binding.seasonId()).isEmpty()) continue;
            for (var race : races.findByMatchdaySeasonId(binding.seasonId())) {
                for (var team : participants(race)) {
                    if (!LineupTargetRules.serves(binding.teamId(), team)) continue;
                    var submission = submissions.findByRaceIdAndTeamId(race.getId(), team.getId()).orElse(null);
                    var target = target(race, team, submission);
                    if (!target.editable() && submission == null) continue;
                    if (!LineupTargetRules.isOpenTarget(race, team) && submission == null) continue;
                    result.putIfAbsent(race.getId() + ":" + team.getId(), target);
                }
            }
        }
        return result.values().stream().sorted(Comparator.comparing(LineupTarget::start, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LineupTarget::label).thenComparing(LineupTarget::raceId)).toList();
    }

    public LineupView load(ChannelPickActor actor, UUID raceId, UUID teamId) {
        var authorized = access.authorizeLineupTarget(actor, raceId, teamId);
        var race = races.findById(authorized.raceId()).orElseThrow(LineupTargetService::unavailable);
        var team = PickAccessService.participant(race, teamId);
        var submission = submissions.findByRaceIdAndTeamId(raceId, teamId).orElse(null);
        LineupPayload current = submission == null ? null : revisions
                .findBySubmissionIdAndRevisionNumber(submission.getId(), submission.getLatestRevisionNumber())
                .map(revision -> revision.lineup()).orElse(null);
        var warnings = current == null ? List.<String>of() : overlaps.foreignWarnings(race, team, current.driverIds());
        return new LineupView(target(race, team, submission), race.getMatchday().getSeason().getLineupSize(), current,
                submission != null && submission.status() == LineupStatus.APPLIED, choices(race, team), warnings);
    }

    /** The current window decision for one leg and team, without access checks. */
    public LineupWindowPolicy.Decision assess(Race race, Team team) {
        var override = windows.findFirstByRaceIdOrderBySequenceNumberDesc(race.getId()).map(LineupWindowEvent::getKind).orElse(null);
        return policy.assess(context(race, team, override));
    }

    LineupWindowPolicy.Context context(Race race, Team team, LineupWindowKind override) {
        boolean valid = LineupTargetRules.isOpenTarget(race, team);
        return new LineupWindowPolicy.Context(valid, valid && override == null && publications.settingsPublished(race), override, race.getDateTime());
    }

    /** Drivers registered this season for any team of the fielding organisation. */
    public List<LineupChoice> choices(Race race, Team team) {
        var season = race.getMatchday().getSeason();
        return seasonDrivers.findBySeasonId(season.getId()).stream()
                .filter(registration -> LineupTargetRules.sameOrganisation(registration.getTeam(), team))
                .sorted(Comparator.comparing((SeasonDriver registration) -> registration.getDriver().getPsnId(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(registration -> registration.getDriver().getId()))
                .map(registration -> new LineupChoice(registration.getDriver().getId(), registration.getDriver().getPsnId(),
                        LineupPayload.Entry.known(registration.getDriver()).label() + " · " + registration.getTeam().getShortName()))
                .toList();
    }

    public LineupTarget target(Race race, Team team, LineupSubmission submission) {
        var decision = assess(race, team);
        var status = submission == null ? LineupStatus.MISSING : submission.status();
        java.time.Instant start;
        try {
            start = race.getDateTime() == null ? null : race.getDateTime().atZone(LineupWindowPolicy.ZONE).toInstant();
        } catch (RuntimeException invalid) {
            start = null;
        }
        return new LineupTarget(race.getId(), team.getId(), label(race, team), status, submission == null ? 0 : submission.getVersion(),
                decision.editable(), decision.reason(), decision.deadline(), start);
    }

    /** Names matchday, fielding team, opponent, leg and start so sub-teams sharing a channel stay distinguishable. */
    public static String label(Race race, Team team) {
        var opponent = team.getId().equals(race.getHomeTeam().getId()) ? race.getAwayTeam() : race.getHomeTeam();
        var parts = new ArrayList<String>();
        parts.add(race.getMatchday().getLabel());
        parts.add(team.getShortName() + " vs " + (opponent == null ? "Bye" : opponent.getShortName())
                + (team.getId().equals(race.getHomeTeam().getId()) ? " (home)" : " (away)"));
        var legs = legs(race);
        if (legs.size() > 1) parts.add("Leg " + (legs.indexOf(race) + 1) + "/" + legs.size());
        parts.add(start(race.getDateTime()));
        return String.join(" | ", parts);
    }

    static String start(LocalDateTime start) { return start == null ? "No start time" : START.format(start) + " Berlin"; }

    public static List<Race> legs(Race race) {
        var rows = race.getPlayoffMatchup() != null ? race.getPlayoffMatchup().getRaces()
                : race.getMatch() != null ? race.getMatch().getRaces() : List.of(race);
        return rows.stream().sorted(Comparator.comparingInt((Race other) -> other.getMatchday().getSortIndex())
                .thenComparing(Race::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Race::getId)).toList();
    }

    private static List<Team> participants(Race race) {
        var teams = new ArrayList<Team>();
        if (race.getHomeTeam() != null) teams.add(race.getHomeTeam());
        if (race.getAwayTeam() != null && (race.getHomeTeam() == null || !race.getAwayTeam().getId().equals(race.getHomeTeam().getId()))) teams.add(race.getAwayTeam());
        return teams;
    }

    private static BusinessRuleException unavailable() { return new BusinessRuleException("Lineup target is unavailable"); }
}
