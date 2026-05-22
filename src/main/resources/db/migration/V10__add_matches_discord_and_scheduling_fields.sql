-- Phase 94 V10 D-13: extend matches table with Discord channel-handle + 5 scheduling/team-facing fields.
-- All columns nullable — operator-populated post-match-creation.
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). MatchForm Jakarta-Validation owns the @Size
-- bounds at the controller layer; the DB only enforces VARCHAR length ceilings.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

ALTER TABLE matches ADD COLUMN discord_channel_id VARCHAR(32);
ALTER TABLE matches ADD COLUMN discord_channel_webhook_url VARCHAR(500);
ALTER TABLE matches ADD COLUMN discord_teaser VARCHAR(2000);
ALTER TABLE matches ADD COLUMN stream_link VARCHAR(500);
ALTER TABLE matches ADD COLUMN lobby_host VARCHAR(100);
ALTER TABLE matches ADD COLUMN race_director VARCHAR(100);
ALTER TABLE matches ADD COLUMN streamer VARCHAR(100);
