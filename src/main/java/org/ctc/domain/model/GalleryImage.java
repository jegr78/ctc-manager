package org.ctc.domain.model;

import java.util.UUID;

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
}
