CREATE TABLE incident_rulings (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL UNIQUE,
    aggregate_version BIGINT NOT NULL,
    latest_revision_number INT NOT NULL,
    requested_revision_number INT,
    published_revision_number INT,
    published_message_id VARCHAR(32),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_incident_ruling_report FOREIGN KEY (report_id) REFERENCES incident_reports(id),
    CONSTRAINT ck_incident_ruling_version CHECK (aggregate_version >= latest_revision_number AND latest_revision_number >= 1),
    CONSTRAINT ck_incident_ruling_requested CHECK (requested_revision_number IS NULL OR requested_revision_number BETWEEN 1 AND latest_revision_number),
    CONSTRAINT ck_incident_ruling_published CHECK ((published_revision_number IS NULL AND published_message_id IS NULL) OR
        (published_revision_number BETWEEN 1 AND requested_revision_number AND published_message_id REGEXP '^[0-9]{1,20}$'))
);
CREATE TABLE incident_ruling_revisions (
    id UUID PRIMARY KEY,
    ruling_id UUID NOT NULL,
    revision_number INT NOT NULL,
    ruling_text VARCHAR(1800) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    accepted_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_incident_ruling_revision FOREIGN KEY (ruling_id) REFERENCES incident_rulings(id),
    CONSTRAINT uq_incident_ruling_revision UNIQUE (ruling_id, revision_number),
    CONSTRAINT ck_incident_ruling_revision CHECK (revision_number >= 1 AND CHAR_LENGTH(TRIM(ruling_text)) > 0 AND CHAR_LENGTH(TRIM(actor_id)) > 0)
);
CREATE TABLE incident_ruling_publications (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL UNIQUE,
    thread_publication_id UUID NOT NULL,
    guild_id VARCHAR(32) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    previous_content VARCHAR(2000),
    previous_revision_number INT,
    previous_message_id VARCHAR(32),
    nonce VARCHAR(25) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    state VARCHAR(16) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    attempts INT NOT NULL,
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
    CONSTRAINT fk_incident_ruling_publication_revision FOREIGN KEY (revision_id) REFERENCES incident_ruling_revisions(id),
    CONSTRAINT fk_incident_ruling_publication_thread FOREIGN KEY (thread_publication_id) REFERENCES incident_thread_publications(id),
    CONSTRAINT ck_incident_ruling_publication_version CHECK (aggregate_version >= attempts AND attempts >= 0),
    CONSTRAINT ck_incident_ruling_publication_state CHECK (state IN ('PENDING','CLAIMED','SENT','FAILED','UNCERTAIN','SUPERSEDED')),
    CONSTRAINT ck_incident_ruling_publication_destination CHECK (guild_id REGEXP '^[0-9]{1,20}$' AND channel_id REGEXP '^[0-9]{1,20}$'),
    CONSTRAINT ck_incident_ruling_publication_previous CHECK ((previous_revision_number IS NULL AND previous_content IS NULL AND previous_message_id IS NULL) OR
        (previous_revision_number IS NOT NULL AND previous_revision_number >= 1 AND previous_content IS NOT NULL AND previous_message_id IS NOT NULL))
);
ALTER TABLE incident_deliveries ADD COLUMN pairing_label VARCHAR(1000);
ALTER TABLE incident_deliveries ADD COLUMN race_label VARCHAR(1000);
ALTER TABLE incident_deliveries ADD CONSTRAINT ck_incident_disclosure_identity CHECK (
    (pairing_label IS NULL AND race_label IS NULL) OR
    (operation = 'DISCLOSURE' AND pairing_label IS NOT NULL AND race_label IS NOT NULL
     AND CHAR_LENGTH(TRIM(pairing_label)) > 0 AND CHAR_LENGTH(TRIM(race_label)) > 0)
);
