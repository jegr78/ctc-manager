ALTER TABLE matches ADD COLUMN walkover_team_id UUID NULL;
ALTER TABLE matches ADD CONSTRAINT fk_match_walkover_team FOREIGN KEY (walkover_team_id) REFERENCES teams(id);
