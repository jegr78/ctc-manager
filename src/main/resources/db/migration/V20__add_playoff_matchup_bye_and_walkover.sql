ALTER TABLE playoff_matchups ADD COLUMN bye BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE playoff_matchups ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE playoff_matchups ADD CONSTRAINT fk_pm_walkover_team FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_walkover_team_id ON playoff_matchups(walkover_team_id);
