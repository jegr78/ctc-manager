package org.ctc.admin.service;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.LineupApplyForm;
import org.ctc.admin.dto.LineupCorrectionForm;
import org.ctc.admin.dto.LineupWindowForm;
import org.ctc.dataimport.DriverMatchingService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.lineups.LineupChoice;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.LineupApplyService;
import org.ctc.domain.service.LineupOverlapService;
import org.ctc.domain.service.LineupSubmissionService;
import org.ctc.domain.service.LineupTargetService;
import org.ctc.domain.service.PickSessionEpoch;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Prepares the management lineup overview and detail pages and forwards their decisions to the domain services. */
@Service
@RequiredArgsConstructor
public class MatchdayLineupAdminService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'Europe/Berlin'").withZone(ZoneId.of("Europe/Berlin"));
    private final SeasonRepository seasons;
    private final MatchdayRepository matchdays;
    private final RaceRepository races;
    private final TeamRepository teams;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final LineupWindowEventRepository windows;
    private final LineupTargetService targets;
    private final LineupOverlapService overlaps;
    private final LineupSubmissionService submissionService;
    private final LineupApplyService applyService;
    private final DriverMatchingService matching;
    private final PickSessionEpoch epoch;
    private final Environment environment;

    @Transactional(readOnly = true)
    public Overview overview(UUID seasonId, UUID matchdayId) {
        if (matchdayId != null) {
            var day = matchdays.findById(matchdayId).orElseThrow(MatchdayLineupAdminService::unavailable);
            if (seasonId != null && !seasonId.equals(day.getSeason().getId())) throw unavailable();
            seasonId = day.getSeason().getId();
        }
        if (seasonId != null && !seasons.existsById(seasonId)) throw unavailable();
        var seasonChoices = seasons.findAll().stream().sorted(Comparator.comparing(Season::getDisplayLabel))
                .map(season -> new Choice(season.getId(), season.getDisplayLabel())).toList();
        if (seasonId == null) return new Overview(null, null, seasonChoices, List.of(), List.of());
        var dayChoices = matchdays.findBySeasonIdOrderBySortIndexAsc(seasonId).stream().map(day -> new Choice(day.getId(), day.getLabel())).toList();
        var rows = new ArrayList<Row>();
        for (var race : races.findByMatchdaySeasonId(seasonId)) {
            if (matchdayId != null && !matchdayId.equals(race.getMatchday().getId())) continue;
            for (var team : participants(race)) {
                var submission = submissions.findByRaceIdAndTeamId(race.getId(), team.getId()).orElse(null);
                if (submission == null && !LineupTargetRules.isOpenTarget(race, team)) continue;
                rows.add(row(race, team, submission));
            }
        }
        rows.sort(Comparator.comparing(Row::matchdaySort).thenComparing(Row::label).thenComparing(Row::raceId).thenComparing(Row::teamId));
        return new Overview(seasonId, matchdayId, seasonChoices, dayChoices, rows);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID raceId, UUID teamId) {
        var race = races.findById(raceId).orElseThrow(MatchdayLineupAdminService::unavailable);
        var team = teams.findById(teamId).orElseThrow(MatchdayLineupAdminService::unavailable);
        if (!team.getId().equals(race.getHomeTeam() == null ? null : race.getHomeTeam().getId())
                && !team.getId().equals(race.getAwayTeam() == null ? null : race.getAwayTeam().getId())) throw unavailable();
        var submission = submissions.findByRaceIdAndTeamId(raceId, teamId).orElse(null);
        var history = submission == null ? List.<LineupSubmissionRevision>of() : revisions.findBySubmissionIdOrderByRevisionNumberDesc(submission.getId());
        var revisionViews = history.stream().map(revision -> new RevisionView(revision.getRevisionNumber(), TIME.format(revision.getSubmittedAt()),
                revision.getActorKind() == LineupActorKind.TEAM ? "Team member " + revision.getActorId() : "Management: " + revision.getActorId(),
                revision.getReason(), lines(revision.lineup()),
                submission.getAppliedRevisionNumber() != null && submission.getAppliedRevisionNumber() == revision.getRevisionNumber())).toList();
        var latest = history.isEmpty() ? null : history.getFirst().lineup();
        var proposals = new ArrayList<Proposal>();
        if (latest != null) {
            for (var entry : latest.proposed()) {
                var match = matching.findDriver(entry.psnId());
                String key = entry.psnId().toLowerCase(Locale.ROOT);
                proposals.add(new Proposal(key, entry.label(), match.isMatch() ? match.driver().getId() : null,
                        match.isMatch() ? match.driver().getPsnId() + " (" + match.type().name().toLowerCase(Locale.ROOT)
                                + (match.needsConfirmation() ? String.format(Locale.ROOT, ", similarity %.2f", match.similarity()) : "") + ")" : null,
                        match.isMatch() && !match.needsConfirmation()));
            }
        }
        var events = windows.findByRaceIdOrderBySequenceNumberDesc(raceId);
        var eventViews = events.stream().map(event -> new WindowEventView(event.getSequenceNumber(), event.getKind() == LineupWindowKind.OPEN ? "Opened" : "Closed",
                event.getReason(), event.getActorId(), TIME.format(event.getOccurredAt()))).toList();
        var fielded = new HashMap<UUID, Team>();
        if (latest != null) latest.driverIds().forEach(id -> fielded.put(id, team));
        var conflicts = latest == null ? List.<String>of() : overlaps.conflicts(race, fielded, lineup -> LineupTargetRules.fieldingSide(lineup, race, team))
                .stream().map(conflict -> conflict.psnId() + " is already in the lineup of " + conflict.otherTeam().getShortName()).toList();
        var warnings = latest == null ? List.<String>of() : overlaps.foreignWarnings(race, team, latest.driverIds());
        var choices = targets.choices(race, team);
        var current = new ArrayList<UUID>();
        if (latest != null) current.addAll(latest.driverIds());
        return new Detail(row(race, team, submission), race.getMatchday().getSeason().getId(), race.getMatchday().getId(),
                race.getMatchday().getSeason().getLineupSize(), submission == null ? null : submission.getId(),
                submission == null ? 0 : submission.getVersion(), submission == null ? null : submission.getLatestRevisionNumber(),
                revisionViews, proposals, !race.getResults().isEmpty(), choices, current,
                events.isEmpty() ? 0 : events.getFirst().getSequenceNumber(), eventViews, conflicts, warnings, epoch.managementGeneration());
    }

    public LineupApplyForm applyForm(Detail detail) {
        var form = new LineupApplyForm();
        form.setExpectedVersion(detail.version());
        form.setRevisionNumber(detail.latestRevision());
        form.setExpectedGeneration(detail.generation());
        detail.proposals().forEach(proposal -> form.getLinks().put(proposal.key(),
                proposal.preselect() ? proposal.suggestedDriverId().toString() : "create"));
        return form;
    }

    public LineupCorrectionForm correctionForm(Detail detail) {
        var form = new LineupCorrectionForm();
        form.setExpectedVersion(detail.version());
        form.setExpectedGeneration(detail.generation());
        form.setRequestId(UUID.randomUUID());
        var drivers = new ArrayList<UUID>(detail.currentDrivers());
        while (drivers.size() < detail.lineupSize()) drivers.add(null);
        form.setDrivers(drivers.subList(0, detail.lineupSize()));
        return form;
    }

    public LineupWindowForm windowForm(Detail detail) {
        var form = new LineupWindowForm();
        form.setExpectedSequence(detail.windowSequence());
        form.setExpectedGeneration(detail.generation());
        form.setKind(LineupWindowKind.OPEN);
        return form;
    }

    public List<String> apply(UUID raceId, UUID teamId, LineupApplyForm form, Principal principal) {
        var submission = submissions.findByRaceIdAndTeamId(raceId, teamId).orElseThrow(MatchdayLineupAdminService::unavailable);
        var links = new HashMap<String, UUID>();
        for (var entry : form.getLinks().entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank() || entry.getValue().equals("create")) continue;
            try {
                links.put(entry.getKey().toLowerCase(Locale.ROOT), UUID.fromString(entry.getValue()));
            } catch (IllegalArgumentException invalid) {
                throw new BusinessRuleException("Choose an existing driver or create a new one for every new driver");
            }
        }
        return applyService.apply(submission.getId(), form.getExpectedVersion(), form.getRevisionNumber(), links, form.isResultsConsent(),
                actor(principal, form.getExpectedGeneration()));
    }

    public List<String> correct(UUID raceId, UUID teamId, LineupCorrectionForm form, Principal principal) {
        var chosen = form.getDrivers().stream().filter(Objects::nonNull).toList();
        if (chosen.isEmpty()) throw new BusinessRuleException("Choose at least one driver");
        var entries = new ArrayList<LineupPayload.Entry>();
        for (var driverId : chosen) entries.add(new LineupPayload.Entry(driverId, driverId.toString(), null));
        LineupPayload payload;
        try {
            payload = new LineupPayload(Season.LINEUP_SIZE_MAX, entries);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessRuleException(invalid.getMessage());
        }
        return submissionService.correct(raceId, teamId, form.getExpectedVersion(), form.getRequestId(),
                actor(principal, form.getExpectedGeneration()), payload, form.getReason()).warnings();
    }

    public void decideWindow(UUID raceId, LineupWindowForm form, Principal principal) {
        submissionService.decideWindow(raceId, form.getKind(), form.getExpectedSequence(), actor(principal, form.getExpectedGeneration()), form.getReason());
    }

    private Row row(Race race, Team team, LineupSubmission submission) {
        var target = targets.target(race, team, submission);
        var flags = new ArrayList<String>();
        Integer revision = null;
        if (submission != null) {
            revision = submission.getLatestRevisionNumber();
            var latest = revisions.findBySubmissionIdAndRevisionNumber(submission.getId(), revision).map(LineupSubmissionRevision::lineup).orElse(null);
            if (latest != null) {
                if (!latest.proposed().isEmpty() && submission.status() != LineupStatus.APPLIED) flags.add("New driver pending");
                var fielded = new HashMap<UUID, Team>();
                latest.driverIds().forEach(id -> fielded.put(id, team));
                if (!overlaps.conflicts(race, fielded, lineup -> LineupTargetRules.fieldingSide(lineup, race, team)).isEmpty()) flags.add("Overlap");
                if (!latest.complete()) flags.add(latest.entries().size() + " of " + latest.requiredSize() + " drivers");
            }
        }
        return new Row(race.getId(), team.getId(), race.getMatchday().getId(), race.getMatchday().getSortIndex(), target.label(),
                team.getShortName(), target.status().getLabel(), flags, revision, deadline(target.deadline()), target.reason(), target.editable());
    }

    private static String deadline(Instant deadline) { return deadline == null ? "None" : TIME.format(deadline); }

    private static List<String> lines(LineupPayload payload) {
        var lines = new ArrayList<String>();
        for (var entry : payload.entries()) lines.add(entry.label() + (entry.proposed() ? " (new driver)" : ""));
        return lines;
    }

    private static List<Team> participants(Race race) {
        var result = new ArrayList<Team>();
        if (race.getHomeTeam() != null) result.add(race.getHomeTeam());
        if (race.getAwayTeam() != null && (race.getHomeTeam() == null || !race.getAwayTeam().getId().equals(race.getHomeTeam().getId()))) result.add(race.getAwayTeam());
        return result;
    }

    private ManagementPickActor actor(Principal principal, String generation) {
        String name = principal == null ? null : principal.getName();
        if (name != null && !name.isBlank() && name.length() <= 128) return new ManagementPickActor(name, generation);
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) return new ManagementPickActor("development-manager", generation);
        throw new BusinessRuleException("An authenticated management actor is required");
    }

    private static BusinessRuleException unavailable() { return new BusinessRuleException("Lineup target is unavailable"); }

    public record Choice(UUID id, String label) { }
    public record Overview(UUID seasonId, UUID matchdayId, List<Choice> seasons, List<Choice> matchdays, List<Row> rows) { }
    public record Row(UUID raceId, UUID teamId, UUID matchdayId, int matchdaySort, String label, String team, String status,
                      List<String> flags, Integer revisionNumber, String deadline, String window, boolean open) { }
    public record RevisionView(int number, String submittedAt, String actor, String reason, List<String> lines, boolean applied) { }
    public record Proposal(String key, String label, UUID suggestedDriverId, String suggestion, boolean preselect) { }
    public record WindowEventView(int sequence, String kind, String reason, String actor, String at) { }
    public record Detail(Row row, UUID seasonId, UUID matchdayId, int lineupSize, UUID submissionId, long version, Integer latestRevision,
                         List<RevisionView> revisions, List<Proposal> proposals, boolean hasResults, List<LineupChoice> choices,
                         List<UUID> currentDrivers, int windowSequence, List<WindowEventView> windowEvents, List<String> conflicts,
                         List<String> warnings, String generation) { }
}
