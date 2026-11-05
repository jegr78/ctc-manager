package org.ctc.domain.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.ctc.domain.exception.BusinessRuleException;

/** Reads known native Racing spellings without rewriting saved display or fingerprint bytes. */
public final class PickTyreText {
    private PickTyreText() { }

    public static Optional<Set<PickTyre>> read(String text, boolean mandatory) {
        if (text != null && text.length() > 255) throw new BusinessRuleException("Tyre selections are limited to 255 characters");
        if (text == null || text.isBlank() || text.equalsIgnoreCase("None")) return Optional.of(Set.of());
        if (!mandatory && (text.equalsIgnoreCase("All") || text.equalsIgnoreCase("any"))) {
            return Optional.of(Set.of(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W));
        }
        var result = EnumSet.noneOf(PickTyre.class);
        boolean unknown = false;
        for (String value : text.split(",", -1)) {
            var token = value.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) throw new BusinessRuleException("Choose valid tyre compounds without empty entries");
            token = switch (token) { case "SOFT" -> "RS"; case "MEDIUM" -> "RM"; case "HARD" -> "RH"; default -> token; };
            PickTyre tyre;
            try { tyre = PickTyre.valueOf(token); }
            catch (IllegalArgumentException unclassified) { unknown = true; continue; }
            if (!result.add(tyre)) throw new BusinessRuleException("Choose each tyre compound only once");
        }
        return unknown ? Optional.empty() : Optional.of(Set.copyOf(result));
    }
}
