ALTER TABLE tracks ADD COLUMN display_short_name VARCHAR(30) NULL;
CREATE TABLE music_tracks (
    id UUID PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    artist VARCHAR(150) NOT NULL,
    credit VARCHAR(500),
    file_path VARCHAR(500) NOT NULL,
    duration_seconds INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT chk_music_track_duration CHECK (duration_seconds > 0)
);
