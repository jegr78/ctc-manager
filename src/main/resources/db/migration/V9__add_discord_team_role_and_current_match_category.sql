-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordSnowflake.PATTERN owns the snowflake
-- regex contract at the Jakarta-Validation layer; the DB only enforces length.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32);

ALTER TABLE discord_global_config ADD COLUMN current_match_category_id VARCHAR(32) NOT NULL DEFAULT '';
