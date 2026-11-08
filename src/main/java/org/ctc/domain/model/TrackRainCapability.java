package org.ctc.domain.model;

public enum TrackRainCapability {
    UNKNOWN("Unknown / not verified"), DRY_ONLY("Verified: no rain"), RAIN("Verified: rain available");
    private final String label;
    TrackRainCapability(String label) { this.label = label; }
    public String getLabel() { return label; }
}
