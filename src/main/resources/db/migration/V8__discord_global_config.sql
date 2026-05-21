-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm Jakarta-Validation owns the
-- snowflake/webhook regex contract instead of the DB schema.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

CREATE TABLE discord_global_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    guild_id VARCHAR(32) NOT NULL DEFAULT '',
    announcement_webhook_url VARCHAR(500) NOT NULL DEFAULT '',
    race_results_forum_channel_id VARCHAR(32) NOT NULL DEFAULT '',
    standings_forum_channel_id VARCHAR(32) NOT NULL DEFAULT '',
    vs_emoji_name VARCHAR(50) NOT NULL DEFAULT 'CTC',
    bot_application_id VARCHAR(32),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO discord_global_config (
    guild_id, announcement_webhook_url, race_results_forum_channel_id,
    standings_forum_channel_id, vs_emoji_name, bot_application_id, created_at, updated_at
) VALUES ('', '', '', '', 'CTC', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
