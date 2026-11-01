package org.ctc.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.springframework.stereotype.Repository;

@Repository
public class CarLockingRepositoryImpl implements CarLockingRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Car> findAllByGt7IdForUpdate(String gt7Id) {
        return lockAndRefresh(entityManager.createQuery(
                "select c from Car c where c.gt7Id = :gt7Id order by c.id", Car.class)
                .setParameter("gt7Id", gt7Id));
    }

    @Override
    public Optional<Car> findByIdForUpdate(UUID id) {
        return lockAndRefresh(entityManager.createQuery(
                "select c from Car c where c.id = :id", Car.class)
                .setParameter("id", id)).stream().findFirst();
    }

    private List<Car> lockAndRefresh(TypedQuery<Car> query) {
        var cars = query.setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        // A locking query alone does not replace an entity already in the first-level cache.
        for (Car car : cars) entityManager.refresh(car, LockModeType.PESSIMISTIC_WRITE);
        return cars;
    }
}
