package org.ctc.domain.model;

public enum Drivetrain {
    FR("FR"), MR("MR"), RR("RR"), FF("FF"), FOUR_WD("4WD");
    private final String label;
    Drivetrain(String label) { this.label = label; }
    public String getLabel() { return label; }
    public static Drivetrain fromSource(String value) {
        if ("AWD".equals(value)) return FOUR_WD;
        for (var drivetrain : values()) if (drivetrain.label.equals(value)) return drivetrain;
        return null;
    }
}
