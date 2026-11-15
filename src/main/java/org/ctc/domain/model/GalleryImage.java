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

    /** Alternative text naming the owner, kind, optional label and 1-based position. */
    default String altText(String owner, int position) {
        String kind = getKindName() == null ? "image" : getKindName().toLowerCase(Locale.ROOT);
        return owner + ", " + kind + (getLabel() != null ? ", " + getLabel() : "") + ", image " + position;
    }
}
