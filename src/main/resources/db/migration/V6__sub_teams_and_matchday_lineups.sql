ALTER TABLE teams ADD COLUMN parent_team_id UUID;
ALTER TABLE teams ADD CONSTRAINT fk_team_parent FOREIGN KEY (parent_team_id) REFERENCES teams(id);

CREATE TABLE matchday_lineups (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    matchday_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_ml_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_ml_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_ml_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_matchday_driver UNIQUE (matchday_id, driver_id)
);
