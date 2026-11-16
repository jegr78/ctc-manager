package org.ctc.gt7sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.CatalogImageService.Gt7Shot;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.ImageContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Loads the official GT7 car shots of one car into its gallery. Scraping and downloads run outside any transaction;
 * new files are removed when a step before applying fails, and by the apply transaction on rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Gt7CarShotService {
    private final CarRepository cars;
    private final Gt7ScraperService scraper;
    private final FileStorageService files;
    private final CatalogImageService gallery;

    /** Loads one car's shots inside a request that already holds the restore writer admission. */
    public Result load(UUID carId) {
        return load(carId, scraper::scrapeCarShots, Admission.HELD);
    }

    /**
     * Loads one car's shots from a car list bundle fetched once for a bulk run. Downloads run before {@code admission} is
     * entered, so a restore waits only for storing and applying.
     */
    public Result load(UUID carId, Gt7ScraperService.CarShotIndex index, Admission admission) {
        return load(carId, gt7Id -> scraper.scrapeCarShots(index, gt7Id), admission);
    }

    private Result load(UUID carId, ShotLister lister, Admission admission) {
        var car = cars.findById(carId).orElseThrow(() -> new EntityNotFoundException("Car", carId));
        String gt7Id = car.getGt7Id();
        if (!Gt7ScraperService.isCarId(gt7Id)) throw new BusinessRuleException("This car has no GT7 ID");
        var current = gallery.gt7Slots(carId);
        var downloaded = new ArrayList<Download>();
        var unchanged = new ArrayList<String>();
        try {
            for (var shot : lister.shots(gt7Id)) {
                byte[] data = files.downloadImage(shot.imageUrl(), "gt7.jpg");
                String stored = current.get(shot.slot());
                if (stored != null && files.hasContent(stored, data)) unchanged.add(shot.slot());
                else downloaded.add(new Download(shot.slot(), data, ImageContent.dimensions(data)));
            }
        } catch (IOException | RuntimeException failure) {
            log.warn("Loading GT7 images for car {} failed", carId, failure);
            throw new BusinessRuleException("GT7 images could not be loaded: " + (failure instanceof IOException ? failure.getMessage() : "the GT7 data is not readable"));
        }
        if (!admission.enter()) throw new WriterRefusedException();
        try {
            return store(carId, downloaded, unchanged);
        } finally {
            admission.exit();
        }
    }

    private Result store(UUID carId, List<Download> downloaded, List<String> unchanged) {
        var staged = new ArrayList<Gt7Shot>();
        try {
            for (var shot : downloaded) {
                staged.add(new Gt7Shot(shot.slot(), files.storeBytes("car-images", carId, "gt7_" + shot.slot() + ".jpg", shot.data()),
                        shot.size().width(), shot.size().height()));
            }
        } catch (IOException | RuntimeException failure) {
            staged.forEach(shot -> files.delete(shot.imagePath()));
            log.warn("Storing GT7 images for car {} failed", carId, failure);
            throw new BusinessRuleException("GT7 images could not be saved; try again.");
        }
        try {
            var applied = gallery.applyGt7CarImages(carId, staged);
            return new Result(applied.added(), applied.replaced(), List.copyOf(unchanged));
        } catch (CannotCreateTransactionException notStarted) {
            staged.forEach(shot -> files.delete(shot.imagePath()));
            log.warn("Saving GT7 images for car {} could not start", carId, notStarted);
            throw new BusinessRuleException("GT7 images could not be saved; try again.");
        } catch (RuntimeException failure) {
            log.warn("Saving GT7 images for car {} failed", carId, failure);
            throw new BusinessRuleException("GT7 images could not be saved; try again.");
        }
    }

    /** The restore writer admission around storing and applying; {@link #HELD} when the caller already holds it. */
    public interface Admission {
        Admission HELD = new Admission() {
            public boolean enter() { return true; }
            public void exit() { }
        };

        boolean enter();

        void exit();
    }

    /** Thrown when a backup restore holds the import lock, before any file is stored. */
    public static final class WriterRefusedException extends RuntimeException {
        WriterRefusedException() { super("A backup restore is in progress."); }
    }

    private record Download(String slot, byte[] data, ImageContent.Dimensions size) { }

    private interface ShotLister {
        List<Gt7ScraperService.CarShot> shots(String gt7Id) throws IOException;
    }

    public record Result(List<String> added, List<String> replaced, List<String> unchanged) {
        public boolean empty() { return added.isEmpty() && replaced.isEmpty() && unchanged.isEmpty(); }

        public String message() {
            if (empty()) return "GT7 lists no car shots for this car; nothing changed.";
            return "GT7 images: added " + slots(added) + "; replaced " + slots(replaced) + "; unchanged " + slots(unchanged) + ".";
        }

        private static String slots(List<String> slots) { return slots.isEmpty() ? "none" : String.join(", ", slots); }
    }
}
