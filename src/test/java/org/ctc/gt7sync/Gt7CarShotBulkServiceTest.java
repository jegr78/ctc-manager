package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.repository.CarRepository;
import org.ctc.gt7sync.Gt7CarShotService.Result;
import org.ctc.gt7sync.Gt7ScraperService.CarShotIndex;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Gt7CarShotBulkServiceTest {
    @Mock CarRepository cars;
    @Mock Gt7ScraperService scraper;
    @Mock Gt7CarShotService shots;
    @Mock ImportLockService imports;
    @Mock OutboundPolicy outbound;
    final List<Runnable> queued = new ArrayList<>();
    final CarShotIndex index = new CarShotIndex("index");
    final UUID first = UUID.randomUUID();
    final UUID second = UUID.randomUUID();
    final UUID third = UUID.randomUUID();
    Gt7CarShotBulkService service;

    @BeforeEach
    void setUp() {
        service = new Gt7CarShotBulkService(cars, scraper, shots, imports, outbound, queued::add,
                Clock.fixed(Instant.parse("2026-11-16T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void givenCarsWithoutGt7Images_whenRunning_thenLoadEachWithOneFetchedIndexAndCountTheOutcome() throws IOException {
        // given
        pending(first, second, third);
        when(cars.findWithoutGt7Images()).thenReturn(List.of(car(first, "car1"), car(UUID.randomUUID(), "carx"), car(second, "car2"), car(third, "car3")));
        when(scraper.carShotIndex()).thenReturn(index);
        when(imports.tryEnterWriter()).thenReturn(true);
        when(shots.load(first, index)).thenReturn(new Result(List.of("1_01", "2_01"), List.of(), List.of()));
        when(shots.load(second, index)).thenReturn(new Result(List.of(), List.of(), List.of()));
        when(shots.load(third, index)).thenThrow(new EntityNotFoundException("Car", third));
        // when
        var started = service.start();
        queued.getFirst().run();
        // then
        assertThat(started.message()).isEqualTo("Loading GT7 images: processed 0 of 3 cars, stored 0 images.");
        assertThat(service.status().message()).isEqualTo("GT7 image run finished: processed 3 of 3 cars, stored 2 images, "
                + "1 cars without GT7 shots, 1 cars deleted meanwhile.");
        verify(scraper, times(1)).carShotIndex();
        verify(imports, times(3)).exitWriter();
    }

    @Test
    void givenRestoreStartsDuringTheRun_whenRunning_thenStopBeforeTheNextCar() throws IOException {
        // given
        pending(first, second);
        when(scraper.carShotIndex()).thenReturn(index);
        when(imports.tryEnterWriter()).thenReturn(true, false);
        when(shots.load(first, index)).thenReturn(new Result(List.of("1_01"), List.of(), List.of()));
        // when
        service.start();
        queued.getFirst().run();
        // then
        assertThat(service.status().message()).isEqualTo("GT7 image run stopped (a backup restore started): processed 1 of 2 cars, stored 1 images.");
        verify(shots, never()).load(second, index);
        verify(imports, times(1)).exitWriter();
    }

    @Test
    void givenRepeatedFailures_whenRunning_thenStopAfterFiveCarsInARow() throws IOException {
        // given
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 7; i++) ids.add(UUID.randomUUID());
        pending(ids.toArray(UUID[]::new));
        when(scraper.carShotIndex()).thenReturn(index);
        when(imports.tryEnterWriter()).thenReturn(true);
        when(shots.load(any(), any())).thenThrow(new BusinessRuleException("GT7 images could not be loaded: HTTP 503"));
        // when
        service.start();
        queued.getFirst().run();
        // then
        var status = service.status();
        assertThat(status.message()).isEqualTo("GT7 image run stopped (5 cars in a row failed): processed 5 of 7 cars, stored 0 images, 5 failed.");
        assertThat(status.failures()).first().asString().isEqualTo(ids.getFirst() + ": GT7 images could not be loaded: HTTP 503");
    }

    @Test
    void givenUnreadableCarList_whenRunning_thenStopWithoutLoadingAnyCar() throws IOException {
        // given
        pending(first);
        when(scraper.carShotIndex()).thenThrow(new IOException("The GT7 car list no longer lists car shots in the expected format"));
        // when
        service.start();
        queued.getFirst().run();
        // then
        assertThat(service.status().message()).isEqualTo("GT7 image run stopped (the GT7 car list could not be read): processed 0 of 1 cars, stored 0 images.");
        verify(imports, never()).tryEnterWriter();
    }

    @Test
    void givenActiveRun_whenStartingAgainOrStopping_thenRejectTheSecondStartAndStopBeforeTheFirstCar() throws IOException {
        // given
        pending(first);
        service.start();
        // when / then
        assertThatThrownBy(() -> service.start()).hasMessage("GT7 images are already loading for all cars.");
        service.stop();
        when(scraper.carShotIndex()).thenReturn(index);
        queued.getFirst().run();
        assertThat(service.status().message()).isEqualTo("GT7 image run stopped (stopped by management): processed 0 of 1 cars, stored 0 images.");
        verify(shots, never()).load(any(), any());
        assertThatThrownBy(() -> service.stop()).hasMessage("No GT7 image run is active.");
    }

    @Test
    void givenNothingToLoadOrOutboundDisabled_whenStarting_thenRejectWithoutARun() {
        // given
        when(outbound.isEnabled()).thenReturn(true);
        when(cars.findWithoutGt7Images()).thenReturn(List.of(car(first, "notacarid")));
        // when / then
        assertThatThrownBy(() -> service.start()).hasMessage("Every car with a GT7 ID already has GT7 images.");
        when(outbound.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.start()).isInstanceOf(BusinessRuleException.class);
        assertThat(queued).isEmpty();
        assertThat(service.status()).isNull();
    }

    @Test
    void givenExecutorThatRejects_whenStarting_thenEndTheRunSoALaterStartIsPossible() {
        // given
        pending(first);
        service = new Gt7CarShotBulkService(cars, scraper, shots, imports, outbound, task -> {
            throw new java.util.concurrent.RejectedExecutionException("shut down");
        }, Clock.systemUTC());
        // when / then
        assertThatThrownBy(() -> service.start()).hasMessage("GT7 images cannot load while the application shuts down.");
        assertThat(service.status().running()).as("a rejected run must not block later starts").isFalse();
    }

    private void pending(UUID... ids) {
        when(outbound.isEnabled()).thenReturn(true);
        var rows = new ArrayList<CarRepository.Gt7CarId>();
        for (int i = 0; i < ids.length; i++) rows.add(car(ids[i], "car" + (i + 1)));
        when(cars.findWithoutGt7Images()).thenReturn(rows);
    }

    private static CarRepository.Gt7CarId car(UUID id, String gt7Id) {
        return new CarRepository.Gt7CarId() {
            public UUID getId() { return id; }
            public String getGt7Id() { return gt7Id; }
        };
    }
}
