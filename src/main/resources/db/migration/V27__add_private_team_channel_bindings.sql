CREATE TABLE team_pick_channel_bindings (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL,
    team_id UUID NOT NULL,
    guild_id VARCHAR(32) NOT NULL,
    channel_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_pick_binding_team UNIQUE (guild_id, season_id, team_id),
    CONSTRAINT uq_pick_binding_channel UNIQUE (guild_id, season_id, channel_id),
    CONSTRAINT ck_pick_binding_guild CHECK (guild_id REGEXP '^[0-9]{1,32}$'),
    CONSTRAINT ck_pick_binding_channel CHECK (channel_id REGEXP '^[0-9]{1,32}$'),
    CONSTRAINT fk_pick_binding_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    CONSTRAINT fk_pick_binding_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);
