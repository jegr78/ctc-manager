package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.repository.RaceScoringRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaceScoringService {

	private final RaceScoringRepository raceScoringRepository;

	@Transactional(readOnly = true)
	public List<RaceScoring> findAll() {
		return raceScoringRepository.findAll();
	}

	@Transactional(readOnly = true)
	public RaceScoring findById(UUID id) {
		return raceScoringRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("RaceScoring", id));
	}

	@Transactional
	public RaceScoring save(UUID id, String name, String racePoints, String qualiPoints, int fastestLapPoints) {
		var scoring = new RaceScoring(name, racePoints, qualiPoints, fastestLapPoints);

		if (!scoring.isValid()) {
			throw new BusinessRuleException("Points must be monotonically decreasing (equal values allowed)");
		}

		try {
			if (id != null) {
				var existing = findById(id);
				existing.setName(name);
				existing.setRacePoints(racePoints);
				existing.setQualiPoints(qualiPoints);
				existing.setFastestLapPoints(fastestLapPoints);
				var saved = raceScoringRepository.saveAndFlush(existing);
				log.info("Updated race scoring: {}", saved.getName());
				return saved;
			} else {
				var saved = raceScoringRepository.saveAndFlush(scoring);
				log.info("Created race scoring: {}", saved.getName());
				return saved;
			}
		} catch (DataIntegrityViolationException e) {
			throw new BusinessRuleException("A race scoring with this name already exists");
		}
	}

	@Transactional
	public void delete(UUID id) {
		var scoring = findById(id);
		try {
			raceScoringRepository.delete(scoring);
			raceScoringRepository.flush();
			log.info("Deleted race scoring: {}", scoring.getName());
		} catch (DataIntegrityViolationException e) {
			throw new BusinessRuleException("Cannot delete — still referenced by a season");
		}
	}
}
