package org.ctc.domain.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.LineupTargetRules;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.LineupSubmissionRepository;
import org.ctc.domain.repository.LineupSubmissionRevisionRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.springframework.stereotype.Service;

/**
 * Writes the latest revision of a lineup submission into the leg's {@code RaceLineup} rows. Only rows of the fielding
 * side are replaced; the opponent's rows and every guest row stay, and score aggregation runs afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineupApplyService {
    private final PickWriteExecutor writes;
    private final ScoreWriteLockService locks;
    private final EntityManager entityManager;
    private final LineupSubmissionRepository submissions;
    private final LineupSubmissionRevisionRepository revisions;
    private final RaceLineupRepository lineups;
    private final DriverRepository drivers;
    private final SeasonDriverRepository seasonDrivers;
    private final RaceLineupService raceLineups;
    private final LineupOverlapService overlaps;
    private final PickSessionEpoch epoch;
    private final Clock clock;

    /**
     * @param links          for each proposed PSN ID (lower case), an existing driver to use; a missing key creates
     *                       the driver and registers it for the fielding team
     * @param resultsConsent required when the leg already has results
     */
    public List<String> apply(UUID submissionId, long expectedVersion, int revisionNumber, Map<String, UUID> links,
                              boolean resultsConsent, ManagementPickActor actor) {
        String actorId = LineupSubmissionService.managementActor(actor);
        return writes.execute(() -> {
            if (actor.generation() != null && !actor.generation().equals(epoch.managementGeneration())) {
                throw new BusinessRuleException("This management form was reset by a restore or application restart; reload its current version");
            }
            var submission = submissions.findById(submissionId).orElseThrow(() -> new BusinessRuleException("Lineup submission is unavailable"));
            var raceId = submission.getRace().getId();
            locks.lockRace(raceId);
            entityManager.refresh(submission, LockModeType.PESSIMISTIC_WRITE);
            submission.requireVersion(expectedVersion);
            if (revisionNumber != submission.getLatestRevisionNumber()) throw new BusinessRuleException("Only the latest revision can be applied; reload the submission");
            var race = submission.getRace();
            var team = PickAccessService.participant(race, submission.getTeam().getId());
            if (!LineupTargetRules.isOpenTarget(race, team) && race.getResults().isEmpty()) {
                throw new BusinessRuleException("The team no longer plays this leg");
            }
            if (!race.getResults().isEmpty() && !resultsConsent) {
                throw new BusinessRuleException("This leg already has results; confirm that the lineup should change anyway");
            }
            var lineup = revisions.findBySubmissionIdAndRevisionNumber(submissionId, revisionNumber)
                    .orElseThrow(() -> new BusinessRuleException("Lineup revision is unavailable")).lineup();

            var roster = new HashMap<UUID, UUID>();
            var guests = new HashMap<UUID, UUID>();
            for (var row : lineups.findByRaceId(raceId)) {
                if (row.isGuest()) guests.put(row.getDriver().getId(), row.getTeam().getId());
                else if (!LineupTargetRules.fieldingSide(row, race, team)) roster.put(row.getDriver().getId(), row.getTeam().getId());
            }
            for (var entry : lineup.entries()) {
                var driver = entry.proposed() ? resolve(entry.psnId(), entry.displayName(), links, race.getMatchday().getSeason(), team)
                        : drivers.findById(entry.driverId()).orElseThrow(() -> new BusinessRuleException(entry.psnId() + " no longer exists; correct the lineup first"));
                if (roster.containsKey(driver.getId())) throw new BusinessRuleException(driver.getPsnId() + " is in the opponent's lineup for this leg");
                if (guests.containsKey(driver.getId())) throw new BusinessRuleException(driver.getPsnId() + " is already a guest in this leg");
                roster.put(driver.getId(), team.getId());
            }
            raceLineups.saveLineup(raceId, roster, guests);
            submission.apply(revisionNumber, clock.instant(), actorId);
            submissions.save(submission);
            log.info("Applied lineup revision {} for team {} in race {}", revisionNumber, team.getId(), raceId);
            var applied = roster.entrySet().stream().filter(e -> e.getValue().equals(team.getId())).map(Map.Entry::getKey).toList();
            return overlaps.foreignWarnings(race, team, applied);
        });
    }

    private Driver resolve(String psnId, String displayName, Map<String, UUID> links, org.ctc.domain.model.Season season, Team team) {
        var linked = links.get(psnId.toLowerCase(Locale.ROOT));
        if (linked != null) {
            return drivers.findById(linked).orElseThrow(() -> new BusinessRuleException("The linked driver for " + psnId + " no longer exists"));
        }
        if (drivers.findByPsnIdIgnoreCase(psnId).isPresent()) {
            throw new BusinessRuleException(psnId + " already exists; link the existing driver instead of creating one");
        }
        var created = drivers.save(new Driver(psnId, displayName == null ? psnId : displayName));
        if (seasonDrivers.findBySeasonIdAndDriverId(season.getId(), created.getId()).isEmpty()) {
            seasonDrivers.save(new SeasonDriver(season, created, team));
        }
        log.info("Created driver {} from a lineup submission for team {}", created.getId(), team.getId());
        return created;
    }
}
