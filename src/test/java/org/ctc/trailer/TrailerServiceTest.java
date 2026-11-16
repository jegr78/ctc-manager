package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.ctc.domain.service.CarCutoutService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.cutout.CutoutModel;
import org.ctc.media.job.MediaJobExecutor;
import org.junit.jupiter.api.Test;

class TrailerServiceTest {

    @Test
    void givenStorageWithoutResolvableTrailerFolder_whenRemoveInterruptedWork_thenStartupContinues() {
        // given
        var service = new TrailerService(mock(TrailerPlanService.class), mock(TrailerSceneRenderer.class), mock(TrailerVideoComposer.class),
                mock(CarCutoutService.class), mock(CutoutModel.class), mock(MusicLibraryService.class), mock(MediaJobExecutor.class),
                mock(FileStorageService.class), Clock.systemUTC());

        // when / then
        assertThatCode(service::removeInterruptedWork).as("startup cleanup never fails the application start").doesNotThrowAnyException();
    }
}
