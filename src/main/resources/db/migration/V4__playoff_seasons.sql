CREATE TABLE playoff_seasons (
    playoff_id UUID NOT NULL,
    season_id UUID NOT NULL,
    PRIMARY KEY (playoff_id, season_id),
    CONSTRAINT fk_ps_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs(id),
    CONSTRAINT fk_ps_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);
