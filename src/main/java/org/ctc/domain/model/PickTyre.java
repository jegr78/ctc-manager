package org.ctc.domain.model;

public enum PickTyre {
    RS("Racing Soft", PickTyreFamily.RACING, false),
    RM("Racing Medium", PickTyreFamily.RACING, false),
    RH("Racing Hard", PickTyreFamily.RACING, false),
    I("Racing Intermediate", PickTyreFamily.RACING, true),
    W("Racing Wet", PickTyreFamily.RACING, true),
    SS("Sports Soft", PickTyreFamily.SPORTS, false),
    SM("Sports Medium", PickTyreFamily.SPORTS, false),
    SH("Sports Hard", PickTyreFamily.SPORTS, false);

    private final String label;
    private final PickTyreFamily family;
    private final boolean wet;

    PickTyre(String label, PickTyreFamily family, boolean wet) {
        this.label = label;
        this.family = family;
        this.wet = wet;
    }

    public String getLabel() { return label; }
    public PickTyreFamily getFamily() { return family; }
    public boolean isWet() { return wet; }
}
