CREATE TABLE seasons (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE teams (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50) NOT NULL UNIQUE,
    logo_url VARCHAR(500)
);

CREATE TABLE drivers (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    psn_id VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE season_drivers (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    season_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_sd_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_sd_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_sd_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_season_driver UNIQUE (season_id, driver_id)
);

CREATE TABLE matchdays (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    season_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    date DATE,
    sort_index INT NOT NULL,
    CONSTRAINT fk_md_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);

CREATE TABLE races (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    matchday_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    track VARCHAR(500),
    car VARCHAR(500),
    CONSTRAINT fk_race_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_race_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_race_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id)
);

CREATE TABLE race_results (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    race_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    position INT NOT NULL,
    quali_position INT NOT NULL,
    fastest_lap BOOLEAN NOT NULL DEFAULT FALSE,
    points_race INT NOT NULL DEFAULT 0,
    points_quali INT NOT NULL DEFAULT 0,
    points_fl INT NOT NULL DEFAULT 0,
    points_total INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_rr_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_rr_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT uk_race_driver UNIQUE (race_id, driver_id)
);
