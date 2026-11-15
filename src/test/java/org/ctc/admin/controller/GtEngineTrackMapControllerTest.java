package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.gt7sync.GtEngineTrackMapService;
import org.ctc.gt7sync.GtEngineTrackMapService.BulkResult;
import org.ctc.gt7sync.GtEngineTrackMaps.Comparison;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class GtEngineTrackMapControllerTest {
    @Mock GtEngineTrackMapService trackMaps;
    @InjectMocks GtEngineTrackMapController controller;
    final RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

    @Test
    void givenLoadedMaps_whenLoadingAll_thenReturnToTheTrackListWithTheCounts() {
        // given
        when(trackMaps.loadAll()).thenReturn(new BulkResult(2, 1, 3, 4, List.of()));
        // when
        String view = controller.loadAll(redirect);
        // then
        assertThat(view).isEqualTo("redirect:/admin/tracks");
        assertThat(flash()).containsEntry("successMessage",
                "GT-Engine maps: added 2, replaced 1, unchanged 3, tracks without a map 4.");
    }

    @Test
    void givenFailedTracks_whenLoadingAll_thenReportTheSummaryAsError() {
        // given
        when(trackMaps.loadAll()).thenReturn(new BulkResult(1, 0, 0, 0, List.of("Suzuka Circuit")));
        // when
        controller.loadAll(redirect);
        // then
        assertThat(flash()).containsKey("errorMessage").doesNotContainKey("successMessage");
    }

    @Test
    void givenUnreachablePage_whenComparing_thenShowTheReasonAsError() {
        // given
        when(trackMaps.compare()).thenThrow(new BusinessRuleException("GT-Engine could not be compared: HTTP 503"));
        // when
        String view = controller.compare(redirect);
        // then
        assertThat(view).isEqualTo("redirect:/admin/tracks");
        assertThat(flash()).containsEntry("errorMessage", "GT-Engine could not be compared: HTTP 503")
                .doesNotContainKey("successMessage");
    }

    @Test
    void givenUnchangedPage_whenComparing_thenConfirmTheReviewedVersion() {
        // given
        when(trackMaps.compare()).thenReturn(new Comparison("1.65", "1.65", List.of(), List.of()));
        // when
        controller.compare(redirect);
        // then
        assertThat(flash()).containsEntry("successMessage", "GT-Engine still lists the reviewed 1.65 layouts.");
    }

    private java.util.Map<String, Object> flash() {
        return new java.util.HashMap<>(redirect.getFlashAttributes());
    }
}
