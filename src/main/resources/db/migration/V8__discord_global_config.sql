-- Phase 93: discord_global_config table — singleton row holding Discord-integration settings.
-- This table is PERMANENTLY OUT OF BACKUP EXPORT SCOPE: the entity lives under
-- org.ctc.discord.model.* (Phase 72 D-15 package filter restricts BackupSchema.EXPORT_ORDER to
-- org.ctc.domain.model.*), so adding it does NOT bump EXPORT_ORDER size 24.
--
-- Column type rationale (Phase 72 D-09 + Phase 93 D-02):
--   - id BIGINT IDENTITY:        portable across H2 2.x + MariaDB 10.7+; deterministic seed row.
--   - VARCHAR(32) snowflakes:    Discord snowflake IDs are 17–20 digit decimal strings, safely
--                                fits in 32 chars with headroom.
--   - VARCHAR(500) webhook URL:  full Discord webhook URLs incl. token segment fit comfortably.
--   - VARCHAR(50) vs emoji name: Discord emoji short-names cap at 32 chars; 50 covers headroom.
--   - VARCHAR(50) NOT NULL DEFAULT 'CTC' for vs_emoji_name: seed row pre-populated.
--   - VARCHAR(32) NULLABLE bot_application_id: only known after first Test Connection.
--   - TIMESTAMP NOT NULL audit columns: BaseEntity auditing fills these via Spring Data JPA.
--   - NO CHECK constraints (D-02): H2 and MariaDB drift on CHECK semantics; Jakarta-Validation
--     on the DiscordConfigForm DTO is the single source of regex-truth.
--   - NO LONGTEXT (Phase 72 D-09): every field is a short bounded string.
--
-- Seed-row INSERT places exactly ONE row with empty-string defaults so the admin page renders
-- the "not configured" badges (CONTEXT D-02 + D-12). The operator promotes empty → configured
-- via the admin form; subsequent saves UPDATE this row by id, never INSERT a second row
-- (DiscordGlobalConfigService.save loads via findFirstByOrderByIdAsc).
--
-- Compatible with H2 2.x and MariaDB 10.7+.
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
