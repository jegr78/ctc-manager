package org.ctc.domain.service;

import java.util.Set;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;

/** Call after the shared season/car-selection locks and before mutating a race. */
public final class RaceTyrePolicy {
    private RaceTyrePolicy() { }

    public static void require(Season season, SeasonCarPoolEntry entry, Car car, RaceSettingsValues values) {
        boolean explicitOneMake = season.getId() != null && season.getCarPoolMode() == CarPoolMode.CATEGORY_POOL
                && season.getCarPoolState() == CarPoolState.FROZEN && entry != null && entry.getId() != null
                && entry.getSeason() != null && season.getId().equals(entry.getSeason().getId())
                && entry.getOneMakeCar() != null && car != null && car.getId() != null
                && car.getId().equals(entry.getOneMakeCar().getId());
        require(season.getLobbyRulesProfile(), explicitOneMake, values.availableTyres(), values.mandatoryTyres());
    }

    /** Scalar counterpart for bounded archive preflight and the read-only audit. */
    public static void require(LobbyRulesProfile profile, boolean explicitOneMake, String availableText, String mandatoryText) {
        var available = PickTyreText.read(availableText, false);
        var mandatory = PickTyreText.read(mandatoryText, true);
        PickTyreFamily family;
        if (available.isPresent()) {
            if (available.get().isEmpty()) {
                if (mandatory.isPresent() && !mandatory.get().isEmpty()) {
                    throw new BusinessRuleException("Pick mandatory tyres require an available tyre selection");
                }
                return;
            }
            family = PickTyreSelection.require(profile, available.get(), mandatory.orElse(Set.of()));
        } else if (mandatory.isPresent() && !mandatory.get().isEmpty()) {
            family = PickTyreSelection.require(profile, mandatory.get(), mandatory.get());
        } else return;
        if (family != PickTyreFamily.SPORTS) return;
        if (!explicitOneMake) {
            throw new BusinessRuleException("Sports tyres require an explicit One-Make entry and its selected car from this season");
        }
    }
}
