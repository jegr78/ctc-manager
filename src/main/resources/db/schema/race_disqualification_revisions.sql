ALTER TABLE race_results ADD COLUMN disqualified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE race_results ADD CONSTRAINT chk_result_dsq_flag CHECK (CASE disqualified WHEN FALSE THEN 1 WHEN TRUE THEN 1 ELSE 0 END = 1);
ALTER TABLE race_results ADD CONSTRAINT chk_result_dsq_points CHECK (disqualified = FALSE OR points_race = 0);

CREATE TABLE race_disqualification_revisions (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL,
    race_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    season_id UUID NOT NULL,
    phase_id UUID NOT NULL,
    matchday_id UUID NOT NULL,
    match_id UUID,
    playoff_matchup_id UUID,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    revision_number INT NOT NULL,
    previous_disqualified BOOLEAN NOT NULL,
    disqualified BOOLEAN NOT NULL,
    recorded_position INT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    result_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_dsq_result FOREIGN KEY (result_id) REFERENCES race_results(id),
    CONSTRAINT fk_dsq_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_dsq_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_dsq_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_dsq_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id),
    CONSTRAINT fk_dsq_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_dsq_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_dsq_playoff FOREIGN KEY (playoff_matchup_id) REFERENCES playoff_matchups(id),
    CONSTRAINT fk_dsq_home FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_dsq_away FOREIGN KEY (away_team_id) REFERENCES teams(id),
    CONSTRAINT uq_dsq_result_revision UNIQUE (result_id, revision_number),
    CONSTRAINT chk_dsq_revision CHECK (revision_number > 0),
    CONSTRAINT chk_dsq_previous_flag CHECK (CASE previous_disqualified WHEN FALSE THEN 1 WHEN TRUE THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_dsq_flag CHECK (CASE disqualified WHEN FALSE THEN 1 WHEN TRUE THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_dsq_first_state CHECK (revision_number <> 1 OR (previous_disqualified = FALSE AND disqualified = TRUE)),
    CONSTRAINT chk_dsq_position CHECK (recorded_position BETWEEN 1 AND 12),
    CONSTRAINT chk_dsq_pairing CHECK (match_id IS NOT NULL OR playoff_matchup_id IS NOT NULL),
    CONSTRAINT chk_dsq_teams CHECK (home_team_id <> away_team_id),
    CONSTRAINT chk_dsq_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_dsq_actor CHECK (CHAR_LENGTH(TRIM(actor_id)) > 0),
    CONSTRAINT chk_dsq_fingerprint CHECK (CHAR_LENGTH(result_fingerprint) = 64)
);
CREATE INDEX idx_dsq_race ON race_disqualification_revisions(race_id);
CREATE INDEX idx_dsq_driver ON race_disqualification_revisions(driver_id);
CREATE INDEX idx_dsq_season ON race_disqualification_revisions(season_id);
CREATE INDEX idx_dsq_phase ON race_disqualification_revisions(phase_id);
CREATE INDEX idx_dsq_matchday ON race_disqualification_revisions(matchday_id);
CREATE INDEX idx_dsq_match ON race_disqualification_revisions(match_id);
CREATE INDEX idx_dsq_playoff ON race_disqualification_revisions(playoff_matchup_id);
