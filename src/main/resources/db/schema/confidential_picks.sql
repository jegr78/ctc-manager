ALTER TABLE seasons ADD COLUMN picks_collection_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE races ADD COLUMN picks_managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE races ADD CONSTRAINT uq_race_pick_target UNIQUE (id, matchday_id);

CREATE TABLE matchday_picks (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    original_matchday_id UUID NOT NULL,
    original_away_team_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'MISSING',
    latest_revision_number INT,
    approved_revision_number INT,
    manual_open BOOLEAN NOT NULL DEFAULT FALSE,
    manual_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_pick_race UNIQUE (race_id),
    CONSTRAINT fk_pick_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_pick_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_pick_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_pick_original_matchday FOREIGN KEY (original_matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_pick_original_away FOREIGN KEY (original_away_team_id) REFERENCES teams(id),
    CONSTRAINT fk_pick_race_target FOREIGN KEY (race_id, original_matchday_id) REFERENCES races(id, matchday_id),
    CONSTRAINT chk_pick_opponent CHECK (team_id <> original_away_team_id),
    CONSTRAINT chk_pick_version CHECK (aggregate_version >= 0),
    CONSTRAINT chk_pick_state CHECK (CASE state WHEN 'MISSING' THEN 1 WHEN 'SUBMITTED' THEN 1 WHEN 'RETURNED' THEN 1 WHEN 'APPROVED' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_pick_overrides CHECK (NOT (manual_open AND manual_closed)),
    CONSTRAINT chk_pick_pointers CHECK (
        (state = 'MISSING' AND latest_revision_number IS NULL AND approved_revision_number IS NULL)
        OR (CASE state WHEN 'SUBMITTED' THEN 1 WHEN 'RETURNED' THEN 1 ELSE 0 END = 1 AND latest_revision_number IS NOT NULL AND latest_revision_number > 0 AND approved_revision_number IS NULL)
        OR (state = 'APPROVED' AND latest_revision_number IS NOT NULL AND latest_revision_number > 0 AND approved_revision_number IS NOT NULL
            AND approved_revision_number = latest_revision_number AND NOT manual_open AND NOT manual_closed)
    )
);
CREATE INDEX idx_pick_season_team ON matchday_picks(season_id, team_id);
CREATE INDEX idx_pick_original_matchday ON matchday_picks(original_matchday_id);
CREATE INDEX idx_pick_original_away ON matchday_picks(original_away_team_id);

CREATE TABLE matchday_pick_revisions (
    id UUID PRIMARY KEY,
    pick_id UUID NOT NULL,
    revision_number INT NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    submitter_id VARCHAR(32) NOT NULL,
    request_id UUID NOT NULL,
    receipt_version BIGINT NOT NULL,
    track_id UUID NOT NULL,
    car_id UUID NOT NULL,
    car_pool_entry_id UUID,
    laps INT NOT NULL,
    tyre_wear INT NOT NULL,
    fuel_consumption INT NOT NULL,
    refueling_speed INT NOT NULL,
    initial_fuel VARCHAR(16) NOT NULL,
    required_pit_stops INT NOT NULL,
    time_progression_mode VARCHAR(24) NOT NULL,
    time_progression_multiplier INT,
    weather VARCHAR(255) NOT NULL,
    time_of_day VARCHAR(255) NOT NULL,
    available_tyres VARCHAR(8) NOT NULL,
    mandatory_tyres VARCHAR(8) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_pick_revision UNIQUE (pick_id, revision_number),
    CONSTRAINT uq_pick_request UNIQUE (request_id),
    CONSTRAINT fk_pick_revision_pick FOREIGN KEY (pick_id) REFERENCES matchday_picks(id),
    CONSTRAINT fk_pick_revision_track FOREIGN KEY (track_id) REFERENCES tracks(id),
    CONSTRAINT fk_pick_revision_car FOREIGN KEY (car_id) REFERENCES cars(id),
    CONSTRAINT fk_pick_revision_entry FOREIGN KEY (car_pool_entry_id) REFERENCES season_car_pool_entries(id),
    CONSTRAINT chk_pick_revision_number CHECK (revision_number > 0 AND receipt_version >= revision_number),
    CONSTRAINT chk_pick_revision_submitter CHECK (CHAR_LENGTH(TRIM(submitter_id)) > 0),
    CONSTRAINT chk_pick_revision_payload CHECK (
        laps > 0
        AND tyre_wear BETWEEN 1 AND 7
        AND fuel_consumption BETWEEN 1 AND 6
        AND refueling_speed BETWEEN 1 AND 10
        AND CASE initial_fuel WHEN 'DEFAULT' THEN 1 WHEN 'FUEL_30' THEN 1 WHEN 'FUEL_40' THEN 1 WHEN 'FUEL_50' THEN 1 WHEN 'FUEL_60' THEN 1 WHEN 'FUEL_70' THEN 1 WHEN 'FUEL_80' THEN 1 WHEN 'FUEL_90' THEN 1 ELSE 0 END = 1
        AND required_pit_stops BETWEEN 0 AND 2
        AND ((time_progression_mode = 'MULTIPLIER' AND time_progression_multiplier IS NOT NULL AND time_progression_multiplier BETWEEN 0 AND 6) OR (time_progression_mode = 'EQUAL_CONDITIONS' AND time_progression_multiplier IS NULL))
        AND CHAR_LENGTH(TRIM(weather)) > 0
        AND CHAR_LENGTH(TRIM(time_of_day)) > 0
        AND CASE available_tyres WHEN 'RS' THEN 1 WHEN 'RM' THEN 1 WHEN 'RH' THEN 1 WHEN 'RS,RM' THEN 1 WHEN 'RS,RH' THEN 1 WHEN 'RM,RH' THEN 1 WHEN 'RS,RM,RH' THEN 1 ELSE 0 END = 1
        AND CASE mandatory_tyres WHEN '' THEN 1 WHEN 'RS' THEN 1 WHEN 'RM' THEN 1 WHEN 'RH' THEN 1 WHEN 'RS,RM' THEN 1 WHEN 'RS,RH' THEN 1 WHEN 'RM,RH' THEN 1 WHEN 'RS,RM,RH' THEN 1 ELSE 0 END = 1
        AND (POSITION('RS' IN mandatory_tyres) = 0 OR POSITION('RS' IN available_tyres) > 0)
        AND (POSITION('RM' IN mandatory_tyres) = 0 OR POSITION('RM' IN available_tyres) > 0)
        AND (POSITION('RH' IN mandatory_tyres) = 0 OR POSITION('RH' IN available_tyres) > 0)
    )
);
CREATE INDEX idx_pick_revision_car ON matchday_pick_revisions(car_id);
CREATE INDEX idx_pick_revision_track ON matchday_pick_revisions(track_id);
CREATE INDEX idx_pick_revision_entry ON matchday_pick_revisions(car_pool_entry_id);

CREATE TABLE matchday_pick_decisions (
    id UUID PRIMARY KEY,
    pick_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    actor_kind VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    decided_at TIMESTAMP(6) NOT NULL,
    track_id UUID,
    car_id UUID,
    car_pool_entry_id UUID,
    laps INT,
    tyre_wear INT,
    fuel_consumption INT,
    refueling_speed INT,
    initial_fuel VARCHAR(16),
    required_pit_stops INT,
    time_progression_mode VARCHAR(24),
    time_progression_multiplier INT,
    weather VARCHAR(255),
    time_of_day VARCHAR(255),
    available_tyres VARCHAR(8),
    mandatory_tyres VARCHAR(8),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_pick_decision_version UNIQUE (pick_id, aggregate_version),
    CONSTRAINT fk_pick_decision_pick FOREIGN KEY (pick_id) REFERENCES matchday_picks(id),
    CONSTRAINT fk_pick_decision_track FOREIGN KEY (track_id) REFERENCES tracks(id),
    CONSTRAINT fk_pick_decision_car FOREIGN KEY (car_id) REFERENCES cars(id),
    CONSTRAINT fk_pick_decision_entry FOREIGN KEY (car_pool_entry_id) REFERENCES season_car_pool_entries(id),
    CONSTRAINT chk_pick_decision_kind CHECK (CASE kind WHEN 'OPEN' THEN 1 WHEN 'RETURN' THEN 1 WHEN 'CLOSE' THEN 1 WHEN 'APPROVE' THEN 1 WHEN 'CORRECTION' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_pick_decision_actor_kind CHECK (CASE actor_kind WHEN 'MANAGEMENT' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_pick_decision_metadata CHECK (aggregate_version > 0 AND CHAR_LENGTH(TRIM(actor_id)) > 0 AND CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_pick_decision_correction CHECK (
        (kind <> 'CORRECTION' AND track_id IS NULL AND car_id IS NULL AND car_pool_entry_id IS NULL AND laps IS NULL AND tyre_wear IS NULL AND fuel_consumption IS NULL AND refueling_speed IS NULL AND initial_fuel IS NULL AND required_pit_stops IS NULL AND time_progression_mode IS NULL AND time_progression_multiplier IS NULL AND weather IS NULL AND time_of_day IS NULL AND available_tyres IS NULL AND mandatory_tyres IS NULL)
        OR (kind = 'CORRECTION'
            AND track_id IS NOT NULL AND car_id IS NOT NULL AND laps IS NOT NULL AND tyre_wear IS NOT NULL AND fuel_consumption IS NOT NULL AND refueling_speed IS NOT NULL AND initial_fuel IS NOT NULL AND required_pit_stops IS NOT NULL AND time_progression_mode IS NOT NULL AND weather IS NOT NULL AND time_of_day IS NOT NULL AND available_tyres IS NOT NULL AND mandatory_tyres IS NOT NULL
            AND laps > 0
            AND tyre_wear BETWEEN 1 AND 7
            AND fuel_consumption BETWEEN 1 AND 6
            AND refueling_speed BETWEEN 1 AND 10
            AND CASE initial_fuel WHEN 'DEFAULT' THEN 1 WHEN 'FUEL_30' THEN 1 WHEN 'FUEL_40' THEN 1 WHEN 'FUEL_50' THEN 1 WHEN 'FUEL_60' THEN 1 WHEN 'FUEL_70' THEN 1 WHEN 'FUEL_80' THEN 1 WHEN 'FUEL_90' THEN 1 ELSE 0 END = 1
            AND required_pit_stops BETWEEN 0 AND 2
            AND ((time_progression_mode = 'MULTIPLIER' AND time_progression_multiplier IS NOT NULL AND time_progression_multiplier BETWEEN 0 AND 6) OR (time_progression_mode = 'EQUAL_CONDITIONS' AND time_progression_multiplier IS NULL))
            AND CHAR_LENGTH(TRIM(weather)) > 0
            AND CHAR_LENGTH(TRIM(time_of_day)) > 0
            AND CASE available_tyres WHEN 'RS' THEN 1 WHEN 'RM' THEN 1 WHEN 'RH' THEN 1 WHEN 'RS,RM' THEN 1 WHEN 'RS,RH' THEN 1 WHEN 'RM,RH' THEN 1 WHEN 'RS,RM,RH' THEN 1 ELSE 0 END = 1
            AND CASE mandatory_tyres WHEN '' THEN 1 WHEN 'RS' THEN 1 WHEN 'RM' THEN 1 WHEN 'RH' THEN 1 WHEN 'RS,RM' THEN 1 WHEN 'RS,RH' THEN 1 WHEN 'RM,RH' THEN 1 WHEN 'RS,RM,RH' THEN 1 ELSE 0 END = 1
            AND (POSITION('RS' IN mandatory_tyres) = 0 OR POSITION('RS' IN available_tyres) > 0)
            AND (POSITION('RM' IN mandatory_tyres) = 0 OR POSITION('RM' IN available_tyres) > 0)
            AND (POSITION('RH' IN mandatory_tyres) = 0 OR POSITION('RH' IN available_tyres) > 0))
    )
);
CREATE INDEX idx_pick_decision_car ON matchday_pick_decisions(car_id);
CREATE INDEX idx_pick_decision_track ON matchday_pick_decisions(track_id);
CREATE INDEX idx_pick_decision_entry ON matchday_pick_decisions(car_pool_entry_id);
