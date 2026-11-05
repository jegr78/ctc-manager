package org.ctc.domain.model;

public enum PickTyreFamily {
    RACING("Racing"), SPORTS("Sports");

    private final String label;
    PickTyreFamily(String label) { this.label = label; }
    public String getLabel() { return label; }
}
