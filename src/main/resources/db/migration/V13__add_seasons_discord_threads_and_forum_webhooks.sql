-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm Jakarta-Validation owns the
-- webhook regex contract; SeasonForm owns the snowflake regex contract.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

ALTER TABLE discord_global_config ADD COLUMN race_results_forum_webhook_url VARCHAR(500);
ALTER TABLE discord_global_config ADD COLUMN standings_forum_webhook_url VARCHAR(500);
ALTER TABLE seasons ADD COLUMN discord_race_results_thread_id VARCHAR(32);
ALTER TABLE seasons ADD COLUMN discord_standings_thread_id VARCHAR(32);
