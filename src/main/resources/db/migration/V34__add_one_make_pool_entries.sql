ALTER TABLE season_car_pool_entries ADD COLUMN one_make_car_id UUID;
ALTER TABLE season_car_pool_entries ADD CONSTRAINT fk_car_pool_entry_one_make FOREIGN KEY (one_make_car_id) REFERENCES cars(id);
CREATE INDEX idx_car_pool_entry_one_make ON season_car_pool_entries(one_make_car_id);
