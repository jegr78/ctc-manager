ALTER TABLE track_images DROP CONSTRAINT chk_track_image_source;
ALTER TABLE track_images ADD CONSTRAINT chk_track_image_source CHECK (CASE source
    WHEN 'GT7' THEN CASE WHEN source_key IS NOT NULL AND CHAR_LENGTH(TRIM(source_key)) > 0 THEN 1 ELSE 0 END
    WHEN 'GTENGINE' THEN CASE WHEN source_key IS NOT NULL AND CHAR_LENGTH(TRIM(source_key)) > 0 THEN 1 ELSE 0 END
    WHEN 'MANUAL' THEN CASE WHEN source_key IS NULL THEN 1 ELSE 0 END
    ELSE 0 END = 1);
