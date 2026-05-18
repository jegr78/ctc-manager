package org.ctc.domain.service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final PsnAliasRepository psnAliasRepository;
    private final SeasonDriverRepository seasonDriverRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    /**
     * Return value for driver edit form data.
     */
    public record DriverEditData(
            Driver driver,
            List<SeasonDriver> seasonDrivers,
            List<Season> allSeasons,
            List<Team> allTeams
    ) {}

    @Transactional(readOnly = true)
    public List<Driver> findAll() {
        return driverRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Driver> getMergeFormDrivers(UUID excludeDriverId) {
        return driverRepository.findAll().stream()
                .filter(d -> !d.getId().equals(excludeDriverId))
                .sorted(Comparator.comparing(Driver::getPsnId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public Driver findById(UUID id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Driver", id));
    }

    @Transactional(readOnly = true)
    public Driver findDetailById(UUID id) {
        return driverRepository.findDetailById(id)
                .orElseThrow(() -> new EntityNotFoundException("Driver", id));
    }

    @Transactional(readOnly = true)
    public DriverEditData getEditFormData(UUID id) {
        var driver = findById(id);
        var seasonDrivers = seasonDriverRepository.findByDriverId(id);
        var allSeasons = seasonRepository.findAll();
        var allTeams = teamRepository.findAll();
        return new DriverEditData(driver, seasonDrivers, allSeasons, allTeams);
    }

    @Transactional
    public String assignToSeason(UUID driverId, UUID seasonId, UUID teamId) {
        var driver = findById(driverId);
        var season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new EntityNotFoundException("Season", seasonId));
        var team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team", teamId));

        var existing = seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driverId);
        if (existing.isPresent()) {
            var sd = existing.get();
            sd.setTeam(team);
            seasonDriverRepository.save(sd);
        } else {
            seasonDriverRepository.save(new SeasonDriver(season, driver, team));
        }

        return driver.getPsnId() + " assigned to " + team.getShortName() + " in " + season.getName();
    }

    @Transactional
    public void delete(UUID id) {
        var driver = findById(id);
        try {
            driverRepository.delete(driver);
            driverRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("Cannot delete driver '" + driver.getPsnId()
                    + "' because it is still referenced by other entities.");
        }
    }

    @Transactional
    public Driver save(UUID id, String psnId, String nickname, boolean active, List<String> aliases) {
        Driver driver;
        if (id != null) {
            driver = driverRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Driver", id));
            driver.setPsnId(psnId);
            driver.setNickname(nickname);
            driver.setActive(active);
        } else {
            driver = new Driver(psnId, nickname);
            driver.setActive(active);
        }

        syncAliases(driver, aliases);

        return driverRepository.save(driver);
    }

    public List<String> validateAliases(UUID driverId, List<String> aliases) {
        var errors = new ArrayList<String>();
        var seen = new HashSet<String>();

        for (String alias : aliases) {
			if (alias == null || alias.isBlank()) {
				continue;
			}

            String normalized = alias.trim().toLowerCase();

            // Duplicate within form
            if (!seen.add(normalized)) {
                errors.add("Duplicate alias: " + alias);
                continue;
            }

            // Conflicts with existing PSN ID
            var psnMatch = driverRepository.findByPsnIdIgnoreCase(alias);
            if (psnMatch.isPresent()) {
                errors.add("Alias '" + alias + "' is already used as PSN ID by " + psnMatch.get().getPsnId());
                continue;
            }

            // Conflicts with another driver's alias
            var aliasMatch = psnAliasRepository.findByAliasIgnoreCase(alias);
            if (aliasMatch.isPresent() && !aliasMatch.get().getDriver().getId().equals(driverId)) {
                errors.add("Alias '" + alias + "' is already used by " + aliasMatch.get().getDriver().getPsnId());
            }
        }

        return errors;
    }

    private void syncAliases(Driver driver, List<String> formAliases) {
        var desired = formAliases != null
                ? formAliases.stream().filter(a -> a != null && !a.isBlank()).map(String::trim).toList()
                : List.<String>of();

        // Remove aliases no longer in form
        driver.getAliases().removeIf(existing ->
                desired.stream().noneMatch(d -> d.equalsIgnoreCase(existing.getAlias())));

        // Add new aliases
        var currentAliases = driver.getAliases().stream()
                .map(a -> a.getAlias().toLowerCase())
                .collect(Collectors.toSet());

        for (String alias : desired) {
            if (!currentAliases.contains(alias.toLowerCase())) {
                driver.addAlias(alias);
            }
        }
    }
}
