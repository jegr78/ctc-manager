package de.ctc.domain.service;

import de.ctc.domain.model.Match;
import de.ctc.domain.model.Matchday;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.Team;
import de.ctc.domain.repository.MatchRepository;
import de.ctc.domain.repository.MatchdayRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchdayRepository matchdayRepository;
    private final TeamRepository teamRepository;
    private final RaceRepository raceRepository;

    /**
     * Returns the matchday and its season's teams for the match creation form.
     */
    public record CreateFormData(Matchday matchday, List<Team> teams) {}

    public CreateFormData getCreateFormData(UUID matchdayId) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        return new CreateFormData(matchday, matchday.getSeason().getTeams());
    }

    /**
     * Creates a match with automatic first leg (Race) creation.
     * Throws IllegalStateException if a duplicate match already exists.
     */
    @Transactional
    public Match createMatch(UUID matchdayId, UUID homeTeamId, UUID awayTeamId, boolean bye) {
        var matchday = matchdayRepository.findById(matchdayId).orElseThrow();
        var homeTeam = teamRepository.findById(homeTeamId).orElseThrow();
        var awayTeam = bye ? null : (awayTeamId != null ? teamRepository.findById(awayTeamId).orElse(null) : null);

        // Duplicate check
        if (!bye && awayTeam != null &&
                matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
                        matchdayId, homeTeam.getId(), awayTeam.getId())) {
            throw new IllegalStateException(
                    "Match already exists: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName());
        }

        var match = new Match(matchday, homeTeam, awayTeam);
        match.setBye(bye);
        match = matchRepository.save(match);

        // Auto-create first leg (Race) for the match
        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        raceRepository.save(race);

        log.info("Created match: {} {} on {}",
                homeTeam.getShortName(),
                bye ? "bye" : "vs " + (awayTeam != null ? awayTeam.getShortName() : "?"),
                matchday.getLabel());

        return match;
    }

    /**
     * Adds an additional leg (Race) to an existing match.
     * Returns the match. Throws IllegalStateException (with matchday ID prefix) if max legs reached.
     */
    @Transactional
    public Match addLeg(UUID matchId) {
        var match = matchRepository.findById(matchId).orElseThrow();
        var matchday = match.getMatchday();
        int maxLegs = matchday.getSeason().getLegs();

        if (match.getRaces().size() >= maxLegs) {
            throw new IllegalStateException("Maximum legs reached (" + maxLegs + ")");
        }

        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        match.getRaces().add(race);
        raceRepository.save(race);

        log.info("Added leg {} for match {} vs {}",
                match.getRaces().size(), match.getHomeTeam().getShortName(),
                match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");

        return match;
    }

    /**
     * Returns the matchday ID for a given match (useful for redirects).
     */
    public UUID getMatchdayId(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow().getMatchday().getId();
    }

    /**
     * Deletes a match and returns the matchday ID for redirect purposes.
     */
    @Transactional
    public UUID deleteMatch(UUID matchId) {
        var match = matchRepository.findById(matchId).orElseThrow();
        var matchdayId = match.getMatchday().getId();
        matchRepository.delete(match);
        log.info("Deleted match: {} vs {}", match.getHomeTeam().getShortName(),
                match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");
        return matchdayId;
    }
}
