ALTER TABLE playoff_rounds ADD COLUMN best_of_legs INT NOT NULL DEFAULT 1;

ALTER TABLE playoffs ADD COLUMN start_date DATE;
ALTER TABLE playoffs ADD COLUMN end_date DATE;
ALTER TABLE playoffs DROP COLUMN best_of_legs;
