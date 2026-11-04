UPDATE seasons SET active = FALSE
WHERE active = TRUE AND id NOT IN (
    SELECT kept.id FROM (
        SELECT id FROM seasons WHERE active = TRUE
        ORDER BY season_year DESC, season_number DESC, id ASC LIMIT 1
    ) kept
);

ALTER TABLE seasons ADD COLUMN active_slot INTEGER
    GENERATED ALWAYS AS (CASE WHEN active = TRUE THEN 1 ELSE NULL END);
CREATE UNIQUE INDEX uq_seasons_active_slot ON seasons(active_slot);
