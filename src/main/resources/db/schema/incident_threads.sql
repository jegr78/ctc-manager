ALTER TABLE discord_global_config ADD COLUMN stewards_channel_id VARCHAR(32) NOT NULL DEFAULT '';
ALTER TABLE discord_global_config ADD COLUMN incident_staff_role_ids VARCHAR(2000) NOT NULL DEFAULT '';
ALTER TABLE discord_global_config ADD COLUMN stewards_forum_tag_ids VARCHAR(200) NOT NULL DEFAULT '';
CREATE TABLE incident_thread_publications (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL UNIQUE,
    stewards_channel_id VARCHAR(32) NOT NULL,
    staff_role_ids VARCHAR(2000) NOT NULL,
    forum_tag_ids VARCHAR(200) NOT NULL,
    message_chunks INT NOT NULL,
    private_starter_id VARCHAR(32),
    private_thread_id VARCHAR(32),
    private_message_ids VARCHAR(2000) NOT NULL DEFAULT '',
    public_starter_id VARCHAR(32),
    public_thread_id VARCHAR(32),
    public_message_ids VARCHAR(2000) NOT NULL DEFAULT '',
    reference_message_id VARCHAR(32),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT fk_incident_threads_delivery FOREIGN KEY (delivery_id) REFERENCES incident_deliveries(id),
    CONSTRAINT ck_incident_threads_chunks CHECK (message_chunks >= 1 AND message_chunks <= 40),
    CONSTRAINT ck_incident_threads_destination CHECK (stewards_channel_id REGEXP '^[0-9]{1,20}$' AND CHAR_LENGTH(staff_role_ids) > 0),
    CONSTRAINT ck_incident_threads_private CHECK (private_thread_id IS NULL OR private_starter_id IS NOT NULL),
    CONSTRAINT ck_incident_threads_public CHECK (public_starter_id IS NULL OR private_thread_id IS NOT NULL),
    CONSTRAINT ck_incident_threads_public_thread CHECK (public_thread_id IS NULL OR (public_starter_id IS NOT NULL AND public_thread_id = public_starter_id)),
    CONSTRAINT ck_incident_threads_reference CHECK (reference_message_id IS NULL OR public_thread_id IS NOT NULL)
);
