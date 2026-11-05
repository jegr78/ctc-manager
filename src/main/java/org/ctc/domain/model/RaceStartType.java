package org.ctc.domain.model;

public enum RaceStartType {
    GRID_FALSE_START("Grid Start With False Start Check"), ROLLING("Rolling Start");

    private final String label;
    RaceStartType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
