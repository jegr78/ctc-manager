package org.ctc.domain.model;

/**
 * Where a gallery image came from: loaded from the GT7 website under a source key, a GT-Engine track map, or uploaded
 * by management. Downloaded sources carry a source key; manual uploads carry none.
 */
public enum ImageSource {
    GT7("GT7", null),
    GTENGINE("GT-Engine", "https://gt-engine.com/gt7/tracks/track-maps.html"),
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

    public boolean isDownloaded() { return this != MANUAL; }
}
