ALTER TABLE season_phases ADD COLUMN tie_break_race_winner BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE matches ADD COLUMN tie_break_winner_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_tie_break_winner FOREIGN KEY (tie_break_winner_id) REFERENCES teams(id);
CREATE INDEX IF NOT EXISTS idx_matches_tie_break_winner_id ON matches(tie_break_winner_id);
ALTER TABLE playoff_matchups ADD COLUMN decided_by_tie_break BOOLEAN NOT NULL DEFAULT FALSE;
