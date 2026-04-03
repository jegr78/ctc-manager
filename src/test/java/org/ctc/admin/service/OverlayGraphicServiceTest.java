package org.ctc.admin.service;

import org.ctc.domain.model.Race;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverlayGraphicServiceTest {

    @TempDir
    Path tempDir;

    private OverlayGraphicService createService() {
        return new OverlayGraphicService(null, null, null, tempDir.toString());
    }

    @Test
    void givenRaceWithNoTeams_whenGenerateOverlay_thenThrowsIllegalState() {
        // given
        var service = createService();
        var race = new Race();

        // when / then
        assertThatThrownBy(() -> service.generateOverlay(race))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no teams");
    }

    @Test
    void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
        // given
        var service = createService();

        // when / then
        assertThat(service.hasCustomTemplate()).isFalse();
    }

    @Test
    void givenNoCustomTemplate_whenSaveTemplate_thenCustomTemplateExistsAndCanBeLoaded() throws IOException {
        // given
        var service = createService();

        // when
        service.saveTemplate("<html>custom overlay</html>");

        // then
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom overlay</html>");
    }

    @Test
    void givenSavedCustomTemplate_whenResetTemplate_thenNoCustomTemplateExists() throws IOException {
        // given
        var service = createService();
        service.saveTemplate("<html>custom overlay</html>");

        // when
        service.resetTemplate();

        // then
        assertThat(service.hasCustomTemplate()).isFalse();
    }

    @Test
    void whenLoadDefaultTemplate_thenReturnsNonEmptyHtml() throws IOException {
        // given
        var service = createService();

        // when
        String template = service.loadDefaultTemplate();

        // then
        assertThat(template).isNotEmpty();
        assertThat(template).contains("transparent");
        assertThat(template).contains("1920px");
    }
}
