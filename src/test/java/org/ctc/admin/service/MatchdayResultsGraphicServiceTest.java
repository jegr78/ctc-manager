package org.ctc.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MatchdayResultsGraphicServiceTest {

    @TempDir
    Path tempDir;

    private MatchdayResultsGraphicService createService() {
        return new MatchdayResultsGraphicService(null, null, null, tempDir.toString());
    }

    @Test
    void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
        assertThat(createService().hasCustomTemplate()).isFalse();
    }

    @Test
    void givenSavedTemplate_whenLoadTemplate_thenReturnsCustomContent() throws IOException {
        var service = createService();
        service.saveTemplate("<html>custom results</html>");
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom results</html>");
    }

    @Test
    void givenCustomTemplate_whenResetTemplate_thenCustomRemoved() throws IOException {
        var service = createService();
        service.saveTemplate("<html>custom</html>");
        service.resetTemplate();
        assertThat(service.hasCustomTemplate()).isFalse();
    }
}
