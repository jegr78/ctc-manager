ALTER TABLE seasons ADD COLUMN car_pool_mode VARCHAR(24) NOT NULL DEFAULT 'FIXED_CARS';
ALTER TABLE seasons ADD COLUMN car_pool_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE seasons ADD CONSTRAINT chk_season_car_pool_mode CHECK (car_pool_mode IN ('FIXED_CARS', 'CATEGORY_POOL'));
ALTER TABLE seasons ADD CONSTRAINT chk_season_car_pool_state CHECK (car_pool_state IN ('DRAFT', 'FROZEN'));

CREATE TABLE season_car_pool_entries (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    label VARCHAR(120) NOT NULL,
    sort_index INT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_car_pool_entry_season FOREIGN KEY (season_id) REFERENCES seasons(id)
);
CREATE INDEX idx_car_pool_entry_season ON season_car_pool_entries(season_id);

CREATE TABLE season_car_pool_clauses (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL,
    category VARCHAR(16) NOT NULL,
    drivetrain VARCHAR(16),
    model_year INT,
    sort_index INT NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_car_pool_clause_entry FOREIGN KEY (entry_id) REFERENCES season_car_pool_entries(id),
    CONSTRAINT chk_car_pool_clause_category CHECK (category IN ('GR_1', 'GR_2', 'GR_3', 'GR_4', 'GR_B', 'GR_N', 'GR_X')),
    CONSTRAINT chk_car_pool_clause_drivetrain CHECK (drivetrain IS NULL OR drivetrain IN ('FR', 'MR', 'RR', 'FF', 'FOUR_WD')),
    CONSTRAINT chk_car_pool_clause_year CHECK (model_year IS NULL OR model_year BETWEEN 1000 AND 9999)
);
CREATE INDEX idx_car_pool_clause_entry ON season_car_pool_clauses(entry_id);

CREATE TABLE season_car_pool_members (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL,
    car_id UUID NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_car_pool_member_entry FOREIGN KEY (entry_id) REFERENCES season_car_pool_entries(id),
    CONSTRAINT fk_car_pool_member_car FOREIGN KEY (car_id) REFERENCES cars(id),
    CONSTRAINT uq_car_pool_member UNIQUE (entry_id, car_id)
);
CREATE INDEX idx_car_pool_member_car ON season_car_pool_members(car_id);

ALTER TABLE races ADD COLUMN car_pool_entry_id UUID;
ALTER TABLE races ADD CONSTRAINT fk_race_car_pool_entry FOREIGN KEY (car_pool_entry_id) REFERENCES season_car_pool_entries(id);
CREATE INDEX idx_race_car_pool_entry ON races(car_pool_entry_id);
