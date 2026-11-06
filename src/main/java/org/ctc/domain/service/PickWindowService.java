package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickWindowService {
    private final SeasonRepository seasons;
    private final MatchdayRepository matchdays;
    private final MatchRepository matches;
    private final RaceRepository races;
    private final PlayoffMatchupRepository playoffs;
    private final MatchdayPickRepository picks;
    private final MatchdayPickDecisionRepository decisions;
    private final EntityManager entityManager;
    private final PickWriteExecutor writes;
    private final PickSessionEpoch epoch;
    private final PickDeadlinePolicy deadlines;
    private final Clock clock;

    public record DayWindow(UUID id, boolean open, LocalDateTime deadline, String weekend, long version, String scope, String generation) { }
    public record PairingWindow(UUID id, boolean playoff, UUID matchdayId, LocalDateTime defaultDeadline,
                                LocalDateTime override, long version, String scope, String generation) { }
    private record DayScope(Matchday day, List<Match> matches, List<Race> races) { }
    private record Pairing(Matchday day, Match match, PlayoffMatchup playoff) {
        long version() { return playoff != null ? playoff.getPickWindowVersion() : match.getPickWindowVersion(); }
        LocalDateTime override() { return playoff != null ? playoff.getPickDeadlineOverride() : match.getPickDeadlineOverride(); }
    }

    @Transactional(readOnly = true)
    public DayWindow readMatchday(UUID id) {
        String generation = epoch.managementGeneration();
        var day = matchdays.findById(id).orElseThrow(PickWindowService::unavailable);
        return new DayWindow(id, day.isPicksOpen(), day.getPickDeadline(), day.getScheduledWeekend(), day.getPickWindowVersion(), dayScope(day), generation);
    }

    @Transactional(readOnly = true)
    public PairingWindow readPairing(UUID id, boolean playoff) {
        String generation = epoch.managementGeneration();
        var pair = pairing(id, playoff);
        return new PairingWindow(id, playoff, pair.day().getId(), pair.day().getPickDeadline(), pair.override(), pair.version(), pairingScope(pair), generation);
    }

    public void saveMatchday(UUID id, boolean open, LocalDateTime deadline, String weekend,
                             long expectedVersion, String expectedScope, ManagementPickActor actor) {
        writes.execute(() -> {
            var current = lockDay(id);
            requireActor(actor); requireVersion(current.day().getPickWindowVersion(), expectedVersion);
            requireScope(dayScope(current.day()), expectedScope); validateDeadline(deadline);
            if (weekend != null && weekend.length() > 64) throw new BusinessRuleException("The event window must not exceed 64 characters");
            if (open && complete(current)) throw new BusinessRuleException("All matches are completed; picks cannot be opened");
            long next = next(current.day().getPickWindowVersion());
            if (open) clearLegacyClosures(current, actor);
            current.day().setPicksOpen(open); current.day().setPickDeadline(deadline);
            current.day().setScheduledWeekend(weekend); current.day().setPickWindowVersion(next);
            return null;
        });
    }

    public void savePairing(UUID id, boolean playoff, LocalDateTime override, long expectedVersion,
                            String expectedScope, ManagementPickActor actor) {
        writes.execute(() -> {
            var before = pairing(id, playoff);
            var current = lockDay(before.day().getId());
            var pair = pairing(id, playoff, current.races());
            requireActor(actor);
            if (!current.day().getId().equals(pair.day().getId())) throw unavailable();
            requireVersion(pair.version(), expectedVersion); requireScope(pairingScope(pair), expectedScope); validateDeadline(override);
            long next = next(Math.max(pair.match() == null ? 0 : pair.match().getPickWindowVersion(),
                    pair.playoff() == null ? 0 : pair.playoff().getPickWindowVersion()));
            if (pair.match() != null) { pair.match().setPickDeadlineOverride(override); pair.match().setPickWindowVersion(next); }
            if (pair.playoff() != null) { pair.playoff().setPickDeadlineOverride(override); pair.playoff().setPickWindowVersion(next); }
            return null;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void closeCompletedForSeason(UUID seasonId) {
        entityManager.flush();
        seasons.findByIdForUpdate(seasonId).orElseThrow(PickWindowService::unavailable);
        var openDays = matchdays.findOpenForUpdate(seasonId);
        if (openDays.isEmpty()) return;
        for (var day : openDays) {
            var current = lockDay(day.getId());
            if (current.day().isPicksOpen() && complete(current)) {
                current.day().setPickWindowVersion(next(current.day().getPickWindowVersion()));
                current.day().setPicksOpen(false);
            }
        }
    }

    private DayScope lockDay(UUID id) {
        var day = matchdays.findById(id).orElseThrow(PickWindowService::unavailable);
        if (day.getSeason() == null) throw unavailable();
        var owner = seasons.findByIdForUpdate(day.getSeason().getId()).orElseThrow(PickWindowService::unavailable);
        var all = races.findBySeasonForSelectionUpdate(owner.getId());
        entityManager.refresh(day, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(day.getPhase(), LockModeType.PESSIMISTIC_WRITE);
        if (!owner.getId().equals(day.getSeason().getId())) throw unavailable();
        var members = matches.findByMatchdayIdForUpdate(id);
        for (var match : members) entityManager.refresh(match, LockModeType.PESSIMISTIC_WRITE);
        return new DayScope(day, members, all.stream().filter(race -> id.equals(race.getMatchday().getId())).toList());
    }

    private Pairing pairing(UUID id, boolean playoff) {
        return pairing(id, playoff, null);
    }

    private Pairing pairing(UUID id, boolean playoff, List<Race> lockedRows) {
        Match match = playoff ? null : matches.findById(id).orElseThrow(PickWindowService::unavailable);
        PlayoffMatchup matchup = playoff ? playoffs.findById(id).orElseThrow(PickWindowService::unavailable) : null;
        if (lockedRows != null) {
            if (match != null) entityManager.refresh(match, LockModeType.PESSIMISTIC_WRITE);
            if (matchup != null) entityManager.refresh(matchup, LockModeType.PESSIMISTIC_WRITE);
        }
        var rows = lockedRows == null ? (playoff ? races.findByPlayoffMatchupId(id) : races.findByMatchId(id))
                : lockedRows.stream().filter(race -> playoff
                    ? race.getPlayoffMatchup() != null && id.equals(race.getPlayoffMatchup().getId())
                    : race.getMatch() != null && id.equals(race.getMatch().getId())).toList();
        if (playoff && rows.isEmpty()) throw new BusinessRuleException("Configure the playoff pairing's races before setting its pick deadline");
        var day = match == null ? rows.getFirst().getMatchday() : match.getMatchday();
        if (rows.stream().anyMatch(race -> !day.getId().equals(race.getMatchday().getId()))) {
            throw new BusinessRuleException("The pairing spans different matchdays; management must review its deadline context");
        }
        var matchups = rows.stream().map(Race::getPlayoffMatchup).filter(Objects::nonNull).map(PlayoffMatchup::getId).distinct().toList();
        var bridges = rows.stream().map(Race::getMatch).filter(Objects::nonNull).map(Match::getId).distinct().toList();
        if (matchups.size() > 1 || bridges.size() > 1
                || !matchups.isEmpty() && rows.stream().anyMatch(race -> race.getPlayoffMatchup() == null)) throw unavailable();
        if (matchup == null && !matchups.isEmpty()) matchup = playoffs.findById(matchups.getFirst()).orElseThrow(PickWindowService::unavailable);
        if (match == null && !bridges.isEmpty()) match = matches.findById(bridges.getFirst()).orElseThrow(PickWindowService::unavailable);
        if (lockedRows != null && matchup != null) {
            entityManager.refresh(matchup, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(matchup.getRound(), LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(matchup.getRound().getPlayoff(), LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(matchup.getRound().getPlayoff().getPhase(), LockModeType.PESSIMISTIC_WRITE);
        }
        if (matchup != null && !day.getSeason().getId().equals(matchup.getRound().getPlayoff().getSeason().getId())) throw unavailable();
        if (match != null && !day.getId().equals(match.getMatchday().getId())) throw unavailable();
        return new Pairing(day, match, matchup);
    }

    private void clearLegacyClosures(DayScope current, ManagementPickActor actor) {
        for (var race : current.races()) {
            var pick = picks.findByRaceId(race.getId()).orElse(null);
            if (pick == null) continue;
            entityManager.refresh(pick, LockModeType.PESSIMISTIC_WRITE);
            if (!pick.matchesTarget(race)) throw unavailable();
            if (pick.getState() != PickState.APPROVED && pick.isManualClosed()) {
                pick.resumeWithinMatchday(); picks.save(pick);
                decisions.save(new MatchdayPickDecision(pick, PickDecisionKind.WINDOW_OPEN, pick.getVersion(),
                        actor.userId(), "Central matchday opening", clock.instant()));
            }
        }
    }

    private boolean complete(DayScope current) {
        return MatchdayPickWindowRules.complete(current.matches(), current.races(), current.day().getPhase().getLegs());
    }

    private void validateDeadline(LocalDateTime deadline) {
        if (deadline == null) return;
        if (deadline.getNano() != 0) throw new BusinessRuleException("Pick deadlines must use whole seconds");
        if (deadline.getYear() < 1000 || deadline.getYear() > 9999) throw new BusinessRuleException("Pick deadline year must be between 1000 and 9999");
        deadlines.resolveDeadline(deadline);
    }
    private void requireActor(ManagementPickActor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128
                || !epoch.managementGeneration().equals(actor.generation())) {
            throw new BusinessRuleException("Reload the current picks settings with a management actor");
        }
    }
    private static void requireVersion(long current, long expected) {
        if (expected < 0 || current != expected) throw new BusinessRuleException("Picks settings changed; reload before saving");
    }
    private static void requireScope(String current, String expected) {
        if (!current.equals(expected)) throw new BusinessRuleException("The picks context changed; reload before saving");
    }
    private static long next(long version) {
        try { return Math.incrementExact(version); }
        catch (ArithmeticException overflow) { throw new BusinessRuleException("Picks settings version exceeds the supported range"); }
    }
    private static String dayScope(Matchday day) {
        return hash(day.getId(), day.getPhase().getId(), day.getSeason().getId(), day.getGroup() == null ? null : day.getGroup().getId(), day.getPickWindowVersion());
    }
    private static String pairingScope(Pairing pair) {
        return hash(dayScope(pair.day()), pair.match() == null ? null : pair.match().getId(),
                pair.match() == null ? null : pair.match().getPickWindowVersion(),
                pair.match() == null || pair.match().getHomeTeam() == null ? null : pair.match().getHomeTeam().getId(),
                pair.match() == null || pair.match().getAwayTeam() == null ? null : pair.match().getAwayTeam().getId(),
                pair.playoff() == null ? null : pair.playoff().getRound().getPlayoff().getPhase().getId(),
                pair.playoff() == null ? null : pair.playoff().getRound().getId(),
                pair.playoff() == null ? null : pair.playoff().getRound().getPlayoff().getId(),
                pair.playoff() == null ? null : pair.playoff().getId(), pair.playoff() == null ? null : pair.playoff().getPickWindowVersion(),
                pair.playoff() == null || pair.playoff().getTeam1() == null ? null : pair.playoff().getTeam1().getId(),
                pair.playoff() == null || pair.playoff().getTeam2() == null ? null : pair.playoff().getTeam2().getId());
    }
    private static String hash(Object... values) {
        var canonical = new StringBuilder();
        for (var value : values) {
            if (value == null) canonical.append("-1:");
            else { String text = value.toString(); canonical.append(text.length()).append(':').append(text); }
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("Required SHA-256 support is unavailable", missing); }
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("The picks context changed or is unavailable; reload its workspace"); }
}
