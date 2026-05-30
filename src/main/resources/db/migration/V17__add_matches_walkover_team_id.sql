ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
CREATE INDEX IF NOT EXISTS idx_matches_walkover_team_id ON matches(walkover_team_id);
