-- Add indexes on all foreign key columns for query performance
-- Uses IF NOT EXISTS for H2 and MariaDB compatibility

-- seasons (2 FKs)
CREATE INDEX IF NOT EXISTS idx_seasons_race_scoring_id ON seasons(race_scoring_id);
CREATE INDEX IF NOT EXISTS idx_seasons_match_scoring_id ON seasons(match_scoring_id);

-- teams (1 FK)
CREATE INDEX IF NOT EXISTS idx_teams_parent_team_id ON teams(parent_team_id);

-- season_drivers (3 FKs)
CREATE INDEX IF NOT EXISTS idx_season_drivers_season_id ON season_drivers(season_id);
CREATE INDEX IF NOT EXISTS idx_season_drivers_driver_id ON season_drivers(driver_id);
CREATE INDEX IF NOT EXISTS idx_season_drivers_team_id ON season_drivers(team_id);

-- season_teams (3 FKs)
CREATE INDEX IF NOT EXISTS idx_season_teams_season_id ON season_teams(season_id);
CREATE INDEX IF NOT EXISTS idx_season_teams_team_id ON season_teams(team_id);
CREATE INDEX IF NOT EXISTS idx_season_teams_successor_season_team_id ON season_teams(successor_season_team_id);

-- matchdays (1 FK)
CREATE INDEX IF NOT EXISTS idx_matchdays_season_id ON matchdays(season_id);

-- matches (3 FKs)
CREATE INDEX IF NOT EXISTS idx_matches_matchday_id ON matches(matchday_id);
CREATE INDEX IF NOT EXISTS idx_matches_home_team_id ON matches(home_team_id);
CREATE INDEX IF NOT EXISTS idx_matches_away_team_id ON matches(away_team_id);

-- playoffs (1 FK)
CREATE INDEX IF NOT EXISTS idx_playoffs_season_id ON playoffs(season_id);

-- playoff_rounds (1 FK)
CREATE INDEX IF NOT EXISTS idx_playoff_rounds_playoff_id ON playoff_rounds(playoff_id);

-- playoff_matchups (5 FKs)
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_round_id ON playoff_matchups(round_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_team1_id ON playoff_matchups(team1_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_team2_id ON playoff_matchups(team2_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_winner_id ON playoff_matchups(winner_id);
CREATE INDEX IF NOT EXISTS idx_playoff_matchups_next_matchup_id ON playoff_matchups(next_matchup_id);

-- playoff_seeds (2 FKs)
CREATE INDEX IF NOT EXISTS idx_playoff_seeds_playoff_id ON playoff_seeds(playoff_id);
CREATE INDEX IF NOT EXISTS idx_playoff_seeds_team_id ON playoff_seeds(team_id);

-- races (7 FKs)
CREATE INDEX IF NOT EXISTS idx_races_matchday_id ON races(matchday_id);
CREATE INDEX IF NOT EXISTS idx_races_match_id ON races(match_id);
CREATE INDEX IF NOT EXISTS idx_races_track_id ON races(track_id);
CREATE INDEX IF NOT EXISTS idx_races_car_id ON races(car_id);
CREATE INDEX IF NOT EXISTS idx_races_playoff_matchup_id ON races(playoff_matchup_id);
CREATE INDEX IF NOT EXISTS idx_races_home_team_id ON races(home_team_id);
CREATE INDEX IF NOT EXISTS idx_races_away_team_id ON races(away_team_id);

-- race_results (2 FKs)
CREATE INDEX IF NOT EXISTS idx_race_results_race_id ON race_results(race_id);
CREATE INDEX IF NOT EXISTS idx_race_results_driver_id ON race_results(driver_id);

-- race_lineups (3 FKs)
CREATE INDEX IF NOT EXISTS idx_race_lineups_race_id ON race_lineups(race_id);
CREATE INDEX IF NOT EXISTS idx_race_lineups_driver_id ON race_lineups(driver_id);
CREATE INDEX IF NOT EXISTS idx_race_lineups_team_id ON race_lineups(team_id);

-- race_settings (1 FK)
CREATE INDEX IF NOT EXISTS idx_race_settings_race_id ON race_settings(race_id);

-- race_attachments (1 FK)
CREATE INDEX IF NOT EXISTS idx_race_attachments_race_id ON race_attachments(race_id);
