-- CTC Manager: Complete schema
-- Compatible with H2 2.x and MariaDB 10.7+

CREATE TABLE race_scorings (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    race_points VARCHAR(500) NOT NULL,
    quali_points VARCHAR(500),
    fastest_lap_points INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE match_scorings (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    points_win INT NOT NULL,
    points_draw INT NOT NULL,
    points_loss INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE seasons (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    season_year INT NOT NULL,
    season_number INT NOT NULL,
    description VARCHAR(255),
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    format VARCHAR(20) DEFAULT 'LEAGUE' NOT NULL,
    total_rounds INT,
    legs INT NOT NULL DEFAULT 1,
    event_duration_minutes INT,
    race_scoring_id UUID NOT NULL,
    match_scoring_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_season_race_scoring FOREIGN KEY (race_scoring_id) REFERENCES race_scorings(id),
    CONSTRAINT fk_season_match_scoring FOREIGN KEY (match_scoring_id) REFERENCES match_scorings(id)
);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    parent_team_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_team_parent FOREIGN KEY (parent_team_id) REFERENCES teams(id)
);

CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    psn_id VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE season_drivers (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_sd_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_sd_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sd_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_season_driver UNIQUE (season_id, driver_id)
);

CREATE TABLE season_teams (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    rating INTEGER,
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    logo_url VARCHAR(500),
    successor_season_team_id UUID,
    replaced_at DATE,
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_st_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_st_successor FOREIGN KEY (successor_season_team_id) REFERENCES season_teams(id),
    CONSTRAINT uk_season_team UNIQUE (season_id, team_id)
);

CREATE TABLE cars (
    id UUID PRIMARY KEY,
    manufacturer VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    gt7_id VARCHAR(20),
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_car UNIQUE (manufacturer, name)
);

CREATE INDEX idx_cars_gt7id ON cars(gt7_id);

CREATE TABLE tracks (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    country VARCHAR(100),
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE season_cars (
    season_id UUID NOT NULL,
    car_id UUID NOT NULL,
    PRIMARY KEY (season_id, car_id),
    CONSTRAINT fk_sc_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_car FOREIGN KEY (car_id) REFERENCES cars(id) ON DELETE CASCADE
);

CREATE TABLE season_tracks (
    season_id UUID NOT NULL,
    track_id UUID NOT NULL,
    PRIMARY KEY (season_id, track_id),
    CONSTRAINT fk_st2_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    CONSTRAINT fk_st2_track FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

CREATE TABLE matchdays (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_md_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    matchday_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID,
    home_score INT,
    away_score INT,
    bye BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT fk_match_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id)
);

CREATE TABLE playoffs (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    event_duration_minutes INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_playoff_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT uk_playoff_season UNIQUE (season_id)
);

CREATE TABLE playoff_rounds (
    id UUID PRIMARY KEY,
    playoff_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    round_index INT NOT NULL,
    best_of_legs INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    home_score INT,
    away_score INT,
    CONSTRAINT fk_pm_round FOREIGN KEY (round_id) REFERENCES playoff_rounds(id),
    CONSTRAINT fk_pm_team1 FOREIGN KEY (team1_id) REFERENCES teams(id),
    CONSTRAINT fk_pm_team2 FOREIGN KEY (team2_id) REFERENCES teams(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
    match_id UUID,
    playoff_matchup_id UUID,
    track_id UUID,
    car_id UUID,
    home_team_id UUID,
    away_team_id UUID,
    date_time TIMESTAMP,
    calendar_event_id VARCHAR(255),
    CONSTRAINT fk_race_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_race_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_race_home_team FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_race_away_team FOREIGN KEY (away_team_id) REFERENCES teams(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_race_track FOREIGN KEY (track_id) REFERENCES tracks(id),
    CONSTRAINT fk_race_car FOREIGN KEY (car_id) REFERENCES cars(id),
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rr_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT uk_race_driver UNIQUE (race_id, driver_id)
);

CREATE TABLE race_lineups (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    team_id UUID NOT NULL,
    CONSTRAINT fk_rl_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_rl_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rl_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT uk_race_lineup_driver UNIQUE (race_id, driver_id)
);

CREATE TABLE race_settings (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    number_of_laps INT,
    tyre_wear_multiplier INT,
    fuel_consumption_multiplier INT,
    refueling_speed INT,
    initial_fuel VARCHAR(100),
    number_of_required_pit_stops INT,
    time_progression_multiplier INT,
    weather VARCHAR(255),
    time_of_day VARCHAR(100),
    available_tyres VARCHAR(255),
    mandatory_tyres VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_race_settings_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT uq_race_settings_race UNIQUE (race_id)
);

CREATE TABLE psn_aliases (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    alias VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alias_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE CASCADE,
    CONSTRAINT uk_alias UNIQUE (alias)
);

CREATE INDEX idx_alias_driver ON psn_aliases(driver_id);

CREATE TABLE race_attachments (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    type VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ra_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE
);
