package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One gallery image of a track; {@code sourceKey} names the GT7 slot and is null for manual uploads. */
@Entity
@Table(name = "track_images")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "track")
public class TrackImage extends BaseEntity implements GalleryImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TrackImageKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ImageSource source;

    @Column(name = "source_key", length = 32)
    private String sourceKey;

    @Column(length = 100)
    private String label;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "sort_index", nullable = false)
    private int sortIndex;

    @Override public String getKindName() { return kind == null ? null : kind.name(); }
}
