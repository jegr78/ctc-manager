package org.ctc.domain.model;

import java.util.UUID;
import java.util.Locale;

/** The fields car and track gallery images share. */
public interface GalleryImage {
    UUID getId();
    String getKindName();
    ImageSource getSource();
    String getSourceKey();
    String getLabel();
    String getImagePath();
    int getWidth();
    int getHeight();
    int getSortIndex();
    void setSortIndex(int sortIndex);
    void setSource(ImageSource source);
    void setLabel(String label);
    void setImagePath(String imagePath);
    void setWidth(int width);
    void setHeight(int height);

    /** The GT7 slot shown next to the source, or null for other sources. */
    default String getSlot() {
        return getSource() == ImageSource.GT7 ? getSourceKey() : null;
    }

    /** Alternative text naming the owner, kind, optional label and 1-based position. */
    default String altText(String owner, int position) {
        String kind = getKindName() == null ? "image" : getKindName().toLowerCase(Locale.ROOT);
        return owner + ", " + kind + (getLabel() != null ? ", " + getLabel() : "") + ", image " + position;
    }
}
