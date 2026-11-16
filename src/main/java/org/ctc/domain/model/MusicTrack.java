package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A music file that trailers can play; {@code credit} is copied into the YouTube description. */
@Entity
@Table(name = "music_tracks")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class MusicTrack extends BaseEntity implements StoredFileRow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 150)
    private String artist;

    @Column(length = 500)
    private String credit;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Override
    public String storedFilePath() {
        return filePath;
    }
}
