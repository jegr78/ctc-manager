package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.gt7sync.Gt7CarShotBulkService;
import org.ctc.gt7sync.Gt7CarShotBulkService.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class Gt7CarShotBulkControllerTest {
    @Mock Gt7CarShotBulkService bulk;
    @InjectMocks Gt7CarShotBulkController controller;
    final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
    final Status running = new Status(true, 3, 1, 4, 0, 0, 0, List.of(), null, Instant.EPOCH, null);

    @Test
    void givenPendingCars_whenStarting_thenReturnToTheCarListWithTheRunStatus() {
        // given
        when(bulk.start()).thenReturn(running);
        // when
        String view = controller.start(redirect);
        // then
        assertThat(view).isEqualTo("redirect:/admin/cars#gt7-car-images");
        assertThat(flash()).containsEntry("successMessage", "Started: Loading GT7 images: processed 1 of 3 cars, stored 4 images.");
    }

    @Test
    void givenActiveRun_whenStartingOrStoppingWithoutRun_thenShowTheReasonAsError() {
        // given
        when(bulk.start()).thenThrow(new BusinessRuleException("GT7 images are already loading for all cars."));
        doThrow(new BusinessRuleException("No GT7 image run is active.")).when(bulk).stop();
        // when
        controller.start(redirect);
        var stopRedirect = new RedirectAttributesModelMap();
        controller.stop(stopRedirect);
        // then
        assertThat(flash()).containsEntry("errorMessage", "GT7 images are already loading for all cars.").doesNotContainKey("successMessage");
        assertThat(new HashMap<String, Object>(stopRedirect.getFlashAttributes())).containsEntry("errorMessage", "No GT7 image run is active.");
    }

    @Test
    void givenRunningOrNoRun_whenPollingTheStatus_thenReturnRunningAndMessage() {
        // given
        when(bulk.status()).thenReturn(running, (Status) null);
        // when / then
        assertThat(controller.status()).isEqualTo(Map.of("running", true, "message", "Loading GT7 images: processed 1 of 3 cars, stored 4 images."));
        assertThat(controller.status()).isEqualTo(Map.of("running", false, "message", ""));
    }

    private Map<String, Object> flash() {
        return new HashMap<>(redirect.getFlashAttributes());
    }
}
