ALTER TABLE incident_deliveries ADD COLUMN presentation_version INTEGER NULL;
ALTER TABLE incident_deliveries ADD CONSTRAINT chk_incident_presentation_version CHECK (
    presentation_version IS NULL OR
    CASE WHEN presentation_version = 1 AND operation = 'DISCLOSURE'
        AND pairing_label IS NOT NULL AND race_label IS NOT NULL THEN 1 ELSE 0 END = 1
);
