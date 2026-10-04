package org.ctc.admin.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayForm;
import org.ctc.admin.dto.PlayoffForm;
import org.ctc.admin.dto.RaceForm;
import org.ctc.admin.dto.SeasonForm;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.ValidationException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonWorkspaceService {
    private static final Set<String> SECTIONS = Set.of("seasons", "matchdays", "races", "playoffs", "standings");
    private final SeasonRepository seasonRepository;
    private final MatchdayRepository matchdayRepository;

    public Workspace build(Map<String, Object> model) {
        var section = (String) model.get("activeRoute");
        if (!SECTIONS.contains(section == null ? "" : section)) return null;
        var seasons = seasonRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Season::getYear).thenComparingInt(Season::getNumber).reversed())
                .map(this::option)
                .toList();
        var resourceSeason = resourceSeason(model);
        var selectedId = resourceSeason != null ? resourceSeason.getId() : selectedId(model);
        var selected = seasons.stream().filter(s -> s.id().equals(selectedId)).findFirst().orElse(null);
        if (selected == null && resourceSeason != null) selected = option(resourceSeason);
        var query = selected == null ? "" : "?seasonId=" + selected.id();
        return new Workspace(selected, seasons, section,
                selected == null ? "/admin/seasons" : "/admin/seasons/" + selected.id(),
                "/admin/matchdays" + query, "/admin/races" + query,
                "/admin/playoffs" + query, "/admin/standings" + query,
                selected == null ? null : "/admin/seasons/" + selected.id() + "/edit");
    }

    public String destination(UUID seasonId, String section) {
        if (!SECTIONS.contains(section)) throw new ValidationException("Unknown workspace section");
        seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        return section.equals("seasons") ? "/admin/seasons/" + seasonId
                : "/admin/" + section + "?seasonId=" + seasonId;
    }

    private Season resourceSeason(Map<String, Object> model) {
        for (var key : List.of("race", "match", "matchday", "matchup", "playoff", "phase", "season", "selectedSeason")) {
            var season = switch (model.get(key)) {
                case Race race -> race.getMatchday() == null ? null : race.getMatchday().getSeason();
                case Match match -> match.getMatchday().getSeason();
                case Matchday matchday -> matchday.getSeason();
                case PlayoffMatchup matchup -> matchup.getRound().getPlayoff().getSeason();
                case Playoff playoff -> playoff.getSeason();
                case SeasonPhase phase -> phase.getSeason();
                case Season value -> value;
                case null, default -> null;
            };
            if (season != null) return season;
        }
        return null;
    }

    private UUID selectedId(Map<String, Object> model) {
        if (model.get("selectedSeasonId") instanceof UUID id) return id;
        if (model.get("raceForm") instanceof RaceForm form && form.getMatchdayId() != null) {
            return matchdayRepository.findById(form.getMatchdayId())
                    .map(Matchday::getSeason).map(Season::getId).orElse(null);
        }
        if (model.get("form") instanceof MatchdayForm form) return form.getSeasonId();
        if (model.get("playoffForm") instanceof PlayoffForm form) return form.getSeasonId();
        if (model.get("seasonForm") instanceof SeasonForm form) return form.getId();
        return null;
    }

    private SeasonOption option(Season season) {
        return new SeasonOption(season.getId(), season.getDisplayLabel(), season.isActive());
    }

    public record SeasonOption(UUID id, String label, boolean active) {}
    public record Workspace(SeasonOption selectedSeason, List<SeasonOption> seasons, String section,
                            String overviewUrl, String matchdaysUrl, String racesUrl,
                            String playoffsUrl, String standingsUrl, String settingsUrl) {}
}
