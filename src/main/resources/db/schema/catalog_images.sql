CREATE TABLE car_images (
    id UUID PRIMARY KEY,
    car_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    source VARCHAR(8) NOT NULL,
    source_key VARCHAR(32),
    label VARCHAR(100),
    image_path VARCHAR(500) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_car_image_car FOREIGN KEY (car_id) REFERENCES cars(id) ON DELETE CASCADE,
    CONSTRAINT uq_car_image_source_key UNIQUE (car_id, source, source_key),
    CONSTRAINT chk_car_image_kind CHECK (CASE kind WHEN 'STUDIO' THEN 1 WHEN 'SCENE' THEN 1 WHEN 'COCKPIT' THEN 1 WHEN 'OTHER' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_car_image_source CHECK (CASE source
        WHEN 'GT7' THEN CASE WHEN source_key IS NOT NULL AND CHAR_LENGTH(TRIM(source_key)) > 0 THEN 1 ELSE 0 END
        WHEN 'MANUAL' THEN CASE WHEN source_key IS NULL THEN 1 ELSE 0 END
        ELSE 0 END = 1),
    CONSTRAINT chk_car_image_size CHECK (width > 0 AND height > 0)
);
CREATE INDEX idx_car_image_car ON car_images(car_id, sort_index);

CREATE TABLE track_images (
    id UUID PRIMARY KEY,
    track_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    source VARCHAR(8) NOT NULL,
    source_key VARCHAR(32),
    label VARCHAR(100),
    image_path VARCHAR(500) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_track_image_track FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    CONSTRAINT uq_track_image_source_key UNIQUE (track_id, source, source_key),
    CONSTRAINT chk_track_image_kind CHECK (CASE kind WHEN 'BACKGROUND' THEN 1 WHEN 'LAYOUT' THEN 1 WHEN 'OTHER' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_track_image_source CHECK (CASE source
        WHEN 'GT7' THEN CASE WHEN source_key IS NOT NULL AND CHAR_LENGTH(TRIM(source_key)) > 0 THEN 1 ELSE 0 END
        WHEN 'MANUAL' THEN CASE WHEN source_key IS NULL THEN 1 ELSE 0 END
        ELSE 0 END = 1),
    CONSTRAINT chk_track_image_size CHECK (width > 0 AND height > 0)
);
CREATE INDEX idx_track_image_track ON track_images(track_id, sort_index)
