package org.ctc.domain.service;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;

/** Resolves submitted choices against locked authoritative text before protected-setup comparison. */
final class NativeTyreInputs {
    private NativeTyreInputs() { }

    static Values resolve(Race race, UUID entryId, UUID carId, LobbyRulesProfile profile, String available, String mandatory) {
        var selected = PickTyreText.read(available, false);
        var required = PickTyreText.read(mandatory, true);
        var saved = race.getSettings();
        boolean sameContext = Objects.equals(entryId, race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId())
                && Objects.equals(carId, race.getCar() == null ? null : race.getCar().getId());
        if (selected.isEmpty() || required.isEmpty()) {
            if (saved != null && sameContext && Objects.equals(available, saved.getAvailableTyres())
                    && Objects.equals(mandatory, saved.getMandatoryTyres())) {
                return new Values(saved.getAvailableTyres(), saved.getMandatoryTyres());
            }
            throw new BusinessRuleException("Choose known tyre compounds, or keep the unchanged saved tyre selection");
        }
        if (selected.get().isEmpty()) {
            if (!required.get().isEmpty()) throw new BusinessRuleException("Pick mandatory tyres require available tyres");
            return new Values(null, null);
        }
        PickTyreSelection.require(profile, selected.get(), required.get());
        String resolvedAvailable = saved != null && sameContext && sameDrySelection(saved.getAvailableTyres(), selected.get())
                ? saved.getAvailableTyres()
                : preserve(null, PickTyreSelection.withAutomaticWetTyres(profile, selected.get()), false);
        String resolvedMandatory = preserve(saved == null ? null : saved.getMandatoryTyres(), required.get(), true);
        return new Values(resolvedAvailable, resolvedMandatory);
    }

    private static boolean sameDrySelection(String saved, Set<PickTyre> submitted) {
        try {
            var original = PickTyreText.read(saved, false);
            if (original.isEmpty() || original.get().isEmpty()) return false;
            return original.get().stream().allMatch(tyre -> tyre.getFamily() == submitted.iterator().next().getFamily())
                    && dry(original.get()).equals(dry(submitted));
        } catch (BusinessRuleException malformedHistory) {
            return false;
        }
    }

    private static Set<PickTyre> dry(Set<PickTyre> tyres) {
        return tyres.stream().filter(tyre -> !tyre.isWet()).collect(Collectors.toUnmodifiableSet());
    }

    private static String preserve(String saved, Set<PickTyre> submitted, boolean mandatory) {
        if (saved != null && !saved.isBlank()) {
            try {
                var original = PickTyreText.read(saved, mandatory);
                if (original.isPresent() && original.get().equals(submitted)) return saved;
            } catch (BusinessRuleException malformedHistory) {
                // The explicitly submitted valid selection repairs malformed old text instead of preserving it.
            }
        }
        if (mandatory && submitted.isEmpty()) return "None";
        return Arrays.stream(PickTyre.values()).filter(submitted::contains).map(Enum::name).collect(Collectors.joining(", "));
    }

    record Values(String available, String mandatory) { }
}
