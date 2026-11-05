-- Canonical one-family sets preserve existing RS/RM/RH order and dry mandatory subsets.

ALTER TABLE matchday_pick_revisions MODIFY COLUMN available_tyres VARCHAR(12) NOT NULL;

ALTER TABLE matchday_pick_revisions DROP CONSTRAINT chk_pick_revision_payload;

ALTER TABLE matchday_pick_revisions ADD CONSTRAINT chk_pick_revision_payload CHECK (
        laps > 0
        AND tyre_wear BETWEEN 1 AND 7
        AND fuel_consumption BETWEEN 1 AND 6
        AND refueling_speed BETWEEN 1 AND 10
        AND CASE initial_fuel WHEN 'DEFAULT' THEN 1 WHEN 'FUEL_30' THEN 1 WHEN 'FUEL_40' THEN 1 WHEN 'FUEL_50' THEN 1 WHEN 'FUEL_60' THEN 1 WHEN 'FUEL_70' THEN 1 WHEN 'FUEL_80' THEN 1 WHEN 'FUEL_90' THEN 1 ELSE 0 END = 1
        AND required_pit_stops BETWEEN 0 AND 2
        AND ((time_progression_mode = 'MULTIPLIER' AND time_progression_multiplier IS NOT NULL AND time_progression_multiplier BETWEEN 0 AND 6) OR (time_progression_mode = 'EQUAL_CONDITIONS' AND time_progression_multiplier IS NULL))
        AND CHAR_LENGTH(TRIM(weather)) > 0
        AND CHAR_LENGTH(TRIM(time_of_day)) > 0
        AND CASE available_tyres
                WHEN 'RS' THEN 1
                WHEN 'RM' THEN 1
                WHEN 'RH' THEN 1
                WHEN 'I' THEN 1
                WHEN 'W' THEN 1
                WHEN 'RS,RM' THEN 1
                WHEN 'RS,RH' THEN 1
                WHEN 'RS,I' THEN 1
                WHEN 'RS,W' THEN 1
                WHEN 'RM,RH' THEN 1
                WHEN 'RM,I' THEN 1
                WHEN 'RM,W' THEN 1
                WHEN 'RH,I' THEN 1
                WHEN 'RH,W' THEN 1
                WHEN 'I,W' THEN 1
                WHEN 'RS,RM,RH' THEN 1
                WHEN 'RS,RM,I' THEN 1
                WHEN 'RS,RM,W' THEN 1
                WHEN 'RS,RH,I' THEN 1
                WHEN 'RS,RH,W' THEN 1
                WHEN 'RS,I,W' THEN 1
                WHEN 'RM,RH,I' THEN 1
                WHEN 'RM,RH,W' THEN 1
                WHEN 'RM,I,W' THEN 1
                WHEN 'RH,I,W' THEN 1
                WHEN 'RS,RM,RH,I' THEN 1
                WHEN 'RS,RM,RH,W' THEN 1
                WHEN 'RS,RM,I,W' THEN 1
                WHEN 'RS,RH,I,W' THEN 1
                WHEN 'RM,RH,I,W' THEN 1
                WHEN 'RS,RM,RH,I,W' THEN 1
                WHEN 'SS' THEN 1
                WHEN 'SM' THEN 1
                WHEN 'SH' THEN 1
                WHEN 'SS,SM' THEN 1
                WHEN 'SS,SH' THEN 1
                WHEN 'SM,SH' THEN 1
                WHEN 'SS,SM,SH' THEN 1
                ELSE 0 END = 1
        AND CASE mandatory_tyres
                WHEN '' THEN 1
                WHEN 'RS' THEN 1
                WHEN 'RM' THEN 1
                WHEN 'RH' THEN 1
                WHEN 'RS,RM' THEN 1
                WHEN 'RS,RH' THEN 1
                WHEN 'RM,RH' THEN 1
                WHEN 'RS,RM,RH' THEN 1
                WHEN 'SS' THEN 1
                WHEN 'SM' THEN 1
                WHEN 'SH' THEN 1
                WHEN 'SS,SM' THEN 1
                WHEN 'SS,SH' THEN 1
                WHEN 'SM,SH' THEN 1
                WHEN 'SS,SM,SH' THEN 1
                ELSE 0 END = 1
        AND (POSITION('RS' IN mandatory_tyres) = 0 OR POSITION('RS' IN available_tyres) > 0)
        AND (POSITION('RM' IN mandatory_tyres) = 0 OR POSITION('RM' IN available_tyres) > 0)
        AND (POSITION('RH' IN mandatory_tyres) = 0 OR POSITION('RH' IN available_tyres) > 0)
            AND (POSITION('SS' IN mandatory_tyres) = 0 OR POSITION('SS' IN available_tyres) > 0)
            AND (POSITION('SM' IN mandatory_tyres) = 0 OR POSITION('SM' IN available_tyres) > 0)
            AND (POSITION('SH' IN mandatory_tyres) = 0 OR POSITION('SH' IN available_tyres) > 0)
    );

