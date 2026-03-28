-- CTC Manager: Complete schema (consolidated from V1-V11)
-- Compatible with H2 2.x and MariaDB 10.7+

CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    format VARCHAR(10) DEFAULT 'LEAGUE' NOT NULL,
    total_rounds INT
);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    parent_team_id UUID,
    CONSTRAINT fk_team_parent FOREIGN KEY (parent_team_id) REFERENCES teams(id)
);

CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    psn_id VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE season_drivers (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_sd_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_sd_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_sd_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_season_driver UNIQUE (season_id, driver_id)
);

CREATE TABLE season_teams (
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    PRIMARY KEY (season_id, team_id),
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_st_team FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE TABLE matchdays (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    sort_index INT NOT NULL,
    CONSTRAINT fk_md_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);

CREATE TABLE playoffs (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    CONSTRAINT fk_playoff_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT uk_playoff_season UNIQUE (season_id)
);

CREATE TABLE playoff_rounds (
    id UUID PRIMARY KEY,
    playoff_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    round_index INT NOT NULL,
    best_of_legs INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_pr_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs(id)
);

CREATE TABLE playoff_matchups (
    id UUID PRIMARY KEY,
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

CREATE TABLE playoff_seasons (
    playoff_id UUID NOT NULL,
    season_id UUID NOT NULL,
    PRIMARY KEY (playoff_id, season_id),
    CONSTRAINT fk_ps_playoff FOREIGN KEY (playoff_id) REFERENCES playoffs(id),
    CONSTRAINT fk_ps_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);

CREATE TABLE races (
    id UUID PRIMARY KEY,
    matchday_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID,
    track VARCHAR(500),
    car VARCHAR(500),
    playoff_matchup_id UUID,
    bye BOOLEAN DEFAULT FALSE NOT NULL,
    home_score INT,
    away_score INT,
    date_time TIMESTAMP,
    CONSTRAINT fk_race_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_race_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_race_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id),
    CONSTRAINT fk_race_playoff_matchup FOREIGN KEY (playoff_matchup_id) REFERENCES playoff_matchups(id)
);

CREATE TABLE race_results (
    id UUID PRIMARY KEY,
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

CREATE TABLE matchday_lineups (
    id UUID PRIMARY KEY,
    matchday_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_ml_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_ml_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_ml_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_matchday_driver UNIQUE (matchday_id, driver_id)
);

CREATE TABLE race_attachments (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ra_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE
);
