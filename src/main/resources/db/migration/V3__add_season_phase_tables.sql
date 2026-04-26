-- Add Season Phase tables (Season -> Phase -> Group hierarchy)
-- Adds nullable phase_id/group_id FKs on matchdays and playoffs (additive; non-null flip in V?? after data migration)
-- Compatible with H2 2.x and MariaDB 10.7+

CREATE TABLE season_phases (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    sort_index INT NOT NULL,
    phase_type VARCHAR(20) NOT NULL,
    layout VARCHAR(20) NOT NULL,
    format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL,
    label VARCHAR(255),
    start_date DATE,
    end_date DATE,
    total_rounds INT,
    legs INT NOT NULL DEFAULT 1,
    event_duration_minutes INT,
    race_scoring_id UUID NOT NULL,
    match_scoring_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seasonphase_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_seasonphase_race_scoring FOREIGN KEY (race_scoring_id) REFERENCES race_scorings(id),
    CONSTRAINT fk_seasonphase_match_scoring FOREIGN KEY (match_scoring_id) REFERENCES match_scorings(id),
    CONSTRAINT uk_season_phase_type UNIQUE (season_id, phase_type)
);

CREATE TABLE season_phase_groups (
    id UUID PRIMARY KEY,
    phase_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seasonphasegroup_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id)
);

CREATE TABLE phase_teams (
    id UUID PRIMARY KEY,
    phase_id UUID NOT NULL,
    team_id UUID NOT NULL,
    group_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_phaseteam_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id),
    CONSTRAINT fk_phaseteam_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_phaseteam_group FOREIGN KEY (group_id) REFERENCES season_phase_groups(id),
    CONSTRAINT uk_phase_team UNIQUE (phase_id, team_id)
);

ALTER TABLE matchdays ADD COLUMN phase_id UUID;
ALTER TABLE matchdays ADD COLUMN group_id UUID;
ALTER TABLE matchdays ADD CONSTRAINT fk_matchday_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id);
ALTER TABLE matchdays ADD CONSTRAINT fk_matchday_group FOREIGN KEY (group_id) REFERENCES season_phase_groups(id);

ALTER TABLE playoffs ADD COLUMN phase_id UUID;
ALTER TABLE playoffs ADD CONSTRAINT fk_playoff_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id);
ALTER TABLE playoffs ADD CONSTRAINT uk_playoff_phase UNIQUE (phase_id);

CREATE INDEX IF NOT EXISTS idx_season_phases_season_id ON season_phases(season_id);
CREATE INDEX IF NOT EXISTS idx_season_phases_race_scoring_id ON season_phases(race_scoring_id);
CREATE INDEX IF NOT EXISTS idx_season_phases_match_scoring_id ON season_phases(match_scoring_id);
CREATE INDEX IF NOT EXISTS idx_season_phase_groups_phase_id ON season_phase_groups(phase_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_phase_id ON phase_teams(phase_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_team_id ON phase_teams(team_id);
CREATE INDEX IF NOT EXISTS idx_phase_teams_group_id ON phase_teams(group_id);
CREATE INDEX IF NOT EXISTS idx_matchdays_phase_id ON matchdays(phase_id);
CREATE INDEX IF NOT EXISTS idx_matchdays_group_id ON matchdays(group_id);
CREATE INDEX IF NOT EXISTS idx_playoffs_phase_id ON playoffs(phase_id);
