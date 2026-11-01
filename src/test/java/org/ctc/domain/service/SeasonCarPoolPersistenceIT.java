package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class SeasonCarPoolPersistenceIT {
    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired RaceRepository races;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired SeasonCarPoolMemberRepository members;
    @Autowired SeasonCarPoolService pools;
    @Autowired CarService carService;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestHelper helper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SeasonManagementService seasonManagement;
    private final java.util.List<UUID> createdCars = new java.util.ArrayList<>();

    @Test
    @Transactional
    void givenFrozenPool_whenCatalogChanges_thenStoredMembershipAndIdentifiersRemain() {
        var season = newSeason();
        var first = car("Original", CarCategory.GR_2, 2016);
        var entryId = pools.saveEntry(season.getId(), null, "2016", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
        pools.freeze(season.getId());
        var originalMemberId = entries.findById(entryId).orElseThrow().getMembers().getFirst().getId();
        first.setCategory(CarCategory.GR_4);
        cars.saveAndFlush(first);
        var later = car("Later", CarCategory.GR_2, 2016);
        em.clear();
        assertThat(pools.preview(season.getId()).entries().getFirst().carIds()).containsExactly(first.getId()).doesNotContain(later.getId());
        assertThat(entries.findById(entryId).orElseThrow().getMembers().getFirst().getId()).isEqualTo(originalMemberId);
        assertThatThrownBy(() -> carService.delete(first.getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("snapshot");
    }

    @Test
    @Transactional
    void givenForeignEntry_whenEditingOrRemoving_thenOtherSeasonRemainsUnchanged() {
        var owner = newSeason();
        var other = newSeason();
        var id = pools.saveEntry(owner.getId(), null, "Owner", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, null)));
        assertThatThrownBy(() -> pools.saveEntry(other.getId(), id, "Forged", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_4, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("belong");
        assertThat(entries.findById(id).orElseThrow().getLabel()).isEqualTo("Owner");
    }

    @Test
    @Transactional
    void givenFrozenEntryUsedByRace_whenReopenModeChangeOrEditIsForged_thenReject() {
        var fixture = helper.createFullSeasonFixture("UsedPool " + UUID.randomUUID().toString().substring(0, 8));
        pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
        var selected = car("Used", CarCategory.GR_2, 2016);
        var id = pools.saveEntry(fixture.season().getId(), null, "Used entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
        pools.freeze(fixture.season().getId());
        fixture.race().setCarPoolEntry(entries.findById(id).orElseThrow());
        fixture.race().setCar(selected);
        races.saveAndFlush(fixture.race());
        assertThatThrownBy(() -> pools.reopen(fixture.season().getId())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("used");
        assertThatThrownBy(() -> pools.changeMode(fixture.season().getId(), CarPoolMode.FIXED_CARS)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> pools.removeEntry(fixture.season().getId(), id)).isInstanceOf(BusinessRuleException.class);
        assertThat(entries.findById(id)).isPresent();
    }

    @Test
    void givenInvalidPool_whenFreezeFails_thenEveryEntryAndStateRollsBack() {
        var tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> {
            var season = newSeason();
            car("Rollback", CarCategory.GR_2, 2016);
            pools.saveEntry(season.getId(), null, "Valid", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
            pools.saveEntry(season.getId(), null, "Empty", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_X, null, 1000)));
            return season.getId();
        });
        try {
            assertThatThrownBy(() -> pools.freeze(id)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Empty");
            tx.executeWithoutResult(status -> {
                var season = seasons.findById(id).orElseThrow();
                assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
                assertThat(season.getCarPoolEntries()).allSatisfy(entry -> assertThat(entry.getMembers()).isEmpty());
            });
        } finally {
            tx.executeWithoutResult(status -> {
                seasons.deleteById(id);
                seasons.flush();
                createdCars.forEach(cars::deleteById);
            });
        }
    }

    @Test
    void givenCachedSeasonAndConcurrentFreeze_whenSavingDetails_thenFrozenStateCannotBeOverwritten() {
        var tx = new TransactionTemplate(transactionManager);
        var writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID id = tx.execute(status -> {
            var season = newSeason();
            car("Metadata writer", CarCategory.GR_2, 2016);
            pools.saveEntry(season.getId(), null, "Keep", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
            return season.getId();
        });
        try {
            tx.executeWithoutResult(status -> {
                var cached = seasons.findById(id).orElseThrow();
                writer.executeWithoutResult(inner -> pools.freeze(id));
                assertThat(cached.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
                var saved = seasonManagement.save(id, "Changed details", 2030, 1, "Keep frozen", false);
                assertThat(saved.getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
            });
            tx.executeWithoutResult(status -> {
                var persisted = seasons.findById(id).orElseThrow();
                assertThat(persisted.getName()).isEqualTo("Changed details");
                assertThat(persisted.getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
                assertThat(persisted.getCarPoolEntries().getFirst().getMembers()).isNotEmpty();
            });
        } finally {
            tx.executeWithoutResult(status -> {
                seasons.deleteById(id);
                seasons.flush();
                createdCars.forEach(cars::deleteById);
            });
        }
    }

    @Test
    void givenCachedDraftAndConcurrentFreeze_whenSavingEntry_thenFreshLockedStateRejectsEdit() {
        var tx = new TransactionTemplate(transactionManager);
        var writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID id = tx.execute(status -> {
            var season = newSeason();
            car("Fresh lock", CarCategory.GR_2, 2016);
            pools.saveEntry(season.getId(), null, "Keep", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
            return season.getId();
        });
        try {
            tx.executeWithoutResult(status -> {
                var cached = seasons.findById(id).orElseThrow();
                cached.getCarPoolEntries().size();
                writer.executeWithoutResult(inner -> pools.freeze(id));
                assertThat(cached.getCarPoolState()).isEqualTo(CarPoolState.DRAFT);
                assertThatThrownBy(() -> pools.saveEntry(id, null, "Stale write", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, null))))
                        .isInstanceOf(BusinessRuleException.class).hasMessageContaining("frozen");
                status.setRollbackOnly();
            });
            tx.executeWithoutResult(status -> {
                var season = seasons.findById(id).orElseThrow();
                assertThat(season.getCarPoolState()).isEqualTo(CarPoolState.FROZEN);
                assertThat(season.getCarPoolEntries()).extracting(SeasonCarPoolEntry::getLabel).containsExactly("Keep");
            });
        } finally {
            tx.executeWithoutResult(status -> {
                seasons.deleteById(id);
                seasons.flush();
                createdCars.forEach(cars::deleteById);
            });
        }
    }

    @Test
    @Transactional
    void givenDuplicateSnapshotMembership_whenPersisted_thenDatabaseRejectsDuplicatePair() {
        var season = newSeason();
        var car = car("Unique member", CarCategory.GR_2, 2016);
        var id = pools.saveEntry(season.getId(), null, "Unique", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_2, null, 2016)));
        pools.freeze(season.getId());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO season_car_pool_members (id, entry_id, car_id) VALUES (?, ?, ?)", UUID.randomUUID(), id, car.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO season_car_pool_clauses (id, entry_id, category, sort_index) VALUES (?, ?, ?, 0)", UUID.randomUUID(), UUID.randomUUID(), "GR_3"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Season newSeason() {
        var season = new Season("Test Pool " + UUID.randomUUID(), 2030, 1);
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        return seasons.saveAndFlush(season);
    }

    private Car car(String name, CarCategory category, Integer year) {
        var car = new Car("Test Pool " + UUID.randomUUID(), name);
        car.setCategory(category);
        car.setModelYear(year);
        var saved = cars.saveAndFlush(car);
        createdCars.add(saved.getId());
        return saved;
    }
}
