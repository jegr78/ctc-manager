package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.PickSetupSnapshot;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeasonLobbyRulesService {
    private final PickWriteExecutor writes;
    private final SeasonRepository seasons;
    private final RaceRepository races;
    private final PickHistoryReferences history;
    private final PickPublicationAttemptRepository publications;
    @PersistenceContext private EntityManager entities;

    public void changeProfile(UUID seasonId, LobbyRulesProfile expected, LobbyRulesProfile selected) {
        if (expected == null || selected == null) throw new BusinessRuleException("Choose a season rules profile and reload its current version.");
        writes.execute(() -> {
            var season = seasons.findByIdForUpdate(seasonId).orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
            if (season.getLobbyRulesProfile() != expected) throw new BusinessRuleException("The season rules changed; reload before choosing a profile.");
            if (selected == expected) return null;
            for (var race : races.findBySeasonForSelectionUpdate(seasonId)) {
                if (race.getSettings() != null) entities.refresh(race.getSettings(), LockModeType.PESSIMISTIC_WRITE);
                if (PickSetupSnapshot.from(race).configured()) throw frozen();
            }
            if (history.hasSelection(seasonId) || !publications.findRulesHistoryForUpdate(seasonId).isEmpty()) throw frozen();
            season.setLobbyRulesProfile(selected);
            seasons.saveAndFlush(season);
            return null;
        });
    }

    private static BusinessRuleException frozen() {
        return new BusinessRuleException("Season rules cannot change after race setup, private pick history or a publication attempt has been saved.");
    }
}
