package org.ctc.domain.model;

public enum PickInitialFuel {
    DEFAULT, FUEL_30, FUEL_40, FUEL_50, FUEL_60, FUEL_70, FUEL_80, FUEL_90;

    public String settingsValue() {
        return this == DEFAULT ? "Default" : name().substring(5);
    }
}
