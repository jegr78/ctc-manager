package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ScoreWriteLockService {
    private final EntityManager entityManager;
    private final RaceRepository races;
    private final org.ctc.domain.repository.SeasonRepository seasons;

    public void lockRace(UUID raceId) {
        lockOwner("select r.matchday.phase.season.id from Race r where r.id = :id", raceId, true);
    }

    public void lockMatch(UUID matchId) {
        lockOwner("select m.matchday.phase.season.id from Match m where m.id = :id", matchId, false);
    }

    private void lockOwner(String ownerQuery, UUID id, boolean race) {
        try {
            var owners = entityManager.createQuery("select s from Season s where s.id = (" + ownerQuery + ")", Season.class)
                    .setParameter("id", id).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            if (owners.isEmpty()) throw new EntityNotFoundException(race ? "Race" : "Match", id);
            var owner = owners.getFirst();
            entityManager.refresh(owner, LockModeType.PESSIMISTIC_WRITE);
            var current = races.findBySeasonForSelectionUpdate(owner.getId());
            boolean retained;
            if (race) {
                retained = current.stream().anyMatch(row -> id.equals(row.getId()));
            } else {
                var match = entityManager.find(Match.class, id);
                if (match != null) entityManager.refresh(match, LockModeType.PESSIMISTIC_WRITE);
                retained = match != null && owner.getId().equals(match.getMatchday().getSeason().getId());
            }
            if (!retained) throw new BusinessRuleException("The pairing changed while saving. Reload and try again.");
        } catch (RuntimeException failure) {
            throw RaceSelectionConcurrency.translate(failure);
        }
    }

    public void lockPhase(UUID phaseId) {
        var owners=entityManager.createQuery("select p.season.id from SeasonPhase p where p.id=:phase",UUID.class)
                .setParameter("phase",phaseId).getResultList();
        if (owners.isEmpty()) throw new EntityNotFoundException("SeasonPhase",phaseId);
        UUID ownerId=owners.getFirst();
        seasons.findByIdForUpdate(ownerId).orElseThrow(() -> new EntityNotFoundException("Season",ownerId));
        var phase=entityManager.find(org.ctc.domain.model.SeasonPhase.class,phaseId);
        if (phase!=null) entityManager.refresh(phase,LockModeType.PESSIMISTIC_WRITE);
        if (phase==null || !ownerId.equals(phase.getSeason().getId()))
            throw new BusinessRuleException("The phase context changed. Reload before saving.");
        races.findBySeasonForSelectionUpdate(ownerId);
    }

    public void lockAllSeasons() {
        try {
            entityManager.flush();
            var ids = entityManager.createQuery("select s.id from Season s", UUID.class)
                    .getResultList().stream().sorted().toList();
            for (var id : ids) {
                seasons.findByIdForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Season", id));
                races.findBySeasonForSelectionUpdate(id);
            }
        } catch (RuntimeException failure) {
            throw RaceSelectionConcurrency.translate(failure);
        }
    }
}
