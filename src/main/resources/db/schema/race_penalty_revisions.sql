CREATE TABLE race_penalty_revisions (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    season_id UUID NOT NULL,
    phase_id UUID NOT NULL,
    matchday_id UUID NOT NULL,
    match_id UUID,
    playoff_matchup_id UUID,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    revision_number INT NOT NULL,
    home_penalty INT NOT NULL,
    away_penalty INT NOT NULL,
    home_gross INT NOT NULL,
    away_gross INT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    result_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_penalty_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_penalty_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_penalty_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id),
    CONSTRAINT fk_penalty_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_penalty_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_penalty_playoff FOREIGN KEY (playoff_matchup_id) REFERENCES playoff_matchups(id),
    CONSTRAINT fk_penalty_home FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_penalty_away FOREIGN KEY (away_team_id) REFERENCES teams(id),
    CONSTRAINT uq_penalty_revision UNIQUE (race_id, revision_number),
    CONSTRAINT chk_penalty_revision CHECK (revision_number > 0),
    CONSTRAINT chk_penalty_home CHECK (home_penalty >= 0),
    CONSTRAINT chk_penalty_away CHECK (away_penalty >= 0),
    CONSTRAINT chk_penalty_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT chk_penalty_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_penalty_actor CHECK (CHAR_LENGTH(TRIM(actor_id)) > 0),
    CONSTRAINT chk_penalty_fingerprint CHECK (CHAR_LENGTH(result_fingerprint) = 64)
);
CREATE INDEX idx_penalty_season ON race_penalty_revisions(season_id);
CREATE INDEX idx_penalty_phase ON race_penalty_revisions(phase_id);
CREATE INDEX idx_penalty_matchday ON race_penalty_revisions(matchday_id);
CREATE INDEX idx_penalty_match ON race_penalty_revisions(match_id);
CREATE INDEX idx_penalty_playoff ON race_penalty_revisions(playoff_matchup_id);
CREATE INDEX idx_penalty_home ON race_penalty_revisions(home_team_id);
CREATE INDEX idx_penalty_away ON race_penalty_revisions(away_team_id);
