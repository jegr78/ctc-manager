package org.ctc.domain.incidents;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record IncidentDriverChoices(UUID raceId, String fingerprint, List<DriverChoice> drivers) {
    public IncidentDriverChoices { drivers = List.copyOf(drivers); }
    public String selectedText(List<UUID> selected) {
        if (selected.isEmpty() || selected.stream().distinct().count() != selected.size()
                || selected.stream().anyMatch(id -> drivers.stream().noneMatch(driver -> driver.id().equals(id)))) {
            throw new IncidentLineupException("Choose at least one involved driver from this race's lineup.");
        }
        return drivers.stream().filter(driver -> selected.contains(driver.id())).map(DriverChoice::label).collect(Collectors.joining("\n"));
    }
    public Set<UUID> savedSelection(String text) {
        if (text == null || text.isBlank()) return Set.of();
        var labels = List.of(text.split("\n", -1));
        var matching = drivers.stream().filter(driver -> labels.contains(driver.label())).toList();
        return matching.size() == labels.size() && matching.stream().map(DriverChoice::label).distinct().count() == labels.size()
                ? matching.stream().map(DriverChoice::id).collect(Collectors.toUnmodifiableSet()) : Set.of();
    }
    @Override public String toString() { return "IncidentDriverChoices[private]"; }
    public record DriverChoice(UUID id, UUID teamId, String label, boolean home) {
        @Override public String toString() { return "IncidentDriverChoice[private]"; }
    }
}
