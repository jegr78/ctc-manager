package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.ctc.domain.service.CarCutoutService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.cutout.CutoutModel;
import org.ctc.media.job.MediaJobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void givenCrashBetweenSwapMoves_whenRemoveInterruptedWork_thenPreviousTrailerMovedBack(@TempDir Path uploads) throws Exception {
        // given
        var files = mock(FileStorageService.class);
        when(files.resolve(anyString())).thenAnswer(call -> uploads.resolve(call.<String>getArgument(0).substring("/uploads/".length())));
        String id = UUID.randomUUID().toString();
        Path parent = Files.createDirectories(uploads.resolve("trailers/matchday"));
        Path old = Files.createDirectories(parent.resolve(".old-" + id + "-" + UUID.randomUUID()));
        Files.writeString(old.resolve("trailer.mp4"), "previous");
        Path staged = Files.createDirectories(parent.resolve(".new-" + UUID.randomUUID()));
        var service = service(files);

        // when
        service.removeInterruptedWork();

        // then
        assertThat(parent.resolve(id).resolve("trailer.mp4")).hasContent("previous");
        assertThat(old).doesNotExist();
        assertThat(staged).doesNotExist();
    }

    @Test
    void givenOldDirectoryNextToCurrentTrailer_whenRemoveInterruptedWork_thenOldDeletedAndCurrentKept(@TempDir Path uploads)
            throws Exception {
        // given
        var files = mock(FileStorageService.class);
        when(files.resolve(anyString())).thenAnswer(call -> uploads.resolve(call.<String>getArgument(0).substring("/uploads/".length())));
        String id = UUID.randomUUID().toString();
        Path parent = Files.createDirectories(uploads.resolve("trailers/round"));
        Files.writeString(Files.createDirectories(parent.resolve(id)).resolve("trailer.mp4"), "current");
        Path old = Files.createDirectories(parent.resolve(".old-" + id + "-" + UUID.randomUUID()));

        // when
        service(files).removeInterruptedWork();

        // then
        assertThat(parent.resolve(id).resolve("trailer.mp4")).hasContent("current");
        assertThat(old).doesNotExist();
    }

    private static TrailerService service(FileStorageService files) {
        return new TrailerService(mock(TrailerPlanService.class), mock(TrailerSceneRenderer.class), mock(TrailerVideoComposer.class),
                mock(CarCutoutService.class), mock(CutoutModel.class), mock(MusicLibraryService.class), mock(MediaJobExecutor.class),
                files, Clock.systemUTC());
    }
}
