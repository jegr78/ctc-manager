package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LobbyRulesProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeasonLobbyRulesServiceTest {
    @ParameterizedTest
    @CsvSource({",LEGACY", "LEGACY,", ","})
    void givenMissingExpectedOrSelectedProfile_whenChangingRules_thenRejectBeforeWriterAdmission(LobbyRulesProfile expected, LobbyRulesProfile selected) {
        // given
        var writes = mock(PickWriteExecutor.class);
        var service = new SeasonLobbyRulesService(writes, null, null, null, null);
        // when / then
        assertThatThrownBy(() -> service.changeProfile(java.util.UUID.randomUUID(), expected, selected))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Choose");
        verifyNoInteractions(writes);
    }
}
