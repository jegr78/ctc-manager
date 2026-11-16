package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.domain.service.PlayoffService;
import org.junit.jupiter.api.Test;

class TrailerViewServiceTest {

    @Test
    void givenPlanFails_whenView_thenSingleBlockerAndStartDisabled() {
        // given
        var plans = mock(TrailerPlanService.class);
        when(plans.plan(any())).thenThrow(new BusinessRuleException("Published pick content has inconsistent confirmation evidence"));
        var service = new TrailerViewService(plans, mock(TrailerService.class), mock(MusicLibraryService.class), mock(PlayoffService.class));

        // when
        var view = service.view(new TrailerScope.MatchdayScope(UUID.randomUUID()));

        // then
        assertThat(view.blockers()).extracting(TrailerChecklistItem::message)
                .containsExactly("The trailer checklist could not be prepared: Published pick content has inconsistent confirmation evidence");
        assertThat(view.startEnabled()).as("the host page renders and the start stays disabled").isFalse();
    }
}
