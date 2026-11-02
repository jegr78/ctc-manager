package org.ctc.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.springframework.stereotype.Repository;

@Repository
public class RaceSelectionLockingRepositoryImpl implements RaceSelectionLockingRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Race> findBySeasonForSelectionUpdate(UUID seasonId) {
        entityManager.flush();
        var rows = entityManager.createQuery("select r from Race r where r.matchday.phase.season.id = :seasonId order by r.id", Race.class)
                .setParameter("seasonId", seasonId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (var race : rows) entityManager.refresh(race, LockModeType.PESSIMISTIC_WRITE);
        var matches = new TreeMap<UUID, Match>();
        var matchups = new TreeMap<UUID, PlayoffMatchup>();
        for (var race : rows) {
            if (race.getMatch() != null) matches.putIfAbsent(race.getMatch().getId(), race.getMatch());
            if (race.getPlayoffMatchup() != null) matchups.putIfAbsent(race.getPlayoffMatchup().getId(), race.getPlayoffMatchup());
        }
        for (var match : matches.values()) {
            entityManager.refresh(match, LockModeType.PESSIMISTIC_WRITE);
        }
        for (var matchup : matchups.values()) {
            entityManager.refresh(matchup, LockModeType.PESSIMISTIC_WRITE);
        }
        return rows;
    }

    @Override
    public Optional<Race> findByIdForSelectionUpdate(UUID raceId) {
        entityManager.flush();
        var rows = entityManager.createQuery("select r from Race r where r.id = :raceId", Race.class)
                .setParameter("raceId", raceId).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for (var race : rows) entityManager.refresh(race, LockModeType.PESSIMISTIC_WRITE);
        return rows.stream().findFirst();
    }
}
