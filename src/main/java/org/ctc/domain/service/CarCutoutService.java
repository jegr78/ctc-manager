package org.ctc.domain.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.CarImageKind;
import org.ctc.media.cutout.CutoutModel;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobKind;
import org.ctc.media.job.MediaJobStatus;
import org.springframework.stereotype.Service;

/**
 * Cuts the car out of its first studio image and stores the result as the car's automatic cutout. Inference runs
 * outside any transaction; only the save takes the restore writer admission.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarCutoutService {

    private final CatalogImageService gallery;
    private final FileStorageService files;
    private final CutoutModel model;
    private final ImportLockService imports;
    private final MediaJobExecutor jobs;

    public static String scopeKey(UUID carId) {
        return "car:" + carId;
    }

    /** Whether the gallery may offer the action: the model is installed and the car has a studio image. */
    public boolean available(UUID carId) {
        return model.isAvailable() && gallery.firstCarImage(carId, CarImageKind.STUDIO).isPresent();
    }

    /** Runs {@link #createCutout(UUID)} as a background media job and releases the model afterwards. */
    public MediaJobStatus start(UUID carId) {
        return jobs.submit(MediaJobKind.CUTOUT, scopeKey(carId), ctx -> {
            try {
                ctx.progress(1, 1, "Creating the cutout");
                if (!createCutout(carId)) throw new BusinessRuleException("The car has no studio image.");
                ctx.progress(1, 1, "Cutout created.");
            } finally {
                model.release();
            }
        });
    }

    /** @return false when the car has no studio image */
    public boolean createCutout(UUID carId) {
        var studio = gallery.firstCarImage(carId, CarImageKind.STUDIO);
        if (studio.isEmpty()) return false;
        byte[] png;
        int width;
        int height;
        try {
            var source = ImageIO.read(files.resolve(studio.get().getImagePath()).toFile());
            if (source == null) throw new BusinessRuleException("The studio image cannot be read for a cutout.");
            var cut = model.cutout(source);
            width = cut.getWidth();
            height = cut.getHeight();
            var out = new ByteArrayOutputStream();
            ImageIO.write(cut, "png", out);
            png = out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("The studio image cannot be read for a cutout.");
        }
        if (!imports.tryEnterWriter()) throw new BusinessRuleException("A restore is running.");
        try {
            gallery.applyAutoCutout(carId, files.storeBytes("car-images", carId, "cutout.png", png), width, height);
            return true;
        } catch (IOException e) {
            throw new BusinessRuleException("The cutout could not be stored.");
        } finally {
            imports.exitWriter();
        }
    }
}
