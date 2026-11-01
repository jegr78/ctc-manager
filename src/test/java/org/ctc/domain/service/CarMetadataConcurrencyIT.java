package org.ctc.domain.service;

import java.util.List;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
import org.ctc.domain.repository.CarRepository;
import org.ctc.gt7sync.Gt7ScraperService.ScrapedCar;
import org.ctc.gt7sync.GtPlusCarYearSource.YearCandidate;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@CtcDevSpringBootContext
@Tag("integration")
class CarMetadataConcurrencyIT {
    @Autowired private CarService service;
    @Autowired private CarMetadataService metadataService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private CarRepository cars;

    @Test
    void givenSyncDuringManualSave_whenCheckingDuplicateName_thenMetadataCannotBeLostWithoutAnOverride() {
        // given
        var tx = new TransactionTemplate(transactionManager);
        var writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String manufacturer = "Test_ConcurrentMetadata_" + UUID.randomUUID();
        UUID id = tx.execute(status -> cars.saveAndFlush(new Car(manufacturer, "Test Car '20")).getId());
        var syncCompleted = new AtomicBoolean();
        Runnable sync = () -> writer.executeWithoutResult(status -> {
            jdbc.execute("SET LOCK_TIMEOUT 250");
            try {
                var car = cars.findByIdForUpdate(id).orElseThrow();
                metadataService.applyImported(car, metadataService.propose(car,
                        new ScrapedCar("car999998", manufacturer, "Test Car '20", "", CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR"),
                        new YearCandidate(2020, "GTPlus candidate 2020", null)));
                cars.saveAndFlush(car);
            } finally {
                jdbc.execute("SET LOCK_TIMEOUT 2000");
            }
        });
        doAnswer(invocation -> {
            List<Car> loaded = entityManager.createQuery(
                    "select c from Car c where c.manufacturer = :manufacturer and c.name = :name", Car.class)
                    .setParameter("manufacturer", manufacturer).setParameter("name", "Test Car '20").getResultList();
            try {
                // A second transaction runs while the manual transaction is paused at its name check.
                sync.run();
                syncCompleted.set(true);
            } catch (PessimisticLockingFailureException expected) {
                // The manual save already holds the row lock; the source writer must retry afterward.
            }
            return loaded;
        }).when(cars).findAllByManufacturerAndName(manufacturer, "Test Car '20");
        try {
            // when
            service.save(id, manufacturer, "Test Car '20",
                    new CarMetadataService.ManualMetadata(CarCategory.GR_4, null, null, false, false, false));
            if (!syncCompleted.get()) sync.run();
            // then
            tx.executeWithoutResult(status -> {
                var saved = cars.findById(id).orElseThrow();
                assertThat(saved.getModelYear() != null || saved.isModelYearOverride())
                        .as("a source year survives, or a deliberate clear is protected by an explicit override").isTrue();
                assertThat(saved.getCategory()).isEqualTo(CarCategory.GR_4);
                assertThat(saved.isCategoryOverride()).isTrue();
            });
        } finally {
            tx.executeWithoutResult(status -> cars.deleteById(id));
        }
    }
}