ALTER TABLE matchday_pick_revisions ADD CONSTRAINT ck_revision_tyre_profile CHECK (CASE WHEN available_tyres LIKE 'S%' THEN CASE lobby_rules_profile WHEN 'CTC_2027' THEN 1 ELSE 0 END ELSE 1 END = 1);

ALTER TABLE matchday_pick_decisions MODIFY COLUMN available_tyres VARCHAR(12) NULL;

ALTER TABLE matchday_pick_decisions DROP CONSTRAINT chk_pick_decision_correction;

ALTER TABLE matchday_pick_decisions ADD CONSTRAINT chk_pick_decision_correction CHECK (
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
            AND CASE available_tyres
                WHEN 'RS' THEN 1
                WHEN 'RM' THEN 1
                WHEN 'RH' THEN 1
                WHEN 'I' THEN 1
                WHEN 'W' THEN 1
                WHEN 'RS,RM' THEN 1
                WHEN 'RS,RH' THEN 1
                WHEN 'RS,I' THEN 1
                WHEN 'RS,W' THEN 1
                WHEN 'RM,RH' THEN 1
                WHEN 'RM,I' THEN 1
                WHEN 'RM,W' THEN 1
                WHEN 'RH,I' THEN 1
                WHEN 'RH,W' THEN 1
                WHEN 'I,W' THEN 1
                WHEN 'RS,RM,RH' THEN 1
                WHEN 'RS,RM,I' THEN 1
                WHEN 'RS,RM,W' THEN 1
                WHEN 'RS,RH,I' THEN 1
                WHEN 'RS,RH,W' THEN 1
                WHEN 'RS,I,W' THEN 1
                WHEN 'RM,RH,I' THEN 1
                WHEN 'RM,RH,W' THEN 1
                WHEN 'RM,I,W' THEN 1
                WHEN 'RH,I,W' THEN 1
                WHEN 'RS,RM,RH,I' THEN 1
                WHEN 'RS,RM,RH,W' THEN 1
                WHEN 'RS,RM,I,W' THEN 1
                WHEN 'RS,RH,I,W' THEN 1
                WHEN 'RM,RH,I,W' THEN 1
                WHEN 'RS,RM,RH,I,W' THEN 1
                WHEN 'SS' THEN 1
                WHEN 'SM' THEN 1
                WHEN 'SH' THEN 1
                WHEN 'SS,SM' THEN 1
                WHEN 'SS,SH' THEN 1
                WHEN 'SM,SH' THEN 1
                WHEN 'SS,SM,SH' THEN 1
                ELSE 0 END = 1
            AND CASE mandatory_tyres
                WHEN '' THEN 1
                WHEN 'RS' THEN 1
                WHEN 'RM' THEN 1
                WHEN 'RH' THEN 1
                WHEN 'RS,RM' THEN 1
                WHEN 'RS,RH' THEN 1
                WHEN 'RM,RH' THEN 1
                WHEN 'RS,RM,RH' THEN 1
                WHEN 'SS' THEN 1
                WHEN 'SM' THEN 1
                WHEN 'SH' THEN 1
                WHEN 'SS,SM' THEN 1
                WHEN 'SS,SH' THEN 1
                WHEN 'SM,SH' THEN 1
                WHEN 'SS,SM,SH' THEN 1
                ELSE 0 END = 1
            AND (POSITION('RS' IN mandatory_tyres) = 0 OR POSITION('RS' IN available_tyres) > 0)
            AND (POSITION('RM' IN mandatory_tyres) = 0 OR POSITION('RM' IN available_tyres) > 0)
            AND (POSITION('RH' IN mandatory_tyres) = 0 OR POSITION('RH' IN available_tyres) > 0)
            AND (POSITION('SS' IN mandatory_tyres) = 0 OR POSITION('SS' IN available_tyres) > 0)
            AND (POSITION('SM' IN mandatory_tyres) = 0 OR POSITION('SM' IN available_tyres) > 0)
            AND (POSITION('SH' IN mandatory_tyres) = 0 OR POSITION('SH' IN available_tyres) > 0))
    );

ALTER TABLE matchday_pick_decisions ADD CONSTRAINT ck_decision_tyre_profile CHECK (CASE WHEN available_tyres LIKE 'S%' THEN CASE lobby_rules_profile WHEN 'CTC_2027' THEN 1 ELSE 0 END ELSE 1 END = 1);
