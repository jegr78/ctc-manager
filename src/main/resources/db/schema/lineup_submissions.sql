ALTER TABLE seasons ADD COLUMN lineup_size INT DEFAULT 6 NOT NULL;
ALTER TABLE seasons ADD CONSTRAINT chk_season_lineup_size CHECK (lineup_size BETWEEN 1 AND 12);

CREATE TABLE lineup_submissions (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    team_id UUID NOT NULL,
    latest_revision_number INT NOT NULL,
    applied_revision_number INT,
    applied_at TIMESTAMP(6),
    applied_by VARCHAR(128),
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lineup_submission_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE,
    CONSTRAINT fk_lineup_submission_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT uq_lineup_submission_target UNIQUE (race_id, team_id),
    CONSTRAINT chk_lineup_submission_latest CHECK (latest_revision_number > 0),
    CONSTRAINT chk_lineup_submission_applied CHECK (
        (applied_revision_number IS NULL AND applied_at IS NULL AND applied_by IS NULL)
        OR (applied_revision_number IS NOT NULL AND applied_at IS NOT NULL AND applied_by IS NOT NULL
            AND applied_revision_number > 0 AND applied_revision_number <= latest_revision_number
            AND CHAR_LENGTH(TRIM(applied_by)) > 0))
);
CREATE INDEX idx_lineup_submission_team ON lineup_submissions(team_id);

CREATE TABLE lineup_submission_revisions (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    revision_number INT NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    actor_kind VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500),
    request_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lineup_revision_submission FOREIGN KEY (submission_id) REFERENCES lineup_submissions(id) ON DELETE CASCADE,
    CONSTRAINT uq_lineup_revision_number UNIQUE (submission_id, revision_number),
    CONSTRAINT uq_lineup_revision_request UNIQUE (request_id),
    CONSTRAINT chk_lineup_revision_number CHECK (revision_number > 0),
    CONSTRAINT chk_lineup_revision_actor CHECK (CHAR_LENGTH(TRIM(actor_id)) > 0),
    CONSTRAINT chk_lineup_revision_kind CHECK (CASE actor_kind
        WHEN 'TEAM' THEN CASE WHEN reason IS NULL THEN 1 ELSE 0 END
        WHEN 'MANAGEMENT' THEN CASE WHEN reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0 THEN 1 ELSE 0 END
        ELSE 0 END = 1)
);

CREATE TABLE lineup_window_events (
    id UUID PRIMARY KEY,
    race_id UUID NOT NULL,
    sequence_number INT NOT NULL,
    kind VARCHAR(8) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lineup_window_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE CASCADE,
    CONSTRAINT uq_lineup_window_sequence UNIQUE (race_id, sequence_number),
    CONSTRAINT chk_lineup_window_sequence CHECK (sequence_number > 0),
    CONSTRAINT chk_lineup_window_kind CHECK (CASE kind WHEN 'OPEN' THEN 1 WHEN 'CLOSE' THEN 1 ELSE 0 END = 1),
    CONSTRAINT chk_lineup_window_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_lineup_window_actor CHECK (CHAR_LENGTH(TRIM(actor_id)) > 0)
);
