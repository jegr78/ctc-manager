package org.ctc.domain.model;

public enum CarImageKind {
    STUDIO, SCENE, COCKPIT, OTHER;

    /** The kind of an official GT7 car shot slot such as {@code 2_01}: 1 scene, 2 studio, 3 cockpit. */
    public static CarImageKind forGt7Slot(String slot) {
        String prefix = slot == null ? "" : slot.split("_", 2)[0];
        return switch (prefix) {
            case "1" -> SCENE;
            case "2" -> STUDIO;
            case "3" -> COCKPIT;
            default -> OTHER;
        };
    }
}
