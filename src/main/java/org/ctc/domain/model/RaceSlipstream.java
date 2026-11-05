package org.ctc.domain.model;

public enum RaceSlipstream {
    REAL("Real"), WEAK("Weak");

    private final String label;
    RaceSlipstream(String label) { this.label = label; }
    public String getLabel() { return label; }
}
