package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentLineupService {
    private final RaceLineupRepository lineups;
    private final IncidentContextService contexts;
    private final Optional<IncidentReportAccess> access;
    private final ScoreWriteLockService scoreLocks;
    private final EntityManager entities;

    @Transactional(readOnly = true)
    public IncidentDriverChoices load(AuthorizedIncidentActor actor, UUID raceId) {
        var context = contexts.get(actor.contextId());
        access.orElseThrow(() -> unavailable()).requireCurrent(actor, context, null);
        var race = contexts.resolve(IncidentContextService.reference(context)).races().stream()
                .filter(row -> row.getId().equals(raceId)).findFirst().orElseThrow(IncidentLineupService::unavailable);
        return snapshot(race, lineups.findByRaceId(raceId));
    }

    public void lockRace(UUID raceId) { scoreLocks.lockRace(raceId); }

    public IncidentPayload resolve(Race race, IncidentPayload fields, IncidentDriverSelection selection) {
        if (selection == null || !race.getId().equals(selection.raceId())) throw unavailable();
        var rows = lineups.findByRaceIdForIncidentUpdate(race.getId());
        for (var row : rows) {
            entities.refresh(row, LockModeType.PESSIMISTIC_READ);
            entities.refresh(row.getDriver(), LockModeType.PESSIMISTIC_READ);
            entities.refresh(row.getTeam(), LockModeType.PESSIMISTIC_READ);
        }
        var current = snapshot(race, rows);
        if (!current.fingerprint().equals(selection.fingerprint())) {
            throw new IncidentLineupException("The race lineup changed. Reopen the report form and review the involved drivers again.");
        }
        String drivers = current.selectedText(selection.driverIds());
        return new IncidentPayload(drivers, fields.getReplayTimestamp(), fields.getDescription(), fields.getEvidenceLinks());
    }

    static IncidentDriverChoices snapshot(Race race, List<RaceLineup> rows) {
        var choices = new LinkedHashMap<UUID, IncidentDriverChoices.DriverChoice>();
        for (var row : rows) {
            if (row.getRace() == null || !race.getId().equals(row.getRace().getId()) || row.getDriver() == null || row.getTeam() == null) throw unavailable();
            boolean home = participant(race.getHomeTeam(), row.getTeam()), away = participant(race.getAwayTeam(), row.getTeam());
            if (!home && !away) continue;
            if (home && away) throw unavailable();
            String label = (row.getDriver().getPsnId() + " · " + row.getTeam().getShortName()).replaceAll("[\\p{Cntrl}]", " ");
            var choice = new IncidentDriverChoices.DriverChoice(row.getDriver().getId(), row.getTeam().getId(), label, home);
            var previous = choices.putIfAbsent(choice.id(), choice);
            if (previous != null && !previous.equals(choice)) throw unavailable();
        }
        var sorted = choices.values().stream().sorted(Comparator.comparing(IncidentDriverChoices.DriverChoice::label).thenComparing(IncidentDriverChoices.DriverChoice::id)).toList();
        if (sorted.isEmpty()) throw new IncidentLineupException("No race lineup is configured. Ask management to complete this race's lineup before reporting an incident.");
        if (sorted.stream().filter(IncidentDriverChoices.DriverChoice::home).count() > 25
                || sorted.stream().filter(choice -> !choice.home()).count() > 25) {
            throw new IncidentLineupException("This lineup has more than 25 drivers on one team. Ask management to review the race lineup before reporting.");
        }
        StringBuilder canonical = new StringBuilder(race.getId().toString());
        sorted.forEach(choice -> canonical.append('|').append(choice.id()).append('|').append(choice.teamId()).append('|')
                .append(choice.home()).append('|').append(choice.label().length()).append(':').append(choice.label()));
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return new IncidentDriverChoices(race.getId(), fingerprint, sorted);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("Required SHA-256 is unavailable", impossible); }
    }

    private static boolean participant(Team participant, Team assigned) {
        return participant != null && (participant.getId().equals(assigned.getId())
                || !participant.isSubTeam() && participant.getId().equals(assigned.getParentOrSelf().getId()));
    }
    private static IncidentLineupException unavailable() { return new IncidentLineupException("The incident race lineup is unavailable. Ask management to review the assignments."); }
}
