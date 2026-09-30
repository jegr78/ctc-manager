package org.ctc.backup.it;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pauses an admin POST after the write rejector admitted it and checks that a backup restore
 * waits for it, rejects new writers meanwhile, and aborts before any change when it does not finish.
 */
@SpringBootTest(properties = "app.backup.writer-drain-timeout=PT1S")
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Tag("integration")
class ImportInFlightWriterIT {

	private static final String PAUSED_WRITE = "/admin/test-paused-write";

	@Autowired MockMvc mockMvc;
	@Autowired ImportLockService importLockService;
	@Autowired CarRepository carRepository;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private CompletableFuture<Void> writer;

	@BeforeEach
	void resetLatches() {
		PausedWriterController.entered = new CountDownLatch(1);
		PausedWriterController.release = new CountDownLatch(1);
	}

	@AfterEach
	void releaseWriterAndLock() throws Exception {
		PausedWriterController.release.countDown();
		if (writer != null) {
			writer.get(10, TimeUnit.SECONDS);
		}
		importLockService.unlock();
		carRepository.findAll().stream()
				.filter(car -> car.getManufacturer().equals("Test_Drain"))
				.forEach(carRepository::delete);
	}

	@Test
	void givenAdmittedWriter_whenRestoreDrainsWriters_thenNewWritesAreRejectedAndTheRestoreStartsAfterItsCommit()
			throws Exception {
		// given
		startPausedWriter("before-" + id);
		assertThat(importLockService.tryLock()).as("import lock").isTrue();

		// when
		var drained = CompletableFuture.runAsync(() -> {
			try {
				importLockService.awaitWritersDrained();
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});

		// then
		Thread.sleep(300);
		assertThat(drained).as("the restore must wait for the admitted writer").isNotDone();
		mockMvc.perform(post(PAUSED_WRITE).param("model", "during-" + id))
				.andExpect(status().isServiceUnavailable());
		PausedWriterController.release.countDown();
		drained.get(5, TimeUnit.SECONDS);
		assertThat(carNamed("before-" + id)).as("the admitted write committed before the restore began").isTrue();
		assertThat(carNamed("during-" + id)).as("a write that arrived while pending").isFalse();
	}

	@Test
	void givenWriterStillRunning_whenImportExecutes_thenAbortedBeforeAnyChangeAndTheLockIsFree() throws Exception {
		// given
		startPausedWriter("slow-" + id);
		long carsBefore = carRepository.count();

		// when
		mockMvc.perform(post("/admin/backup/import-execute")
						.param("stagingId", UUID.randomUUID().toString())
						.param("acknowledged", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage",
						"Import aborted, changes that started before the import are still being saved. "
								+ "No database changes. Try again in a moment."));

		// then
		assertThat(importLockService.isLocked()).as("the aborted import releases its lock").isFalse();
		assertThat(carRepository.count()).as("no table was wiped").isEqualTo(carsBefore);
		PausedWriterController.release.countDown();
		writer.get(10, TimeUnit.SECONDS);
		assertThat(carNamed("slow-" + id)).as("the slow writer still commits").isTrue();
	}

	private void startPausedWriter(String model) throws InterruptedException {
		writer = CompletableFuture.runAsync(() -> {
			try {
				mockMvc.perform(post(PAUSED_WRITE).param("model", model)).andExpect(status().isOk());
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
		assertThat(PausedWriterController.entered.await(5, TimeUnit.SECONDS)).as("writer admitted").isTrue();
	}

	private boolean carNamed(String model) {
		return carRepository.findAll().stream().anyMatch(car -> car.getName().equals(model));
	}

	@TestConfiguration
	static class PausedWriterConfig {

		@Bean
		PausedWriterController pausedWriterController(CarRepository carRepository) {
			return new PausedWriterController(carRepository);
		}
	}

	@RestController
	static class PausedWriterController {

		static volatile CountDownLatch entered = new CountDownLatch(1);
		static volatile CountDownLatch release = new CountDownLatch(1);

		private final CarRepository carRepository;

		PausedWriterController(CarRepository carRepository) {
			this.carRepository = carRepository;
		}

		@PostMapping(PAUSED_WRITE)
		String write(@RequestParam String model) throws InterruptedException {
			entered.countDown();
			release.await(10, TimeUnit.SECONDS);
			carRepository.save(new Car("Test_Drain", model));
			return "ok";
		}
	}
}
