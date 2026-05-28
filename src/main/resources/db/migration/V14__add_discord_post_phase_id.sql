ALTER TABLE discord_post ADD COLUMN phase_id UUID NULL;

ALTER TABLE discord_post ADD CONSTRAINT fk_discord_post_phase FOREIGN KEY (phase_id) REFERENCES season_phases(id) ON DELETE SET NULL;

CREATE INDEX idx_discord_post_phase_id ON discord_post (phase_id);
