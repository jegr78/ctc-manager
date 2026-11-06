package org.ctc.domain.model;

import java.util.Objects;
import java.util.EnumSet;
import java.util.Set;
import org.ctc.domain.exception.BusinessRuleException;

/** Complete selection validation; partial public projections intentionally do not call this. */
public final class PickTyreSelection {
    private PickTyreSelection() { }

    /** New user input only; historical payloads retain their original compound sets. */
    public static Set<PickTyre> withAutomaticWetTyres(LobbyRulesProfile profile, Set<PickTyre> selected) {
        if (selected == null) throw new BusinessRuleException("Pick requires a non-empty available tyre set");
        require(profile, selected, Set.of());
        if (selected.stream().allMatch(PickTyre::isWet)) {
            throw new BusinessRuleException("Choose at least one available dry tyre compound");
        }
        var available = EnumSet.copyOf(selected);
        if (available.iterator().next().getFamily() == PickTyreFamily.RACING) {
            available.add(PickTyre.I);
            available.add(PickTyre.W);
        }
        return Set.copyOf(available);
    }

    public static PickTyreFamily require(LobbyRulesProfile profile, Set<PickTyre> available, Set<PickTyre> mandatory) {
        if (available == null || available.isEmpty() || available.stream().anyMatch(Objects::isNull)) {
            throw new BusinessRuleException("Pick requires a non-empty available tyre set");
        }
        var family = available.iterator().next().getFamily();
        if (available.stream().anyMatch(tyre -> tyre.getFamily() != family)) {
            throw new BusinessRuleException("Pick available tyres must use one tyre family, Racing or Sports");
        }
        if (mandatory == null || mandatory.stream().anyMatch(Objects::isNull) || !available.containsAll(mandatory)) {
            throw new BusinessRuleException("Pick mandatory tyres must be a subset of available tyres");
        }
        if (mandatory.stream().anyMatch(PickTyre::isWet)) {
            throw new BusinessRuleException("Pick mandatory tyres must be dry; Intermediate and Wet cannot be mandatory");
        }
        if (family == PickTyreFamily.SPORTS && profile != LobbyRulesProfile.CTC_2027) {
            throw new BusinessRuleException("Sports tyres require the CTC 2027 season rules");
        }
        return family;
    }
}
