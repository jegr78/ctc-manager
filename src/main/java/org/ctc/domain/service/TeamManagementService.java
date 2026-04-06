package org.ctc.domain.service;

import org.ctc.admin.dto.SeasonDriverGroupDto;
import org.ctc.admin.dto.TeamForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamManagementService {

    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final RaceLineupRepository raceLineupRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final FileStorageService fileStorageService;

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

    /**
     * Finds a season team by ID or throws EntityNotFoundException.
     */
    @Transactional(readOnly = true)
    public SeasonTeam findSeasonTeamById(UUID id) {
        return seasonTeamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SeasonTeam", id));
    }

    /**
     * Returns all season teams for a given season.
     */
    @Transactional(readOnly = true)
    public List<SeasonTeam> findSeasonTeamsBySeasonId(UUID seasonId) {
        return seasonTeamRepository.findBySeasonId(seasonId);
    }

    /**
     * Returns all parent teams (no parentTeam) sorted by shortName.
     */
    @Transactional(readOnly = true)
    public List<Team> findParentTeamsSorted() {
        return teamRepository.findAll().stream()
                .filter(t -> t.getParentTeam() == null)
                .sorted(Comparator.comparing(Team::getShortName))
                .toList();
    }

    /**
     * Finds a team by ID or throws EntityNotFoundException.
     */
    @Transactional(readOnly = true)
    public Team findById(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team", id));
    }

    /**
     * Creates or updates a team from a TeamForm.
     */
    @Transactional
    public Team save(TeamForm form) {
        Team team;
        if (form.getId() != null) {
            team = findById(form.getId());
            team.setName(form.getName());
            team.setShortName(form.getShortName());
            team.setPrimaryColor(form.getPrimaryColor());
            team.setSecondaryColor(form.getSecondaryColor());
            team.setAccentColor(form.getAccentColor());
            team = teamRepository.save(team);
            propagateColorsToSubTeams(team);
        } else {
            team = new Team(form.getName(), form.getShortName());
            team.setPrimaryColor(form.getPrimaryColor());
            team.setSecondaryColor(form.getSecondaryColor());
            team.setAccentColor(form.getAccentColor());
            team = teamRepository.save(team);
        }
        return team;
    }

    /**
     * Deletes a team by ID. Throws BusinessRuleException if referenced.
     */
    @Transactional
    public void delete(UUID id) {
        var team = findById(id);
        try {
            teamRepository.delete(team);
            teamRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("Cannot delete team '" + team.getShortName()
                    + "' because it is still referenced by other entities.");
        }
    }

    /**
     * Uploads a logo for a team, deleting the old one if present, and propagates to sub-teams.
     */
    @Transactional
    public void uploadLogo(UUID id, MultipartFile logo) {
        var team = findById(id);
        try {
            if (team.getLogoUrl() != null) {
                fileStorageService.delete(team.getLogoUrl());
            }
            var newUrl = fileStorageService.storeImage("teams", id, logo);
            team.setLogoUrl(newUrl);
            teamRepository.save(team);
            propagateLogoToSubTeams(team, newUrl);
        } catch (Exception e) {
            throw new BusinessRuleException("Logo upload failed: " + e.getMessage());
        }
    }

    /**
     * Creates a sub-team under a parent team.
     */
    @Transactional
    public Team addSubTeam(UUID parentId, String name, String shortName) {
        var parent = findById(parentId);
        var subTeam = new Team(name, shortName);
        subTeam.setParentTeam(parent);
        subTeam = teamRepository.save(subTeam);
        return subTeam;
    }

    /**
     * Removes a sub-team by ID.
     */
    @Transactional
    public void removeSubTeam(UUID subTeamId) {
        var subTeam = findById(subTeamId);
        teamRepository.delete(subTeam);
    }
}
