ALTER TABLE matchday_picks DROP CONSTRAINT chk_pick_pointers;
ALTER TABLE matchday_picks ADD CONSTRAINT chk_pick_pointers CHECK (
    (state = 'MISSING' AND latest_revision_number IS NULL AND approved_revision_number IS NULL)
    OR (CASE state WHEN 'SUBMITTED' THEN 1 WHEN 'RETURNED' THEN 1 ELSE 0 END = 1 AND latest_revision_number IS NOT NULL AND latest_revision_number > 0
        AND (approved_revision_number IS NULL OR (approved_revision_number > 0 AND approved_revision_number < latest_revision_number)))
    OR (state = 'APPROVED' AND latest_revision_number IS NOT NULL AND latest_revision_number > 0 AND approved_revision_number IS NOT NULL
        AND approved_revision_number = latest_revision_number AND NOT manual_open AND NOT manual_closed)
);
ALTER TABLE races DROP COLUMN picks_managed;
ALTER TABLE seasons DROP COLUMN picks_collection_enabled;
