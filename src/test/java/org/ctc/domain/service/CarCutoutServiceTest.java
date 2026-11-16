package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.media.cutout.CutoutModel;
import org.ctc.media.job.MediaJobAbandonedException;
import org.ctc.media.job.MediaJobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CarCutoutServiceTest {

    @TempDir Path dir;

    @Test
    void givenRestoreDuringInference_whenCreateCutout_thenAbandonedWithoutSaving() throws Exception {
        // given
        var gallery = mock(CatalogImageService.class);
        var files = mock(FileStorageService.class);
        var model = mock(CutoutModel.class);
        var epoch = new PickSessionEpoch();
        var service = new CarCutoutService(gallery, files, model, new ImportLockService(), mock(MediaJobExecutor.class), epoch);
        UUID car = UUID.randomUUID();
        Path studioFile = dir.resolve("studio.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", studioFile.toFile());
        var studio = new CarImage();
        studio.setId(UUID.randomUUID());
        studio.setImagePath("/uploads/car-images/x/studio.png");
        when(gallery.firstCarImage(car, CarImageKind.STUDIO)).thenReturn(Optional.of(studio));
        when(files.resolve(studio.getImagePath())).thenReturn(studioFile);
        when(model.cutout(any())).thenAnswer(call -> {
            epoch.afterRestore(null);
            return new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        });

        // when / then
        assertThatThrownBy(() -> service.createCutout(car)).isInstanceOf(MediaJobAbandonedException.class);
        verify(gallery, never()).applyAutoCutout(any(), anyString(), anyInt(), anyInt());
        verify(files, never()).storeBytes(anyString(), any(), anyString(), any());
    }
}
