package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoreWriteLockServiceTest {
    @Mock EntityManager entityManager;
    @Mock RaceRepository races;
    @Mock SeasonRepository seasons;
    @Mock TypedQuery<UUID> ids;
    @InjectMocks ScoreWriteLockService locks;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void givenPhaseChangedWhileWaitingForItsParent_whenLocking_thenRejectBeforeAnyRoundWrite(boolean deleted) {
        var owner=new Season("Test owner"); owner.setId(UUID.randomUUID());
        var other=new Season("Test other owner"); other.setId(UUID.randomUUID());
        var phase=new org.ctc.domain.model.SeasonPhase(); phase.setId(UUID.randomUUID()); phase.setSeason(other);
        when(entityManager.createQuery(anyString(),eq(UUID.class))).thenReturn(ids);
        when(ids.setParameter(anyString(),any())).thenReturn(ids); when(ids.getResultList()).thenReturn(List.of(owner.getId()));
        when(seasons.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(entityManager.find(org.ctc.domain.model.SeasonPhase.class,phase.getId())).thenReturn(deleted?null:phase);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> locks.lockPhase(phase.getId()))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("changed");
        verifyNoInteractions(races);
    }

    @Test
    void givenIdsAcrossTheSignBit_whenLockingAllSeasons_thenUseTheSameUuidOrderAsCrossSeasonMoves() {
        // given
        var positive = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var negative = UUID.fromString("f1111111-1111-1111-1111-111111111111");
        when(entityManager.createQuery(anyString(), eq(UUID.class))).thenReturn(ids);
        when(ids.getResultList()).thenReturn(List.of(positive, negative));
        when(seasons.findByIdForUpdate(any())).thenReturn(Optional.of(new Season()));
        // when
        locks.lockAllSeasons();
        // then
        var order = inOrder(seasons);
        order.verify(seasons).findByIdForUpdate(negative);
        order.verify(seasons).findByIdForUpdate(positive);
    }
}
