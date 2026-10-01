CREATE TABLE site_slugs (
    id UUID NOT NULL PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    base_slug VARCHAR(255) NOT NULL,
    entity_id UUID NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_site_slugs_kind_slug UNIQUE (kind, slug),
    CONSTRAINT uk_site_slugs_kind_entity UNIQUE (kind, entity_id)
);
