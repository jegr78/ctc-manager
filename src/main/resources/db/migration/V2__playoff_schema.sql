CREATE TABLE playoffs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    season_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    best_of_legs INT NOT NULL DEFAULT 2,
    CONSTRAINT fk_playoff_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT uk_playoff_season UNIQUE (season_id)
);

CREATE TABLE playoff_rounds (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    playoff_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    round_index INT NOT NULL,
    CONSTRAINT fk_pr_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs(id)
);

CREATE TABLE playoff_matchups (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    round_id UUID NOT NULL,
    bracket_position INT NOT NULL,
    team1_id UUID,
    team2_id UUID,
    winner_id UUID,
    next_matchup_id UUID,
    CONSTRAINT fk_pm_round FOREIGN KEY (round_id) REFERENCES playoff_rounds(id),
    CONSTRAINT fk_pm_team1 FOREIGN KEY (team1_id) REFERENCES teams(id),
    CONSTRAINT fk_pm_team2 FOREIGN KEY (team2_id) REFERENCES teams(id),
    CONSTRAINT fk_pm_winner FOREIGN KEY (winner_id) REFERENCES teams(id),
    CONSTRAINT fk_pm_next FOREIGN KEY (next_matchup_id) REFERENCES playoff_matchups(id)
);

ALTER TABLE races ADD COLUMN playoff_matchup_id UUID;
ALTER TABLE races ADD CONSTRAINT fk_race_playoff_matchup
    FOREIGN KEY (playoff_matchup_id) REFERENCES playoff_matchups(id);
