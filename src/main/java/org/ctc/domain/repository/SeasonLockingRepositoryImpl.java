package org.ctc.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Season;
import org.springframework.stereotype.Repository;

@Repository
public class SeasonLockingRepositoryImpl implements SeasonLockingRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Season> findByIdForUpdate(UUID id) {
        entityManager.flush();
        var rows = entityManager.createQuery("select s from Season s where s.id = :id", Season.class)
                .setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        // Refresh stale request state after acquiring the database lock.
        for (var season : rows) entityManager.refresh(season, LockModeType.PESSIMISTIC_WRITE);
        return rows.stream().findFirst();
    }
}
