package org.ctc.gt7sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.CatalogImageService.Gt7Shot;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.ImageContent;
import org.springframework.stereotype.Service;

/**
 * Loads the official GT7 car shots of one car into its gallery. Scraping and downloads run outside any transaction;
 * new files are removed again when applying them fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Gt7CarShotService {
    private final CarRepository cars;
    private final Gt7ScraperService scraper;
    private final FileStorageService files;
    private final CatalogImageService gallery;

    public Result load(UUID carId) {
        var car = cars.findById(carId).orElseThrow(() -> new EntityNotFoundException("Car", carId));
        String gt7Id = car.getGt7Id();
        if (gt7Id == null || !gt7Id.matches("\\d{1,6}")) throw new BusinessRuleException("This car has no GT7 ID");
        var current = gallery.carImages(carId).stream().filter(image -> image.getSource() == ImageSource.GT7).toList();
        var staged = new ArrayList<Gt7Shot>();
        var unchanged = new ArrayList<String>();
        try {
            for (var shot : scraper.scrapeCarShots(gt7Id)) {
                byte[] data = files.downloadImage(shot.imageUrl(), "gt7.jpg");
                if (current.stream().anyMatch(image -> shot.slot().equals(image.getSourceKey()) && files.hasContent(image.getImagePath(), data))) {
                    unchanged.add(shot.slot());
                    continue;
                }
                var size = ImageContent.dimensions(data);
                String path = files.storeBytes("car-images", carId, "gt7_" + shot.slot() + ".jpg", data);
                staged.add(new Gt7Shot(shot.slot(), path, size.width(), size.height()));
            }
            var applied = gallery.applyGt7CarImages(carId, staged);
            return new Result(applied.added(), applied.replaced(), List.copyOf(unchanged));
        } catch (IOException | RuntimeException failure) {
            staged.forEach(shot -> files.delete(shot.imagePath()));
            if (failure instanceof BusinessRuleException || failure instanceof EntityNotFoundException) throw (RuntimeException) failure;
            log.warn("Loading GT7 images for car {} failed", carId, failure);
            throw new BusinessRuleException("GT7 images could not be loaded: " + failure.getMessage());
        }
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
