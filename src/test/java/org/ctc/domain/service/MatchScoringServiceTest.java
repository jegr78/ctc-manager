package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.repository.MatchScoringRepository;
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
class MatchScoringServiceTest {

	@Mock
	private MatchScoringRepository matchScoringRepository;

	@InjectMocks
	private MatchScoringService matchScoringService;

	@Test
	void whenFindAll_thenReturnsAllScorings() {
		// given
		var scoring1 = new MatchScoring("S1", 3, 1, 0);
		var scoring2 = new MatchScoring("S2", 2, 1, 0);
		when(matchScoringRepository.findAll()).thenReturn(List.of(scoring1, scoring2));

		// when
		var result = matchScoringService.findAll();

		// then
		assertThat(result).hasSize(2);
		verify(matchScoringRepository).findAll();
	}

	@Test
	void givenExistingId_whenFindById_thenReturnsScoring() {
		// given
		var id = UUID.randomUUID();
		var scoring = new MatchScoring("Test", 3, 1, 0);
		scoring.setId(id);
		when(matchScoringRepository.findById(id)).thenReturn(Optional.of(scoring));

		// when
		var result = matchScoringService.findById(id);

		// then
		assertThat(result.getName()).isEqualTo("Test");
		verify(matchScoringRepository).findById(id);
	}

	@Test
	void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
		// given
		var id = UUID.randomUUID();
		when(matchScoringRepository.findById(id)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> matchScoringService.findById(id))
				.isInstanceOf(EntityNotFoundException.class)
				.hasMessageContaining("MatchScoring")
				.hasMessageContaining(id.toString());
	}

	@Test
	void givenValidNewScoring_whenSave_thenCreatesScoring() {
		// given
		when(matchScoringRepository.saveAndFlush(any(MatchScoring.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		matchScoringService.save(null, "New Scoring", 3, 1, 0);

		// then
		verify(matchScoringRepository).saveAndFlush(any(MatchScoring.class));
	}

	@Test
	void givenValidExistingScoring_whenSave_thenUpdatesScoring() {
		// given
		var id = UUID.randomUUID();
		var existing = new MatchScoring("Old Name", 3, 1, 0);
		existing.setId(id);
		when(matchScoringRepository.findById(id)).thenReturn(Optional.of(existing));
		when(matchScoringRepository.saveAndFlush(any(MatchScoring.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		matchScoringService.save(id, "Updated Name", 4, 2, 1);

		// then
		verify(matchScoringRepository).findById(id);
		verify(matchScoringRepository).saveAndFlush(existing);
		assertThat(existing.getName()).isEqualTo("Updated Name");
		assertThat(existing.getPointsWin()).isEqualTo(4);
	}

	@Test
	void givenDuplicateName_whenSave_thenThrowsBusinessRuleException() {
		// given
		when(matchScoringRepository.saveAndFlush(any(MatchScoring.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint"));

		// when / then
		assertThatThrownBy(() -> matchScoringService.save(null, "Duplicate", 3, 1, 0))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("already exists");
	}

	@Test
	void givenExistingScoring_whenDelete_thenRemoves() {
		// given
		var id = UUID.randomUUID();
		var scoring = new MatchScoring("Delete Me", 3, 1, 0);
		scoring.setId(id);
		when(matchScoringRepository.findById(id)).thenReturn(Optional.of(scoring));

		// when
		matchScoringService.delete(id);

		// then
		verify(matchScoringRepository).delete(scoring);
		verify(matchScoringRepository).flush();
	}

	@Test
	void givenScoringReferencedBySeason_whenDelete_thenThrowsBusinessRuleException() {
		// given
		var id = UUID.randomUUID();
		var scoring = new MatchScoring("Referenced", 3, 1, 0);
		scoring.setId(id);
		when(matchScoringRepository.findById(id)).thenReturn(Optional.of(scoring));
		doNothing().when(matchScoringRepository).delete(scoring);
		doThrow(new DataIntegrityViolationException("foreign key constraint"))
				.when(matchScoringRepository).flush();

		// when / then
		assertThatThrownBy(() -> matchScoringService.delete(id))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Cannot delete");
	}
}
