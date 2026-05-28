UPDATE discord_post AS dp
SET phase_id = (
    SELECT sp.id
    FROM season_phases sp
    WHERE sp.season_id = dp.season_id
      AND sp.phase_type = 'REGULAR'
    LIMIT 1
)
WHERE dp.post_type = 'STANDINGS'
  AND dp.phase_id IS NULL
  AND dp.season_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM season_phases sp
    WHERE sp.season_id = dp.season_id
      AND sp.phase_type = 'REGULAR'
  );
