CREATE TABLE incident_match_contexts (
    id UUID PRIMARY KEY,
    match_id UUID UNIQUE,
    playoff_matchup_id UUID UNIQUE,
    season_id UUID NOT NULL,
    home_team_id UUID NOT NULL,
    away_team_id UUID NOT NULL,
    guild_id VARCHAR(32) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    bound_by VARCHAR(128) NOT NULL,
    bound_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uq_incident_context_channel UNIQUE (guild_id, channel_id),
    CONSTRAINT fk_incident_context_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_incident_context_playoff FOREIGN KEY (playoff_matchup_id) REFERENCES playoff_matchups(id),
    CONSTRAINT fk_incident_context_season FOREIGN KEY (season_id) REFERENCES seasons(id),
    CONSTRAINT fk_incident_context_home FOREIGN KEY (home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_incident_context_away FOREIGN KEY (away_team_id) REFERENCES teams(id),
    CONSTRAINT ck_incident_context_target CHECK ((match_id IS NOT NULL AND playoff_matchup_id IS NULL) OR (match_id IS NULL AND playoff_matchup_id IS NOT NULL)),
    CONSTRAINT ck_incident_context_participants CHECK (home_team_id <> away_team_id),
    CONSTRAINT ck_incident_context_guild CHECK (guild_id REGEXP '^[0-9]{1,32}$'),
    CONSTRAINT ck_incident_context_channel CHECK (channel_id REGEXP '^[0-9]{1,32}$'),
    CONSTRAINT ck_incident_context_metadata CHECK (aggregate_version > 0 AND CHAR_LENGTH(TRIM(bound_by)) > 0)
);

CREATE TABLE incident_report_windows (
    id UUID PRIMARY KEY,
    context_id UUID NOT NULL UNIQUE,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    planned_start_local TIMESTAMP(6),
    planned_start TIMESTAMP(6),
    deadline TIMESTAMP(6),
    opened_at TIMESTAMP(6),
    opened_by VARCHAR(128),
    closed_at TIMESTAMP(6),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_incident_window_context FOREIGN KEY (context_id) REFERENCES incident_match_contexts(id),
    CONSTRAINT ck_incident_window_state CHECK (CASE state WHEN 'DRAFT' THEN 1 WHEN 'OPEN' THEN 1 WHEN 'CLOSED' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_window_version CHECK (aggregate_version >= 0),
    CONSTRAINT ck_incident_window_planned CHECK (planned_start IS NULL OR planned_start_local IS NOT NULL),
    CONSTRAINT ck_incident_window_lifecycle CHECK (CASE state
        WHEN 'DRAFT' THEN CASE WHEN aggregate_version = 0 AND deadline IS NULL AND opened_at IS NULL AND opened_by IS NULL AND closed_at IS NULL AND planned_start IS NULL AND planned_start_local IS NULL THEN 1 ELSE 0 END
        WHEN 'OPEN' THEN CASE WHEN aggregate_version > 0 AND deadline IS NOT NULL AND opened_at IS NOT NULL AND opened_by IS NOT NULL AND CHAR_LENGTH(TRIM(opened_by)) > 0 AND deadline > opened_at AND closed_at IS NULL THEN 1 ELSE 0 END
        WHEN 'CLOSED' THEN CASE WHEN aggregate_version > 1 AND deadline IS NOT NULL AND opened_at IS NOT NULL AND opened_by IS NOT NULL AND CHAR_LENGTH(TRIM(opened_by)) > 0 AND deadline > opened_at AND closed_at IS NOT NULL AND closed_at >= deadline THEN 1 ELSE 0 END
        ELSE 0 END = 1)
);

CREATE TABLE incident_reports (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL,
    race_id UUID NOT NULL,
    original_matchday_id UUID NOT NULL,
    original_home_team_id UUID NOT NULL,
    original_away_team_id UUID NOT NULL,
    case_reference VARCHAR(64) NOT NULL UNIQUE,
    author_id VARCHAR(32) NOT NULL,
    party_type VARCHAR(16) NOT NULL,
    party_id UUID NOT NULL,
    party_label VARCHAR(255) NOT NULL,
    party_role_id VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    aggregate_version BIGINT NOT NULL,
    latest_revision_number INT NOT NULL,
    frozen_revision_number INT,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uq_incident_report_window UNIQUE (id, window_id),
    CONSTRAINT fk_incident_report_window FOREIGN KEY (window_id) REFERENCES incident_report_windows(id),
    CONSTRAINT fk_incident_report_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_incident_report_day FOREIGN KEY (original_matchday_id) REFERENCES matchdays(id),
    CONSTRAINT fk_incident_report_home FOREIGN KEY (original_home_team_id) REFERENCES teams(id),
    CONSTRAINT fk_incident_report_away FOREIGN KEY (original_away_team_id) REFERENCES teams(id),
    CONSTRAINT fk_incident_report_party FOREIGN KEY (party_id) REFERENCES teams(id),
    CONSTRAINT ck_incident_report_participants CHECK (original_home_team_id <> original_away_team_id),
    CONSTRAINT ck_incident_report_state CHECK (CASE state WHEN 'ACTIVE' THEN 1 WHEN 'WITHDRAWN' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_report_party_type CHECK (CASE party_type WHEN 'TEAM' THEN 1 WHEN 'ORGANIZATION' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_report_metadata CHECK (case_reference LIKE 'INC-%' AND CHAR_LENGTH(TRIM(party_label)) > 0 AND author_id REGEXP '^[0-9]{1,32}$' AND party_role_id REGEXP '^[0-9]{1,32}$'),
    CONSTRAINT ck_incident_report_version CHECK (latest_revision_number > 0 AND aggregate_version >= latest_revision_number AND (state <> 'WITHDRAWN' OR aggregate_version > latest_revision_number)),
    CONSTRAINT ck_incident_report_frozen CHECK (frozen_revision_number IS NULL OR (state = 'ACTIVE' AND frozen_revision_number > 0 AND frozen_revision_number <= latest_revision_number))
);
CREATE INDEX idx_incident_report_race ON incident_reports(race_id);

CREATE TABLE incident_report_revisions (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    revision_number INT NOT NULL,
    report_version BIGINT NOT NULL,
    involved_drivers VARCHAR(2000) NOT NULL,
    replay_timestamp VARCHAR(200) NOT NULL,
    description LONGTEXT NOT NULL,
    evidence_links LONGTEXT NOT NULL,
    actor_id VARCHAR(32) NOT NULL,
    accepted_at TIMESTAMP(6) NOT NULL,
    request_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uq_incident_report_revision UNIQUE (report_id, revision_number),
    CONSTRAINT fk_incident_revision_report FOREIGN KEY (report_id) REFERENCES incident_reports(id),
    CONSTRAINT ck_incident_revision_version CHECK (revision_number > 0 AND report_version >= revision_number),
    CONSTRAINT ck_incident_revision_payload CHECK (CHAR_LENGTH(TRIM(involved_drivers)) > 0 AND CHAR_LENGTH(TRIM(replay_timestamp)) > 0 AND CHAR_LENGTH(TRIM(description)) > 0 AND CHAR_LENGTH(description) <= 20000 AND CHAR_LENGTH(evidence_links) <= 8000),
    CONSTRAINT ck_incident_revision_actor CHECK (actor_id REGEXP '^[0-9]{1,32}$')
);

CREATE TABLE incident_report_events (
    id UUID PRIMARY KEY,
    window_id UUID NOT NULL,
    report_id UUID,
    event_key VARCHAR(120) NOT NULL UNIQUE,
    kind VARCHAR(24) NOT NULL,
    window_version BIGINT NOT NULL,
    report_version BIGINT,
    revision_number INT,
    actor_kind VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    accepted_at TIMESTAMP(6) NOT NULL,
    previous_deadline TIMESTAMP(6),
    deadline TIMESTAMP(6) NOT NULL,
    request_id UUID UNIQUE,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT uq_incident_report_event_version UNIQUE (report_id, report_version),
    CONSTRAINT fk_incident_event_window FOREIGN KEY (window_id) REFERENCES incident_report_windows(id),
    CONSTRAINT fk_incident_event_report FOREIGN KEY (report_id) REFERENCES incident_reports(id),
    CONSTRAINT fk_incident_event_owner FOREIGN KEY (report_id, window_id) REFERENCES incident_reports(id, window_id),
    CONSTRAINT ck_incident_event_kind CHECK (CASE kind WHEN 'OPENED' THEN 1 WHEN 'DEADLINE_CHANGED' THEN 1 WHEN 'CLOSED' THEN 1 WHEN 'SUBMITTED' THEN 1 WHEN 'EDITED' THEN 1 WHEN 'WITHDRAWN' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_event_actor CHECK (CASE actor_kind WHEN 'MANAGEMENT' THEN 1 WHEN 'REPORTER' THEN 1 WHEN 'SYSTEM' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_event_metadata CHECK (window_version > 0 AND CHAR_LENGTH(TRIM(event_key)) > 0 AND CHAR_LENGTH(TRIM(actor_id)) > 0),
    CONSTRAINT ck_incident_event_owner CHECK (CASE kind
        WHEN 'SUBMITTED' THEN CASE WHEN report_id IS NOT NULL AND report_version > 0 AND revision_number > 0 AND request_id IS NOT NULL AND actor_kind = 'REPORTER' THEN 1 ELSE 0 END
        WHEN 'EDITED' THEN CASE WHEN report_id IS NOT NULL AND report_version > 0 AND revision_number > 0 AND request_id IS NOT NULL AND actor_kind = 'REPORTER' THEN 1 ELSE 0 END
        WHEN 'WITHDRAWN' THEN CASE WHEN report_id IS NOT NULL AND report_version > 0 AND revision_number > 0 AND request_id IS NOT NULL AND actor_kind = 'REPORTER' THEN 1 ELSE 0 END
        ELSE CASE WHEN report_id IS NULL AND report_version IS NULL AND revision_number IS NULL AND request_id IS NULL AND CHAR_LENGTH(TRIM(reason)) > 0 AND actor_kind <> 'REPORTER' THEN 1 ELSE 0 END END = 1),
    CONSTRAINT ck_incident_event_cutoff CHECK (CASE kind WHEN 'CLOSED' THEN CASE WHEN accepted_at >= deadline THEN 1 ELSE 0 END ELSE CASE WHEN accepted_at < deadline THEN 1 ELSE 0 END END = 1)
);
