package org.ctc.domain.stats;

/** An autocomplete choice: a readable label of at most 100 characters and the entity UUID as value. */
public record StatsChoice(String label, String value) {
    public StatsChoice {
        label = label.replaceAll("\\p{Cntrl}", " ").strip();
        if (label.length() > 100) label = label.substring(0, 97) + "...";
    }
}
