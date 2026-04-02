package org.ctc.domain.service;

import org.ctc.admin.dto.DriverForm;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final PsnAliasRepository psnAliasRepository;

    @Transactional
    public Driver save(DriverForm form) {
        Driver driver;
        if (form.getId() != null) {
            driver = driverRepository.findById(form.getId()).orElseThrow();
            driver.setPsnId(form.getPsnId());
            driver.setNickname(form.getNickname());
            driver.setActive(form.isActive());
        } else {
            driver = new Driver(form.getPsnId(), form.getNickname());
            driver.setActive(form.isActive());
        }

        syncAliases(driver, form.getAliases());

        return driverRepository.save(driver);
    }

    public List<String> validateAliases(UUID driverId, List<String> aliases) {
        var errors = new ArrayList<String>();
        var seen = new HashSet<String>();

        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;

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
