package org.ctc.domain.service;

import org.ctc.admin.dto.SeasonDriverGroupDto;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamManagementService {

    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;

    /**
     * Return value for team detail view data.
     */
    public record TeamDetailData(
            Team team,
            List<Season> seasons,
            List<SeasonDriverGroupDto> seasonDriverGroups,
            List<Season> seasonsWithoutDrivers
    ) {}

    /**
     * Loads all data needed for the team detail view: team, seasons, driver groups (from RaceLineups
     * with SeasonDriver fallback), and seasons without any drivers.
     */
    @Transactional(readOnly = true)
    public TeamDetailData getTeamDetailData(UUID teamId) {
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", teamId));
        var seasons = seasonRepository.findBySeasonTeamsTeamId(teamId);

        var teamIds = new ArrayList<UUID>();
        teamIds.add(teamId);
        team.getSubTeams().forEach(sub -> teamIds.add(sub.getId()));

        // RaceLineups as primary data source: only drivers who actually raced
        var allLineups = raceLineupRepository.findByTeamIdIn(teamIds);

        var seasonDriverGroups = allLineups.stream()
                .collect(Collectors.groupingBy(
                        lu -> lu.getRace().getMatchday().getSeason(),
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                RaceLineup::getTeam,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        RaceLineup::getDriver,
                                        Collectors.toCollection(
                                                () -> new TreeSet<>(Comparator.comparing(Driver::getPsnId)))
                                )
                        )
                ))
                .entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().isActive() != b.getKey().isActive()) {
                        return a.getKey().isActive() ? -1 : 1;
                    }
                    return b.getKey().getName().compareTo(a.getKey().getName());
                })
                .map(entry -> {
                    var sortedByTeam = new LinkedHashMap<Team, List<Driver>>();
                    entry.getValue().entrySet().stream()
                            .sorted(Comparator.comparing(e -> e.getKey().getShortName()))
                            .forEach(e -> sortedByTeam.put(e.getKey(), new ArrayList<>(e.getValue())));
                    return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
                })
                .toList();

        // Seasons without lineups: fallback to SeasonDriver assignments
        var groupedSeasonIds = seasonDriverGroups.stream()
                .map(g -> g.season().getId())
                .collect(Collectors.toSet());

        var allSeasonDrivers = seasonDriverRepository.findByTeamIdIn(teamIds);
        var fallbackGroups = allSeasonDrivers.stream()
                .filter(sd -> !groupedSeasonIds.contains(sd.getSeason().getId()))
                .collect(Collectors.groupingBy(
                        SeasonDriver::getSeason,
                        LinkedHashMap::new,
                        Collectors.groupingBy(
                                SeasonDriver::getTeam,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        SeasonDriver::getDriver,
                                        Collectors.toCollection(
                                                () -> new TreeSet<>(Comparator.comparing(Driver::getPsnId)))
                                )
                        )
                ))
                .entrySet().stream()
                .sorted((a, b) -> {
                    if (a.getKey().isActive() != b.getKey().isActive()) {
                        return a.getKey().isActive() ? -1 : 1;
                    }
                    return b.getKey().getName().compareTo(a.getKey().getName());
                })
                .map(entry -> {
                    var sortedByTeam = new LinkedHashMap<Team, List<Driver>>();
                    entry.getValue().entrySet().stream()
                            .sorted(Comparator.comparing(e -> e.getKey().getShortName()))
                            .forEach(e -> sortedByTeam.put(e.getKey(), new ArrayList<>(e.getValue())));
                    return new SeasonDriverGroupDto(entry.getKey(), sortedByTeam);
                })
                .toList();

        // Merge: lineup groups first, then fallback groups
        var allGroups = new ArrayList<>(seasonDriverGroups);
        allGroups.addAll(fallbackGroups);
        allGroups.sort((a, b) -> {
            if (a.season().isActive() != b.season().isActive()) {
                return a.season().isActive() ? -1 : 1;
            }
            return b.season().getName().compareTo(a.season().getName());
        });

        var allGroupedSeasonIds = allGroups.stream()
                .map(g -> g.season().getId())
                .collect(Collectors.toSet());
        var seasonsWithoutDrivers = seasons.stream()
                .filter(s -> !allGroupedSeasonIds.contains(s.getId()))
                .toList();

        return new TeamDetailData(team, seasons, allGroups, seasonsWithoutDrivers);
    }

    /**
     * Propagates primary/secondary/accent colors from a parent team to its sub-teams
     * where the sub-team value is null.
     */
    @Transactional
    public void propagateColorsToSubTeams(Team parent) {
        if (!parent.hasSubTeams()) return;
        for (var sub : parent.getSubTeams()) {
            boolean changed = false;
            if (sub.getPrimaryColor() == null && parent.getPrimaryColor() != null) {
                sub.setPrimaryColor(parent.getPrimaryColor());
                changed = true;
            }
            if (sub.getSecondaryColor() == null && parent.getSecondaryColor() != null) {
                sub.setSecondaryColor(parent.getSecondaryColor());
                changed = true;
            }
            if (sub.getAccentColor() == null && parent.getAccentColor() != null) {
                sub.setAccentColor(parent.getAccentColor());
                changed = true;
            }
            if (changed) {
                teamRepository.save(sub);
                log.info("Propagated colors from {} to {}", parent.getShortName(), sub.getShortName());
            }
        }
    }

    /**
     * Propagates a logo URL from a parent team to its sub-teams where the sub-team logo is null.
     */
    @Transactional
    public void propagateLogoToSubTeams(Team parent, String logoUrl) {
        if (!parent.hasSubTeams()) return;
        for (var sub : parent.getSubTeams()) {
            if (sub.getLogoUrl() == null) {
                sub.setLogoUrl(logoUrl);
                teamRepository.save(sub);
                log.info("Propagated logo from {} to {}", parent.getShortName(), sub.getShortName());
            }
        }
    }
}
