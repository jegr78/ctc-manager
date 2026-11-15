package org.ctc.domain.model;

public enum LineupStatus {
    MISSING("Missing"), SUBMITTED("Submitted"), SUBMITTED_AFTER_APPLY("Submitted after apply"), APPLIED("Applied");

    private final String label;
    LineupStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
