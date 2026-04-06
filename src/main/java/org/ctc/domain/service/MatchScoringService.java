package org.ctc.domain.service;

import org.ctc.admin.dto.MatchScoringForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.repository.MatchScoringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScoringService {

    private final MatchScoringRepository matchScoringRepository;

    @Transactional(readOnly = true)
    public List<MatchScoring> findAll() {
        return matchScoringRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MatchScoring findById(UUID id) {
        return matchScoringRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MatchScoring", id));
    }

    @Transactional
    public MatchScoring save(MatchScoringForm form) {
        try {
            if (form.getId() != null) {
                var existing = findById(form.getId());
                existing.setName(form.getName());
                existing.setPointsWin(form.getPointsWin());
                existing.setPointsDraw(form.getPointsDraw());
                existing.setPointsLoss(form.getPointsLoss());
                var saved = matchScoringRepository.saveAndFlush(existing);
                log.info("Updated match scoring: {}", saved.getName());
                return saved;
            } else {
                var scoring = new MatchScoring(form.getName(), form.getPointsWin(), form.getPointsDraw(), form.getPointsLoss());
                var saved = matchScoringRepository.saveAndFlush(scoring);
                log.info("Created match scoring: {}", saved.getName());
                return saved;
            }
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A match scoring with this name already exists");
        }
    }

    @Transactional
    public void delete(UUID id) {
        var scoring = findById(id);
        try {
            matchScoringRepository.delete(scoring);
            matchScoringRepository.flush();
            log.info("Deleted match scoring: {}", scoring.getName());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("Cannot delete — still referenced by a season");
        }
    }
}
