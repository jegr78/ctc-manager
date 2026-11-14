package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickLifecycleGuardTest {
    final MatchdayPickRepository picks = mock(MatchdayPickRepository.class);
    final PickLifecycleGuard guard = new PickLifecycleGuard(picks);
    final Race race = new Race();
    final Team home = new Team();
    final Team away = new Team();
    final Matchday day = new Matchday();

    @BeforeEach
    void setUp() {
        race.setId(UUID.randomUUID());
        day.setId(UUID.randomUUID());
        home.setId(UUID.randomUUID());
        away.setId(UUID.randomUUID());
        race.setMatchday(day);
        race.setMatch(new Match(day, home, away));
        
        when(picks.findByRaceId(race.getId())).thenReturn(Optional.of(new MatchdayPick(race, new Season(), home)));
    }

    @Test
    void givenHistory_whenChangingEffectiveParticipantsOrMatchday_thenRejectIdentityChange() {
        // given / when / then
        assertThatThrownBy(() -> guard.requireIdentity(race, UUID.randomUUID(), home, away))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
        assertThatThrownBy(() -> guard.requireIdentity(race, day.getId(), away, home))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
        assertThatCode(() -> guard.requireIdentity(race, day.getId(), home, away)).doesNotThrowAnyException();
    }

    @Test
    void givenHistory_whenDeletingAnyContainingFixture_thenRejectBeforeDeletion() {
        // given / when / then
        assertThatThrownBy(() -> guard.requireNoHistory(List.of(race)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
    }

    @Test
    void givenNoHistory_whenChangingIdentityOrDeleting_thenKeepLegacyBehavior() {
        // given
        when(picks.findByRaceId(race.getId())).thenReturn(Optional.empty());
        // when / then
        assertThatCode(() -> guard.requireIdentity(race, UUID.randomUUID(), away, home)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireNoHistory(List.of(race))).doesNotThrowAnyException();
    }
}
