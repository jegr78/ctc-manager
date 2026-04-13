package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchdayService {

    private final MatchdayRepository matchdayRepository;
    private final SeasonRepository seasonRepository;
    private final RaceLineupRepository raceLineupRepository;

    // --- Return types ---

    public record MatchdayData(UUID id, String label, int sortIndex) {}
    public record MatchdayListData(List<Matchday> matchdays, UUID selectedSeasonId, List<Season> seasons) {}
    public record MatchdayDetailData(Matchday matchday, Map<String, List<RaceLineup>> lineupsByTeam) {}

    // --- Season helpers (for controller form data) ---

    public List<Season> getAllSeasons() {
        return seasonRepository.findAll();
    }

    public Season findSeasonById(UUID id) {
        return seasonRepository.findById(id).orElse(null);
    }

    // --- List ---

    public MatchdayListData getMatchdayList(UUID seasonId) {
        List<Matchday> matchdays;
        UUID selectedSeasonId = null;

        if (seasonId != null) {
            matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId);
            selectedSeasonId = seasonId;
        } else {
            var activeSeason = seasonRepository.findByActiveTrue();
            if (activeSeason.isPresent()) {
                matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(activeSeason.get().getId());
                selectedSeasonId = activeSeason.get().getId();
            } else {
                matchdays = matchdayRepository.findAll();
            }
        }

        return new MatchdayListData(matchdays, selectedSeasonId, seasonRepository.findAll());
    }

    // --- Detail ---

    public MatchdayDetailData getMatchdayDetail(UUID id) {
        var matchday = matchdayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Matchday", id));
        var lineups = raceLineupRepository.findByRaceMatchdayId(id);
        var lineupsByTeam = lineups.stream()
                .collect(Collectors.groupingBy(
                        lu -> lu.getTeam().getShortName(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return new MatchdayDetailData(matchday, lineupsByTeam);
    }

    // --- Save ---

    @Transactional
    public Matchday saveMatchday(String label, int sortIndex, UUID seasonId, UUID matchdayId) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));

        Matchday matchday;
        if (matchdayId != null) {
            matchday = matchdayRepository.findById(matchdayId)
                    .orElseThrow(() -> new EntityNotFoundException("Matchday", matchdayId));
            matchday.setLabel(label);
            matchday.setSortIndex(sortIndex);
            matchday.setSeason(season);
        } else {
            matchday = new Matchday(season, label, sortIndex);
        }

        matchdayRepository.save(matchday);
        log.info("Saved matchday: {} (season {})", label, seasonId);
        return matchday;
    }

    // --- Delete ---

    @Transactional
    public UUID deleteMatchday(UUID id) {
        var matchday = matchdayRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Matchday", id));
        var seasonId = matchday.getSeason().getId();
        matchdayRepository.delete(matchday);
        log.info("Deleted matchday: {}", matchday.getLabel());
        return seasonId;
    }

    // --- By season ID (JSON API) ---

    public List<MatchdayData> getMatchdaysBySeason(UUID seasonId) {
        return matchdayRepository.findBySeasonIdOrderBySortIndexAsc(seasonId).stream()
                .map(md -> new MatchdayData(md.getId(), md.getLabel(), md.getSortIndex()))
                .toList();
    }

    // --- Create inline (JSON API) ---

    @Transactional
    public MatchdayData createInline(UUID seasonId, String label) {
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));

        var existingMatchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());

        boolean duplicateLabel = existingMatchdays.stream()
                .anyMatch(md -> md.getLabel().equals(label));
        if (duplicateLabel) {
            throw new BusinessRuleException("Matchday label already exists in this season: " + label);
        }

        int nextSortIndex = existingMatchdays.stream()
                .mapToInt(Matchday::getSortIndex)
                .max()
                .orElse(0) + 1;

        var matchday = matchdayRepository.save(new Matchday(season, label, nextSortIndex));
        log.info("Created matchday inline: {} (season {})", matchday.getLabel(), season.getName());

        return new MatchdayData(matchday.getId(), matchday.getLabel(), matchday.getSortIndex());
    }
}
