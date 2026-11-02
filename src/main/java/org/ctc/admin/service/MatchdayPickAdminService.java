package org.ctc.admin.service;

import java.security.Principal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayPickView.*;
import org.ctc.admin.dto.PickCorrectionForm;
import org.ctc.admin.dto.PickReviewForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchdayPickAdminService {
    private final SeasonRepository seasons;
    private final MatchdayRepository matchdays;
    private final RaceRepository races;
    private final MatchdayPickRepository picks;
    private final MatchdayPickRevisionRepository revisions;
    private final MatchdayPickDecisionRepository decisions;
    private final CarRepository cars;
    private final TrackRepository tracks;
    private final SeasonCarPoolEntryRepository entries;
    private final PickTargetService targets;
    private final PickReviewService reviews;
    private final PickDeadlinePolicy deadlines;
    private final PickCollectionStatus collection;
    private final RaceFormDataService forms;
    private final Clock clock;
    private final Environment environment;

    @Transactional(readOnly = true)
    public Overview overview(UUID seasonId, UUID matchdayId, PickState state) {
        if (matchdayId != null) {
            var day = matchdays.findById(matchdayId).orElseThrow(MatchdayPickAdminService::unavailable);
            if (seasonId != null && !seasonId.equals(day.getSeason().getId())) throw unavailable();
            seasonId = day.getSeason().getId();
        }
        if (seasonId != null && !seasons.existsById(seasonId)) throw unavailable();
        var seasonChoices = seasons.findAll().stream().sorted(Comparator.comparing(Season::getDisplayLabel))
                .map(season -> new Choice(season.getId(), season.getDisplayLabel())).toList();
        var days = seasonId == null ? matchdays.findAll() : matchdays.findBySeasonIdOrderBySortIndexAsc(seasonId);
        var dayChoices = days.stream().map(day -> new Choice(day.getId(), day.getSeason().getDisplayLabel() + " | " + day.getLabel())).toList();
        var candidates = seasonId == null ? races.findAll() : races.findByMatchdaySeasonId(seasonId);
        var rows = candidates.stream().filter(Race::isPicksManaged)
                .filter(race -> matchdayId == null || matchdayId.equals(race.getMatchday().getId()))
                .map(race -> row(race, candidates, picks.findSnapshotByRaceId(race.getId()).orElse(null)))
                .filter(row -> state == null || row.state() == state).sorted(Comparator.comparing(Row::label).thenComparing(Row::raceId)).toList();
        return new Overview(seasonId, matchdayId, state, seasonChoices, dayChoices, rows);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID raceId) {
        var race = race(raceId);
        var pick = picks.findSnapshotByRaceId(raceId).orElse(null);
        var row = row(race, races.findByMatchdaySeasonId(race.getMatchday().getSeason().getId()), pick);
        var history = pick == null ? List.<MatchdayPickRevision>of() : revisions.findByPickIdOrderByRevisionNumberDesc(pick.getId());
        var decisionHistory = pick == null ? List.<MatchdayPickDecision>of() : decisions.findByPickIdOrderByAggregateVersionDesc(pick.getId());
        var latest = history.stream().filter(revision -> Objects.equals(revision.getRevisionNumber(), row.revisionNumber())).findFirst().orElse(null);
        if (row.revisionNumber() != null && latest == null) throw new BusinessRuleException("Pick history is incomplete");
        var existing = PickSetupSnapshot.from(race);
        var proposed = latest == null ? null : latest.getPayload();
        var currentValues = values(existing);
        var proposedValues = proposed == null ? List.<Value>of() : values(proposed);
        var comparison = new ArrayList<Comparison>();
        for (int i = 0; i < currentValues.size(); i++) {
            var current = currentValues.get(i); var submitted = proposed == null ? null : proposedValues.get(i);
            boolean changed = submitted != null && !current.value().equals(submitted.value());
            if (proposed != null && i == 0) changed = !Objects.equals(existing.trackId(), proposed.trackId());
            if (proposed != null && i == 1) changed = !Objects.equals(existing.carId(), proposed.carId());
            if (proposed != null && i == 2) changed = !Objects.equals(existing.entryId(), proposed.carPoolEntryId());
            comparison.add(new Comparison(current.label(), current.value(), submitted == null ? "No submission" : submitted.value(), changed));
        }
        var returnReason = stateReturnReason(pick, decisionHistory);
        return new Detail(row, existing.fingerprint(), proposed != null && existing.configured() && !existing.matches(proposed), returnReason, comparison,
                history.stream().map(revision -> new Revision(revision.getRevisionNumber(), revision.getReceiptVersion(), revision.getSubmitterId(),
                        revision.getConfirmedAt(), values(revision.getPayload()))).toList(),
                decisionHistory.stream().map(decision -> new Decision(decision.getKind(), decision.getAggregateVersion(), decision.getActorId(),
                        decision.getReason(), decision.getDecidedAt(), decision.getCorrectionPayload() == null ? List.of() : values(decision.getCorrectionPayload()))).toList());
    }

    public PickReviewForm reviewForm(Detail detail) {
        var form = new PickReviewForm(); form.setExpectedVersion(detail.row().version()); form.setRevisionNumber(detail.row().revisionNumber());
        form.setSettingsFingerprint(detail.fingerprint());
        if (!detail.row().actions().isEmpty()) form.setAction(detail.row().actions().getFirst());
        return form;
    }

    public void decide(UUID raceId, PickReviewForm form, Principal principal) {
        requireForm(form.getExpectedVersion(), form.getSettingsFingerprint());
        var actor = actor(principal);
        if (form.getAction() == null) throw new BusinessRuleException("Choose a review decision");
        switch (form.getAction()) {
            case OPEN_MISSING -> reviews.openMissing(raceId, form.getExpectedVersion(), actor, form.getReason());
            case RETURN -> reviews.returnForCorrection(raceId, form.getExpectedVersion(), actor, form.getReason());
            case CLOSE -> reviews.close(raceId, form.getExpectedVersion(), actor, form.getReason());
            case APPROVE -> {
                if (form.getRevisionNumber() == null) throw new BusinessRuleException("Choose the exact current revision");
                reviews.approve(raceId, form.getExpectedVersion(), form.getRevisionNumber(), actor, form.getReason(),
                        form.isReplaceExisting(), form.getSettingsFingerprint());
            }
        }
    }

    @Transactional(readOnly = true)
    public PickCorrectionForm correctionForm(UUID raceId) {
        var detail = detail(raceId);
        if (!detail.row().correctable()) throw new BusinessRuleException("Only an approved pick with unchanged target identity can be corrected");
        var race = race(raceId); var snapshot = PickSetupSnapshot.from(race); var values = snapshot.settings();
        var form = new PickCorrectionForm(); form.setExpectedVersion(detail.row().version()); form.setSettingsFingerprint(detail.fingerprint());
        form.setTrackId(snapshot.trackId()); form.setCarId(snapshot.carId()); form.setCarPoolEntryId(snapshot.entryId());
        form.setLaps(values.laps()); form.setTyreWear(values.tyreWear()); form.setFuelConsumption(values.fuelConsumption());
        form.setRefuelingSpeed(values.refuelingSpeed()); form.setRequiredPitStops(values.requiredPitStops());
        form.setTimeProgressionMode(values.timeProgressionMode()); form.setTimeProgressionMultiplier(values.timeProgressionMultiplier());
        form.setWeather(values.weather()); form.setTimeOfDay(values.timeOfDay());
        form.setInitialFuel(Arrays.stream(PickInitialFuel.values()).filter(fuel -> fuel.settingsValue().equals(values.initialFuel())).findFirst().orElse(null));
        form.setAvailableTyres(tyres(values.availableTyres())); form.setMandatoryTyres(tyres(values.mandatoryTyres()));
        return form;
    }

    @Transactional(readOnly = true)
    public RaceService.RaceFormData correctionChoices(UUID raceId, PickCorrectionForm form) {
        var race = race(raceId);
        return forms.getSubmittedRaceFormData(race.getMatchday().getId(), race.getHomeTeam().getId(), raceId,
                form.getCarPoolEntryId(), form.getCarId(), form.getTrackId());
    }

    public void correct(UUID raceId, PickCorrectionForm form, Principal principal) {
        requireForm(form.getExpectedVersion(), form.getSettingsFingerprint());
        reviews.correct(raceId, form.getExpectedVersion(), actor(principal), form.getReason(), form.payload(), form.getSettingsFingerprint());
    }

    private ManagementPickActor actor(Principal principal) {
        String name = principal == null ? null : principal.getName();
        if (name != null && !name.isBlank() && name.length() <= 128) {
            return new ManagementPickActor(name);
        }
        if (principal == null && environment.acceptsProfiles(Profiles.of("dev", "local"))) return new ManagementPickActor("development-manager");
        throw new BusinessRuleException("An authenticated management actor is required");
    }

    private static void requireForm(Long version, String fingerprint) {
        if (version == null || version < 0 || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new BusinessRuleException("Reload the current review before applying a decision");
        }
    }

    private Row row(Race race, List<Race> seasonRaces, MatchdayPick pick) {
        var day = race.getMatchday(); var season = day.getSeason(); var state = pick == null ? PickState.MISSING : pick.getState();
        boolean identity = pick == null || pick.matchesTarget(race); boolean eligible = identity && targets.isEligible(race);
        var actions = new ArrayList<PickReviewForm.Action>();
        if (eligible && state != PickState.APPROVED) {
            if (pick == null || pick.getLatestRevisionNumber() == null) actions.add(PickReviewForm.Action.OPEN_MISSING);
            else { actions.add(PickReviewForm.Action.APPROVE); actions.add(PickReviewForm.Action.RETURN); }
            actions.add(PickReviewForm.Action.CLOSE);
        }
        var decision = deadlines.assess(new PickDeadlinePolicy.Context(state, season.isPicksCollectionEnabled(), collection.isAvailable(), eligible,
                pick != null && pick.isManualOpen(), pick != null && pick.isManualClosed(), day.getPickDeadline()));
        String deadline = "Not configured"; String deadlineState = "Missing deadline";
        if (day.getPickDeadline() != null) {
            deadline = day.getPickDeadline() + " Europe/Berlin";
            try {
                var cutoff = deadlines.resolveDeadline(day.getPickDeadline());
                deadline += " " + cutoff.atZone(ZoneId.of("Europe/Berlin")).getOffset();
                deadlineState = clock.instant().isBefore(cutoff) ? "Before deadline" : "Deadline passed";
            } catch (BusinessRuleException ambiguous) { deadlineState = "Deadline requires management review"; }
        }
        return new Row(race.getId(), season.getId(), day.getId(), PickTargetService.label(season, race, seasonRaces), season.getDisplayLabel(), day.getLabel(),
                state, pick == null ? 0 : pick.getVersion(), pick == null ? null : pick.getLatestRevisionNumber(),
                pick == null ? null : pick.getApprovedRevisionNumber(), deadline, deadlineState,
                pick != null && pick.isManualOpen(), pick != null && pick.isManualClosed(), identity ? decision.reason() : "Target identity changed; resolve before review",
                identity, actions, identity && state == PickState.APPROVED);
    }

    private Race race(UUID id) {
        var race = races.findById(id).orElseThrow(MatchdayPickAdminService::unavailable);
        if (!race.isPicksManaged() || race.getMatchday() == null || race.getHomeTeam() == null) throw unavailable();
        return race;
    }
    private List<Value> values(PickPayload payload) {
        return values(new PickSetupSnapshot(payload.trackId(), payload.carId(), payload.carPoolEntryId(), RaceSettingsValues.from(payload)));
    }
    private List<Value> values(PickSetupSnapshot snapshot) {
        var v = snapshot.settings();
        return List.of(new Value("Track", snapshot.trackId() == null ? "Not set" : tracks.findById(snapshot.trackId()).orElseThrow().getName()),
                new Value("Car", snapshot.carId() == null ? "Not set" : cars.findById(snapshot.carId()).orElseThrow().getDisplayName()),
                new Value("Pool entry", snapshot.entryId() == null ? "None" : entries.findById(snapshot.entryId()).orElseThrow().getLabel()),
                new Value("Laps", text(v.laps())), new Value("Tyre wear", text(v.tyreWear())), new Value("Fuel consumption", text(v.fuelConsumption())),
                new Value("Refuelling litres/second", text(v.refuelingSpeed())), new Value("Initial fuel", text(v.initialFuel())),
                new Value("Required pit stops", text(v.requiredPitStops())), new Value("Time progression", v.timeProgressionMode() == TimeProgressionMode.EQUAL_CONDITIONS
                        ? "Equal Conditions" : v.timeProgressionMultiplier() == null ? "Not set" : v.timeProgressionMultiplier() + "x"),
                new Value("Weather", text(v.weather())), new Value("Time of day", text(v.timeOfDay())), new Value("Available tyres", text(v.availableTyres())),
                new Value("Mandatory tyres", text(v.mandatoryTyres())));
    }
    private static String text(Object value) { return value == null || value.toString().isBlank() ? "Not set" : value.toString(); }
    private static String stateReturnReason(MatchdayPick pick, List<MatchdayPickDecision> history) {
        if (pick == null || pick.getState() != PickState.RETURNED) return null;
        return history.stream().filter(decision -> decision.getKind() == PickDecisionKind.RETURN).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Return history is incomplete")).getReason();
    }
    private static Set<PickTyre> tyres(String value) {
        if (value == null || value.isBlank() || value.equals("None")) return Set.of();
        return Arrays.stream(value.split(",\\s*")).map(PickTyre::valueOf).collect(java.util.stream.Collectors.toSet());
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Pick review target or filter is unavailable"); }
}
