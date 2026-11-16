ALTER TABLE car_images DROP CONSTRAINT chk_car_image_kind;
ALTER TABLE car_images ADD CONSTRAINT chk_car_image_kind CHECK (CASE kind
    WHEN 'STUDIO' THEN 1 WHEN 'SCENE' THEN 1 WHEN 'COCKPIT' THEN 1 WHEN 'OTHER' THEN 1 WHEN 'CUTOUT' THEN 1
    ELSE 0 END = 1);
ALTER TABLE car_images DROP CONSTRAINT chk_car_image_source;
ALTER TABLE car_images ADD CONSTRAINT chk_car_image_source CHECK (CASE source
    WHEN 'GT7' THEN CASE WHEN source_key IS NOT NULL AND CHAR_LENGTH(TRIM(source_key)) > 0 THEN 1 ELSE 0 END
    WHEN 'AUTO' THEN CASE WHEN source_key = 'cutout' AND kind = 'CUTOUT' THEN 1 ELSE 0 END
    WHEN 'MANUAL' THEN CASE WHEN source_key IS NULL THEN 1 ELSE 0 END
    ELSE 0 END = 1);
