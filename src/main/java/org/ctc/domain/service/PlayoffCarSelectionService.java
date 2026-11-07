package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.PickTargetRules;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes indirect selection ownership changes through the owning season. */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayoffCarSelectionService {
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final RaceCarSelectionService selections;
    private final EntityManager entityManager;
    private final PickLifecycleGuard pickGuard;
    private final IncidentLifecycleGuard incidentGuard;
    private final RacePenaltyLifecycleGuard penaltyGuard;
    private final RaceDisqualificationLifecycleGuard disqualificationGuard;

    public void lock(Playoff playoff) {
        try {
            var seasonId = playoff.getSeason().getId();
            seasons.findByIdForUpdate(seasonId).orElseThrow();
            races.findBySeasonForSelectionUpdate(seasonId);
            var matchups = entityManager.createQuery(
                    "select m from PlayoffMatchup m where m.round.playoff.id = :playoffId order by m.id", PlayoffMatchup.class)
                    .setParameter("playoffId", playoff.getId()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            for (var matchup : matchups) entityManager.refresh(matchup, LockModeType.PESSIMISTIC_WRITE);
        } catch (RuntimeException ex) {
            throw RaceSelectionConcurrency.translate(ex);
        }
    }

    public void requireValid(Playoff playoff, List<Participants> changes) {
        try {
            var seasonId = playoff.getSeason().getId();
            seasons.findByIdForUpdate(seasonId).orElseThrow();
            var rows = races.findBySeasonForSelectionUpdate(seasonId);
            var assignments = new ArrayList<RaceCarSelectionService.Assignment>();
            var newlyEligible = new ArrayList<Race>();
            for (var change : changes) {
                incidentGuard.requirePlayoffIdentity(change.matchup(), change.team1(), change.team2());
                penaltyGuard.requirePlayoffIdentity(change.matchup(), change.team1(), change.team2());
                disqualificationGuard.requirePlayoffIdentity(change.matchup(), change.team1(), change.team2());
                for (var race : rows) {
                    if (race.getPlayoffMatchup() == null || !race.getPlayoffMatchup().getId().equals(change.matchup().getId())) continue;
                    Team home = race.projectPlayoffHome(change.team1());
                    Team away = race.projectPlayoffAway(change.team2());
                    pickGuard.requireIdentity(race, race.getMatchday().getId(), home, away);
                    incidentGuard.requireIdentity(race, race.getMatchday().getId(), home, away);
                    penaltyGuard.requireIdentity(race, race.getMatchday().getId(), home, away);
                    disqualificationGuard.requireIdentity(race, race.getMatchday().getId(), home, away);
                    if (!PickTargetRules.isUnfinishedTarget(race) && PickTargetRules.isUnfinishedTarget(race, home, away)) newlyEligible.add(race);
                    var previousHome = race.getHomeTeam();
                    if (Objects.equals(previousHome == null ? null : previousHome.getId(), home == null ? null : home.getId())) continue;
                    assignments.add(new RaceCarSelectionService.Assignment(race.getId(), home == null ? null : home.getId(),
                            race.isBye() || change.matchup().isBye(),
                            race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId(),
                            race.getCar() == null ? null : race.getCar().getId()));
                }
            }
            var result = selections.validateAssignments(seasonId, assignments);
            if (!result.valid()) throw new BusinessRuleException(result.message());
            if (playoff.getSeason().isPicksCollectionEnabled()) newlyEligible.forEach(race -> race.setPicksManaged(true));
        } catch (RuntimeException ex) {
            throw RaceSelectionConcurrency.translate(ex);
        }
    }

    public record Participants(PlayoffMatchup matchup, Team team1, Team team2) {}
}
