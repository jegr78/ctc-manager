package org.ctc.domain.model;

/**
 * Where a gallery image came from: loaded from the GT7 website under a source key, a GT-Engine track map, a cutout the
 * application produced, or uploaded by management. Every source except manual uploads carries a source key.
 */
public enum ImageSource {
    GT7("GT7", null),
    GTENGINE("GT-Engine", "https://gt-engine.com/gt7/tracks/track-maps.html"),
    AUTO("Automatic", null),
    MANUAL("Manual", null);

    private final String displayName;
    private final String attributionUrl;

    ImageSource(String displayName, String attributionUrl) {
        this.displayName = displayName;
        this.attributionUrl = attributionUrl;
    }

    public String getDisplayName() { return displayName; }

    /** The page to credit for images of this source, or null when no credit is needed. */
    public String getAttributionUrl() { return attributionUrl; }

    public boolean requiresSourceKey() { return this != MANUAL; }
}
