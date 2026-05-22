CREATE TABLE discord_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_id VARCHAR(32) NOT NULL,
    message_id VARCHAR(32) NOT NULL,
    webhook_id VARCHAR(32) NOT NULL,
    webhook_token VARCHAR(128) NOT NULL,
    post_type VARCHAR(32) NOT NULL,
    match_id BINARY(16) NULL,
    matchday_id BINARY(16) NULL,
    race_id BINARY(16) NULL,
    season_id BINARY(16) NULL,
    posted_at TIMESTAMP NOT NULL,
    attachments_replaced_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_discord_post_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE SET NULL,
    CONSTRAINT fk_discord_post_matchday FOREIGN KEY (matchday_id) REFERENCES matchdays(id) ON DELETE SET NULL,
    CONSTRAINT fk_discord_post_race FOREIGN KEY (race_id) REFERENCES races(id) ON DELETE SET NULL,
    CONSTRAINT fk_discord_post_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE SET NULL
);

CREATE INDEX idx_discord_post_channel_id ON discord_post (channel_id);
CREATE INDEX idx_discord_post_match_id ON discord_post (match_id);
CREATE INDEX idx_discord_post_matchday_id ON discord_post (matchday_id);
CREATE INDEX idx_discord_post_race_id ON discord_post (race_id);
CREATE INDEX idx_discord_post_season_id ON discord_post (season_id);
