package de.ctc;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.MatchScoringRepository;
import de.ctc.domain.repository.RaceScoringRepository;
import de.ctc.domain.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestHelper {

    private final RaceScoringRepository raceScoringRepository;
    private final MatchScoringRepository matchScoringRepository;
    private final SeasonRepository seasonRepository;

    public Season createSeason(String name) {
        var suffix = UUID.randomUUID().toString().substring(0, 4);
        var rs = raceScoringRepository.save(
                new RaceScoring("RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var ms = matchScoringRepository.save(
                new MatchScoring("MS " + suffix, 3, 1, 0));
        var season = new Season(name);
        season.setRaceScoring(rs);
        season.setMatchScoring(ms);
        return seasonRepository.save(season);
    }
}
