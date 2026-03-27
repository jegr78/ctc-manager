CREATE TABLE season_teams (
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    PRIMARY KEY (season_id, team_id),
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_st_team FOREIGN KEY (team_id) REFERENCES teams(id)
);
