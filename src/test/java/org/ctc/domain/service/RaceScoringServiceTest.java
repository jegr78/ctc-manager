package org.ctc.domain.service;

import org.ctc.admin.dto.RaceScoringForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.repository.RaceScoringRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceScoringServiceTest {

    @Mock
    private RaceScoringRepository raceScoringRepository;

    @InjectMocks
    private RaceScoringService raceScoringService;

    @Test
    void whenFindAll_thenReturnsAllScorings() {
        // given
        var scoring1 = new RaceScoring("S1", "20,17,14", "3,2,1", 2);
        var scoring2 = new RaceScoring("S2", "10,8,6", null, 0);
        when(raceScoringRepository.findAll()).thenReturn(List.of(scoring1, scoring2));

        // when
        var result = raceScoringService.findAll();

        // then
        assertThat(result).hasSize(2);
        verify(raceScoringRepository).findAll();
    }

    @Test
    void givenExistingId_whenFindById_thenReturnsScoring() {
        // given
        var id = UUID.randomUUID();
        var scoring = new RaceScoring("Test", "20,17", null, 0);
        scoring.setId(id);
        when(raceScoringRepository.findById(id)).thenReturn(Optional.of(scoring));

        // when
        var result = raceScoringService.findById(id);

        // then
        assertThat(result.getName()).isEqualTo("Test");
        verify(raceScoringRepository).findById(id);
    }

    @Test
    void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
        // given
        var id = UUID.randomUUID();
        when(raceScoringRepository.findById(id)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> raceScoringService.findById(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("RaceScoring")
                .hasMessageContaining(id.toString());
    }

    @Test
    void givenValidNewForm_whenSave_thenCreatesScoring() {
        // given
        var form = new RaceScoringForm();
        form.setName("New Scoring");
        form.setRacePoints("20,17,14");
        form.setQualiPoints("3,2,1");
        form.setFastestLapPoints(2);
        when(raceScoringRepository.saveAndFlush(any(RaceScoring.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        raceScoringService.save(form);

        // then
        verify(raceScoringRepository).saveAndFlush(any(RaceScoring.class));
    }

    @Test
    void givenValidExistingForm_whenSave_thenUpdatesScoring() {
        // given
        var id = UUID.randomUUID();
        var existing = new RaceScoring("Old Name", "10,8", null, 0);
        existing.setId(id);
        when(raceScoringRepository.findById(id)).thenReturn(Optional.of(existing));
        when(raceScoringRepository.saveAndFlush(any(RaceScoring.class))).thenAnswer(inv -> inv.getArgument(0));

        var form = new RaceScoringForm();
        form.setId(id);
        form.setName("Updated Name");
        form.setRacePoints("20,17,14");
        form.setQualiPoints("3,2,1");
        form.setFastestLapPoints(2);

        // when
        raceScoringService.save(form);

        // then
        verify(raceScoringRepository).findById(id);
        verify(raceScoringRepository).saveAndFlush(existing);
        assertThat(existing.getName()).isEqualTo("Updated Name");
        assertThat(existing.getRacePoints()).isEqualTo("20,17,14");
    }

    @Test
    void givenInvalidPoints_whenSave_thenThrowsBusinessRuleException() {
        // given
        var form = new RaceScoringForm();
        form.setName("Invalid");
        form.setRacePoints("10,20,5");
        form.setFastestLapPoints(0);

        // when / then
        assertThatThrownBy(() -> raceScoringService.save(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("monotonically decreasing");
    }

    @Test
    void givenDuplicateName_whenSave_thenThrowsBusinessRuleException() {
        // given
        var form = new RaceScoringForm();
        form.setName("Duplicate");
        form.setRacePoints("20,17");
        form.setFastestLapPoints(0);
        when(raceScoringRepository.saveAndFlush(any(RaceScoring.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        // when / then
        assertThatThrownBy(() -> raceScoringService.save(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void givenExistingScoring_whenDelete_thenRemoves() {
        // given
        var id = UUID.randomUUID();
        var scoring = new RaceScoring("Delete Me", "20,17", null, 0);
        scoring.setId(id);
        when(raceScoringRepository.findById(id)).thenReturn(Optional.of(scoring));

        // when
        raceScoringService.delete(id);

        // then
        verify(raceScoringRepository).delete(scoring);
        verify(raceScoringRepository).flush();
    }

    @Test
    void givenScoringReferencedBySeason_whenDelete_thenThrowsBusinessRuleException() {
        // given
        var id = UUID.randomUUID();
        var scoring = new RaceScoring("Referenced", "20,17", null, 0);
        scoring.setId(id);
        when(raceScoringRepository.findById(id)).thenReturn(Optional.of(scoring));
        doNothing().when(raceScoringRepository).delete(scoring);
        doThrow(new DataIntegrityViolationException("foreign key constraint"))
                .when(raceScoringRepository).flush();

        // when / then
        assertThatThrownBy(() -> raceScoringService.delete(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot delete");
    }
}
