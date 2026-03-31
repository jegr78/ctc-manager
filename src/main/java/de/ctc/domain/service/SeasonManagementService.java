package de.ctc.domain.service;

import de.ctc.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonManagementService {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final SeasonTeamRepository seasonTeamRepository;
    private final FileStorageService fileStorageService;

    /**
     * Adds a team to a season. Auto-adds the parent team when adding a sub-team.
     * Returns the team's short name for flash messages.
     */
    @Transactional
    public String addTeamToSeason(UUID seasonId, UUID teamId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();

        if (!season.containsTeam(team)) {
            if (team.isSubTeam() && !season.containsTeam(team.getParentTeam())) {
                season.addTeam(team.getParentTeam());
                log.info("Auto-added parent team {} to season {}", team.getParentTeam().getShortName(), season.getName());
            }
            season.addTeam(team);
            seasonRepository.save(season);
            log.info("Added team {} to season {}", team.getShortName(), season.getName());
        }

        return team.getShortName();
    }

    /**
     * Removes a team from a season with sub-team constraint check.
     * Auto-removes the parent team if no more sub-teams remain.
     * Throws IllegalStateException if trying to remove a parent team with sub-teams still in the season.
     */
    @Transactional
    public String removeTeamFromSeason(UUID seasonId, UUID teamId) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        var team = teamRepository.findById(teamId).orElseThrow();

        if (!team.isSubTeam()) {
            boolean hasSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(team.getId()));
            if (hasSubs) {
                throw new IllegalStateException(
                        "Cannot remove parent team " + team.getShortName() + " — remove its sub-teams first");
            }
        }

        season.removeTeamById(teamId);

        if (team.isSubTeam()) {
            var parent = team.getParentTeam();
            boolean hasOtherSubs = season.getTeams().stream()
                    .anyMatch(t -> t.isSubTeam() && t.getParentOrSelf().getId().equals(parent.getId()));
            if (!hasOtherSubs) {
                season.removeTeam(parent);
                log.info("Auto-removed parent team {} from season {} (no sub-teams left)",
                        parent.getShortName(), season.getName());
            }
        }

        seasonRepository.save(season);
        log.info("Removed team {} from season {}", team.getShortName(), season.getName());

        return team.getShortName();
    }

    /**
     * Updates season team properties including optional logo upload.
     * Returns the team's short name for flash messages.
     */
    @Transactional
    public String updateSeasonTeam(UUID seasonTeamId, Integer rating,
                                   String primaryColor, String secondaryColor, String accentColor,
                                   MultipartFile logoOverride) throws IOException {
        var seasonTeam = seasonTeamRepository.findById(seasonTeamId).orElseThrow();
        seasonTeam.setRating(rating);
        seasonTeam.setPrimaryColor(primaryColor != null && !primaryColor.isBlank() ? primaryColor : null);
        seasonTeam.setSecondaryColor(secondaryColor != null && !secondaryColor.isBlank() ? secondaryColor : null);
        seasonTeam.setAccentColor(accentColor != null && !accentColor.isBlank() ? accentColor : null);

        if (logoOverride != null && !logoOverride.isEmpty()) {
            if (seasonTeam.getLogoUrl() != null) {
                fileStorageService.delete(seasonTeam.getLogoUrl());
            }
            String url = fileStorageService.storeImage("season-teams", seasonTeamId, logoOverride);
            seasonTeam.setLogoUrl(url);
        }

        seasonTeamRepository.save(seasonTeam);
        return seasonTeam.getTeam().getShortName();
    }

    /**
     * Adds cars to a season's car pool. Returns the number of cars actually added.
     */
    @Transactional
    public int addCarsToSeason(UUID seasonId, List<UUID> carIds) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        int added = 0;
        for (UUID carId : carIds) {
            var car = carRepository.findById(carId).orElse(null);
            if (car != null && !season.getCars().contains(car)) {
                season.getCars().add(car);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        return added;
    }

    /**
     * Removes cars from a season's car pool. Returns the number of car IDs requested for removal.
     */
    @Transactional
    public int removeCarsFromSeason(UUID seasonId, List<UUID> carIds) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        season.getCars().removeIf(c -> carIds.contains(c.getId()));
        seasonRepository.save(season);
        return carIds.size();
    }

    /**
     * Adds tracks to a season's track pool. Returns the number of tracks actually added.
     */
    @Transactional
    public int addTracksToSeason(UUID seasonId, List<UUID> trackIds) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        int added = 0;
        for (UUID trackId : trackIds) {
            var track = trackRepository.findById(trackId).orElse(null);
            if (track != null && !season.getTracks().contains(track)) {
                season.getTracks().add(track);
                added++;
            }
        }
        if (added > 0) seasonRepository.save(season);
        return added;
    }

    /**
     * Removes tracks from a season's track pool. Returns the number of track IDs requested for removal.
     */
    @Transactional
    public int removeTracksFromSeason(UUID seasonId, List<UUID> trackIds) {
        var season = seasonRepository.findById(seasonId).orElseThrow();
        season.getTracks().removeIf(t -> trackIds.contains(t.getId()));
        seasonRepository.save(season);
        return trackIds.size();
    }
}
