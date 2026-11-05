package org.ctc.domain.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;

/** Read-only form choices; final permissions are checked again against the locked saved entry. */
public final class RaceTyreFormChoices {
    private RaceTyreFormChoices() { }

    public static Options from(LobbyRulesProfile profile, RaceService.CarSelectionForm selection, UUID entryId,
                               String available, String mandatory) {
        boolean sports = profile == LobbyRulesProfile.CTC_2027 && selection != null && selection.category() && selection.frozen()
                && selection.entries().stream().anyMatch(entry -> entry.id().equals(entryId) && entry.oneMakeCarId() != null);
        var usable = Arrays.stream(PickTyre.values()).filter(tyre -> tyre.getFamily() == PickTyreFamily.RACING || sports).toList();
        var required = usable.stream().filter(tyre -> !tyre.isWet()).toList();
        var selected = state(available, false, usable);
        var selectedRequired = state(mandatory, true, required);
        return new Options(usable, required, selected.codes(), selectedRequired.codes(), selected.retained(), selectedRequired.retained());
    }

    private static Selection state(String text, boolean mandatory, List<PickTyre> allowed) {
        try {
            var parsed = PickTyreText.read(text, mandatory);
            if (parsed.isPresent() && allowed.containsAll(parsed.get())) {
                var codes = parsed.get().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
                return new Selection(mandatory && codes.isEmpty() ? Set.of("None") : codes, null);
            }
        } catch (BusinessRuleException invalid) {
            // Preserve the exact rejected input so the user can replace it without losing the error context.
        }
        return new Selection(Set.of(), text);
    }

    private record Selection(Set<String> codes, String retained) { }
    public record Options(List<PickTyre> available, List<PickTyre> mandatory, Set<String> selectedAvailable,
                          Set<String> selectedMandatory, String retainedAvailable, String retainedMandatory) {
        public Options {
            available = List.copyOf(available); mandatory = List.copyOf(mandatory);
            selectedAvailable = Set.copyOf(selectedAvailable); selectedMandatory = Set.copyOf(selectedMandatory);
        }
    }
}
