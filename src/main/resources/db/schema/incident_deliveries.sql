ALTER TABLE incident_report_windows ADD COLUMN delivery_paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE incident_report_windows ADD COLUMN delivery_resumed_by VARCHAR(128);
ALTER TABLE incident_report_windows ADD COLUMN delivery_resume_reason VARCHAR(1000);
ALTER TABLE incident_report_windows ADD COLUMN delivery_resumed_at TIMESTAMP(6);
ALTER TABLE incident_report_revisions ADD CONSTRAINT uq_incident_revision_owner UNIQUE (id, report_id);
CREATE TABLE incident_deliveries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    operation VARCHAR(16) NOT NULL,
    operation_key VARCHAR(64) NOT NULL UNIQUE,
    guild_id VARCHAR(32) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    nonce VARCHAR(25) NOT NULL,
    case_reference VARCHAR(64) NOT NULL,
    party_label VARCHAR(255),
    captured_at TIMESTAMP(6) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    claim_token UUID,
    claim_generation VARCHAR(64),
    lease_until TIMESTAMP(6),
    next_attempt_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    message_id VARCHAR(32),
    failure_code VARCHAR(64),
    restore_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled_by VARCHAR(128),
    reconciliation_reason VARCHAR(1000),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_incident_delivery_report FOREIGN KEY (report_id) REFERENCES incident_reports(id),
    CONSTRAINT fk_incident_delivery_revision FOREIGN KEY (revision_id, report_id) REFERENCES incident_report_revisions(id, report_id),
    CONSTRAINT ck_incident_delivery_operation CHECK (CASE operation WHEN 'RECEIPT' THEN 1 WHEN 'DISCLOSURE' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_delivery_state CHECK (CASE state WHEN 'PENDING' THEN 1 WHEN 'CLAIMED' THEN 1 WHEN 'SENT' THEN 1 WHEN 'FAILED' THEN 1 WHEN 'UNCERTAIN' THEN 1 WHEN 'SUPERSEDED' THEN 1 ELSE 0 END = 1),
    CONSTRAINT ck_incident_delivery_counts CHECK (aggregate_version >= 0 AND attempts >= 0 AND aggregate_version >= attempts),
    CONSTRAINT ck_incident_delivery_destination CHECK (guild_id REGEXP '^[0-9]{1,32}$' AND channel_id REGEXP '^[0-9]{1,32}$' AND CHAR_LENGTH(nonce) <= 25),
    CONSTRAINT ck_incident_delivery_party CHECK ((operation = 'RECEIPT' AND party_label IS NULL) OR (operation = 'DISCLOSURE' AND party_label IS NOT NULL AND CHAR_LENGTH(TRIM(party_label)) > 0)),
    CONSTRAINT ck_incident_delivery_claim CHECK (state <> 'CLAIMED' OR (claim_token IS NOT NULL AND claim_generation IS NOT NULL AND lease_until IS NOT NULL AND attempts > 0)),
    CONSTRAINT ck_incident_delivery_sent CHECK (state <> 'SENT' OR (message_id IS NOT NULL AND completed_at IS NOT NULL AND attempts > 0))
);
CREATE INDEX idx_incident_delivery_due ON incident_deliveries(operation, state, next_attempt_at);
