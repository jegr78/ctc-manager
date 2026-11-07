package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.PickPublicationSnapshotRepository;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management-only review of unfinished targets before opt-in or later enrollment. */
@Service
@RequiredArgsConstructor
public class PickActivationService {
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final PickPublicationSnapshotRepository snapshots;
    private final PickPublicationAttemptRepository attempts;
    private final PickWriteExecutor writes;
    private final PickSessionEpoch epoch;
    private final EntityManager entities;

    @Transactional(readOnly = true)
    public Review review(UUID seasonId) {
        String generation = epoch.managementGeneration();
        var season = seasons.findById(seasonId).orElseThrow(PickActivationService::unavailable);
        return review(season, races.findByMatchdaySeasonId(seasonId), generation);
    }

    public void enable(UUID seasonId, String fingerprint, Map<UUID, LegacyPickClassification> classifications, ManagementPickActor actor) {
        writes.execute(() -> {
            actor(actor);
            var season = seasons.findByIdForUpdate(seasonId).orElseThrow(PickActivationService::unavailable);
            var rows = races.findBySeasonForSelectionUpdate(seasonId);
            for (var race : rows) {
                entities.refresh(race.getMatchday(), LockModeType.PESSIMISTIC_WRITE);
                entities.refresh(race.getMatchday().getPhase(), LockModeType.PESSIMISTIC_WRITE);
                if (race.getSettings() != null) entities.refresh(race.getSettings(), LockModeType.PESSIMISTIC_WRITE);
                if (!seasonId.equals(race.getMatchday().getSeason().getId())) throw unavailable();
            }
            var review = review(season, rows, epoch.managementGeneration());
            if (!review.fingerprint().equals(fingerprint) || season.isPicksCollectionEnabled() && review.newTargetCount() == 0) {
                throw new BusinessRuleException("The collection review changed; reload before enabling");
            }
            if (review.pendingPublicationCount() != 0) throw new BusinessRuleException("Reconcile pending publication outputs before enabling collection for these races");
            Set<UUID> required = review.configured().stream().map(Candidate::raceId).collect(Collectors.toSet());
            if (classifications == null || !required.equals(classifications.keySet())) {
                throw new BusinessRuleException("Classify every configured target in this exact season review");
            }
            for (var candidate : review.configured()) {
                var classification = classifications.get(candidate.raceId());
                if (classification == null) throw new BusinessRuleException("Choose a classification for every configured target");
                if (classification == LegacyPickClassification.CONFIRMED_PUBLIC && !candidate.confirmedPublic()) {
                    throw new BusinessRuleException("Already-public settings require matching confirmed publication evidence");
                }
                if (classification == LegacyPickClassification.WITHHELD && candidate.confirmedPublic()) {
                    throw new BusinessRuleException("Preserve the confirmed public fields instead of classifying them as withheld");
                }
            }
            for (var race : rows) if (!race.isPicksManaged() && PickTargetRules.isUnfinishedTarget(race)) race.setPicksManaged(true);
            season.setPicksCollectionEnabled(true);
            return null;
        });
    }

    public void pause(UUID seasonId, ManagementPickActor actor) {
        writes.execute(() -> {
            actor(actor);
            seasons.findByIdForUpdate(seasonId).orElseThrow(PickActivationService::unavailable).setPicksCollectionEnabled(false);
            return null;
        });
    }

    private Review review(Season season, List<Race> rows, String generation) {
        var candidates = new ArrayList<Candidate>();
        var canonical = new StringBuilder().append(season.getId()).append(':').append(season.isPicksCollectionEnabled());
        int newTargets = 0, managed = 0, historical = 0, pending = 0;
        for (var race : rows.stream().sorted(Comparator.comparing(Race::getId)).toList()) {
            if (race.isPicksManaged()) { managed++; continue; }
            if (!PickTargetRules.isUnfinishedTarget(race)) { historical++; continue; }
            newTargets++;
            boolean unresolved = attempts.findByActiveRaceId(race.getId()).isPresent();
            if (unresolved) pending++;
            canonical.append(":pending=").append(unresolved);
            var content = PickPublicationContent.from(race);
            canonical.append('|').append(race.getId()).append(':').append(race.getMatchday().getId()).append(':').append(race.getDateTime())
                    .append(':').append(race.getHomeTeam().getId()).append(':').append(race.getAwayTeam().getId()).append(':').append(content.fingerprint());
            if (!PickSetupSnapshot.from(race).configured()) continue;
            var evidence = evidence(race, content.fingerprint());
            for (var proof : evidence) canonical.append('|').append(proof);
            candidates.add(new Candidate(race.getId(), race.getHomeTeam().getShortName() + " | " + PickTargetService.label(season, race, rows),
                    !evidence.isEmpty(), evidence, content));
        }
        return new Review(season.getId(), season.getDisplayLabel(), season.isPicksCollectionEnabled(), generation,
                PickPublicationAttempt.manifestHash(canonical.toString()), List.copyOf(candidates), newTargets, managed, historical, pending);
    }

    private List<Evidence> evidence(Race race, String hash) {
        return snapshots.findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(race.getId()).stream()
                .map(snapshot -> snapshot.getAttempt())
                .filter(attempt -> attempt.getState() == PickPublicationState.SUCCEEDED && attempt.matchesTarget(race)
                        && PickPublicationAttempt.hashMatches(hash, attempt.getContentHash())
                        && PickPublicationAttempt.hashMatches(hash, attempt.getContent().fingerprint()) && !attempt.fields().isEmpty())
                .map(attempt -> new Evidence(attempt.getOutput().name(), attempt.getChannelId(), attempt.getMessageId(),
                        attempt.fields().stream().sorted().map(field -> field.name().toLowerCase(java.util.Locale.ENGLISH).replace('_', ' '))
                                .collect(Collectors.joining(", "))))
                .sorted(Comparator.comparing(Evidence::messageId).thenComparing(Evidence::output).thenComparing(Evidence::fields)).toList();
    }

    private void actor(ManagementPickActor actor) {
        if (actor == null || actor.userId() == null || actor.userId().isBlank() || actor.userId().length() > 128
                || !epoch.managementGeneration().equals(actor.generation())) {
            throw new BusinessRuleException("Reload the current collection review with a management actor");
        }
    }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("The collection review target changed; reload its current season"); }
    public record Review(UUID seasonId, String seasonLabel, boolean enabled, String generation, String fingerprint,
                         List<Candidate> configured, int newTargetCount, int managedTargetCount, int legacyHistoricalCount, int pendingPublicationCount) { }
    public record Candidate(UUID raceId, String label, boolean confirmedPublic, List<Evidence> evidence, PickPublicationContent content) { }
    public record Evidence(String output, String channelId, String messageId, String fields) { }
}
